package com.example.nissingeppomusic.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// 設定ツリー定義（settings_tree.json）のデータモデル。
// 構造・項目定義はJSONが持ち、動作（バインド/アクション）は SettingsScreen 側の対応表が持つ。
data class SettingChoiceDef(
    val v: String,
    val label: String,
    val short: String? = null,
    val illo: String? = null,
    val colorHex: String? = null,
    val preview: String? = null,
    val effect: String? = null
)

data class SettingItemDef(
    val id: String,
    val label: String,
    val desc: String,
    val glyph: String,
    val type: String,
    val bind: String? = null,
    val choices: List<SettingChoiceDef> = emptyList(),
    val min: Float = 0f,
    val max: Float = 0f,
    val steps: Int = 0,
    val format: String? = null,
    val action: String? = null,
    val visibleIf: String? = null
)

data class SettingNodeDef(
    val id: String,
    val label: String,
    val glyph: String? = null,
    val cat: Boolean = false,
    val itemId: String? = null,
    val children: List<SettingNodeDef> = emptyList()
)

data class SettingsTreeDef(
    val version: Int,
    val items: Map<String, SettingItemDef>,
    val tree: List<SettingNodeDef>
)

object SettingsTreeData {
    private const val DATA_URL =
        "https://raw.githubusercontent.com/sh4tter815/NissinGeppoMusic/master/settings_tree.json"
    private const val CACHE_FILE = "settings_tree_cache.json"
    private const val ASSET_FILE = "settings_tree.json"

    @Volatile
    var appliedVersion: Int = 0
        private set

    private val VALID_TYPES = setOf("toggle", "choices", "slider", "action", "special")

    // キャッシュ→同梱アセットの順に読み、検証を通った定義を返す。最後の砦は空定義（画面は空になるが落とさない）
    fun load(context: Context): SettingsTreeDef {
        val cacheFile = File(context.filesDir, CACHE_FILE)
        if (cacheFile.exists()) {
            try {
                parse(cacheFile.readText())?.let {
                    appliedVersion = it.version
                    return it
                }
            } catch (_: Exception) {
            }
        }
        try {
            val text = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            parse(text)?.let {
                appliedVersion = it.version
                return it
            }
        } catch (_: Exception) {
        }
        return SettingsTreeDef(0, emptyMap(), emptyList())
    }

    // リモートの最新を取りに行き、今より新しい正当な定義ならキャッシュして返す
    suspend fun refresh(context: Context): SettingsTreeDef? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(DATA_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "NissinGeppoMusic")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val def = parse(text) ?: return@withContext null
            if (def.version > appliedVersion) {
                File(context.filesDir, CACHE_FILE).writeText(text)
                appliedVersion = def.version
                def
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // 検証込みパース。1つでも不正があれば null（呼び出し側はフォールバックする）
    fun parse(text: String): SettingsTreeDef? {
        return try {
            val root = JSONObject(text)
            val version = root.optInt("version", 0)
            if (version < 1) return null
            val itemsObj = root.getJSONObject("items")
            val items = mutableMapOf<String, SettingItemDef>()
            val keys = itemsObj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val o = itemsObj.getJSONObject(id)
                val type = o.getString("type")
                if (type !in VALID_TYPES) return null
                val choices = mutableListOf<SettingChoiceDef>()
                val chArr = o.optJSONArray("choices")
                if (chArr != null) {
                    for (i in 0 until chArr.length()) {
                        val c = chArr.getJSONObject(i)
                        choices += SettingChoiceDef(
                            v = c.getString("v"),
                            label = c.getString("label"),
                            short = if (c.has("short")) c.getString("short") else null,
                            illo = if (c.has("illo")) c.getString("illo") else null,
                            colorHex = if (c.has("colorHex")) c.getString("colorHex") else null,
                            preview = if (c.has("preview")) c.getString("preview") else null,
                            effect = if (c.has("effect")) c.getString("effect") else null
                        )
                    }
                }
                if (type == "choices" && choices.isEmpty()) return null
                val min = if (o.has("min")) o.getDouble("min").toFloat() else 0f
                val max = if (o.has("max")) o.getDouble("max").toFloat() else 0f
                if (type == "slider" && max <= min) return null
                items[id] = SettingItemDef(
                    id = id,
                    label = o.getString("label"),
                    desc = o.optString("desc", ""),
                    glyph = o.optString("glyph", "toggle"),
                    type = type,
                    bind = if (o.has("bind")) o.getString("bind") else null,
                    choices = choices,
                    min = min,
                    max = max,
                    steps = o.optInt("steps", 0),
                    format = if (o.has("format")) o.getString("format") else null,
                    action = if (o.has("action")) o.getString("action") else null,
                    visibleIf = if (o.has("visibleIf")) o.getString("visibleIf") else null
                )
            }
            fun parseNode(o: JSONObject): SettingNodeDef? {
                val itemId = if (o.has("item")) o.getString("item") else null
                if (itemId != null) {
                    if (itemId !in items) return null
                    return SettingNodeDef(id = itemId, label = "", itemId = itemId)
                }
                val children = mutableListOf<SettingNodeDef>()
                val chArr = o.optJSONArray("children")
                if (chArr != null) {
                    for (i in 0 until chArr.length()) {
                        children += parseNode(chArr.getJSONObject(i)) ?: return null
                    }
                }
                return SettingNodeDef(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    glyph = if (o.has("glyph")) o.getString("glyph") else null,
                    cat = o.optBoolean("cat", false),
                    children = children
                )
            }
            val treeArr = root.getJSONArray("tree")
            val tree = mutableListOf<SettingNodeDef>()
            for (i in 0 until treeArr.length()) {
                tree += parseNode(treeArr.getJSONObject(i)) ?: return null
            }
            if (tree.isEmpty()) return null
            SettingsTreeDef(version, items, tree)
        } catch (_: Exception) {
            null
        }
    }
}
