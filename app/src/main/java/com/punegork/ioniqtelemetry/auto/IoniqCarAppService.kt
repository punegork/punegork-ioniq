package com.punegork.ioniqtelemetry.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import com.punegork.ioniqtelemetry.IoniqTelemetryApp
import com.punegork.ioniqtelemetry.telemetry.LiveTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class IoniqCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session = IoniqCarSession()
}

private class IoniqCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = TelemetryDashboardScreen(carContext)
}

private abstract class LiveTelemetryScreen(carContext: CarContext) : Screen(carContext) {
    protected val app: IoniqTelemetryApp
        get() = carContext.applicationContext as IoniqTelemetryApp

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastInvalidateMs = 0L

    init {
        scope.launch {
            app.telemetryBus.state.collectLatest {
                val now = SystemClock.elapsedRealtime()
                if (now - lastInvalidateMs >= 750L) {
                    lastInvalidateMs = now
                    invalidate()
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    protected fun snapshot(): LiveTelemetry = app.telemetryBus.state.value

    protected fun f(v: Double?, digits: Int = 1): String =
        v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: "—"
}

private class TelemetryDashboardScreen(carContext: CarContext) : LiveTelemetryScreen(carContext) {
    override fun onGetTemplate(): Template {
        val live = snapshot()
        val v = live.vehicle
        val speed = live.location?.speedKph ?: v.vehicleSpeedKph

        val list = ItemList.Builder()
            .addItem(metric("HIZ", "${f(speed, 0)} km/h"))
            .addItem(metric("GÜÇ", signed(v.packPowerKw, "kW")))
            .addItem(metric("HV AKIM", signed(v.packCurrentA, "A")))
            .addItem(metric("SOC", "${f(v.socPercent, 1)} %"))
            .addItem(
                metric(
                    "BATARYA",
                    "${f(v.batteryMinTempC, 0)} / ${f(v.batteryAvgTempC, 0)} / ${f(v.batteryMaxTempC, 0)} °C",
                    onClick = { screenManager.push(TelemetryDetailsScreen(carContext)) }
                )
            )
            .addItem(
                metric(
                    "MOTOR / INV",
                    "${f(v.rearMotorTempC, 0)}° / ${f(v.inverterTempC, 0)}°",
                    onClick = { screenManager.push(TelemetryDetailsScreen(carContext)) }
                )
            )
            .build()

        return GridTemplate.Builder()
            .setTitle(if (live.recording) "IONIQ TELEMETRY • KAYIT" else "IONIQ TELEMETRY")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(list)
            .build()
    }

    private fun metric(title: String, value: String, onClick: (() -> Unit)? = null): GridItem {
        val b = GridItem.Builder().setTitle(title).setText(value)
        if (onClick != null) b.setOnClickListener { onClick() }
        return b.build()
    }

    private fun signed(v: Double?, unit: String): String =
        v?.let { String.format(Locale.US, "%+.1f %s", it, unit) } ?: "— $unit"
}

private class TelemetryDetailsScreen(carContext: CarContext) : LiveTelemetryScreen(carContext) {
    override fun onGetTemplate(): Template {
        val live = snapshot()
        val v = live.vehicle

        val rows = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("G kuvveti")
                    .addText("Long ${signed(v.longitudinalG, "g", 3)} • Lat ${signed(v.lateralG, "g", 3)}")
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Motorlar")
                    .addText("Arka ${f(v.rearMotorTempC)} °C • ${f(v.rearMotorRpm, 0)} rpm")
                    .addText("Ön ${f(v.frontMotorTempC)} °C • ${f(v.frontMotorRpm, 0)} rpm")
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("HV batarya")
                    .addText("${f(v.packVoltageV)} V • ${signed(v.packCurrentA, "A", 1)} • ${signed(v.packPowerKw, "kW", 1)}")
                    .addText("SOC ${f(v.socPercent)}% • SOH ${f(v.sohPercent)}%")
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Batarya sıcaklığı")
                    .addText("Min ${f(v.batteryMinTempC)}° • Avg ${f(v.batteryAvgTempC)}° • Max ${f(v.batteryMaxTempC)}°")
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Hücre / 12 V")
                    .addText("${f(v.minCellVoltageV, 3)}–${f(v.maxCellVoltageV, 3)} V • Δ ${f(v.cellDeltaMv, 0)} mV")
                    .addText("12 V: ${f(v.auxVoltageV, 2)} V")
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("ADAS")
                    .addText("ACC ${state(v.accActive)} • LKA ${state(v.lkaActive)} • Lead ${v.leadDistanceM?.let { "${f(it, 0)} m" } ?: "—"}")
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("Telemetri detay")
            .setHeaderAction(Action.BACK)
            .setSingleList(rows)
            .build()
    }

    private fun signed(v: Double?, unit: String, digits: Int): String =
        v?.let { String.format(Locale.US, "%+.${digits}f %s", it, unit) } ?: "— $unit"

    private fun state(v: Boolean?): String = when (v) {
        true -> "Açık"
        false -> "Kapalı"
        null -> "—"
    }
}
