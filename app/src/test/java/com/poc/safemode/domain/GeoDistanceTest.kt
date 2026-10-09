package com.poc.safemode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoDistanceTest {

    @Test
    fun samePointIsZero() {
        assertEquals(0.0, GeoDistance.metersBetween(-23.5505, -46.6333, -23.5505, -46.6333), 0.0)
    }

    @Test
    fun oneDegreeOfLatitudeIsAbout111Km() {
        assertEquals(111_195.0, GeoDistance.metersBetween(0.0, 0.0, 1.0, 0.0), 1.0)
    }

    @Test
    fun oneDegreeOfLongitudeShrinksWithLatitude() {
        assertEquals(111_195.0 / 2, GeoDistance.metersBetween(60.0, 0.0, 60.0, 1.0), 10.0)
    }

    @Test
    fun isSymmetric() {
        val forward = GeoDistance.metersBetween(-23.5505, -46.6333, -22.9068, -43.1729)
        val backward = GeoDistance.metersBetween(-22.9068, -43.1729, -23.5505, -46.6333)
        assertEquals(forward, backward, 1e-6)
    }

    @Test
    fun crossesTheAntimeridian() {
        assertEquals(
            GeoDistance.metersBetween(0.0, 0.0, 0.0, 2.0),
            GeoDistance.metersBetween(0.0, 179.0, 0.0, -179.0),
            1e-3
        )
    }
}
