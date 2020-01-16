package io.homeassistant.companion.android.settings

import androidx.preference.PreferenceDataStore
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.url.UrlUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SettingsPresenterImpl @Inject constructor(
    private val settingsView: SettingsView,
    private val urlUseCase: UrlUseCase,
    private val integrationUseCase: IntegrationUseCase
) : SettingsPresenter, PreferenceDataStore() {

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return runBlocking {
            return@runBlocking when (key) {
                "location_zone" -> integrationUseCase.isZoneTrackingEnabled()
                "location_background" -> integrationUseCase.isBackgroundTrackingEnabled()
                "fullscreen" -> integrationUseCase.isFullScreenEnabled()
                else -> throw Exception()
            }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        mainScope.launch {
            when (key) {
                "location_zone" -> integrationUseCase.setZoneTrackingEnabled(value)
                "location_background" -> integrationUseCase.setBackgroundTrackingEnabled(value)
                "fullscreen" -> integrationUseCase.setFullScreenEnabled(value)
                else -> throw Exception()
            }
            settingsView.onLocationSettingChanged()
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        return runBlocking {
            when (key) {
                "connection_internal" -> (urlUseCase.getUrl(true) ?: "").toString()
                "connection_internal_wifi" -> urlUseCase.getHomeWifiSsid()
                "connection_external" -> (urlUseCase.getUrl(false) ?: "").toString()
                "registration_name" -> integrationUseCase.getRegistration().deviceName
                else -> throw Exception()
            }
        }
    }

    override fun putString(key: String?, value: String?) {
        mainScope.launch {
            when (key) {
                "connection_internal" -> {
                    urlUseCase.saveUrl(value ?: "", true)
                    settingsView.onUrlChanged()
                }
                "connection_internal_wifi" -> {
                    urlUseCase.saveHomeWifiSsid(value)
                    handleInternalUrlStatus(value)
                }
                "connection_external" -> {
                    urlUseCase.saveUrl(value ?: "", false)
                    settingsView.onUrlChanged()
                }
                "registration_name" -> integrationUseCase.updateRegistration(deviceName = value!!)
                else -> throw Exception()
            }
        }
    }

    override fun getPreferenceDataStore(): PreferenceDataStore {
        return this
    }

    override fun onCreate() {
        mainScope.launch {
            handleInternalUrlStatus(urlUseCase.getHomeWifiSsid())
        }
    }

    override fun onFinish() {
        mainScope.cancel()
    }

    private suspend fun handleInternalUrlStatus(ssid: String?) {
        if (ssid.isNullOrBlank()) {
            settingsView.disableInternalConnection()
            urlUseCase.saveUrl("", true)
            settingsView.onUrlChanged()
        } else {
            settingsView.enableInternalConnection()
        }
    }
}
