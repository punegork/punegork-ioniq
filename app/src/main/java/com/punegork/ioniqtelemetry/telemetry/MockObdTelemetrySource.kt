package com.punegork.ioniqtelemetry.telemetry

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin

class MockObdTelemetrySource : TelemetrySource {
    override val sourceName = "DEMO OBD"

    override fun stream(): Flow<VehicleTelemetry> = flow {
        var t = 0.0
        while (true) {
            val current = 35.0 + 95.0 * sin(t / 7.0)
            val voltage = 694.0 - 4.0 * sin(t / 13.0)
            val power = voltage * current / 1000.0
            val battery = 27.0 + 5.0 * (0.5 + 0.5 * sin(t / 40.0))

            emit(
                VehicleTelemetry(
                    packVoltageV = voltage,
                    packCurrentA = current,
                    packPowerKw = power,
                    socPercent = 73.0,
                    sohPercent = 100.0,
                    availableDischargePowerKw = 170.0,
                    availableRegenPowerKw = 95.0,
                    batteryMinTempC = battery - 1.0,
                    batteryAvgTempC = battery,
                    batteryMaxTempC = battery + 1.5,
                    batteryHeaterTempC = battery - 2.0,
                    rearMotorTempC = 39.0 + 7.0 * (0.5 + 0.5 * sin(t / 22.0)),
                    inverterTempC = 37.0 + 6.0 * (0.5 + 0.5 * sin(t / 18.0)),
                    rearMotorRpm = 2200.0 + 900.0 * sin(t / 5.0),
                    minCellVoltageV = 3.742,
                    maxCellVoltageV = 3.757,
                    auxVoltageV = 14.42,
                    longitudinalG = 0.08 * sin(t / 2.4),
                    lateralG = 0.12 * sin(t / 1.7)
                )
            )
            t += 0.2
            delay(200)
        }
    }
}
