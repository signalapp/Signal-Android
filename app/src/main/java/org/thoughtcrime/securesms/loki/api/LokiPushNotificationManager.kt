package org.thoughtcrime.securesms.loki.api

import android.content.Context
import nl.komponents.kovenant.functional.map
import okhttp3.*
import org.session.libsession.messaging.sending_receiving.notifications.PushNotificationAPI
import org.session.libsession.snode.OnionRequestAPI
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.retryIfNeeded

object LokiPushNotificationManager {
    private val maxRetryCount = 4
    private val tokenExpirationInterval = 12 * 60 * 60 * 1000

    private val server by lazy {
        PushNotificationAPI.server
    }
    private val pnServerPublicKey by lazy {
        PushNotificationAPI.serverPublicKey
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
        val url = "$server/unregister"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body)
        retryIfNeeded(maxRetryCount) {
            OnionRequestAPI.sendOnionRequest(request.build(), server, pnServerPublicKey, "/loki/v2/lsrpc").map { json ->
                val code = json["code"] as? Int
                if (code != null && code != 0) {
                    TextSecurePreferences.setIsUsingFCM(context, false)
                } else {
                    Log.d("Loki", "Couldn't disable FCM due to error: ${json["message"] as? String ?: "null"}.")
                }
            }.fail { exception ->
                Log.d("Loki", "Couldn't disable FCM due to error: ${exception}.")
            }
        }
        // Unsubscribe from all closed groups
        val allClosedGroupPublicKeys = DatabaseFactory.getLokiAPIDatabase(context).getAllClosedGroupPublicKeys()
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
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
        val request = Request.Builder().url(url).post(body)
        retryIfNeeded(maxRetryCount) {
            OnionRequestAPI.sendOnionRequest(request.build(), server, pnServerPublicKey, "/loki/v2/lsrpc").map { json ->
                val code = json["code"] as? Int
                if (code != null && code != 0) {
                    TextSecurePreferences.setIsUsingFCM(context, true)
                    TextSecurePreferences.setFCMToken(context, token)
                    TextSecurePreferences.setLastFCMUploadTime(context, System.currentTimeMillis())
                } else {
                    Log.d("Loki", "Couldn't register for FCM due to error: ${json["message"] as? String ?: "null"}.")
                }
            }.fail { exception ->
                Log.d("Loki", "Couldn't register for FCM due to error: ${exception}.")
            }
        }
        // Subscribe to all closed groups
        val allClosedGroupPublicKeys = DatabaseFactory.getLokiAPIDatabase(context).getAllClosedGroupPublicKeys()
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
        val request = Request.Builder().url(url).post(body)
        retryIfNeeded(maxRetryCount) {
            OnionRequestAPI.sendOnionRequest(request.build(), server, pnServerPublicKey, "/loki/v2/lsrpc").map { json ->
                val code = json["code"] as? Int
                if (code == null || code == 0) {
                    Log.d("Loki", "Couldn't subscribe/unsubscribe closed group: $closedGroupPublicKey due to error: ${json["message"] as? String ?: "null"}.")
                }
            }.fail { exception ->
                Log.d("Loki", "Couldn't subscribe/unsubscribe closed group: $closedGroupPublicKey due to error: ${exception}.")
            }
        }
    }
}
