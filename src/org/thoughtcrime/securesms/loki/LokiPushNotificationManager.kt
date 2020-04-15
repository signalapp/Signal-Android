package org.thoughtcrime.securesms.loki

import android.content.Context
import okhttp3.*
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.IOException

object LokiPushNotificationManager {
    //const val server = "https://live.apns.getsession.org/"
    const val server = "https://dev.apns.getsession.org/"
    const val tokenExpirationInterval = 2 * 24 * 60 * 60
    private val connection = OkHttpClient()

    fun disableRemoteNotification(token: String, context: Context?) {
        val parameters = mapOf("token" to token)
        val url = "${server}register"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()
        connection.newCall(request).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                when (response.code()) {
                    200 -> {
                        val bodyAsString = response.body()!!.string()
                        val json = JsonUtil.fromJson(bodyAsString, Map::class.java)
                        val code  = json?.get("code") as? Int
                        if (code != null && code != 0) {
                            TextSecurePreferences.setIsUsingRemoteNotification(context, false)
                        } else {
                            Log.d("Loki", "Couldn't disable remote notification due to error: ${json?.get("message") as? String}.")
                        }
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                Log.d("Loki", "Couldn't disable remote notification.")
            }
        })
    }

    @JvmStatic
    fun register(token: String, hexEncodedPublicKey: String, context: Context?) {
        val parameters = mapOf("token" to token, "pubKey" to hexEncodedPublicKey)
        val url = "${server}register"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()
        connection.newCall(request).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                when (response.code()) {
                    200 -> {
                        val bodyAsString = response.body()!!.string()
                        val json = JsonUtil.fromJson(bodyAsString, Map::class.java)
                        val code  = json?.get("code") as? Int
                        if (code != null && code != 0) {
                            TextSecurePreferences.setIsUsingRemoteNotification(context, true)
                        } else {
                            Log.d("Loki", "Couldn't register device token due to error: ${json?.get("message") as? String}.")
                        }
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                Log.d("Loki", "Couldn't register device token.")
            }
        })
    }

    fun acknowledgeDeliveryForMessageWith(hash: String, expiration: Int, hexEncodedPublicKey: String, context: Context?) {
        val parameters = mapOf("hash" to hash, "pubKey" to hexEncodedPublicKey, "expiration" to expiration)
        val url = "${server}acknowledge_message_delivery"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()
        connection.newCall(request).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                when (response.code()) {
                    200 -> {
                        val bodyAsString = response.body()!!.string()
                        val json = JsonUtil.fromJson(bodyAsString, Map::class.java)
                        val code  = json?.get("code") as? Int
                        if (code == null || code == 0) {
                            Log.d("Loki", "Couldn't acknowledge the delivery for message due to error: ${json?.get("message") as? String}.")
                        }
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                Log.d("Loki", "Couldn't acknowledge the delivery for message with last hash: ${hash}")
            }
        })
    }
}
