package com.example.nissingeppomusic.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

typealias AppRepository = MusicRepository

class MusicRepository(private val context: Context) {
    val prefManager = AppPreferenceManager(context)

    companion object {
        private var cachedTracks: List<AppInfo>? = null
        private var cachedRecords: List<TrackRecord>? = null
        private val albumArtCache = LruCache<Long, Bitmap>(100)

        fun invalidateCache() {
            cachedTracks = null
        }
    }

    /** 永続化できる曲メタデータ（Bitmapは含まない） */
    private data class TrackRecord(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: Long,
        val filePath: String,
        val uriString: String,
        val dateModified: Long,
        val track: Int = 0
    ) {
        /** アートの保存キー。アルバム単位を基本にし、無い曲は曲単位 */
        fun artKey(): String = if (albumId > 0) "album_$albumId" else "track_$id"
        fun memKey(): Long = if (albumId > 0) albumId else -id
    }

    suspend fun getInstalledApps(): List<AppInfo> = getMusicTracks()

    suspend fun getMusicTracks(forceRefresh: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedTracks != null) {
            return@withContext cachedTracks!!
        }

        var records = cachedRecords
        if (records == null) {
            val diskRecords = loadRecordsFromDisk()
            if (diskRecords != null) {
                // 保存済みライブラリを読み、差分だけ最新化する
                records = applyDiff(diskRecords) ?: diskRecords
            } else {
                // 初回（保存データなし）はフルスキャン
                records = fullScan()
            }
            cachedRecords = records
        } else if (forceRefresh) {
            // 設定画面の「ライブラリを更新」など: 差分更新のみ行う
            val updated = applyDiff(records)
            if (updated != null) {
                records = updated
                cachedRecords = updated
            }
            // 除外を解除した曲など、アートが未保存の曲があれば補完する
            records.forEach {
                if (!isExcluded(it.filePath)) loadArtBitmap(it, allowSourceDecode = true)
            }
        }

        val result = buildAppInfoList(records)
        cachedTracks = result
        result
    }

    // ---------------- スキャン / 差分更新 ----------------

    private fun fullScan(): List<TrackRecord> {
        val records = queryTrackRecords(null, null)
        // 初回だけアートを全曲分デコードしてアプリ内に保存する（除外フォルダ配下は除く）
        records.forEach {
            if (!isExcluded(it.filePath)) loadArtBitmap(it, allowSourceDecode = true, forceRetry = true)
        }
        saveRecordsToDisk(records)
        return records
    }

    /** 指定フォルダ配下の曲かどうか（除外フォルダ判定） */
    private fun isExcluded(filePath: String): Boolean {
        if (filePath.isEmpty()) return false
        val normalized = filePath.replace('\\', '/')
        return prefManager.getExcludedFolders().any { folder ->
            val prefix = folder.trimEnd('/')
            prefix.isNotEmpty() && (normalized == prefix || normalized.startsWith("$prefix/"))
        }
    }

    /**
     * MediaStoreの (ID, DATE_MODIFIED) だけを軽く問い合わせて差分を検出し、
     * 追加・変更された曲だけを取り直す。変化がなければ null を返す。
     */
    private fun applyDiff(old: List<TrackRecord>): List<TrackRecord>? {
        val current = queryIdAndDateModified() ?: return null
        val oldById = old.associateBy { it.id }

        val removedIds = oldById.keys - current.keys
        val changedIds = current.entries
            .filter { (id, dateModified) -> oldById[id]?.dateModified != dateModified }
            .map { it.key }

        if (removedIds.isEmpty() && changedIds.isEmpty()) return null

        val fetched = if (changedIds.isEmpty()) {
            emptyList()
        } else {
            changedIds.chunked(400).flatMap { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                val args = chunk.map { it.toString() }.toTypedArray()
                queryTrackRecords("${MediaStore.Audio.Media._ID} IN ($placeholders)", args)
            }
        }
        // 追加・変更された曲のアートだけデコードして保存する（除外フォルダ配下は除く）
        fetched.forEach {
            if (!isExcluded(it.filePath)) loadArtBitmap(it, allowSourceDecode = true, forceRetry = true)
        }

        val changedSet = changedIds.toSet()
        val merged = old.filter { it.id !in removedIds && it.id !in changedSet } + fetched

        // 使われなくなったアートファイルを掃除する
        val validKeys = merged.map { it.artKey() }.toSet()
        try {
            artDir().listFiles()?.forEach { file ->
                if (file.nameWithoutExtension !in validKeys) file.delete()
            }
        } catch (_: Exception) {}

        saveRecordsToDisk(merged)
        return merged
    }

    private fun queryIdAndDateModified(): Map<Long, Long>? {
        val collection = audioCollection()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        return try {
            val map = mutableMapOf<Long, Long>()
            context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                while (cursor.moveToNext()) {
                    map[cursor.getLong(idCol)] = cursor.getLong(dateCol)
                }
            }
            map
        } catch (_: Exception) {
            null
        }
    }

    private fun queryTrackRecords(extraSelection: String?, selectionArgs: Array<String>?): List<TrackRecord> {
        val collection = audioCollection()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = if (extraSelection == null) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        } else {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ($extraSelection)"
        }

        val records = mutableListOf<TrackRecord>()
        try {
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = (cursor.getString(titleCol) ?: "Unknown Title").trim()
                    if (title.isBlank()) continue
                    records.add(
                        TrackRecord(
                            id = id,
                            title = title,
                            artist = cursor.getString(artistCol) ?: "Unknown Artist",
                            album = cursor.getString(albumCol) ?: "Unknown Album",
                            albumId = cursor.getLong(albumIdCol),
                            filePath = cursor.getString(dataCol) ?: "",
                            uriString = ContentUris.withAppendedId(collection, id).toString(),
                            dateModified = cursor.getLong(dateCol),
                            track = cursor.getInt(trackCol)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return records
    }

    private fun audioCollection(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    // ---------------- AppInfo 構築 ----------------

    private fun buildAppInfoList(records: List<TrackRecord>): List<AppInfo> {
        val favoritesSet = prefManager.getFavoritePackages()
        val hiddenSet = prefManager.getHiddenPackages()
        val yomiMap = prefManager.getYomiMap()
        val aliasesMap = prefManager.getAliasesMap()
        val sortMode = prefManager.getLibrarySortMode()

        // 除外フォルダ配下の曲はライブラリに表示しない
        val visibleRecords = records.filter { !isExcluded(it.filePath) }

        val tracks = visibleRecords.map { rec ->
            val isHidden = hiddenSet.contains(rec.uriString)
            val isFavorite = favoritesSet.contains(rec.uriString)
            val customYomi = yomiMap[rec.uriString] ?: ""
            val searchAliases = aliasesMap[rec.uriString] ?: emptyList()
            val targetForInitials = when (sortMode) {
                "artist" -> rec.artist
                "album" -> rec.album
                else -> if (customYomi.isNotBlank()) customYomi else rec.title
            }

            // 起動時はアプリ内保存済みアートだけを使う（MediaStoreから再デコードしない）
            val albumArtBitmap = loadArtBitmap(rec, allowSourceDecode = false)
            val (japaneseInitial, englishInitial) = extractInitials(targetForInitials)

            AppInfo(
                label = rec.title,
                packageName = rec.uriString,
                icon = null,
                iconBitmap = albumArtBitmap?.asImageBitmap(),
                albumArt = albumArtBitmap,
                artist = rec.artist,
                album = rec.album,
                filePath = rec.filePath,
                trackNumber = rec.track,
                initialJapanese = japaneseInitial,
                initialEnglish = englishInitial,
                customYomi = customYomi,
                searchAliases = searchAliases,
                isFavorite = isFavorite,
                isHidden = isHidden
            )
        }

        fun sortKeyOf(song: AppInfo): String = when (sortMode) {
            "artist" -> song.artist
            "album" -> song.album
            else -> if (song.customYomi.isNotBlank()) song.customYomi else song.label
        }
        return tracks.sortedWith(
            compareBy(
                { toHiragana(sortKeyOf(it).lowercase()) },
                { toHiragana((if (it.customYomi.isNotBlank()) it.customYomi else it.label).lowercase()) }
            )
        )
    }

    // ---------------- 永続化（メタデータ） ----------------

    private fun cacheFile(): File = File(context.filesDir, "music_library_cache.json")

    private fun saveRecordsToDisk(records: List<TrackRecord>) {
        try {
            val arr = JSONArray()
            records.forEach { r ->
                arr.put(JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("artist", r.artist)
                    put("album", r.album)
                    put("albumId", r.albumId)
                    put("path", r.filePath)
                    put("uri", r.uriString)
                    put("dateModified", r.dateModified)
                    put("track", r.track)
                })
            }
            val root = JSONObject().apply {
                put("version", 2)
                put("tracks", arr)
            }
            cacheFile().writeText(root.toString())
        } catch (_: Exception) {}
    }

    private fun loadRecordsFromDisk(): List<TrackRecord>? {
        return try {
            val file = cacheFile()
            if (!file.exists()) return null
            val root = JSONObject(file.readText())
            if (root.optInt("version") != 2) return null
            val arr = root.getJSONArray("tracks")
            val list = mutableListOf<TrackRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    TrackRecord(
                        id = o.getLong("id"),
                        title = o.optString("title"),
                        artist = o.optString("artist"),
                        album = o.optString("album"),
                        albumId = o.optLong("albumId"),
                        filePath = o.optString("path"),
                        uriString = o.optString("uri"),
                        dateModified = o.optLong("dateModified"),
                        track = o.optInt("track")
                    )
                )
            }
            list
        } catch (_: Exception) {
            null
        }
    }

    // ---------------- アルバムアート（アプリ内保存） ----------------

    private fun artDir(): File = File(context.filesDir, "album_art").apply { mkdirs() }

    private fun artFile(artKey: String): File = File(artDir(), "$artKey.jpg")

    /**
     * アート取得の順序: メモリ → アプリ内保存ファイル → （許可時のみ）MediaStore等からデコードして保存。
     * allowSourceDecode=false のときは保存済みファイルのみを使い、起動を高速に保つ。
     * デコードに失敗した曲には「アートなし」マーカー (.none) を置き、毎回の再試行を防ぐ。
     * forceRetry=true のときはマーカーを無視して再デコードする（スキャン・差分更新で取り直した曲用）。
     */
    private fun loadArtBitmap(rec: TrackRecord, allowSourceDecode: Boolean, forceRetry: Boolean = false): Bitmap? {
        val memKey = rec.memKey()
        albumArtCache.get(memKey)?.let { return it }

        val file = artFile(rec.artKey())
        if (file.exists()) {
            val fromDisk = try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) { null }
            if (fromDisk != null) {
                albumArtCache.put(memKey, fromDisk)
                return fromDisk
            }
        }

        val noArtMarker = File(artDir(), "${rec.artKey()}.none")
        if (noArtMarker.exists() && !forceRetry) return null
        if (!allowSourceDecode) return null

        val decoded = decodeArtFromSource(rec.albumId, rec.filePath)
        if (decoded == null) {
            try { noArtMarker.createNewFile() } catch (_: Exception) {}
            return null
        }
        val scaled = if (decoded.width > 300 || decoded.height > 300) {
            Bitmap.createScaledBitmap(decoded, 300, 300, true)
        } else decoded

        try {
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (noArtMarker.exists()) noArtMarker.delete()
        } catch (_: Exception) {}

        albumArtCache.put(memKey, scaled)
        return scaled
    }

    private fun decodeArtFromSource(albumId: Long, filePath: String): Bitmap? {
        val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, albumArtUri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, albumArtUri)
            }
        } catch (_: Exception) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                val artBytes = retriever.embeddedPicture
                retriever.release()
                if (artBytes != null) {
                    BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    // ---------------- お気に入り・非表示・読み仮名など ----------------

    fun setFavorite(songUri: String, isFavorite: Boolean) {
        prefManager.setFavoritePackage(songUri, isFavorite)
        invalidateCache()
    }

    fun setHidden(songUri: String, isHidden: Boolean) {
        prefManager.setHiddenPackage(songUri, isHidden)
        invalidateCache()
    }

    fun setYomi(songUri: String, yomi: String) {
        prefManager.setYomi(songUri, yomi)
        invalidateCache()
    }

    fun addSearchAlias(songUri: String, alias: String) {
        prefManager.addAlias(songUri, alias)
        invalidateCache()
    }

    fun removeSearchAlias(songUri: String, alias: String) {
        prefManager.removeAlias(songUri, alias)
        invalidateCache()
    }

    private fun extractInitials(label: String): Pair<String, String> {
        val firstChar = label.firstOrNull() ?: return Pair("＃", "＃")

        val englishInitial = if (firstChar.isLetter() && firstChar.code < 128) {
            firstChar.uppercaseChar().toString()
        } else {
            "＃"
        }

        val japaneseInitial = getJapaneseGroup(firstChar)

        return Pair(japaneseInitial, englishInitial)
    }

    private fun getJapaneseGroup(ch: Char): String {
        val code = ch.code
        val hiraChar = when (code) {
            in 0x30A1..0x30FA -> (code - 0x60).toChar()
            else -> ch
        }

        return when (hiraChar) {
            'あ', 'い', 'う', 'え', 'お', 'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ' -> "あ"
            'か', 'き', 'く', 'け', 'こ', 'が', 'ぎ', 'ぐ', 'げ', 'ご' -> "か"
            'さ', 'し', 'す', 'せ', 'そ', 'ざ', 'じ', 'ず', 'ぜ', 'ぞ' -> "さ"
            'た', 'ち', 'つ', 'て', 'と', 'だ', 'ぢ', 'づ', 'で', 'ど', 'っ' -> "た"
            'な', 'に', 'ぬ', 'ね', 'の' -> "な"
            'は', 'ひ', 'ふ', 'へ', 'ほ', 'ば', 'び', 'ぶ', 'べ', 'ぼ', 'ぱ', 'ぴ', 'ぷ', 'ぺ', 'ぽ' -> "は"
            'ま', 'み', 'む', 'め', 'も' -> "ま"
            'や', 'ゆ', 'よ', 'ゃ', 'ゅ', 'ょ' -> "や"
            'ら', 'り', 'る', 'れ', 'ろ' -> "ら"
            'わ', 'を', 'ん' -> "わ"
            else -> {
                if (ch.isLetter() && ch.code < 128) ch.uppercaseChar().toString() else "＃"
            }
        }
    }

    private fun toHiragana(text: String): String {
        return text.map { ch ->
            val code = ch.code
            if (code in 0x30A1..0x30FA) (code - 0x60).toChar() else ch
        }.joinToString("")
    }
}
