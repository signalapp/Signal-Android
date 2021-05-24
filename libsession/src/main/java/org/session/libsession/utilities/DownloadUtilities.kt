package org.session.libsession.utilities

import okhttp3.HttpUrl
import org.session.libsession.messaging.file_server.FileServerAPIV2
import org.session.libsignal.utilities.Log
import org.session.libsignal.messages.SignalServiceAttachment
import java.io.*

object DownloadUtilities {

    /**
     * Blocks the calling thread.
     */
    @JvmStatic
    fun downloadFile(destination: File, url: String) {
        val outputStream = FileOutputStream(destination) // Throws
        var remainingAttempts = 4
        var exception: Exception? = null
        while (remainingAttempts > 0) {
            remainingAttempts -= 1
            try {
                downloadFile(outputStream, url)
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
    fun downloadFile(outputStream: OutputStream, urlAsString: String) {
        val url = HttpUrl.parse(urlAsString)!!
        val fileID = url.pathSegments().last()
        try {
            FileServerAPIV2.download(fileID.toLong()).get().let {
                outputStream.write(it)
            }
        } catch (e: Exception) {
            Log.e("Loki", "Couldn't download attachment.", e)
            throw e
        }
    }
}