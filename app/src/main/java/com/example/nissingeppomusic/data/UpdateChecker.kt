package com.example.nissingeppomusic.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** GitHub Releases を見て、新しいデバッグビルドがあるか確認する */
object UpdateChecker {
    private const val REPO = "sh4tter815/NissinGeppoMusic"
    private const val PREFS = "app_updates"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_DISMISSED = "dismissed_build"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    data class LatestRelease(
        val buildNumber: Int,
        val downloadUrl: String,
        val publishedAtMs: Long,
        val isNewer: Boolean
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 起動時用: 前回チェックから6時間以上空いていて、まだ知らせていない新ビルドがあるときだけ返す */
    suspend fun checkIfDue(context: Context): LatestRelease? {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (now - p.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return null
        p.edit().putLong(KEY_LAST_CHECK, now).apply()
        val latest = fetchLatest(context) ?: return null
        if (!latest.isNewer) return null
        if (p.getInt(KEY_DISMISSED, 0) >= latest.buildNumber) return null
        return latest
    }

    fun markDismissed(context: Context, buildNumber: Int) {
        prefs(context).edit().putInt(KEY_DISMISSED, buildNumber).apply()
    }

    /** 最新のデバッグビルドのリリース情報を返す（手動チェック用、スロットルなし） */
    suspend fun fetchLatest(context: Context): LatestRelease? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$REPO/releases?per_page=10")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "NissinGeppoMusic-UpdateChecker")
            }
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            val releases = JSONArray(body)
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val installedAt = try {
                context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            } catch (e: Exception) {
                0L
            }
            for (i in 0 until releases.length()) {
                val rel = releases.getJSONObject(i)
                val tag = rel.optString("tag_name")
                if (!tag.startsWith("debug-build-")) continue
                val num = tag.removePrefix("debug-build-").toIntOrNull() ?: continue
                val assets = rel.getJSONArray("assets")
                var dl: String? = null
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name") == "app-debug.apk") {
                        dl = a.optString("browser_download_url")
                        break
                    }
                }
                val downloadUrl = dl ?: continue
                val published = try {
                    fmt.parse(rel.optString("published_at"))?.time ?: 0L
                } catch (e: Exception) {
                    0L
                }
                val newer = published > 0L && published > installedAt
                return@withContext LatestRelease(num, downloadUrl, published, newer)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** APKをキャッシュにダウンロードする。進捗は0f〜1f（総サイズ不明なら呼ばれない） */
    suspend fun downloadApk(context: Context, info: LatestRelease, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "app-debug.apk")
                val conn = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "NissinGeppoMusic-UpdateChecker")
                }
                if (conn.responseCode !in 200..299) return@withContext null
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress(read.toFloat() / total.toFloat())
                        }
                    }
                }
                out
            } catch (e: Exception) {
                null
            }
        }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
