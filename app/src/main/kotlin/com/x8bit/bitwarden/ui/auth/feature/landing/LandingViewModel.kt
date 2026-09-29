package com.x8bit.bitwarden.ui.auth.feature.landing

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import com.bitwarden.ui.platform.base.util.prefixHttpsIfNecessaryOrNull
import com.bitwarden.ui.platform.manager.intent.model.AuthTabData
import com.bitwarden.ui.util.Text
import com.bitwarden.ui.util.asText
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.util.SsoCallbackResult
import com.x8bit.bitwarden.data.platform.repository.EnvironmentRepository
import com.x8bit.bitwarden.data.tools.generator.repository.GeneratorRepository
import com.x8bit.bitwarden.data.tools.generator.repository.utils.generateRandomString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

private const val KEY_STATE = "state"
private const val KEY_OIDC_DATA = "oidcData"
private const val KEY_CALLBACK_RESULT = "oidcCallbackResult"
private const val RANDOM_STRING_LENGTH = 64
private const val CLIENT_NAME = "Bitwarden Android OIDC Debug"

@HiltViewModel
class LandingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val environmentRepository: EnvironmentRepository,
    private val generatorRepository: GeneratorRepository,
    private val savedStateHandle: SavedStateHandle,
) : BaseViewModel<LandingState, LandingEvent, LandingAction>(
    initialState = savedStateHandle[KEY_STATE]
        ?: LandingState(
            serverUrl = "",
            isContinueButtonEnabled = false,
            dialog = null,
        ),
) {

    private var oidcData: OidcFlowData?
        get() = savedStateHandle[KEY_OIDC_DATA]
        set(value) {
            savedStateHandle[KEY_OIDC_DATA] = value
        }

    private var savedCallbackResult: SsoCallbackResult?
        get() = savedStateHandle[KEY_CALLBACK_RESULT]
        set(value) {
            savedStateHandle[KEY_CALLBACK_RESULT] = value
        }

    init {
        stateFlow
            .onEach { savedStateHandle[KEY_STATE] = it }
            .launchIn(viewModelScope)

        authRepository
            .ssoCallbackResultFlow
            .onEach {
                sendAction(LandingAction.Internal.OnOidcCallbackResult(it))
            }
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: LandingAction) {
        when (action) {
            LandingAction.AppSettingsClick -> handleAppSettingsClick()
            LandingAction.ContinueButtonClick -> handleContinueClick()
            LandingAction.DialogDismiss -> handleDialogDismiss()
            is LandingAction.ServerUrlChange -> handleServerUrlChange(action)
            is LandingAction.Internal -> handleInternal(action)
        }
    }

    private fun handleAppSettingsClick() {
    }

    private fun handleServerUrlChange(action: LandingAction.ServerUrlChange) {
        mutableStateFlow.update {
            it.copy(
                serverUrl = action.serverUrl,
                isContinueButtonEnabled = action.serverUrl.isNotBlank(),
                dialog = null,
            )
        }
    }

    private fun handleContinueClick() {
        val serverUrl = state.serverUrl
            .trimEnd('/')
            .prefixHttpsIfNecessaryOrNull()
            ?: return

        mutableStateFlow.update {
            it.copy(serverUrl = serverUrl)
        }

        showLoading("Discovering OIDC configuration...")
        viewModelScope.launch {
            discoverOidc(serverUrl)
        }
    }

    private suspend fun discoverOidc(serverUrl: String) {
        try {
            val wellKnownUrl = "$serverUrl/.well-known/openid-configuration"
            val jsonString = withContext(Dispatchers.IO) {
                val connection = URL(wellKnownUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP $responseCode from well-known endpoint")
                }
                connection.inputStream.bufferedReader().readText()
            }

            val oidcConfig = Json.parseToJsonElement(jsonString).jsonObject
            val authorizationEndpoint = oidcConfig["authorization_endpoint"]?.jsonPrimitive?.content
                ?: throw Exception("Missing authorization_endpoint in OIDC config")
            val tokenEndpoint = oidcConfig["token_endpoint"]?.jsonPrimitive?.content
                ?: throw Exception("Missing token_endpoint in OIDC config")
            val issuer = oidcConfig["issuer"]?.jsonPrimitive?.content ?: serverUrl
            val registrationEndpoint = oidcConfig["registration_endpoint"]?.jsonPrimitive?.content

            if (registrationEndpoint != null) {
                showLoading("Registering client dynamically...")
                val clientId = registerClient(registrationEndpoint)
                launchOidcAuthorization(
                    serverUrl = serverUrl,
                    authorizationEndpoint = authorizationEndpoint,
                    tokenEndpoint = tokenEndpoint,
                    issuer = issuer,
                    clientId = clientId,
                )
            } else {
                showError("No registration_endpoint in OIDC discovery. Dynamic client registration is required.")
            }
        } catch (e: Exception) {
            showError("Failed to discover OIDC: ${e.message}")
        }
    }

    private suspend fun registerClient(registrationEndpoint: String): String {
        val registrationJson = """
            {
                "client_name": "$CLIENT_NAME",
                "redirect_uris": ["bitwarden://sso-callback"],
                "grant_types": ["authorization_code"],
                "response_types": ["code"],
                "token_endpoint_auth_method": "none"
            }
        """.trimIndent()

        val responseJson = withContext(Dispatchers.IO) {
            val connection = URL(registrationEndpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true

            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(registrationJson)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_CREATED &&
                responseCode != HttpURLConnection.HTTP_OK
            ) {
                val errorBody = try {
                    connection.errorStream.bufferedReader().readText()
                } catch (_: Exception) {
                    "no error body"
                }
                throw Exception("HTTP $responseCode from registration endpoint: $errorBody")
            }

            connection.inputStream.bufferedReader().readText()
        }

        val registrationResponse = Json.parseToJsonElement(responseJson).jsonObject
        return registrationResponse["client_id"]?.jsonPrimitive?.content
            ?: throw Exception("Missing client_id in registration response")
    }

    private suspend fun launchOidcAuthorization(
        serverUrl: String,
        authorizationEndpoint: String,
        tokenEndpoint: String,
        issuer: String,
        clientId: String,
    ) {
        val codeVerifier = generatorRepository.generateRandomString(RANDOM_STRING_LENGTH)
        val state = generatorRepository.generateRandomString(RANDOM_STRING_LENGTH)
        val redirectUri = "bitwarden://sso-callback"

        val codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest
                .getInstance("SHA-256")
                .digest(codeVerifier.toByteArray()),
        )

        oidcData = OidcFlowData(
            serverUrl = serverUrl,
            tokenEndpoint = tokenEndpoint,
            issuer = issuer,
            clientId = clientId,
            codeVerifier = codeVerifier,
            state = state,
        )

        val encodedRedirectUri = URLEncoder.encode(redirectUri, "UTF-8")
        val uri = "$authorizationEndpoint" +
            "?client_id=${URLEncoder.encode(clientId, "UTF-8")}" +
            "&redirect_uri=$encodedRedirectUri" +
            "&response_type=code" +
            "&scope=openid%20profile%20email" +
            "&state=$state" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=S256" +
            "&response_mode=query"

        mutableStateFlow.update { it.copy(dialog = null) }
        sendEvent(
            LandingEvent.NavigateToSsoLogin(
                uri = uri.toUri(),
                authTabData = AuthTabData.CustomScheme(
                    callbackUrl = redirectUri,
                ),
            ),
        )
    }

    private fun handleInternal(action: LandingAction.Internal) {
        when (action) {
            is LandingAction.Internal.OnOidcCallbackResult -> {
                savedCallbackResult = action.ssoCallbackResult
                attemptTokenExchange()
            }
        }
    }

    private fun attemptTokenExchange() {
        val callbackResult = requireNotNull(savedCallbackResult)
        val data = requireNotNull(oidcData)

        when (callbackResult) {
            is SsoCallbackResult.MissingCode -> {
                showError("Authorization failed: no code received")
            }
            is SsoCallbackResult.Success -> {
                if (callbackResult.state != data.state) {
                    showError("Authorization failed: state mismatch")
                    return
                }

                showLoading("Exchanging code for tokens...")
                viewModelScope.launch {
                    exchangeCodeForTokens(
                        tokenEndpoint = data.tokenEndpoint,
                        code = callbackResult.code,
                        codeVerifier = data.codeVerifier,
                        clientId = data.clientId,
                        redirectUri = "bitwarden://sso-callback",
                        serverUrl = data.serverUrl,
                        issuer = data.issuer,
                    )
                }
            }
        }
    }

    private suspend fun exchangeCodeForTokens(
        tokenEndpoint: String,
        code: String,
        codeVerifier: String,
        clientId: String,
        redirectUri: String,
        serverUrl: String,
        issuer: String,
    ) {
        try {
            val body = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
                "client_id" to clientId,
                "code_verifier" to codeVerifier,
            ).entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }

            val responseJson = withContext(Dispatchers.IO) {
                val connection = URL(tokenEndpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true

                connection.outputStream.bufferedWriter().use { writer ->
                    writer.write(body)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    val errorBody = try {
                        connection.errorStream.bufferedReader().readText()
                    } catch (_: Exception) {
                        "no error body"
                    }
                    throw Exception("HTTP $responseCode from token endpoint: $errorBody")
                }

                connection.inputStream.bufferedReader().readText()
            }

            val tokenInfo = buildTokenInfo(
                responseJson = responseJson,
                serverUrl = serverUrl,
                issuer = issuer,
                clientId = clientId,
                tokenEndpoint = tokenEndpoint,
            )

            mutableStateFlow.update { it.copy(dialog = null) }
            sendEvent(LandingEvent.NavigateToOidcToken(tokenInfo))
        } catch (e: Exception) {
            showError("Token exchange failed: ${e.message}")
        }
    }

    private fun buildTokenInfo(
        responseJson: String,
        serverUrl: String,
        issuer: String,
        clientId: String,
        tokenEndpoint: String,
    ): String {
        val parts = mutableListOf<String>()
        parts.add("Server URL: $serverUrl")
        parts.add("Issuer: $issuer")
        parts.add("Client ID: $clientId")
        parts.add("Token Endpoint: $tokenEndpoint")
        parts.add("")

        try {
            val json = Json.parseToJsonElement(responseJson).jsonObject
            json.forEach { (key, value) ->
                val displayValue = when (key) {
                    "access_token", "refresh_token", "id_token" -> {
                        val token = value.jsonPrimitive.content
                        if (token.length > 50) {
                            "${token.take(30)}...${token.takeLast(20)}"
                        } else {
                            token
                        }
                    }
                    else -> value.toString()
                }
                parts.add("$key: $displayValue")
            }
        } catch (_: Exception) {
            parts.add("Raw response: $responseJson")
        }

        return parts.joinToString("\n")
    }

    private fun handleDialogDismiss() {
        mutableStateFlow.update { it.copy(dialog = null) }
    }

    private fun showError(message: String) {
        mutableStateFlow.update {
            it.copy(
                dialog = LandingState.DialogState.Error(message = message.asText()),
            )
        }
    }

    private fun showLoading(message: String) {
        mutableStateFlow.update {
            it.copy(
                dialog = LandingState.DialogState.Loading(message = message.asText()),
            )
        }
    }
}

@Parcelize
data class LandingState(
    val serverUrl: String,
    val isContinueButtonEnabled: Boolean,
    val dialog: DialogState?,
) : Parcelable {
    sealed class DialogState : Parcelable {
        @Parcelize
        data class Error(val message: Text) : DialogState()

        @Parcelize
        data class Loading(val message: Text) : DialogState()
    }
}

sealed class LandingEvent {
    data class NavigateToOidcToken(val tokenInfoJson: String) : LandingEvent()

    data class NavigateToSsoLogin(
        val uri: Uri,
        val authTabData: AuthTabData,
    ) : LandingEvent()
}

sealed class LandingAction {
    data object AppSettingsClick : LandingAction()
    data object ContinueButtonClick : LandingAction()
    data object DialogDismiss : LandingAction()

    data class ServerUrlChange(val serverUrl: String) : LandingAction()

    sealed class Internal : LandingAction() {
        data class OnOidcCallbackResult(val ssoCallbackResult: SsoCallbackResult) : Internal()
    }
}

@Parcelize
data class OidcFlowData(
    val serverUrl: String,
    val tokenEndpoint: String,
    val issuer: String,
    val clientId: String,
    val codeVerifier: String,
    val state: String,
) : Parcelable
