package com.example.nissingeppomusic.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class AppPreferenceManager(private val context: Context) {

    /** 曲ごとの歌詞タイミング補正（ミリ秒）。キーはタイトル|アーティストのMD5 */
    fun getLyricOffsetMs(title: String, artist: String): Long {
        return prefs.getLong("lyric_offset_" + lyricOffsetKey(title, artist), 0L)
    }

    fun setLyricOffsetMs(title: String, artist: String, offsetMs: Long) {
        prefs.edit().putLong("lyric_offset_" + lyricOffsetKey(title, artist), offsetMs).apply()
    }

    private fun lyricOffsetKey(title: String, artist: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest("$title|$artist".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

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

    // 再生のリピート（0=オフ, 1=全曲, 2=1曲）とシャッフル
    fun getRepeatMode(): Int = prefs.getInt("repeat_mode", 0)
    fun setRepeatMode(mode: Int) = prefs.edit().putInt("repeat_mode", mode).apply()
    fun isShuffleEnabled(): Boolean = prefs.getBoolean("shuffle_enabled", false)
    fun setShuffleEnabled(enabled: Boolean) = prefs.edit().putBoolean("shuffle_enabled", enabled).apply()

    // ホーム画面背景の不透明度 (暗さ)
    fun isLiquidBackgroundEnabled(): Boolean = prefs.getBoolean("liquid_background", true)
    fun setLiquidBackgroundEnabled(enabled: Boolean) { prefs.edit().putBoolean("liquid_background", enabled).apply() }
    fun isLyricsPvMode(): Boolean = prefs.getBoolean("lyrics_pv_mode", false)
    fun setLyricsPvMode(enabled: Boolean) { prefs.edit().putBoolean("lyrics_pv_mode", enabled).apply() }
    fun getLyricsPvFont(): String = prefs.getString("lyrics_pv_font", "default") ?: "default"
    fun setLyricsPvFont(value: String) { prefs.edit().putString("lyrics_pv_font", value).apply() }
    fun getLyricsEnFont(): String = prefs.getString("lyrics_pv_font_en", "same") ?: "same"
    fun setLyricsEnFont(value: String) { prefs.edit().putString("lyrics_pv_font_en", value).apply() }
    fun getLyricsEnCustomFontPath(): String? = prefs.getString("lyrics_en_custom_font_path", null)
    fun setLyricsEnCustomFontPath(value: String?) { prefs.edit().putString("lyrics_en_custom_font_path", value).apply() }
    fun isPvPartsEntryEnabled(): Boolean = prefs.getBoolean("pv_parts_entry", false)
    fun setPvPartsEntryEnabled(value: Boolean) { prefs.edit().putBoolean("pv_parts_entry", value).apply() }
    fun isDisc3dEnabled(): Boolean = prefs.getBoolean("disc_3d", false)
    fun setDisc3dEnabled(enabled: Boolean) { prefs.edit().putBoolean("disc_3d", enabled).apply() }
    fun isDisc3dTiltEnabled(): Boolean = prefs.getBoolean("disc_3d_tilt", false)
    fun setDisc3dTiltEnabled(enabled: Boolean) { prefs.edit().putBoolean("disc_3d_tilt", enabled).apply() }
    fun isLyricsBold(): Boolean = prefs.getBoolean("lyrics_bold", false)
    fun setLyricsBold(value: Boolean) { prefs.edit().putBoolean("lyrics_bold", value).apply() }

    fun isLyricTranslationEnabled(): Boolean = prefs.getBoolean("lyric_translation", false)
    fun setLyricTranslationEnabled(value: Boolean) { prefs.edit().putBoolean("lyric_translation", value).apply() }
    /** 歌詞の表示モード: "line"=現在の行のみ / "video"=リリックビデオ（文字PV） / "multi"=複数行 */
    fun getLyricsDisplayMode(): String {
        val saved = prefs.getString("lyrics_display_mode", null)
        if (saved != null) return saved
        // 旧設定（文字PVスイッチ）からの引き継ぎ
        return if (isLyricsPvMode()) "video" else "line"
    }
    fun setLyricsDisplayMode(value: String) { prefs.edit().putString("lyrics_display_mode", value).apply() }
    fun getHomeDiscLayout(): String = prefs.getString("home_disc_layout", "artwork") ?: "artwork"
    fun setHomeDiscLayout(layout: String) { prefs.edit().putString("home_disc_layout", layout).apply() }
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
    fun isDiscOutlineEnabled(): Boolean = prefs.getBoolean("disc_outline", true)
    fun setDiscOutlineEnabled(v: Boolean) = prefs.edit().putBoolean("disc_outline", v).apply()
    fun isAppIconHidden(): Boolean = prefs.getBoolean("app_icon_hidden", false)
    fun setAppIconHidden(v: Boolean) = prefs.edit().putBoolean("app_icon_hidden", v).apply()
    fun getSettingsNavPath(): List<String> = prefs.getString("settings_nav_path", null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    fun saveSettingsNavPath(path: List<String>) = prefs.edit().putString("settings_nav_path", path.joinToString(",")).apply()

    // 再生履歴（再生数と最終再生日時を独自に記録する）
    fun recordPlay(packageName: String) {
        try {
            val jsonStr = prefs.getString("play_history_json", null)
            val obj = if (jsonStr != null) JSONObject(jsonStr) else JSONObject()
            val entry = obj.optJSONObject(packageName) ?: JSONObject()
            entry.put("c", entry.optInt("c", 0) + 1)
            entry.put("t", System.currentTimeMillis())
            obj.put(packageName, entry)
            prefs.edit().putString("play_history_json", obj.toString()).apply()
        } catch (e: Exception) {
        }
    }
    fun getPlayCounts(): Map<String, Int> {
        val map = HashMap<String, Int>()
        try {
            val jsonStr = prefs.getString("play_history_json", null) ?: return map
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optJSONObject(k)?.optInt("c", 0) ?: 0
            }
        } catch (e: Exception) {
        }
        return map
    }
    fun getLastPlayedMap(): Map<String, Long> {
        val map = HashMap<String, Long>()
        try {
            val jsonStr = prefs.getString("play_history_json", null) ?: return map
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optJSONObject(k)?.optLong("t", 0L) ?: 0L
            }
        } catch (e: Exception) {
        }
        return map
    }
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
    // スリープタイマー: 終了時刻（epoch ms、0=無効）と選択中の分数
    fun getSleepTimerEndMs(): Long = prefs.getLong("sleep_timer_end_ms", 0L)
    fun setSleepTimerEndMs(value: Long) { prefs.edit().putLong("sleep_timer_end_ms", value).apply() }
    fun getSleepTimerMinutes(): Int = prefs.getInt("sleep_timer_minutes", 0)
    fun setSleepTimerMinutes(value: Int) { prefs.edit().putInt("sleep_timer_minutes", value).apply() }
    fun setLibrarySortMode(mode: String) = prefs.edit().putString("library_sort_mode", mode).apply()


    // ===== ランチャー版から移植した追加設定 =====
    fun isDopagakiEnabled(): Boolean = prefs.getBoolean("dopagaki_mode", false)
    fun setDopagakiEnabled(value: Boolean) { prefs.edit().putBoolean("dopagaki_mode", value).apply() }
    fun isDopaBlackFontEnabled(): Boolean = prefs.getBoolean("dopagaki_black_font", true)
    fun setDopaBlackFontEnabled(value: Boolean) { prefs.edit().putBoolean("dopagaki_black_font", value).apply() }

    fun getDateDisplayMode(): String = prefs.getString("date_display_mode", "auto") ?: "auto"
    fun setDateDisplayMode(mode: String) { prefs.edit().putString("date_display_mode", mode).apply() }

    fun getDiscInertia(): Float = prefs.getFloat("disc_inertia", 1.0f)
    fun setDiscInertia(v: Float) = prefs.edit().putFloat("disc_inertia", v).apply()
    // 振動フィードバック（円盤のカチッという触覚）
    fun isHapticsEnabled(): Boolean = prefs.getBoolean("haptics_enabled", true)
    fun setHapticsEnabled(v: Boolean) = prefs.edit().putBoolean("haptics_enabled", v).apply()
    // 時計: 秒表示 / 12時間表示 / バッテリー残量 / 和暦
    fun isClockShowSeconds(): Boolean = prefs.getBoolean("clock_show_seconds", false)
    fun setClockShowSeconds(v: Boolean) = prefs.edit().putBoolean("clock_show_seconds", v).apply()
    fun isClock12h(): Boolean = prefs.getBoolean("clock_12h", false)
    fun setClock12h(v: Boolean) = prefs.edit().putBoolean("clock_12h", v).apply()
    fun isClockShowBattery(): Boolean = prefs.getBoolean("clock_show_battery", false)
    fun setClockShowBattery(v: Boolean) = prefs.edit().putBoolean("clock_show_battery", v).apply()
    fun isDateWareki(): Boolean = prefs.getBoolean("date_wareki", false)
    fun setDateWareki(v: Boolean) = prefs.edit().putBoolean("date_wareki", v).apply()
    // 歌詞マスター（オフでPV・時計下・ステータスバーの歌詞を全部消す）
    fun isLyricsEnabled(): Boolean = prefs.getBoolean("lyrics_enabled", true)
    fun setLyricsEnabled(v: Boolean) = prefs.edit().putBoolean("lyrics_enabled", v).apply()
    // PV: 文字サイズ倍率 / 縦位置オフセット(dp) / 文字色 / ルビ / 間奏アート
    fun getPvFontScale(): Float = prefs.getFloat("pv_font_scale", 1.0f)
    fun setPvFontScale(v: Float) = prefs.edit().putFloat("pv_font_scale", v).apply()
    fun getPvTopOffsetDp(): Float = prefs.getFloat("pv_top_offset", 0f)
    fun setPvTopOffsetDp(v: Float) = prefs.edit().putFloat("pv_top_offset", v).apply()
    fun getPvTextColor(): String = prefs.getString("pv_text_color", "white") ?: "white"
    fun setPvTextColor(v: String) = prefs.edit().putString("pv_text_color", v).apply()
    fun isPvRubyEnabled(): Boolean = prefs.getBoolean("pv_ruby_enabled", true)
    fun setPvRubyEnabled(v: Boolean) = prefs.edit().putBoolean("pv_ruby_enabled", v).apply()
    fun isPvInterludeArtEnabled(): Boolean = prefs.getBoolean("pv_interlude_art", true)
    fun setPvInterludeArtEnabled(v: Boolean) = prefs.edit().putBoolean("pv_interlude_art", v).apply()
    // 液体: 速さ倍率 / 濃さ倍率
    fun getLiquidSpeed(): Float = prefs.getFloat("liquid_speed", 1.0f)
    fun setLiquidSpeed(v: Float) = prefs.edit().putFloat("liquid_speed", v).apply()
    fun getLiquidAlpha(): Float = prefs.getFloat("liquid_alpha", 1.0f)
    fun setLiquidAlpha(v: Float) = prefs.edit().putFloat("liquid_alpha", v).apply()
    // 起動時に開くページ: "home" / "last"（last のときは getLastPage を使う）
    fun getStartupPage(): String = prefs.getString("startup_page", "home") ?: "home"
    fun setStartupPage(v: String) = prefs.edit().putString("startup_page", v).apply()
    fun getLastPage(): Int = prefs.getInt("last_page", 1)
    fun setLastPage(v: Int) = prefs.edit().putInt("last_page", v).apply()
    // 下ダイヤルの表示方法: "auto"（ラック中などは隠れる） / "always" / "hidden"
    fun getDialVisibility(): String = prefs.getString("dial_visibility", "auto") ?: "auto"
    fun setDialVisibility(v: String) = prefs.edit().putString("dial_visibility", v).apply()
    // ページ切り替えアニメの速さ倍率
    fun getPageTransitionSpeed(): Float = prefs.getFloat("page_transition_speed", 1.0f)
    fun setPageTransitionSpeed(v: Float) = prefs.edit().putFloat("page_transition_speed", v).apply()
    // 再生中は画面を点けたまま
    fun isKeepScreenOn(): Boolean = prefs.getBoolean("keep_screen_on", false)
    fun setKeepScreenOn(v: Boolean) = prefs.edit().putBoolean("keep_screen_on", v).apply()
    // 壁紙のぼかし (dp)
    fun getWallpaperBlurDp(): Float = prefs.getFloat("wallpaper_blur", 0f)
    fun setWallpaperBlurDp(v: Float) = prefs.edit().putFloat("wallpaper_blur", v).apply()
    // ページごとの壁紙（未設定のページは共通の壁紙にフォールバック）
    fun getPageWallpaperPath(page: Int): String? = prefs.getString("wallpaper_path_page_$page", null)
    fun setPageWallpaperPath(page: Int, path: String?) = prefs.edit().putString("wallpaper_path_page_$page", path).apply()

    // アートワーク表示（左円とラックの画像そのもの）
    fun isArtworkEnabled(): Boolean = prefs.getBoolean("artwork_enabled", true)
    fun setArtworkEnabled(v: Boolean) = prefs.edit().putBoolean("artwork_enabled", v).apply()
    // ラック専用のスクロール反転（円盤の反転とは別）
    fun isRackReverseScroll(): Boolean = prefs.getBoolean("rack_reverse", false)
    fun setRackReverseScroll(v: Boolean) = prefs.edit().putBoolean("rack_reverse", v).apply()

    fun getClockOffsetXDp(): Float = prefs.getFloat("clock_offset_x", 0f)
    fun getClockOffsetYDp(): Float = prefs.getFloat("clock_offset_y", 0f)
    fun setClockOffset(x: Float, y: Float) = prefs.edit().putFloat("clock_offset_x", x).putFloat("clock_offset_y", y).apply()
    fun getPvOffsetXDp(): Float = prefs.getFloat("pv_offset_x", 0f)
    fun setPvOffsetX(v: Float) = prefs.edit().putFloat("pv_offset_x", v).apply()
    // 設定画面からレイアウト編集モードを開くための一時フラグ
    fun requestLayoutEdit() = prefs.edit().putBoolean("layout_edit_request", true).apply()
    fun isLayoutEditRequested(): Boolean = prefs.getBoolean("layout_edit_request", false)
    fun clearLayoutEditRequest() = prefs.edit().putBoolean("layout_edit_request", false).apply()

    fun exportAllJson(): String {
        val prefsObj = org.json.JSONObject()
        for ((k, v) in prefs.all) {
            val o = org.json.JSONObject()
            when (v) {
                is Boolean -> { o.put("t", "b"); o.put("v", v) }
                is Int -> { o.put("t", "i"); o.put("v", v) }
                is Long -> { o.put("t", "l"); o.put("v", v) }
                is Float -> { o.put("t", "f"); o.put("v", v.toDouble()) }
                is String -> { o.put("t", "s"); o.put("v", v) }
                is Set<*> -> { o.put("t", "set"); o.put("v", org.json.JSONArray(v.map { it.toString() })) }
                else -> continue
            }
            prefsObj.put(k, o)
        }
        return org.json.JSONObject().apply {
            put("app", "NissinGeppoMusic")
            put("kind", "settings-backup")
            put("version", 1)
            put("prefs", prefsObj)
        }.toString()
    }

    fun importAllJson(text: String): Boolean {
        return try {
            val root = org.json.JSONObject(text)
            if (root.optString("kind") != "settings-backup") return false
            val prefsObj = root.getJSONObject("prefs")
            val e = prefs.edit()
            e.clear()
            val keys = prefsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = prefsObj.getJSONObject(k)
                when (o.getString("t")) {
                    "b" -> e.putBoolean(k, o.getBoolean("v"))
                    "i" -> e.putInt(k, o.getInt("v"))
                    "l" -> e.putLong(k, o.getLong("v"))
                    "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                    "s" -> e.putString(k, o.getString("v"))
                    "set" -> {
                        val arr = o.getJSONArray("v")
                        val s = mutableSetOf<String>()
                        for (i in 0 until arr.length()) s.add(arr.getString(i))
                        e.putStringSet(k, s)
                    }
                }
            }
            e.apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    // ===== ランチャー版 MusicVinylWidget 移植に伴う追加（prefキーはランチャーと同一） =====
    fun isArtworkOutlineEnabled(): Boolean = prefs.getBoolean("artwork_outline", true)
    fun setArtworkOutlineEnabled(v: Boolean) = prefs.edit().putBoolean("artwork_outline", v).apply()
    fun isCurvedTitleEnabled(): Boolean = prefs.getBoolean("curved_title", true)
    fun setCurvedTitleEnabled(v: Boolean) = prefs.edit().putBoolean("curved_title", v).apply()
    fun isCaseCircleClipEnabled(): Boolean = prefs.getBoolean("case_circle_clip", false)
    fun setCaseCircleClipEnabled(v: Boolean) = prefs.edit().putBoolean("case_circle_clip", v).apply()
    fun getDiscStyle(): String = prefs.getString("disc_style", "digital") ?: "digital"
    fun setDiscStyle(v: String) = prefs.edit().putString("disc_style", v).apply()
    fun getWidgetTapAction(): String = prefs.getString("widget_tap_action", "toggle") ?: "toggle"
    fun setWidgetTapAction(v: String) = prefs.edit().putString("widget_tap_action", v).apply()
    fun getWidgetDoubleTapAction(): String = prefs.getString("widget_double_tap_action", "app") ?: "app"
    fun setWidgetDoubleTapAction(v: String) = prefs.edit().putString("widget_double_tap_action", v).apply()
    fun getWidgetTripleTapAction(): String = prefs.getString("widget_triple_tap_action", "theme") ?: "theme"
    fun setWidgetTripleTapAction(v: String) = prefs.edit().putString("widget_triple_tap_action", v).apply()
    fun getWidgetLongPressAction(): String = prefs.getString("widget_long_press_action", "grab") ?: "grab"
    fun setWidgetLongPressAction(v: String) = prefs.edit().putString("widget_long_press_action", v).apply()
    fun getWidgetDoubleTapHoldAction(): String = prefs.getString("widget_double_tap_hold_action", "rack") ?: "rack"
    fun setWidgetDoubleTapHoldAction(v: String) = prefs.edit().putString("widget_double_tap_hold_action", v).apply()
    fun getTonearmScale(): Float = prefs.getFloat("tonearm_scale", 1f)
    fun setTonearmScale(v: Float) = prefs.edit().putFloat("tonearm_scale", v).apply()
    fun getTonearmStyle(): String = prefs.getString("tonearm_style", "auto") ?: "auto"
    fun setTonearmStyle(v: String) = prefs.edit().putString("tonearm_style", v).apply()
    fun getCassetteStyle(): String = prefs.getString("cassette_style", "clear") ?: "clear"
    fun setCassetteStyle(v: String) = prefs.edit().putString("cassette_style", v).apply()
    fun getPowerSaveMode(): String = prefs.getString("power_save_mode", "auto") ?: "auto"
    fun setPowerSaveMode(v: String) = prefs.edit().putString("power_save_mode", v).apply()
    fun isPowerSaveChargingExempt(): Boolean = prefs.getBoolean("power_save_charging_exempt", true)
    fun setPowerSaveChargingExempt(v: Boolean) = prefs.edit().putBoolean("power_save_charging_exempt", v).apply()
    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }
    fun getPowerSaveBatteryThreshold(): Int = (prefs.getFloat("power_save_battery_threshold", 0f) * 100).toInt()
    fun setPowerSaveBatteryThreshold(v: Float) = prefs.edit().putFloat("power_save_battery_threshold", v).apply()
    fun batteryLevel(context: Context): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return 100
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else 100
    }
    fun isPowerSaveNightEnabled(): Boolean = prefs.getBoolean("power_save_night", false)
    fun setPowerSaveNightEnabled(v: Boolean) = prefs.edit().putBoolean("power_save_night", v).apply()
    fun getPowerSaveNightStart(): Float = prefs.getFloat("power_save_night_start", 23f)
    fun setPowerSaveNightStart(v: Float) = prefs.edit().putFloat("power_save_night_start", v).apply()
    fun getPowerSaveNightEnd(): Float = prefs.getFloat("power_save_night_end", 7f)
    fun setPowerSaveNightEnd(v: Float) = prefs.edit().putFloat("power_save_night_end", v).apply()
    fun isNightTimeNow(): Boolean {
        val start = getPowerSaveNightStart().toInt()
        val end = getPowerSaveNightEnd().toInt()
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (start <= end) hour in start until end else hour >= start || hour < end
    }
    fun isPowerSaveActive(context: Context): Boolean {
        if (isPowerSaveChargingExempt() && isCharging(context)) return false
        if (isPowerSaveNightEnabled() && isNightTimeNow()) return true
        val threshold = getPowerSaveBatteryThreshold()
        if (threshold > 0 && batteryLevel(context) <= threshold) return true
        return when (getPowerSaveMode()) {
            "on" -> true
            "auto" -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                pm?.isPowerSaveMode == true
            }
            else -> false
        }
    }

}
