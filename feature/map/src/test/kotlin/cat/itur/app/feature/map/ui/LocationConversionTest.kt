/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package cat.itur.app.feature.map.ui

import android.location.Location
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocationConversionTest {
    @Test
    fun `complete Android fix preserves values and epoch timestamp milliseconds`() {
        val fix = mockk<Location> {
            every { latitude } returns 41.1
            every { longitude } returns 2.2
            every { time } returns 1_725_000_123_456L
            every { hasAltitude() } returns true
            every { altitude } returns 123.4
            every { hasSpeed() } returns true
            every { speed } returns 5.6f
            every { hasBearing() } returns true
            every { bearing } returns 78.9f
            every { hasAccuracy() } returns true
            every { accuracy } returns 3.2f
            every { hasVerticalAccuracy() } returns true
            every { verticalAccuracyMeters } returns 4.3f
            every { hasSpeedAccuracy() } returns true
            every { speedAccuracyMetersPerSecond } returns 0.4f
            every { hasBearingAccuracy() } returns true
            every { bearingAccuracyDegrees } returns 2.1f
        }

        val result = fix.toIturLocation(Build.VERSION_CODES.O)

        assertEquals(1_725_000_123_456L, result.providerTimestampMillis)
        assertEquals(123.4, result.altitudeMeters)
        assertEquals(5.6f, result.speedMetersPerSecond)
        assertEquals(78.9f, result.bearingDegrees)
        assertEquals(3.2f, result.horizontalAccuracyMeters)
        assertEquals(4.3f, result.verticalAccuracyMeters)
        assertEquals(0.4f, result.speedAccuracyMetersPerSecond)
        assertEquals(2.1f, result.bearingAccuracyDegrees)
    }

    @Test
    fun `absent Android values remain absent rather than being invented`() {
        val fix = mockk<Location> {
            every { latitude } returns 41.1
            every { longitude } returns 2.2
            every { time } returns 0L
            every { hasAltitude() } returns false
            every { hasSpeed() } returns false
            every { hasBearing() } returns false
            every { hasAccuracy() } returns false
        }

        val result = fix.toIturLocation(Build.VERSION_CODES.N)

        assertNull(result.providerTimestampMillis)
        assertNull(result.altitudeMeters)
        assertNull(result.speedMetersPerSecond)
        assertNull(result.bearingDegrees)
        assertNull(result.horizontalAccuracyMeters)
        assertNull(result.verticalAccuracyMeters)
        assertNull(result.speedAccuracyMetersPerSecond)
        assertNull(result.bearingAccuracyDegrees)
    }
}
