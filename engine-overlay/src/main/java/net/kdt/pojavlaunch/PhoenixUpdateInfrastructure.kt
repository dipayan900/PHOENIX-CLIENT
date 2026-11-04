package net.kdt.pojavlaunch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Production key provisioning is deliberately fail-closed. Replace this empty
 * value with the base64 X.509 Ed25519 public key generated for Phoenix releases.
 * Never put the matching private key in source control.
 */
internal object PhoenixUpdateSecurity {
    const val MANIFEST_URL =
        "https://raw.githubusercontent.com/dipayan900/PHOENIX-CLIENT/integration/amethyst-phoenix/phoenix-update/stable.json"
    const val SIGNATURE_URL = "$MANIFEST_URL.sig"
    private const val ED25519_PUBLIC_KEY_X509_BASE64 = ""

    fun verifyManifest(rawManifest: ByteArray, detachedSignatureBase64: String): Boolean {
        if (ED25519_PUBLIC_KEY_X509_BASE64.isBlank()) return false
        return runCatching {
            val keyBytes = Base64.getDecoder().decode(ED25519_PUBLIC_KEY_X509_BASE64)
            val publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(keyBytes))
            val signatureBytes = Base64.getDecoder().decode(detachedSignatureBase64.trim())
            Signature.getInstance("Ed25519").run {
                initVerify(publicKey)
                update(rawManifest)
                verify(signatureBytes)
            }
        }.getOrDefault(false)
    }
}

/** Download a bounded HTTPS resource without following redirects. */
internal fun phoenixDownloadHttps(urlText: String, destination: File, maxBytes: Long): Long {
    require(urlText.startsWith("https://") && urlText.length < 2048) { "Only HTTPS downloads are accepted." }
    val connection = (URL(urlText).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 20000
        instanceFollowRedirects = false
        setRequestProperty("Cache-Control", "no-cache")
    }
    try {
        require(connection.responseCode == HttpURLConnection.HTTP_OK) {
            "Download failed with HTTP ${connection.responseCode}."
        }
        val declared = connection.contentLengthLong
        require(declared <= 0L || declared <= maxBytes) { "Download exceeds the allowed size." }
        destination.parentFile?.let { require(it.exists() || it.mkdirs()) }
        val temporary = File(destination.parentFile, destination.name + ".part")
        var total = 0L
        connection.inputStream.buffered().use { input ->
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= maxBytes) { "Download exceeded the allowed size." }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        require(total > 0L) { "Downloaded file is empty." }
        if (destination.exists() && !destination.delete()) error("Could not replace staged file.")
        require(temporary.renameTo(destination)) { "Could not finalize staged download." }
        return total
    } finally {
        connection.disconnect()
    }
}

/**
 * Component activation keeps the previous directory until the replacement has
 * been moved into place. Caller must stop the game before activating components.
 */
internal fun phoenixActivateComponent(staged: File, active: File, previous: File) {
    require(staged.exists() && staged.isDirectory) { "Staged component is missing." }
    val parent = active.parentFile ?: error("Invalid component destination.")
    require(parent.exists() || parent.mkdirs()) { "Could not create component directory." }
    if (previous.exists()) previous.deleteRecursively()
    if (active.exists()) require(active.renameTo(previous)) { "Could not preserve previous component." }
    if (!staged.renameTo(active)) {
        if (previous.exists()) previous.renameTo(active)
        error("Could not activate component; previous version restored.")
    }
}

/** Restore the last known-good component directory after a failed smoke check. */
internal fun phoenixRollbackComponent(active: File, previous: File) {
    require(previous.exists() && previous.isDirectory) { "No previous component is available." }
    if (active.exists()) require(active.deleteRecursively()) { "Could not remove failed component." }
    require(previous.renameTo(active)) { "Could not restore previous component." }
}

/**
 * Versioned JSON migration helper. Migrations operate on an in-memory copy;
 * callers persist only after the complete chain and validation succeed.
 */
internal object PhoenixDataMigrations {
    const val CURRENT_SCHEMA = 1

    fun migrate(
        original: JSONObject,
        targetSchema: Int = CURRENT_SCHEMA,
        steps: Map<Int, (JSONObject) -> JSONObject>,
        validate: (JSONObject) -> Boolean
    ): JSONObject {
        val copy = JSONObject(original.toString())
        var schema = copy.optInt("_phoenixSchemaVersion", 1)
        require(schema in 1..targetSchema) { "Unsupported Phoenix data schema: $schema" }
        while (schema < targetSchema) {
            val migration = steps[schema] ?: error("Missing migration from schema $schema.")
            val migrated = migration(JSONObject(copy.toString()))
            schema++
            migrated.put("_phoenixSchemaVersion", schema)
            require(validate(migrated)) { "Phoenix data migration validation failed at schema $schema." }
            copy.keys().asSequence().toList().forEach { copy.remove(it) }
            migrated.keys().asSequence().forEach { key -> copy.put(key, migrated.get(key)) }
        }
        require(validate(copy)) { "Phoenix data validation failed." }
        return copy
    }
}

internal object PhoenixUpdateNotifications {
    private const val CHANNEL_ID = "phoenix_updates"
    private const val WORK_NAME = "phoenix_periodic_update_check"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequest.Builder(
            PhoenixUpdateCheckWorker::class.java, 12, TimeUnit.HOURS
        ).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    internal fun notifyUpdate(context: Context, version: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Phoenix updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Phoenix update available")
            .setContentText("Phoenix $version is available. Open Phoenix Update Center to review it.")
            .setAutoCancel(true)
            .build()
        // Android 13+ may deny notification permission. This must never affect game startup.
        runCatching { manager.notify(4107, notification) }
    }
}

internal class PhoenixUpdateCheckWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker(context, parameters) {
    override fun doWork(): Result = runCatching {
        val manifestConnection = (URL(PhoenixUpdateSecurity.MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            instanceFollowRedirects = false
        }
        val raw: ByteArray
        try {
            require(manifestConnection.responseCode == HttpURLConnection.HTTP_OK) { "Manifest unavailable." }
            raw = manifestConnection.inputStream.use { it.readBytes() }
            require(raw.size <= 256 * 1024) { "Manifest too large." }
        } finally {
            manifestConnection.disconnect()
        }
        val signatureConnection = (URL(PhoenixUpdateSecurity.SIGNATURE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            instanceFollowRedirects = false
        }
        val detached: String
        try {
            require(signatureConnection.responseCode == HttpURLConnection.HTTP_OK) { "Manifest signature unavailable." }
            detached = signatureConnection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            signatureConnection.disconnect()
        }
        require(PhoenixUpdateSecurity.verifyManifest(raw, detached)) {
            "Manifest signature is not trusted; update notification suppressed."
        }
        val json = JSONObject(raw.toString(Charsets.UTF_8))
        val latest = json.optString("latestVersion")
        val current = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
            .versionName ?: "0.0.0"
        if (compareVersions(latest, current) > 0) {
            PhoenixUpdateNotifications.notifyUpdate(applicationContext, latest)
        }
        Result.success()
    }.getOrElse { error ->
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    private fun compareVersions(left: String, right: String): Int {
        fun parts(value: String) = value.split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrNull(index) ?: 0).compareTo(b.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }
        return 0
    }
}
