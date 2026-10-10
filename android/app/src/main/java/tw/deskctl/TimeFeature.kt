package tw.deskctl

import org.json.JSONObject
import java.util.TimeZone

/** 校時: 連線時與每分鐘送一次手機時間與時區。 */
class TimeFeature(host: FeatureHost) : Feature(host) {
    override val id = "time"

    override fun onReady() {
        push()
    }

    override fun onTick(n: Int) {
        if (n % 60 == 0) push()
    }

    private fun push() {
        val now = System.currentTimeMillis()
        host.send(
            JSONObject().put("k", "time").put("t", now / 1000)
                .put("z", TimeZone.getDefault().getOffset(now) / 60000)
        )
    }
}
