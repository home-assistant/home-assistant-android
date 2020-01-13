package io.homeassistant.companion.android.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.RemoteViews
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.widgets.WidgetUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ButtonWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "ButtonWidget"
        private const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.ButtonWidget.CALL_SERVICE"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.ButtonWidget.RECEIVE_DATA"

        internal const val EXTRA_DOMAIN = "EXTRA_DOMAIN"
        internal const val EXTRA_SERVICE = "EXTRA_SERVICE"
        internal const val EXTRA_SERVICE_DATA = "EXTRA_SERVICE_DATA"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase
    @Inject
    lateinit var widgetStorage: WidgetUseCase

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            mainScope.launch {
                try {
                    val intent = Intent(context, ButtonWidget::class.java).apply {
                        action = CALL_SERVICE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }

                    val views = RemoteViews(context.packageName, R.layout.widget_button).apply {
                        setOnClickPendingIntent(
                            R.id.widgetImageButton,
                            PendingIntent.getBroadcast(
                                context,
                                appWidgetId,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )
                        setTextViewText(
                            R.id.widgetLabel,
                            widgetStorage.loadLabel(appWidgetId)
                        )
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widget $appWidgetId", e)
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            mainScope.launch {
                widgetStorage.deleteWidgetData(appWidgetId)
            }
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        Log.d(
            TAG, "Broadcast received: " + System.lineSeparator() +
                    "Broadcast action: " + action + System.lineSeparator() +
                    "AppWidgetId: " + appWidgetId
        )

        ensureInjected(context)

        super.onReceive(context, intent)
        when (action) {
            CALL_SERVICE -> callConfiguredService(context, appWidgetId)
            RECEIVE_DATA -> saveServiceCallConfiguration(context, intent.extras, appWidgetId)
        }
    }

    private fun callConfiguredService(context: Context, appWidgetId: Int) {
        Log.d(TAG, "Calling widget service")

        mainScope.launch {
            try {
                // Load the service call data from Shared Preferences
                val domain = widgetStorage.loadDomain(appWidgetId)
                val service = widgetStorage.loadService(appWidgetId)
                val serviceData = widgetStorage.loadServiceData(appWidgetId)

                Log.d(
                    TAG, "Service Call Data loaded:" + System.lineSeparator() +
                            "domain: " + domain + System.lineSeparator() +
                            "service: " + service + System.lineSeparator() +
                            "service_data: " + serviceData
                )

                if (domain == null || service == null || serviceData == null) {
                    Log.w(TAG, "Service Call Data incomplete.  Aborting service call")
                    return@launch
                }

                val serviceDataMap = HashMap<String, String>()
                serviceDataMap["entity_id"] = serviceData

                integrationUseCase.callService(domain, service, serviceDataMap)

                // Change color of background image to show succsseful call
                val views = RemoteViews(context.packageName, R.layout.widget_button)
                val appWidgetManager = AppWidgetManager.getInstance(context)

                views.setImageViewResource(
                    R.id.widgetImageButtonBackground,
                    R.drawable.ic_circle_yellow_24dp
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)

                // Set a timer to change it back after 1 second
                Handler().postDelayed({
                    views.setImageViewResource(
                        R.id.widgetImageButtonBackground,
                        R.drawable.ic_circle_white_24dp
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }, 1000)
            } catch (e: Exception) {
                Log.e(TAG, "Could not send service call.", e)
            }
        }
    }

    private fun saveServiceCallConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val domain: String? = extras.getString(EXTRA_DOMAIN)
        val service: String? = extras.getString(EXTRA_SERVICE)
        val serviceData: String? = extras.getString(EXTRA_SERVICE_DATA)
        val label: String? = extras.getString(EXTRA_LABEL)

        if (domain == null || service == null || serviceData == null) {
            Log.e(TAG, "Did not receive complete service call data")
            return
        }

        mainScope.launch {
            Log.d(
                TAG, "Saving service call config data:" + System.lineSeparator() +
                        "domain: " + domain + System.lineSeparator() +
                        "service: " + service + System.lineSeparator() +
                        "service_data: " + serviceData + System.lineSeparator() +
                        "label: " + label
            )

            widgetStorage.saveServiceCallData(appWidgetId, domain, service, serviceData)
            widgetStorage.saveLabel(appWidgetId, label)

            // It is the responsibility of the configuration activity to update the app widget
            // This method is only called during the initial setup of the widget,
            // so rather than duplicating code in the ButtonWidgetConfigurationActivity,
            // it is just calling onUpdate manually here.
            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    private fun ensureInjected(context: Context) {
        if (context.applicationContext is GraphComponentAccessor) {
            DaggerProviderComponent.builder()
                .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
        }
    }
}
