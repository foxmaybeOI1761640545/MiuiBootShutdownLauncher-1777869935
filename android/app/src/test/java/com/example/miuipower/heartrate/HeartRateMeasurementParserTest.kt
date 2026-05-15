package com.example.miuipower.heartrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateMeasurementParserTest {
    @Test
    fun parsesUint8HeartRate() {
        val result = HeartRateMeasurementParser.parse(byteArrayOf(0x00, 0x64))

        assertEquals(100, result?.bpm)
        assertEquals("uint8", result?.bpmFormat)
        assertEquals("0064", result?.rawHex)
    }

    @Test
    fun parsesUint16HeartRateAndSensorContact() {
        val result = HeartRateMeasurementParser.parse(byteArrayOf(0x07, 0x2C, 0x01))

        assertEquals(300, result?.bpm)
        assertEquals("uint16", result?.bpmFormat)
        assertEquals(true, result?.sensorContactSupported)
        assertEquals(true, result?.sensorContactDetected)
    }

    @Test
    fun parsesEnergyAndRrIntervals() {
        val result = HeartRateMeasurementParser.parse(
            byteArrayOf(0x18, 0x4B, 0x10, 0x00, 0x00, 0x04, 0x00, 0x02),
        )

        assertEquals(75, result?.bpm)
        assertEquals(16, result?.energyExpended)
        assertEquals(listOf(1000, 500), result?.rrIntervalsMs)
    }

    @Test
    fun rejectsMalformedEnergyPacket() {
        val result = HeartRateMeasurementParser.parse(byteArrayOf(0x08, 0x4B, 0x10))

        assertNull(result)
    }

    @Test
    fun reconnectPolicyCapsAtTenSeconds() {
        assertEquals(1_000L, HeartRateReconnectPolicy.delayForAttempt(0))
        assertEquals(3_000L, HeartRateReconnectPolicy.delayForAttempt(1))
        assertEquals(5_000L, HeartRateReconnectPolicy.delayForAttempt(2))
        assertEquals(10_000L, HeartRateReconnectPolicy.delayForAttempt(3))
        assertEquals(10_000L, HeartRateReconnectPolicy.delayForAttempt(12))
    }
}
