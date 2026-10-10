package tw.deskctl

class FeatureEntry(val id: String, val title: String, val make: (FeatureHost) -> Feature)

/**
 * 功能登記表。要新增功能: 寫一個 Feature 子類別, 然後在 registry 加一行 FeatureEntry。
 */
object Features {
    val registry: List<FeatureEntry> = listOf(
        FeatureEntry("time", "時間同步") { h -> TimeFeature(h) },
        FeatureEntry("battery", "手機電量") { h -> BatteryFeature(h) },
        FeatureEntry("media", "音樂控制") { h -> MediaFeature(h) },
        FeatureEntry("shortcut", "捷徑") { h -> ShortcutFeature(h) },
        // 新增功能就加在這裡, 例如: FeatureEntry("weather", "天氣") { h -> WeatherFeature(h) },
    )
}
