package com.dauxiliary.core.registry

/**
 * Applications that can host DAuxiliary integrations.
 *
 * Keeping the package list outside the Xposed entry makes adding another host a
 * data/configuration change instead of another hard-coded branch in the loader.
 */
enum class AppTarget(
    val packageName: String,
    val displayName: String,
) {
    DOUYIN("com.ss.android.ugc.aweme", "抖音"),
    WECHAT("com.tencent.mm", "微信"),
    QQ("com.tencent.mobileqq", "QQ"),
    ;

    companion object {
        fun fromPackageName(packageName: String): AppTarget? =
            entries.firstOrNull { it.packageName == packageName }
    }
}
