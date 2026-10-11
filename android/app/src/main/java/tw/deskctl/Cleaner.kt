package tw.deskctl

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.os.Handler
import android.provider.Settings
import org.json.JSONArray
import java.io.File

/**
 * 手機清理 (全部不需要 root):
 *  1. 結束背景 App (killBackgroundProcesses): 釋放記憶體; 不會動前景/正在播放/有媒體工作階段/輸入法/桌面/你自己列入保護的 App
 *  2. 清除本 App 的快取 (別的 App 的快取 Android 不允許第三方清除)
 *  3. 清除「最近使用」清單: 用無障礙服務開啟最近工作並點「全部關閉」(各品牌按鈕名稱不同, 見 CLEAR_LABELS)
 */
object Cleaner {
    val CLEAR_LABELS = arrayOf("全部關閉", "全部清除", "清除全部", "關閉全部", "全部結束", "Close all", "Clear all", "CLEAR ALL")

    class Result(val freedMb: Int, val tried: Int, val cacheKb: Int)

    private fun availMb(ctx: Context): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return (mi.availMem / 1048576L).toInt()
    }

    /** 不能清除的 App: 本 App + 有媒體工作階段的 App + 輸入法 + 桌面 + 使用者保護清單 */
    fun protectedPkgs(ctx: Context): Set<String> {
        val s = HashSet<String>()
        s.add(ctx.packageName)
        s.addAll(Prefs.cleanProtect(ctx))
        try {
            val mgr = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            for (c in mgr.getActiveSessions(ComponentName(ctx, NotifListener::class.java))) s.add(c.packageName)
        } catch (_: Exception) {
        }
        try {
            val ime = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ComponentName.unflattenFromString(ime ?: "")?.let { s.add(it.packageName) }
        } catch (_: Exception) {
        }
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName?.let { s.add(it) }
        } catch (_: Exception) {
        }
        return s
    }

    fun launcherApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }

    /** 結束背景 App; 結果 (釋放量) 要等系統回收, 所以由呼叫端延遲後再用 availMb 比較 */
    fun killBackground(ctx: Context): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val keep = protectedPkgs(ctx)
        var n = 0
        for ((_, pkg) in launcherApps(ctx)) {
            if (pkg in keep) continue
            try {
                am.killBackgroundProcesses(pkg)
                n++
            } catch (_: Exception) {
            }
        }
        return n
    }

    fun clearOwnCache(ctx: Context): Int {
        var bytes = 0L
        fun wipe(f: File?, root: Boolean) {
            if (f == null) return
            val l = f.listFiles()
            if (l != null) for (x in l) wipe(x, false)
            if (!root) {
                bytes += f.length()
                f.delete()
            }
        }
        wipe(ctx.cacheDir, true)
        wipe(ctx.externalCacheDir, true)
        return (bytes / 1024L).toInt()
    }

    /** 結束背景 App + 清快取, 約 2 秒後回報釋放的記憶體 */
    fun cleanMemory(ctx: Context, h: Handler, done: (Result) -> Unit) {
        val before = availMb(ctx)
        val tried = killBackground(ctx)
        val kb = clearOwnCache(ctx)
        h.postDelayed({ done(Result((availMb(ctx) - before).coerceAtLeast(0), tried, kb)) }, 2000L)
    }

    /** 清除最近使用的 App (需無障礙)。完成後回到桌面。 */
    fun clearRecents(h: Handler, done: (Boolean, String) -> Unit) {
        if (DeskAccess.instance == null) {
            done(false, "需要開啟無障礙服務")
            return
        }
        if (!DeskAccess.recents()) {
            done(false, "無法開啟最近工作")
            return
        }
        h.postDelayed({
            val ok = DeskAccess.clickAny(*CLEAR_LABELS)
            if (!ok) DeskAccess.dump("畫面內容")
            h.postDelayed({
                DeskAccess.home()
                done(ok, if (ok) "" else "找不到「全部關閉」按鈕")
            }, 900L)
        }, 1200L)
    }

    fun encode(l: Collection<String>): String = JSONArray(l.toList()).toString()
}
