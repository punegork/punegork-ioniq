package com.punegork.ioniqtelemetry.obd

import android.content.Context
import com.punegork.ioniqtelemetry.telemetry.MockObdTelemetrySource
import com.punegork.ioniqtelemetry.telemetry.TelemetrySource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class ObdMode {
    DEMO,
    VGATE_WIFI
}

data class ObdUiState(
    val mode: ObdMode = ObdMode.DEMO,
    val status: String = "Demo modu",
    val adapterId: String? = null,
    val lastRawResponse: String? = null,
    val lastProbeSummary: String? = null
)

class ObdController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("obd", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        ObdUiState(
            mode = if (prefs.getString("mode", "DEMO") == "VGATE_WIFI") {
                ObdMode.VGATE_WIFI
            } else {
                ObdMode.DEMO
            }
        )
    )
    val state: StateFlow<ObdUiState> = _state

    private val demo = MockObdTelemetrySource()
    private val vgate = VgateWifiTelemetrySource(appContext) { status, adapter, raw ->
        _state.update {
            it.copy(
                status = status,
                adapterId = adapter ?: it.adapterId,
                lastRawResponse = raw ?: it.lastRawResponse
            )
        }
    }

    fun selectMode(mode: ObdMode) {
        prefs.edit().putString("mode", mode.name).apply()
        _state.update {
            it.copy(
                mode = mode,
                status = if (mode == ObdMode.DEMO) "Demo modu" else "Vgate Wi‑Fi seçildi"
            )
        }
    }

    fun source(): TelemetrySource =
        if (_state.value.mode == ObdMode.VGATE_WIFI) vgate else demo

    suspend fun probeVgate() {
        _state.update { it.copy(status = "Vgate test ediliyor…", lastProbeSummary = null) }
        try {
            val result = vgate.probe()
            _state.update {
                it.copy(
                    status = "Vgate + Ioniq BMS hazır",
                    adapterId = result.adapterId,
                    lastRawResponse = result.raw.takeLast(500),
                    lastProbeSummary = result.summary
                )
            }
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    status = "Test başarısız: ${t.message ?: t::class.java.simpleName}",
                    lastProbeSummary = null
                )
            }
        }
    }
}
