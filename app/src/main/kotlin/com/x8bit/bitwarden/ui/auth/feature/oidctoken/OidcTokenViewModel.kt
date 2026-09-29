package com.x8bit.bitwarden.ui.auth.feature.oidctoken

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bitwarden.ui.platform.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

private const val KEY_STATE = "state"

@HiltViewModel
class OidcTokenViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : BaseViewModel<OidcTokenState, OidcTokenEvent, OidcTokenAction>(
    initialState = savedStateHandle[KEY_STATE]
        ?: OidcTokenState(
            tokenInfo = savedStateHandle.toOidcTokenArgs().tokenInfoJson,
        ),
) {

    init {
        stateFlow
            .onEach { savedStateHandle[KEY_STATE] = it }
            .launchIn(viewModelScope)
    }

    override fun handleAction(action: OidcTokenAction) {
        when (action) {
            OidcTokenAction.CloseClick -> sendEvent(OidcTokenEvent.NavigateBack)
        }
    }
}

@Parcelize
data class OidcTokenState(
    val tokenInfo: String,
) : Parcelable

sealed class OidcTokenEvent {
    data object NavigateBack : OidcTokenEvent()
}

sealed class OidcTokenAction {
    data object CloseClick : OidcTokenAction()
}
