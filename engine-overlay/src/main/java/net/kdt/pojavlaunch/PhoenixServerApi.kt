package net.kdt.pojavlaunch

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PhoenixRemoteInventory(
    val playerUuid: String,
    val coins: Int,
    val isOwner: Boolean,
    val owned: Set<String>,
    val equipped: Map<String, String>
)

object PhoenixServerApi {
    private fun request(baseUrl: String, token: String, path: String, method: String = "GET", body: String? = null): String {
        val base = baseUrl.trim().removeSuffix("/")
        require(base.startsWith("http://") || base.startsWith("https://")) { "Enter a server URL such as http://192.168.1.10:8765" }
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty("Authorization", "Bearer " + token.trim())
            connection.setRequestProperty("Accept", "text/plain")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException(response.ifBlank { "Server returned HTTP $status" })
            return response
        } finally {
            connection.disconnect()
        }
    }

    fun inventory(baseUrl: String, token: String): PhoenixRemoteInventory {
        val fields = request(baseUrl, token, "/api/inventory").lineSequence()
            .mapNotNull { line ->
                val split = line.indexOf('=')
                if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
            }.toMap()
        val equipped = listOf("cape", "wings", "head", "back", "emote")
            .mapNotNull { slot -> fields[slot]?.takeIf { it.isNotBlank() }?.let { slot to it } }.toMap()
        return PhoenixRemoteInventory(
            playerUuid = fields["uuid"].orEmpty(),
            coins = fields["coins"]?.toIntOrNull() ?: 0,
            isOwner = fields["owner"].equals("true", true),
            owned = fields["owned"].orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
            equipped = equipped
        )
    }

    fun equip(baseUrl: String, token: String, cosmeticId: String): String =
        request(baseUrl, token, "/api/equip", "POST", "id=" + URLEncoder.encode(cosmeticId, "UTF-8"))

    fun unequip(baseUrl: String, token: String, slot: String): String =
        request(baseUrl, token, "/api/unequip", "POST", "slot=" + URLEncoder.encode(slot, "UTF-8"))
}
