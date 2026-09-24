package com.dauxiliary.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    private const val TEST_COMMIT_API = "https://api.github.com/repos/$REPOSITORY/commits/Test"
    private const val TEST_BRANCH_URL = "https://github.com/$REPOSITORY/tree/Test"
    private const val USER_AGENT = "DAuxiliary-UpdateChecker"
    private val versionFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        .withZone(ZoneId.of("Asia/Shanghai"))

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

    /** Test builds use the latest Test commit timestamp because Test pushes do not publish Releases. */
    private fun checkTest(currentVersion: String): UpdateInfo? {
        val commit = request(TEST_COMMIT_API) ?: return null
        val commitTime = commit.optJSONObject("commit")
            ?.optJSONObject("committer")
            ?.optString("date")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val latestVersion = runCatching {
            versionFormatter.format(Instant.parse(commitTime))
        }.getOrNull() ?: return null
        if (compareVersions(latestVersion, currentVersion) <= 0) return null

        val message = commit.optJSONObject("commit")
            ?.optString("message")
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
            .ifBlank { "Test 分支最新构建。" }
        val commitUrl = commit.optString("html_url").ifBlank { TEST_BRANCH_URL }
        return UpdateInfo(
            currentVersion = normalize(currentVersion),
            latestVersion = latestVersion,
            releaseNotes = "Test 分支：$message",
            // Test pushes only upload Actions artifacts, so link to the exact commit page.
            downloadUrl = commitUrl,
            releaseUrl = commitUrl,
            channel = UpdateChannel.TEST,
        )
    }

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

    private fun request(url: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
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