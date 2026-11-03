package net.kdt.pojavlaunch

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import net.kdt.pojavlaunch.value.MinecraftAccount

private const val PHOENIX_SOCIAL_DEFAULT_API = "https://phoenix-social.rdipayan209.workers.dev"

private fun phoenixSocialHttp(base: String, path: String, token: String, method: String, payload: JSONObject?): JSONObject {
    require(base.startsWith("https://")) { "Phoenix Social requires its HTTPS service URL." }
    val c = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
    try {
        c.requestMethod = method
        c.connectTimeout = 12000
        c.readTimeout = 12000
        c.setRequestProperty("Accept", "application/json")
        if (token.isNotBlank()) c.setRequestProperty("Authorization", "Bearer " + token)
        if (payload != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        }
        val status = c.responseCode
        val stream = if (status in 200..299) c.inputStream else c.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val result = if (body.isBlank()) JSONObject() else JSONObject(body)
        if (status !in 200..299) error(result.optString("error", "Phoenix Social HTTP " + status))
        return result
    } finally { c.disconnect() }
}

private fun phoenixSocialSecret(): String {
    val bytes = ByteArray(36)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

@Composable
fun PhoenixSocialScreen(context: Context, accent: Color) {
    val prefs = remember { context.getSharedPreferences("phoenix_social", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var api by remember { mutableStateOf(prefs.getString("api", PHOENIX_SOCIAL_DEFAULT_API) ?: PHOENIX_SOCIAL_DEFAULT_API) }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var userId by remember { mutableStateOf(prefs.getString("user_id", "") ?: "") }
    var displayName by remember { mutableStateOf(prefs.getString("display_name", "") ?: "") }
    var authType by remember { mutableStateOf(prefs.getString("auth_type", "guest") ?: "guest") }
    var search by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(JSONArray()) }
    var requests by remember { mutableStateOf(JSONArray()) }
    var friends by remember { mutableStateOf(JSONArray()) }
    var messages by remember { mutableStateOf(JSONArray()) }
    var searched by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Connect a Phoenix account to find friends and chat.") }
    var busy by remember { mutableStateOf(false) }
    var removeId by remember { mutableStateOf("") }
    var removeName by remember { mutableStateOf("") }

    fun task(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { block() } catch (e: Exception) { status = e.message ?: "Request failed." }
            finally { busy = false }
        }
    }

    suspend fun call(path: String, method: String = "GET", payload: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) { phoenixSocialHttp(api, path, token, method, payload) }

    suspend fun refresh() {
        val me = call("/v1/me").optJSONObject("user")
        userId = me?.optString("id", "") ?: ""
        displayName = me?.optString("username", displayName) ?: displayName
        authType = me?.optString("auth_type", "guest") ?: "guest"
        prefs.edit().putString("user_id", userId).putString("display_name", displayName)
            .putString("auth_type", authType).apply()
        call("/v1/presence", "POST", JSONObject())
        requests = call("/v1/friends/requests").optJSONArray("requests") ?: JSONArray()
        friends = call("/v1/friends").optJSONArray("friends") ?: JSONArray()
        status = "Connected · friends and requests are up to date."
    }

    suspend fun currentMinecraftAccessToken(): String = withContext(Dispatchers.IO) {
        val account: MinecraftAccount? = PojavProfile.getCurrentProfileContent(context, null)
        if (account == null || !account.isMicrosoft || account.accessToken.isNullOrBlank() || account.accessToken == "0")
            throw IllegalStateException("No signed-in Microsoft Minecraft account is selected. Sign in with Microsoft in the launcher first.")
        if (account.expiresAt > 0L && account.expiresAt <= System.currentTimeMillis())
            throw IllegalStateException("Your Microsoft session has expired. Re-select your Microsoft account in the launcher to refresh it, then retry.")
        account.accessToken
    }

    suspend fun connectPremium(linkExisting: Boolean) {
        val accessToken = currentMinecraftAccessToken()
        val payload = JSONObject().put("minecraftAccessToken", accessToken)
        val result = if (linkExisting) {
            call("/v1/auth/link-minecraft", "POST", payload)
        } else {
            withContext(Dispatchers.IO) { phoenixSocialHttp(api, "/v1/auth/minecraft", "", "POST", payload) }
        }
        if (!linkExisting) token = result.optString("token")
        val me = result.optJSONObject("user")
        userId = me?.optString("id", "") ?: ""
        displayName = me?.optString("username", "MinecraftPlayer") ?: "MinecraftPlayer"
        authType = "premium"
        prefs.edit().putString("token", token).putString("user_id", userId)
            .putString("display_name", displayName).putString("auth_type", authType).apply()
        status = "Verified Minecraft Java account connected as $displayName."
        refresh()
    }

    LaunchedEffect(token, api) {
        if (token.isNotBlank() && api.startsWith("https://")) {
            try { refresh() } catch (e: Exception) { status = e.message ?: "Could not restore Phoenix Social." }
        }
    }

    LaunchedEffect(token, api, selectedId) {
        if (token.isNotBlank() && api.startsWith("https://")) {
            while (isActive) {
                delay(18000)
                if (!busy) {
                    try {
                        call("/v1/presence", "POST", JSONObject())
                        requests = call("/v1/friends/requests").optJSONArray("requests") ?: JSONArray()
                        friends = call("/v1/friends").optJSONArray("friends") ?: JSONArray()
                        if (selectedId.isNotBlank()) {
                            messages = call("/v1/messages?with=" + URLEncoder.encode(selectedId, "UTF-8"))
                                .optJSONArray("messages") ?: JSONArray()
                        }
                    } catch (_: Exception) {
                        // Keep the last successful data visible during brief network interruptions.
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text("PHOENIX SOCIAL", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Text("Friends · requests · private launcher messages", color = Color(0xFFAAA8B2), fontSize = 12.sp)
        OutlinedTextField(
            value = api,
            onValueChange = { api = it.trim(); prefs.edit().putString("api", it.trim()).apply() },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Phoenix Social service URL") }
        )

        if (token.isBlank()) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(16) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Guest username") }
            )
            Button(
                enabled = !busy && username.length >= 3 && api.startsWith("https://"),
                onClick = {
                    task {
                        val device = prefs.getString("device_id", null) ?: java.util.UUID.randomUUID().toString()
                            .also { prefs.edit().putString("device_id", it).apply() }
                        val secret = prefs.getString("device_secret", null) ?: phoenixSocialSecret()
                            .also { prefs.edit().putString("device_secret", it).apply() }
                        val result = withContext(Dispatchers.IO) {
                            phoenixSocialHttp(api, "/v1/auth/guest", "", "POST",
                                JSONObject().put("deviceId", device).put("deviceSecret", secret).put("username", username))
                        }
                        token = result.optString("token")
                        val u = result.optJSONObject("user")
                        userId = u?.optString("id", "") ?: ""
                        displayName = u?.optString("username", username) ?: username
                        authType = "guest"
                        prefs.edit().putString("token", token).putString("user_id", userId)
                            .putString("display_name", displayName).putString("username", username)
                            .putString("auth_type", authType).apply()
                        status = "Connected as $displayName (guest)."
                        refresh()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) { Text(if (busy) "CONNECTING…" else "CREATE / RESTORE GUEST ACCOUNT", color = Color.Black) }

            OutlinedButton(
                enabled = !busy && api.startsWith("https://"),
                onClick = { task { connectPremium(false) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("CONNECT CURRENT MICROSOFT ACCOUNT", color = accent) }

            Text(
                "Guests can send requests. Only accounts verified by Minecraft Services can receive and accept requests. Your Minecraft access token is sent only to Phoenix Social over HTTPS for verification and is never saved by the Worker.",
                color = Color(0xFFAAA8B2), fontSize = 11.sp
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(displayName, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (authType == "premium") "Verified Minecraft Java account" else "Phoenix guest account",
                        color = if (authType == "premium") Color(0xFF70D89A) else Color(0xFFAAA8B2), fontSize = 11.sp)
                }
                TextButton(onClick = {
                    token = ""; userId = ""; displayName = ""; authType = "guest"
                    selectedId = ""; selectedName = ""; messages = JSONArray()
                    prefs.edit().remove("token").remove("user_id").remove("display_name").remove("auth_type").apply()
                    status = "Signed out. Your device guest identity remains available to restore."
                }) { Text("SIGN OUT", color = accent) }
            }

            if (authType != "premium") {
                OutlinedButton(enabled = !busy, onClick = { task { connectPremium(true) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("LINK VERIFIED MINECRAFT JAVA ACCOUNT", color = accent)
                }
            }

            Button(enabled = !busy, onClick = { task { refresh() } }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text(if (busy) "PLEASE WAIT…" else "REFRESH FRIENDS", color = Color.Black) }

            SocialLabel("FIND PLAYERS", accent)
            OutlinedTextField(search, { search = it.take(24); searched = false }, Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Search Phoenix username") })
            Button(enabled = !busy && search.trim().length >= 3, onClick = {
                task {
                    searched = true
                    results = call("/v1/users/search?q=" + URLEncoder.encode(search.trim(), "UTF-8"))
                        .optJSONArray("users") ?: JSONArray()
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Text("SEARCH PLAYERS", color = Color.Black)
            }
            if (searched && results.length() == 0) SocialEmpty("No new verified players found for that search.", accent)
            for (i in 0 until results.length()) {
                val u = results.optJSONObject(i) ?: continue
                SocialPlayer(u.optString("username"), "Verified Java account · Send request") {
                    task {
                        val result = call("/v1/friends/requests", "POST", JSONObject().put("toUserId", u.optString("id")))
                        status = if (result.optString("status") == "accepted") "You are now friends." else "Friend request sent."
                        results = JSONArray()
                        refresh()
                    }
                }
            }

            SocialLabel("FRIEND REQUESTS  ·  ${requests.length()}", accent)
            if (requests.length() == 0) SocialEmpty("No pending friend requests.", accent)
            for (i in 0 until requests.length()) {
                val item = requests.optJSONObject(i) ?: continue
                val incoming = item.optString("to_user_id") == userId
                val person = if (incoming) item.optString("from_username") else item.optString("to_username")
                Row(Modifier.fillMaxWidth().background(Color(0xFF17171F), RoundedCornerShape(12.dp)).padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(person, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(if (incoming) "Incoming request" else "Sent · awaiting response", color = Color(0xFFAAA8B2), fontSize = 10.sp)
                    }
                    if (incoming) {
                        TextButton(onClick = {
                            task { call("/v1/friends/requests/" + item.optString("id") + "/respond", "POST", JSONObject().put("action", "accept")); status = "Friend request accepted."; refresh() }
                        }) { Text("ACCEPT", color = accent) }
                        TextButton(onClick = {
                            task { call("/v1/friends/requests/" + item.optString("id") + "/respond", "POST", JSONObject().put("action", "decline")); refresh() }
                        }) { Text("DECLINE", color = Color(0xFFFF7777)) }
                    } else {
                        TextButton(onClick = {
                            task { call("/v1/friends/requests/" + item.optString("id"), "DELETE"); status = "Friend request cancelled."; refresh() }
                        }) { Text("CANCEL", color = accent) }
                    }
                }
            }

            SocialLabel("YOUR FRIENDS  ·  ${friends.length()}", accent)
            if (friends.length() == 0) SocialEmpty("Your accepted Phoenix friends will appear here.", accent)
            for (i in 0 until friends.length()) {
                val f = friends.optJSONObject(i) ?: continue
                Row(Modifier.fillMaxWidth().background(Color(0xFF17171F), RoundedCornerShape(12.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(f.optString("username"), color = Color.White, fontWeight = FontWeight.SemiBold)
                        val online = f.optInt("online", 0) == 1
                        Text(if (online) "● ONLINE" else "○ OFFLINE", color = if (online) Color(0xFF70D89A) else Color(0xFFAAA8B2), fontSize = 10.sp)
                    }
                    TextButton(onClick = {
                        selectedId = f.optString("id")
                        selectedName = f.optString("username")
                        task { messages = call("/v1/messages?with=" + URLEncoder.encode(selectedId, "UTF-8"))
                            .optJSONArray("messages") ?: JSONArray() }
                    }) { Text("CHAT", color = accent) }
                    TextButton(onClick = { removeId = f.optString("id"); removeName = f.optString("username") }) {
                        Text("REMOVE", color = Color(0xFFFF7777))
                    }
                }
            }

            if (selectedId.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SocialLabel("CHAT · $selectedName", accent)
                    TextButton(onClick = { selectedId = ""; selectedName = ""; messages = JSONArray() }) { Text("CLOSE", color = accent) }
                }
                if (messages.length() == 0) SocialEmpty("No messages yet. Start the conversation.", accent)
                for (i in 0 until messages.length()) {
                    val m = messages.optJSONObject(i) ?: continue
                    val mine = m.optString("sender_id") == userId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Column(Modifier.widthIn(max = 300.dp).background(
                            if (mine) accent.copy(alpha = 0.20f) else Color(0xFF17171F),
                            RoundedCornerShape(12.dp)).padding(10.dp)) {
                            Text(if (mine) "YOU" else selectedName, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(m.optString("body"), color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                OutlinedTextField(draft, { draft = it.take(500) }, Modifier.fillMaxWidth(),
                    label = { Text("Message · 500 characters max") })
                Button(enabled = !busy && draft.isNotBlank(), onClick = {
                    task {
                        call("/v1/messages", "POST", JSONObject().put("toUserId", selectedId).put("message", draft.trim()))
                        draft = ""
                        messages = call("/v1/messages?with=" + URLEncoder.encode(selectedId, "UTF-8"))
                            .optJSONArray("messages") ?: JSONArray()
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                    Text("SEND MESSAGE", color = Color.Black)
                }
            }
        }
        Text(status, color = Color(0xFFAAA8B2), fontSize = 11.sp)
    }

    if (removeId.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { removeId = ""; removeName = "" },
            title = { Text("Remove $removeName?") },
            text = { Text("This removes the Phoenix friendship and disables private messaging. You can send a new request later.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = removeId
                    removeId = ""; removeName = ""
                    task {
                        call("/v1/friends/$target", "DELETE")
                        if (selectedId == target) { selectedId = ""; selectedName = ""; messages = JSONArray() }
                        status = "Friend removed."
                        refresh()
                    }
                }) { Text("REMOVE", color = Color(0xFFFF7777)) }
            },
            dismissButton = { TextButton(onClick = { removeId = ""; removeName = "" }) { Text("CANCEL", color = accent) } }
        )
    }
}

@Composable
private fun SocialLabel(text: String, accent: Color) {
    Text(text, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
}

@Composable
private fun SocialEmpty(text: String, accent: Color) {
    Text(text, color = Color(0xFFAAA8B2), fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().background(Color(0xFF17171F), RoundedCornerShape(10.dp)).padding(11.dp))
}

@Composable
private fun SocialPlayer(name: String, subtitle: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF17171F), RoundedCornerShape(12.dp))
        .clickable(onClick = action).padding(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFAAA8B2), fontSize = 11.sp)
        }
        Text("›", color = Color.White, fontSize = 22.sp)
    }
}
