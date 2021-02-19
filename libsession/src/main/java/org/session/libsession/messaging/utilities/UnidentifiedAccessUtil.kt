package org.session.libsession.messaging.utilities

import android.content.Context
import com.goterl.lazycode.lazysodium.LazySodiumAndroid
import com.goterl.lazycode.lazysodium.SodiumAndroid
import org.session.libsession.messaging.MessagingConfiguration
import org.session.libsession.utilities.TextSecurePreferences.isUniversalUnidentifiedAccess
import org.session.libsession.utilities.Util.getSecretBytes
import org.session.libsignal.metadata.SignalProtos
import org.session.libsignal.service.api.crypto.UnidentifiedAccess
import org.session.libsignal.utilities.logging.Log

object UnidentifiedAccessUtil {
    private val TAG = UnidentifiedAccessUtil::class.simpleName
    private val sodium by lazy { LazySodiumAndroid(SodiumAndroid()) }

    fun getAccessFor(recipientPublicKey: String): UnidentifiedAccess? {
        val theirUnidentifiedAccessKey = getTargetUnidentifiedAccessKey(recipientPublicKey)
        val ourUnidentifiedAccessKey = getSelfUnidentifiedAccessKey()
        val ourUnidentifiedAccessCertificate = getUnidentifiedAccessCertificate()

        Log.i(TAG, "Their access key present? " + (theirUnidentifiedAccessKey != null) +
                " | Our access key present? " + (ourUnidentifiedAccessKey != null) +
                " | Our certificate present? " + (ourUnidentifiedAccessCertificate != null))

        return if (theirUnidentifiedAccessKey != null && ourUnidentifiedAccessKey != null && ourUnidentifiedAccessCertificate != null) {
           UnidentifiedAccess(theirUnidentifiedAccessKey)
        } else null
    }

    fun getAccessForSync(context: Context): UnidentifiedAccess? {
        var ourUnidentifiedAccessKey = getSelfUnidentifiedAccessKey()
        val ourUnidentifiedAccessCertificate = getUnidentifiedAccessCertificate()
        if (isUniversalUnidentifiedAccess(context)) {
            ourUnidentifiedAccessKey = getSecretBytes(16)
        }
        return if (ourUnidentifiedAccessKey != null && ourUnidentifiedAccessCertificate != null) {
            UnidentifiedAccess(ourUnidentifiedAccessKey)
        } else null
    }

    private fun getTargetUnidentifiedAccessKey(recipientPublicKey: String): ByteArray? {
        val theirProfileKey = MessagingConfiguration.shared.storage.getProfileKeyForRecipient(recipientPublicKey) ?: return sodium.randomBytesBuf(16)
        return UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey)
    }

    private fun getSelfUnidentifiedAccessKey(): ByteArray? {
        val userPublicKey = MessagingConfiguration.shared.storage.getUserPublicKey()
        if (userPublicKey != null) {
            return sodium.randomBytesBuf(16)
        }
        return null
    }

    private fun getUnidentifiedAccessCertificate(): ByteArray? {
        val userPublicKey = MessagingConfiguration.shared.storage.getUserPublicKey()
        if (userPublicKey != null) {
            val certificate = SignalProtos.SenderCertificate.newBuilder().setSender(userPublicKey).setSenderDevice(1).build()
            return certificate.toByteArray()
        }
        return null
    }
}