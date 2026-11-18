package com.msgu.internet

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ServerConfig(val url: String = "", val publicKey: String = "")
data class UserSession(
    val uid: String, val access: String, val refresh: String, val expiresAt: Long
)
data class ChatProfile(val id: String, val username: String, val name: String, val discoverable: Boolean)
data class ChatItem(
    val id: String, val peerId: String, val peerName: String, val status: String,
    val requestedBy: String, val preview: String, val updatedAt: String
)
data class ChatText(val id: String, val author: String, val body: String, val time: String)

object ConfigRules {
    fun validUrl(value: String): Boolean = try {
        val uri = URI(value.trim())
        uri.scheme == "https" && uri.host?.endsWith(".supabase.co") == true &&
            uri.userInfo == null && uri.query == null && uri.fragment == null
    } catch (_: Exception) { false }
    fun validKey(value: String): Boolean {
        val key = value.trim()
        return key.startsWith("sb_publishable_") && key.length >= 24 ||
            key.startsWith("eyJ") && key.length > 100
    }
    fun validUsername(value: String): Boolean = Regex("[a-z0-9_]{3,20}").matches(value)
}

class ApiProblem(message: String) : Exception(message)

class SupabaseGateway(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("msgu_internet_config", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(18, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    var config = ServerConfig(prefs.getString("url","").orEmpty(), prefs.getString("publicKey","").orEmpty())
        private set
    var session: UserSession? = restoreSession()
        private set

    fun saveConfig(url: String, publicKey: String) {
        val candidate = ServerConfig(url.trim().trimEnd('/'), publicKey.trim())
        require(ConfigRules.validUrl(candidate.url)) { "Use your HTTPS project URL ending in .supabase.co" }
        require(ConfigRules.validKey(candidate.publicKey)) {
            "Use only the project's public/publishable key, never a secret/service key"
        }
        if (config != candidate) clearLocalSession()
        config = candidate
        prefs.edit().putString("url",candidate.url).putString("publicKey",candidate.publicKey).apply()
    }

    private fun secureKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey("msgu_session_key", null) as? SecretKey
        if (key != null) return key
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("msgu_session_key",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    private fun storeSession(s: UserSession) {
        session = s
        val payload = JSONObject()
            .put("uid",s.uid).put("access",s.access)
            .put("refresh",s.refresh).put("expiry",s.expiresAt).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secureKey())
        val bytes = cipher.iv + cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("session", Base64.encodeToString(bytes,Base64.NO_WRAP)).apply()
    }
    private fun restoreSession(): UserSession? {
        return try {
        val stored = prefs.getString("session",null) ?: return null
        val bytes = Base64.decode(stored,Base64.NO_WRAP)
        if (bytes.size <= 12) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secureKey(), GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        val j = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
        UserSession(j.getString("uid"),j.getString("access"),j.getString("refresh"),j.getLong("expiry"))
    } catch (_: Exception) { null }
    }
    fun clearLocalSession() {
        session = null
        prefs.edit().remove("session").apply()
    }

    private data class Reply(val status: Int, val body: String)
    private suspend fun perform(path: String, method: String, json: JSONObject?, authenticated: Boolean): Reply {
        val conf = config
        if (!ConfigRules.validUrl(conf.url) || !ConfigRules.validKey(conf.publicKey))
            throw ApiProblem("Enter your Supabase project URL and publishable key first.")
        val token = if (authenticated) session?.access ?: throw ApiProblem("Sign in first.") else ""
        return withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(conf.url + path)
                .header("apikey",conf.publicKey)
                .header("Accept","application/json")
            if (authenticated) builder.header("Authorization","Bearer " + token)
                .header("Prefer","return=representation")
            val requestBody = json?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
            builder.method(method,if (method == "GET" || method == "DELETE") null else
                requestBody ?: "{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            val response = http.newCall(builder.build()).execute()
            response.use { Reply(it.code,it.body?.string().orEmpty()) }
        }
    }
    private fun decodeError(response: Reply): ApiProblem {
        val obj = try { JSONObject(response.body) } catch (_: Exception) { JSONObject() }
        val detail = listOf("msg","error_description","message","error")
            .firstNotNullOfOrNull { key -> obj.optString(key).takeIf { it.isNotBlank() } }
        return ApiProblem((detail ?: "HTTP " + response.status + " — check connection, login or database policies").take(240))
    }
    private suspend fun query(path: String, method: String = "GET", body: JSONObject? = null, auth: Boolean = true): String {
        var reply = perform(path,method,body,auth)
        if (auth && reply.status == 401 && session?.refresh?.isNotBlank() == true) {
            refreshSession()
            reply = perform(path,method,body,auth)
        }
        if (reply.status !in 200..299) throw decodeError(reply)
        return reply.body
    }
    private fun acceptSession(body: String) {
        val j = JSONObject(body)
        val token = j.optString("access_token")
        val refresh = j.optString("refresh_token")
        val uid = j.optJSONObject("user")?.optString("id") ?: session?.uid.orEmpty()
        if (token.isBlank() || refresh.isBlank() || uid.isBlank())
            throw ApiProblem("Sign-in session missing. Confirm your email and retry.")
        val seconds = j.optLong("expires_in",3600L)
        storeSession(UserSession(uid,token,refresh,System.currentTimeMillis()+seconds*1000L))
    }
    private suspend fun refreshSession() {
        val previous = session ?: throw ApiProblem("Sign in again.")
        val json = JSONObject().put("refresh_token",previous.refresh)
        val response = perform("/auth/v1/token?grant_type=refresh_token","POST",json,false)
        if (response.status !in 200..299) {
            clearLocalSession()
            throw ApiProblem("Session expired. Please sign in again.")
        }
        acceptSession(response.body)
    }
    suspend fun ensureSession() {
        if (session != null && System.currentTimeMillis() > (session?.expiresAt ?: 0L) - 60000L)
            refreshSession()
    }
    suspend fun signIn(email: String, password: String) {
        val body = JSONObject().put("email",email.trim()).put("password",password)
        val json = query("/auth/v1/token?grant_type=password","POST",body,false)
        acceptSession(json)
    }
    suspend fun signUp(email: String, password: String): Boolean {
        val body = JSONObject().put("email",email.trim()).put("password",password)
        val data = query("/auth/v1/signup","POST",body,false)
        val j = JSONObject(data)
        return if (j.optString("access_token").isNotBlank()) {
            acceptSession(data); true
        } else false
    }
    suspend fun signOut() {
        try { query("/auth/v1/logout","POST",JSONObject()) } catch (_: Exception) { }
        clearLocalSession()
    }
    suspend fun myProfile(): ChatProfile? {
        ensureSession()
        val uid = session?.uid ?: throw ApiProblem("Sign in first.")
        val array = JSONArray(query("/rest/v1/profiles?select=id,username,display_name,discoverable&id=eq." + uid + "&limit=1"))
        return if (array.length() == 0) null else array.getJSONObject(0).toProfile()
    }
    suspend fun createProfile(username: String, name: String) {
        val handle = username.trim().lowercase()
        require(ConfigRules.validUsername(handle)) { "Username: 3–20 lowercase letters, digits, _" }
        require(name.trim().length in 2..50) { "Your display name must be 2–50 characters" }
        val uid = session?.uid ?: throw ApiProblem("Sign in first.")
        val data = JSONObject().put("id",uid).put("username",handle)
            .put("display_name",name.trim()).put("discoverable",true)
        query("/rest/v1/profiles","POST",data)
    }
    suspend fun updateDiscovery(discoverable: Boolean) {
        val uid = session?.uid ?: throw ApiProblem("Sign in first.")
        query("/rest/v1/profiles?id=eq." + uid,"PATCH",JSONObject().put("discoverable",discoverable))
    }
    suspend fun conversations(): List<ChatItem> {
        ensureSession()
        val uid = session?.uid ?: throw ApiProblem("Sign in first.")
        val data = JSONArray(query("/rest/v1/conversations?select=id,user_low,user_high,requested_by,status,last_preview,updated_at&or=(user_low.eq." +
            uid + ",user_high.eq." + uid + ")&order=updated_at.desc&limit=75"))
        val peerIds = (0 until data.length()).map { index ->
            val row = data.getJSONObject(index)
            if (row.getString("user_low") == uid) row.getString("user_high") else row.getString("user_low")
        }.distinct()
        val people = mutableMapOf<String,String>()
        if (peerIds.isNotEmpty()) {
            val list = JSONArray(query("/rest/v1/profiles?select=id,display_name&id=in.(" + peerIds.joinToString(",") + ")"))
            for (i in 0 until list.length()) {
                val obj = list.getJSONObject(i)
                people[obj.getString("id")] = obj.optString("display_name")
            }
        }
        return (0 until data.length()).map { i ->
            val obj = data.getJSONObject(i)
            val peer = if (obj.getString("user_low") == uid) obj.getString("user_high") else obj.getString("user_low")
            ChatItem(obj.getString("id"),peer, people[peer] ?: "Member",obj.getString("status"),
                obj.getString("requested_by"),obj.optString("last_preview"),obj.optString("updated_at"))
        }
    }
    suspend fun requestChat(username: String): String {
        val handle = username.trim().lowercase()
        require(ConfigRules.validUsername(handle)) { "Enter a valid MsgU username" }
        val reply = query("/rest/v1/rpc/start_chat","POST",JSONObject().put("peer_username",handle))
        return reply.trim().removeSurrounding("\"")
    }
    suspend fun respondToRequest(chatId: String, accept: Boolean) {
        val answer = if (accept) "accepted" else "declined"
        val result = JSONArray(query("/rest/v1/conversations?id=eq." + chatId + "&status=eq.pending",
            "PATCH",JSONObject().put("status",answer)))
        if (result.length() == 0) throw ApiProblem("Request unavailable or already answered.")
    }
    suspend fun messages(chatId: String): List<ChatText> {
        ensureSession()
        val array = JSONArray(query("/rest/v1/messages?select=id,sender_id,body,created_at&conversation_id=eq." +
            chatId + "&order=created_at.desc&limit=100"))
        return (array.length()-1 downTo 0).map { i ->
            val item = array.getJSONObject(i)
            ChatText(item.getString("id"),item.getString("sender_id"),
                item.getString("body"),item.optString("created_at"))
        }
    }
    suspend fun send(chatId: String, text: String, nonce: String) {
        val uid = session?.uid ?: throw ApiProblem("Sign in first.")
        val body = text.trim()
        require(body.length in 1..3000) { "Message must contain 1–3000 characters" }
        val data = JSONObject().put("conversation_id",chatId).put("sender_id",uid)
            .put("body",body).put("client_nonce",nonce)
        try {
            query("/rest/v1/messages","POST",data)
        } catch (e: ApiProblem) {
            // A lost HTTP response can occur after a successful server insert.
            // The stable client nonce prevents duplicate messages on explicit retry.
            if (e.message?.contains("duplicate",ignoreCase=true) != true &&
                e.message?.contains("409") != true) throw e
            val existing = JSONArray(query("/rest/v1/messages?select=id&conversation_id=eq." +
                chatId + "&client_nonce=eq." + nonce + "&limit=1"))
            if (existing.length() == 0) throw e
        }
    }
    suspend fun block(peerId: String) {
        val uid = session?.uid ?: throw ApiProblem("Sign in first.")
        query("/rest/v1/blocks","POST",JSONObject().put("blocker",uid).put("blocked",peerId))
    }

    fun openChatSocket(chatId: String, onEvent: () -> Unit): WebSocket? {
        val conf = config
        val access = session?.access ?: return null
        if (!ConfigRules.validUrl(conf.url)) return null
        val wsEndpoint = conf.url.replaceFirst("https://","wss://") +
            "/realtime/v1/websocket?apikey=" + conf.publicKey + "&vsn=1.0.0"
        val request = Request.Builder().url(wsEndpoint)
            .header("apikey",conf.publicKey)
            .header("Authorization","Bearer " + access).build()
        return http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val changes = JSONObject().put("event","INSERT").put("schema","public")
                    .put("table","messages").put("filter","conversation_id=eq." + chatId)
                val payload = JSONObject().put("config",
                    JSONObject().put("postgres_changes",JSONArray().put(changes)))
                    .put("access_token",access)
                val join = JSONObject().put("topic","realtime:msgu-" + chatId)
                    .put("event","phx_join").put("payload",payload).put("ref","1")
                webSocket.send(join.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    if (JSONObject(text).optString("event") == "postgres_changes") onEvent()
                } catch (_: Exception) { }
            }
            // Foreground REST refresh below remains the fallback on WS failures.
        })
    }
    private fun JSONObject.toProfile() = ChatProfile(
        getString("id"),getString("username"),getString("display_name"),optBoolean("discoverable")
    )
}
