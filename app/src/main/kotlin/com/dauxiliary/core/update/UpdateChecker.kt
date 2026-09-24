package com.dauxiliary.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val channel: UpdateChannel,
)

enum class UpdateChannel(val label: String) {
    STABLE("稳定版"),
    TEST("测试版"),
}

object UpdateChecker {
    private const val REPOSITORY = "NHXXHNWAN/DAuxiliary"
    private const val RELEASES_API = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    private const val TEST_RELEASES_API = "https://api.github.com/repos/$REPOSITORY/releases?per_page=100"
    private const val TEST_RELEASES_URL = "https://github.com/$REPOSITORY/releases?q=test-"
    private const val USER_AGENT = "DAuxiliary-UpdateChecker"

    suspend fun check(currentVersion: String, channel: UpdateChannel): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                when (channel) {
                    UpdateChannel.STABLE -> checkStable(currentVersion)
                    UpdateChannel.TEST -> checkTest(currentVersion)
                }
            }.getOrNull()
        }

    private fun checkStable(currentVersion: String): UpdateInfo? {
        val release = request(RELEASES_API) ?: return null
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val latestVersion = normalize(release.optString("tag_name"))
        if (latestVersion.isBlank() || compareVersions(latestVersion, currentVersion) <= 0) return null
        return UpdateInfo(
            currentVersion = normalize(currentVersion),
            latestVersion = latestVersion,
            releaseNotes = release.optString("body").trim()
                .ifBlank { release.optString("name").trim() }
                .ifBlank { "该版本暂无更新说明。" },
            downloadUrl = findApkUrl(release) ?: release.optString("html_url"),
            releaseUrl = release.optString("html_url"),
            channel = UpdateChannel.STABLE,
        )
    }

    /** Test builds are published as prereleases with the test- tag prefix. */
    private fun checkTest(currentVersion: String): UpdateInfo? {
        val releases = requestArray(TEST_RELEASES_API) ?: return null
        val release = (0 until releases.length())
            .mapNotNull { releases.optJSONObject(it) }
            .filter { !it.optBoolean("draft") }
            .filter { it.optString("tag_name").startsWith("test-", ignoreCase = true) }
            .maxWithOrNull(compareBy { releaseVersion(it) })
            ?: return null
        val latestVersion = normalize(release.optString("tag_name").removePrefix("test-").removePrefix("TEST-"))
        if (latestVersion.isBlank() || compareVersions(latestVersion, currentVersion) <= 0) return null
        val releaseUrl = release.optString("html_url").ifBlank { TEST_RELEASES_URL }
        return UpdateInfo(
            currentVersion = normalize(currentVersion),
            latestVersion = latestVersion,
            releaseNotes = compactNotes(release.optString("body"), "测试版构建。"),
            downloadUrl = findApkUrl(release) ?: releaseUrl,
            releaseUrl = releaseUrl,
            channel = UpdateChannel.TEST,
        )
    }

    private fun releaseVersion(release: JSONObject): String =
        normalize(release.optString("tag_name").removePrefix("test-").removePrefix("TEST-"))

    private fun compactNotes(body: String, fallback: String): String = body
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .take(3)
        .joinToString("\n")
        .ifBlank { fallback }

    private fun findApkUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun requestArray(url: String): JSONArray? {
        val connection = openConnection(url)
        return try {
            if (connection.responseCode !in 200..299) null
            else JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun request(url: String): JSONObject? {
        val connection = openConnection(url)
        return try {
            if (connection.responseCode !in 200..299) null
            else JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun normalize(version: String): String =
        version.trim().removePrefix("v").removePrefix("V")

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