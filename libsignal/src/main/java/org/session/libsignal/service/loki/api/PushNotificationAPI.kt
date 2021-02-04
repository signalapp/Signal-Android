package org.session.libsignal.service.loki.api

import nl.komponents.kovenant.functional.map
import okhttp3.*
import org.session.libsignal.utilities.logging.Log
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.service.loki.api.onionrequests.OnionRequestAPI
import org.session.libsignal.service.loki.utilities.retryIfNeeded

public class PushNotificationAPI private constructor(public val server: String) {

    companion object {
        private val maxRetryCount = 4
        public val pnServerPublicKey = "642a6585919742e5a2d4dc51244964fbcd8bcab2b75612407de58b810740d049"

        lateinit var shared: PushNotificationAPI

        public fun configureIfNeeded(isDebugMode: Boolean) {
            if (::shared.isInitialized) { return; }
            val server = if (isDebugMode) "https://live.apns.getsession.org" else "https://live.apns.getsession.org"
            shared = PushNotificationAPI(server)
        }
    }

    public fun notify(messageInfo: SignalMessageInfo) {
        val message = LokiMessage.from(messageInfo) ?: return
        val parameters = mapOf( "data" to message.data, "send_to" to message.recipientPublicKey )
        val url = "${server}/notify"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body)
        retryIfNeeded(maxRetryCount) {
            OnionRequestAPI.sendOnionRequest(request.build(), server, PushNotificationAPI.pnServerPublicKey, "/loki/v2/lsrpc").map { json ->
                val code = json["code"] as? Int
                if (code == null || code == 0) {
                    Log.d("Loki", "[Loki] Couldn't notify PN server due to error: ${json["message"] as? String ?: "null"}.")
                }
            }.fail { exception ->
                Log.d("Loki", "[Loki] Couldn't notify PN server due to error: $exception.")
            }
        }
    }
}
