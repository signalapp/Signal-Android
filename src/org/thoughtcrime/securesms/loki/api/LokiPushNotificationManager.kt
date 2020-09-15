package org.thoughtcrime.securesms.loki.api

import android.content.Context
import okhttp3.*
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobmanager.Data
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.loki.protocol.ClosedGroupUpdateMessageSendJob
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.loki.api.PushNotificationAcknowledgement
import java.io.IOException
import java.lang.Exception
import java.util.concurrent.TimeUnit

object LokiPushNotificationManager {
    private val connection = OkHttpClient()
    private val tokenExpirationInterval = 12 * 60 * 60 * 1000

    private val server by lazy {
        PushNotificationAcknowledgement.shared.server
    }

    enum class ClosedGroupOperation {
        Subscribe, Unsubscribe;

        val rawValue: String
            get() {
                return when (this) {
                    Subscribe -> "subscribe_closed_group"
                    Unsubscribe -> "unsubscribe_closed_group"
                }
            }
    }

    @JvmStatic
    fun unregister(token: String, context: Context) {
        val parameters = mapOf( "token" to token )
        val url = "$server/register"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()
        connection.newCall(request).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                when (response.code()) {
                    200 -> {
                        val bodyAsString = response.body()!!.string()
                        val json = JsonUtil.fromJson(bodyAsString, Map::class.java)
                        val code = json?.get("code") as? Int
                        if (code != null && code != 0) {
                            TextSecurePreferences.setIsUsingFCM(context, false)
                        } else {
                            Log.d("Loki", "Couldn't disable FCM due to error: ${json?.get("message") as? String ?: "null"}.")
                        }
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                Log.d("Loki", "Couldn't disable FCM.")
            }
        })
        // Unsubscribe from all closed groups
        val allClosedGroupPublicKeys = DatabaseFactory.getSSKDatabase(context).getAllClosedGroupPublicKeys()
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        allClosedGroupPublicKeys.forEach { closedGroup ->
            performOperation(context, ClosedGroupOperation.Unsubscribe, closedGroup, userPublicKey)
        }
    }

    @JvmStatic
    fun register(token: String, publicKey: String, context: Context, force: Boolean) {
        val oldToken = TextSecurePreferences.getFCMToken(context)
        val lastUploadDate = TextSecurePreferences.getLastFCMUploadTime(context)
        if (!force && token == oldToken && System.currentTimeMillis() - lastUploadDate < tokenExpirationInterval) { return }
        val parameters = mapOf( "token" to token, "pubKey" to publicKey )
        val url = "$server/register"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()
        connection.newCall(request).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                when (response.code()) {
                    200 -> {
                        val bodyAsString = response.body()!!.string()
                        val json = JsonUtil.fromJson(bodyAsString, Map::class.java)
                        val code = json?.get("code") as? Int
                        if (code != null && code != 0) {
                            TextSecurePreferences.setIsUsingFCM(context, true)
                            TextSecurePreferences.setFCMToken(context, token)
                            TextSecurePreferences.setLastFCMUploadTime(context, System.currentTimeMillis())
                        } else {
                            Log.d("Loki", "Couldn't register for FCM due to error: ${json?.get("message") as? String ?: "null"}.")
                        }
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                Log.d("Loki", "Couldn't register for FCM.")
            }
        })
        // Subscribe to all closed groups
        val allClosedGroupPublicKeys = DatabaseFactory.getSSKDatabase(context).getAllClosedGroupPublicKeys()
        allClosedGroupPublicKeys.forEach { closedGroup ->
            performOperation(context, ClosedGroupOperation.Subscribe, closedGroup, publicKey)
        }
    }

    @JvmStatic
    fun performOperation(context: Context, operation: ClosedGroupOperation, closedGroupPublicKey: String, publicKey: String) {
        if (!TextSecurePreferences.isUsingFCM(context)) { return }
        val parameters = mapOf( "closedGroupPublicKey" to closedGroupPublicKey, "pubKey" to publicKey )
        val url = "$server/${operation.rawValue}"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()
        connection.newCall(request).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                when (response.code()) {
                    200 -> {
                        val bodyAsString = response.body()!!.string()
                        val json = JsonUtil.fromJson(bodyAsString, Map::class.java)
                        val code = json?.get("code") as? Int
                        if (code == null || code == 0) {
                            Log.d("Loki", "Couldn't subscribe/unsubscribe to/from PNs for closed group with ID: $closedGroupPublicKey due to error: ${json?.get("message") as? String ?: "null"}.")
                        }
                    }
                    else -> {
                        Log.d("Loki", "Couldn't subscribe/unsubscribe to/from PNs for closed group with ID: $closedGroupPublicKey.")
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                Log.d("Loki", "Couldn't subscribe/unsubscribe to/from PNs for closed group with ID: $closedGroupPublicKey due to error: $exception.")
            }
        })
    }
}
