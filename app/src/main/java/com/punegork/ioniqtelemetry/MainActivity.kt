package com.punegork.ioniqtelemetry

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.punegork.ioniqtelemetry.data.TripEntity
import com.punegork.ioniqtelemetry.service.TripRecordingService
import com.punegork.ioniqtelemetry.telemetry.LiveTelemetry
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as IoniqTelemetryApp

        setContent {
            MaterialTheme {
                val live by app.telemetryBus.state.collectAsState()
                val trips by app.database.tripDao().observeAll().collectAsState(initial = emptyList())
                var tab by remember { mutableIntStateOf(0) }

                val permissionsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { granted ->
                    if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    ) {
                        startTripService()
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Text("◉") },
                                label = { Text("Canlı") }
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Text("↺") },
                                label = { Text("Sürüşler") }
                            )
                        }
                    }
                ) { padding ->
                    when (tab) {
                        0 -> Dashboard(
                            modifier = Modifier.padding(padding),
                            live = live,
                            onStart = {
                                if (hasLocationPermission()) startTripService()
                                else {
                                    val permissions = buildList {
                                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                                    }.toTypedArray()
                                    permissionsLauncher.launch(permissions)
                                }
                            },
                            onStop = { stopTripService() }
                        )
                        else -> TripHistory(
                            modifier = Modifier.padding(padding),
                            trips = trips,
                            onOpenRoute = { openRoute(this, it) }
                        )
                    }
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startTripService() {
        val intent = Intent(this, TripRecordingService::class.java).apply {
            action = TripRecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopTripService() {
        startService(Intent(this, TripRecordingService::class.java).apply {
            action = TripRecordingService.ACTION_STOP
        })
    }
}

@Composable
private fun Dashboard(
    modifier: Modifier,
    live: LiveTelemetry,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val v = live.vehicle
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("IONIQ 5 Telemetry", style = MaterialTheme.typography.headlineMedium)
            Text("Kaynak: ${live.sourceName} — DEMO OBD değerleri sentetiktir.")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = !live.recording) { Text("Sürüşü başlat") }
                Button(onClick = onStop, enabled = live.recording) { Text("Sürüşü bitir") }
            }
        }
        item { MetricCard("Hız", "${fmt(live.location?.speedKph)} km/h") }
        item { MetricCard("HV güç", "${fmt(v.packPowerKw)} kW") }
        item { MetricCard("HV akım", "${fmt(v.packCurrentA)} A") }
        item { MetricCard("HV voltaj", "${fmt(v.packVoltageV)} V") }
        item { MetricCard("SOC / SOH", "${fmt(v.socPercent)}% / ${fmt(v.sohPercent)}%") }
        item { MetricCard("Batarya", "${fmt(v.batteryMinTempC)} / ${fmt(v.batteryAvgTempC)} / ${fmt(v.batteryMaxTempC)} °C  min/avg/max") }
        item { MetricCard("Arka motor", "${fmt(v.rearMotorTempC)} °C • ${fmt(v.rearMotorRpm, 0)} rpm") }
        item { MetricCard("İnverter", "${fmt(v.inverterTempC)} °C") }
        item { MetricCard("Hücre Δ", "${fmt(v.cellDeltaMv)} mV") }
        item { MetricCard("12 V", "${fmt(v.auxVoltageV)} V") }
        item { MetricCard("G", "Long ${fmt(v.longitudinalG, 3)} g • Lat ${fmt(v.lateralG, 3)} g") }
        item {
            val l = live.location
            MetricCard(
                "GPS",
                if (l == null) "Konum bekleniyor"
                else "${"%.6f".format(Locale.US, l.latitude)}, ${"%.6f".format(Locale.US, l.longitude)} • ±${l.accuracyM.toInt()} m"
            )
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun TripHistory(
    modifier: Modifier,
    trips: List<TripEntity>,
    onOpenRoute: (TripEntity) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("Sürüş geçmişi", style = MaterialTheme.typography.headlineMedium) }
        items(trips, key = { it.id }) { trip ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(DateFormat.getDateTimeInstance().format(Date(trip.startTimeMs)))
                    Text("${"%.1f".format(trip.distanceKm)} km • ${formatDuration(trip.durationSeconds)}")
                    Text("Net ${"%.2f".format(trip.netEnergyKWh)} kWh • ${trip.consumptionKwh100Km?.let { "%.1f kWh/100 km".format(it) } ?: "—"}")
                    Text("Max ${"%.0f".format(trip.maxSpeedKph)} km/h • ${"%.1f".format(trip.maxDischargePowerKw)} kW • ${"%.0f".format(trip.maxDischargeCurrentA)} A")
                    Text("Batarya ${trip.batteryMinTempC?.let { "%.1f".format(it) } ?: "—"}…${trip.batteryMaxTempC?.let { "%.1f".format(it) } ?: "—"} °C")
                    Button(
                        onClick = { onOpenRoute(trip) },
                        enabled = trip.startLatitude != null && trip.endLatitude != null
                    ) { Text("A → B Google Maps") }
                }
            }
        }
    }
}

private fun fmt(value: Double?, decimals: Int = 1): String =
    value?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: "—"

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}s ${m}dk" else "${m}dk"
}

private fun openRoute(context: Context, trip: TripEntity) {
    val slat = trip.startLatitude ?: return
    val slon = trip.startLongitude ?: return
    val elat = trip.endLatitude ?: return
    val elon = trip.endLongitude ?: return

    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1" +
            "&origin=$slat,$slon" +
            "&destination=$elat,$elon"
    )
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
