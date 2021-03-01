package io.homeassistant.companion.android.sensors

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import com.google.android.gms.location.SleepSegmentRequest
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import javax.inject.Inject

class ActivitySensorManager : BroadcastReceiver(), SensorManager {

    companion object {

        internal const val TAG = "ActivitySM"

        const val ACTION_UPDATE_ACTIVITY =
            "io.homeassistant.companion.android.background.UPDATE_ACTIVITY"

        const val ACTION_SLEEP_ACTIVITY = "io.homeassistant.companion.android.background.SLEEP_ACTIVITY"

        private val activity = SensorManager.BasicSensor(
            "detected_activity",
            "sensor",
            R.string.basic_sensor_name_activity,
            R.string.sensor_description_detected_activity
        )

        private val sleepConfidence = SensorManager.BasicSensor(
            "sleep_confidence",
            "sensor",
            R.string.basic_sensor_name_sleep_confidence,
            R.string.sensor_description_sleep_confidence
        )

        private val sleepSegment = SensorManager.BasicSensor(
            "sleep_segment",
            "sensor",
            R.string.basic_sensor_name_sleep_segment,
            R.string.sensor_description_sleep_segment
        )
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onReceive(context: Context, intent: Intent) {
        ensureInjected(context)

        when (intent.action) {
            ACTION_UPDATE_ACTIVITY -> handleActivityUpdate(intent, context)
            ACTION_SLEEP_ACTIVITY -> handleSleepUpdate(intent, context)
            else -> Log.w(TAG, "Unknown intent action: ${intent.action}!")
        }
    }

    private fun ensureInjected(context: Context) {
        if (context.applicationContext is GraphComponentAccessor) {
            DaggerSensorComponent.builder()
                .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
        }
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivitySensorManager::class.java)
        intent.action = ACTION_UPDATE_ACTIVITY
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getSleepPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ActivitySensorManager::class.java)
        intent.action = ACTION_SLEEP_ACTIVITY
        return PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun handleActivityUpdate(intent: Intent, context: Context) {
        Log.d(TAG, "Received activity update.")
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            var probActivity = typeToString(result.mostProbableActivity)

            if (probActivity == "on_foot")
                probActivity = getSubActivity(result)

            onSensorUpdated(
                context,
                activity,
                probActivity,
                getSensorIcon(probActivity),
                result.probableActivities.map { typeToString(it) to it.confidence }.toMap()
            )
        }
    }

    private fun handleSleepUpdate(intent: Intent, context: Context) {
        Log.d(TAG, "Received sleep update")
        if (SleepClassifyEvent.hasEvents(intent) && isEnabled(context, sleepConfidence.id)) {
            val sleepClassifyEvent = SleepClassifyEvent.extractEvents(intent)
            if (sleepClassifyEvent.size > 0) {
                onSensorUpdated(
                    context,
                    sleepConfidence,
                    sleepClassifyEvent[0].confidence,
                    "mdi:sleep",
                    mapOf(
                        "light" to sleepClassifyEvent[0].light,
                        "motion" to sleepClassifyEvent[0].motion,
                        "timestamp" to sleepClassifyEvent[0].timestampMillis
                    )
                )
            }
        }
        if (SleepSegmentEvent.hasEvents(intent) && isEnabled(context, sleepSegment.id)) {
            val sleepSegmentEvent = SleepSegmentEvent.extractEvents(intent)
            if (sleepSegmentEvent.size > 0) {
                onSensorUpdated(
                    context,
                    sleepSegment,
                    sleepSegmentEvent[0].segmentDurationMillis,
                    "mdi:sleep",
                    mapOf(
                        "start" to sleepSegmentEvent[0].startTimeMillis,
                        "end" to sleepSegmentEvent[0].endTimeMillis,
                        "status" to getSleepSegmentStatus(sleepSegmentEvent[0].status)
                    )
                )
            }
        }
    }

    private fun typeToString(activity: DetectedActivity): String {
        return when (activity.type) {
            DetectedActivity.IN_VEHICLE -> "in_vehicle"
            DetectedActivity.ON_BICYCLE -> "on_bicycle"
            DetectedActivity.ON_FOOT -> "on_foot"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.STILL -> "still"
            DetectedActivity.TILTING -> "tilting"
            DetectedActivity.WALKING -> "walking"
            DetectedActivity.UNKNOWN -> "unknown"
            else -> "unknown"
        }
    }

    private fun getSubActivity(result: ActivityRecognitionResult): String {
        if (result.probableActivities[1].type == DetectedActivity.RUNNING) return "running"
        if (result.probableActivities[1].type == DetectedActivity.WALKING) return "walking"
        return "on_foot"
    }

    private fun getSleepSegmentStatus(int: Int): String {
        return when (int) {
            SleepSegmentEvent.STATUS_SUCCESSFUL -> "successful"
            SleepSegmentEvent.STATUS_MISSING_DATA -> "missing data"
            SleepSegmentEvent.STATUS_NOT_DETECTED -> "not detected"
            else -> "unknown"
        }
    }

    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = R.string.sensor_name_activity

    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(activity, sleepConfidence, sleepSegment)

    override fun requiredPermissions(sensorId: String): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            arrayOf()
        }
    }

    override fun requestSensorUpdate(context: Context) {
        if (isEnabled(context, activity.id)) {
            val actReg = ActivityRecognition.getClient(context)
            val pendingIntent = getPendingIntent(context)
            Log.d(TAG, "Unregistering for activity updates.")
            actReg.removeActivityUpdates(pendingIntent)

            Log.d(TAG, "Registering for activity updates.")
            actReg.requestActivityUpdates(120000, pendingIntent)
        }
        if (isEnabled(context, sleepConfidence.id) || isEnabled(context, sleepSegment.id)) {
            val pendingIntent = getSleepPendingIntent(context)
            Log.d(TAG, "Unregistering for sleep updates")
            ActivityRecognition.getClient(context).removeSleepSegmentUpdates(pendingIntent)
            Log.d(TAG, "Registering for sleep updates")
            val task = when {
                (isEnabled(context, sleepConfidence.id) && isEnabled(context, sleepSegment.id)) -> {
                    Log.d(TAG, "Registering for both sleep confidence and segment updates")
                    ActivityRecognition.getClient(context).requestSleepSegmentUpdates(
                        pendingIntent,
                        SleepSegmentRequest.getDefaultSleepSegmentRequest()
                    )
                }
                (isEnabled(context, sleepConfidence.id) && !isEnabled(context, sleepSegment.id)) -> {
                    Log.d(TAG, "Registering for sleep confidence updates only")
                    ActivityRecognition.getClient(context).requestSleepSegmentUpdates(
                        pendingIntent,
                        SleepSegmentRequest(SleepSegmentRequest.CLASSIFY_EVENTS_ONLY)
                    )
                }
                (!isEnabled(context, sleepConfidence.id) && isEnabled(context, sleepSegment.id)) -> {
                    Log.d(TAG, "Registering for sleep segment updates only")
                    ActivityRecognition.getClient(context).requestSleepSegmentUpdates(
                        pendingIntent,
                        SleepSegmentRequest(SleepSegmentRequest.SEGMENT_EVENTS_ONLY)
                    )
                }
                else -> ActivityRecognition.getClient(context).removeSleepSegmentUpdates(pendingIntent)
            }
            task.addOnSuccessListener {
                Log.d(TAG, "Successfully registered for sleep updates")
            }
            task.addOnFailureListener {
                Log.e(TAG, "Failed to register for sleep updates", it)
                ActivityRecognition.getClient(context).removeSleepSegmentUpdates(pendingIntent)
            }
        }
    }

    private fun getSensorIcon(activity: String): String {

        return when (activity) {
            "in_vehicle" -> "mdi:car"
            "on_bicycle" -> "mdi:bike"
            "on_foot" -> "mdi:shoe-print"
            "still" -> "mdi:sleep"
            "tilting" -> "mdi:phone-rotate-portrait"
            "walking" -> "mdi:walk"
            "running" -> "mdi:run"
            else -> "mdi:progress-question"
        }
    }
}
