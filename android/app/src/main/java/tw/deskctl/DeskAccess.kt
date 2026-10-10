package tw.deskctl

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 無障礙服務 (選用): 讓捷徑能「點擊畫面位置」「點擊含某文字的按鈕」「返回」。
 * 不讀取也不上傳任何畫面內容, 只在執行捷徑的步驟時使用。
 */
class DeskAccess : AccessibilityService() {

    companion object {
        @Volatile var instance: DeskAccess? = null

        fun tap(xPct: Float, yPct: Float): Boolean {
            val s = instance ?: return false
            return s.doTap(xPct, yPct)
        }

        fun clickText(t: String): Boolean {
            val s = instance ?: return false
            return s.doClickText(t)
        }

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 350L): Boolean {
            val s = instance ?: return false
            return s.doSwipe(x1, y1, x2, y2, ms)
        }

        /** 重新整理目前頁面: 先找「重新載入/Reload…」按鈕, 找不到就下拉 */
        fun refreshPage(): Boolean {
            val s = instance ?: return false
            for (t in arrayOf("重新載入", "重新整理", "Reload", "Refresh")) {
                if (s.doClickText(t)) return true
            }
            return s.doSwipe(50f, 30f, 50f, 80f, 450L)
        }

        fun back(): Boolean {
            val s = instance ?: return false
            return s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppLog.i("ACC", "無障礙服務已連線")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun realMetrics(): android.util.DisplayMetrics {
        val dm = android.util.DisplayMetrics()
        val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        return dm
    }

    private fun doTap(xp: Float, yp: Float): Boolean {
        val dm = realMetrics()
        val p = Path()
        p.moveTo(dm.widthPixels * xp / 100f, dm.heightPixels * yp / 100f)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0L, 80L))
            .build()
        return dispatchGesture(g, null, null)
    }

    private fun doSwipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long): Boolean {
        val dm = realMetrics()
        val p = Path()
        p.moveTo(dm.widthPixels * x1 / 100f, dm.heightPixels * y1 / 100f)
        p.lineTo(dm.widthPixels * x2 / 100f, dm.heightPixels * y2 / 100f)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0L, ms))
            .build()
        return dispatchGesture(g, null, null)
    }

    private fun doClickText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val q = ArrayList<AccessibilityNodeInfo>()
        q.add(root)
        var n = 0
        while (q.isNotEmpty() && n < 3000) {
            val node = q.removeAt(0)
            n++
            val label = (node.text?.toString() ?: "") + "|" + (node.contentDescription?.toString() ?: "")
            if (label.contains(t, ignoreCase = true)) {
                var c: AccessibilityNodeInfo? = node
                while (c != null) {
                    if (c.isClickable) return c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    c = c.parent
                }
            }
            for (i in 0 until node.childCount) {
                val ch = node.getChild(i)
                if (ch != null) q.add(ch)
            }
        }
        return false
    }
}
