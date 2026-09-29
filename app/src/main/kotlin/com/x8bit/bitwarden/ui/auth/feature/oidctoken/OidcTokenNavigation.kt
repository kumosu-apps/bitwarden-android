package com.x8bit.bitwarden.ui.auth.feature.oidctoken

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.toRoute
import com.bitwarden.ui.platform.base.util.composableWithSlideTransitions
import kotlinx.serialization.Serializable

@Serializable
data class OidcTokenRoute(
    val tokenInfoJson: String,
)

data class OidcTokenArgs(val tokenInfoJson: String)

fun SavedStateHandle.toOidcTokenArgs(): OidcTokenArgs {
    val route = this.toRoute<OidcTokenRoute>()
    return OidcTokenArgs(tokenInfoJson = route.tokenInfoJson)
}

fun NavController.navigateToOidcToken(
    tokenInfoJson: String,
    navOptions: NavOptions? = null,
) {
    this.navigate(
        route = OidcTokenRoute(tokenInfoJson = tokenInfoJson),
        navOptions = navOptions,
    )
}

fun NavGraphBuilder.oidcTokenDestination(
    onNavigateBack: () -> Unit,
) {
    composableWithSlideTransitions<OidcTokenRoute> {
        OidcTokenScreen(onNavigateBack = onNavigateBack)
    }
}
