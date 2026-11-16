package com.example.nissingeppomusic.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// 歌詞エフェクトの単語データ（アイコン・色・カタカナ英訳の対応表）の配信ストア。
// 正本はランチャーのリポジトリ直下の lyric_effects.json。起動時に取得して端末へキャッシュし、
// 取得できないときは内蔵デフォルト（このファイルの DEFAULT_*、JSON の v1 と同内容）を使う。
// 設定の「歌詞エフェクトの更新」から手動でも更新できる。
object LyricEffectsData {
    private const val DATA_URL = "https://raw.githubusercontent.com/sh4tter815/NissinGeppoLauncher/master/lyric_effects.json"
    private const val CACHE_FILE = "lyric_effects.json"

    private val DEFAULT_ICON_WORDS: List<Pair<String, Int>> = listOf(
    "傘" to LyricIcon.UMBRELLA,
    "桜" to LyricIcon.FLOWER,
    "雨" to LyricIcon.RAIN,
    "凍" to LyricIcon.SNOW,
    "氷" to LyricIcon.SNOW,
    "雪" to LyricIcon.SNOW,
    "星" to LyricIcon.STAR,
    "月" to LyricIcon.MOON,
    "太陽" to LyricIcon.SUN,
    "雲" to LyricIcon.CLOUD,
    "花" to LyricIcon.FLOWER,
    "心" to LyricIcon.HEART,
    "ハート" to LyricIcon.HEART,
    "恋" to LyricIcon.HEART,
    "愛" to LyricIcon.HEART,
    "夜" to LyricIcon.MOON,
    "鍵" to LyricIcon.KEY,
    "音楽" to LyricIcon.NOTE,
    "歌" to LyricIcon.NOTE,
    "手紙" to LyricIcon.ENVELOPE,
    "時計" to LyricIcon.CLOCK,
    "炎" to LyricIcon.FLAME,
    "火" to LyricIcon.FLAME,
    "涙" to LyricIcon.DROP,
    "雫" to LyricIcon.DROP,
    "虹" to LyricIcon.RAINBOW,
    "雷" to LyricIcon.LIGHTNING,
    "笑顔" to LyricIcon.SMILE,
    "魚" to LyricIcon.FISH,
    "山" to LyricIcon.MOUNTAIN,
    "風" to LyricIcon.WIND,
    "音" to LyricIcon.NOTE,
    "濡" to LyricIcon.DROP,
    "時間" to LyricIcon.CLOCK,
    "場所" to LyricIcon.MAP,
    "死" to LyricIcon.COFFIN,
    "傷" to LyricIcon.BANDAGE,
    "キズ" to LyricIcon.BANDAGE,
    "駄目" to LyricIcon.CROSS,
    "ダメ" to LyricIcon.CROSS,
    "見ている" to LyricIcon.EYE,
    "見てる" to LyricIcon.EYE,
    "見る" to LyricIcon.EYE,
    "瞳" to LyricIcon.EYE,
    "目" to LyricIcon.EYE,
    "水" to LyricIcon.DROP,
    "増" to LyricIcon.PLUS,
    "減" to LyricIcon.MINUS,
    "骨" to LyricIcon.BONE
    )
    private val DEFAULT_ICON_WORDS_EN: List<Pair<String, Int>> = listOf(
    "umbrella" to LyricIcon.UMBRELLA,
    "rainbow" to LyricIcon.RAINBOW,
    "rain" to LyricIcon.RAIN,
    "snow" to LyricIcon.SNOW,
    "star" to LyricIcon.STAR,
    "moon" to LyricIcon.MOON,
    "sun" to LyricIcon.SUN,
    "cloud" to LyricIcon.CLOUD,
    "flower" to LyricIcon.FLOWER,
    "sea" to LyricIcon.WAVES,
    "wave" to LyricIcon.WAVES,
    "heart" to LyricIcon.HEART,
    "love" to LyricIcon.HEART,
    "night" to LyricIcon.MOON,
    "key" to LyricIcon.KEY,
    "music" to LyricIcon.NOTE,
    "song" to LyricIcon.NOTE,
    "sound" to LyricIcon.NOTE,
    "letter" to LyricIcon.ENVELOPE,
    "clock" to LyricIcon.CLOCK,
    "time" to LyricIcon.CLOCK,
    "fire" to LyricIcon.FLAME,
    "flame" to LyricIcon.FLAME,
    "tear" to LyricIcon.DROP,
    "thunder" to LyricIcon.LIGHTNING,
    "smile" to LyricIcon.SMILE,
    "fish" to LyricIcon.FISH,
    "mountain" to LyricIcon.MOUNTAIN,
    "wind" to LyricIcon.WIND,
    "eye" to LyricIcon.EYE,
    "bone" to LyricIcon.BONE,
    "map" to LyricIcon.MAP,
    "death" to LyricIcon.COFFIN
    )
    private val DEFAULT_ICON_EXCLUSIONS: Map<String, List<String>> = mapOf(
    "心" to listOf("心配"),
    "山" to listOf("沢山")
    )
    private val DEFAULT_COLOR_WORDS: List<Pair<String, Color>> = listOf(
    "水色" to Color(0xFF7FD4F2),
    "ピンク" to Color(0xFFFF8FBF),
    "オレンジ" to Color(0xFFFF9A3D),
    "グレー" to Color(0xFF9AA0A6),
    "群青" to Color(0xFF3A5FCD),
    "瑠璃" to Color(0xFF1F4788),
    "翡翠" to Color(0xFF38B48B),
    "琥珀" to Color(0xFFFFB000),
    "山吹" to Color(0xFFF8A500),
    "萌葱" to Color(0xFF2E9E6B),
    "紅" to Color(0xFFE60033),
    "藍" to Color(0xFF2B4C9B),
    "朱" to Color(0xFFFF4D2E),
    "緋" to Color(0xFFE8383F),
    "茜" to Color(0xFFB7282E),
    "藤" to Color(0xFFA58FCE),
    "青" to Color(0xFF3D7BFF),
    "赤" to Color(0xFFFF4545),
    "白" to Color(0xFFF2F3F5),
    "黒" to Color(0xFF26262C),
    "紫" to Color(0xFFB07FE8),
    "緑" to Color(0xFF4FC96B),
    "黄" to Color(0xFFFFD94D),
    "金" to Color(0xFFFFC94D),
    "銀" to Color(0xFFC9CED6),
    "茶" to Color(0xFFB07B4F),
    "灰" to Color(0xFF9AA0A6),
    "red" to Color(0xFFFF4545),
    "blue" to Color(0xFF3D7BFF),
    "green" to Color(0xFF4FC96B),
    "yellow" to Color(0xFFFFD94D),
    "pink" to Color(0xFFFF8FBF),
    "purple" to Color(0xFFB07FE8),
    "violet" to Color(0xFF8A6FE8),
    "black" to Color(0xFF26262C),
    "white" to Color(0xFFF2F3F5),
    "orange" to Color(0xFFFF9A3D),
    "gray" to Color(0xFF9AA0A6),
    "grey" to Color(0xFF9AA0A6),
    "brown" to Color(0xFFB07B4F),
    "gold" to Color(0xFFFFC94D),
    "silver" to Color(0xFFC9CED6),
    "cyan" to Color(0xFF4FD8E8),
    "magenta" to Color(0xFFFF4FD8),
    "crimson" to Color(0xFFE60033),
    "indigo" to Color(0xFF2B4C9B),
    "emerald" to Color(0xFF38B48B),
    "scarlet" to Color(0xFFE8383F),
    "amber" to Color(0xFFFFB000)
    )
    private val DEFAULT_ASSOC_COLOR_WORDS: List<Pair<String, Color>> = listOf(
    "恥" to Color(0xFFFF8FBF),
    "愛" to Color(0xFFFF8FBF),
    "恋" to Color(0xFFFF8FBF),
    "好" to Color(0xFFFF8FBF),
    "心" to Color(0xFFFF8FBF),
    "桜" to Color(0xFFFF8FBF),
    "血" to Color(0xFFFF4545),
    "宵闇" to Color(0xFF2B4C9B),
    "闇" to Color(0xFF000000),
    "夜" to Color(0xFF2B4C9B),
    "海" to Color(0xFF3D7BFF),
    "嫌" to Color(0xFF3D7BFF),
    "悲" to Color(0xFF3D7BFF),
    "哀" to Color(0xFF3D7BFF),
    "月" to Color(0xFFFFC94D),
    "星" to Color(0xFFFFC94D),
    "涙" to Color(0xFF7FD4F2),
    "空" to Color(0xFF7FD4F2),
    "凍" to Color(0xFF7FD4F2),
    "氷" to Color(0xFF7FD4F2),
    "想い" to Color(0xFFFF8FBF),
    "炎" to Color(0xFFFF9A3D),
    "熱" to Color(0xFFFF9A3D),
    "火" to Color(0xFFFF9A3D),
    "砂" to Color(0xFFFFC94D),
    "汚" to Color(0xFFA9744F),
    "希望" to Color(0xFFFFE14D),
    "殺" to Color(0xFFFF4545),
    "死" to Color(0xFFFF4545),
    "地獄" to Color(0xFFFF4545),
    "行方不明" to Color(0xFFFF4545),
    "不明" to Color(0xFFFF4545),
    "不安" to Color(0xFFFF4545),
    "危険" to Color(0xFFFF4545),
    "怖" to Color(0xFFFF4545),
    "恐" to Color(0xFFFF4545),
    "無事" to Color(0xFF4CD964),
    "安心" to Color(0xFF4CD964),
    "安全" to Color(0xFF4CD964),
    "大丈夫" to Color(0xFF4CD964),
    "love" to Color(0xFFFF8FBF),
    "heart" to Color(0xFFFF8FBF),
    "blood" to Color(0xFFFF4545),
    "night" to Color(0xFF2B4C9B),
    "moon" to Color(0xFFFFC94D),
    "star" to Color(0xFFFFC94D),
    "sea" to Color(0xFF3D7BFF),
    "hate" to Color(0xFF3D7BFF),
    "sad" to Color(0xFF3D7BFF),
    "sky" to Color(0xFF7FD4F2),
    "tear" to Color(0xFF7FD4F2),
    "tears" to Color(0xFF7FD4F2),
    "fire" to Color(0xFFFF9A3D)
    )
    private val DEFAULT_KATAKANA_EN: Map<String, String> = mapOf(
    "ハート" to "HEART",
    "ラブ" to "LOVE",
    "キス" to "KISS",
    "ドリーム" to "DREAM",
    "ライト" to "LIGHT",
    "ナイト" to "NIGHT",
    "スター" to "STAR",
    "ムーン" to "MOON",
    "スカイ" to "SKY",
    "ミュージック" to "MUSIC",
    "ソング" to "SONG",
    "ダンス" to "DANCE",
    "ロック" to "ROCK",
    "ポップ" to "POP",
    "ビート" to "BEAT",
    "リズム" to "RHYTHM",
    "メロディ" to "MELODY",
    "ハーモニー" to "HARMONY",
    "ボイス" to "VOICE",
    "サウンド" to "SOUND",
    "ワールド" to "WORLD",
    "ライフ" to "LIFE",
    "タイム" to "TIME",
    "モーメント" to "MOMENT",
    "メモリー" to "MEMORY",
    "ストーリー" to "STORY",
    "フューチャー" to "FUTURE",
    "プロミス" to "PROMISE",
    "シークレット" to "SECRET",
    "ミステリー" to "MYSTERY",
    "マジック" to "MAGIC",
    "パワー" to "POWER",
    "エナジー" to "ENERGY",
    "ファイア" to "FIRE",
    "ウォーター" to "WATER",
    "レイン" to "RAIN",
    "スノー" to "SNOW",
    "フラワー" to "FLOWER",
    "バタフライ" to "BUTTERFLY",
    "エンジェル" to "ANGEL",
    "デビル" to "DEVIL",
    "ゴースト" to "GHOST",
    "シャドウ" to "SHADOW",
    "シャイン" to "SHINE",
    "ダーク" to "DARK",
    "カラー" to "COLOR",
    "ブルー" to "BLUE",
    "ピンク" to "PINK",
    "ブラック" to "BLACK",
    "ホワイト" to "WHITE",
    "ゴールド" to "GOLD",
    "シルバー" to "SILVER",
    "クリスタル" to "CRYSTAL",
    "ダイヤモンド" to "DIAMOND",
    "プラズマ" to "PLASMA",
    "ブラックアウト" to "BLACKOUT",
    "テレビ" to "TV",
    "ラジオ" to "RADIO",
    "ゲーム" to "GAME",
    "ヒーロー" to "HERO",
    "クイーン" to "QUEEN",
    "プリンセス" to "PRINCESS",
    "ベイビー" to "BABY",
    "ダーリン" to "DARLING",
    "ハニー" to "HONEY",
    "フレンド" to "FRIEND",
    "ラバー" to "LOVER",
    "エンド" to "END",
    "スタート" to "START",
    "ゴール" to "GOAL",
    "ドア" to "DOOR",
    "キー" to "KEY",
    "ロード" to "ROAD",
    "シティ" to "CITY",
    "ホーム" to "HOME",
    "ミラー" to "MIRROR",
    "スマイル" to "SMILE",
    "ソウル" to "SOUL",
    "マインド" to "MIND",
    "ステージ" to "STAGE",
    "ライブ" to "LIVE",
    "パーティ" to "PARTY",
    "カフェ" to "CAFE",
    "ホテル" to "HOTEL",
    "レター" to "LETTER",
    "カード" to "CARD",
    "チケット" to "TICKET",
    "ドレス" to "DRESS",
    "リング" to "RING",
    "ブック" to "BOOK",
    "シーズン" to "SEASON",
    "サマー" to "SUMMER",
    "ウィンター" to "WINTER",
    "モーニング" to "MORNING",
    "ミッドナイト" to "MIDNIGHT",
    "トゥモロー" to "TOMORROW",
    "イエスタデイ" to "YESTERDAY",
    "フォーエバー" to "FOREVER",
    "パラダイス" to "PARADISE",
    "ヘブン" to "HEAVEN",
    "アース" to "EARTH",
    "オーシャン" to "OCEAN",
    "リバー" to "RIVER",
    "フォレスト" to "FOREST"
    )

    @Volatile var iconWords: List<Pair<String, Int>> = DEFAULT_ICON_WORDS; private set
    @Volatile var iconWordsEn: List<Pair<String, Int>> = DEFAULT_ICON_WORDS_EN; private set
    @Volatile var iconExclusions: Map<String, List<String>> = DEFAULT_ICON_EXCLUSIONS; private set
    @Volatile var colorWords: List<Pair<String, Color>> = DEFAULT_COLOR_WORDS; private set
    @Volatile var assocColorWords: List<Pair<String, Color>> = DEFAULT_ASSOC_COLOR_WORDS; private set
    @Volatile var katakanaEn: Map<String, String> = DEFAULT_KATAKANA_EN; private set
    @Volatile var appliedVersion: Int = 4; private set
    @Volatile private var appliedRaw: String = ""

    private val ICON_BY_NAME: Map<String, Int> = mapOf(
"UMBRELLA" to LyricIcon.UMBRELLA,
        "RAIN" to LyricIcon.RAIN,
        "SNOW" to LyricIcon.SNOW,
        "STAR" to LyricIcon.STAR,
        "MOON" to LyricIcon.MOON,
        "SUN" to LyricIcon.SUN,
        "CLOUD" to LyricIcon.CLOUD,
        "FLOWER" to LyricIcon.FLOWER,
        "WAVES" to LyricIcon.WAVES,
        "HEART" to LyricIcon.HEART,
        "KEY" to LyricIcon.KEY,
        "NOTE" to LyricIcon.NOTE,
        "ENVELOPE" to LyricIcon.ENVELOPE,
        "CLOCK" to LyricIcon.CLOCK,
        "FLAME" to LyricIcon.FLAME,
        "DROP" to LyricIcon.DROP,
        "RAINBOW" to LyricIcon.RAINBOW,
        "LIGHTNING" to LyricIcon.LIGHTNING,
        "SMILE" to LyricIcon.SMILE,
        "FISH" to LyricIcon.FISH,
        "MOUNTAIN" to LyricIcon.MOUNTAIN,
        "WIND" to LyricIcon.WIND,
        "LINE" to LyricIcon.LINE,
        "CIRCLE" to LyricIcon.CIRCLE,
        "GLOBE" to LyricIcon.GLOBE,
        "BUBBLE" to LyricIcon.BUBBLE,
        "COFFIN" to LyricIcon.COFFIN,
        "EYE" to LyricIcon.EYE,
        "PLUS" to LyricIcon.PLUS,
        "MINUS" to LyricIcon.MINUS,
        "MAP" to LyricIcon.MAP,
        "BONE" to LyricIcon.BONE,
        "CROSS" to LyricIcon.CROSS,
        "BANDAGE" to LyricIcon.BANDAGE
    )

    private fun parseIconPairs(arr: JSONArray): List<Pair<String, Int>>? {
        val out = mutableListOf<Pair<String, Int>>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONArray(i) ?: return null
            val word = p.optString(0)
            val kind = ICON_BY_NAME[p.optString(1)] ?: return null
            if (word.isEmpty()) return null
            out.add(word to kind)
        }
        return out
    }

    private fun parseColors(arr: JSONArray): List<Pair<String, Color>>? {
        val out = mutableListOf<Pair<String, Color>>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONArray(i) ?: return null
            val word = p.optString(0)
            val hex = p.optString(1).removePrefix("#")
            val rgb = hex.toLongOrNull(16) ?: return null
            if (word.isEmpty() || hex.length != 6) return null
            out.add(word to Color(0xFF000000L or rgb))
        }
        return out
    }

    // JSON を検証してから一括で差し替える。途中で壊れていたら何も変えず false
    private fun applyJson(text: String): Boolean {
        return try {
            val root = JSONObject(text)
            val version = root.optInt("version", -1)
            if (version < 1) return false
            val iw = parseIconPairs(root.getJSONArray("iconWords")) ?: return false
            val iwe = parseIconPairs(root.getJSONArray("iconWordsEn")) ?: return false
            val cw = parseColors(root.getJSONArray("colorWords")) ?: return false
            val aw = parseColors(root.getJSONArray("assocColorWords")) ?: return false
            val exclObj = root.getJSONObject("iconExclusions")
            val excl = mutableMapOf<String, List<String>>()
            val keys = exclObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val a = exclObj.getJSONArray(k)
                excl[k] = List(a.length()) { a.getString(it) }
            }
            val kataObj = root.getJSONObject("katakanaEn")
            val kata = mutableMapOf<String, String>()
            val kkeys = kataObj.keys()
            while (kkeys.hasNext()) {
                val k = kkeys.next()
                kata[k] = kataObj.getString(k)
            }
            if (iw.isEmpty() || cw.isEmpty() || kata.isEmpty()) return false
            iconWords = iw
            iconWordsEn = iwe
            iconExclusions = excl
            colorWords = cw
            assocColorWords = aw
            katakanaEn = kata
            appliedVersion = version
            appliedRaw = text
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun init(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val f = File(context.filesDir, CACHE_FILE)
                if (f.exists()) applyJson(f.readText())
            } catch (e: Exception) {
                // キャッシュが壊れていたら内蔵デフォルトのまま
            }
        }
        refresh(context)
    }

    // 戻り値: 1=更新した / 0=最新だった / -1=失敗
    suspend fun refresh(context: Context): Int {
        val text = withContext(Dispatchers.IO) {
            try {
                val conn = URL(DATA_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "NissinGeppoMusic")
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                null
            }
        } ?: return -1
        if (text == appliedRaw) return 0
        if (!applyJson(text)) return -1
        withContext(Dispatchers.IO) {
            try {
                File(context.filesDir, CACHE_FILE).writeText(text)
            } catch (e: Exception) {
                // 保存に失敗しても、今回の適用は有効
            }
        }
        return 1
    }

}
