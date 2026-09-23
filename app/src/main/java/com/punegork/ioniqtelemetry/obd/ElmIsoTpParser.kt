package com.punegork.ioniqtelemetry.obd

object ElmIsoTpParser {
    fun extractDidPayload(raw: String, didHi: Int, didLo: Int): ByteArray? {
        val frames = raw
            .replace(">", "\n")
            .lineSequence()
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .filterNot {
                it.startsWith("SEARCHING") ||
                    it == "NO DATA" ||
                    it == "STOPPED" ||
                    it.startsWith("AT") ||
                    it.startsWith("22")
            }
            .mapNotNull(::parseFrame)
            .toList()

        if (frames.isEmpty()) return null

        val firstIso = frames.indexOfFirst {
            it.isNotEmpty() && ((it[0].toInt() and 0xF0) == 0x10)
        }

        val payload = if (firstIso >= 0) {
            val first = frames[firstIso]
            if (first.size < 3) return null
            val totalLength = ((first[0].toInt() and 0x0F) shl 8) or
                (first[1].toInt() and 0xFF)
            val data = ArrayList<Byte>(totalLength)
            data.addAll(first.drop(2))

            for (i in firstIso + 1 until frames.size) {
                val frame = frames[i]
                if (frame.isEmpty()) continue
                val type = frame[0].toInt() and 0xF0
                if (type == 0x20) data.addAll(frame.drop(1))
                if (data.size >= totalLength) break
            }
            data.take(totalLength).toByteArray()
        } else {
            val direct = frames.flatMap { frame ->
                when {
                    frame.isEmpty() -> emptyList()
                    (frame[0].toInt() and 0xF0) == 0x00 -> {
                        val len = frame[0].toInt() and 0x0F
                        frame.drop(1).take(len)
                    }
                    else -> frame.toList()
                }
            }
            direct.toByteArray()
        }

        val marker = byteArrayOf(0x62, didHi.toByte(), didLo.toByte())
        val start = indexOf(payload, marker)
        if (start < 0) return null
        return payload.copyOfRange(start + marker.size, payload.size)
    }

    private fun parseFrame(line: String): ByteArray? {
        val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }

        val hexBytes: List<String> = if (tokens.size > 1) {
            val body = if (tokens.first().matches(Regex("[0-9A-F]{3,8}"))) {
                tokens.drop(1)
            } else {
                tokens
            }
            body.filter { it.matches(Regex("[0-9A-F]{2}")) }
        } else {
            val compact = line.replace(" ", "")
            if (!compact.matches(Regex("[0-9A-F]+"))) return null
            val body = when {
                compact.length >= 5 && compact.substring(0, 3).matches(Regex("[0-9A-F]{3}")) &&
                    (compact.length - 3) % 2 == 0 -> compact.substring(3)
                compact.length % 2 == 0 -> compact
                else -> return null
            }
            body.chunked(2)
        }

        if (hexBytes.isEmpty()) return null
        return hexBytes.map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun indexOf(data: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..data.size - needle.size) {
            for (j in needle.indices) {
                if (data[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
