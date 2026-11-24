package com.example.nissingeppomusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.example.nissingeppomusic.ui.LyricEffectsData
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** 歌詞の1行（同期歌詞用にタイムスタンプ付き） */
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord> = emptyList())

/** 文字同期の1セグメント（Kugou の KRC 由来。通常は1文字。開始・終了は曲頭からの絶対時刻ms） */
data class LyricWord(val text: String, val startMs: Long, val endMs: Long)

/**
 * 同期歌詞の取得まわり。
 * 優先順位: 音源と同じ場所の .lrc ファイル → 端末内キャッシュ → LRCLIB（lrclib.net、無料・キー不要）
 * 取得できた同期歌詞は端末内にキャッシュして、次回からは通信しない。
 */
object LyricsManager {

    private val timestampRegex = Regex("""\[(\d+):(\d+)(?:\.(\d{1,3}))?\]""")

    /** 簡体字・繁体字を日本の漢字（新字体）に寄せる変換表。正本は配信データ（LyricEffectsData）で、未取得時は内蔵デフォルトを使う */
    private val kanjiConvertMap: Map<Char, Char> get() = LyricEffectsData.kanjiMap

    /** 簡体字・繁体字を日本の漢字に変換する */
    /**
     * 歌詞本文（クレジット行を除く）に簡体字・繁体字（日本の漢字への変換対象）が含まれているか。
     * Kugou の歌詞は中国語版や簡体字混じりのことがあるため、優先判定に使う
     */
    private fun bodyHasSimplified(text: String): Boolean {
        return text.lines().any { line ->
            !isCreditLine(line) && line.any { ch -> kanjiConvertMap.containsKey(ch) }
        }
    }

    private fun toJapaneseKanji(text: String): String {
        if (text.none { kanjiConvertMap.containsKey(it) }) return text
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(kanjiConvertMap[ch] ?: ch)
        return sb.toString()
    }


    /** LRC形式テキストをパースする。タイムスタンプのない行は無視する */
    // インスト曲のダミー歌詞や「歌詞なし」通知の断片（簡体字・繁体字・日本語表記のゆれをまとめて拾う）
    private val PLACEHOLDER_LYRIC_MARKERS: List<String> get() = LyricEffectsData.placeholderMarkers

    fun parseLrc(lrcText: String): List<LyricLine> {
        val raw = mutableListOf<LyricLine>()
        for (rawLine in lrcText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val matches = timestampRegex.findAll(line).toList()
            if (matches.isEmpty()) continue
            val rawText = line.replace(timestampRegex, "").trim()
            if (rawText.isEmpty()) continue
            // 中国ソースがインスト曲に付けるダミー行（「纯音乐，请欣赏」＝純音楽、ご鑑賞ください）は歌詞ではないので除外する
            if (PLACEHOLDER_LYRIC_MARKERS.any { rawText.contains(it) }) continue
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
                raw.add(LyricLine(min * 60_000L + sec * 1_000L + fracMs, rawText))
            }
        }
        // 簡体字・繁体字の手直しは「日本語の歌詞」のときだけ行う（かなを含む行があるかで判定する。中国語の歌詞は原文のまま使う）。
        // クレジット行（作词：〜等）も、ラベル部分が簡体字のまま残らないよう一緒に変換する
        val hasKana = raw.any { line -> line.text.any { it in '\u3040'..'\u30FF' } }
        val result = if (hasKana) {
            raw.map { line -> LyricLine(line.timeMs, toJapaneseKanji(line.text)) }
        } else raw
        return splitLongLines(result.sortedBy { it.timeMs })
    }

    /** KRC形式（文字単位の同期情報つき）をパースする。1行 = [開始ms,長さms]<オフセット,長さ,0>文字… の形式 */
    private val krcLineRegex = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val krcWordRegex = Regex("""<(\d+),(\d+),\d+>([^<]*)""")

    fun parseKrc(krcText: String): List<LyricLine> {
        val raw = mutableListOf<LyricLine>()
        for (rawLine in krcText.lineSequence()) {
            val line = rawLine.trim()
            val m = krcLineRegex.find(line) ?: continue
            val lineStart = m.groupValues[1].toLongOrNull() ?: continue
            val body = m.groupValues[3]
            val words = mutableListOf<LyricWord>()
            val sb = StringBuilder()
            for (wm in krcWordRegex.findAll(body)) {
                val off = wm.groupValues[1].toLongOrNull() ?: continue
                val dur = wm.groupValues[2].toLongOrNull() ?: continue
                val ch = wm.groupValues[3]
                if (ch.isEmpty()) continue
                words.add(LyricWord(ch, lineStart + off, lineStart + off + dur))
                sb.append(ch)
            }
            // 文字位置と words をずらさないため、行テキストは trim しない（空白もセグメントとして数える）
            val text = sb.toString()
            if (words.isEmpty() || text.isBlank()) continue
            if (PLACEHOLDER_LYRIC_MARKERS.any { text.contains(it) }) continue
            raw.add(LyricLine(lineStart, text, words))
        }
        // 日本語の歌詞のときだけ簡体字・繁体字を日本の漢字にそろえる（クレジット行も含む。中国語の歌詞は原文のまま使う）
        val hasKana = raw.any { line -> line.text.any { it in '\u3040'..'\u30FF' } }
        val result = if (hasKana) {
            raw.map { line ->
                LyricLine(
                    line.timeMs,
                    toJapaneseKanji(line.text),
                    line.words.map { w -> LyricWord(toJapaneseKanji(w.text), w.startMs, w.endMs) }
                )
            }
        } else raw
        return splitLongLines(result.sortedBy { it.timeMs })
    }

    /** KRC の暗号化（先頭4バイト "krc1" ＋ 16バイト周期XOR ＋ zlib）を復号してテキストに戻す。失敗したら null */
    private fun decodeKrc(contentB64: String): String? {
        val raw = try {
            android.util.Base64.decode(contentB64, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            return null
        }
        if (raw.size < 5 || String(raw, 0, 4, Charsets.US_ASCII) != "krc1") return null
        val key = intArrayOf(0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d, 0xce, 0xd2, 0x6e, 0x69)
        val xored = ByteArray(raw.size - 4) { i -> (raw[i + 4].toInt() xor key[i % 16]).toByte() }
        return try {
            java.util.zip.InflaterInputStream(java.io.ByteArrayInputStream(xored)).use { it.readBytes() }
                .toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** 行の幅見積もり（半角は 0.55、全角は 1.0 として数える） */
    private fun lyricEmWidth(s: String): Float =
        s.fold(0f) { acc, ch -> acc + if (ch.code < 0x300) 0.55f else 1.0f }

    /**
     * 長すぎる行を、意味の切れ目で複数行に分割する（幅24を超える行が対象。再帰的に処理）。
     * 分割点は「スペース ＞ 句読点の直後 ＞ 助詞の直後 ＞ 中央」の優先順で、中央付近から選ぶ。
     * 文字同期がある行は単語境界で割り、後半の開始時刻は実時刻を使う
     */
    private fun splitLongLines(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines
        val out = mutableListOf<LyricLine>()
        for (i in lines.indices) {
            out.addAll(splitOneLine(lines[i], lines.getOrNull(i + 1)?.timeMs))
        }
        return out
    }

    private fun splitOneLine(line: LyricLine, nextStartMs: Long?): List<LyricLine> {
        val text = line.text
        if (text.length < 10 || lyricEmWidth(text) <= 24f) return listOf(line)
        val mid = text.length / 2
        val rangeStart = (text.length * 0.3f).toInt()
        val rangeEnd = (text.length * 0.7f).toInt()
        var best = -1
        var bestScore = Int.MAX_VALUE
        for (i in rangeStart..rangeEnd) {
            if (i <= 0 || i >= text.length) continue
            val prev = text[i - 1]
            val priority = when {
                prev == ' ' || prev == '　' -> 0
                prev in "、。，．・：:；;！!？?）)】」』" -> 1
                prev in "はがをにへとやのもで" -> 2
                else -> continue
            }
            val score = priority * 1000 + kotlin.math.abs(i - mid)
            if (score < bestScore) {
                bestScore = score
                best = i
            }
        }
        var splitIdx = if (best >= 0) best else mid
        // 英単語の途中では割らない（手前のスペースまで戻す）
        if (splitIdx in 1 until text.length &&
            text[splitIdx - 1] != ' ' && text[splitIdx] != ' ' &&
            text[splitIdx - 1].code < 0x300 && text[splitIdx].code < 0x300
        ) {
            val back = text.lastIndexOf(' ', splitIdx - 1)
            if (back >= rangeStart) splitIdx = back + 1
        }
        if (splitIdx <= 0 || splitIdx >= text.length) return listOf(line)
        if (line.words.isNotEmpty()) {
            var acc = 0
            var wi = 0
            while (wi < line.words.size && acc + line.words[wi].text.length <= splitIdx) {
                acc += line.words[wi].text.length
                wi++
            }
            if (wi <= 0 || wi >= line.words.size) return listOf(line)
            val g1 = line.words.subList(0, wi).dropLastWhile { it.text.isBlank() }
            val g2 = line.words.subList(wi, line.words.size).dropWhile { it.text.isBlank() }
            if (g1.isEmpty() || g2.isEmpty()) return listOf(line)
            val l1 = LyricLine(line.timeMs, g1.joinToString("") { it.text }, g1)
            val l2 = LyricLine(g2.first().startMs, g2.joinToString("") { it.text }, g2)
            return splitOneLine(l1, l2.timeMs) + splitOneLine(l2, nextStartMs)
        }
        val t1 = text.substring(0, splitIdx).trimEnd()
        val t2 = text.substring(splitIdx).trimStart()
        if (t1.isEmpty() || t2.isEmpty()) return listOf(line)
        val endMs = nextStartMs ?: (line.timeMs + 4000L)
        val span = (endMs - line.timeMs).coerceAtLeast(0L)
        val t2Start = line.timeMs + (span * splitIdx / text.length).coerceIn(0L, span)
        val l1 = LyricLine(line.timeMs, t1)
        val l2 = LyricLine(t2Start, t2)
        return splitOneLine(l1, t2Start) + splitOneLine(l2, nextStartMs)
    }

    /** 行リストを行単位の LRC テキストに変換する（通常キャッシュ保存用。文字タイミングは .krc 側に残す） */
    private fun linesToLrc(lines: List<LyricLine>): String {
        val sb = StringBuilder()
        for (line in lines) {
            val totalSec = line.timeMs / 1000
            val mm = (totalSec / 60).toString().padStart(2, '0')
            val ss = (totalSec % 60).toString().padStart(2, '0')
            val cs = ((line.timeMs % 1000) / 10).toString().padStart(2, '0')
            sb.append("[$mm:$ss.$cs]").append(line.text).append("\n")
        }
        return sb.toString()
    }

    /** 文字同期（KRC）のキャッシュを、通常キャッシュと並べて保存する */
    fun saveKrcCache(context: Context, title: String, artist: String, album: String, krcText: String) {
        try {
            File(cacheFile(context, title, artist, album).absolutePath + ".krc").writeText(krcText, Charsets.UTF_8)
        } catch (e: Exception) {
            // 保存失敗は無視する
        }
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
        try {
            File(cacheFile(context, title, artist, album).absolutePath + ".krc").delete()
        } catch (e: Exception) {
            // 削除失敗は無視する
        }
        try {
            File(cacheFile(context, title, artist, album).absolutePath + ".nokrc").delete()
        } catch (e: Exception) {
            // 削除失敗は無視する
        }
    }

    /**
     * JSONの文字列フィールドを安全に取り出す。
     * Androidの optString は、値が JSON null のときに文字列 "null" を返してしまうことがある。
     * そのためインスト曲（syncedLyrics が null）でも歌詞があるように見えてしまい、
     * タグ検索で実在しない候補が並んでいた。opt() で実物の型を見て、文字列のときだけ返す。
     */
    private fun JSONObject.optLyricsString(name: String): String? =
        (opt(name) as? String)?.takeIf { it.isNotBlank() }

    private fun httpGetJson(urlString: String): JSONObject? {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "NissinGeppoMusic (personal launcher)")
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
            connection.setRequestProperty("User-Agent", "NissinGeppoMusic (personal launcher)")
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
    /** 直近の getSyncedLyrics がどのソースから歌詞を取ったか */
    @Volatile
    var lastFetchSource: String = ""

    /** 直近の fetchKugouKrc が、アーティスト名の一致する候補から取得したかどうか */
    @Volatile
    var lastKugouKrcArtistMatched: Boolean = false

    suspend fun getSyncedLyrics(
        context: Context,
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        filePath: String?
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        lastFetchSource = ""
        // 1) 音源と同じ場所の .lrc ファイル
        if (!filePath.isNullOrBlank() && filePath.startsWith("/")) {
            val lrcFile = File(filePath.substringBeforeLast('.') + ".lrc")
            if (lrcFile.exists()) {
                val parsed = try {
                    parseLrc(lrcFile.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    emptyList()
                }
                if (parsed.isNotEmpty()) {
                    lastFetchSource = "ローカルファイル"
                    return@withContext parsed
                }
            }
        }

        // 1.5) 「歌詞なし（インスト）」指定がある曲は、歌詞なしとして即座に返す
        if (isInstrumentalOverride(context, title, artist, album)) {
            lastFetchSource = "インスト（歌詞なし）"
            return@withContext emptyList()
        }

        // 2) 端末内キャッシュ
        val cache = cacheFile(context, title, artist, album)
        if (cache.exists()) {
            val parsed = try {
                parseLrc(cache.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                emptyList()
            }
            if (parsed.isNotEmpty()) {
                lastFetchSource = "キャッシュ"
                // 文字同期（KRC）のキャッシュがあれば、文字タイミング付きの行で返す
                val krcFile = File(cache.absolutePath + ".krc")
                if (krcFile.exists()) {
                    val krcParsed = try {
                        parseKrc(krcFile.readText(Charsets.UTF_8))
                    } catch (e: Exception) {
                        emptyList()
                    }
                    if (krcParsed.isNotEmpty()) return@withContext krcParsed
                }
                // 文字同期なしのキャッシュ（LRCLIB由来など）の場合は、Kugouに文字同期（KRC）が
                // ないか一度だけ確認し、あればそちらへ差し替える。無いことを確認したら
                // .nokrc マーカーを残し、次回からは確認しない
                val noKrcFile = File(cache.absolutePath + ".nokrc")
                if (!noKrcFile.exists() && title.isNotBlank()) {
                    val qTitle = title.replace(Regex("""^\\s*\\d{1,3}\\s*[.．、:：_\\-]\\s*"""), "").ifBlank { title }
                    val krcText = try {
                        fetchKugouKrc(qTitle, artist, durationMs)
                    } catch (e: Exception) {
                        null
                    }
                    if (!krcText.isNullOrBlank()) {
                        val krcParsed2 = try {
                            parseKrc(krcText)
                        } catch (e: Exception) {
                            emptyList()
                        }
                        if (krcParsed2.isNotEmpty() && krcParsed2.any { it.words.isNotEmpty() } && lastKugouKrcArtistMatched && !bodyHasSimplified(krcText)) {
                            try {
                                cache.writeText(linesToLrc(krcParsed2), Charsets.UTF_8)
                                File(cache.absolutePath + ".krc").writeText(krcText, Charsets.UTF_8)
                            } catch (e: Exception) {
                                // キャッシュ保存失敗は無視する
                            }
                            lastFetchSource = "Kugou"
                            return@withContext krcParsed2
                        }
                    }
                    try {
                        noKrcFile.writeText("1", Charsets.UTF_8)
                    } catch (e: Exception) {
                        // マーカー保存失敗は無視する
                    }
                }
                return@withContext parsed
            }
        }

        if (title.isBlank()) return@withContext null

        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

        // 検索用タイトル: 先頭のトラック番号（"11. " など）を落とす。
        // データベース側の曲名には番号が付いていないため、そのままだと照合で不利になる。
        // 表示やキャッシュのキーは元のタイトルのままにする
        val queryTitle = title.replace(Regex("""^\\s*\\d{1,3}\\s*[.．、:：_\\-]\\s*"""), "").ifBlank { title }

        // 2.5) Kugouに文字同期（KRC）がある場合は、LRCLIBより優先する。
        // ただしアーティストが一致していないKRCは即採用せず保留し、LRCLIBにアーティスト一致の
        // 歌詞（行同期）があればそちらを優先する（同名曲の別アーティスト版を掴まないため）
        var pendingKrc: Pair<String, List<LyricLine>>? = null
        var pendingStrict = false
        val kugouKrcFirst = try {
            fetchKugouKrc(queryTitle, artist, durationMs)
        } catch (e: Exception) {
            null
        }
        if (!kugouKrcFirst.isNullOrBlank()) {
            val parsedKrcFirst = try {
                parseKrc(kugouKrcFirst)
            } catch (e: Exception) {
                emptyList()
            }
            if (parsedKrcFirst.isNotEmpty() && parsedKrcFirst.any { it.words.isNotEmpty() }) {
                // 本文に簡体字が混ざっているKRCも即採用しない（LRCLIBを優先する）
                val krcSimplified = bodyHasSimplified(kugouKrcFirst)
                if (lastKugouKrcArtistMatched && !krcSimplified) {
                    try {
                        cache.writeText(linesToLrc(parsedKrcFirst), Charsets.UTF_8)
                        File(cache.absolutePath + ".krc").writeText(kugouKrcFirst, Charsets.UTF_8)
                    } catch (e: Exception) {
                        // キャッシュ保存失敗は無視する
                    }
                    lastFetchSource = "Kugou"
                    return@withContext parsedKrcFirst
                } else {
                    pendingKrc = kugouKrcFirst to parsedKrcFirst
                    // LRCLIB側にアーティスト一致を要求するのは「アーティスト不一致だけ」が理由のとき。
                    // 簡体字が理由のときは、LRCLIBの通常の採用基準で優先する
                    pendingStrict = !lastKugouKrcArtistMatched && !krcSimplified
                }
            }
        }

        // 3) LRCLIB /api/get（タイトル・アーティスト・アルバム・長さで照合）
        val durationSec = if (durationMs > 0) durationMs / 1000 else 0
        val getUrl = "https://lrclib.net/api/get?track_name=${enc(queryTitle)}&artist_name=${enc(artist)}" +
            "&album_name=${enc(album)}&duration=$durationSec"
        val getJson = httpGetJson(getUrl)
        val wantJapanese = containsJapanese(title) || containsJapanese(artist)
        val synced = getJson?.optLyricsString("syncedLyrics")
        // 日本語の曲なのに日本語を含まない歌詞（ローマ字・翻訳など）は別バージョンとみなして採用しない
        if (synced != null && (!wantJapanese || containsJapanese(synced))) {
            val parsed = parseLrc(synced)
            if (parsed.isNotEmpty()) {
                try {
                    cache.writeText(synced, Charsets.UTF_8)
                } catch (e: Exception) {
                    // キャッシュ保存失敗は無視する
                }
                lastFetchSource = "LRCLIB"
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
                val candidate = item.optLyricsString("syncedLyrics") ?: continue
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
        // アーティスト不一致だけが理由でKRCを保留している間は、LRCLIB側もアーティスト一致の候補だけを採用する
        val chosen = if (pendingKrc != null && pendingStrict) bestArtistText else (bestArtistText ?: bestText)
        if (chosen != null) {
            val parsed = parseLrc(chosen)
            if (parsed.isNotEmpty()) {
                try {
                    cache.writeText(chosen, Charsets.UTF_8)
                } catch (e: Exception) {
                    // キャッシュ保存失敗は無視する
                }
                lastFetchSource = "LRCLIB"
                return@withContext parsed
            }
        }

        // 保留していたKRC: LRCLIBにアーティスト一致の歌詞が無かったので、ここで採用する
        val pk = pendingKrc
        if (pk != null) {
            val (krcText, krcLines) = pk
            try {
                cache.writeText(linesToLrc(krcLines), Charsets.UTF_8)
                File(cache.absolutePath + ".krc").writeText(krcText, Charsets.UTF_8)
            } catch (e: Exception) {
                // キャッシュ保存失敗は無視する
            }
            lastFetchSource = "Kugou"
            return@withContext krcLines
        }

        // 5) Kugou（酷狗）: さらにマニアックな曲の候補（KRCは 2.5 で試し済みなので、ここではLRCのみ）
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
                lastFetchSource = "Kugou"
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
                lastFetchSource = "NetEase"
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

    /** Kugou の非公式APIから文字同期つき歌詞（KRCテキスト）を復号して取得する。見つからなければ null */
    private fun fetchKugouKrc(title: String, artist: String, durationMs: Long): String? {
        lastKugouKrcArtistMatched = false
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

        for ((hash, durSec, artistMatched) in ordered.take(3)) {
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
                "&accesskey=$accessKey&fmt=krc&charset=utf8"
            val dlText = httpGetText(dlUrl, desktopUa) ?: continue
            val contentB64 = try {
                JSONObject(dlText).optString("content", "")
            } catch (e: Exception) {
                null
            }
            if (contentB64.isNullOrBlank()) continue
            val krc = decodeKrc(contentB64) ?: continue
            if (parseKrc(krc).isNotEmpty() &&
                (!wantJapanese || containsJapanese(krc))
            ) {
                lastKugouKrcArtistMatched = artistMatched
                return krc
            }
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
        val kugouHash: String?,
        val instrumental: Boolean = false,
        /** 文字単位の同期（カラオケ）がある候補か。Kugou候補は検索時にKRCの実在を確認して埋める */
        val wordSync: Boolean = false
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
                        val synced = item.optLyricsString("syncedLyrics")
                        // インスト曲（歌詞なし）として登録されているものも候補に出す。
                        // 選ぶと「この曲は歌詞なし」という指定として適用される
                        val isInst = item.optBoolean("instrumental", false) && synced == null
                        if (synced == null && !isInst) continue
                        results.add(
                            LyricCandidate(
                                source = "LRCLIB",
                                title = item.optString("trackName", ""),
                                artist = item.optString("artistName", ""),
                                durationSec = item.optInt("duration", 0),
                                syncedLyrics = synced,
                                kugouHash = null,
                                instrumental = isInst
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
                        // 曲名が一致しないヒット（アルバム名などで引っかかった無関係な曲）は候補に出さない
                        if (!titleMatches(songName, title)) continue
                        // Kugou の曲カタログにあっても歌詞が登録されていない曲は、タップしても取得できないので候補に出さない
                        val durMs = info.optInt("duration", 0) * 1000L
                        val candUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
                            "&keyword=${enc(songName)}&duration=$durMs&hash=$hash"
                        val candText = httpGetText(candUrl, desktopUa) ?: continue
                        val candArr = try {
                            JSONObject(candText).optJSONArray("candidates")
                        } catch (e: Exception) {
                            null
                        }
                        if (candArr == null || candArr.length() == 0) continue
                        // チップ表示用に、文字同期（KRC）があるかも先に確認しておく
                        val hasWordSync = kugouCandidateHasWordSync(candArr, songName)
                        results.add(
                            LyricCandidate(
                                source = "Kugou",
                                title = songName,
                                artist = info.optString("singername", ""),
                                durationSec = info.optInt("duration", 0),
                                syncedLyrics = null,
                                kugouHash = hash,
                                wordSync = hasWordSync
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

    /**
     * Kugouの歌詞候補リストに、文字同期つき（KRC）の歌詞があるかを確認する。
     * 候補チップ表示用。ダウンロードまで試して、実際に文字タイミングが取れたときだけ true
     */
    private fun kugouCandidateHasWordSync(candidates: org.json.JSONArray, songTitle: String): Boolean {
        return try {
            fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
            var first = candidates.optJSONObject(0) ?: return false
            for (j in 0 until candidates.length()) {
                val cand = candidates.optJSONObject(j) ?: continue
                if (titleMatches(cand.optString("song", ""), songTitle)) {
                    first = cand
                    break
                }
            }
            val candId = first.optString("id", "")
            val accessKey = first.optString("accesskey", "")
            if (candId.isBlank() || accessKey.isBlank()) return false
            val dlUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$candId" +
                "&accesskey=$accessKey&fmt=krc&charset=utf8"
            val dlText = httpGetText(dlUrl, desktopUa) ?: return false
            val contentB64 = try {
                JSONObject(dlText).optString("content", "")
            } catch (e: Exception) {
                ""
            }
            if (contentB64.isBlank()) return false
            val krc = decodeKrc(contentB64) ?: return false
            val parsed = try {
                parseKrc(krc)
            } catch (e: Exception) {
                emptyList()
            }
            parsed.isNotEmpty() && parsed.any { it.words.isNotEmpty() }
        } catch (e: Exception) {
            false
        }
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

    /** タグ検索の候補（Kugou）から文字同期つき歌詞（KRCテキスト）を復号して取得する。無ければ null */
    suspend fun fetchCandidateKrc(candidate: LyricCandidate): String? = withContext(Dispatchers.IO) {
        if (candidate.source != "Kugou") return@withContext null
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
            "&accesskey=$accessKey&fmt=krc&charset=utf8"
        val dlText = httpGetText(dlUrl, desktopUa) ?: return@withContext null
        val contentB64 = try {
            JSONObject(dlText).optString("content", "")
        } catch (e: Exception) {
            ""
        }
        if (contentB64.isBlank()) return@withContext null
        val krc = decodeKrc(contentB64) ?: return@withContext null
        krc.takeIf { parseKrc(it).isNotEmpty() }
    }

    /** 手動で選んだ歌詞を、その曲のキャッシュとして保存する（次回から自動でこれが出る） */
    fun saveLyricsOverride(context: Context, title: String, artist: String, album: String, lrcText: String) {
        // 歌詞を適用したら「歌詞なし」指定は外す
        try { instrumentalFile(context, title, artist, album).delete() } catch (e: Exception) { }
        // 文字同期キャッシュも一旦消す（KRC候補を選んだ場合は、この後で保存し直される）
        try { File(cacheFile(context, title, artist, album).absolutePath + ".krc").delete() } catch (e: Exception) { }
        try {
            cacheFile(context, title, artist, album).writeText(lrcText, Charsets.UTF_8)
        } catch (e: Exception) {
            // 保存に失敗しても、表示への適用は呼び出し側で行う
        }
    }

    /**
     * 編集した歌詞を LRCLIB へ投稿する（公開データベースへの書き込み）。
     * スパム防止の計算パズル（SHA-256 の Proof of Work）を解いてから POST する。
     * 戻り値: null = 成功、それ以外はエラーメッセージ。
     */
    suspend fun publishToLrclib(title: String, artist: String, album: String, durationSec: Int, syncedLyrics: String): String? {
        if (durationSec <= 0) return "曲の長さが分からないため投稿できません"
        return withContext(Dispatchers.IO) {
            try {
                val ua = "NissinGeppoMusic/1.2 (https://github.com/sh4tter815/NissinGeppoMusic)"
                // 1) チャレンジ取得
                val chConn = (URL("https://lrclib.net/api/request-challenge").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("User-Agent", ua)
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                chConn.outputStream.use { it.write(ByteArray(0)) }
                if (chConn.responseCode != 200) return@withContext "チャレンジ取得に失敗しました (${chConn.responseCode})"
                val chJson = JSONObject(chConn.inputStream.bufferedReader().readText())
                val prefix = chJson.getString("prefix")
                val target = hexToBytes(chJson.getString("target")) ?: return@withContext "チャレンジの形式が不正です"
                // 2) Proof of Work: SHA-256(prefix + nonce) が target 未満になる nonce を探す（最大約4分）
                val md = MessageDigest.getInstance("SHA-256")
                var nonce = 0L
                var solved: String? = null
                val deadline = System.currentTimeMillis() + 240_000L
                while (System.currentTimeMillis() < deadline) {
                    val hash = md.digest((prefix + nonce).toByteArray(Charsets.UTF_8))
                    if (isLessThanUnsigned(hash, target)) {
                        solved = nonce.toString()
                        break
                    }
                    nonce++
                }
                val solvedNonce = solved ?: return@withContext "計算が時間切れになりました。もう一度お試しください"
                // 3) 投稿
                val plain = syncedLyrics.lines()
                    .map { it.replace(Regex("^\\[[^\\]]*\\]"), "").trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                val body = JSONObject()
                    .put("trackName", title)
                    .put("artistName", artist)
                    .put("albumName", album)
                    .put("duration", durationSec)
                    .put("plainLyrics", plain)
                    .put("syncedLyrics", syncedLyrics)
                    .toString()
                val conn = (URL("https://lrclib.net/api/publish").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Publish-Token", "$prefix:$solvedNonce")
                    setRequestProperty("User-Agent", ua)
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code == 201) null else "投稿に失敗しました ($code)"
            } catch (e: Exception) {
                "投稿に失敗しました (${e.javaClass.simpleName})"
            }
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val v = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
            out[i] = v.toByte()
        }
        return out
    }

    private fun isLessThanUnsigned(a: ByteArray, b: ByteArray): Boolean {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val av = a[i].toInt() and 0xFF
            val bv = b[i].toInt() and 0xFF
            if (av != bv) return av < bv
        }
        return a.size < b.size
    }

    /** 手編集した歌詞をキャッシュへ保存する（対訳キャッシュは行対応がずれるので消す） */
    fun saveEditedLyrics(context: Context, title: String, artist: String, album: String, lrcText: String) {
        saveLyricsOverride(context, title, artist, album, lrcText)
        try { File(cacheFile(context, title, artist, album).absolutePath + ".ja4").delete() } catch (e: Exception) { }
    }

    /** インスト曲（歌詞なし）指定を保存するマーカー */
    private fun instrumentalFile(context: Context, title: String, artist: String, album: String): File {
        val dir = File(context.filesDir, "lyrics")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, cacheKey(title, artist, album) + ".inst")
    }

    /** この曲に「歌詞なし（インスト）」指定が付いているか */
    fun isInstrumentalOverride(context: Context, title: String, artist: String, album: String): Boolean =
        instrumentalFile(context, title, artist, album).exists()

    /** 「歌詞なし（インスト）」として適用する。以後、この曲では歌詞を表示しない */
    fun saveInstrumentalOverride(context: Context, title: String, artist: String, album: String) {
        try {
            instrumentalFile(context, title, artist, album).writeText("instrumental", Charsets.UTF_8)
        } catch (e: Exception) {
            // 保存に失敗しても、表示への適用は呼び出し側で行う
        }
    }

    /** この曲に歌詞の指定（手動適用した歌詞キャッシュ、または歌詞なし指定）があるか */
    fun hasLyricsOverride(context: Context, title: String, artist: String, album: String): Boolean =
        cacheFile(context, title, artist, album).exists() || isInstrumentalOverride(context, title, artist, album)

    /** 歌詞を外して「歌詞なし」にする。以後、この曲では自動取得も走らない */
    fun removeLyricsToNone(context: Context, title: String, artist: String, album: String) {
        try { cacheFile(context, title, artist, album).delete() } catch (e: Exception) { }
        try { File(cacheFile(context, title, artist, album).absolutePath + ".ja4").delete() } catch (e: Exception) { }
        try { File(cacheFile(context, title, artist, album).absolutePath + ".nokrc").delete() } catch (e: Exception) { }
        try { File(cacheFile(context, title, artist, album).absolutePath + ".krc").delete() } catch (e: Exception) { }
        saveInstrumentalOverride(context, title, artist, album)
    }

    /** 歌詞の指定をすべて解除して、自動取得に戻す */
    fun clearLyricsOverride(context: Context, title: String, artist: String, album: String) {
        try { cacheFile(context, title, artist, album).delete() } catch (e: Exception) { }
        try { instrumentalFile(context, title, artist, album).delete() } catch (e: Exception) { }
        try { File(cacheFile(context, title, artist, album).absolutePath + ".ja4").delete() } catch (e: Exception) { }
        try { File(cacheFile(context, title, artist, album).absolutePath + ".nokrc").delete() } catch (e: Exception) { }
    }

    /** 歌詞の各行を日本語へ翻訳する（日本語を含む行は空文字＝訳なし）。結果はキャッシュする */
    // 冒頭のクレジット行（作詞・作曲など）は人名が誤訳されるので翻訳対象にしない
    private fun isCreditLine(text: String): Boolean {
        val t = text.trim()
        return LyricEffectsData.creditKeywords.any { t.contains(it) }
    }

    /** 文のまとまり（次の行が小文字で始まる間は同じ文）の行indexグループを返す */
    private fun translationGroups(lines: List<LyricLine>): List<List<Int>> {
        val translatable = lines.map { l ->
            l.text.isNotBlank() && !l.text.any { it in '\u3040'..'\u30FF' } && !isCreditLine(l.text)
        }
        fun endsSentence(t: String): Boolean {
            val s = t.trim().trimEnd('"', '”', '’', '\'', ')', '）')
            return s.isNotEmpty() && (s.last() == '.' || s.last() == '!' || s.last() == '?' || s.last() == '…')
        }
        val groups = mutableListOf<List<Int>>()
        var curGroup = mutableListOf<Int>()
        for (i in lines.indices) {
            if (!translatable[i]) {
                if (curGroup.isNotEmpty()) { groups.add(curGroup); curGroup = mutableListOf() }
                continue
            }
            if (curGroup.isNotEmpty()) {
                val nextLower = lines[i].text.trim().firstOrNull()?.isLowerCase() == true
                if (endsSentence(lines[curGroup.last()].text) || !nextLower) {
                    groups.add(curGroup); curGroup = mutableListOf()
                }
            }
            curGroup.add(i)
        }
        if (curGroup.isNotEmpty()) groups.add(curGroup)
        return groups
    }

    /**
     * 対訳を返す。訳は文のまとまり単位で作り、既定では先頭の行にだけ付ける。
     * fillChunkLines=true の時は、まとまりの全行に同じ訳を付ける（PV表示用）。
     */
    suspend fun translateToJapanese(
        context: Context,
        title: String,
        artist: String,
        album: String,
        lines: List<LyricLine>,
        fillChunkLines: Boolean = false,
        placeAtGroupEnd: Boolean = false
    ): List<String> {
        val head = translateToJapaneseHead(context, title, artist, album, lines)
        if (!fillChunkLines) {
            if (!placeAtGroupEnd) return head
            // まとまりの訳は末尾の行へ移す（全体歌詞で、文が終わる行の下に訳を出すため）
            val moved = head.toMutableList()
            for (g in translationGroups(lines)) {
                if (g.size > 1) {
                    val t = head.getOrNull(g.first()) ?: ""
                    if (t.isNotEmpty()) {
                        moved[g.first()] = ""
                        moved[g.last()] = t
                    }
                }
            }
            return moved
        }
        val filled = head.toMutableList()
        for (g in translationGroups(lines)) {
            val t = head.getOrNull(g.first()) ?: ""
            if (t.isNotEmpty()) for (i in g) filled[i] = t
        }
        return filled
    }

    private suspend fun translateToJapaneseHead(
        context: Context,
        title: String,
        artist: String,
        album: String,
        lines: List<LyricLine>
    ): List<String> = withContext(Dispatchers.IO) {
        val cache = File(cacheFile(context, title, artist, album).absolutePath + ".ja4")
        if (cache.exists()) {
            try {
                val cached = cache.readText(Charsets.UTF_8).split("\n")
                if (cached.size == lines.size) return@withContext cached
            } catch (e: Exception) {
                // キャッシュが読めなければ翻訳し直す
            }
        }
        val result = MutableList(lines.size) { "" }
        // 文が次の行へ続く間は、ひとまとまりで翻訳する（まとまりの訳は先頭の行にだけ付ける）
        val groups = translationGroups(lines)
        if (groups.isEmpty()) return@withContext result
        // 翻訳単位 = まとまり（先頭行のindex to スペースでつないだ原文）
        val targets = groups.map { g -> g.first() to g.joinToString(" ") { lines[it].text.trim() } }
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
                chunk.forEachIndexed { k, (lineIdx, src) ->
                    val tr = parts[k].trim()
                    result[lineIdx] = if (tr == src.trim()) "" else tr
                }
            } else {
                // 数がずれた場合は1単位ずつ翻訳し直す
                for ((lineIdx, text) in chunk) {
                    val one = translateTextGoogle(text)
                    if (one != null) {
                        val tr = one.trim()
                        result[lineIdx] = if (tr == text.trim()) "" else tr
                    }
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

