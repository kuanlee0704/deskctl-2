package tw.deskctl

import android.content.Context
import android.content.SharedPreferences

/** 所有設定集中在這裡 (SharedPreferences "deskctl")。新增設定項: 加一對 getter/setter 即可。 */
object Prefs {
    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("deskctl", Context.MODE_PRIVATE)

    /** 已記住的控制器 MAC (找到後自動儲存, 之後直連) */
    fun mac(c: Context): String? = sp(c).getString("mac", null)
    fun setMac(c: Context, v: String?) {
        val e = sp(c).edit()
        if (v == null) e.remove("mac") else e.putString("mac", v)
        e.apply()
    }

    /** 手機開機後, 若關機前是啟動狀態, 自動啟動服務 */
    fun autostart(c: Context): Boolean = sp(c).getBoolean("autostart", true)
    fun setAutostart(c: Context, v: Boolean) { sp(c).edit().putBoolean("autostart", v).apply() }

    /** 開啟 App 時自動啟動服務 */
    fun autoOpen(c: Context): Boolean = sp(c).getBoolean("auto_open", false)
    fun setAutoOpen(c: Context, v: Boolean) { sp(c).edit().putBoolean("auto_open", v).apply() }

    /** 服務是否「被使用者啟動」(按停止會變 false, 開機自動啟動以此為準) */
    fun wasStarted(c: Context): Boolean = sp(c).getBoolean("was_started", false)
    fun setWasStarted(c: Context, v: Boolean) { sp(c).edit().putBoolean("was_started", v).apply() }

    /** 連線期間持有 CPU 喚醒鎖 (螢幕關閉也不中斷), 預設開啟 */
    fun wakeLock(c: Context): Boolean = sp(c).getBoolean("wake_lock", true)
    fun setWakeLock(c: Context, v: Boolean) { sp(c).edit().putBoolean("wake_lock", v).apply() }

    /** 目錄設定 (JSON 物件, m=目錄順序 r=快速切換), 空字串 = 沿用控制器自己的設定 */
    fun layout(c: Context): String = sp(c).getString("layout", "") ?: ""
    fun setLayout(c: Context, v: String) { sp(c).edit().putString("layout", v).apply() }

    /** 捷徑清單 (JSON 字串, 格式見 Shortcuts.kt) */
    fun shortcuts(c: Context): String = sp(c).getString("shortcuts", "[]") ?: "[]"
    fun setShortcuts(c: Context, v: String) { sp(c).edit().putString("shortcuts", v).apply() }
}
