package com.punegork.ioniqtelemetry.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class TelemetryBus {
    private val _state = MutableStateFlow(LiveTelemetry())
    val state: StateFlow<LiveTelemetry> = _state

    fun vehicle(value: VehicleTelemetry, sourceName: String) {
        _state.update { it.copy(vehicle = value, sourceName = sourceName) }
    }

    fun location(value: LocationTelemetry) {
        _state.update { it.copy(location = value) }
    }

    fun recording(recording: Boolean, tripId: Long?) {
        _state.update { it.copy(recording = recording, activeTripId = tripId) }
    }
}
