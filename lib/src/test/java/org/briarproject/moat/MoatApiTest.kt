package org.briarproject.moat

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoatApiTest {
    private val moatApi = MoatApi()

    @Test
    fun testCn() {
        val bridges = moatApi.getWithCountry("cn")
        assertTrue(bridges.any { it.type == "obfs4" })
        assertTrue(bridges.any { it.type == "snowflake" })
        assertTrue(bridges.all { it.bridgeStrings.isNotEmpty() })
    }
    @Test
    fun testUs() {
        assertEquals(emptyList(), moatApi.getWithCountry("us"))
    }
}
