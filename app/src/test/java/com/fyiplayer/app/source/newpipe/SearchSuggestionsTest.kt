package com.fyiplayer.app.source.newpipe

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSuggestionsTest {
    @Test fun `suggestions are the second element of the answer`() {
        val body = """["pytho",["python full course","python","পাইথন"],[],{"k":1}]"""
        assertEquals(listOf("python full course", "python", "পাইথন"), parseSuggestions(body))
    }

    @Test fun `no suggestions is an empty list`() {
        assertEquals(emptyList<String>(), parseSuggestions("""["zzzz",[]]"""))
    }
}
