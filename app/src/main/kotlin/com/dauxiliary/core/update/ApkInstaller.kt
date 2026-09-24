package com.dauxiliary.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ApkInstaller {
    suspend fun downloadAndInstall(context: Context, downloadUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val tempFile = File(updateDir, "dauxiliary-update.apk.part")
                val apkFile = File(updateDir, "dauxiliary-update.apk")
                tempFile.delete()
                apkFile.delete()
                download(downloadUrl, tempFile)
                check(tempFile.renameTo(apkFile)) { "无法保存更新包" }

                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        !context.packageManager.canRequestPackageInstalls()
                    ) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        Toast.makeText(context, "请允许安装未知应用后重新点击更新", Toast.LENGTH_LONG).show()
                    } else {
                        val apkUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            apkFile,
                        )
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(apkUri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                        )
                    }
                }
                true
            }.getOrElse { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "更新下载失败：${error.message ?: "网络错误"}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                false
            }
        }

    private fun download(downloadUrl: String, target: File) {
        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DAuxiliary-Updater")
            setRequestProperty("Accept", "application/vnd.android.package-archive")
        }
        try {
            check(connection.responseCode in 200..299) { "服务器返回 ${connection.responseCode}" }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }
}