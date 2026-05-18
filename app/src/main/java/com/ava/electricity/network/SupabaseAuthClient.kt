package com.ava.electricity.network

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SupabaseAuthClient {
    private const val SUPABASE_URL = "https://lkynggdnjxiksotguakm.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_JPmI2gY1b3gtF03zGxGTmA_eGxRafgB"

    fun createAccount(email: String, password: String): String {
        ensureConfigured()
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)

        request(path = "/auth/v1/signup", method = "POST", payload = payload)
        return "Account created. Please check your email to confirm it."
    }

    fun signIn(email: String, password: String): String {
        ensureConfigured()
        val cleanEmail = email.trim().lowercase()
        val payload = JSONObject()
            .put("email", cleanEmail)
            .put("password", password)

        val response = request(path = "/auth/v1/token?grant_type=password", method = "POST", payload = payload)
        val json = JSONObject(response)
        val userEmail = json.optJSONObject("user")?.optString("email").orEmpty()
        return userEmail.ifBlank { cleanEmail }
    }

    private fun ensureConfigured() {
        if (SUPABASE_ANON_KEY == "PASTE_SUPABASE_ANON_KEY_HERE") {
            throw IllegalStateException("Supabase anon key is not configured yet.")
        }
    }

    private fun request(path: String, method: String, payload: JSONObject): String {
        val connection = (URL("$SUPABASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
        connection.disconnect()

        if (code !in 200..299) {
            val message = runCatching { JSONObject(response).optString("msg") }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: runCatching { JSONObject(response).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: response
            throw IllegalStateException(message.ifBlank { "Supabase request failed." })
        }

        return response
    }
}
