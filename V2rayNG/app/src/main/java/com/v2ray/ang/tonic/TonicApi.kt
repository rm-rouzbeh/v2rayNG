package com.v2ray.ang.tonic

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** One subscription entry as delivered by the backend. */
data class TonicSub(
    @SerializedName("name") val name: String = "",
    @SerializedName("url") val url: String = ""
)

data class TonicLoginResponse(
    @SerializedName("token") val token: String = "",
    @SerializedName("subscriptions") val subscriptions: List<TonicSub> = emptyList()
)

data class TonicSubsResponse(
    @SerializedName("subscriptions") val subscriptions: List<TonicSub> = emptyList()
)

/** Result wrapper for backend calls. */
sealed class TonicResult<out T> {
    data class Success<T>(val data: T) : TonicResult<T>()
    data class Error(val code: Int, val message: String) : TonicResult<Nothing>()
}

/**
 * Thin OkHttp + Gson client for the TONIC backend. The base URL comes from the
 * brand config (BuildConfig.BACKEND_URL). All calls are blocking and must be run
 * off the main thread.
 */
object TonicApi {
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun baseUrl() = BuildConfig.BACKEND_URL.trimEnd('/')

    /** Authenticate with username/password. On success returns token + subscriptions. */
    fun login(username: String, password: String): TonicResult<TonicLoginResponse> {
        return try {
            val bodyJson = gson.toJson(mapOf("username" to username, "password" to password))
            val req = Request.Builder()
                .url("${baseUrl()}/api/login")
                .post(bodyJson.toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val parsed = gson.fromJson(text, TonicLoginResponse::class.java)
                    if (parsed == null || parsed.token.isEmpty()) {
                        TonicResult.Error(resp.code, "empty token")
                    } else {
                        TonicResult.Success(parsed)
                    }
                } else {
                    TonicResult.Error(resp.code, text)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "TonicApi.login failed", e)
            TonicResult.Error(-1, e.message ?: "network error")
        }
    }

    /** Fetch the up-to-date subscription list for the logged-in user. */
    fun fetchSubscriptions(token: String): TonicResult<TonicSubsResponse> {
        return try {
            val req = Request.Builder()
                .url("${baseUrl()}/api/subscriptions")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val parsed = gson.fromJson(text, TonicSubsResponse::class.java) ?: TonicSubsResponse()
                    TonicResult.Success(parsed)
                } else {
                    TonicResult.Error(resp.code, text)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "TonicApi.fetchSubscriptions failed", e)
            TonicResult.Error(-1, e.message ?: "network error")
        }
    }
}
