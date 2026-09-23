package com.punegork.ioniqtelemetry.telemetry

import kotlinx.coroutines.flow.Flow

interface TelemetrySource {
    val sourceName: String
    fun stream(): Flow<VehicleTelemetry>
}
