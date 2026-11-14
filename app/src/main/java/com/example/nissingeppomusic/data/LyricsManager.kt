package com.example.nissingeppomusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** 歌詞の1行（同期歌詞用にタイムスタンプ付き） */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * 同期歌詞の取得まわり。
 * 優先順位: 音源と同じ場所の .lrc ファイル → 端末内キャッシュ → LRCLIB（lrclib.net、無料・キー不要）
 * 取得できた同期歌詞は端末内にキャッシュして、次回からは通信しない。
 */
object LyricsManager {

    private val timestampRegex = Regex("""\[(\d+):(\d+)(?:\.(\d{1,3}))?\]""")

    /** LRC形式テキストをパースする。タイムスタンプのない行は無視する */
    fun parseLrc(lrcText: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        for (rawLine in lrcText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val matches = timestampRegex.findAll(line).toList()
            if (matches.isEmpty()) continue
            val text = line.replace(timestampRegex, "").trim()
            if (text.isEmpty()) continue
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: continue
                val sec = m.groupValues[2].toLongOrNull() ?: continue
                val fracRaw = m.groupValues[3]
                val fracMs = when (fracRaw.length) {
                    0 -> 0L
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                    else -> (fracRaw.toLongOrNull() ?: 0L)
                }
                result.add(LyricLine(min * 60_000L + sec * 1_000L + fracMs, text))
            }
        }
        return result.sortedBy { it.timeMs }
    }

    private fun cacheKey(title: String, artist: String, album: String): String {
        val raw = "$title|$artist|$album"
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cacheFile(context: Context, title: String, artist: String, album: String): File {
        val dir = File(context.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, cacheKey(title, artist, album) + ".lrc")
    }

    private fun httpGetJson(urlString: String): JSONObject? {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "NissinGeppoMusic (personal music player)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                JSONObject(body)
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun httpGetJsonArray(urlString: String): org.json.JSONArray? {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "NissinGeppoMusic (personal music player)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                org.json.JSONArray(body)
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** ヘッダー付きでテキストを取得する（NetEase / Kugou 用） */
    private fun httpGetText(urlString: String, userAgent: String, referer: String? = null): String? {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", userAgent)
            if (!referer.isNullOrBlank()) {
                connection.setRequestProperty("Referer", referer)
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 同期歌詞を取得する。見つからなければ null。
     * filePath が実ファイルパスのときは、同フォルダの同名 .lrc も探す。
     */
    suspend fun getSyncedLyrics(
        context: Context,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        filePath: String?
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        // 1) 音源と同じ場所の .lrc ファイル
        if (!filePath.isNullOrBlank() && filePath.startsWith("/")) {
            val lrcFile = File(filePath.substringBeforeLast('.') + ".lrc")
            if (lrcFile.exists()) {
                val parsed = try {
                    parseLrc(lrcFile.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    emptyList()
                }
                if (parsed.isNotEmpty()) return@withContext parsed
            }
        }

        // 2) 端末内キャッシュ
        val cache = cacheFile(context, title, artist, album)
        if (cache.exists()) {
            val parsed = try {
                parseLrc(cache.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                emptyList()
            }
            if (parsed.isNotEmpty()) return@withContext parsed
        }

        if (title.isBlank()) return@withContext null

        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

        // 3) LRCLIB /api/get（タイトル・アーティスト・アルバム・長さで照合）
        val durationSec = if (durationMs > 0) durationMs / 1000 else 0
        val getUrl = "https://lrclib.net/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}" +
            "&album_name=${enc(album)}&duration=$durationSec"
        val getJson = httpGetJson(getUrl)
        val synced = getJson?.optString("syncedLyrics", "")?.takeIf { it.isNotBlank() }
        if (synced != null) {
            val parsed = parseLrc(synced)
            if (parsed.isNotEmpty()) {
                try {
                    cache.writeText(synced, Charsets.UTF_8)
                } catch (e: Exception) {
                    // キャッシュ保存失敗は無視する
                }
                return@withContext parsed
            }
        }

        // 4) LRCLIB /api/search フォールバック（候補の中で曲の長さが一番近いものを選ぶ）
        val searchUrl = "https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"
        var bestText: String? = null
        var bestDiffSec = Long.MAX_VALUE
        fun consider(results: org.json.JSONArray?, requireCloseDuration: Boolean) {
            if (results == null) return
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val candidate = item.optString("syncedLyrics", "").takeIf { it.isNotBlank() } ?: continue
                val candDurSec = item.optLong("duration", 0L)
                val diff = if (durationSec > 0 && candDurSec > 0) {
                    kotlin.math.abs(candDurSec - durationSec)
                } else {
                    Long.MAX_VALUE - 1
                }
                // 曲名だけで引いた場合は、長さが近い候補だけを採用する（別曲の歌詞を掴まないため）
                if (requireCloseDuration && diff > 4) continue
                if (bestText == null || diff < bestDiffSec) {
                    bestText = candidate
                    bestDiffSec = diff
                }
            }
        }
        consider(httpGetJsonArray(searchUrl), requireCloseDuration = false)
        if (bestText == null) {
            // アーティスト名の表記ゆれ（日本語／ローマ字など）で空振りした場合は曲名だけで引き直す
            val titleOnlyUrl = "https://lrclib.net/api/search?track_name=${enc(title)}"
            consider(httpGetJsonArray(titleOnlyUrl), requireCloseDuration = true)
        }
        val chosen = bestText
        if (chosen != null) {
            val parsed = parseLrc(chosen)
            if (parsed.isNotEmpty()) {
                try {
                    cache.writeText(chosen, Charsets.UTF_8)
                } catch (e: Exception) {
                    // キャッシュ保存失敗は無視する
                }
                return@withContext parsed
            }
        }

        // 5) NetEase（网易云）: 中国系サービスで、ボカロ曲の同期歌詞が比較的厚い
        val neteaseLrc = try {
            fetchNetEaseLrc(title, artist, durationMs)
        } catch (e: Exception) {
            null
        }
        if (!neteaseLrc.isNullOrBlank()) {
            val parsed = parseLrc(neteaseLrc)
            if (parsed.isNotEmpty()) {
                try {
                    cache.writeText(neteaseLrc, Charsets.UTF_8)
                } catch (e: Exception) {
                    // キャッシュ保存失敗は無視する
                }
                return@withContext parsed
            }
        }

        // 6) Kugou（酷狗）: さらにマニアックな曲の候補
        val kugouLrc = try {
            fetchKugouLrc(title, artist, durationMs)
        } catch (e: Exception) {
            null
        }
        if (!kugouLrc.isNullOrBlank()) {
            val parsed = parseLrc(kugouLrc)
            if (parsed.isNotEmpty()) {
                try {
                    cache.writeText(kugouLrc, Charsets.UTF_8)
                } catch (e: Exception) {
                    // キャッシュ保存失敗は無視する
                }
                return@withContext parsed
            }
        }

        null
    }

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    /** NetEase の非公式Web APIから同期歌詞（LRCテキスト）を取得する。見つからなければ null */
    private fun fetchNetEaseLrc(title: String, artist: String, durationMs: Long): String? {
        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        val keyword = if (artist.isBlank()) title else "$title $artist"
        val searchUrl = "https://music.163.com/api/search/get/web?csrf_token=&type=1" +
            "&s=${enc(keyword)}&limit=8&offset=0"
        val searchText = httpGetText(searchUrl, desktopUa, "https://music.163.com/") ?: return null
        val songs = try {
            JSONObject(searchText).optJSONObject("result")?.optJSONArray("songs")
        } catch (e: Exception) {
            null
        } ?: return null

        // 曲の長さが近い候補を最大3件まで試す
        val candidateIds = mutableListOf<Long>()
        var fallbackId = -1L
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val id = song.optLong("id", -1L)
            if (id <= 0) continue
            if (fallbackId < 0) fallbackId = id
            val songDurMs = song.optLong("duration", 0L)
            if (durationMs > 0 && songDurMs > 0 && kotlin.math.abs(songDurMs - durationMs) <= 4000) {
                candidateIds.add(id)
            }
        }
        if (candidateIds.isEmpty() && fallbackId > 0) candidateIds.add(fallbackId)

        for (id in candidateIds.take(3)) {
            val lyricUrl = "https://music.163.com/api/song/lyric?id=$id&lv=1&tv=1&rv=1"
            val lyricText = httpGetText(lyricUrl, desktopUa, "https://music.163.com/") ?: continue
            val lrc = try {
                JSONObject(lyricText).optJSONObject("lrc")?.optString("lyric", "")
            } catch (e: Exception) {
                null
            }
            if (!lrc.isNullOrBlank() && parseLrc(lrc).isNotEmpty()) return lrc
        }
        return null
    }

    /** Kugou の非公式APIから同期歌詞（LRCテキスト）を取得する。見つからなければ null */
    private fun fetchKugouLrc(title: String, artist: String, durationMs: Long): String? {
        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        val keyword = if (artist.isBlank()) title else "$title $artist"
        val searchUrl = "http://mobilecdn.kugou.com/api/v3/search/song?format=json" +
            "&keyword=${enc(keyword)}&page=1&pagesize=5&showtype=1"
        val searchText = httpGetText(searchUrl, desktopUa) ?: return null
        val infos = try {
            JSONObject(searchText).optJSONObject("data")?.optJSONArray("info")
        } catch (e: Exception) {
            null
        } ?: return null

        // 曲の長さが近い候補を優先して最大3件まで試す
        val hashes = mutableListOf<Pair<String, Long>>()
        for (i in 0 until infos.length()) {
            val info = infos.optJSONObject(i) ?: continue
            val hash = info.optString("hash", "")
            if (hash.isBlank()) continue
            val durSec = info.optLong("duration", 0L)
            hashes.add(hash to durSec)
        }
        val ordered = if (durationMs > 0) {
            hashes.sortedBy { (kotlin.math.abs(it.second * 1000 - durationMs)) }
        } else {
            hashes
        }

        for ((hash, durSec) in ordered.take(3)) {
            val durMs = if (durSec > 0) durSec * 1000 else durationMs
            val candUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
                "&keyword=${enc(title)}&duration=$durMs&hash=$hash"
            val candText = httpGetText(candUrl, desktopUa) ?: continue
            val candidates = try {
                JSONObject(candText).optJSONArray("candidates")
            } catch (e: Exception) {
                null
            } ?: continue
            if (candidates.length() == 0) continue
            val first = candidates.optJSONObject(0) ?: continue
            val candId = first.optString("id", "")
            val accessKey = first.optString("accesskey", "")
            if (candId.isBlank() || accessKey.isBlank()) continue
            val dlUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$candId" +
                "&accesskey=$accessKey&fmt=lrc&charset=utf8"
            val dlText = httpGetText(dlUrl, desktopUa) ?: continue
            val contentB64 = try {
                JSONObject(dlText).optString("content", "")
            } catch (e: Exception) {
                null
            }
            if (contentB64.isNullOrBlank()) continue
            val lrc = try {
                String(android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
            if (!lrc.isNullOrBlank() && parseLrc(lrc).isNotEmpty()) return lrc
        }
        return null
    }
}
