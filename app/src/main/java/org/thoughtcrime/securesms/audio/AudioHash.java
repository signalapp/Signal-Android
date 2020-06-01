package org.thoughtcrime.securesms.audio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData;
import org.whispersystems.util.Base64;

import java.io.IOException;

/**
 * An AudioHash is a compact string representation of the wave form and duration for an audio file.
 */
public final class AudioHash {

  @NonNull private final String            hash;
  @NonNull private final AudioWaveFormData audioWaveForm;

  private AudioHash(@NonNull String hash, @NonNull AudioWaveFormData audioWaveForm) {
    this.hash          = hash;
    this.audioWaveForm = audioWaveForm;
  }

  public AudioHash(@NonNull AudioWaveFormData audioWaveForm) {
    this(Base64.encodeBytes(audioWaveForm.toByteArray()), audioWaveForm);
  }

  public static @Nullable AudioHash parseOrNull(@Nullable String hash) {
    if (hash == null) return null;
    try {
      return new AudioHash(hash, AudioWaveFormData.parseFrom(Base64.decode(hash)));
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
