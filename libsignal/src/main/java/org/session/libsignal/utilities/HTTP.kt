package org.session.libsignal.utilities

import okhttp3.*
import java.lang.IllegalStateException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object HTTP {

    private val seedNodeConnection by lazy {
        OkHttpClient().newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()
    }

    private val defaultConnection by lazy {
        // Snode to snode communication uses self-signed certificates but clients can safely ignore this
        val trustManager = object : X509TrustManager {

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authorizationType: String?) { }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authorizationType: String?) { }
            override fun getAcceptedIssuers(): Array<X509Certificate> { return arrayOf() }
        }
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, arrayOf( trustManager ), SecureRandom())
        OkHttpClient().newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()
    }

    private fun getDefaultConnection(timeout: Long): OkHttpClient {
        // Snode to snode communication uses self-signed certificates but clients can safely ignore this
        val trustManager = object : X509TrustManager {

            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authorizationType: String?) { }
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authorizationType: String?) { }
            override fun getAcceptedIssuers(): Array<X509Certificate> { return arrayOf() }
        }
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, arrayOf( trustManager ), SecureRandom())
        return OkHttpClient().newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .build()
    }

    private const val timeout: Long = 10

    class HTTPRequestFailedException(val statusCode: Int, val json: Map<*, *>?)
        : kotlin.Exception("HTTP request failed with status code $statusCode.")

    enum class Verb(val rawValue: String) {
        GET("GET"), PUT("PUT"), POST("POST"), DELETE("DELETE")
    }

    /**
     * Sync. Don't call from the main thread.
     */
    fun execute(verb: Verb, url: String, timeout: Long = HTTP.timeout, useSeedNodeConnection: Boolean = false): Map<*, *> {
        return execute(verb = verb, url = url, body = null, timeout = timeout, useSeedNodeConnection = useSeedNodeConnection)
    }

    /**
     * Sync. Don't call from the main thread.
     */
    fun execute(verb: Verb, url: String, parameters: Map<String, Any>?, timeout: Long = HTTP.timeout, useSeedNodeConnection: Boolean = false): Map<*, *> {
        if (parameters != null) {
            val body = JsonUtil.toJson(parameters).toByteArray()
            return execute(verb = verb, url = url, body = body, timeout = timeout, useSeedNodeConnection = useSeedNodeConnection)
        } else {
            return execute(verb = verb, url = url, body = null, timeout = timeout, useSeedNodeConnection = useSeedNodeConnection)
        }
    }

    /**
     * Sync. Don't call from the main thread.
     */
    fun execute(verb: Verb, url: String, body: ByteArray?, timeout: Long = HTTP.timeout, useSeedNodeConnection: Boolean = false): Map<*, *> {
        val request = Request.Builder().url(url)
        when (verb) {
            Verb.GET -> request.get()
            Verb.PUT, Verb.POST -> {
                if (body == null) { throw Exception("Invalid request body.") }
                val contentType = MediaType.get("application/json; charset=utf-8")
                @Suppress("NAME_SHADOWING") val body = RequestBody.create(contentType, body)
                if (verb == Verb.PUT) request.put(body) else request.post(body)
            }
            Verb.DELETE -> request.delete()
        }
        lateinit var response: Response
        try {
            val connection: OkHttpClient
            if (timeout != HTTP.timeout) { // Custom timeout
                if (useSeedNodeConnection) {
                    throw IllegalStateException("Setting a custom timeout is only allowed for requests to snodes.")
                }
                connection = getDefaultConnection(timeout)
            } else {
                connection = if (useSeedNodeConnection) seedNodeConnection else defaultConnection
            }
            response = connection.newCall(request.build()).execute()
        } catch (exception: Exception) {
            Log.d("Loki", "${verb.rawValue} request to $url failed due to error: ${exception.localizedMessage}.")
            // Override the actual error so that we can correctly catch failed requests in OnionRequestAPI
            throw HTTPRequestFailedException(0, null)
        }
        when (val statusCode = response.code()) {
            200 -> {
                val bodyAsString = response.body()?.string() ?: throw Exception("An error occurred.")
                try {
                    return JsonUtil.fromJson(bodyAsString, Map::class.java)
                } catch (exception: Exception) {
                    return mapOf( "result" to bodyAsString)
                }
            }
            else -> {
                Log.d("Loki", "${verb.rawValue} request to $url failed with status code: $statusCode.")
                throw HTTPRequestFailedException(statusCode, null)
            }
        }
    }
}
