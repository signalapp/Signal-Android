package org.thoughtcrime.securesms.video;

import android.content.Context;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.util.MimeTypes;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.MediaStream;
import org.thoughtcrime.securesms.util.MemoryFileDescriptor;
import org.thoughtcrime.securesms.video.videoconverter.EncodingException;
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter;
import org.thoughtcrime.securesms.video.videoconverter.VideoInput;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

@RequiresApi(26)
public final class InMemoryTranscoder implements Closeable {

  private static final String TAG = Log.tag(InMemoryTranscoder.class);

  private static final int MAXIMUM_TARGET_VIDEO_BITRATE = VideoUtil.VIDEO_BIT_RATE;
  private static final int LOW_RES_TARGET_VIDEO_BITRATE = 1_750_000;
  private static final int MINIMUM_TARGET_VIDEO_BITRATE = 500_000;
  private static final int AUDIO_BITRATE                = VideoUtil.AUDIO_BIT_RATE;
  private static final int OUTPUT_FORMAT                = VideoUtil.VIDEO_SHORT_WIDTH;
  private static final int LOW_RES_OUTPUT_FORMAT        = 480;

  private final Context         context;
  private final MediaDataSource dataSource;
  private final long            upperSizeLimit;
  private final long            inSize;
  private final long            duration;
  private final int             inputBitRate;
  private final int             targetVideoBitRate;
  private final long            memoryFileEstimate;
  private final boolean         transcodeRequired;
  private final long            fileSizeEstimate;
  private final int             outputFormat;
  private final @Nullable Options options;

  private @Nullable MemoryFileDescriptor memoryFile;

  /**
   * @param upperSizeLimit A upper size to transcode to. The actual output size can be up to 10% smaller.
   */
  public InMemoryTranscoder(@NonNull Context context, @NonNull MediaDataSource dataSource, @Nullable Options options, long upperSizeLimit) throws IOException, VideoSourceException {
    this.context    = context;
    this.dataSource = dataSource;
    this.options    = options;

    final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
    try {
      mediaMetadataRetriever.setDataSource(dataSource);
    } catch (RuntimeException e) {
      Log.w(TAG, "Unable to read datasource", e);
      throw new VideoSourceException("Unable to read datasource", e);
    }

    long upperSizeLimitWithMargin = (long) (upperSizeLimit / 1.1);

    this.inSize             = dataSource.getSize();
    this.duration           = getDuration(mediaMetadataRetriever);
    this.inputBitRate       = bitRate(inSize, duration);
    this.targetVideoBitRate = getTargetVideoBitRate(upperSizeLimitWithMargin, duration);
    this.upperSizeLimit     = upperSizeLimit;

    this.transcodeRequired = inputBitRate >= targetVideoBitRate * 1.2 || inSize > upperSizeLimit || containsLocation(mediaMetadataRetriever) || options != null;
    if (!transcodeRequired) {
      Log.i(TAG, "Video is within 20% of target bitrate, below the size limit, contained no location metadata or custom options.");
    }

    this.fileSizeEstimate   = (targetVideoBitRate + AUDIO_BITRATE) * duration / 8000;
    this.memoryFileEstimate = (long) (fileSizeEstimate * 1.1);
    this.outputFormat       = targetVideoBitRate < LOW_RES_TARGET_VIDEO_BITRATE
                              ? LOW_RES_OUTPUT_FORMAT
                              : OUTPUT_FORMAT;
  }

  public @NonNull MediaStream transcode(@NonNull Progress progress,
                                        @Nullable CancelationSignal cancelationSignal)
      throws IOException, EncodingException, VideoSizeException
  {
    if (memoryFile != null) throw new AssertionError("Not expecting to reuse transcoder");

    float durationSec = duration / 1000f;

    NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

    Log.i(TAG, String.format(Locale.US,
                             "Transcoding:\n" +
                             "Target bitrate : %s + %s = %s\n" +
                             "Target format  : %dp\n" +
                             "Video duration : %.1fs\n" +
                             "Size limit     : %s kB\n" +
                             "Estimate       : %s kB\n" +
                             "Input size     : %s kB\n" +
                             "Input bitrate  : %s bps",
                             numberFormat.format(targetVideoBitRate),
                             numberFormat.format(AUDIO_BITRATE),
                             numberFormat.format(targetVideoBitRate + AUDIO_BITRATE),
                             outputFormat,
                             durationSec,
                             numberFormat.format(upperSizeLimit / 1024),
                             numberFormat.format(fileSizeEstimate / 1024),
                             numberFormat.format(inSize / 1024),
                             numberFormat.format(inputBitRate)));

    if (fileSizeEstimate > upperSizeLimit) {
      throw new VideoSizeException("Size constraints could not be met!");
    }

    memoryFile = MemoryFileDescriptor.newMemoryFileDescriptor(context,
                                                              "TRANSCODE",
                                                              memoryFileEstimate);
    final long startTime = System.currentTimeMillis();

    final FileDescriptor memoryFileFileDescriptor = memoryFile.getFileDescriptor();

    final MediaConverter converter = new MediaConverter();

    converter.setInput(new VideoInput.MediaDataSourceVideoInput(dataSource));
    converter.setOutput(memoryFileFileDescriptor);
    converter.setVideoResolution(outputFormat);
    converter.setVideoBitrate(targetVideoBitRate);
    converter.setAudioBitrate(AUDIO_BITRATE);

    if (options != null) {
      if (options.endTimeUs > 0) {
        long timeFrom = options.startTimeUs / 1000;
        long timeTo   = options.endTimeUs   / 1000;
        converter.setTimeRange(timeFrom, timeTo);
        Log.i(TAG, String.format(Locale.US, "Trimming:\nTotal duration: %d\nKeeping: %d..%d\nFinal duration:(%d)", duration, timeFrom, timeTo, timeTo - timeFrom));
      }
    }

    converter.setListener(percent -> {
      progress.onProgress(percent);
      return cancelationSignal != null && cancelationSignal.isCanceled();
    });

    converter.convert();

    // output details of the transcoding
    long  outSize           = memoryFile.size();
    float encodeDurationSec = (System.currentTimeMillis() - startTime) / 1000f;

    Log.i(TAG, String.format(Locale.US,
                             "Transcoding complete:\n" +
                             "Transcode time : %.1fs (%.1fx)\n" +
                             "Output size    : %s kB\n" +
                             "  of Original  : %.1f%%\n" +
                             "  of Estimate  : %.1f%%\n" +
                             "  of Memory    : %.1f%%\n" +
                             "Output bitrate : %s bps",
                             encodeDurationSec,
                             durationSec / encodeDurationSec,
                             numberFormat.format(outSize / 1024),
                             (outSize * 100d) / inSize,
                             (outSize * 100d) / fileSizeEstimate,
                             (outSize * 100d) / memoryFileEstimate,
                             numberFormat.format(bitRate(outSize, duration))));

    if (outSize > upperSizeLimit) {
      throw new VideoSizeException("Size constraints could not be met!");
    }

    memoryFile.seek(0);

    return new MediaStream(new FileInputStream(memoryFileFileDescriptor), MimeTypes.VIDEO_MP4, 0, 0);
  }

  public boolean isTranscodeRequired() {
    return transcodeRequired;
  }

  @Override
  public void close() throws IOException {
    if (memoryFile != null) {
      memoryFile.close();
    }
  }

  private static int bitRate(long bytes, long duration) {
    return (int) (bytes * 8 / (duration / 1000f));
  }

  private static int getTargetVideoBitRate(long sizeGuideBytes, long duration) {
    sizeGuideBytes -= (duration / 1000d) * AUDIO_BITRATE / 8;

    double targetAttachmentSizeBits = sizeGuideBytes * 8L;

    double bitRateToFixTarget = targetAttachmentSizeBits / (duration / 1000d);
    return Math.max(MINIMUM_TARGET_VIDEO_BITRATE, Math.min(MAXIMUM_TARGET_VIDEO_BITRATE, (int) bitRateToFixTarget));
  }

  private static long getDuration(MediaMetadataRetriever mediaMetadataRetriever) throws VideoSourceException {
    String durationString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
    if (durationString == null) {
      throw new VideoSourceException("Cannot determine duration of video, null meta data");
    }
    try {
      long duration = Long.parseLong(durationString);
      if (duration <= 0) {
        throw new VideoSourceException("Cannot determine duration of video, meta data: " + durationString);
      }
      return duration;
    } catch (NumberFormatException e) {
      throw new VideoSourceException("Cannot determine duration of video, meta data: " + durationString, e);
    }
  }

  private static boolean containsLocation(MediaMetadataRetriever mediaMetadataRetriever) {
    String locationString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
    return locationString != null;
  }

  public interface Progress {
    void onProgress(int percent);
  }

  public interface CancelationSignal {
    boolean isCanceled();
  }

  public final static class Options {
    final long startTimeUs;
    final long endTimeUs;

    public Options(long startTimeUs, long endTimeUs) {
      this.startTimeUs = startTimeUs;
      this.endTimeUs   = endTimeUs;
    }
  }
}
