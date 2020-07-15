package org.thoughtcrime.securesms.loki.protocol

import android.content.Context
import android.util.Log
import nl.komponents.kovenant.Promise
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.PushMediaSendJob
import org.thoughtcrime.securesms.jobs.PushSendJob
import org.thoughtcrime.securesms.jobs.PushTextSendJob
import org.thoughtcrime.securesms.loki.utilities.Broadcaster
import org.thoughtcrime.securesms.loki.utilities.recipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.fileserver.FileServerAPI
import org.whispersystems.signalservice.loki.protocol.meta.SessionMetaProtocol
import org.whispersystems.signalservice.loki.protocol.multidevice.DeviceLink
import org.whispersystems.signalservice.loki.protocol.multidevice.DeviceLinkingSession
import org.whispersystems.signalservice.loki.protocol.multidevice.MultiDeviceProtocol
import org.whispersystems.signalservice.loki.utilities.retryIfNeeded

object MultiDeviceProtocol {

    enum class MessageType { Text, Media }

    @JvmStatic
    fun sendTextPush(context: Context, recipient: Recipient, messageID: Long, isEndSession: Boolean) {
        sendMessagePush(context, recipient, messageID, MessageType.Text, isEndSession)
    }

    @JvmStatic
    fun sendMediaPush(context: Context, recipient: Recipient, messageID: Long) {
        sendMessagePush(context, recipient, messageID, MessageType.Media, false)
    }

    private fun sendMessagePush(context: Context, recipient: Recipient, messageID: Long, messageType: MessageType, isEndSession: Boolean) {
        val jobManager = ApplicationContext.getInstance(context).jobManager
        val isMultiDeviceRequired = !recipient.address.isOpenGroup
        if (!isMultiDeviceRequired) {
            when (messageType) {
                MessageType.Text -> jobManager.add(PushTextSendJob(messageID, recipient.address))
                MessageType.Media -> PushMediaSendJob.enqueue(context, jobManager, messageID, recipient.address)
            }
        }
        val publicKey = recipient.address.serialize()
        FileServerAPI.shared.getDeviceLinks(publicKey).success {
            val devices = MultiDeviceProtocol.shared.getAllLinkedDevices(publicKey)
            val jobs = devices.map {
                when (messageType) {
                    MessageType.Text -> PushTextSendJob(messageID, messageID, recipient(context, it).address) as PushSendJob
                    MessageType.Media -> PushMediaSendJob(messageID, messageID, recipient(context, it).address) as PushSendJob
                }
            }
            @Suppress("UNCHECKED_CAST")
            when (messageType) {
                MessageType.Text -> jobManager.startChain(jobs).enqueue()
                MessageType.Media -> PushMediaSendJob.enqueue(context, jobManager, jobs as List<PushMediaSendJob>)
            }
        }.fail {
            // Proceed even if updating the recipient's device links failed, so that message sending
            // is independent of whether the file server is online
            when (messageType) {
                MessageType.Text -> jobManager.add(PushTextSendJob(messageID, recipient.address))
                MessageType.Media -> PushMediaSendJob.enqueue(context, jobManager, messageID, recipient.address)
            }
        }
    }

    fun sendDeviceLinkMessage(context: Context, publicKey: String, deviceLink: DeviceLink): Promise<Unit, Exception> {
        val messageSender = ApplicationContext.getInstance(context).communicationModule.provideSignalMessageSender()
        val address = SignalServiceAddress(publicKey)
        val message = SignalServiceDataMessage.newBuilder().withDeviceLink(deviceLink)
        // A request should include a pre key bundle. An authorization should be a normal message.
        if (deviceLink.type == DeviceLink.Type.REQUEST) {
            val preKeyBundle = DatabaseFactory.getLokiPreKeyBundleDatabase(context).generatePreKeyBundle(address.number)
            message.withPreKeyBundle(preKeyBundle)
        } else {
            // Include the user's profile key so that the slave device can get the user's profile picture
            message.withProfileKey(ProfileKeyUtil.getProfileKey(context))
        }
        return try {
            Log.d("Loki", "Sending device link message to: $publicKey.")
            val udAccess = UnidentifiedAccessUtil.getAccessFor(context, recipient(context, publicKey))
            val result = messageSender.sendMessage(0, address, udAccess, message.build())
            if (result.success == null) {
                val exception = when {
                    result.isNetworkFailure -> "Failed to send device link message due to a network error."
                    else -> "Failed to send device link message."
                }
                throw Exception(exception)
            }
            Promise.ofSuccess(Unit)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to send device link message to: $publicKey due to error: $e.")
            Promise.ofFail(e)
        }
    }

    fun signAndSendDeviceLinkMessage(context: Context, deviceLink: DeviceLink): Promise<Unit, Exception> {
        val userPrivateKey = IdentityKeyUtil.getIdentityKeyPair(context).privateKey.serialize()
        val signedDeviceLink = deviceLink.sign(DeviceLink.Type.AUTHORIZATION, userPrivateKey)
        if (signedDeviceLink == null || signedDeviceLink.type != DeviceLink.Type.AUTHORIZATION) {
            return Promise.ofFail(Exception("Failed to sign device link."))
        }
        return retryIfNeeded(8) {
            sendDeviceLinkMessage(context, deviceLink.slavePublicKey, signedDeviceLink)
        }
    }

    @JvmStatic
    fun handleDeviceLinkMessageIfNeeded(context: Context, deviceLink: DeviceLink, content: SignalServiceContent) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        if (deviceLink.type == DeviceLink.Type.REQUEST) {
            handleDeviceLinkRequestMessage(context, deviceLink, content)
        } else if (deviceLink.slavePublicKey == userPublicKey) {
            handleDeviceLinkAuthorizedMessage(context, deviceLink, content)
        }
    }

    private fun isValidDeviceLinkMessage(context: Context, deviceLink: DeviceLink): Boolean {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        val isRequest = (deviceLink.type == DeviceLink.Type.REQUEST)
        if (deviceLink.requestSignature == null) {
            Log.d("Loki", "Ignoring device link without a request signature.")
            return false
        } else if (isRequest && TextSecurePreferences.getMasterHexEncodedPublicKey(context) != null) {
            Log.d("Loki", "Ignoring unexpected device link message (the device is a slave device).")
            return false
        } else if (isRequest && deviceLink.masterPublicKey != userPublicKey) {
            Log.d("Loki", "Ignoring device linking message addressed to another user.")
            return false
        } else if (isRequest && deviceLink.slavePublicKey == userPublicKey) {
            Log.d("Loki", "Ignoring device linking request message from self.")
            return false
        }
        return deviceLink.verify()
    }

    private fun handleDeviceLinkRequestMessage(context: Context, deviceLink: DeviceLink, content: SignalServiceContent) {
        val linkingSession = DeviceLinkingSession.shared
        if (!linkingSession.isListeningForLinkingRequests) {
            return Broadcaster(context).broadcast("unexpectedDeviceLinkRequestReceived")
        }
        val isValid = isValidDeviceLinkMessage(context, deviceLink)
        if (!isValid) { return }
        // The line below isn't actually necessary because this is called after PushDecryptJob
        // calls handlePreKeyBundleMessageIfNeeded, but it also doesn't hurt.
        SessionManagementProtocol.handlePreKeyBundleMessageIfNeeded(context, content)
        linkingSession.processLinkingRequest(deviceLink)
    }

    private fun handleDeviceLinkAuthorizedMessage(context: Context, deviceLink: DeviceLink, content: SignalServiceContent) {
        val linkingSession = DeviceLinkingSession.shared
        if (!linkingSession.isListeningForLinkingRequests) {
            return
        }
        val isValid = isValidDeviceLinkMessage(context, deviceLink)
        if (!isValid) { return }
        SessionManagementProtocol.handlePreKeyBundleMessageIfNeeded(context, content)
        linkingSession.processLinkingAuthorization(deviceLink)
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        DatabaseFactory.getLokiAPIDatabase(context).clearDeviceLinks(userPublicKey)
        DatabaseFactory.getLokiAPIDatabase(context).addDeviceLink(deviceLink)
        TextSecurePreferences.setMasterHexEncodedPublicKey(context, deviceLink.masterPublicKey)
        TextSecurePreferences.setMultiDevice(context, true)
        FileServerAPI.shared.addDeviceLink(deviceLink)
        org.thoughtcrime.securesms.loki.protocol.SessionMetaProtocol.handleProfileUpdateIfNeeded(context, content)
        org.thoughtcrime.securesms.loki.protocol.SessionMetaProtocol.duplicate_handleProfileKey(context, content)
    }

    @JvmStatic
    fun handleUnlinkingRequestIfNeeded(context: Context, content: SignalServiceContent) {
        val userPublicKey = TextSecurePreferences.getLocalNumber(context)
        // Check that the request was sent by the user's master device
        val masterDevicePublicKey = TextSecurePreferences.getMasterHexEncodedPublicKey(context) ?: return
        val wasSentByMasterDevice = (content.sender == masterDevicePublicKey)
        if (!wasSentByMasterDevice) { return }
        // Ignore the request if we don't know about the device link in question
        val masterDeviceLinks = DatabaseFactory.getLokiAPIDatabase(context).getDeviceLinks(masterDevicePublicKey)
        if (masterDeviceLinks.none {
            it.masterPublicKey == masterDevicePublicKey && it.slavePublicKey == userPublicKey
        }) {
            return
        }
        FileServerAPI.shared.getDeviceLinks(userPublicKey, true).success { slaveDeviceLinks ->
            // Check that the device link IS present on the file server.
            // Note that the device link as seen from the master device's perspective has been deleted at this point, but the
            // device link as seen from the slave perspective hasn't.
            if (slaveDeviceLinks.any {
                it.masterPublicKey == masterDevicePublicKey && it.slavePublicKey == userPublicKey
            }) {
                for (slaveDeviceLink in slaveDeviceLinks) { // In theory there should only be one
                    FileServerAPI.shared.removeDeviceLink(slaveDeviceLink) // Attempt to clean up on the file server
                }
                TextSecurePreferences.setWasUnlinked(context, true)
                ApplicationContext.getInstance(context).clearData()
            }
        }
    }
}
