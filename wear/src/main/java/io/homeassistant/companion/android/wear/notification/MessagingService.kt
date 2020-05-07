package io.homeassistant.companion.android.wear.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import io.homeassistant.companion.android.notification.AbstractMessagingService
import io.homeassistant.companion.android.notification.AbstractNotificationActionReceiver
import io.homeassistant.companion.android.notification.AbstractNotificationActionReceiver.Companion.OPEN_URI
import io.homeassistant.companion.android.notification.NotificationAction

@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MessagingService : AbstractMessagingService() {

    companion object {
        const val EXTRA_ACTION_URL = "MessagingService.ACTION_URL"
    }

    override fun actionHandler(): Class<*> = NotificationActionReceiver::class.java

    override fun handleIntent(
        notificationTag: String?, messageId: Int, actionUrl: String?
    ): PendingIntent {
        val dummyWithActionUrl = NotificationAction(EXTRA_ACTION_URL, EXTRA_ACTION_URL, actionUrl, HashMap())

        val intent = Intent(this, NotificationActionReceiver::class.java)
            .setAction(OPEN_URI)
            .putExtra(AbstractNotificationActionReceiver.EXTRA_NOTIFICATION_ID, messageId)
            .putExtra(AbstractNotificationActionReceiver.EXTRA_NOTIFICATION_TAG, notificationTag)
            .putExtra(AbstractNotificationActionReceiver.EXTRA_NOTIFICATION_ACTION, dummyWithActionUrl)

        return PendingIntent.getBroadcast(this, messageId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

}