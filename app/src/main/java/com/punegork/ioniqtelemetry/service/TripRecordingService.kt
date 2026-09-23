package com.punegork.ioniqtelemetry.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.punegork.ioniqtelemetry.IoniqTelemetryApp
import com.punegork.ioniqtelemetry.data.TelemetrySampleEntity
import com.punegork.ioniqtelemetry.data.TripEntity
import com.punegork.ioniqtelemetry.telemetry.LocationTelemetry
import com.punegork.ioniqtelemetry.telemetry.TripAccumulator
import com.punegork.ioniqtelemetry.telemetry.VehicleTelemetry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

class TripRecordingService : Service() {
    companion object {
        const val ACTION_START = "com.punegork.ioniqtelemetry.START"
        const val ACTION_STOP = "com.punegork.ioniqtelemetry.STOP"
        private const val CHANNEL_ID = "trip_recording"
        private const val NOTIFICATION_ID = 51
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("IoniqTelemetry", "Trip service coroutine failed", throwable)
        app.telemetryBus.recording(false, null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private var recordingJob: Job? = null

    private lateinit var app: IoniqTelemetryApp
    private var trip: TripEntity? = null
    private var accumulator: TripAccumulator? = null
    private var latestVehicle = VehicleTelemetry()
    private var latestLocation: LocationTelemetry? = null
    private var lastDistanceLocation: LocationTelemetry? = null
    private var distanceKm = 0.0
    private var movingDurationSeconds = 0L
    private var maxSpeedKph = 0.0

    override fun onCreate() {
        super.onCreate()
        app = application as IoniqTelemetryApp
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (recordingJob == null) startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        distanceKm = 0.0
        movingDurationSeconds = 0L
        maxSpeedKph = 0.0
        latestVehicle = VehicleTelemetry()
        latestLocation = null
        lastDistanceLocation = null
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Ioniq Telemetry")
                .setContentText("Sürüş kaydı aktif")
                .setOngoing(true)
                .build(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        )

        recordingJob = scope.launch {
            val now = System.currentTimeMillis()
            val newTrip = TripEntity(startTimeMs = now)
            val id = app.database.tripDao().insert(newTrip)
            trip = newTrip.copy(id = id)
            accumulator = TripAccumulator(now)
            app.telemetryBus.recording(true, id)
            app.telemetryBus.tripStats(0.0, 0.0)

            launch {
                val obdSource = app.obdController.source()
                obdSource.stream().collect { vehicle ->
                    latestVehicle = vehicle
                    accumulator?.addVehicle(vehicle)
                    app.telemetryBus.vehicle(vehicle, obdSource.sourceName)
                    publishTripStats()
                }
            }

            launch {
                app.locationTracker.stream().collectLatest { location ->
                    app.telemetryBus.location(location)
                    updateLocationStats(location)
                    publishTripStats()
                    persistLocationSample(id, location)
                }
            }
        }
    }

    private fun updateLocationStats(location: LocationTelemetry) {
        val previous = lastDistanceLocation
        if (previous != null && location.accuracyM <= 30f && previous.accuracyM <= 30f) {
            val meters = FloatArray(1)
            Location.distanceBetween(
                previous.latitude, previous.longitude,
                location.latitude, location.longitude,
                meters
            )
            if (meters[0] in 0f..500f) distanceKm += meters[0] / 1000.0

            val dtSec = ((location.timestampMs - previous.timestampMs) / 1000L).coerceIn(0L, 10L)
            if ((location.speedKph ?: 0.0) > 3.0) movingDurationSeconds += dtSec
        }

        val gpsSpeed = location.speedKph ?: 0.0
        val vehicleSpeed = latestVehicle.vehicleSpeedKph ?: 0.0
        maxSpeedKph = max(maxSpeedKph, max(gpsSpeed, vehicleSpeed))

        val current = trip
        if (current != null && current.startLatitude == null) {
            trip = current.copy(
                startLatitude = location.latitude,
                startLongitude = location.longitude,
                startSocPercent = latestVehicle.socPercent
            )
        }

        latestLocation = location
        lastDistanceLocation = location
    }

    private fun publishTripStats() {
        val acc = accumulator ?: return
        val netEnergyKWh = acc.energyDrawnKWh - acc.energyRegeneratedKWh
        app.telemetryBus.tripStats(distanceKm, netEnergyKWh)
    }

    private suspend fun persistLocationSample(tripId: Long, location: LocationTelemetry) {
        val v = latestVehicle
        app.database.sampleDao().insert(
            TelemetrySampleEntity(
                tripId = tripId,
                timestampMs = location.timestampMs,
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeM = location.altitudeM,
                gpsAccuracyM = location.accuracyM,
                gpsSpeedKph = location.speedKph,
                vehicleSpeedKph = v.vehicleSpeedKph,
                packVoltageV = v.packVoltageV,
                packCurrentA = v.packCurrentA,
                packPowerKw = v.packPowerKw,
                socPercent = v.socPercent,
                batteryMinTempC = v.batteryMinTempC,
                batteryAvgTempC = v.batteryAvgTempC,
                batteryMaxTempC = v.batteryMaxTempC,
                rearMotorTempC = v.rearMotorTempC,
                frontMotorTempC = v.frontMotorTempC,
                inverterTempC = v.inverterTempC,
                minCellVoltageV = v.minCellVoltageV,
                maxCellVoltageV = v.maxCellVoltageV,
                auxVoltageV = v.auxVoltageV,
                longitudinalG = v.longitudinalG,
                lateralG = v.lateralG,
                yawRateDegS = v.yawRateDegS,
                steeringAngleDeg = v.steeringAngleDeg
            )
        )
    }

    private fun stopRecording() {
        scope.launch {
            val current = trip
            val acc = accumulator
            if (current != null && acc != null) {
                val last = latestLocation
                val finalTrip = acc.applyTo(
                    trip = current,
                    endTimeMs = System.currentTimeMillis(),
                    endSoc = latestVehicle.socPercent,
                    distanceKm = distanceKm,
                    movingDurationSeconds = movingDurationSeconds,
                    maxSpeedKph = maxSpeedKph,
                    endLatitude = last?.latitude,
                    endLongitude = last?.longitude
                )
                app.database.tripDao().update(finalTrip)
            }
            app.telemetryBus.recording(false, null)
            recordingJob?.cancel()
            recordingJob = null
            trip = null
            accumulator = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Sürüş kaydı",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
