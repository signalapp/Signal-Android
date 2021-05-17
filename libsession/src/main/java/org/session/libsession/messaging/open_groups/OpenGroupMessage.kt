package org.session.libsession.messaging.open_groups

import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsignal.utilities.removing05PrefixIfNeeded
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.Log
import org.whispersystems.curve25519.Curve25519

data class OpenGroupMessage(
        val serverID: Long?,
        val senderPublicKey: String,
        val displayName: String,
        val body: String,
        val timestamp: Long,
        val type: String,
        val quote: Quote?,
        val attachments: MutableList<Attachment>,
        val profilePicture: ProfilePicture?,
        val signature: Signature?,
        val serverTimestamp: Long,
) {

    // region Settings
    companion object {
        fun from(message: VisibleMessage, server: String): OpenGroupMessage? {
            val storage = MessagingModuleConfiguration.shared.storage
            val userPublicKey = storage.getUserPublicKey() ?: return null
            val attachmentIDs = message.attachmentIDs
            // Validation
            if (!message.isValid()) { return null } // Should be valid at this point
            // Quote
            val quote: Quote? = {
                val quote = message.quote
                if (quote != null && quote.isValid()) {
                    val quotedMessageBody = quote.text ?: quote.timestamp!!.toString()
                    val serverID = storage.getQuoteServerID(quote.timestamp!!, quote.publicKey!!)
                    Quote(quote.timestamp!!, quote.publicKey!!, quotedMessageBody, serverID)
                } else {
                    null
                }
            }()
            // Message
            val displayname = storage.getUserDisplayName() ?: "Anonymous"
            val text = message.text
            val body = if (text.isNullOrEmpty()) message.sentTimestamp.toString() else text // The back-end doesn't accept messages without a body so we use this as a workaround
            val result = OpenGroupMessage(null, userPublicKey, displayname, body, message.sentTimestamp!!, OpenGroupAPI.openGroupMessageType, quote, mutableListOf(), null, null, 0)
            // Link preview
            val linkPreview = message.linkPreview
            linkPreview?.let {
                if (!linkPreview.isValid()) { return@let }
                val attachmentID = linkPreview.attachmentID ?: return@let
                val attachment = MessagingModuleConfiguration.shared.messageDataProvider.getSignalAttachmentPointer(attachmentID) ?: return@let
                val openGroupLinkPreview = Attachment(
                        Attachment.Kind.LinkPreview,
                        server,
                        attachment.id,
                        attachment.contentType!!,
                        attachment.size.get(),
                        attachment.fileName.orNull(),
                        0,
                        attachment.width,
                        attachment.height,
                        attachment.caption.orNull(),
                        attachment.url,
                        linkPreview.url,
                        linkPreview.title)
                result.attachments.add(openGroupLinkPreview)
            }
            // Attachments
            val attachments = message.attachmentIDs.mapNotNull {
                val attachment = MessagingModuleConfiguration.shared.messageDataProvider.getSignalAttachmentPointer(it) ?: return@mapNotNull null
                return@mapNotNull Attachment(
                        Attachment.Kind.Attachment,
                        server,
                        attachment.id,
                        attachment.contentType!!,
                        attachment.size.orNull(),
                        attachment.fileName.orNull() ?: "",
                        0,
                        attachment.width,
                        attachment.height,
                        attachment.caption.orNull(),
                        attachment.url,
                        null,
                        null)
            }
            result.attachments.addAll(attachments)
            // Return
            return result
        }

        private val curve = Curve25519.getInstance(Curve25519.BEST)
        private val signatureVersion: Long = 1
        private val attachmentType = "net.app.core.oembed"
    }
    // endregion

    // region Types
    data class ProfilePicture(
            val profileKey: ByteArray,
            val url: String,
    )

    data class Quote(
            val quotedMessageTimestamp: Long,
            val quoteePublicKey: String,
            val quotedMessageBody: String,
            val quotedMessageServerID: Long? = null,
    )

    data class Signature(
            val data: ByteArray,
            val version: Long,
    )

    data class Attachment(
            val kind: Kind,
            val server: String,
            val serverID: Long,
            val contentType: String,
            val size: Int,
            val fileName: String?,
            val flags: Int,
            val width: Int,
            val height: Int,
            val caption: String?,
            val url: String,
            /**
            Guaranteed to be non-`nil` if `kind` is `LinkPreview`.
             */
            val linkPreviewURL: String?,
            /**
            Guaranteed to be non-`nil` if `kind` is `LinkPreview`.
             */
            val linkPreviewTitle: String?,
    ) {
        val dotNetAPIType = when {
            contentType.startsWith("image") -> "photo"
            contentType.startsWith("video") -> "video"
            contentType.startsWith("audio") -> "audio"
            else -> "other"
        }

        enum class Kind(val rawValue: String) {
            Attachment("attachment"), LinkPreview("preview")
        }
    }
    // endregion

    // region Initialization
    constructor(hexEncodedPublicKey: String, displayName: String, body: String, timestamp: Long, type: String, quote: Quote?, attachments: List<Attachment>)
        : this(null, hexEncodedPublicKey, displayName, body, timestamp, type, quote, attachments as MutableList<Attachment>, null, null, 0)
    // endregion

    // region Crypto
    internal fun sign(privateKey: ByteArray): OpenGroupMessage? {
        val data = getValidationData(signatureVersion)
        if (data == null) {
            Log.d("Loki", "Failed to sign public chat message.")
            return null
        }
        try {
            val signatureData = curve.calculateSignature(privateKey, data)
            val signature = Signature(signatureData, signatureVersion)
            return copy(signature = signature)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to sign public chat message due to error: ${e.message}.")
            return null
        }
    }

    internal fun hasValidSignature(): Boolean {
        if (signature == null) { return false }
        val data = getValidationData(signature.version) ?: return false
        val publicKey = Hex.fromStringCondensed(senderPublicKey.removing05PrefixIfNeeded())
        try {
            return curve.verifySignature(publicKey, data, signature.data)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to verify public chat message due to error: ${e.message}.")
            return false
        }
    }
    // endregion

    // region Parsing
    internal fun toJSON(): Map<String, Any> {
        val value = mutableMapOf<String, Any>("timestamp" to timestamp)
        if (quote != null) {
            value["quote"] = mapOf("id" to quote.quotedMessageTimestamp, "author" to quote.quoteePublicKey, "text" to quote.quotedMessageBody)
        }
        if (signature != null) {
            value["sig"] = Hex.toStringCondensed(signature.data)
            value["sigver"] = signature.version
        }
        val annotation = mapOf("type" to type, "value" to value)
        val annotations = mutableListOf(annotation)
        attachments.forEach { attachment ->
            val attachmentValue = mutableMapOf(
                    // Fields required by the .NET API
                    "version" to 1,
                    "type" to attachment.dotNetAPIType,
                    // Custom fields
                    "lokiType" to attachment.kind.rawValue,
                    "server" to attachment.server,
                    "id" to attachment.serverID,
                    "contentType" to attachment.contentType,
                    "size" to attachment.size,
                    "fileName" to attachment.fileName,
                    "flags" to attachment.flags,
                    "width" to attachment.width,
                    "height" to attachment.height,
                    "url" to attachment.url
            )
            if (attachment.caption != null) { attachmentValue["caption"] = attachment.caption }
            if (attachment.linkPreviewURL != null) { attachmentValue["linkPreviewUrl"] = attachment.linkPreviewURL }
            if (attachment.linkPreviewTitle != null) { attachmentValue["linkPreviewTitle"] = attachment.linkPreviewTitle }
            val attachmentAnnotation = mapOf("type" to attachmentType, "value" to attachmentValue)
            annotations.add(attachmentAnnotation)
        }
        val result = mutableMapOf("text" to body, "annotations" to annotations)
        if (quote?.quotedMessageServerID != null) {
            result["reply_to"] = quote.quotedMessageServerID
        }
        return result
    }
    // endregion

    // region Convenience
    private fun getValidationData(signatureVersion: Long): ByteArray? {
        var string = "${body.trim()}$timestamp"
        if (quote != null) {
            string += "${quote.quotedMessageTimestamp}${quote.quoteePublicKey}${quote.quotedMessageBody.trim()}"
            if (quote.quotedMessageServerID != null) {
                string += "${quote.quotedMessageServerID}"
            }
        }
        string += attachments.sortedBy { it.serverID }.map { it.serverID }.joinToString("")
        string += "$signatureVersion"
        try {
            return string.toByteArray(Charsets.UTF_8)
        } catch (exception: Exception) {
            return null
        }
    }
    // endregion
}
