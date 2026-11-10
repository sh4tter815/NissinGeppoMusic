package com.example.nissingeppomusic.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable? = null,
    val iconBitmap: ImageBitmap? = null,
    val initialJapanese: String = "＃", // 「あ」「か」「さ」など
    val initialEnglish: String = "＃",   // "A", "B", "C" など
    val customYomi: String = "",
    val searchAliases: List<String> = emptyList(), // 検索可能な別名（複数保存可能）
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false
)

class AppRepository(private val context: Context) {
    val prefManager = AppPreferenceManager(context)

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val favoritesSet = prefManager.getFavoritePackages()
        val hiddenSet = prefManager.getHiddenPackages()
        val yomiMap = prefManager.getYomiMap()
        val aliasesMap = prefManager.getAliasesMap()

        resolveInfos.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(pm).toString().trim()
            if (label.isBlank()) return@mapNotNull null

            val isHidden = hiddenSet.contains(packageName)
            val isFavorite = favoritesSet.contains(packageName)
            val customYomi = yomiMap[packageName] ?: ""
            val searchAliases = aliasesMap[packageName] ?: emptyList()

            val targetForInitials = if (customYomi.isNotBlank()) customYomi else label

            val icon = try {
                resolveInfo.loadIcon(pm)
            } catch (_: Exception) {
                null
            }

            val iconBitmap = try {
                icon?.toBitmap()?.asImageBitmap()
            } catch (_: Exception) {
                null
            }

            val (japaneseInitial, englishInitial) = extractInitials(targetForInitials)

            AppInfo(
                label = label,
                packageName = packageName,
                icon = icon,
                iconBitmap = iconBitmap,
                initialJapanese = japaneseInitial,
                initialEnglish = englishInitial,
                customYomi = customYomi,
                searchAliases = searchAliases,
                isFavorite = isFavorite,
                isHidden = isHidden
            )
        }.sortedBy { app ->
            val rawKey = if (app.customYomi.isNotBlank()) app.customYomi else app.label
            toHiragana(rawKey.lowercase())
        }
    }

    fun setFavorite(packageName: String, isFavorite: Boolean) {
        prefManager.setFavoritePackage(packageName, isFavorite)
    }

    fun setHidden(packageName: String, isHidden: Boolean) {
        prefManager.setHiddenPackage(packageName, isHidden)
    }

    fun setYomi(packageName: String, yomi: String) {
        prefManager.setYomi(packageName, yomi)
    }

    fun addSearchAlias(packageName: String, alias: String) {
        prefManager.addAlias(packageName, alias)
    }

    fun removeSearchAlias(packageName: String, alias: String) {
        prefManager.removeAlias(packageName, alias)
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
