package org.thoughtcrime.securesms.audio;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData;
import org.thoughtcrime.securesms.util.ParcelUtil;
import org.signal.core.util.Base64;

import java.io.IOException;
import java.util.Objects;

/**
 * An AudioHash is a compact string representation of the wave form and duration for an audio file.
 */
public final class AudioHash implements Parcelable {

  @NonNull private final String            hash;
  @NonNull private final AudioWaveFormData audioWaveForm;

  private AudioHash(@NonNull String hash, @NonNull AudioWaveFormData audioWaveForm) {
    this.hash          = hash;
    this.audioWaveForm = audioWaveForm;
  }

  public AudioHash(@NonNull AudioWaveFormData audioWaveForm) {
    this(Base64.encodeWithPadding(audioWaveForm.encode()), audioWaveForm);
  }

  protected AudioHash(Parcel in) {
    hash = Objects.requireNonNull(in.readString());

    try {
      audioWaveForm = AudioWaveFormData.ADAPTER.decode(Objects.requireNonNull(ParcelUtil.readByteArray(in)));
    } catch (IOException e) {
      throw new AssertionError();
    }
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(hash);
    ParcelUtil.writeByteArray(dest, audioWaveForm.encode());
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<AudioHash> CREATOR = new Creator<>() {
    @Override
    public AudioHash createFromParcel(Parcel in) {
      return new AudioHash(in);
    }

    @Override
    public AudioHash[] newArray(int size) {
      return new AudioHash[size];
    }
  };

  public static @Nullable AudioHash parseOrNull(@Nullable String hash) {
    if (hash == null) return null;
    try {
      return new AudioHash(hash, AudioWaveFormData.ADAPTER.decode(Base64.decode(hash)));
    } catch (IOException e) {
      return null;
    }
  }

  @NonNull AudioWaveFormData getAudioWaveForm() {
    return audioWaveForm;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    AudioHash other = (AudioHash) o;
    return hash.equals(other.hash);
  }

  @Override
  public int hashCode() {
    return hash.hashCode();
  }

  public @NonNull String getHash() {
    return hash;
  }
}
