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

    /** 簡体字・繁体字を日本の漢字（新字体）に寄せる変換表。中国ソースの歌詞の見た目を日本の表記にそろえる */
    private val kanjiConvertMap: Map<Char, Char> = mapOf(
        '编' to '編', '词' to '詞', '爱' to '愛', '乐' to '楽', '声' to '声', '风' to '風', '云' to '雲',
        '龙' to '竜', '马' to '馬', '车' to '車', '门' to '門', '开' to '開', '关' to '関',
        '时' to '時', '间' to '間', '线' to '線', '红' to '紅', '绿' to '緑', '蓝' to '藍',
        '银' to '銀', '铁' to '鉄', '飞' to '飛', '鸟' to '鳥', '鱼' to '魚', '长' to '長',
        '发' to '発', '后' to '後', '过' to '過', '还' to '還', '这' to '這', '个' to '個',
        '们' to '們', '为' to '為', '对' to '対', '说' to '説', '读' to '読', '听' to '聴',
        '买' to '買', '卖' to '売', '现' to '現', '见' to '見', '觉' to '覚', '远' to '遠',
        '进' to '進', '运' to '運', '动' to '動', '气' to '気', '压' to '圧', '电' to '電',
        '视' to '視', '圆' to '円', '团' to '団', '园' to '園', '图' to '図', '书' to '書',
        '贝' to '貝', '页' to '頁', '阳' to '陽', '阴' to '陰', '梦' to '夢', '伤' to '傷',
        '泪' to '涙', '让' to '譲', '给' to '給', '话' to '話', '语' to '語', '调' to '調',
        '谁' to '誰', '岁' to '歳', '尘' to '塵', '净' to '浄', '飘' to '飄', '摇' to '揺',
        '摆' to '擺', '脸' to '顔', '湿' to '湿', '干' to '乾', '挂' to '掛', '牵' to '牽',
        '怀' to '懷', '转' to '転', '轮' to '輪', '响' to '響', '应' to '応', '传' to '伝',
        '统' to '統', '续' to '続', '烧' to '焼', '毁' to '毀', '灭' to '滅', '烟' to '煙',
        '雾' to '霧', '凉' to '涼', '热' to '熱', '闷' to '悶', '恼' to '悩', '烦' to '煩',
        '忧' to '憂', '欢' to '歓', '惊' to '驚', '吓' to '嚇', '怜' to '憐', '义' to '義',
        '节' to '節', '钟' to '鐘', '针' to '針', '钱' to '銭', '铜' to '銅', '铅' to '鉛',
        '镜' to '鏡', '锁' to '鎖', '窗' to '窓', '户' to '戸', '桥' to '橋', '墙' to '壁',
        '弹' to '弾', '诗' to '詩', '谱' to '譜', '韵' to '韻', '愿' to '願', '够' to '夠',
        '麼' to '麼', '來' to '来', '說' to '説', '話' to '話', '語' to '語', '樂' to '楽',
        '聲' to '声', '聽' to '聴', '寫' to '写', '讀' to '読', '覺' to '覚', '氣' to '気',
        '壓' to '圧', '畫' to '画', '圓' to '円', '團' to '団', '圖' to '図', '淚' to '涙',
        '讓' to '譲', '鐵' to '鉄', '髮' to '髪', '臉' to '顔', '綠' to '緑', '關' to '関',
        '時' to '時', '紅' to '紅', '愛' to '愛', '夢' to '夢', '傷' to '傷', '還' to '還',
        '過' to '過', '個' to '個', '為' to '為', '這' to '這', '們' to '們'
    )

    /** 簡体字・繁体字を日本の漢字に変換する */
    private fun toJapaneseKanji(text: String): String {
        if (text.none { kanjiConvertMap.containsKey(it) }) return text
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(kanjiConvertMap[ch] ?: ch)
        return sb.toString()
    }


    /** LRC形式テキストをパースする。タイムスタンプのない行は無視する */
    // インスト曲のダミー歌詞や「歌詞なし」通知の断片（簡体字・繁体字・日本語表記のゆれをまとめて拾う）
    private val PLACEHOLDER_LYRIC_MARKERS = listOf(
        "纯音乐", "純音樂", "纯音樂", "請欣賞", "请欣赏", "暫無歌詞", "暂无歌词"
    )

    fun parseLrc(lrcText: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        for (rawLine in lrcText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val matches = timestampRegex.findAll(line).toList()
            if (matches.isEmpty()) continue
            val rawText = line.replace(timestampRegex, "").trim()
            if (rawText.isEmpty()) continue
            // 中国ソースがインスト曲に付けるダミー行（「纯音乐，请欣赏」＝純音楽、ご鑑賞ください）は歌詞ではないので除外する
            if (PLACEHOLDER_LYRIC_MARKERS.any { rawText.contains(it) }) continue
            // 中国ソース由来の簡体字・繁体字は日本の漢字にそろえる（詞：〜等のクレジット行は残す）
            val text = toJapaneseKanji(rawText)
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
        // v5: 照合ロジックを厳しくしたため、旧ロジックで保存された誤った歌詞キャッシュを使わない
        val raw = "v5|$title|$artist|$album"
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** テキストに日本語（ひらがな・カタカナ・漢字）が含まれるか */
    private fun containsJapanese(text: String): Boolean =
        text.any { it in '\u3040'..'\u30FF' || it in '\u4E00'..'\u9FFF' }

    /** 曲名照合用の正規化: 括弧内（ver.表記や feat. 等）を落とし、空白と中点を除いて小文字化する */
    private fun normalizeTitleForMatch(raw: String): String =
        raw.lowercase()
            .replace(Regex("""\(.*?\)|（.*?）|\[.*?\]|【.*?】"""), "")
            .replace(Regex("""\s+"""), "")
            .replace("・", "")
            .trim()

    /** 候補の曲名が目的の曲名と一致するか（正規化後に完全一致 or 相互包含） */
    private fun titleMatches(candidate: String, target: String): Boolean {
        val a = normalizeTitleForMatch(candidate)
        val b = normalizeTitleForMatch(target)
        if (a.isBlank() || b.isBlank()) return false
        return a == b || a.contains(b) || b.contains(a)
    }

    /** 候補のアーティスト名が目的のアーティストと一致するか（表記ゆれ・feat.表記を吸収して判定） */
    private fun artistNameMatches(candidateArtist: String, artist: String): Boolean {
        if (candidateArtist.isBlank() || artist.isBlank()) return false
        fun norm(v: String) = v.lowercase().replace(Regex("""[\s・、,，/]"""), "")
        val c = norm(candidateArtist)
        if (c.isBlank()) return false
        // "A feat. B" のような表記は構成要素ごとに照合する
        val parts = artist.lowercase().split(Regex("""[\s・、,，/]+"""))
            .map { it.trim().removeSuffix(".") }
            .filter { it.isNotBlank() && it != "feat" && it != "featuring" }
        if (parts.any { part -> c.contains(norm(part)) }) return true
        val a = norm(artist)
        return a.isNotBlank() && (a.contains(c) || c.contains(a))
    }

    private fun cacheFile(context: Context, title: String, artist: String, album: String): File {
        val dir = File(context.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, cacheKey(title, artist, album) + ".lrc")
    }

    /** 指定した曲の歌詞キャッシュを削除する（誤った歌詞を掴んだときの再取得用） */
    fun invalidateCache(context: Context, title: String, artist: String, album: String) {
        try {
            cacheFile(context, title, artist, album).delete()
        } catch (e: Exception) {
            // 削除失敗は無視する
        }
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

        // 検索用タイトル: 先頭のトラック番号（"11. " など）を落とす。
        // データベース側の曲名には番号が付いていないため、そのままだと照合で不利になる。
        // 表示やキャッシュのキーは元のタイトルのままにする
        val queryTitle = title.replace(Regex("""^\\s*\\d{1,3}\\s*[.．、:：_\\-]\\s*"""), "").ifBlank { title }

        // 3) LRCLIB /api/get（タイトル・アーティスト・アルバム・長さで照合）
        val durationSec = if (durationMs > 0) durationMs / 1000 else 0
        val getUrl = "https://lrclib.net/api/get?track_name=${enc(queryTitle)}&artist_name=${enc(artist)}" +
            "&album_name=${enc(album)}&duration=$durationSec"
        val getJson = httpGetJson(getUrl)
        val wantJapanese = containsJapanese(title) || containsJapanese(artist)
        val synced = getJson?.optString("syncedLyrics", "")?.takeIf { it.isNotBlank() }
        // 日本語の曲なのに日本語を含まない歌詞（ローマ字・翻訳など）は別バージョンとみなして採用しない
        if (synced != null && (!wantJapanese || containsJapanese(synced))) {
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
        val searchUrl = "https://lrclib.net/api/search?track_name=${enc(queryTitle)}&artist_name=${enc(artist)}"
        var bestText: String? = null
        var bestDiffSec = Long.MAX_VALUE
        var bestArtistText: String? = null
        var bestArtistDiffSec = Long.MAX_VALUE
        fun consider(results: org.json.JSONArray?, requireCloseDuration: Boolean) {
            if (results == null) return
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val candidate = item.optString("syncedLyrics", "").takeIf { it.isNotBlank() } ?: continue
                // 候補の曲名自体が目的の曲と一致することも確認する（検索の曖昧さで別曲が混ざるため）
                if (!titleMatches(item.optString("trackName", ""), queryTitle)) continue
                if (wantJapanese && !containsJapanese(candidate)) continue
                val candDurSec = item.optLong("duration", 0L)
                // 長さが大きく違う候補（別バージョン・別録音）は採用しない
                if (durationSec > 0 && candDurSec > 0 && kotlin.math.abs(candDurSec - durationSec) > 10) continue
                val diff = if (durationSec > 0 && candDurSec > 0) {
                    kotlin.math.abs(candDurSec - durationSec)
                } else {
                    Long.MAX_VALUE - 1
                }
                // 曲名だけで引いた場合は、長さが近い候補だけを採用する（別曲の歌詞を掴まないため）
                if (requireCloseDuration && durationSec > 0 && diff > 4) continue
                if (bestText == null || diff < bestDiffSec) {
                    bestText = candidate
                    bestDiffSec = diff
                }
                // 同名曲が複数ある場合は、アーティスト名が一致する候補を優先する
                if (artistNameMatches(item.optString("artistName", ""), artist)) {
                    if (bestArtistText == null || diff < bestArtistDiffSec) {
                        bestArtistText = candidate
                        bestArtistDiffSec = diff
                    }
                }
            }
        }
        consider(httpGetJsonArray(searchUrl), requireCloseDuration = false)
        if (bestText == null) {
            // アーティスト名の表記ゆれ（日本語／ローマ字など）で空振りした場合は曲名だけで引き直す
            val titleOnlyUrl = "https://lrclib.net/api/search?track_name=${enc(queryTitle)}"
            consider(httpGetJsonArray(titleOnlyUrl), requireCloseDuration = true)
        }
        val chosen = bestArtistText ?: bestText
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

        // 5) Kugou（酷狗）: さらにマニアックな曲の候補
        val kugouLrc = try {
            fetchKugouLrc(queryTitle, artist, durationMs)
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

        // 6) NetEase（网易云）: 中国系サービスで、ボカロ曲の同期歌詞が比較的厚い
        val neteaseLrc = try {
            fetchNetEaseLrc(queryTitle, artist, durationMs)
        } catch (e: Exception) {
            null
        }
        if (!neteaseLrc.isNullOrBlank()) {
            val parsed = parseLrc(neteaseLrc)
            if (parsed.isNotEmpty()) {
                // NetEaseは別曲の歌詞が誤って紐付いていることがあるため、結果をキャッシュしない
                // （次回再生時にKugou等を先に試し直す）
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

        // 曲名が一致する候補だけを対象に、長さが近い順に最大3件まで試す。
        // 以前は「長さが近いだけ」で選び、無ければ先頭の結果を無条件に使っていたため、
        // 同じアーティストの別曲の歌詞を掴む事故が起きていた
        val wantJapanese = containsJapanese(title) || containsJapanese(artist)
        val matched = mutableListOf<Triple<Long, Long, Boolean>>()
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val id = song.optLong("id", -1L)
            if (id <= 0) continue
            if (!titleMatches(song.optString("name", ""), title)) continue
            val songDurMs = song.optLong("duration", 0L)
            val diff = if (durationMs > 0 && songDurMs > 0) {
                kotlin.math.abs(songDurMs - durationMs)
            } else {
                Long.MAX_VALUE - 1
            }
            val artistsArr = song.optJSONArray("artists")
            val artistNames = StringBuilder()
            if (artistsArr != null) {
                for (j in 0 until artistsArr.length()) {
                    val an = artistsArr.optJSONObject(j)?.optString("name", "") ?: ""
                    if (an.isNotBlank()) {
                        if (artistNames.isNotEmpty()) artistNames.append("、")
                        artistNames.append(an)
                    }
                }
            }
            matched.add(Triple(id, diff, artistNameMatches(artistNames.toString(), artist)))
        }
        // アーティストが一致する候補を最優先し、その中で長さが近い順に試す
        val artistMatchedSongs = matched.filter { it.third }
        val songPool = if (artistMatchedSongs.isNotEmpty()) artistMatchedSongs else matched
        val orderedMatched = songPool.sortedBy { it.second }

        for ((id, _, _) in orderedMatched.take(3)) {
            val lyricUrl = "https://music.163.com/api/song/lyric?id=$id&lv=1&tv=1&rv=1"
            val lyricText = httpGetText(lyricUrl, desktopUa, "https://music.163.com/") ?: continue
            val lrc = try {
                JSONObject(lyricText).optJSONObject("lrc")?.optString("lyric", "")
            } catch (e: Exception) {
                null
            }
            if (!lrc.isNullOrBlank() && parseLrc(lrc).isNotEmpty() &&
                (!wantJapanese || containsJapanese(lrc))
            ) return lrc
        }
        return null
    }

    /** Kugou の非公式APIから同期歌詞（LRCテキスト）を取得する。見つからなければ null */
    private fun fetchKugouLrc(title: String, artist: String, durationMs: Long): String? {
        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        val wantJapanese = containsJapanese(title) || containsJapanese(artist)
        fun searchInfos(keyword: String): org.json.JSONArray? {
            val url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json" +
                "&keyword=${enc(keyword)}&page=1&pagesize=30&showtype=1"
            val text = httpGetText(url, desktopUa) ?: return null
            return try {
                JSONObject(text).optJSONObject("data")?.optJSONArray("info")
            } catch (e: Exception) {
                null
            }
        }
        // 曲名が一致する候補だけを集める（別曲の歌詞を掴まないため）
        fun collectHashes(infos: org.json.JSONArray?): MutableList<Triple<String, Long, Boolean>> {
            val list = mutableListOf<Triple<String, Long, Boolean>>()
            if (infos == null) return list
            for (i in 0 until infos.length()) {
                val info = infos.optJSONObject(i) ?: continue
                val hash = info.optString("hash", "")
                if (hash.isBlank()) continue
                val songName = info.optString("songname", "")
                    .ifBlank { info.optString("filename", "").substringAfterLast(" - ") }
                if (!titleMatches(songName, title)) continue
                val singer = info.optString("singername", "")
                list.add(Triple(hash, info.optLong("duration", 0L), artistNameMatches(singer, artist)))
            }
            return list
        }
        // まず曲名だけで検索する。アーティスト名（特に feat. 表記）を混ぜると検索が汚染され、
        // 目的の曲が結果から消えることが実測で分かったため
        var hashes = collectHashes(searchInfos(title))
        if (hashes.isEmpty() && artist.isNotBlank()) {
            hashes = collectHashes(searchInfos("$title $artist"))
        }
        if (hashes.isEmpty()) return null
        // 同名曲が大量にある場合に別アーティストの版を掴まないよう、アーティストが一致する候補を優先する
        val artistPool = hashes.filter { it.third }
        var pool = if (artistPool.isNotEmpty()) artistPool else hashes
        // 長さが大きく違う候補（別バージョン・別曲）は除外する。ただし全滅する場合は元の候補に戻す
        if (durationMs > 0) {
            val gated = pool.filter { it.second <= 0 || kotlin.math.abs(it.second * 1000 - durationMs) <= 15_000 }
            if (gated.isNotEmpty()) pool = gated
        }
        val ordered = if (durationMs > 0) {
            pool.sortedBy { kotlin.math.abs(it.second * 1000 - durationMs) }
        } else {
            pool
        }

        for ((hash, durSec, _) in ordered.take(3)) {
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
            // 歌詞候補も曲名が一致するものを優先する（無ければ先頭）
            var first = candidates.optJSONObject(0) ?: continue
            for (j in 0 until candidates.length()) {
                val cand = candidates.optJSONObject(j) ?: continue
                if (titleMatches(cand.optString("song", ""), title)) {
                    first = cand
                    break
                }
            }
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
            if (!lrc.isNullOrBlank() && parseLrc(lrc).isNotEmpty() &&
                (!wantJapanese || containsJapanese(lrc))
            ) return lrc
        }
        return null
    }

    // ===== タグ指定検索（手動で候補から選んで適用する用） =====

    data class LyricCandidate(
        val source: String,
        val title: String,
        val artist: String,
        val durationSec: Int,
        val syncedLyrics: String?,
        val kugouHash: String?
    )

    /** 曲名・アーティスト名で歌詞候補を検索する（LRCLIBとKugouの両方を当たる） */
    suspend fun searchCandidates(title: String, artist: String): List<LyricCandidate> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<LyricCandidate>()
            fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
            try {
                val url = "https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"
                val arr = httpGetJsonArray(url)
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val synced = item.optString("syncedLyrics", "").takeIf { it.isNotBlank() } ?: continue
                        results.add(
                            LyricCandidate(
                                source = "LRCLIB",
                                title = item.optString("trackName", ""),
                                artist = item.optString("artistName", ""),
                                durationSec = item.optInt("duration", 0),
                                syncedLyrics = synced,
                                kugouHash = null
                            )
                        )
                        if (results.size >= 15) break
                    }
                }
            } catch (e: Exception) {
                // 片方のソースが失敗しても、もう片方の結果は返す
            }
            try {
                val keyword = if (artist.isBlank()) title else "$title $artist"
                val url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json" +
                    "&keyword=${enc(keyword)}&page=1&pagesize=20&showtype=1"
                val text = httpGetText(url, desktopUa)
                val infos = try {
                    JSONObject(text ?: "").optJSONObject("data")?.optJSONArray("info")
                } catch (e: Exception) {
                    null
                }
                if (infos != null) {
                    var added = 0
                    for (i in 0 until infos.length()) {
                        val info = infos.optJSONObject(i) ?: continue
                        val hash = info.optString("hash", "")
                        if (hash.isBlank()) continue
                        val songName = info.optString("songname", "")
                            .ifBlank { info.optString("filename", "").substringAfterLast(" - ") }
                        results.add(
                            LyricCandidate(
                                source = "Kugou",
                                title = songName,
                                artist = info.optString("singername", ""),
                                durationSec = info.optInt("duration", 0),
                                syncedLyrics = null,
                                kugouHash = hash
                            )
                        )
                        added++
                        if (added >= 15) break
                    }
                }
            } catch (e: Exception) {
                // 同上
            }
            results
        }

    /** 候補の歌詞本文（LRC）を取得する */
    suspend fun fetchCandidateLrc(candidate: LyricCandidate): String? = withContext(Dispatchers.IO) {
        if (candidate.source == "LRCLIB") {
            return@withContext candidate.syncedLyrics?.takeIf { parseLrc(it).isNotEmpty() }
        }
        val hash = candidate.kugouHash ?: return@withContext null
        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        val durMs = if (candidate.durationSec > 0) candidate.durationSec * 1000L else 0L
        val candUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
            "&keyword=${enc(candidate.title)}&duration=$durMs&hash=$hash"
        val candText = httpGetText(candUrl, desktopUa) ?: return@withContext null
        val candidates = try {
            JSONObject(candText).optJSONArray("candidates")
        } catch (e: Exception) {
            null
        } ?: return@withContext null
        if (candidates.length() == 0) return@withContext null
        var first = candidates.optJSONObject(0) ?: return@withContext null
        for (j in 0 until candidates.length()) {
            val cand = candidates.optJSONObject(j) ?: continue
            if (titleMatches(cand.optString("song", ""), candidate.title)) {
                first = cand
                break
            }
        }
        val candId = first.optString("id", "")
        val accessKey = first.optString("accesskey", "")
        if (candId.isBlank() || accessKey.isBlank()) return@withContext null
        val dlUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$candId" +
            "&accesskey=$accessKey&fmt=lrc&charset=utf8"
        val dlText = httpGetText(dlUrl, desktopUa) ?: return@withContext null
        val contentB64 = try {
            JSONObject(dlText).optString("content", "")
        } catch (e: Exception) {
            ""
        }
        if (contentB64.isBlank()) return@withContext null
        val lrc = try {
            String(android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
        lrc?.takeIf { parseLrc(it).isNotEmpty() }
    }

    /** 手動で選んだ歌詞を、その曲のキャッシュとして保存する（次回から自動でこれが出る） */
    fun saveLyricsOverride(context: Context, title: String, artist: String, album: String, lrcText: String) {
        try {
            cacheFile(context, title, artist, album).writeText(lrcText, Charsets.UTF_8)
        } catch (e: Exception) {
            // 保存に失敗しても、表示への適用は呼び出し側で行う
        }
    }

    /** 歌詞の各行を日本語へ翻訳する（日本語を含む行は空文字＝訳なし）。結果はキャッシュする */
    suspend fun translateToJapanese(
        context: Context,
        title: String,
        artist: String,
        album: String,
        lines: List<LyricLine>
    ): List<String> = withContext(Dispatchers.IO) {
        val cache = File(cacheFile(context, title, artist, album).absolutePath + ".ja")
        if (cache.exists()) {
            try {
                val cached = cache.readText(Charsets.UTF_8).split("\n")
                if (cached.size == lines.size) return@withContext cached
            } catch (e: Exception) {
                // キャッシュが読めなければ翻訳し直す
            }
        }
        val result = MutableList(lines.size) { "" }
        val targets = lines.mapIndexedNotNull { i, l ->
            if (l.text.isBlank() || containsJapanese(l.text)) null else i to l.text
        }
        if (targets.isEmpty()) return@withContext result
        var idx = 0
        while (idx < targets.size) {
            val chunk = mutableListOf<Pair<Int, String>>()
            var len = 0
            while (idx < targets.size && len < 1200) {
                val t = targets[idx]
                chunk.add(t)
                len += t.second.length + 1
                idx++
            }
            val joined = chunk.joinToString("\n") { it.second }
            val translated = translateTextGoogle(joined)
            val parts = translated?.split("\n")
            if (parts != null && parts.size == chunk.size) {
                chunk.forEachIndexed { k, (lineIdx, _) -> result[lineIdx] = parts[k].trim() }
            } else {
                // 行数がずれた場合は1行ずつ翻訳し直す
                for ((lineIdx, text) in chunk) {
                    val one = translateTextGoogle(text)
                    if (one != null) result[lineIdx] = one.trim()
                }
            }
        }
        try {
            cache.writeText(result.joinToString("\n"), Charsets.UTF_8)
        } catch (e: Exception) {
            // キャッシュ保存失敗は無視する
        }
        result
    }

    /** Google翻訳の非公式エンドポイントでテキストを日本語に翻訳する */
    private fun translateTextGoogle(text: String): String? {
        return try {
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ja&dt=t&q=" +
                URLEncoder.encode(text, "UTF-8")
            val body = httpGetText(url, desktopUa) ?: return null
            val root = org.json.JSONArray(body)
            val segments = root.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                sb.append(segments.optJSONArray(i)?.optString(0, "") ?: "")
            }
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }
}

