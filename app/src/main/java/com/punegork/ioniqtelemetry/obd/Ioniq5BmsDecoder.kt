package com.punegork.ioniqtelemetry.obd

import com.punegork.ioniqtelemetry.telemetry.VehicleTelemetry

data class BmsDecodeState(
    val telemetry: VehicleTelemetry = VehicleTelemetry(),
    val temps01to05: List<Double> = emptyList(),
    val temps06to16: List<Double> = emptyList()
)

object Ioniq5BmsDecoder {
    fun decode220101(data: ByteArray, previous: BmsDecodeState): BmsDecodeState {
        val current = signed16FromSignedHigh(data, 10, 11)?.div(10.0)?.valid(-1000.0, 1000.0)
        val voltage = u16(data, 12)?.div(10.0)?.valid(250.0, 950.0)
        val maxTemp = s8(data, 14)?.toDouble()?.valid(-50.0, 120.0)
        val minTemp = s8(data, 15)?.toDouble()?.valid(-50.0, 120.0)

        val firstTemps = (16..20).mapNotNull { i ->
            s8(data, i)?.toDouble()?.valid(-50.0, 120.0)
        }

        val maxCell = u8(data, 23)?.div(50.0)?.valid(2.0, 5.0)
        val minCell = u8(data, 25)?.div(50.0)?.valid(2.0, 5.0)
        val aux = u8(data, 29)?.times(0.1)?.valid(8.0, 16.5)

        val rearRpm = signed16(data, 53)?.toDouble()?.valid(-20_000.0, 20_000.0)
        val frontRpm = signed16(data, 55)?.toDouble()?.valid(-20_000.0, 20_000.0)

        val socBms = u8(data, 4)?.div(2.0)?.valid(0.0, 100.0)
        val power = if (voltage != null && current != null) voltage * current / 1000.0 else null

        val allTemps = firstTemps + previous.temps06to16
        val avgTemp = allTemps.takeIf { it.isNotEmpty() }?.average()

        return previous.copy(
            temps01to05 = firstTemps,
            telemetry = previous.telemetry.copy(
                timestampMs = System.currentTimeMillis(),
                packVoltageV = voltage ?: previous.telemetry.packVoltageV,
                packCurrentA = current ?: previous.telemetry.packCurrentA,
                packPowerKw = power ?: previous.telemetry.packPowerKw,
                socPercent = previous.telemetry.socPercent ?: socBms,
                batteryMinTempC = minTemp ?: previous.telemetry.batteryMinTempC,
                batteryAvgTempC = avgTemp ?: previous.telemetry.batteryAvgTempC,
                batteryMaxTempC = maxTemp ?: previous.telemetry.batteryMaxTempC,
                rearMotorRpm = rearRpm ?: previous.telemetry.rearMotorRpm,
                frontMotorRpm = frontRpm ?: previous.telemetry.frontMotorRpm,
                minCellVoltageV = minCell ?: previous.telemetry.minCellVoltageV,
                maxCellVoltageV = maxCell ?: previous.telemetry.maxCellVoltageV,
                auxVoltageV = aux ?: previous.telemetry.auxVoltageV
            )
        )
    }

    fun decode220105(data: ByteArray, previous: BmsDecodeState): BmsDecodeState {
        val temps = buildList {
            for (i in 9..15) {
                s8(data, i)?.toDouble()?.valid(-50.0, 120.0)?.let(::add)
            }
            for (i in 39..42) {
                s8(data, i)?.toDouble()?.valid(-50.0, 120.0)?.let(::add)
            }
        }

        val maxRegen = u16(data, 16)?.div(100.0)?.valid(0.0, 400.0)
        val maxPower = u16(data, 18)?.div(100.0)?.valid(0.0, 400.0)
        val heater = s8(data, 23)?.toDouble()?.valid(-50.0, 120.0)
        val soh = u16(data, 25)?.div(10.0)?.valid(0.0, 110.0)
        val displaySoc = u8(data, 31)?.div(2.0)?.valid(0.0, 100.0)

        val allTemps = previous.temps01to05 + temps
        val avgTemp = allTemps.takeIf { it.isNotEmpty() }?.average()

        return previous.copy(
            temps06to16 = temps,
            telemetry = previous.telemetry.copy(
                timestampMs = System.currentTimeMillis(),
                socPercent = displaySoc ?: previous.telemetry.socPercent,
                sohPercent = soh ?: previous.telemetry.sohPercent,
                availableRegenPowerKw = maxRegen ?: previous.telemetry.availableRegenPowerKw,
                availableDischargePowerKw = maxPower ?: previous.telemetry.availableDischargePowerKw,
                batteryHeaterTempC = heater ?: previous.telemetry.batteryHeaterTempC,
                batteryAvgTempC = avgTemp ?: previous.telemetry.batteryAvgTempC
            )
        )
    }

    private fun u8(data: ByteArray, i: Int): Double? =
        data.getOrNull(i)?.toInt()?.and(0xFF)?.toDouble()

    private fun s8(data: ByteArray, i: Int): Int? =
        data.getOrNull(i)?.toInt()

    private fun u16(data: ByteArray, i: Int): Double? {
        val hi = data.getOrNull(i)?.toInt()?.and(0xFF) ?: return null
        val lo = data.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: return null
        return ((hi shl 8) or lo).toDouble()
    }

    private fun signed16(data: ByteArray, i: Int): Int? {
        val hi = data.getOrNull(i)?.toInt()?.and(0xFF) ?: return null
        val lo = data.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: return null
        val raw = (hi shl 8) or lo
        return if (raw and 0x8000 != 0) raw - 0x10000 else raw
    }

    private fun signed16FromSignedHigh(data: ByteArray, hiIndex: Int, loIndex: Int): Double? {
        val hi = data.getOrNull(hiIndex)?.toInt() ?: return null
        val lo = data.getOrNull(loIndex)?.toInt()?.and(0xFF) ?: return null
        return (hi * 256 + lo).toDouble()
    }

    private fun Double.valid(min: Double, max: Double): Double? =
        takeIf { it.isFinite() && it in min..max }
}
