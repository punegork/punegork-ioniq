package com.punegork.ioniqtelemetry.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,

    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,

    val distanceKm: Double = 0.0,
    val movingDurationSeconds: Long = 0,

    val startSocPercent: Double? = null,
    val endSocPercent: Double? = null,

    val energyDrawnKWh: Double = 0.0,
    val energyRegeneratedKWh: Double = 0.0,
    val netEnergyKWh: Double = 0.0,

    val maxSpeedKph: Double = 0.0,
    val maxDischargeCurrentA: Double = 0.0,
    val maxRegenCurrentA: Double = 0.0,
    val maxDischargePowerKw: Double = 0.0,
    val maxRegenPowerKw: Double = 0.0,

    val maxAccelerationG: Double = 0.0,
    val maxBrakingG: Double = 0.0,
    val maxLateralAbsG: Double = 0.0,

    val batteryMinTempC: Double? = null,
    val batteryMaxTempC: Double? = null,
    val rearMotorMaxTempC: Double? = null,
    val frontMotorMaxTempC: Double? = null,
    val inverterMaxTempC: Double? = null,
    val maxCellDeltaMv: Double? = null
) {
    val durationSeconds: Long
        get() = ((endTimeMs ?: System.currentTimeMillis()) - startTimeMs).coerceAtLeast(0L) / 1000L

    val consumptionKwh100Km: Double?
        get() = if (distanceKm > 0.1) netEnergyKWh / distanceKm * 100.0 else null
}

@Entity(
    tableName = "telemetry_samples",
    indices = [Index("tripId"), Index(value = ["tripId", "timestampMs"])]
)
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val timestampMs: Long,

    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val gpsAccuracyM: Float,
    val gpsSpeedKph: Double?,

    val vehicleSpeedKph: Double?,
    val packVoltageV: Double?,
    val packCurrentA: Double?,
    val packPowerKw: Double?,
    val socPercent: Double?,

    val batteryMinTempC: Double?,
    val batteryAvgTempC: Double?,
    val batteryMaxTempC: Double?,
    val rearMotorTempC: Double?,
    val frontMotorTempC: Double?,
    val inverterTempC: Double?,

    val minCellVoltageV: Double?,
    val maxCellVoltageV: Double?,
    val auxVoltageV: Double?,

    val longitudinalG: Double?,
    val lateralG: Double?,
    val yawRateDegS: Double?,
    val steeringAngleDeg: Double?
)
