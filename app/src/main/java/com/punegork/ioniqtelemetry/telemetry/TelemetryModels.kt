package com.punegork.ioniqtelemetry.telemetry

data class VehicleTelemetry(
    val timestampMs: Long = System.currentTimeMillis(),
    val packVoltageV: Double? = null,
    val packCurrentA: Double? = null,
    val packPowerKw: Double? = null,
    val socPercent: Double? = null,
    val sohPercent: Double? = null,
    val availableDischargePowerKw: Double? = null,
    val availableRegenPowerKw: Double? = null,
    val batteryMinTempC: Double? = null,
    val batteryAvgTempC: Double? = null,
    val batteryMaxTempC: Double? = null,
    val batteryHeaterTempC: Double? = null,
    val rearMotorTempC: Double? = null,
    val frontMotorTempC: Double? = null,
    val inverterTempC: Double? = null,
    val rearMotorRpm: Double? = null,
    val frontMotorRpm: Double? = null,
    val minCellVoltageV: Double? = null,
    val maxCellVoltageV: Double? = null,
    val auxVoltageV: Double? = null,
    val vehicleSpeedKph: Double? = null,
    val longitudinalG: Double? = null,
    val lateralG: Double? = null,
    val yawRateDegS: Double? = null,
    val steeringAngleDeg: Double? = null,
    val accActive: Boolean? = null,
    val lkaActive: Boolean? = null,
    val leadDistanceM: Double? = null
) {
    val cellDeltaMv: Double?
        get() {
            val min = minCellVoltageV ?: return null
            val max = maxCellVoltageV ?: return null
            return (max - min) * 1000.0
        }
}

data class LocationTelemetry(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val accuracyM: Float,
    val speedKph: Double?
)

data class LiveTelemetry(
    val vehicle: VehicleTelemetry = VehicleTelemetry(),
    val location: LocationTelemetry? = null,
    val recording: Boolean = false,
    val activeTripId: Long? = null,
    val sourceName: String = "DEMO OBD",
    val tripDistanceKm: Double = 0.0,
    val tripNetEnergyKWh: Double = 0.0,
    val tripConsumptionKwh100Km: Double? = null
)
