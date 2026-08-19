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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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

private class UpdateDownloadCancelledException : Exception("已取消下载")

class UpdateDownloadTask {
    @Volatile private var cancelled = false
    @Volatile private var connection: HttpURLConnection? = null

    fun cancel() {
        cancelled = true
        connection?.disconnect()
    }

    internal fun bind(connection: HttpURLConnection?) {
        this.connection = connection
        if (cancelled) connection?.disconnect()
    }

    internal fun checkCancelled() {
        if (cancelled) throw UpdateDownloadCancelledException()
    }
}

data class GitHubReleaseUpdate(
    val tagName: String,
    val title: String,
    val notes: String,
    val assetApiUrl: String,
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
                    notes = json.optString("body").replace("\\r\\n", "\n").replace("\\n", "\n"),
                    assetApiUrl = apk.getString("url"),
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
        onStatus: (String) -> Unit,
        callback: (Result<File>) -> Unit
    ): UpdateDownloadTask {
        val task = UpdateDownloadTask()
        Thread {
            var lastError: Throwable = IllegalStateException("更新下载失败")
            for (attempt in 1..3) {
                try {
                    task.checkCancelled()
                    postStatus(onStatus, if (attempt == 1) "正在连接 GitHub 更新服务…" else "正在进行第 $attempt 次下载…")
                    val file = downloadOnce(activity, update, task, onProgress, onStatus)
                    task.bind(null)
                    mainHandler.post { callback(Result.success(file)) }
                    return@Thread
                } catch (cancelled: UpdateDownloadCancelledException) {
                    task.bind(null)
                    mainHandler.post { callback(Result.failure(cancelled)) }
                    return@Thread
                } catch (error: Throwable) {
                    lastError = error
                    task.bind(null)
                    if (attempt < 3) {
                        postStatus(onStatus, "连接失败，正在自动重试（${attempt + 1}/3）…")
                        try {
                            repeat(15) { task.checkCancelled(); Thread.sleep(100) }
                        } catch (cancelled: UpdateDownloadCancelledException) {
                            mainHandler.post { callback(Result.failure(cancelled)) }
                            return@Thread
                        }
                    }
                }
            }
            mainHandler.post { callback(Result.failure(lastError)) }
        }.start()
        return task
    }

    private fun downloadOnce(
        activity: Activity,
        update: GitHubReleaseUpdate,
        task: UpdateDownloadTask,
        onProgress: (Int) -> Unit,
        onStatus: (String) -> Unit
    ): File {
        val directory = File(activity.cacheDir, "app_updates").apply { mkdirs() }
        val safeTag = update.tagName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val temporary = File(directory, "smokebao-$safeTag.download")
        val target = File(directory, "smokebao-$safeTag.apk")
        if (temporary.exists()) temporary.delete()
        val connection = openAssetConnection(update, task, onStatus)
        task.checkCancelled()
        postStatus(onStatus, "已连接附件 CDN，正在下载…")
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.apkSize.takeIf { it > 0 } ?: -1L
        connection.inputStream.use { input ->
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                while (true) {
                    task.checkCancelled()
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
        connection.disconnect()
        task.bind(null)
        if (temporary.length() < 100_000) error("下载的 APK 文件不完整")
        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) { "无法保存更新文件" }
        return target
    }

    private fun openAssetConnection(
        update: GitHubReleaseUpdate,
        task: UpdateDownloadTask,
        onStatus: (String) -> Unit
    ): HttpURLConnection {
        var currentUrl = update.assetApiUrl.ifBlank { update.apkUrl }
        repeat(6) {
            task.checkCancelled()
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 12_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "TobaccoLedger-Android/${BuildConfig.VERSION_NAME}")
                if (currentUrl.startsWith("https://api.github.com/")) {
                    setRequestProperty("Accept", "application/octet-stream")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
            }
            task.bind(connection)
            connection.connect()
            val code = connection.responseCode
            if (code in 200..299) return connection
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: error("附件跳转地址缺失")
                connection.disconnect()
                task.bind(null)
                currentUrl = URL(URL(currentUrl), location).toString()
                postStatus(onStatus, "正在跳转 GitHub 附件 CDN…")
            } else {
                connection.disconnect()
                task.bind(null)
                error("下载失败：HTTP $code")
            }
        }
        error("GitHub 附件跳转次数过多")
    }

    private fun postStatus(callback: (String) -> Unit, value: String) {
        mainHandler.post { callback(value) }
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
    var failed by remember(update.tagName) { mutableStateOf(false) }
    var downloadTask by remember(update.tagName) { mutableStateOf<UpdateDownloadTask?>(null) }
    val latestDownloadTask by rememberUpdatedState(downloadTask)

    DisposableEffect(Unit) {
        onDispose { latestDownloadTask?.cancel() }
    }

    fun beginUpdate() {
        if (!GitHubUpdateManager.canInstall(activity)) {
            message = "请先允许烟收宝安装未知应用，返回后再次点击更新"
            GitHubUpdateManager.openInstallPermission(activity)
            return
        }
        downloading = true
        failed = false
        progress = 0
        message = "正在连接 GitHub 更新服务…"
        downloadTask = GitHubUpdateManager.download(activity, update, { progress = it }, { message = it }) { result ->
            downloading = false
            downloadTask = null
            result.onSuccess { apk ->
                message = "下载完成，正在打开安装界面"
                GitHubUpdateManager.install(activity, apk)
            }.onFailure { error ->
                failed = error !is UpdateDownloadCancelledException
                progress = 0
                message = if (error is UpdateDownloadCancelledException) "已取消下载" else "${error.message ?: "更新下载失败"}，可以重新下载"
            }
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
                    if (progress > 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(if (progress > 0) "下载进度 $progress%" else "正在建立连接…")
                }
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(onClick = { if (downloading) downloadTask?.cancel() else beginUpdate() }) {
                Text(if (downloading) "取消下载" else if (failed) "重新下载" else "立即更新")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !downloading) { Text("稍后再说") } }
    )
}
