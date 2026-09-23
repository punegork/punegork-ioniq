package com.punegork.ioniqtelemetry.telemetry

import com.punegork.ioniqtelemetry.data.TripEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TripAccumulator(startTimeMs: Long) {
    private val startedAt = startTimeMs
    private var previousVehicle: VehicleTelemetry? = null

    var energyDrawnKWh = 0.0
        private set
    var energyRegeneratedKWh = 0.0
        private set

    var maxDischargeCurrentA = 0.0
        private set
    var maxRegenCurrentA = 0.0
        private set
    var maxDischargePowerKw = 0.0
        private set
    var maxRegenPowerKw = 0.0
        private set
    var maxAccelerationG = 0.0
        private set
    var maxBrakingG = 0.0
        private set
    var maxLateralAbsG = 0.0
        private set

    var batteryMinTempC: Double? = null
        private set
    var batteryMaxTempC: Double? = null
        private set
    var rearMotorMaxTempC: Double? = null
        private set
    var frontMotorMaxTempC: Double? = null
        private set
    var inverterMaxTempC: Double? = null
        private set
    var maxCellDeltaMv: Double? = null
        private set

    fun addVehicle(v: VehicleTelemetry) {
        integrateEnergy(previousVehicle, v)

        v.packCurrentA?.let {
            if (it >= 0.0) maxDischargeCurrentA = max(maxDischargeCurrentA, it)
            else maxRegenCurrentA = max(maxRegenCurrentA, -it)
        }
        v.packPowerKw?.let {
            if (it >= 0.0) maxDischargePowerKw = max(maxDischargePowerKw, it)
            else maxRegenPowerKw = max(maxRegenPowerKw, -it)
        }
        v.longitudinalG?.let {
            if (it >= 0.0) maxAccelerationG = max(maxAccelerationG, it)
            else maxBrakingG = max(maxBrakingG, -it)
        }
        v.lateralG?.let { maxLateralAbsG = max(maxLateralAbsG, abs(it)) }

        v.batteryMinTempC?.let { batteryMinTempC = batteryMinTempC?.let { old -> min(old, it) } ?: it }
        v.batteryMaxTempC?.let { batteryMaxTempC = batteryMaxTempC?.let { old -> max(old, it) } ?: it }
        v.rearMotorTempC?.let { rearMotorMaxTempC = rearMotorMaxTempC?.let { old -> max(old, it) } ?: it }
        v.frontMotorTempC?.let { frontMotorMaxTempC = frontMotorMaxTempC?.let { old -> max(old, it) } ?: it }
        v.inverterTempC?.let { inverterMaxTempC = inverterMaxTempC?.let { old -> max(old, it) } ?: it }
        v.cellDeltaMv?.let { maxCellDeltaMv = maxCellDeltaMv?.let { old -> max(old, it) } ?: it }

        previousVehicle = v
    }

    private fun integrateEnergy(previous: VehicleTelemetry?, current: VehicleTelemetry) {
        previous ?: return
        val p1 = previous.packPowerKw ?: return
        val p2 = current.packPowerKw ?: return
        val dtHours = (current.timestampMs - previous.timestampMs) / 3_600_000.0
        if (dtHours <= 0.0 || dtHours > 0.05) return

        val energyKWh = ((p1 + p2) / 2.0) * dtHours
        if (energyKWh >= 0.0) energyDrawnKWh += energyKWh
        else energyRegeneratedKWh += -energyKWh
    }

    fun applyTo(
        trip: TripEntity,
        endTimeMs: Long,
        endSoc: Double?,
        distanceKm: Double,
        movingDurationSeconds: Long,
        maxSpeedKph: Double,
        endLatitude: Double?,
        endLongitude: Double?
    ): TripEntity {
        return trip.copy(
            endTimeMs = endTimeMs,
            endLatitude = endLatitude,
            endLongitude = endLongitude,
            distanceKm = distanceKm,
            movingDurationSeconds = movingDurationSeconds,
            endSocPercent = endSoc,
            energyDrawnKWh = energyDrawnKWh,
            energyRegeneratedKWh = energyRegeneratedKWh,
            netEnergyKWh = energyDrawnKWh - energyRegeneratedKWh,
            maxSpeedKph = maxSpeedKph,
            maxDischargeCurrentA = maxDischargeCurrentA,
            maxRegenCurrentA = maxRegenCurrentA,
            maxDischargePowerKw = maxDischargePowerKw,
            maxRegenPowerKw = maxRegenPowerKw,
            maxAccelerationG = maxAccelerationG,
            maxBrakingG = maxBrakingG,
            maxLateralAbsG = maxLateralAbsG,
            batteryMinTempC = batteryMinTempC,
            batteryMaxTempC = batteryMaxTempC,
            rearMotorMaxTempC = rearMotorMaxTempC,
            frontMotorMaxTempC = frontMotorMaxTempC,
            inverterMaxTempC = inverterMaxTempC,
            maxCellDeltaMv = maxCellDeltaMv
        )
    }
}
