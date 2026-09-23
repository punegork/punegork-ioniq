package com.punegork.ioniqtelemetry.obd

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class Elm327Client(
    private val context: Context,
    private val host: String = "192.168.0.10",
    private val port: Int = 35000
) {
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: BufferedOutputStream? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        close()

        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val wifiNetwork = connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }

        val s = wifiNetwork?.socketFactory?.createSocket() ?: Socket()
        s.tcpNoDelay = true
        s.soTimeout = 3500
        s.connect(InetSocketAddress(host, port), 3500)

        socket = s
        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
    }

    suspend fun initialize(): String {
        val reset = command("ATZ", timeoutMs = 5000)
        delay(800)

        val init = listOf(
            "ATE0",
            "ATL0",
            "ATS1",
            "ATH1",
            "ATSP6",
            "ATAL",
            "ATCFC1",
            "ATCAF1",
            "ATAT1",
            "ATSTC8",
            "ATSH7E4"
        )
        init.forEach { command(it) }

        val id = command("ATI")
            .replace(">", "")
            .trim()
            .ifBlank { reset.replace(">", "").trim() }

        return id
    }

    suspend fun query(command: String): String = command(command, timeoutMs = 4000)

    private suspend fun command(command: String, timeoutMs: Int = 3000): String =
        withContext(Dispatchers.IO) {
            val s = socket ?: error("Vgate socket bağlı değil")
            s.soTimeout = timeoutMs

            val out = output ?: error("Vgate output yok")
            val bytes = (command.trim() + "\r").toByteArray(StandardCharsets.US_ASCII)
            out.write(bytes)
            out.flush()

            readUntilPrompt()
        }

    private fun readUntilPrompt(): String {
        val stream = input ?: error("Vgate input yok")
        val buffer = StringBuilder()
        while (true) {
            val b = stream.read()
            if (b < 0) break
            val c = b.toChar()
            buffer.append(c)
            if (c == '>') break
        }
        return buffer.toString()
    }

    fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
    }
}
