package org.thoughtcrime.securesms.audio;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.util.LruCache;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.google.protobuf.ByteString;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.databaseprotos.AudioWaveFormData;
import org.thoughtcrime.securesms.media.DecryptableUriMediaInput;
import org.thoughtcrime.securesms.media.MediaInput;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.util.concurrent.SerialExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.M)
public final class AudioWaveForm {

  private static final String TAG = Log.tag(AudioWaveForm.class);

  private static final int BAR_COUNT       = 46;
  private static final int SAMPLES_PER_BAR =  4;

  private final Context    context;
  private final AudioSlide slide;

  public AudioWaveForm(@NonNull Context context, @NonNull AudioSlide slide) {
    this.context = context.getApplicationContext();
    this.slide   = slide;
  }

  private static final LruCache<String, AudioFileInfo> WAVE_FORM_CACHE        = new LruCache<>(200);
  private static final Executor                        AUDIO_DECODER_EXECUTOR = new SerialExecutor(SignalExecutors.BOUNDED);

  @AnyThread
  public void getWaveForm(@NonNull Consumer<AudioFileInfo> onSuccess, @NonNull Runnable onFailure) {
    Uri        uri        = slide.getUri();
    Attachment attachment = slide.asAttachment();

    if (uri == null) {
      Log.w(TAG, "No uri");
      ThreadUtil.runOnMain(onFailure);
      return;
    }

    String        cacheKey = uri.toString();
    AudioFileInfo cached   = WAVE_FORM_CACHE.get(cacheKey);
    if (cached != null) {
      Log.i(TAG, "Loaded wave form from cache " + cacheKey);
      ThreadUtil.runOnMain(() -> onSuccess.accept(cached));
      return;
    }

    AUDIO_DECODER_EXECUTOR.execute(() -> {
      AudioFileInfo cachedInExecutor = WAVE_FORM_CACHE.get(cacheKey);
      if (cachedInExecutor != null) {
        Log.i(TAG, "Loaded wave form from cache inside executor" + cacheKey);
        ThreadUtil.runOnMain(() -> onSuccess.accept(cachedInExecutor));
        return;
      }

      AudioHash audioHash = attachment.getAudioHash();
      if (audioHash != null) {
        AudioFileInfo audioFileInfo = AudioFileInfo.fromDatabaseProtobuf(audioHash.getAudioWaveForm());
        if (audioFileInfo.waveForm.length == 0) {
          Log.w(TAG, "Recovering from a wave form generation error  " + cacheKey);
          ThreadUtil.runOnMain(onFailure);
          return;
        } else if (audioFileInfo.waveForm.length != BAR_COUNT) {
          Log.w(TAG, "Wave form from database does not match bar count, regenerating " + cacheKey);
        } else {
          WAVE_FORM_CACHE.put(cacheKey, audioFileInfo);
          Log.i(TAG, "Loaded wave form from DB " + cacheKey);
          ThreadUtil.runOnMain(() -> onSuccess.accept(audioFileInfo));
          return;
        }
      }

      if (attachment instanceof DatabaseAttachment) {
        try {
          AttachmentTable    attachmentDatabase = SignalDatabase.attachments();
          DatabaseAttachment dbAttachment       = (DatabaseAttachment) attachment;
          long               startTime          = System.currentTimeMillis();

          attachmentDatabase.writeAudioHash(dbAttachment.getAttachmentId(), AudioWaveFormData.getDefaultInstance());

          Log.i(TAG, String.format("Starting wave form generation (%s)", cacheKey));

          AudioFileInfo fileInfo = generateWaveForm(uri);

          Log.i(TAG, String.format(Locale.US, "Audio wave form generation time %d ms (%s)", System.currentTimeMillis() - startTime, cacheKey));

          attachmentDatabase.writeAudioHash(dbAttachment.getAttachmentId(), fileInfo.toDatabaseProtobuf());

          WAVE_FORM_CACHE.put(cacheKey, fileInfo);
          ThreadUtil.runOnMain(() -> onSuccess.accept(fileInfo));
        } catch (Throwable e) {
          Log.w(TAG, "Failed to create audio wave form for " + cacheKey, e);
          ThreadUtil.runOnMain(onFailure);
        }
      } else {
        try {
          Log.i(TAG, "Not in database and not cached. Generating wave form on-the-fly.");

          long startTime = System.currentTimeMillis();

          Log.i(TAG, String.format("Starting wave form generation (%s)", cacheKey));

          AudioFileInfo fileInfo = generateWaveForm(uri);

          Log.i(TAG, String.format(Locale.US, "Audio wave form generation time %d ms (%s)", System.currentTimeMillis() - startTime, cacheKey));

          WAVE_FORM_CACHE.put(cacheKey, fileInfo);
          ThreadUtil.runOnMain(() -> onSuccess.accept(fileInfo));
        } catch (IOException e) {
          Log.w(TAG, "Failed to create audio wave form for " + cacheKey, e);
          ThreadUtil.runOnMain(onFailure);
        }
      }
    });
  }

  /**
   * Based on decode sample from:
   * <p>
   * https://android.googlesource.com/platform/cts/+/jb-mr2-release/tests/tests/media/src/android/media/cts/DecoderTest.java
   */
  @WorkerThread
  @RequiresApi(api = 23)
  private @NonNull AudioFileInfo generateWaveForm(@NonNull Uri uri) throws IOException {
    try (MediaInput dataSource = DecryptableUriMediaInput.createForUri(context, uri)) {
      long[] wave        = new long[BAR_COUNT];
      int[]  waveSamples = new int[BAR_COUNT];

      MediaExtractor extractor = dataSource.createExtractor();

      if (extractor.getTrackCount() == 0) {
        throw new IOException("No audio track");
      }

      MediaFormat format = extractor.getTrackFormat(0);

      if (!format.containsKey(MediaFormat.KEY_DURATION)) {
        throw new IOException("Unknown duration");
      }

      long   totalDurationUs = format.getLong(MediaFormat.KEY_DURATION);
      String mime            = format.getString(MediaFormat.KEY_MIME);

      if (!mime.startsWith("audio/")) {
        throw new IOException("Mime not audio");
      }

      MediaCodec codec = MediaCodec.createDecoderByType(mime);

      if (totalDurationUs == 0) {
        throw new IOException("Zero duration");
      }

      codec.configure(format, null, null, 0);
      codec.start();

      ByteBuffer[] codecInputBuffers  = codec.getInputBuffers();
      ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

      extractor.selectTrack(0);

      long                  kTimeOutUs      = 5000;
      MediaCodec.BufferInfo info            = new MediaCodec.BufferInfo();
      boolean               sawInputEOS     = false;
      boolean               sawOutputEOS    = false;
      int                   noOutputCounter = 0;

      while (!sawOutputEOS && noOutputCounter < 50) {
        noOutputCounter++;
        if (!sawInputEOS) {
          int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);
          if (inputBufIndex >= 0) {
            ByteBuffer dstBuf             = codecInputBuffers[inputBufIndex];
            int        sampleSize         = extractor.readSampleData(dstBuf, 0);
            long       presentationTimeUs = 0;

            if (sampleSize < 0) {
              sawInputEOS = true;
              sampleSize  = 0;
            } else {
              presentationTimeUs = extractor.getSampleTime();
            }

            codec.queueInputBuffer(
              inputBufIndex,
              0,
              sampleSize,
              presentationTimeUs,
              sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

            if (!sawInputEOS) {
              int barSampleIndex = (int) (SAMPLES_PER_BAR * (wave.length * extractor.getSampleTime()) / totalDurationUs);
              sawInputEOS = !extractor.advance();
              int nextBarSampleIndex = (int) (SAMPLES_PER_BAR * (wave.length * extractor.getSampleTime()) / totalDurationUs);
              while (!sawInputEOS && nextBarSampleIndex == barSampleIndex) {
                sawInputEOS = !extractor.advance();
                if (!sawInputEOS) {
                  nextBarSampleIndex = (int) (SAMPLES_PER_BAR * (wave.length * extractor.getSampleTime()) / totalDurationUs);
                }
              }
            }
          }
        }

        int outputBufferIndex;
        do {
          outputBufferIndex = codec.dequeueOutputBuffer(info, kTimeOutUs);
          if (outputBufferIndex >= 0) {
            if (info.size > 0) {
              noOutputCounter = 0;
            }

            ByteBuffer buf = codecOutputBuffers[outputBufferIndex];
            int barIndex = (int) ((wave.length * info.presentationTimeUs) / totalDurationUs);
            long total = 0;
            for (int i = 0; i < info.size; i += 2 * 4) {
              short aShort = buf.getShort(i);
              total += Math.abs(aShort);
            }
            if (barIndex >= 0 && barIndex < wave.length) {
              wave[barIndex] += total;
              waveSamples[barIndex] += info.size / 2;
            }
            codec.releaseOutputBuffer(outputBufferIndex, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              sawOutputEOS = true;
            }
          } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            codecOutputBuffers = codec.getOutputBuffers();
          } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.d(TAG, "output format has changed to " + codec.getOutputFormat());
          }
        } while (outputBufferIndex >= 0);
      }

      codec.stop();
      codec.release();
      extractor.release();

      float[] floats = new float[BAR_COUNT];
      byte[]  bytes  = new byte[BAR_COUNT];
      float   max    = 0;

      for (int i = 0; i < BAR_COUNT; i++) {
        if (waveSamples[i] == 0) continue;

        floats[i] = wave[i] / (float) waveSamples[i];
        if (floats[i] > max) {
          max = floats[i];
        }
      }

      for (int i = 0; i < BAR_COUNT; i++) {
        float normalized = floats[i] / max;
        bytes[i] = (byte) (255 * normalized);
      }

      return new AudioFileInfo(totalDurationUs, bytes);
    }
  }

  public static class AudioFileInfo {
    private final long    durationUs;
    private final byte[]  waveFormBytes;
    private final float[] waveForm;

    private static @NonNull AudioFileInfo fromDatabaseProtobuf(@NonNull AudioWaveFormData audioWaveForm) {
      return new AudioFileInfo(audioWaveForm.getDurationUs(), audioWaveForm.getWaveForm().toByteArray());
    }

    private AudioFileInfo(long durationUs, byte[] waveFormBytes) {
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

    private @NonNull AudioWaveFormData toDatabaseProtobuf() {
      return AudioWaveFormData.newBuilder()
                              .setDurationUs(durationUs)
                              .setWaveForm(ByteString.copyFrom(waveFormBytes))
                              .build();
    }
  }
}
