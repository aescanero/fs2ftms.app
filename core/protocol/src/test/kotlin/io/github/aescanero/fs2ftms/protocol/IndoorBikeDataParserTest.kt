package io.github.aescanero.fs2ftms.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndoorBikeDataParserTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `lee el pulso que envía la bicicleta`() {
        // Flags 0x0204: cadencia (bit 2) y pulso (bit 9). Velocidad 10,00 km/h, cadencia 80, pulso 120.
        val parsed = IndoorBikeDataParser().accept(bytes(0x04, 0x02, 0xE8, 0x03, 0xA0, 0x00, 0x78))!!
        assertEquals(10.0, parsed.sample.speedKmh, 0.001)
        assertEquals(80, parsed.sample.cadencePerMinute)
        assertEquals(120, parsed.sample.heartRate)
        assertTrue(Metric.HEART_RATE in parsed.present)
    }

    @Test
    fun `pulso cero se trata como sin lectura`() {
        val parsed = IndoorBikeDataParser().accept(bytes(0x00, 0x02, 0xE8, 0x03, 0x00))!!
        assertNull(parsed.sample.heartRate)
        assertFalse(Metric.HEART_RATE in parsed.present)
    }
}
