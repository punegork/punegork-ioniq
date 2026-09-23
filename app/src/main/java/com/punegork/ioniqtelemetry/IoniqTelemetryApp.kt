package com.punegork.ioniqtelemetry

import android.app.Application
import androidx.room.Room
import com.punegork.ioniqtelemetry.data.AppDatabase
import com.punegork.ioniqtelemetry.location.AndroidLocationTracker
import com.punegork.ioniqtelemetry.obd.ObdController
import com.punegork.ioniqtelemetry.telemetry.TelemetryBus

class IoniqTelemetryApp : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "ioniq_telemetry.db"
        ).build()
    }

    val telemetryBus by lazy { TelemetryBus() }
    val locationTracker by lazy { AndroidLocationTracker(applicationContext) }
    val obdController by lazy { ObdController(applicationContext) }
}
