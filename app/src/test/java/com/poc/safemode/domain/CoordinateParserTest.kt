package com.poc.safemode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateParserTest {

    @Test
    fun acceptsDotAndCommaAsDecimalSeparator() {
        assertEquals(-23.5505, CoordinateParser.parseLatitude("-23.5505")!!, 0.0)
        assertEquals(-23.5505, CoordinateParser.parseLatitude("-23,5505")!!, 0.0)
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(10.0, CoordinateParser.parseLatitude("  10 ")!!, 0.0)
    }

    @Test
    fun acceptsRangeLimits() {
        assertEquals(90.0, CoordinateParser.parseLatitude("90")!!, 0.0)
        assertEquals(-90.0, CoordinateParser.parseLatitude("-90")!!, 0.0)
        assertEquals(180.0, CoordinateParser.parseLongitude("180")!!, 0.0)
        assertEquals(-180.0, CoordinateParser.parseLongitude("-180")!!, 0.0)
    }

    @Test
    fun rejectsValuesOutOfRange() {
        assertNull(CoordinateParser.parseLatitude("90.0001"))
        assertNull(CoordinateParser.parseLatitude("-91"))
        assertNull(CoordinateParser.parseLongitude("180.5"))
    }

    @Test
    fun longitudeAcceptsValuesLatitudeRejects() {
        assertNull(CoordinateParser.parseLatitude("120"))
        assertEquals(120.0, CoordinateParser.parseLongitude("120")!!, 0.0)
    }

    @Test
    fun rejectsTextThatToDoubleWouldAccept() {
        listOf("", " ", "abc", "-", "NaN", "Infinity", "1e1", "1f", "1d", "0x1p3", "1.2.3", "1 2")
            .forEach { assertNull("should reject '$it'", CoordinateParser.parseLatitude(it)) }
    }

    @Test
    fun formatUsesDotRegardlessOfLocaleAndRoundTrips() {
        val formatted = CoordinateParser.format(-46.6333)
        assertEquals("-46.633300", formatted)
        assertEquals(-46.6333, CoordinateParser.parseLongitude(formatted)!!, 0.0)
    }
}
