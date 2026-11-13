package com.example.nissingeppomusic.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class AppPreferenceManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nisshin_geppo_prefs", Context.MODE_PRIVATE)

    fun getFavoritePackages(): Set<String> {
        return prefs.getStringSet("favorite_packages", emptySet()) ?: emptySet()
    }

    fun setFavoritePackage(packageName: String, isFavorite: Boolean) {
        val current = getFavoritePackages().toMutableSet()
        if (isFavorite) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet("favorite_packages", current).apply()
    }

    fun getHiddenPackages(): Set<String> {
        return prefs.getStringSet("hidden_packages", emptySet()) ?: emptySet()
    }

    fun setHiddenPackage(packageName: String, isHidden: Boolean) {
        val current = getHiddenPackages().toMutableSet()
        if (isHidden) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        prefs.edit().putStringSet("hidden_packages", current).apply()
    }

    fun getYomiMap(): Map<String, String> {
        val jsonStr = prefs.getString("yomi_map_json", "{}") ?: "{}"
        val map = mutableMapOf<String, String>()
        try {
            val jsonObj = JSONObject(jsonStr)
            jsonObj.keys().forEach { key ->
                map[key] = jsonObj.getString(key)
            }
        } catch (_: Exception) {}
        return map
    }

    fun setYomi(packageName: String, yomi: String) {
        val currentMap = getYomiMap().toMutableMap()
        if (yomi.isBlank()) {
            currentMap.remove(packageName)
        } else {
            currentMap[packageName] = yomi.trim()
        }
        val jsonObj = JSONObject(currentMap as Map<*, *>)
        prefs.edit().putString("yomi_map_json", jsonObj.toString()).apply()
    }

    // 検索可能な別名（複数登録可能）
    fun getAliasesMap(): Map<String, List<String>> {
        val jsonStr = prefs.getString("aliases_map_json", "{}") ?: "{}"
        val map = mutableMapOf<String, List<String>>()
        try {
            val jsonObj = JSONObject(jsonStr)
            jsonObj.keys().forEach { key ->
                val arr = jsonObj.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                map[key] = list
            }
        } catch (_: Exception) {}
        return map
    }

    fun addAlias(packageName: String, alias: String) {
        val cleanAlias = alias.trim()
        if (cleanAlias.isBlank()) return
        val currentMap = getAliasesMap().toMutableMap()
        val currentList = (currentMap[packageName] ?: emptyList()).toMutableList()
        if (!currentList.contains(cleanAlias)) {
            currentList.add(cleanAlias)
        }
        currentMap[packageName] = currentList
        saveAliasesMap(currentMap)
    }

    fun removeAlias(packageName: String, alias: String) {
        val currentMap = getAliasesMap().toMutableMap()
        val currentList = (currentMap[packageName] ?: emptyList()).toMutableList()
        currentList.remove(alias)
        if (currentList.isEmpty()) {
            currentMap.remove(packageName)
        } else {
            currentMap[packageName] = currentList
        }
        saveAliasesMap(currentMap)
    }

    private fun saveAliasesMap(map: Map<String, List<String>>) {
        val jsonObj = JSONObject()
        map.forEach { (pkg, list) ->
            val arr = JSONArray(list)
            jsonObj.put(pkg, arr)
        }
        prefs.edit().putString("aliases_map_json", jsonObj.toString()).apply()
    }

    // フォルダ管理 (folder_id -> Pair<folderName, List<packageName>>)
    fun getFoldersMap(): Map<String, Pair<String, List<String>>> {
        val jsonStr = prefs.getString("folders_map_json", "{}") ?: "{}"
        val map = mutableMapOf<String, Pair<String, List<String>>>()
        try {
            val jsonObj = JSONObject(jsonStr)
            jsonObj.keys().forEach { folderId ->
                val obj = jsonObj.getJSONObject(folderId)
                val name = obj.getString("name")
                val pkgArray = obj.getJSONArray("packages")
                val pkgs = mutableListOf<String>()
                for (i in 0 until pkgArray.length()) {
                    pkgs.add(pkgArray.getString(i))
                }
                map[folderId] = Pair(name, pkgs)
            }
        } catch (_: Exception) {}
        return map
    }

    fun saveFolder(folderId: String, name: String, packages: List<String>) {
        val currentMap = getFoldersMap().toMutableMap()
        currentMap[folderId] = Pair(name, packages)
        saveFoldersMap(currentMap)
    }

    fun deleteFolder(folderId: String) {
        val currentMap = getFoldersMap().toMutableMap()
        currentMap.remove(folderId)
        saveFoldersMap(currentMap)
    }

    private fun saveFoldersMap(map: Map<String, Pair<String, List<String>>>) {
        val jsonObj = JSONObject()
        map.forEach { (id, pair) ->
            val folderObj = JSONObject()
            folderObj.put("name", pair.first)
            folderObj.put("packages", JSONArray(pair.second))
            jsonObj.put(id, folderObj)
        }
        prefs.edit().putString("folders_map_json", jsonObj.toString()).apply()
    }

    // 非表示説明ダイアログを今後は表示しない設定
    fun isDontShowHideGuide(): Boolean {
        return prefs.getBoolean("dont_show_hide_guide", false)
    }

    fun setDontShowHideGuide(dontShow: Boolean) {
        prefs.edit().putBoolean("dont_show_hide_guide", dontShow).apply()
    }

    // カスタム背景画像のURI/パス
    fun getCustomBackgroundPath(): String? {
        return prefs.getString("custom_background_path", null)
    }

    fun setCustomBackgroundPath(path: String?) {
        prefs.edit().putString("custom_background_path", path).apply()
    }

    // App Disc Customization Settings
    fun getAppDiscIconSizeDp(): Float = prefs.getFloat("app_disc_icon_size", 25f)
    fun setAppDiscIconSizeDp(size: Float) = prefs.edit().putFloat("app_disc_icon_size", size).apply()

    fun getAppDiscTextSizeSp(): Float = prefs.getFloat("app_disc_text_size", 14f)
    fun setAppDiscTextSizeSp(size: Float) = prefs.edit().putFloat("app_disc_text_size", size).apply()

    fun getAppDiscLetterSpacingSp(): Float = prefs.getFloat("app_disc_letter_spacing", 2.6f)
    fun setAppDiscLetterSpacingSp(spacing: Float) = prefs.edit().putFloat("app_disc_letter_spacing", spacing).apply()

    fun getAppDiscStepAngle(): Float = prefs.getFloat("app_disc_step_angle", 18.6f)

    // ページ切り替え方法 ("buttons" / "dial")
    fun getPageSwitchStyle(): String = prefs.getString("page_switch_style", "dial") ?: "dial"
    fun setPageSwitchStyle(style: String) { prefs.edit().putString("page_switch_style", style).apply() }
    fun setAppDiscStepAngle(angle: Float) = prefs.edit().putFloat("app_disc_step_angle", angle).apply()

    // アクセントカラー (デフォルト: 赤 #E53935)
    fun getAccentColorHex(): String = prefs.getString("accent_color_hex", "#E53935") ?: "#E53935"
    fun setAccentColorHex(hex: String) = prefs.edit().putString("accent_color_hex", hex).apply()

    // アートワーク連動カラー（再生中の曲のアートワークから配色を決める）
    fun isMatchArtworkColor(): Boolean = prefs.getBoolean("match_artwork_color", false)
    fun setMatchArtworkColor(enabled: Boolean) = prefs.edit().putBoolean("match_artwork_color", enabled).apply()

    // ホーム画面背景の不透明度 (暗さ)
    fun getHomeWallpaperAlpha(): Float = prefs.getFloat("home_wallpaper_alpha", 0.35f)
    fun setHomeWallpaperAlpha(alpha: Float) = prefs.edit().putFloat("home_wallpaper_alpha", alpha).apply()

    // フォント設定
    fun getFontFamilyName(): String = prefs.getString("font_family_name", "Default") ?: "Default"
    fun setFontFamilyName(name: String) = prefs.edit().putString("font_family_name", name).apply()

    fun getFontWeightIndex(): Int = prefs.getInt("font_weight_index", 0) // 0: Light
    fun setFontWeightIndex(index: Int) = prefs.edit().putInt("font_weight_index", index).apply()

    fun getCustomFontPath(): String? = prefs.getString("custom_font_path", null)
    fun setCustomFontPath(path: String?) = prefs.edit().putString("custom_font_path", path).apply()

    fun isUseCustomFont(): Boolean = prefs.getBoolean("use_custom_font", true)
    fun setUseCustomFont(use: Boolean) = prefs.edit().putBoolean("use_custom_font", use).apply()

    // セキュリティ設定 (パスコード & 生体認証)
    fun getPasscode(): String = prefs.getString("security_passcode", "1234") ?: "1234"
    fun setPasscode(code: String) = prefs.edit().putString("security_passcode", code).apply()

    fun getUseBiometric(): Boolean = prefs.getBoolean("security_use_biometric", true)
    fun setUseBiometric(use: Boolean) = prefs.edit().putBoolean("security_use_biometric", use).apply()

    fun isUsePasscodeForHidden(): Boolean = prefs.getBoolean("security_use_passcode_for_hidden", true)
    fun setUsePasscodeForHidden(use: Boolean) = prefs.edit().putBoolean("security_use_passcode_for_hidden", use).apply()

    // チュートリアル表示フラグ
    fun isFirstWelcomeTutorialShown(): Boolean = prefs.getBoolean("tutorial_welcome_shown", false)
    fun setFirstWelcomeTutorialShown(shown: Boolean) = prefs.edit().putBoolean("tutorial_welcome_shown", shown).apply()

    fun isFirstIndexTutorialShown(): Boolean = prefs.getBoolean("tutorial_index_shown", false)
    fun setFirstIndexTutorialShown(shown: Boolean) = prefs.edit().putBoolean("tutorial_index_shown", shown).apply()

    fun isFirstHideTutorialShown(): Boolean = prefs.getBoolean("tutorial_hide_shown", false)
    fun setFirstHideTutorialShown(shown: Boolean) = prefs.edit().putBoolean("tutorial_hide_shown", shown).apply()

    // 動作設定 (スクロール方向反転)
    fun isReverseScroll(): Boolean = prefs.getBoolean("reverse_scroll", false)
    fun setReverseScroll(reverse: Boolean) = prefs.edit().putBoolean("reverse_scroll", reverse).apply()

    // フォント選択 ("default" = デフォルト, "receipt" = レシートフォント, "custom" = カスタムファイル)
    fun getFontChoice(): String = prefs.getString("font_choice", "default") ?: "default"
    fun setFontChoice(choice: String) = prefs.edit().putString("font_choice", choice).apply()

    // ライブラリの除外フォルダ（絶対パスのプレフィックス）
    fun getExcludedFolders(): Set<String> {
        return prefs.getStringSet("excluded_folders", emptySet()) ?: emptySet()
    }

    fun addExcludedFolder(path: String) {
        val current = getExcludedFolders().toMutableSet()
        current.add(path)
        prefs.edit().putStringSet("excluded_folders", current).apply()
    }

    fun removeExcludedFolder(path: String) {
        val current = getExcludedFolders().toMutableSet()
        current.remove(path)
        prefs.edit().putStringSet("excluded_folders", current).apply()
    }

    // ライブラリの並び替え基準 ("title" = 曲名, "artist" = アーティスト, "album" = アルバム)
    fun getLibrarySortMode(): String = prefs.getString("library_sort_mode", "title") ?: "title"
    fun setLibrarySortMode(mode: String) = prefs.edit().putString("library_sort_mode", mode).apply()
}
