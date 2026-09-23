package com.punegork.ioniqtelemetry.obd

import android.content.Context
import com.punegork.ioniqtelemetry.telemetry.TelemetrySource
import com.punegork.ioniqtelemetry.telemetry.VehicleTelemetry
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class VgateWifiTelemetrySource(
    private val context: Context,
    private val onStatus: (String, String?, String?) -> Unit = { _, _, _ -> }
) : TelemetrySource {
    override val sourceName: String = "VGATE WIFI • READ ONLY"

    data class ProbeResult(
        val adapterId: String,
        val summary: String,
        val raw: String
    )

    suspend fun probe(): ProbeResult {
        val client = Elm327Client(context)
        try {
            onStatus("Vgate Wi‑Fi bağlanıyor…", null, null)
            client.connect()
            val id = client.initialize()
            onStatus("ELM hazır, BMS okunuyor…", id, null)

            val raw = client.query("220101")
            val payload = ElmIsoTpParser.extractDidPayload(raw, 0x01, 0x01)
                ?: error("220101 cevabı çözülemedi")

            val decoded = Ioniq5BmsDecoder.decode220101(payload, BmsDecodeState()).telemetry
            val summary = buildString {
                append("V=")
                append(decoded.packVoltageV?.let { "%.1f".format(it) } ?: "—")
                append(" V, A=")
                append(decoded.packCurrentA?.let { "%.1f".format(it) } ?: "—")
                append(", SOC=")
                append(decoded.socPercent?.let { "%.1f%%".format(it) } ?: "—")
            }

            onStatus("BMS veri geliyor", id, raw.takeLast(500))
            return ProbeResult(id, summary, raw)
        } finally {
            client.close()
        }
    }

    override fun stream(): Flow<VehicleTelemetry> = flow {
        var state = BmsDecodeState()

        while (currentCoroutineContext().isActive) {
            val client = Elm327Client(context)
            try {
                onStatus("Vgate Wi‑Fi bağlanıyor…", null, null)
                client.connect()
                val id = client.initialize()
                onStatus("Vgate bağlı • BMS okunuyor", id, null)

                var cycle = 0
                while (currentCoroutineContext().isActive) {
                    val raw101 = client.query("220101")
                    val p101 = ElmIsoTpParser.extractDidPayload(raw101, 0x01, 0x01)
                    if (p101 != null) {
                        state = Ioniq5BmsDecoder.decode220101(p101, state)
                    }

                    if (cycle % 4 == 0) {
                        val raw105 = client.query("220105")
                        val p105 = ElmIsoTpParser.extractDidPayload(raw105, 0x01, 0x05)
                        if (p105 != null) {
                            state = Ioniq5BmsDecoder.decode220105(p105, state)
                        }
                    }

                    if (state.telemetry.packVoltageV != null) {
                        onStatus("BMS canlı • READ ONLY", id, raw101.takeLast(500))
                        emit(state.telemetry.copy(timestampMs = System.currentTimeMillis()))
                    } else {
                        onStatus("BMS cevabı var, byte haritası doğrulanamadı", id, raw101.takeLast(500))
                    }

                    cycle++
                    delay(180)
                }
            } catch (t: Throwable) {
                onStatus(
                    "OBD bağlantı hatası: ${t.message ?: t::class.java.simpleName}",
                    null,
                    null
                )
                delay(2000)
            } finally {
                client.close()
            }
        }
    }
}
