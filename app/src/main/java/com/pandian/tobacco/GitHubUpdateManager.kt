package com.pandian.tobacco

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseUpdate(
    val tagName: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long
)

object GitHubUpdateManager {
    private val mainHandler = Handler(Looper.getMainLooper())

    val configured: Boolean
        get() = BuildConfig.UPDATE_GITHUB_OWNER.isNotBlank() && BuildConfig.UPDATE_GITHUB_REPO.isNotBlank()

    fun checkForUpdate(callback: (Result<GitHubReleaseUpdate?>) -> Unit) {
        if (!configured) {
            callback(Result.success(null))
            return
        }
        Thread {
            val result = runCatching {
                val endpoint = "https://api.github.com/repos/${BuildConfig.UPDATE_GITHUB_OWNER}/${BuildConfig.UPDATE_GITHUB_REPO}/releases/latest"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", "TobaccoLedger-Android/${BuildConfig.VERSION_NAME}")
                }
                val body = connection.useResponse()
                val json = JSONObject(body)
                val tag = json.optString("tag_name")
                val assets = json.optJSONArray("assets")
                val apk = (0 until (assets?.length() ?: 0))
                    .mapNotNull { assets?.optJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    ?: error("最新 Release 没有 APK 文件")
                if (compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) null else GitHubReleaseUpdate(
                    tagName = tag,
                    title = json.optString("name").ifBlank { tag },
                    notes = json.optString("body"),
                    apkUrl = apk.getString("browser_download_url"),
                    apkSize = apk.optLong("size")
                )
            }
            mainHandler.post { callback(result) }
        }.start()
    }

    fun canInstall(activity: Activity): Boolean = activity.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
        )
    }

    fun download(
        activity: Activity,
        update: GitHubReleaseUpdate,
        onProgress: (Int) -> Unit,
        callback: (Result<File>) -> Unit
    ) {
        Thread {
            val result = runCatching {
                val directory = File(activity.cacheDir, "app_updates").apply { mkdirs() }
                val safeTag = update.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val temporary = File(directory, "smokebao-$safeTag.download")
                val target = File(directory, "smokebao-$safeTag.apk")
                val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "TobaccoLedger-Android/${BuildConfig.VERSION_NAME}")
                }
                connection.connect()
                if (connection.responseCode !in 200..299) error("下载失败：HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.apkSize.takeIf { it > 0 } ?: -1L
                connection.inputStream.use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            if (total > 0) {
                                val progress = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                mainHandler.post { onProgress(progress) }
                            }
                        }
                    }
                }
                if (temporary.length() < 100_000) error("下载的 APK 文件不完整")
                if (target.exists()) target.delete()
                check(temporary.renameTo(target)) { "无法保存更新文件" }
                target
            }
            mainHandler.post { callback(result) }
        }.start()
    }

    fun install(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    private fun compareVersions(remote: String, current: String): Int {
        fun parts(value: String) = Regex("\\d+").findAll(value).map { it.value.toIntOrNull() ?: 0 }.toList()
        val left = parts(remote)
        val right = parts(current)
        repeat(maxOf(left.size, right.size)) { index ->
            val comparison = (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }
        return 0
    }

    private fun HttpURLConnection.useResponse(): String {
        connect()
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        disconnect()
        if (code !in 200..299) error("GitHub 检查失败：HTTP $code")
        return text
    }
}

@Composable
fun GitHubUpdateDialog(activity: Activity, update: GitHubReleaseUpdate, onDismiss: () -> Unit) {
    var downloading by remember(update.tagName) { mutableStateOf(false) }
    var progress by remember(update.tagName) { mutableStateOf(0) }
    var message by remember(update.tagName) { mutableStateOf("") }

    fun beginUpdate() {
        if (!GitHubUpdateManager.canInstall(activity)) {
            message = "请先允许烟收宝安装未知应用，返回后再次点击更新"
            GitHubUpdateManager.openInstallPermission(activity)
            return
        }
        downloading = true
        message = "正在下载更新…"
        GitHubUpdateManager.download(activity, update, { progress = it }) { result ->
            downloading = false
            result.onSuccess { apk ->
                message = "下载完成，正在打开安装界面"
                GitHubUpdateManager.install(activity, apk)
            }.onFailure { error -> message = error.message ?: "更新下载失败" }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("发现新版本 ${update.tagName}", fontSize = 23.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(update.title, fontWeight = FontWeight.SemiBold)
                if (update.notes.isNotBlank()) Text(update.notes.take(800), color = MaterialTheme.colorScheme.secondary)
                if (downloading) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("下载进度 $progress%")
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { Button(onClick = ::beginUpdate, enabled = !downloading) { Text(if (downloading) "下载中" else "立即更新") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !downloading) { Text("稍后再说") } }
    )
}
