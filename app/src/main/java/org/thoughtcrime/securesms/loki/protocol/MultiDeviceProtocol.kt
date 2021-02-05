package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import org.session.libsession.messaging.messages.control.ConfigurationMessage
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.service.api.push.SignalServiceAddress
import org.session.libsignal.utilities.logging.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.loki.utilities.recipient
import java.util.*

object MultiDeviceProtocol {

    @JvmStatic
    fun syncConfigurationIfNeeded(context: Context) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val lastSyncTime = TextSecurePreferences.getLastConfigurationSyncTime(context)
        val now = System.currentTimeMillis()
        if (now - lastSyncTime < 2 * 24 * 60 * 60 * 1000) return
        val configurationMessage = ConfigurationMessage.getCurrent()
        val serializedMessage = configurationMessage.toProto()!!.toByteArray()
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(userPublicKey)
        val recipient = recipient(context, userPublicKey)
        val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient)
        try {
            messageSender.sendMessage(0, address, udAccess.get().targetUnidentifiedAccess,
                    Date().time, serializedMessage, false, configurationMessage.ttl.toInt(), false,
                    true, false, true, false)
            TextSecurePreferences.setLastConfigurationSyncTime(context, now)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send configuration message due to error: $e.")
        }
    }

    fun forceSyncConfigurationNowIfNeeded(context: Context) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
        val configurationMessage = ConfigurationMessage.getCurrent()
        val serializedMessage = configurationMessage.toProto()!!.toByteArray()
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(userPublicKey)
        val recipient = recipient(context, userPublicKey)
        val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient)
        try {
            messageSender.sendMessage(0, address, udAccess.get().targetUnidentifiedAccess,
                    Date().time, serializedMessage, false, configurationMessage.ttl.toInt(), false,
                    true, false, true, false)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send configuration message due to error: $e.")
        }
    }
}