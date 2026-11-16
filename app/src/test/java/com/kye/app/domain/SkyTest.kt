package com.kye.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SkyTest {
    @Test fun skyFollowsTheHour() {
        assertEquals(Sky.Time.DAWN, Sky.time(6))
        assertEquals(Sky.Time.DAY, Sky.time(13))
        assertEquals(Sky.Time.DUSK, Sky.time(19))
        assertEquals(Sky.Time.NIGHT, Sky.time(23))
        assertEquals(Sky.Time.NIGHT, Sky.time(2))
    }

    @Test fun greetingFollowsTheHour() {
        assertEquals("Good morning", Sky.greeting(11))
        assertEquals("Good afternoon", Sky.greeting(12))
        assertEquals("Good evening", Sky.greeting(20))
        assertEquals("Good night", Sky.greeting(23))
    }

    @Test fun festivals() {
        assertEquals("Happy Deepavali 🪔", Sky.festival(LocalDate.of(2026, 11, 8))?.greeting)
        assertEquals("Happy Deepavali 🪔", Sky.festival(LocalDate.of(2026, 11, 7))?.greeting)
        assertEquals("Happy Holi 🎨", Sky.festival(LocalDate.of(2027, 3, 22))?.greeting)
        assertEquals("Happy Pongal 🌾", Sky.festival(LocalDate.of(2027, 1, 14))?.greeting)
        assertEquals("Happy Independence Day 🇮🇳", Sky.festival(LocalDate.of(2026, 8, 15))?.greeting)
        assertNull(Sky.festival(LocalDate.of(2026, 11, 16)))
    }
}
