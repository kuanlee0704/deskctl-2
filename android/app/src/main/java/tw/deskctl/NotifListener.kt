package tw.deskctl

import android.service.notification.NotificationListenerService

/** 空的通知監聽服務: 使用者授權「通知存取」後, App 才有權讀取其他 App 的媒體工作階段(歌名/進度)。 */
class NotifListener : NotificationListenerService()
