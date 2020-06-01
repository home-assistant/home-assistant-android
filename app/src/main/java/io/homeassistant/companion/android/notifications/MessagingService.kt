package io.homeassistant.companion.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.background.LocationBroadcastReceiver
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.authentication.AuthenticationUseCase
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import io.homeassistant.companion.android.util.UrlHandler
import io.homeassistant.companion.android.webview.WebViewActivity
import kotlinx.coroutines.*
import java.net.URL
import java.text.DateFormat
import javax.inject.Inject

class MessagingService : FirebaseMessagingService() {
    companion object {
        const val TAG = "MessagingService"
        const val TITLE = "title"
        const val MESSAGE = "message"
        const val IMAGE_URL = "image"

        // special action constants
        const val REQUEST_LOCATION_UPDATE = "request_location_update"
        const val CLEAR_NOTIFICATION = "clear_notification"
        const val REMOVE_CHANNEL = "remove_channel"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    @Inject
    lateinit var urlUseCase: UrlUseCase

    @Inject
    lateinit var authenticationUseCase: AuthenticationUseCase

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        DaggerServiceComponent.builder()
            .appComponent((applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        //TODO: Save message to database

        // Check if message contains a data payload.
        remoteMessage.data.let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)

            when {
                it[MESSAGE] == REQUEST_LOCATION_UPDATE -> {
                    Log.d(TAG, "Request location update")
                    requestAccurateLocationUpdate()
                }
                it[MESSAGE] == CLEAR_NOTIFICATION && !it["tag"].isNullOrBlank() -> {
                    Log.d(TAG, "Clearing notification with tag: ${it["tag"]}")
                    clearNotification(it["tag"]!!)
                }
                it[MESSAGE] == REMOVE_CHANNEL && !it["channel"].isNullOrBlank() -> {
                    Log.d(TAG, "Removing Notification channel ${it["tag"]}")
                    removeNotificationChannel(it["channel"]!!)
                }
                else -> mainScope.launch {
                    Log.d(TAG, "Creating notification with following data: $it")
                    sendNotification(it)
                }
            }
        }
    }

    private fun requestAccurateLocationUpdate() {
        val intent = Intent(this, LocationBroadcastReceiver::class.java)
        intent.action = LocationBroadcastReceiver.ACTION_REQUEST_ACCURATE_LOCATION_UPDATE

        sendBroadcast(intent)
    }

    private fun clearNotification(tag: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val messageId = tag.hashCode()

        notificationManager.cancel(tag, messageId)
    }

    private fun removeNotificationChannel(channelName: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelID: String = createChannelID(channelName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.deleteNotificationChannel(channelID)
        }
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     */
    private suspend fun sendNotification(data: Map<String, String>) {

        val tag = data["tag"]
        val messageId = tag?.hashCode() ?: System.currentTimeMillis().toInt()

        val pendingIntent = handleIntent(data)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = handleChannel(notificationManager, data)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        handleColor(notificationBuilder, data)

        handleSticky(notificationBuilder, data)

        handleText(notificationBuilder, data)

        handleImage(notificationBuilder, data)

        handleActions(notificationBuilder, data, tag, messageId)

        if (tag != null) {
            notificationManager.notify(tag, messageId, notificationBuilder.build())
        } else {
            notificationManager.notify(messageId, notificationBuilder.build())
        }

        saveMessageToDb(data)

    }

    private fun saveMessageToDb(data: Map<String, String>) {

        //TODO: Not getting the tag??

        val db = NotificationsDB(this)
        val tag = data[TAG]
        val title = data[TITLE]
        val message = data[MESSAGE]
        val image = data[IMAGE_URL]
        val time = DateFormat.getDateTimeInstance().format(System.currentTimeMillis())
        val read = true // TODO: Change this to false when ready to monitor read state

        try {

            db.open()
            db.addMessage(tag, title, message, image, time, read, "incoming")

            //TODO - remove logger...
            Log.d("test", tag + title + message + image + time + read + "incoming")

        } catch (e: java.lang.Exception) {

            e.printStackTrace()
            //TODO - handle errors?

        } finally {

            db.close()

        }


    }

    private fun handleIntent(
        data: Map<String, String>
    ): PendingIntent {
        val url = data["clickAction"]

        val intent = if (UrlHandler.isAbsoluteUrl(url)) {
            Intent(Intent.ACTION_VIEW).apply {
                this.data = Uri.parse(url)
            }
        } else {
            WebViewActivity.newInstance(this, url)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        return PendingIntent.getActivity(
            this, 0, intent, 0
        )
    }

    private fun handleColor(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {

        val colorString = data["color"]
        var color = ContextCompat.getColor(this, R.color.colorPrimary)

        if (!colorString.isNullOrBlank()) {
            try {
                color = Color.parseColor(colorString)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to parse color", e)
            }
        }

        builder.color = color
    }

    private fun handleSticky(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        val sticky = data["sticky"]?.toBoolean() ?: false
        builder.setAutoCancel(!sticky)
    }

    private fun handleText(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        builder
            .setContentTitle(data[TITLE])
            .setContentText(data[MESSAGE])
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(data[MESSAGE])
            )
    }

    private suspend fun handleImage(
        builder: NotificationCompat.Builder,
        data: Map<String, String>
    ) {
        data[IMAGE_URL]?.let {
            val url = UrlHandler.handle(urlUseCase.getUrl(), it)
            val bitmap = getImageBitmap(url, !UrlHandler.isAbsoluteUrl(it))
            if (bitmap != null) {
                builder
                    .setLargeIcon(bitmap)
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null)
                    )
            }
        }
    }

    private suspend fun getImageBitmap(url: URL?, requiresAuth: Boolean = false): Bitmap? = withContext(Dispatchers.IO) {
        if (url == null)
            return@withContext null

        var image: Bitmap? = null
        try {
            val uc = url.openConnection()
            if (requiresAuth) {
                uc.setRequestProperty("Authorization", authenticationUseCase.buildBearerToken())
            }
            image = BitmapFactory.decodeStream(uc.getInputStream())
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't download image for notification", e)
        }
        return@withContext image
    }

    private fun handleActions(
        builder: NotificationCompat.Builder,
        data: Map<String, String>,
        tag: String?,
        messageId: Int
    ) {
        for (i in 1..3) {
            if (data.containsKey("action_${i}_key")) {
                val notificationAction = NotificationAction(
                    data["action_${i}_key"].toString(),
                    data["action_${i}_title"].toString(),
                    data["action_${i}_uri"],
                    data
                )
                val actionIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                    action =
                        if (notificationAction.key == "URI")
                            NotificationActionReceiver.OPEN_URI
                        else
                            NotificationActionReceiver.FIRE_EVENT
                    if (data["sticky"]?.toBoolean() != true) {
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_TAG, tag)
                        putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, messageId)
                    }
                    putExtra(
                        NotificationActionReceiver.EXTRA_NOTIFICATION_ACTION,
                        notificationAction
                    )
                }
                val actionPendingIntent = PendingIntent.getBroadcast(
                    this,
                    (notificationAction.title.hashCode() + System.currentTimeMillis()).toInt(),
                    actionIntent,
                    0
                )

                builder.addAction(0, notificationAction.title, actionPendingIntent)
            }
        }
    }

    private fun handleChannel(
        notificationManager: NotificationManager,
        data: Map<String, String>
    ): String {
        // Define some values for a default channel
        var channelID = "default"
        var channelName = "Default Channel"

        if (data.containsKey("channel")) {
            channelID = createChannelID(data["channel"].toString())
            channelName = data["channel"].toString().trim()
        }

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelID,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        return channelID
    }

    private fun createChannelID(
        channelName: String
    ): String {
        return channelName
            .trim()
            .toLowerCase()
            .replace(" ", "_")
    }

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
        mainScope.launch {
            Log.d(TAG, "Refreshed token: $token")
            try {
                integrationUseCase.updateRegistration(
                    pushToken = token
                )
            } catch (e: Exception) {
                // TODO: Store for update later
                Log.e(TAG, "Issue updating token", e)
            }
        }
    }
}
