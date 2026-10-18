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
    private val aliases: Set<String> = emptySet(),
) {
    DOUYIN("com.ss.android.ugc.aweme", "抖音"),
    WECHAT("com.tencent.mm", "微信"),
    QQ("com.tencent.mobileqq", "QQ"),
    TELEGRAM(
        packageName = "org.telegram.messenger",
        displayName = "Telegram",

        aliases = setOf(
            // Telegram 官方发行版
            "org.telegram.messenger.web",
            // Nagram / NagramX 系列
            "fork.risin42.nagramx",
            "nu.gpu.nagram",
            "nu.gpu.nagramx",
            "nu.gpu.nagram.web",
            // 其他 Telegram-Android fork
            "com.exteraless.app",
            "xyz.nextalone.nagram",
            "tw.nekomimi.nekogram",
            "it.belloworld.mercurygram",
        ),
    ),
    ;

    fun matchesPackage(packageName: String): Boolean =
        packageName == this.packageName || packageName in aliases

    fun aliasesForListing(): Set<String> = aliases

    fun matchesProcess(processName: String): Boolean =
        matchesPackage(processName.substringBefore(':'))

    companion object {
        fun fromPackageName(packageName: String): AppTarget? =
            entries.firstOrNull { it.matchesPackage(packageName) }
    }
}
