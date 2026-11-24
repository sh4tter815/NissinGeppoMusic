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
    "橙" to Color(0xFFFF9A3D),
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
    "血" to Color(0xFF7A0C0C),
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
    "炎" to Color(0xFFFF6A00),
    "熱" to Color(0xFFFF9A3D),
    "火" to Color(0xFFFF6A00),
    "砂" to Color(0xFFFFC94D),
    "汚" to Color(0xFFA9744F),
    "希望" to Color(0xFFFFE14D),
    "殺" to Color(0xFF7A0C0C),
    "死" to Color(0xFF7A0C0C),
    "地獄" to Color(0xFF7A0C0C),
    "行方不明" to Color(0xFFFF4545),
    "不明" to Color(0xFFFF4545),
    "不安" to Color(0xFF7A0C0C),
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
    "fire" to Color(0xFFFF9A3D),
    "毒" to Color(0xFF9B30FF)
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

    // エフェクト系の単語リスト（v7で配信対象に追加。アプリ更新なしで対象語を増やせるようにするため）
    private val DEFAULT_DRIP_WORDS: List<Pair<String, Color>> = listOf(
        "血液" to Color(0xFFE53935),
        "蜂蜜" to Color(0xFFFFB300),
        "はちみつ" to Color(0xFFFFB300),
        "ハチミツ" to Color(0xFFFFB300),
        "蜜" to Color(0xFFFFB300),
        "どろどろ" to Color(0xFF8D6E63),
        "ドロドロ" to Color(0xFF8D6E63),
        "涎" to Color(0xFF6EC6FF),
        "よだれ" to Color(0xFF6EC6FF),
        "ヨダレ" to Color(0xFF6EC6FF),
        "液体" to Color(0xFF6EC6FF),
        "液" to Color(0xFF6EC6FF)
    )
    private val DEFAULT_DRIP_EXCLUSIONS: Map<String, List<String>> = mapOf(
        "蜜" to listOf("秘密")
    )
    private val DEFAULT_DREAM_WORDS: List<String> = listOf("夢", "思い出")
    private val DEFAULT_WAVE_WORDS: List<String> = listOf("流", "波", "海")
    private val DEFAULT_SAND_WORDS: List<String> = listOf("砂", "消", "壊", "砕")
    private val DEFAULT_WIND_WORDS: List<String> = listOf("風")
    private val DEFAULT_FALL_WORDS: List<String> = listOf("落")
    private val DEFAULT_RIPPLE_WORDS: List<String> = listOf("響")
    private val DEFAULT_MOSAIC_WORDS: List<String> = listOf("隠", "モザイク")
    private val DEFAULT_PILE_WORDS: List<String> = listOf("積み重な", "降り積も", "積も", "積み", "重な", "重ね", "堆積", "累積")
    private val DEFAULT_PARTS_WORDS: List<String> = listOf("バラバラ", "ばらばら", "壊れ", "割れ", "砕け", "崩れ", "破", "割", "壊", "砕")
    private val DEFAULT_BOUNCE_WORDS: List<String> = listOf("跳ねる", "跳ね", "弾む", "弾み", "踊る", "踊り", "舞う", "舞い", "跳躍", "跳ぶ", "跳", "弾", "踊", "舞", "バウンス", "ジャンプ")
    private val DEFAULT_QUAKE_WORDS: List<String> = listOf("身震い", "戦慄", "震える", "震え", "怯える", "怯え", "怖い", "恐れる", "恐れ", "震", "怖", "怯", "慄", "恐")
    private val DEFAULT_NEG_WORDS: List<String> = listOf("なかった", "じゃない", "なくて", "なくな", "無い", "ない", "間違い", "誤り")
    private val DEFAULT_GHOST_WORDS: List<String> = listOf("透明")
    private val DEFAULT_SPARKLE_WORDS: List<String> = listOf("光", "輝")
    private val DEFAULT_DARK_WORDS: List<String> = listOf("暗", "闇")
    private val DEFAULT_STRETCH_VERTICAL_WORDS: List<String> = listOf("深")
    private val DEFAULT_STRETCH_HORIZONTAL_WORDS: List<String> = listOf("長")
    private val DEFAULT_AMBIENT_WORDS: List<Pair<String, Int>> = listOf("桜" to 2, "雨" to 0, "雪" to 1)
    // 歌詞データ系（v10〜）: 漢字変換表・クレジット判定・ダミー歌詞マーカー・コーラスの音節
    private val DEFAULT_KANJI_MAP: Map<Char, Char> = mapOf(
        '编' to '編', '词' to '詞', '爱' to '愛', '乐' to '楽', '声' to '声', '风' to '風',
        '云' to '雲', '龙' to '竜', '马' to '馬', '车' to '車', '门' to '門', '开' to '開',
        '关' to '関', '时' to '時', '间' to '間', '线' to '線', '红' to '紅', '绿' to '緑',
        '蓝' to '藍', '银' to '銀', '铁' to '鉄', '飞' to '飛', '鸟' to '鳥', '鱼' to '魚',
        '长' to '長', '发' to '発', '后' to '後', '过' to '過', '还' to '還', '这' to '這',
        '个' to '個', '们' to '們', '为' to '為', '对' to '対', '说' to '説', '读' to '読',
        '听' to '聴', '买' to '買', '卖' to '売', '现' to '現', '见' to '見', '觉' to '覚',
        '远' to '遠', '进' to '進', '运' to '運', '动' to '動', '气' to '気', '压' to '圧',
        '电' to '電', '视' to '視', '圆' to '円', '团' to '団', '园' to '園', '图' to '図',
        '书' to '書', '贝' to '貝', '页' to '頁', '阳' to '陽', '阴' to '陰', '梦' to '夢',
        '伤' to '傷', '泪' to '涙', '让' to '譲', '给' to '給', '话' to '話', '语' to '語',
        '调' to '調', '谁' to '誰', '岁' to '歳', '尘' to '塵', '净' to '浄', '飘' to '飄',
        '摇' to '揺', '摆' to '擺', '脸' to '顔', '湿' to '湿', '干' to '乾', '挂' to '掛',
        '牵' to '牽', '怀' to '懷', '转' to '転', '轮' to '輪', '响' to '響', '应' to '応',
        '传' to '伝', '统' to '統', '续' to '続', '烧' to '焼', '毁' to '毀', '灭' to '滅',
        '烟' to '煙', '雾' to '霧', '凉' to '涼', '热' to '熱', '闷' to '悶', '恼' to '悩',
        '烦' to '煩', '忧' to '憂', '欢' to '歓', '惊' to '驚', '吓' to '嚇', '怜' to '憐',
        '义' to '義', '节' to '節', '钟' to '鐘', '针' to '針', '钱' to '銭', '铜' to '銅',
        '铅' to '鉛', '镜' to '鏡', '锁' to '鎖', '窗' to '窓', '户' to '戸', '桥' to '橋',
        '墙' to '壁', '弹' to '弾', '诗' to '詩', '谱' to '譜', '韵' to '韻', '愿' to '願',
        '够' to '夠', '麼' to '麼', '來' to '来', '說' to '説', '話' to '話', '語' to '語',
        '樂' to '楽', '聲' to '声', '聽' to '聴', '寫' to '写', '讀' to '読', '覺' to '覚',
        '氣' to '気', '壓' to '圧', '畫' to '画', '圓' to '円', '團' to '団', '圖' to '図',
        '淚' to '涙', '讓' to '譲', '鐵' to '鉄', '髮' to '髪', '臉' to '顔', '綠' to '緑',
        '關' to '関', '時' to '時', '紅' to '紅', '愛' to '愛', '夢' to '夢', '傷' to '傷',
        '還' to '還', '過' to '過', '個' to '個', '為' to '為', '這' to '這', '們' to '們',
        '达' to '達', '诉' to '訴', '边' to '辺', '渐' to '漸', '满' to '満', '游' to '遊',
        '灿' to '燦', '烂' to '爛', '畅' to '暢', '犹' to '猶', '狮' to '獅', '狱' to '獄',
        '贵' to '貴', '质' to '質', '败' to '敗', '购' to '購', '货' to '貨', '贫' to '貧',
        '贴' to '貼', '迟' to '遅', '迹' to '跡', '乡' to '郷', '丰' to '豊', '临' to '臨',
        '丽' to '麗', '乌' to '烏', '乔' to '喬', '习' to '習', '亚' to '亜', '产' to '産',
        '亲' to '親', '亿' to '億', '仅' to '僅', '从' to '従', '仓' to '倉', '仪' to '儀',
        '优' to '優', '侣' to '侶', '侦' to '偵', '侧' to '側', '侨' to '僑', '俭' to '倹',
        '债' to '債', '倾' to '傾', '假' to '仮', '偿' to '償', '储' to '儲', '儿' to '児',
        '兰' to '蘭', '兴' to '興', '养' to '養', '兽' to '獣', '冈' to '岡', '册' to '冊',
        '军' to '軍', '农' to '農', '冲' to '沖', '决' to '決', '况' to '況', '冻' to '凍',
        '减' to '減', '几' to '幾', '凤' to '鳳', '划' to '劃', '刘' to '劉', '则' to '則',
        '刚' to '剛', '创' to '創', '删' to '刪', '别' to '別', '剂' to '剤', '剑' to '剣',
        '剧' to '劇', '劝' to '勧', '务' to '務', '劲' to '勁', '劳' to '労', '势' to '勢',
        '勋' to '勲', '华' to '華', '协' to '協', '卫' to '衛', '厅' to '庁', '历' to '歴',
        '厌' to '厭', '县' to '県', '变' to '変', '叠' to '畳', '叹' to '嘆', '吴' to '呉',
        '咏' to '詠', '國' to '国', '學' to '学', '會' to '会', '實' to '実', '點' to '点',
        '產' to '産', '樣' to '様', '處' to '処', '價' to '価', '妝' to '粧', '寶' to '宝',
        '寬' to '寛', '屬' to '属', '歲' to '歳', '帶' to '帯', '廣' to '広', '廢' to '廃',
        '閒' to '閑', '隨' to '随', '險' to '険', '隱' to '隠', '雙' to '双', '雜' to '雑',
        '雞' to '鶏', '靜' to '静', '顏' to '顔', '體' to '体', '鬥' to '闘', '鹽' to '塩',
        '麥' to '麦', '黃' to '黄', '黑' to '黒'
    )
    private val DEFAULT_CREDIT_KEYWORDS: List<String> = listOf("作詞", "作曲", "編曲", "作词", "编曲", "歌唱", "演唱", "ボーカル", "Vocal", "Lyrics", "Composer", "Arranger", "詞：", "曲：")
    private val DEFAULT_PLACEHOLDER_MARKERS: List<String> = listOf("纯音乐", "純音樂", "纯音樂", "請欣賞", "请欣赏", "暫無歌詞", "暂无歌词")
    private val DEFAULT_CHORUS_SYLLABLES: List<String> = listOf("yeah", "yea", "ah", "oh", "ooh", "wow", "la", "na", "hey", "uh", "yay", "hoo", "ha", "mm", "hmm")

    @Volatile var iconWords: List<Pair<String, Int>> = DEFAULT_ICON_WORDS; private set
    @Volatile var iconWordsEn: List<Pair<String, Int>> = DEFAULT_ICON_WORDS_EN; private set
    @Volatile var iconExclusions: Map<String, List<String>> = DEFAULT_ICON_EXCLUSIONS; private set
    @Volatile var colorWords: List<Pair<String, Color>> = DEFAULT_COLOR_WORDS; private set
    @Volatile var assocColorWords: List<Pair<String, Color>> = DEFAULT_ASSOC_COLOR_WORDS; private set
    @Volatile var katakanaEn: Map<String, String> = DEFAULT_KATAKANA_EN; private set
    @Volatile var dripWords: List<Pair<String, Color>> = DEFAULT_DRIP_WORDS; private set
    @Volatile var dripExclusions: Map<String, List<String>> = DEFAULT_DRIP_EXCLUSIONS; private set
    @Volatile var dreamWords: List<String> = DEFAULT_DREAM_WORDS; private set
    @Volatile var waveWords: List<String> = DEFAULT_WAVE_WORDS; private set
    @Volatile var sandWords: List<String> = DEFAULT_SAND_WORDS; private set
    @Volatile var windWords: List<String> = DEFAULT_WIND_WORDS; private set
    @Volatile var fallWords: List<String> = DEFAULT_FALL_WORDS; private set
    @Volatile var rippleWords: List<String> = DEFAULT_RIPPLE_WORDS; private set
    @Volatile var mosaicWords: List<String> = DEFAULT_MOSAIC_WORDS; private set
    @Volatile var pileWords: List<String> = DEFAULT_PILE_WORDS; private set
    @Volatile var partsWords: List<String> = DEFAULT_PARTS_WORDS; private set
    @Volatile var bounceWords: List<String> = DEFAULT_BOUNCE_WORDS; private set
    @Volatile var quakeWords: List<String> = DEFAULT_QUAKE_WORDS; private set
    // 数値チューニング（配信JSONの tuning セクション。範囲外の値はクランプされ、失敗時は現行値を維持する）
    @Volatile var tuningSwayAmp: Float = 1f; private set
    @Volatile var tuningCamSlide: Float = 1f; private set
    @Volatile var tuningDriftAmp: Float = 1f; private set
    @Volatile var negWords: List<String> = DEFAULT_NEG_WORDS; private set
    @Volatile var ghostWords: List<String> = DEFAULT_GHOST_WORDS; private set
    @Volatile var sparkleWords: List<String> = DEFAULT_SPARKLE_WORDS; private set
    @Volatile var darkWords: List<String> = DEFAULT_DARK_WORDS; private set
    @Volatile var stretchVerticalWords: List<String> = DEFAULT_STRETCH_VERTICAL_WORDS; private set
    @Volatile var stretchHorizontalWords: List<String> = DEFAULT_STRETCH_HORIZONTAL_WORDS; private set
    @Volatile var ambientWords: List<Pair<String, Int>> = DEFAULT_AMBIENT_WORDS; private set
    @Volatile var kanjiMap: Map<Char, Char> = DEFAULT_KANJI_MAP; private set
    @Volatile var creditKeywords: List<String> = DEFAULT_CREDIT_KEYWORDS; private set
    @Volatile var placeholderMarkers: List<String> = DEFAULT_PLACEHOLDER_MARKERS; private set
    @Volatile var chorusSyllables: List<String> = DEFAULT_CHORUS_SYLLABLES; private set
    @Volatile var appliedVersion: Int = 4; private set
    // 直近の更新失敗の理由（設定画面で表示する。成功時は null）
    @Volatile var lastRefreshError: String? = null; private set
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

    private fun parseWordList(arr: JSONArray): List<String>? {
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val w = arr.optString(i)
            if (w.isEmpty()) return null
            out.add(w)
        }
        return out
    }

    private fun parseWordInts(arr: JSONArray): List<Pair<String, Int>>? {
        val out = mutableListOf<Pair<String, Int>>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONArray(i) ?: return null
            val word = p.optString(0)
            if (word.isEmpty() || p.length() < 2) return null
            out.add(word to p.optInt(1))
        }
        return out
    }

    // 漢字変換表: キーも値も1文字のものだけ取り込む。1件も取れなければ不正として null
    private fun parseKanjiMap(obj: JSONObject): Map<Char, Char>? {
        val out = mutableMapOf<Char, Char>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = try { obj.getString(k) } catch (e: Exception) { continue }
            if (k.length == 1 && v.length == 1) out[k[0]] = v[0]
        }
        return if (out.isEmpty()) null else out
    }

    private fun parseExclusions(obj: JSONObject): Map<String, List<String>> {
        val out = mutableMapOf<String, List<String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val a = obj.getJSONArray(k)
            out[k] = List(a.length()) { a.getString(it) }
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
            // v7以降のエフェクト系セクションは任意。ある場合は検証して取り込み、無い場合は現行値（内蔵デフォルト等）を維持する
            val newDrip = root.optJSONArray("dripWords")?.let { parseColors(it) ?: return false }
            val newDripExcl = root.optJSONObject("dripExclusions")?.let { parseExclusions(it) }
            val newDream = root.optJSONArray("dreamWords")?.let { parseWordList(it) ?: return false }
            val newWave = root.optJSONArray("waveWords")?.let { parseWordList(it) ?: return false }
            val newSand = root.optJSONArray("sandWords")?.let { parseWordList(it) ?: return false }
            val newWind = root.optJSONArray("windWords")?.let { parseWordList(it) ?: return false }
            val newFall = root.optJSONArray("fallWords")?.let { parseWordList(it) ?: return false }
            val newRipple = root.optJSONArray("rippleWords")?.let { parseWordList(it) ?: return false }
            val newMosaic = root.optJSONArray("mosaicWords")?.let { parseWordList(it) ?: return false }
            val newPile = root.optJSONArray("pileWords")?.let { parseWordList(it) ?: return false }
            val newParts = root.optJSONArray("partsWords")?.let { parseWordList(it) ?: return false }
            val newBounce = root.optJSONArray("bounceWords")?.let { parseWordList(it) ?: return false }
            val newQuake = root.optJSONArray("quakeWords")?.let { parseWordList(it) ?: return false }
            val tuneObj = root.optJSONObject("tuning")
            val newSwayAmp = tuneObj?.optDouble("swayAmp", 1.0)?.toFloat()?.coerceIn(0f, 2f)
            val newCamSlide = tuneObj?.optDouble("camSlide", 1.0)?.toFloat()?.coerceIn(0f, 2f)
            val newDriftAmp = tuneObj?.optDouble("driftAmp", 1.0)?.toFloat()?.coerceIn(0f, 2.5f)
            val newNeg = root.optJSONArray("negWords")?.let { parseWordList(it) ?: return false }
            val newGhost = root.optJSONArray("ghostWords")?.let { parseWordList(it) ?: return false }
            val newSparkle = root.optJSONArray("sparkleWords")?.let { parseWordList(it) ?: return false }
            val newDark = root.optJSONArray("darkWords")?.let { parseWordList(it) ?: return false }
            val newStretchV = root.optJSONArray("stretchVerticalWords")?.let { parseWordList(it) ?: return false }
            val newStretchH = root.optJSONArray("stretchHorizontalWords")?.let { parseWordList(it) ?: return false }
            val newAmbient = root.optJSONArray("ambientWords")?.let { parseWordInts(it) ?: return false }
            // v10以降の歌詞データ系セクションは任意。ある場合は検証して取り込む
            val newKanji = root.optJSONObject("kanjiMap")?.let { parseKanjiMap(it) ?: return false }
            val newCredit = root.optJSONArray("creditKeywords")?.let { parseWordList(it) ?: return false }
            val newPlaceholder = root.optJSONArray("placeholderMarkers")?.let { parseWordList(it) ?: return false }
            val newChorus = root.optJSONArray("chorusSyllables")?.let { parseWordList(it) ?: return false }
            if (newCredit != null && newCredit.isEmpty()) return false
            if (newPlaceholder != null && newPlaceholder.isEmpty()) return false
            if (newChorus != null && newChorus.isEmpty()) return false
            iconWords = iw
            iconWordsEn = iwe
            iconExclusions = excl
            colorWords = cw
            assocColorWords = aw
            katakanaEn = kata
            if (newDrip != null) dripWords = newDrip
            if (newDripExcl != null) dripExclusions = newDripExcl
            if (newDream != null) dreamWords = newDream
            if (newWave != null) waveWords = newWave
            if (newSand != null) sandWords = newSand
            if (newWind != null) windWords = newWind
            if (newFall != null) fallWords = newFall
            if (newRipple != null) rippleWords = newRipple
            if (newMosaic != null) mosaicWords = newMosaic
            if (newPile != null) pileWords = newPile
            if (newParts != null) partsWords = newParts
            if (newBounce != null) bounceWords = newBounce
            if (newQuake != null) quakeWords = newQuake
            if (newSwayAmp != null) tuningSwayAmp = newSwayAmp
            if (newCamSlide != null) tuningCamSlide = newCamSlide
            if (newDriftAmp != null) tuningDriftAmp = newDriftAmp
            if (newNeg != null) negWords = newNeg
            if (newGhost != null) ghostWords = newGhost
            if (newSparkle != null) sparkleWords = newSparkle
            if (newDark != null) darkWords = newDark
            if (newStretchV != null) stretchVerticalWords = newStretchV
            if (newStretchH != null) stretchHorizontalWords = newStretchH
            if (newAmbient != null) ambientWords = newAmbient
            if (newKanji != null) kanjiMap = newKanji
            if (newCredit != null) creditKeywords = newCredit
            if (newPlaceholder != null) placeholderMarkers = newPlaceholder
            if (newChorus != null) chorusSyllables = newChorus
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
        var text: String? = null
        var lastError: Exception? = null
        // 不安定な回線でも拾えるよう、長めのタイムアウトで最大2回試す
        for (attempt in 0..1) {
            text = withContext(Dispatchers.IO) {
                try {
                    val conn = URL(DATA_URL).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("User-Agent", "NissinGeppoLauncher")
                    conn.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    lastError = e
                    null
                }
            }
            if (text != null) break
        }
        if (text == null) {
            lastRefreshError = "通信エラー: ${lastError?.javaClass?.simpleName ?: "不明"} ${lastError?.message ?: ""}".trim()
            return -1
        }
        if (text == appliedRaw) {
            lastRefreshError = null
            return 0
        }
        if (!applyJson(text)) {
            lastRefreshError = "データ検証エラー"
            return -1
        }
        lastRefreshError = null
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
