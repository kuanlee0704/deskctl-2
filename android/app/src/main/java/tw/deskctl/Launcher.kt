package tw.deskctl

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * 從背景開啟 App / 網址。Android 10+ 禁止背景直接開 Activity, 這裡用兩個合法管道:
 *  1. 已開啟「無障礙服務」時, 由它發出 (視為使用者授權的輔助功能)
 *  2. 已授權「顯示在其他 App 上層」時, 先放一個看不見的 1x1 浮動視窗再開 (系統視為前景)
 */
object Launcher {
    fun start(ctx: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        var view: View? = null
        try {
            if (Settings.canDrawOverlays(ctx)) {
                val v = View(ctx)
                val lp = WindowManager.LayoutParams(
                    1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )
                wm.addView(v, lp)
                view = v
            }
        } catch (e: Exception) {
            AppLog.w("LAUNCH", "浮動視窗失敗: " + e)
        }
        var ok = true
        try {
            val acc = DeskAccess.instance
            if (acc != null) acc.startActivity(intent) else ctx.startActivity(intent)
        } catch (e: Exception) {
            AppLog.e("LAUNCH", "開啟失敗: " + e)
            ok = false
        }
        val v = view
        if (v != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    wm.removeView(v)
                } catch (_: Exception) {
                }
            }, 2500)
        }
        return ok
    }

    fun overlayOk(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun accessibilityOn(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.contains(ctx.packageName + "/")
    }
}
