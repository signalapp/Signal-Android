package org.thoughtcrime.securesms.webrtc.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import network.loki.messenger.R
import org.session.libsignal.utilities.Log
import java.io.IOException
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

class OutgoingRinger(private val context: Context) {
    enum class Type {
        RINGING, BUSY
    }

    private var mediaPlayer: MediaPlayer? = null
    fun start(type: Type) {
        val soundId: Int = if (type == Type.RINGING) R.raw.redphone_outring else if (type == Type.BUSY) R.raw.redphone_busy else throw IllegalArgumentException("Not a valid sound type")
        if (mediaPlayer != null) {
            mediaPlayer!!.release()
        }
        mediaPlayer = MediaPlayer()
        mediaPlayer!!.setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build())
        mediaPlayer!!.isLooping = true
        val packageName = context.packageName
        val dataUri = Uri.parse("android.resource://$packageName/$soundId")
        try {
            mediaPlayer!!.setDataSource(context, dataUri)
            mediaPlayer!!.prepare()
            mediaPlayer!!.start()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, e)
        } catch (e: SecurityException) {
            Log.e(TAG, e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, e)
        } catch (e: IOException) {
            Log.e(TAG, e)
        }
    }

    fun stop() {
        if (mediaPlayer == null) return
        mediaPlayer!!.release()
        mediaPlayer = null
    }

    companion object {
        private val TAG: String = Log.tag(OutgoingRinger::class.java)
    }
}
