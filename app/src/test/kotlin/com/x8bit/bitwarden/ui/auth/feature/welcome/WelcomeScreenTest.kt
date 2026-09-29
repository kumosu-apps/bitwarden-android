package com.x8bit.bitwarden.ui.auth.feature.welcome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.robolectric.annotation.Config

class WelcomeScreenTest : BitwardenComposeTest() {
    private var onNavigateToLoginCalled = false
    private val mutableStateFlow = MutableStateFlow(DEFAULT_STATE)
    private val mutableEventFlow = bufferedMutableSharedFlow<WelcomeEvent>()
    private val viewModel = mockk<WelcomeViewModel>(relaxed = true) {
        every { stateFlow } returns mutableStateFlow
        every { eventFlow } returns mutableEventFlow
    }

    @Before
    fun setUp() {
        setContent {
            WelcomeScreen(
                onNavigateToLogin = { onNavigateToLoginCalled = true },
                onNavigateToStartRegistration = {},
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun `pages should display and update according to state`() {
        composeTestRule
            .onNodeWithText("Security, prioritized")
            .assertExists()
            .assertIsDisplayed()

        mutableEventFlow.tryEmit(WelcomeEvent.UpdatePager(index = 1))
        composeTestRule
            .onNodeWithText("Security, prioritized")
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText("Quick and easy login")
            .assertExists()
            .assertIsDisplayed()

        mutableStateFlow.update { it.copy(pages = listOf(WelcomeState.WelcomeCard.CardThree)) }
        composeTestRule
            .onNodeWithText("Level up your logins")
            .assertExists()
            .assertIsDisplayed()
    }

    @Config(qualifiers = "land")
    @Test
    fun `pages should display and update according to state in landscape mode`() {
        composeTestRule
            .onNodeWithText("Security, prioritized")
            .assertExists()
            .assertIsDisplayed()

        mutableEventFlow.tryEmit(WelcomeEvent.UpdatePager(index = 1))
        composeTestRule
            .onNodeWithText("Security, prioritized")
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText("Quick and easy login")
            .assertExists()
            .assertIsDisplayed()

        mutableStateFlow.update { it.copy(pages = listOf(WelcomeState.WelcomeCard.CardThree)) }
        composeTestRule
            .onNodeWithText("Level up your logins")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun `NavigateToLogin event should call onNavigateToLogin`() {
        mutableEventFlow.tryEmit(WelcomeEvent.NavigateToLogin)
        assertTrue(onNavigateToLoginCalled)
    }

    @Test
    fun `continue button click should send LoginClick action`() {
        mutableStateFlow.update { it.copy(pages = emptyList()) }
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        verify { viewModel.trySendAction(WelcomeAction.LoginClick) }
    }
}

private val DEFAULT_STATE = WelcomeState(
    index = 0,
    pages = listOf(
        WelcomeState.WelcomeCard.CardOne,
        WelcomeState.WelcomeCard.CardTwo,
    ),
)
