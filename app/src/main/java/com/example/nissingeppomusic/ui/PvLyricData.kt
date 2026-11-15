package com.example.nissingeppomusic.ui

import androidx.compose.ui.graphics.Color

// 歌詞PVのデータ定義（単語アイコン対応表・除外リスト・色名・カタカナ英訳・コーラス判定・入場方向）。
// 正本はこの音楽アプリ側。ランチャー側にも同じ内容のファイルがあるので、直したらランチャー側へもコピーすること。

object LyricIcon {
    const val UMBRELLA = 0; const val RAIN = 1; const val SNOW = 2; const val STAR = 3
    const val MOON = 4; const val SUN = 5; const val CLOUD = 6; const val FLOWER = 7
    const val WAVES = 8; const val HEART = 9; const val KEY = 10; const val NOTE = 11
    const val ENVELOPE = 12; const val CLOCK = 13; const val FLAME = 14; const val DROP = 15
    const val RAINBOW = 16; const val LIGHTNING = 17; const val SMILE = 18; const val FISH = 19
    const val MOUNTAIN = 20; const val WIND = 21
    const val LINE = 22; const val CIRCLE = 23
    const val GLOBE = 24; const val BUBBLE = 25
    const val COFFIN = 26; const val EYE = 27; const val PLUS = 28
    const val MINUS = 29; const val MAP = 30; const val BONE = 31
}

/** 歌詞に出てきたらアイコンを出す単語（前にあるものが優先） */
val LYRIC_ICON_WORDS: List<Pair<String, Int>> = listOf(
    "傘" to LyricIcon.UMBRELLA, "桜" to LyricIcon.FLOWER, "雨" to LyricIcon.RAIN,
    "雪" to LyricIcon.SNOW, "星" to LyricIcon.STAR, "月" to LyricIcon.MOON,
    "太陽" to LyricIcon.SUN, "雲" to LyricIcon.CLOUD, "花" to LyricIcon.FLOWER,
    "海" to LyricIcon.WAVES, "波" to LyricIcon.WAVES, "心" to LyricIcon.HEART,
    "ハート" to LyricIcon.HEART, "恋" to LyricIcon.HEART, "愛" to LyricIcon.HEART,
    "夢" to LyricIcon.BUBBLE, "夜" to LyricIcon.MOON, "鍵" to LyricIcon.KEY,
    "音楽" to LyricIcon.NOTE, "歌" to LyricIcon.NOTE, "手紙" to LyricIcon.ENVELOPE,
    "時計" to LyricIcon.CLOCK, "炎" to LyricIcon.FLAME, "火" to LyricIcon.FLAME,
    "涙" to LyricIcon.DROP, "雫" to LyricIcon.DROP, "虹" to LyricIcon.RAINBOW,
    "雷" to LyricIcon.LIGHTNING, "笑顔" to LyricIcon.SMILE, "魚" to LyricIcon.FISH,
    "山" to LyricIcon.MOUNTAIN, "風" to LyricIcon.WIND,
"音" to LyricIcon.NOTE, "濡" to LyricIcon.DROP,
    "時間" to LyricIcon.CLOCK, "場所" to LyricIcon.MAP, "死" to LyricIcon.COFFIN,
    "目" to LyricIcon.EYE, "水" to LyricIcon.DROP, "増" to LyricIcon.PLUS,
    "減" to LyricIcon.MINUS, "骨" to LyricIcon.BONE
)
val LYRIC_ICON_EXCLUSIONS: Map<String, List<String>> = mapOf(
    "心" to listOf("心配"),
    "山" to listOf("沢山")
)

/** 行の中からアイコン対象の単語を探す。除外語の一部として現れる箇所は数えない */
fun findIconWord(text: String): Pair<String, Int>? {
    for ((word, kind) in LYRIC_ICON_WORDS) {
        val exclusions = LYRIC_ICON_EXCLUSIONS[word] ?: emptyList()
        var from = 0
        while (true) {
            val i = text.indexOf(word, from)
            if (i < 0) break
            if (exclusions.any { text.startsWith(it, i) }) {
                from = i + 1
                continue
            }
            return word to kind
        }
    }
    return null
}
val CHORUS_REGEX = Regex("^(yeah|yea|ah|oh|ooh|wow|la|na|hey|uh|yay|hoo|ha|mm|hmm)+$")

fun isChorusWord(text: String): Boolean {
    val t = text.trim().lowercase().trim('!', '?', ',', '.', '~', '〜', '・')
    if (t.isEmpty() || !t.all { it in 'a'..'z' }) return false
    return CHORUS_REGEX.matches(t)
}

/** 色名が出てきたら、その文字をその色で塗るための対応表（長い語を先に判定） */
val COLOR_WORDS: List<Pair<String, Color>> = listOf(
    "水色" to Color(0xFF7FD4F2), "ピンク" to Color(0xFFFF8FBF), "オレンジ" to Color(0xFFFF9A3D),
    "グレー" to Color(0xFF9AA0A6),
    "青" to Color(0xFF3D7BFF), "赤" to Color(0xFFFF4545), "白" to Color(0xFFF2F3F5),
    "黒" to Color(0xFF26262C), "紫" to Color(0xFFB07FE8), "緑" to Color(0xFF4FC96B),
    "黄" to Color(0xFFFFD94D), "金" to Color(0xFFFFC94D), "銀" to Color(0xFFC9CED6),
    "茶" to Color(0xFFB07B4F), "灰" to Color(0xFF9AA0A6)
)
val KATAKANA_EN = mapOf(
    "ハート" to "HEART", "ラブ" to "LOVE", "キス" to "KISS", "ドリーム" to "DREAM",
    "ライト" to "LIGHT", "ナイト" to "NIGHT", "スター" to "STAR", "ムーン" to "MOON",
    "スカイ" to "SKY", "ミュージック" to "MUSIC", "ソング" to "SONG", "ダンス" to "DANCE",
    "ロック" to "ROCK", "ポップ" to "POP", "ビート" to "BEAT", "リズム" to "RHYTHM",
    "メロディ" to "MELODY", "ハーモニー" to "HARMONY", "ボイス" to "VOICE", "サウンド" to "SOUND",
    "ワールド" to "WORLD", "ライフ" to "LIFE", "タイム" to "TIME", "モーメント" to "MOMENT",
    "メモリー" to "MEMORY", "ストーリー" to "STORY", "フューチャー" to "FUTURE", "プロミス" to "PROMISE",
    "シークレット" to "SECRET", "ミステリー" to "MYSTERY", "マジック" to "MAGIC", "パワー" to "POWER",
    "エナジー" to "ENERGY", "ファイア" to "FIRE", "ウォーター" to "WATER", "レイン" to "RAIN",
    "スノー" to "SNOW", "フラワー" to "FLOWER", "バタフライ" to "BUTTERFLY", "エンジェル" to "ANGEL",
    "デビル" to "DEVIL", "ゴースト" to "GHOST", "シャドウ" to "SHADOW", "シャイン" to "SHINE",
    "ダーク" to "DARK", "カラー" to "COLOR", "ブルー" to "BLUE", "ピンク" to "PINK",
    "ブラック" to "BLACK", "ホワイト" to "WHITE", "ゴールド" to "GOLD", "シルバー" to "SILVER",
    "クリスタル" to "CRYSTAL", "ダイヤモンド" to "DIAMOND", "プラズマ" to "PLASMA", "ブラックアウト" to "BLACKOUT",
    "テレビ" to "TV", "ラジオ" to "RADIO", "ゲーム" to "GAME", "ヒーロー" to "HERO",
    "クイーン" to "QUEEN", "プリンセス" to "PRINCESS", "ベイビー" to "BABY", "ダーリン" to "DARLING",
    "ハニー" to "HONEY", "フレンド" to "FRIEND", "ラバー" to "LOVER", "エンド" to "END",
    "スタート" to "START", "ゴール" to "GOAL", "ドア" to "DOOR", "キー" to "KEY",
    "ロード" to "ROAD", "シティ" to "CITY", "ホーム" to "HOME", "ミラー" to "MIRROR",
    "スマイル" to "SMILE", "ソウル" to "SOUL", "マインド" to "MIND", "ステージ" to "STAGE",
    "ライブ" to "LIVE", "パーティ" to "PARTY", "カフェ" to "CAFE", "ホテル" to "HOTEL",
    "レター" to "LETTER", "カード" to "CARD", "チケット" to "TICKET", "ドレス" to "DRESS",
    "リング" to "RING", "ブック" to "BOOK", "シーズン" to "SEASON", "サマー" to "SUMMER",
    "ウィンター" to "WINTER", "モーニング" to "MORNING", "ミッドナイト" to "MIDNIGHT", "トゥモロー" to "TOMORROW",
    "イエスタデイ" to "YESTERDAY", "フォーエバー" to "FOREVER", "パラダイス" to "PARADISE", "ヘブン" to "HEAVEN",
    "アース" to "EARTH", "オーシャン" to "OCEAN", "リバー" to "RIVER", "フォレスト" to "FOREST"
)

val PV_ENTRY_DIRS = listOf(
    0f to -1f, 0.72f to -0.72f, 1f to 0f, 0.72f to 0.72f,
    0f to 1f, -0.72f to 0.72f, -1f to 0f, -0.72f to -0.72f
)
