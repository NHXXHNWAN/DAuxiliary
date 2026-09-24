package com.dauxiliary.ui.widgets

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Manage : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object About : AppRoute
}
