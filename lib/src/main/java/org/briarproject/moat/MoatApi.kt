package org.briarproject.moat

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException

private const val MOAT_URL = "https://bridges.torproject.org/moat"
private const val MOAT_CIRCUMVENTION_SETTINGS = "circumvention/settings"
private val JSON = MediaType.get("application/json; charset=utf-8")

class MoatApi @JvmOverloads constructor(
    private val client: OkHttpClient = OkHttpClient(),
    private val mapper: JsonMapper = JsonMapper.builder()
        .enable(BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
        .build(),
) {

    @Throws(IOException::class)
    fun get(): List<Bridges> {
        return getWithCountry()
    }

    @Throws(IOException::class)
    fun getWithCountry(country: String? = null): List<Bridges> {
        val body = RequestBody.create(JSON, country?.let { """{"country": "$it"}""" } ?: "")
        val request: Request = Request.Builder()
            .url("$MOAT_URL/$MOAT_CIRCUMVENTION_SETTINGS")
            .post(body)
            .build()
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
}
