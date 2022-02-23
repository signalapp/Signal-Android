package org.thoughtcrime.securesms.webrtc.audio

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.ParcelUtil

/**
 * Commands that can be issued to [SignalAudioManager] to perform various tasks.
 *
 * Additional context: The audio management is tied closely with the Android audio and thus benefits from being
 * tied to the [org.thoughtcrime.securesms.service.webrtc.WebRtcCallService] lifecycle. Because of this, all
 * calls have to go through an intent to the service and this allows one entry point for that but multiple
 * operations.
 */
sealed class AudioManagerCommand : Parcelable {

  override fun writeToParcel(parcel: Parcel, flags: Int) = Unit
  override fun describeContents(): Int = 0

  class Initialize : AudioManagerCommand() {
    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<Initialize> = ParcelCheat { Initialize() }
    }
  }

  class StartIncomingRinger(val ringtoneUri: Uri, val vibrate: Boolean) : AudioManagerCommand() {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
      parcel.writeParcelable(ringtoneUri, flags)
      ParcelUtil.writeBoolean(parcel, vibrate)
    }

    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<StartIncomingRinger> = ParcelCheat { parcel ->
        StartIncomingRinger(
          ringtoneUri = parcel.readParcelable(Uri::class.java.classLoader)!!,
          vibrate = ParcelUtil.readBoolean(parcel)
        )
      }
    }
  }

  class StartOutgoingRinger : AudioManagerCommand() {
    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<StartOutgoingRinger> = ParcelCheat { StartOutgoingRinger() }
    }
  }

  class SilenceIncomingRinger : AudioManagerCommand() {
    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<SilenceIncomingRinger> = ParcelCheat { SilenceIncomingRinger() }
    }
  }

  class Start : AudioManagerCommand() {
    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<Start> = ParcelCheat { Start() }
    }
  }

  class Stop(val playDisconnect: Boolean) : AudioManagerCommand() {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
      ParcelUtil.writeBoolean(parcel, playDisconnect)
    }

    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<Stop> = ParcelCheat { Stop(ParcelUtil.readBoolean(it)) }
    }
  }

  class SetUserDevice(val recipientId: RecipientId?, val device: SignalAudioManager.AudioDevice) : AudioManagerCommand() {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
      parcel.writeParcelable(recipientId, flags)
      parcel.writeSerializable(device)
    }

    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<SetUserDevice> = ParcelCheat { SetUserDevice(it.readParcelable(RecipientId::class.java.classLoader), it.readSerializable() as SignalAudioManager.AudioDevice) }
    }
  }

  class SetDefaultDevice(val recipientId: RecipientId?, val device: SignalAudioManager.AudioDevice, val clearUserEarpieceSelection: Boolean) : AudioManagerCommand() {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
      parcel.writeParcelable(recipientId, flags)
      parcel.writeSerializable(device)
      ParcelUtil.writeBoolean(parcel, clearUserEarpieceSelection)
    }

    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<SetDefaultDevice> = ParcelCheat { parcel ->
        SetDefaultDevice(
          recipientId = parcel.readParcelable(RecipientId::class.java.classLoader),
          device = parcel.readSerializable() as SignalAudioManager.AudioDevice,
          clearUserEarpieceSelection = ParcelUtil.readBoolean(parcel)
        )
      }
    }
  }

  class ParcelCheat<T>(private val createFrom: (Parcel) -> T) : Parcelable.Creator<T> {
    override fun createFromParcel(parcel: Parcel): T = createFrom(parcel)
    override fun newArray(size: Int): Array<T?> = throw UnsupportedOperationException()
  }
}
