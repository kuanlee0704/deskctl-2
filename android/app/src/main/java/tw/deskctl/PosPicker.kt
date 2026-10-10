package tw.deskctl

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 在畫面上「選取點擊位置」: 浮出一個可拖曳的十字準星 + 底部的「確定/取消」列。
 * 使用者先切到目標 App 畫面, 把準星拖到要點的位置按「確定」, 回傳 橫%、縱% (不用自己算座標)。
 * 需要「顯示在其他 App 上層」權限。
 */
object PosPicker {
    private var cross: View? = null
    private var bar: View? = null

    fun active(): Boolean = cross != null

    fun start(ctx0: Context, initX: Float, initY: Float, onDone: (Float, Float) -> Unit): Boolean {
        val ctx = ctx0.applicationContext
        if (!Settings.canDrawOverlays(ctx)) return false
        stop(ctx)
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        val d = ctx.resources.displayMetrics.density
        val size = (72 * d).toInt()

        val tv = TextView(ctx)
        fun pct(): Pair<Float, Float> {
            val v = cross ?: return Pair(initX, initY)
            val loc = IntArray(2)
            v.getLocationOnScreen(loc)
            val x = (loc[0] + size / 2f) * 100f / dm.widthPixels
            val y = (loc[1] + size / 2f) * 100f / dm.heightPixels
            return Pair(x.coerceIn(0f, 100f), y.coerceIn(0f, 100f))
        }
        fun refreshText() {
            val (x, y) = pct()
            tv.text = String.format("橫 %.0f%%  縱 %.0f%%", x, y)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cv = object : View(ctx) {
            override fun onDraw(c: Canvas) {
                val w = width / 2f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3 * d
                paint.color = 0xFFE53935.toInt()
                c.drawCircle(w, w, w - 4 * d, paint)
                paint.strokeWidth = 2 * d
                c.drawLine(w, 4 * d, w, width - 4 * d, paint)
                c.drawLine(4 * d, w, width - 4 * d, w, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(w, w, 3 * d, paint)
            }
        }
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (dm.widthPixels * initX / 100f - size / 2f).toInt()
        lp.y = (dm.heightPixels * initY / 100f - size / 2f).toInt()
        var lastX = 0f
        var lastY = 0f
        cv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.rawX
                    lastY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x += (e.rawX - lastX).toInt()
                    lp.y += (e.rawY - lastY).toInt()
                    lastX = e.rawX
                    lastY = e.rawY
                    try {
                        wm.updateViewLayout(cv, lp)
                    } catch (_: Exception) {
                    }
                    cv.post { refreshText() }
                }
            }
            true
        }

        val b = LinearLayout(ctx)
        b.orientation = LinearLayout.VERTICAL
        b.gravity = Gravity.CENTER_HORIZONTAL
        val bg = GradientDrawable()
        bg.setColor(0xE6212121.toInt())
        bg.cornerRadius = 16 * d
        b.background = bg
        b.setPadding((16 * d).toInt(), (10 * d).toInt(), (16 * d).toInt(), (10 * d).toInt())
        tv.setTextColor(Color.WHITE)
        tv.textSize = 15f
        tv.gravity = Gravity.CENTER
        val hint = TextView(ctx)
        hint.text = "拖曳紅色準星到要點的位置"
        hint.setTextColor(0xFFBDBDBD.toInt())
        hint.textSize = 12f
        hint.gravity = Gravity.CENTER
        b.addView(hint)
        b.addView(tv)
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        fun btn(t: String, f: () -> Unit) {
            val x = Button(ctx)
            x.text = t
            x.isAllCaps = false
            x.setOnClickListener { f() }
            row.addView(x, LinearLayout.LayoutParams(0, -2, 1f))
        }
        btn("取消") { stop(ctx) }
        btn("確定") {
            val (x, y) = pct()
            stop(ctx)
            onDone(x, y)
        }
        b.addView(row, LinearLayout.LayoutParams((240 * d).toInt(), -2))
        val blp = WindowManager.LayoutParams(
            -2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        blp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        blp.y = (48 * d).toInt()

        return try {
            wm.addView(cv, lp)
            wm.addView(b, blp)
            cross = cv
            bar = b
            cv.post { refreshText() }
            true
        } catch (e: Exception) {
            AppLog.w("PICK", "無法顯示選取準星: " + e)
            stop(ctx)
            false
        }
    }

    fun stop(ctx0: Context) {
        val wm = ctx0.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        for (v in listOf(cross, bar)) {
            try {
                if (v != null) wm.removeView(v)
            } catch (_: Exception) {
            }
        }
        cross = null
        bar = null
    }
}
