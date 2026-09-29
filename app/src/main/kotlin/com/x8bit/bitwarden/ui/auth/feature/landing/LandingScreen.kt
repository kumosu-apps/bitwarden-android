package com.x8bit.bitwarden.ui.auth.feature.landing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitwarden.ui.platform.base.util.EventsEffect
import com.bitwarden.ui.platform.base.util.standardHorizontalMargin
import com.bitwarden.ui.platform.components.appbar.BitwardenTopAppBar
import com.bitwarden.ui.platform.components.button.BitwardenFilledButton
import com.bitwarden.ui.platform.components.button.BitwardenTextButton
import com.bitwarden.ui.platform.components.dialog.BitwardenBasicDialog
import com.bitwarden.ui.platform.components.dialog.BitwardenLoadingDialog
import com.bitwarden.ui.platform.components.field.BitwardenTextField
import com.bitwarden.ui.platform.components.model.CardStyle
import com.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.bitwarden.ui.platform.components.snackbar.BitwardenSnackbarHost
import com.bitwarden.ui.platform.components.snackbar.model.rememberBitwardenSnackbarHostState
import com.bitwarden.ui.platform.components.util.rememberVectorPainter
import com.bitwarden.ui.platform.composition.LocalIntentManager
import com.bitwarden.ui.platform.manager.IntentManager
import com.bitwarden.ui.platform.resource.BitwardenDrawable
import com.bitwarden.ui.platform.resource.BitwardenString
import com.bitwarden.ui.platform.theme.BitwardenTheme
import com.x8bit.bitwarden.ui.platform.composition.LocalAuthTabLaunchers
import com.x8bit.bitwarden.ui.platform.model.AuthTabLaunchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    onNavigateToLogin: (emailAddress: String) -> Unit,
    onNavigateToEnvironment: () -> Unit,
    onNavigateToStartRegistration: () -> Unit,
    onNavigateToPreAuthSettings: () -> Unit,
    onNavigateToOidcToken: (tokenInfoJson: String) -> Unit,
    authTabLaunchers: AuthTabLaunchers = LocalAuthTabLaunchers.current,
    intentManager: IntentManager = LocalIntentManager.current,
    viewModel: LandingViewModel = hiltViewModel(),
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val snackbarHostState = rememberBitwardenSnackbarHostState()
    EventsEffect(viewModel = viewModel) { event ->
        when (event) {
            is LandingEvent.NavigateToOidcToken -> onNavigateToOidcToken(event.tokenInfoJson)
            is LandingEvent.NavigateToSsoLogin -> {
                intentManager.startAuthTab(
                    uri = event.uri,
                    authTabData = event.authTabData,
                    launcher = authTabLaunchers.sso,
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        is LandingState.DialogState.Error -> {
            BitwardenBasicDialog(
                title = stringResource(id = BitwardenString.an_error_has_occurred),
                message = dialog.message(),
                onDismissRequest = { viewModel.trySendAction(LandingAction.DialogDismiss) },
            )
        }
        is LandingState.DialogState.Loading -> {
            BitwardenLoadingDialog(text = dialog.message())
        }
        null -> Unit
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    BitwardenScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BitwardenTopAppBar(
                title = "",
                scrollBehavior = scrollBehavior,
                navigationIcon = null,
                actions = {
                    BitwardenTextButton(
                        label = stringResource(id = BitwardenString.app_settings),
                        onClick = { viewModel.trySendAction(LandingAction.AppSettingsClick) },
                    )
                },
            )
        },
        snackbarHost = {
            BitwardenSnackbarHost(bitwardenHostState = snackbarHostState)
        },
    ) {
        LandingScreenContent(
            state = state,
            onServerUrlChange = { viewModel.trySendAction(LandingAction.ServerUrlChange(it)) },
            onContinueClick = { viewModel.trySendAction(LandingAction.ContinueButtonClick) },
        )
    }
}

@Composable
private fun LandingScreenContent(
    state: LandingState,
    onServerUrlChange: (String) -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        Spacer(modifier = Modifier.height(height = 12.dp))
        Spacer(modifier = Modifier.weight(1f))

        Image(
            painter = rememberVectorPainter(id = BitwardenDrawable.logo_bitwarden),
            contentDescription = null,
            modifier = Modifier
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(height = 12.dp))

        Text(
            text = stringResource(id = BitwardenString.log_in_verb),
            textAlign = TextAlign.Center,
            style = BitwardenTheme.typography.headlineSmall,
            color = BitwardenTheme.colorScheme.text.primary,
            modifier = Modifier
                .standardHorizontalMargin()
                .wrapContentHeight(),
        )

        Spacer(modifier = Modifier.height(height = 24.dp))

        Text(
            text = "Use your own cloud",
            textAlign = TextAlign.Center,
            style = BitwardenTheme.typography.bodyLarge,
            color = BitwardenTheme.colorScheme.text.secondary,
            modifier = Modifier
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(height = 16.dp))

        BitwardenTextField(
            modifier = Modifier
                .standardHorizontalMargin()
                .fillMaxWidth(),
            value = state.serverUrl,
            onValueChange = onServerUrlChange,
            label = stringResource(id = BitwardenString.server_url),
            placeholder = "ex. https://my-server.com",
            keyboardType = KeyboardType.Uri,
            cardStyle = CardStyle.Full,
        )

        Spacer(modifier = Modifier.height(height = 24.dp))

        BitwardenFilledButton(
            label = stringResource(id = BitwardenString.continue_text),
            onClick = onContinueClick,
            isEnabled = state.isContinueButtonEnabled,
            modifier = Modifier
                .standardHorizontalMargin()
                .fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(height = 12.dp))
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}
