package com.dauxiliary.admin

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
internal sealed interface AdminRoute : NavKey {
    @Serializable
    data object Dashboard : AdminRoute

    @Serializable
    data object Connection : AdminRoute
}
