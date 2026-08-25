package com.fyiplayer.app.source.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQueriesTest {
    @Test fun `country phrases carry the given year`() {
        assertEquals(listOf("নতুন গান 2031", "নতুন সিনেমা 2031", "নতুন নাটক 2031"), LocalQueries.forCountry("bd", 2031))
    }

    @Test fun `unknown or english countries fall back to english`() {
        assertEquals(LocalQueries.forCountry("US", 2030), LocalQueries.forCountry("ZZ", 2030))
        assertTrue(LocalQueries.forCountry("NG", 2030).first().startsWith("new songs"))
    }

    @Test fun `default year is the device clock`() {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
        assertTrue(LocalQueries.forCountry("JP").all { it.endsWith(year) })
    }
}
