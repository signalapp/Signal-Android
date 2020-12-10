package org.session.libsession.messaging.utilities

import com.goterl.lazycode.lazysodium.LazySodiumAndroid
import com.goterl.lazycode.lazysodium.SodiumAndroid

import org.session.libsession.messaging.MessagingConfiguration

import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.metadata.SignalProtos
import org.session.libsignal.metadata.certificate.InvalidCertificateException
import org.session.libsignal.service.api.crypto.UnidentifiedAccess
import org.session.libsignal.service.api.crypto.UnidentifiedAccessPair

object UnidentifiedAccessUtil {
    private val TAG = UnidentifiedAccessUtil::class.simpleName
    private val sodium = LazySodiumAndroid(SodiumAndroid())

    fun getAccessFor(recipientPublicKey: String): UnidentifiedAccessPair? {
        try {
            val theirUnidentifiedAccessKey = getTargetUnidentifiedAccessKey(recipientPublicKey)
            val ourUnidentifiedAccessKey = getSelfUnidentifiedAccessKey()
            val ourUnidentifiedAccessCertificate = getUnidentifiedAccessCertificate()

            Log.i(TAG, "Their access key present? " + (theirUnidentifiedAccessKey != null) +
                    " | Our access key present? " + (ourUnidentifiedAccessKey != null) +
                    " | Our certificate present? " + (ourUnidentifiedAccessCertificate != null))

            if (theirUnidentifiedAccessKey != null && ourUnidentifiedAccessKey != null && ourUnidentifiedAccessCertificate != null) {
                return UnidentifiedAccessPair(UnidentifiedAccess(theirUnidentifiedAccessKey, ourUnidentifiedAccessCertificate),
                        UnidentifiedAccess(ourUnidentifiedAccessKey, ourUnidentifiedAccessCertificate))
            }
            return null
        } catch (e: InvalidCertificateException) {
            Log.w(TAG, e)
            return null
        }
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