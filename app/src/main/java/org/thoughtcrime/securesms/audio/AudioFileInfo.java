package org.thoughtcrime.securesms.audio;

import androidx.annotation.NonNull;

import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData;

import java.util.concurrent.TimeUnit;

public class AudioFileInfo {
  private final long    durationUs;
  private final byte[]  waveFormBytes;
  private final float[] waveForm;

  public static @NonNull AudioFileInfo fromDatabaseProtobuf(@NonNull AudioWaveFormData audioWaveForm) {
    return new AudioFileInfo(audioWaveForm.getDurationUs(), audioWaveForm.getWaveForm().toByteArray());
  }

  AudioFileInfo(long durationUs, byte[] waveFormBytes) {
    this.durationUs    = durationUs;
    this.waveFormBytes = waveFormBytes;
    this.waveForm      = new float[waveFormBytes.length];

    for (int i = 0; i < waveFormBytes.length; i++) {
      int unsigned = waveFormBytes[i] & 0xff;
      this.waveForm[i] = unsigned / 255f;
    }
  }

  public long getDuration(@NonNull TimeUnit timeUnit) {
    return timeUnit.convert(durationUs, TimeUnit.MICROSECONDS);
  }

  public float[] getWaveForm() {
    return waveForm;
  }

  public @NonNull AudioWaveFormData toDatabaseProtobuf() {
    return AudioWaveFormData.newBuilder()
                            .setDurationUs(durationUs)
                            .setWaveForm(ByteString.copyFrom(waveFormBytes))
                            .build();
  }
}
