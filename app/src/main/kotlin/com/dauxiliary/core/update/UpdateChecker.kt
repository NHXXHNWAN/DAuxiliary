package com.dauxiliary.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Release information fetched from the project's public GitHub repository. */
data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseUrl: String,
)

object UpdateChecker {
    private const val RELEASES_API =
        "https://api.github.com/repos/NHXXHNWAN/DAuxiliary/releases/latest"
    private const val USER_AGENT = "DAuxiliary-UpdateChecker"

    /** Returns an update only when the latest release is newer than the installed version. */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@runCatching null

                val tag = release.optString("tag_name").trim()
                val latestVersion = normalize(tag)
                if (latestVersion.isBlank() || compareVersions(latestVersion, currentVersion) <= 0) {
                    return@runCatching null
                }

                val assets = release.optJSONArray("assets")
                val apkUrl = buildList {
                    if (assets != null) {
                        for (index in 0 until assets.length()) {
                            val asset = assets.optJSONObject(index) ?: continue
                            val name = asset.optString("name")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                add(asset.optString("browser_download_url"))
                            }
                        }
                    }
                }.firstOrNull { it.isNotBlank() }

                UpdateInfo(
                    currentVersion = normalize(currentVersion),
                    latestVersion = latestVersion,
                    releaseNotes = release.optString("body").trim()
                        .ifBlank { release.optString("name").trim() }
                        .ifBlank { "该版本暂无更新说明。" },
                    downloadUrl = apkUrl ?: release.optString("html_url"),
                    releaseUrl = release.optString("html_url"),
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun normalize(version: String): String =
        version.trim().removePrefix("v").removePrefix("V")

    /** Compares numeric version components while tolerating v-prefixed release tags. */
    private fun compareVersions(left: String, right: String): Int {
        val leftParts = numericParts(left)
        val rightParts = numericParts(right)
        val count = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until count) {
            val comparison = (leftParts.getOrElse(index) { 0L })
                .compareTo(rightParts.getOrElse(index) { 0L })
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun numericParts(version: String): List<Long> =
        normalize(version)
            .split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .map { it.toLongOrNull() ?: 0L }
}
