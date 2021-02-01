package org.session.libsignal.service.loki.utilities

import okhttp3.HttpUrl
import okhttp3.Request
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.api.messages.SignalServiceAttachment
import org.session.libsignal.service.api.push.exceptions.NonSuccessfulResponseCodeException
import org.session.libsignal.service.api.push.exceptions.PushNetworkException
import org.session.libsignal.utilities.Base64
import org.session.libsignal.service.loki.api.fileserver.FileServerAPI
import org.session.libsignal.service.loki.api.onionrequests.OnionRequestAPI
import java.io.*

object DownloadUtilities {

    /**
     * Blocks the calling thread.
     */
    @JvmStatic
    fun downloadFile(destination: File, url: String, maxSize: Int, listener: SignalServiceAttachment.ProgressListener?) {
        val outputStream = FileOutputStream(destination) // Throws
        var remainingAttempts = 4
        var exception: Exception? = null
        while (remainingAttempts > 0) {
            remainingAttempts -= 1
            try {
                downloadFile(outputStream, url, maxSize, listener)
                exception = null
                break
            } catch (e: Exception) {
                exception = e
            }
        }
        if (exception != null) { throw exception }
    }

    /**
     * Blocks the calling thread.
     */
    @JvmStatic
    fun downloadFile(outputStream: OutputStream, url: String, maxSize: Int, listener: SignalServiceAttachment.ProgressListener?) {
        // We need to throw a PushNetworkException or NonSuccessfulResponseCodeException
        // because the underlying Signal logic requires these to work correctly
        val oldPrefixedHost = "https://" + HttpUrl.get(url).host()
        var newPrefixedHost = oldPrefixedHost
        if (oldPrefixedHost.contains(FileServerAPI.fileStorageBucketURL)) {
            newPrefixedHost = FileServerAPI.shared.server
        }
        // Edge case that needs to work: https://file-static.lokinet.org/i1pNmpInq3w9gF3TP8TFCa1rSo38J6UM
        // → https://file.getsession.org/loki/v1/f/XLxogNXVEIWHk14NVCDeppzTujPHxu35
        val fileID = url.substringAfter(oldPrefixedHost).substringAfter("/f/")
        val sanitizedURL = "$newPrefixedHost/loki/v1/f/$fileID"
        val request = Request.Builder().url(sanitizedURL).get()
        try {
            val serverPublicKey = if (newPrefixedHost.contains(FileServerAPI.shared.server)) FileServerAPI.fileServerPublicKey
                else FileServerAPI.shared.getPublicKeyForOpenGroupServer(newPrefixedHost).get()
            val json = OnionRequestAPI.sendOnionRequest(request.build(), newPrefixedHost, serverPublicKey, isJSONRequired = false).get()
            val result = json["result"] as? String
            if (result == null) {
                Log.d("Loki", "Couldn't parse attachment from: $json.")
                throw PushNetworkException("Missing response body.")
            }
            val body = Base64.decode(result)
            if (body.size > maxSize) {
                Log.d("Loki", "Attachment size limit exceeded.")
                throw PushNetworkException("Max response size exceeded.")
            }
            val input = body.inputStream()
            val buffer = ByteArray(32768)
            var count = 0
            var bytes = input.read(buffer)
            while (bytes >= 0) {
                outputStream.write(buffer, 0, bytes)
                count += bytes
                if (count > maxSize) {
                    Log.d("Loki", "Attachment size limit exceeded.")
                    throw PushNetworkException("Max response size exceeded.")
                }
                listener?.onAttachmentProgress(body.size.toLong(), count.toLong())
                bytes = input.read(buffer)
            }
        } catch (e: Exception) {
            Log.d("Loki", "Couldn't download attachment due to error: $e.")
            throw if (e is NonSuccessfulResponseCodeException) e else PushNetworkException(e)
        }
    }
}
