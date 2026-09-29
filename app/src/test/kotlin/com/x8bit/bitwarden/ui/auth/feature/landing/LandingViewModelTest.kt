package com.x8bit.bitwarden.ui.auth.feature.landing

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.bitwarden.ui.platform.base.BaseViewModelTest
import com.bitwarden.ui.util.asText
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.platform.repository.EnvironmentRepository
import com.x8bit.bitwarden.data.platform.repository.util.FakeEnvironmentRepository
import com.x8bit.bitwarden.data.tools.generator.repository.GeneratorRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LandingViewModelTest : BaseViewModelTest() {

    private val authRepository: AuthRepository = mockk(relaxed = true) {
        every { ssoCallbackResultFlow } returns emptyFlow()
    }
    private val fakeEnvironmentRepository = FakeEnvironmentRepository()
    private val generatorRepository: GeneratorRepository = mockk(relaxed = true)

    private fun createViewModel(): LandingViewModel = LandingViewModel(
        authRepository = authRepository,
        environmentRepository = fakeEnvironmentRepository,
        generatorRepository = generatorRepository,
        savedStateHandle = SavedStateHandle(),
    )

    @Test
    fun `initial state should have empty server url`() = runTest {
        val viewModel = createViewModel()
        viewModel.stateFlow.test {
            val state = awaitItem()
            assertEquals("", state.serverUrl)
            assertFalse(state.isContinueButtonEnabled)
        }
    }

    @Test
    fun `ServerUrlChange should update server url and enable continue`() = runTest {
        val viewModel = createViewModel()
        viewModel.trySendAction(LandingAction.ServerUrlChange("https://example.com"))
        viewModel.stateFlow.test {
            val state = awaitItem()
            assertEquals("https://example.com", state.serverUrl)
            assertTrue(state.isContinueButtonEnabled)
        }
    }

    @Test
    fun `ContinueButtonClick with blank url should not enable button`() = runTest {
        val viewModel = createViewModel()
        viewModel.stateFlow.test {
            val state = awaitItem()
            assertFalse(state.isContinueButtonEnabled)
        }
    }

    @Test
    fun `DialogDismiss should clear dialog`() = runTest {
        val viewModel = createViewModel()
        viewModel.trySendAction(LandingAction.DialogDismiss)
        viewModel.stateFlow.test {
            val state = awaitItem()
            assertEquals(null, state.dialog)
        }
    }
}
