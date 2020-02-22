package io.homeassistant.companion.android.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.widget.Toast
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "NotifActionReceiver"
        const val FIRE_EVENT = "FIRE_EVENT"
        const val OPEN_URI = "OPEN_URI"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_NOTIFICATION_ACTION = "EXTRA_ACTION_KEY"
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    override fun onReceive(context: Context, intent: Intent) {
        DaggerServiceComponent.builder()
            .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        val notificationAction =
            intent.getParcelableExtra<NotificationAction>(EXTRA_NOTIFICATION_ACTION)

        if (notificationAction == null) {
            Log.e(TAG, "Failed to get notification action.")
            return
        }

        val messageId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val onComplete: () -> Unit = {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(messageId)
        }
        val onFailure: () -> Unit = {
            Handler(context.mainLooper).post {
                Toast.makeText(context, "Unable to send event to HA.", Toast.LENGTH_LONG).show()
            }
        }
        when (intent.action) {
            FIRE_EVENT -> fireEvent(notificationAction, onComplete, onFailure)
            OPEN_URI -> openUri(context, notificationAction, onComplete)
        }
    }

    private fun fireEvent(
        action: NotificationAction,
        onComplete: () -> Unit,
        onFailure: () -> Unit
    ) {
        ioScope.launch {
            try {
                integrationUseCase.fireEvent(
                    "mobile_app_notification_action",
                    action.data.plus(Pair("action", action.key))
                )
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to fire event.", e)
                onFailure()
            }
        }
    }

    private fun openUri(context: Context, action: NotificationAction, onComplete: () -> Unit) {
        val newIntent = Intent(Intent.ACTION_VIEW)
        newIntent.data = Uri.parse(action.uri)
        newIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(newIntent)
        onComplete()
    }
}
