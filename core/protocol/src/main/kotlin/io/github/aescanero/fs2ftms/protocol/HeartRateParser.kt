package io.github.aescanero.fs2ftms.protocol

/** Heart Rate Measurement (0x2A37): bit 0 de los flags indica si el valor es de 8 o 16 bits. */
object HeartRateParser {
    fun parse(value: ByteArray): Int? {
        if (value.size < 2) return null
        val wide = (value[0].toInt() and 0x01) != 0
        val bpm = if (wide) {
            if (value.size < 3) return null
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else {
            value[1].toInt() and 0xFF
        }
        return bpm.takeIf { it in 1..250 }
    }
}
