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

    private val server by lazy {
        PushNotificationAcknowledgement.shared.server
    }

    public const val subscribe = "subscribe_closed_group"
    public const val unsubscribe = "unsubscribe_closed_group"

    private const val tokenExpirationInterval = 12 * 60 * 60 * 1000

    @JvmStatic
    fun unregister(token: String, context: Context?) {
        val parameters = mapOf("token" to token)
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

        for (closedGroup: String in DatabaseFactory.getSSKDatabase(context).getAllClosedGroupPublicKeys()) {
            operateClosedGroup(closedGroup, TextSecurePreferences.getLocalNumber(context), context, unsubscribe)
        }
    }

    @JvmStatic
    fun register(token: String, publicKey: String, context: Context?, force: Boolean) {
        val oldToken = TextSecurePreferences.getFCMToken(context)
        val lastUploadDate = TextSecurePreferences.getLastFCMUploadTime(context)
        if (!force && token == oldToken && System.currentTimeMillis() - lastUploadDate < tokenExpirationInterval) { return }
        val parameters = mapOf("token" to token, "pubKey" to publicKey)
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

        for (closedGroup: String in DatabaseFactory.getSSKDatabase(context).getAllClosedGroupPublicKeys()) {
            operateClosedGroup(closedGroup, publicKey, context, subscribe)
        }

    }

    @JvmStatic
    fun operateClosedGroup(closedGroupPublicKey: String, publicKey: String, context: Context?, operation: String) {
        Log.d("Loki", "Start to notify PN server of closed group.")
        if (!TextSecurePreferences.isUsingFCM(context)) { return }
        val parameters = mapOf("closedGroupPublicKey" to closedGroupPublicKey, "pubKey" to publicKey)
        val url = "$server/$operation"
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
                            Log.d("Loki", "Couldn't subscribe/unsubscribe $closedGroupPublicKey due to error: ${json?.get("message") as? String ?: "null"}.")
                        } else {
                            Log.d("Loki", "Subscribe/unsubscribe success.")
                        }
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                Log.d("Loki", "Couldn't subscribe/unsubscribe $closedGroupPublicKey.")
            }
        })
    }
}

class ClosedGroupSubscribeJob private constructor(parameters: Parameters, private val closedGroupPublicKey: String) : BaseJob(parameters) {

    companion object {
        const val KEY = "ClosedGroupSubscribeJob"
    }

    constructor(closedGroupPublicKey: String) : this(Parameters.Builder()
            .addConstraint(NetworkConstraint.KEY)
            .setQueue(KEY)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .setMaxAttempts(1)
            .build(),
            closedGroupPublicKey)

    override fun serialize(): Data {
        val builder = Data.Builder()
        builder.putString("closedGroupPublicKey", closedGroupPublicKey)
        return builder.build()
    }

    override fun getFactoryKey(): String { return KEY }

    override fun onCanceled() { }

    public override fun onRun() {
        LokiPushNotificationManager.operateClosedGroup(closedGroupPublicKey, TextSecurePreferences.getLocalNumber(context), context, LokiPushNotificationManager.subscribe)
    }

    override fun onShouldRetry(e: Exception): Boolean { return false }
}
