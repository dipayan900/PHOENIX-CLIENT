package net.kdt.pojavlaunch

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom

private fun phoenixSocialHttp(base: String, path: String, token: String, method: String, payload: JSONObject?): JSONObject {
    require(base.startsWith("https://")) { "Enter the deployed Phoenix Social HTTPS URL." }
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
    var api by remember { mutableStateOf(prefs.getString("api", "") ?: "") }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var userId by remember { mutableStateOf(prefs.getString("user_id", "") ?: "") }
    var displayName by remember { mutableStateOf(prefs.getString("display_name", "") ?: "") }
    var search by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(JSONArray()) }
    var requests by remember { mutableStateOf(JSONArray()) }
    var friends by remember { mutableStateOf(JSONArray()) }
    var messages by remember { mutableStateOf(JSONArray()) }
    var status by remember { mutableStateOf("Deploy Phoenix Social and enter its HTTPS URL to connect.") }
    var busy by remember { mutableStateOf(false) }

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
        prefs.edit().putString("user_id", userId).apply()
        requests = call("/v1/friends/requests").optJSONArray("requests") ?: JSONArray()
        friends = call("/v1/friends").optJSONArray("friends") ?: JSONArray()
        status = "Friends and requests refreshed."
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text("PHOENIX SOCIAL", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Text("Friend network · requests · private launcher messages", color = Color(0xFFAAA8B2), fontSize = 12.sp)
        OutlinedTextField(api, { api = it; prefs.edit().putString("api", it).apply() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Deployed Worker HTTPS URL") })
        if (token.isBlank()) {
            OutlinedTextField(username, { username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(16) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Guest username") })
            Button(enabled = !busy && username.length >= 3 && api.startsWith("https://"), onClick = {
                task {
                    val device = prefs.getString("device_id", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
                    val secret = prefs.getString("device_secret", null) ?: phoenixSocialSecret().also { prefs.edit().putString("device_secret", it).apply() }
                    val result = withContext(Dispatchers.IO) {
                        phoenixSocialHttp(api, "/v1/auth/guest", "", "POST", JSONObject().put("deviceId", device).put("deviceSecret", secret).put("username", username))
                    }
                    token = result.optString("token")
                    val u = result.optJSONObject("user")
                    userId = u?.optString("id", "") ?: ""
                    displayName = u?.optString("username", username) ?: username
                    prefs.edit().putString("token", token).putString("user_id", userId).putString("display_name", displayName).putString("username", username).apply()
                    status = "Connected as " + displayName + " (guest)."
                    refresh()
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text(if (busy) "CONNECTING…" else "CREATE / RESTORE GUEST ACCOUNT", color = Color.Black) }
            Text("Guests can send requests. Only verified Minecraft Java accounts appear as recipients. Microsoft sign-in is not yet connected in this launcher build.", color = Color(0xFFAAA8B2), fontSize = 11.sp)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(displayName, color = Color.White, fontWeight = FontWeight.Bold); Text("Phoenix guest account", color = Color(0xFFAAA8B2), fontSize = 11.sp) }
                TextButton(onClick = { token = ""; userId = ""; displayName = ""; prefs.edit().remove("token").remove("user_id").remove("display_name").apply(); status = "Signed out." }) { Text("SIGN OUT", color = accent) }
            }
            Button(enabled = !busy, onClick = { task { refresh() } }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("REFRESH", color = Color.Black) }
            OutlinedTextField(search, { search = it.take(24) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search verified Minecraft Java players") })
            Button(enabled = !busy && search.trim().length >= 3, onClick = {
                task { results = call("/v1/users/search?q=" + URLEncoder.encode(search.trim(), "UTF-8")).optJSONArray("users") ?: JSONArray() }
            }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("SEARCH PLAYERS", color = Color.Black) }
            for (i in 0 until results.length()) {
                val u = results.optJSONObject(i) ?: continue
                SocialPlayer(u.optString("username"), "Verified Java account · Send request") {
                    task { call("/v1/friends/requests", "POST", JSONObject().put("toUserId", u.optString("id"))); status = "Friend request sent."; refresh() }
                }
            }
            SocialLabel("FRIEND REQUESTS", accent)
            for (i in 0 until requests.length()) {
                val item = requests.optJSONObject(i) ?: continue
                val incoming = item.optString("to_user_id") == userId
                val person = if (incoming) item.optString("from_username") else item.optString("to_username")
                Row(Modifier.fillMaxWidth().background(Color(0xFF17171F), RoundedCornerShape(12.dp)).padding(9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(person, color = Color.White); Text(if (incoming) "Incoming" else "Sent · pending", color = Color(0xFFAAA8B2), fontSize = 10.sp) }
                    if (incoming) {
                        TextButton(onClick = { task { call("/v1/friends/requests/" + item.optString("id") + "/respond", "POST", JSONObject().put("action", "accept")); refresh() } }) { Text("ACCEPT", color = accent) }
                        TextButton(onClick = { task { call("/v1/friends/requests/" + item.optString("id") + "/respond", "POST", JSONObject().put("action", "decline")); refresh() } }) { Text("DECLINE", color = Color.Red) }
                    }
                }
            }
            SocialLabel("YOUR FRIENDS", accent)
            for (i in 0 until friends.length()) {
                val f = friends.optJSONObject(i) ?: continue
                SocialPlayer(f.optString("username"), "Open private chat") {
                    selectedId = f.optString("id"); selectedName = f.optString("username")
                    task { messages = call("/v1/messages?with=" + URLEncoder.encode(selectedId, "UTF-8")).optJSONArray("messages") ?: JSONArray() }
                }
            }
            if (selectedId.isNotBlank()) {
                SocialLabel("CHAT · " + selectedName, accent)
                for (i in 0 until messages.length()) {
                    val m = messages.optJSONObject(i) ?: continue
                    Text(m.optString("body"), color = Color.White, modifier = Modifier.fillMaxWidth().background(Color(0xFF17171F), RoundedCornerShape(9.dp)).padding(10.dp))
                }
                OutlinedTextField(draft, { draft = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Message (500 characters max)") })
                Button(enabled = !busy && draft.isNotBlank(), onClick = {
                    task { call("/v1/messages", "POST", JSONObject().put("toUserId", selectedId).put("message", draft.trim())); draft = ""; messages = call("/v1/messages?with=" + URLEncoder.encode(selectedId, "UTF-8")).optJSONArray("messages") ?: JSONArray() }
                }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("SEND MESSAGE", color = Color.Black) }
            }
        }
        Text(status, color = Color(0xFFAAA8B2), fontSize = 11.sp)
    }
}

@Composable
private fun SocialLabel(text: String, accent: Color) { Text(text, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp) }

@Composable
private fun SocialPlayer(name: String, subtitle: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF17171F), RoundedCornerShape(12.dp)).clickable(onClick = action).padding(12.dp)) {
        Column(Modifier.weight(1f)) { Text(name, color = Color.White, fontWeight = FontWeight.SemiBold); Text(subtitle, color = Color(0xFFAAA8B2), fontSize = 11.sp) }
        Text("›", color = Color.White, fontSize = 22.sp)
    }
}
