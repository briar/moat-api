package org.briarproject.moat

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MoatApiTest {
    @TempDir
    @JvmField
    var tempFolder: File? = null

    var obfs4Executable : File = File(tempFolder, "obfs4Executable")

    private lateinit var moatApi: MoatApi

    @BeforeEach
    fun setup() {
        extractObfs4Executable()
        moatApi = MoatApi(
            obfs4Executable,
            obfs4Dir = tempFolder ?: fail(),
        )
    }

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

    private fun extractObfs4Executable() {
        obfs4Executable.outputStream().use { outputStream ->
            getInputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        if (!obfs4Executable.setExecutable(true, true)) throw IOException()
    }

    private fun getInputStream(): InputStream {
        val inputStream = getResourceInputStream("obfs4proxy_linux-x86_64.zip")
        return ZipInputStream(inputStream).apply {
            if (nextEntry == null) throw IOException()
        }
    }

    @Suppress("SameParameterValue")
    private fun getResourceInputStream(name: String): InputStream {
        return javaClass.classLoader.getResourceAsStream(name) ?: fail()
    }
}
