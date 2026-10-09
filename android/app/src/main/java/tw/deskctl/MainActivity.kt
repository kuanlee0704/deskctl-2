package tw.deskctl

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var tvStatus: TextView
    private lateinit var tvCheck: TextView
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        fun label(t: String, size: Float, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fun button(t: String, f: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false
            setOnClickListener { f() }
        }

        root.addView(label("桌邊控制器", 24f, true))
        root.addView(label("ESP32 桌邊藍牙控制器的手機端配套 App", 14f))
        tvStatus = label("", 18f, true).apply { setPadding(0, pad, 0, pad / 2) }
        root.addView(tvStatus)
        tvCheck = label("", 14f).apply { setPadding(0, 0, 0, pad) }
        root.addView(tvCheck)

        root.addView(button("① 授權藍牙與通知") { requestPerms() })
        root.addView(button("② 開啟「通知存取」(讀取音樂資訊)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(button("②-1 若無法勾選: 到 App 資訊 → ⋮ → 允許受限設定") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        root.addView(button("③ 啟動 / 重新連線") { startSvc(BridgeService.ACTION_START) })
        root.addView(button("④ 電池設定: 允許背景執行") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        root.addView(button("停止服務") { startSvc(BridgeService.ACTION_STOP) })
        root.addView(button("忘記已記住的控制器 (重新搜尋)") { startSvc(BridgeService.ACTION_FORGET) })

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
        refresh()
    }

    private fun startSvc(action: String) {
        if (action == BridgeService.ACTION_START && !hasBtPerms()) {
            requestPerms(); return
        }
        val i = Intent(this, BridgeService::class.java).setAction(action)
        if (action == BridgeService.ACTION_STOP) startService(i) else ContextCompat.startForegroundService(this, i)
    }

    private fun neededPerms(): Array<String> {
        val l = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            l.add(Manifest.permission.BLUETOOTH_SCAN); l.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            l.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l.toTypedArray()
    }

    private fun hasBtPerms(): Boolean = neededPerms().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPerms() {
        requestPermissions(neededPerms(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        refresh()
    }

    private fun refresh() {
        val listener = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        tvStatus.text = "狀態: " + BridgeService.status
        tvCheck.text = buildString {
            append(if (hasBtPerms()) "✔ 藍牙/通知權限已授權\n" else "✘ 尚未授權藍牙/通知權限 (按①)\n")
            append(if (listener) "✔ 通知存取已開啟\n" else "✘ 通知存取未開啟, 讀不到歌名 (按②)\n")
            append(if (BridgeService.running) "✔ 服務執行中\n" else "✘ 服務未執行 (按③)\n")
            if (BridgeService.mediaInfo.isNotEmpty()) append("♪ " + BridgeService.mediaInfo)
        }
        h.removeCallbacksAndMessages(null)
        h.postDelayed({ refresh() }, 1000)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacksAndMessages(null)
    }
}
