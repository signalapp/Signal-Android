package org.session.libsession.utilities

import android.content.Context
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import okio.Buffer
import org.session.libsession.messaging.file_server.FileServerApi
import org.session.libsignal.streams.ProfileCipherOutputStream
import org.session.libsignal.utilities.ProfileAvatarData
import org.session.libsignal.streams.DigestingRequestBody
import org.session.libsignal.streams.ProfileCipherOutputStreamFactory
import org.session.libsignal.utilities.retryIfNeeded
import org.session.libsignal.utilities.ThreadUtils
import java.io.ByteArrayInputStream
import java.util.*

object ProfilePictureUtilities {

    fun upload(profilePicture: ByteArray, encodedProfileKey: String, context: Context): Promise<Unit, Exception> {
        val deferred = deferred<Unit, Exception>()
        ThreadUtils.queue {
            val inputStream = ByteArrayInputStream(profilePicture)
            val outputStream = ProfileCipherOutputStream.getCiphertextLength(profilePicture.size.toLong())
            val profileKey = ProfileKeyUtil.getProfileKeyFromEncodedString(encodedProfileKey)
            val pad = ProfileAvatarData(inputStream, outputStream, "image/jpeg", ProfileCipherOutputStreamFactory(profileKey))
            val drb = DigestingRequestBody(pad.data, pad.outputStreamFactory, pad.contentType, pad.dataLength, null)
            val b = Buffer()
            drb.writeTo(b)
            val data = b.readByteArray()
            var id: Long = 0
            try {
                id = retryIfNeeded(4) {
                    FileServerApi.upload(data)
                }.get()
            } catch (e: Exception) {
                deferred.reject(e)
            }
            TextSecurePreferences.setLastProfilePictureUpload(context, Date().time)
            val url = "${FileServerApi.server}/file/$id"
            TextSecurePreferences.setProfilePictureURL(context, url)
            deferred.resolve(Unit)
        }
        return deferred.promise
    }
}