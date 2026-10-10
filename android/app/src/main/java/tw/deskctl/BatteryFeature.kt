package tw.deskctl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** 手機電量與充電狀態。 */
class BatteryFeature(host: FeatureHost) : Feature(host) {
    override val id = "battery"
    private var pct = -1
    private var charging = false
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val lv = i.getIntExtra("level", -1)
            val sc = i.getIntExtra("scale", 100)
            pct = if (lv >= 0 && sc > 0) lv * 100 / sc else -1
            val st = i.getIntExtra("status", -1)
            charging = st == 2 || st == 5   // CHARGING / FULL
            push()
        }
    }

    override fun start() {
        if (registered) return
        ContextCompat.registerReceiver(
            host.context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    override fun stop() {
        if (!registered) return
        registered = false
        try {
            host.context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }

    override fun onReady() {
        push()
    }

    private fun push() {
        if (pct < 0) return
        host.sendIfChanged("batt", JSONObject().put("k", "batt").put("p", pct).put("ch", if (charging) 1 else 0))
    }
}
