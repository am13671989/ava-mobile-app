package com.ava.electricity.network

import com.ava.electricity.data.HistoryItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class BackendRecommendation(
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val estimatedCostEur: Double
)

object AvaBackendClient {
    private const val BASE_URL = "https://avaelectricitycost-production.up.railway.app"

    fun getTodayPrices(country: String): List<Double> {
        val encodedCountry = encode(country)
        val body = request("GET", "/api/prices/today?country=$encodedCountry")
        val array = JSONArray(body)
        return List(array.length()) { index ->
            array.getJSONObject(index).getDouble("price_eur_kwh")
        }
    }

    fun getFavorites(nickname: String): Set<Int> {
        val body = request("GET", "/api/users/${encode(nickname)}/favorites")
        val array = JSONArray(body)
        return List(array.length()) { index -> array.getInt(index) }.toSet()
    }

    fun saveFavorites(nickname: String, favoriteIds: Set<Int>) {
        val ids = JSONArray()
        favoriteIds.forEach { ids.put(it) }
        val payload = JSONObject().put("device_ids", ids)
        request("PUT", "/api/users/${encode(nickname)}/favorites", payload.toString())
    }

    fun getHistory(nickname: String): List<HistoryItem> {
        val body = request("GET", "/api/users/${encode(nickname)}/history")
        val array = JSONArray(body)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val start = item.optString("start_time")
            val end = item.optString("end_time")
            HistoryItem(
                deviceName = item.optString("device_name"),
                timeSchedule = "$start - $end",
                price = item.optDouble("estimated_cost_eur")
            )
        }
    }

    fun saveHistory(
        nickname: String,
        deviceId: Int?,
        deviceName: String,
        startTime: String,
        endTime: String,
        durationMinutes: Int,
        estimatedCostEur: Double
    ) {
        val payload = JSONObject()
            .put("device_id", deviceId)
            .put("device_name", deviceName)
            .put("start_time", startTime)
            .put("end_time", endTime)
            .put("duration_minutes", durationMinutes)
            .put("estimated_cost_eur", estimatedCostEur)
        request("POST", "/api/users/${encode(nickname)}/history", payload.toString())
    }

    fun bestTime(
        country: String,
        deviceId: Int,
        deviceName: String,
        powerKwh: Double,
        durationMinutes: Int,
        earliestStartMinute: Int
    ): BackendRecommendation {
        val payload = JSONObject()
            .put("country", country)
            .put("device_id", deviceId)
            .put("device_name", deviceName)
            .put("power_kwh", powerKwh)
            .put("duration_minutes", durationMinutes)
            .put("earliest_start_minute", earliestStartMinute)
        val body = request("POST", "/api/recommendations/best-time", payload.toString())
        val item = JSONObject(body)
        return BackendRecommendation(
            startTime = item.getString("start_time"),
            endTime = item.getString("end_time"),
            durationMinutes = item.getInt("duration_minutes"),
            estimatedCostEur = item.getDouble("estimated_cost_eur")
        )
    }

    private fun request(method: String, path: String, payload: String? = null): String {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        if (payload != null) {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
            }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
        connection.disconnect()

        if (code !in 200..299) {
            throw IllegalStateException("Backend request failed ($code): $response")
        }
        return response
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
