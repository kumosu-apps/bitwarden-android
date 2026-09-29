package com.x8bit.bitwarden.ui.auth.feature.landing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.bitwarden.core.data.repository.util.bufferedMutableSharedFlow
import com.x8bit.bitwarden.ui.platform.base.BitwardenComposeTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue

class LandingScreenTest : BitwardenComposeTest() {
    private var onNavigateToOidcTokenCalled = false
    private var capturedTokenInfo: String? = null

    private val mutableEventFlow = bufferedMutableSharedFlow<LandingEvent>()
    private val mutableStateFlow = MutableStateFlow(
        LandingState(
            serverUrl = "",
            isContinueButtonEnabled = false,
            dialog = null,
        ),
    )
    private val viewModel = mockk<LandingViewModel>(relaxed = true) {
        every { eventFlow } returns mutableEventFlow
        every { stateFlow } returns mutableStateFlow
    }

    @Before
    fun setUp() {
        setContent {
            LandingScreen(
                onNavigateToLogin = {},
                onNavigateToEnvironment = {},
                onNavigateToStartRegistration = {},
                onNavigateToPreAuthSettings = {},
                onNavigateToOidcToken = { tokenInfoJson ->
                    capturedTokenInfo = tokenInfoJson
                    onNavigateToOidcTokenCalled = true
                },
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun `continue button should be disabled when server url is empty`() {
        composeTestRule
            .onNodeWithText("Continue")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Continue")
            .assertIsNotEnabled()
    }

    @Test
    fun `continue button should be enabled when server url is not empty`() {
        mutableStateFlow.update {
            it.copy(serverUrl = "https://example.com", isContinueButtonEnabled = true)
        }
        composeTestRule
            .onNodeWithText("Continue")
            .assertIsEnabled()
    }

    @Test
    fun `server url text field should update state on input`() {
        composeTestRule
            .onNodeWithText("Server URL")
            .performTextInput("https://my-server.com")
        verify { viewModel.trySendAction(LandingAction.ServerUrlChange("https://my-server.com")) }
    }

    @Test
    fun `continue click should call ContinueButtonClick action`() {
        mutableStateFlow.update {
            it.copy(serverUrl = "https://example.com", isContinueButtonEnabled = true)
        }
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        verify { viewModel.trySendAction(LandingAction.ContinueButtonClick) }
    }

    @Test
    fun `NavigateToOidcToken event should call onNavigateToOidcToken`() {
        val tokenInfo = "test token info"
        mutableEventFlow.tryEmit(LandingEvent.NavigateToOidcToken(tokenInfo))
        assertTrue(onNavigateToOidcTokenCalled)
        assertTrue(capturedTokenInfo == tokenInfo)
    }
}
