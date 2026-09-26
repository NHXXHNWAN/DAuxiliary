package com.dauxiliary.core.update
import android.util.Log
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
    DISABLED("不检查更新"),
}

object UpdateChecker {
    private const val REPOSITORY = "NHXXHNWAN/DAuxiliary"
    private const val RELEASES_API = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    private const val TEST_RELEASES_API = "https://api.github.com/repos/$REPOSITORY/releases?per_page=100"
    private const val TEST_RELEASES_URL = "https://github.com/$REPOSITORY/releases?q=test-"
    private const val USER_AGENT = "DAuxiliary-UpdateChecker"

    suspend fun check(currentVersion: String, channel: UpdateChannel): UpdateInfo? =
        withContext(Dispatchers.IO) {
            if (channel == UpdateChannel.DISABLED) return@withContext null
            runCatching {
                when (channel) {
                    UpdateChannel.STABLE -> checkStable(currentVersion)
                    UpdateChannel.TEST -> checkTest(currentVersion)
                    UpdateChannel.DISABLED -> null
                }
            }.getOrNull()
        }

    private fun checkStable(currentVersion: String): UpdateInfo? {
        val release = request(RELEASES_API) ?: return null
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val latestVersion = normalize(release.optString("tag_name"))
        val apkUrl = findApkUrl(release) ?: return null
        if (latestVersion.isBlank() || compareVersions(latestVersion, currentVersion) <= 0) return null
        return UpdateInfo(
            currentVersion = normalize(currentVersion),
            latestVersion = latestVersion,
            releaseNotes = release.optString("body").trim()
                .ifBlank { release.optString("name").trim() }
                .ifBlank { "该版本暂无更新说明。" },
            downloadUrl = apkUrl,
            releaseUrl = release.optString("html_url"),
            channel = UpdateChannel.STABLE,
        )
    }

    /** Test channel checks both automatic Test prereleases and the latest stable release. */
    private fun checkTest(currentVersion: String): UpdateInfo? {
        val candidates = mutableListOf<Pair<JSONObject, UpdateChannel>>()
        requestArray(TEST_RELEASES_API)?.let { releases ->
            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                if (!release.optBoolean("draft") && release.optString("tag_name")
                        .startsWith("test-", ignoreCase = true)
                ) {
                    candidates += release to UpdateChannel.TEST
                }
            }
        }
        request(RELEASES_API)?.let { release ->
            if (!release.optBoolean("draft") && !release.optBoolean("prerelease")) {
                candidates += release to UpdateChannel.STABLE
            }
        }
        val selected = candidates.maxWithOrNull(compareBy { releaseVersion(it.first) }) ?: return null
        val release = selected.first
        val channel = selected.second
        val latestVersion = releaseVersion(release)
        if (latestVersion.isBlank() || compareVersions(latestVersion, currentVersion) <= 0) return null
        val apkUrl = findApkUrl(release) ?: return null
        val releaseUrl = release.optString("html_url").ifBlank { TEST_RELEASES_URL }
        return UpdateInfo(
            currentVersion = normalize(currentVersion),
            latestVersion = latestVersion,
            releaseNotes = compactNotes(
                release.optString("body"),
                if (channel == UpdateChannel.TEST) "测试版构建。" else "正式版发布。",
            ),
            downloadUrl = apkUrl,
            releaseUrl = releaseUrl,
            channel = channel,
        )
    }

    private fun releaseVersion(release: JSONObject): String {
        val tag = release.optString("tag_name")
            .removePrefix("test-")
            .removePrefix("TEST-")
        // Keep the workflow run suffix because the APK versionName contains it.
        // Only remove the trailing commit SHA from test tags.
        val version = if (tag.matches(Regex(".*-[0-9a-fA-F]{7}$"))) {
            tag.substringBeforeLast("-")
        } else {
            tag
        }
        return normalize(version)
    }

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
            useCaches = false
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Cache-Control", "no-cache")
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