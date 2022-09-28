package org.briarproject.moat

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import okhttp3.*
import java.io.File
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.TimeUnit

private const val MOAT_URL = "https://bridges.torproject.org/moat"
private const val MOAT_CIRCUMVENTION_SETTINGS = "circumvention/settings"
private val JSON = MediaType.get("application/json; charset=utf-8")

class MoatApi @JvmOverloads constructor(
    private val obfs4Executable: File,
    private val obfs4Dir: File,
    private val mapper: JsonMapper = JsonMapper.builder()
        .enable(BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
        .build(),
) {
    private var obfs4Process: Process? = null

    init {
        require(obfs4Dir.isDirectory)
    }

    @Throws(IOException::class)
    fun get(): List<Bridges> {
        return getWithCountry()
    }

    @Throws(IOException::class)
    fun getWithCountry(country: String? = null): List<Bridges> {
        val port = startObfs4()
        // TODO try to use OkHttp proxy authenticator instead
        val authenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(
                    "url=https://moat.torproject.org.global.prod.fastly.net/;front=cdn.sstatic.net",
                    "\u0000".toCharArray())
            }
        }
        Authenticator.setDefault(authenticator)

        val client = OkHttpClient.Builder() // TODO try to use/copy client from constructor?
            .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("localhost", port)))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT,
                ConnectionSpec.COMPATIBLE_TLS,
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.RESTRICTED_TLS))
            .build()

        val body = RequestBody.create(JSON, country?.let { """{"country": "$it"}""" } ?: "")
        val request: Request = Request.Builder()
            .url("$MOAT_URL/$MOAT_CIRCUMVENTION_SETTINGS")
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body()
                if (!response.isSuccessful || responseBody == null) throw IOException("request error")
                val node = mapper.readTree(responseBody.string())
                val settings = node.get("settings") ?: throw IOException("no settings in response")
                if (!settings.isArray) throw IOException("settings not an array")
                settings as ArrayNode
                return settings.map { bridges ->
                    parseBridges(bridges)
                }
            }
        } finally {
            // TODO remove print log
            obfs4Dir.listFiles()?.forEach { file ->
                if (file.name == "obfs4proxy.log") {
                    file.inputStream().bufferedReader().forEachLine { line ->
                        println("  $line")
                    }
                }
            }
            obfs4Process?.destroy()
        }
    }

    @Throws(IOException::class)
    private fun parseBridges(node: JsonNode): Bridges {
        val bridgesNode = node.get("bridges") ?: throw IOException("no bridges node")
        return Bridges(
            type = bridgesNode.get("type").asText(),
            source = bridgesNode.get("source").asText(),
            bridgeStrings = bridgesNode.get("bridge_strings")?.let { array ->
                if (array.isArray) (array as ArrayNode).map { it.asText() }
                else emptyList()
            } ?: emptyList(),
        )
    }

    @Throws(IOException::class)
    private fun startObfs4(): Int {
        // TODO remove logging
        val pb = ProcessBuilder(obfs4Executable.absolutePath,
            "-enableLogging",
            "-logLevel=DEBUG"
        ).apply {
            val env = environment()
            env["TOR_PT_MANAGED_TRANSPORT_VER"] = "1"
            env["TOR_PT_STATE_LOCATION"] = obfs4Dir.absolutePath
            env["TOR_PT_EXIT_ON_STDIN_CLOSE"] = "0"
            env["TOR_PT_CLIENT_TRANSPORTS"] = "meek_lite"
            redirectErrorStream(true)
        }
        val obfs4Process = try {
            pb.start()
        } catch (e: SecurityException) {
            throw IOException(e)
        }
        this.obfs4Process = obfs4Process
        obfs4Process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("CMETHOD meek_lite socks5 127.0.0.1:")) {
                    println(line) // TODO remove print
                    return line.substring(35).toIntOrNull()
                        ?: throw IOException("Invalid port $line")
                }
            }
            throw IOException("Did not find meek")
        }
    }
}
