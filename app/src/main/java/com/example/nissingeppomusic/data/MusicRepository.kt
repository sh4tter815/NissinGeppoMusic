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

typealias AppRepository = MusicRepository

class MusicRepository(private val context: Context) {
    val prefManager = AppPreferenceManager(context)

    companion object {
        private var cachedTracks: List<AppInfo>? = null
        private val albumArtCache = LruCache<Long, Bitmap>(100)

        fun invalidateCache() {
            cachedTracks = null
        }
    }

    suspend fun getInstalledApps(): List<AppInfo> = getMusicTracks()

    suspend fun getMusicTracks(forceRefresh: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedTracks != null) {
            return@withContext cachedTracks!!
        }

        val tracks = mutableListOf<AppInfo>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE
        )

        // Only music files
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val favoritesSet = prefManager.getFavoritePackages()
        val hiddenSet = prefManager.getHiddenPackages()
        val yomiMap = prefManager.getYomiMap()
        val aliasesMap = prefManager.getAliasesMap()

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol) ?: "Unknown Title"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
                    val filePath = cursor.getString(dataCol) ?: ""

                    val title = rawTitle.trim()
                    if (title.isBlank()) continue

                    val songUri = ContentUris.withAppendedId(collection, id).toString()

                    val isHidden = hiddenSet.contains(songUri)
                    val isFavorite = favoritesSet.contains(songUri)
                    val customYomi = yomiMap[songUri] ?: ""
                    val searchAliases = aliasesMap[songUri] ?: emptyList()

                    val targetForInitials = if (customYomi.isNotBlank()) customYomi else title

                    // Load album art with memory cache & downscaling
                    val albumArtBitmap = loadAlbumArtCached(context, albumId, filePath)
                    val iconBitmap = albumArtBitmap?.asImageBitmap()

                    val (japaneseInitial, englishInitial) = extractInitials(targetForInitials)

                    tracks.add(
                        AppInfo(
                            label = title,
                            packageName = songUri,
                            icon = null,
                            iconBitmap = iconBitmap,
                            albumArt = albumArtBitmap,
                            artist = artist,
                            album = album,
                            filePath = filePath,
                            initialJapanese = japaneseInitial,
                            initialEnglish = englishInitial,
                            customYomi = customYomi,
                            searchAliases = searchAliases,
                            isFavorite = isFavorite,
                            isHidden = isHidden
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        val sorted = tracks.sortedBy { song ->
            val rawKey = if (song.customYomi.isNotBlank()) song.customYomi else song.label
            toHiragana(rawKey.lowercase())
        }
        cachedTracks = sorted
        sorted
    }

    private fun loadAlbumArtCached(context: Context, albumId: Long, filePath: String): Bitmap? {
        if (albumId > 0) {
            val cached = albumArtCache.get(albumId)
            if (cached != null) return cached
        }

        val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
        val bitmap = try {
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

        if (bitmap != null && albumId > 0) {
            val scaled = if (bitmap.width > 300 || bitmap.height > 300) {
                Bitmap.createScaledBitmap(bitmap, 300, 300, true)
            } else bitmap
            albumArtCache.put(albumId, scaled)
            return scaled
        }
        return bitmap
    }

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
