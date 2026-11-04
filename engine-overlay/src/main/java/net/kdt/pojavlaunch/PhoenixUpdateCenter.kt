package net.kdt.pojavlaunch

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val PHOENIX_UPDATE_MANIFEST =
    "https://raw.githubusercontent.com/dipayan900/PHOENIX-CLIENT/integration/amethyst-phoenix/phoenix-update/stable.json"

private data class PhoenixUpdateManifest(
    val schemaVersion: Int,
    val channel: String,
    val latestVersion: String,
    val minimumLauncherVersion: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkSha256: String
)

private fun parsePhoenixUpdateManifest(raw: String): PhoenixUpdateManifest {
    require(raw.length <= 256_000) { "Update manifest is unexpectedly large." }
    val json = JSONObject(raw)
    require(json.optInt("schemaVersion", -1) == 1) { "Unsupported update manifest schema." }
    val channel = json.optString("channel")
    require(channel == "stable" || channel == "beta") { "Invalid update channel." }
    val version = json.optString("latestVersion")
    require(Regex("""[0-9]+\.[0-9]+\.[0-9]+""").matches(version)) { "Invalid release version." }
    val apkUrl = json.optString("apkUrl")
    val hash = json.optString("apkSha256")
    require(apkUrl.isBlank() || (apkUrl.startsWith("https://") && apkUrl.length < 2048)) {
        "Update downloads must use HTTPS."
    }
    require(hash.isBlank() || Regex("""[a-fA-F0-9]{64}""").matches(hash)) { "Invalid SHA-256 checksum." }
    return PhoenixUpdateManifest(
        schemaVersion = 1,
        channel = channel,
        latestVersion = version,
        minimumLauncherVersion = json.optString("minimumLauncherVersion", "0.0.0"),
        releaseNotes = json.optString("releaseNotes", "").take(4000),
        apkUrl = apkUrl,
        apkSha256 = hash.lowercase()
    )
}

private fun fetchPhoenixUpdateManifest(): PhoenixUpdateManifest {
    val connection = (URL(PHOENIX_UPDATE_MANIFEST).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8000
        readTimeout = 8000
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-cache")
    }
    try {
        require(connection.responseCode == HttpURLConnection.HTTP_OK) {
            "Update service returned HTTP ${connection.responseCode}."
        }
        val contentType = connection.contentType.orEmpty()
        require(contentType.contains("json", true) || contentType.contains("text/plain", true)) {
            "Update service did not return a JSON manifest."
        }
        val rawBytes = connection.inputStream.use { it.readBytes() }
        require(rawBytes.size <= 256 * 1024) { "Update manifest is unexpectedly large." }
        val signatureConnection = (URL(PhoenixUpdateSecurity.SIGNATURE_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = false
        }
        val detachedSignature = try {
            require(signatureConnection.responseCode == HttpURLConnection.HTTP_OK) {
                "Signed update manifest is not published yet."
            }
            signatureConnection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            signatureConnection.disconnect()
        }
        require(PhoenixUpdateSecurity.verifyManifest(rawBytes, detachedSignature)) {
            "Manifest signature is not trusted. Update checking is disabled until the release public key is configured."
        }
        return parsePhoenixUpdateManifest(rawBytes.toString(Charsets.UTF_8))
    } finally {
        connection.disconnect()
    }
}

internal fun phoenixSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered(64 * 1024).use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

@Composable
internal fun PhoenixUpdateCenter(
    context: Context,
    accent: Color,
    panel: Color,
    muted: Color,
    onNotice: (String) -> Unit
) {
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Update manifest has not been checked in this session.") }
    var manifest by remember { mutableStateOf<PhoenixUpdateManifest?>(null) }
    var backupStatus by remember { mutableStateOf("No update backup created in this session.") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PHOENIX UPDATE CENTER", color = accent, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Text("A protected update pipeline for launcher releases, Minecraft profiles and Phoenix components.", color = muted, fontSize = 12.sp)
        Button(
            onClick = {
                if (!checking) {
                    checking = true
                    status = "Checking the signed-official Phoenix release manifest…"
                    Thread {
                        val result = runCatching { fetchPhoenixUpdateManifest() }
                        (context as? android.app.Activity)?.runOnUiThread {
                            result.onSuccess {
                                manifest = it
                                status = if (it.apkUrl.isBlank() || it.apkSha256.isBlank()) {
                                    "Manifest is reachable, but no installable release is published yet. No files were changed."
                                } else {
                                    "Release metadata verified. APK download and Android installation remain gated until release signing is configured."
                                }
                            }.onFailure {
                                status = it.message ?: "Could not check for updates. Existing game files were not changed."
                            }
                            checking = false
                        }
                    }.start()
                }
            },
            enabled = !checking,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (checking) "CHECKING…" else "CHECK FOR UPDATES", color = Color.Black, fontWeight = FontWeight.Bold) }

        PhoenixUpdateInfoCard("UPDATE MANIFEST", manifest?.let { "Stable · ${it.latestVersion}" } ?: "Not checked", status, accent, panel, muted)
        manifest?.let {
            if (it.releaseNotes.isNotBlank()) PhoenixUpdateInfoCard("RELEASE NOTES", "Published notes", it.releaseNotes, accent, panel, muted)
        }

        Text("UPDATE SAFETY SYSTEMS", color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp)
        listOf(
            "Update Manager" to "HTTPS staging-download, size limits and component activation/rollback helpers are present. The UI does not yet run a full release installation transaction.",
            "Integrity Verifier" to "SHA-256 and Ed25519 verification paths are implemented. Verification intentionally fails closed until the Phoenix public key is provisioned.",
            "Compatibility Engine" to "Uses Phoenix's version-specific adapter matrix; a successful build is not treated as runtime compatibility.",
            "Profile Manager" to "Minecraft profiles remain separate. Existing profile directories are not rewritten by this screen.",
            "Data Migration Manager" to "A schema-versioned JSON migration runner validates in-memory copies. Project-specific migration steps still need to be added as settings schemas evolve.",
            "Backup & Rollback" to "Staged component swap and previous-version rollback helpers are present. Full world backup/restore and automatic crash-triggered rollback still require integration testing.",
            "Release Signing" to "Production signing identity is not embedded in source. Configure one persistent protected release key in CI before publishing APK updates.",
            "Update Notifications" to "This screen reports update-check results. WorkManager periodic checks are scheduled from the dashboard. Notifications require a published signed manifest and Android notification permission."
        ).forEach { (title, detail) ->
            PhoenixUpdateInfoCard(title, "Safety status", detail, accent, panel, muted)
        }

        OutlinedButton(
            onClick = {
                val prefs = context.getSharedPreferences("phoenix_client", Context.MODE_PRIVATE)
                val directory = File(context.filesDir, "phoenix_update_backups")
                val saved = runCatching {
                    if (!directory.exists() && !directory.mkdirs()) error("Could not create private backup folder.")
                    val source = JSONObject()
                        .put("createdAt", System.currentTimeMillis())
                        .put("preferences", JSONObject().apply {
                            prefs.all.forEach { (key, value) ->
                                when (value) {
                                    is String -> put(key, value)
                                    is Boolean -> put(key, value)
                                    is Int -> put(key, value)
                                    is Long -> put(key, value)
                                    is Float -> put(key, value.toDouble())
                                }
                            }
                        })
                    val target = File(directory, "preferences-${System.currentTimeMillis()}.json")
                    val temp = File(directory, target.name + ".tmp")
                    temp.writeText(source.toString(2))
                    if (!temp.renameTo(target)) error("Could not finalize the backup file.")
                    target.name
                }
                saved.onSuccess {
                    backupStatus = "Saved private settings backup: $it"
                    onNotice("Phoenix update settings backup created.")
                }.onFailure {
                    backupStatus = it.message ?: "Backup failed."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("BACK UP PHOENIX SETTINGS") }
        Text(backupStatus, color = muted, fontSize = 11.sp)
        Text(
            "Safety note: this is the update-system foundation, not permission for silent APK installation. Android will still require its normal package-install confirmation. Do not uninstall Phoenix to update it.",
            color = muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PhoenixUpdateInfoCard(
    title: String,
    value: String,
    detail: String,
    accent: Color,
    panel: Color,
    muted: Color
) {
    Column(
        Modifier.fillMaxWidth().background(panel, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(detail, color = muted, fontSize = 11.sp)
    }
}
