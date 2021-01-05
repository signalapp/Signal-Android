package org.thoughtcrime.securesms.video;

import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.media.MediaInput;
import org.thoughtcrime.securesms.video.videoconverter.EncodingException;
import org.thoughtcrime.securesms.video.videoconverter.MediaConverter;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.Locale;

@RequiresApi(26)
public final class StreamingTranscoder {

  private static final String TAG = Log.tag(StreamingTranscoder.class);

  private final           MediaDataSource                dataSource;
  private final           long                           upperSizeLimit;
  private final           long                           inSize;
  private final           long                           duration;
  private final           int                            inputBitRate;
  private final           VideoBitRateCalculator.Quality targetQuality;
  private final           long                           memoryFileEstimate;
  private final           boolean                        transcodeRequired;
  private final           long                           fileSizeEstimate;
  private final @Nullable TranscoderOptions              options;

  /**
   * @param upperSizeLimit A upper size to transcode to. The actual output size can be up to 10% smaller.
   */
  public StreamingTranscoder(@NonNull MediaDataSource dataSource,
                             @Nullable TranscoderOptions options,
                             long upperSizeLimit)
      throws IOException, VideoSourceException
  {
    this.dataSource = dataSource;
    this.options    = options;

    final MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
    try {
      mediaMetadataRetriever.setDataSource(dataSource);
    } catch (RuntimeException e) {
      Log.w(TAG, "Unable to read datasource", e);
      throw new VideoSourceException("Unable to read datasource", e);
    }

    this.inSize         = dataSource.getSize();
    this.duration       = getDuration(mediaMetadataRetriever);
    this.inputBitRate   = VideoBitRateCalculator.bitRate(inSize, duration);
    this.targetQuality  = new VideoBitRateCalculator(upperSizeLimit).getTargetQuality(duration, inputBitRate);
    this.upperSizeLimit = upperSizeLimit;

    this.transcodeRequired = inputBitRate >= targetQuality.getTargetTotalBitRate() * 1.2 || inSize > upperSizeLimit || containsLocation(mediaMetadataRetriever) || options != null;
    if (!transcodeRequired) {
      Log.i(TAG, "Video is within 20% of target bitrate, below the size limit, contained no location metadata or custom options.");
    }

    this.fileSizeEstimate   = targetQuality.getFileSizeEstimate();
    this.memoryFileEstimate = (long) (fileSizeEstimate * 1.1);
  }

  public void transcode(@NonNull Progress progress,
                        @NonNull OutputStream stream,
                        @Nullable TranscoderCancelationSignal cancelationSignal)
      throws IOException, EncodingException
  {
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
                             numberFormat.format(targetQuality.getTargetVideoBitRate()),
                             numberFormat.format(targetQuality.getTargetAudioBitRate()),
                             numberFormat.format(targetQuality.getTargetTotalBitRate()),
                             targetQuality.getOutputResolution(),
                             durationSec,
                             numberFormat.format(upperSizeLimit / 1024),
                             numberFormat.format(fileSizeEstimate / 1024),
                             numberFormat.format(inSize / 1024),
                             numberFormat.format(inputBitRate)));

    if (fileSizeEstimate > upperSizeLimit) {
      throw new VideoSizeException("Size constraints could not be met!");
    }

    final long startTime = System.currentTimeMillis();

    final MediaConverter          converter               = new MediaConverter();
    final LimitedSizeOutputStream limitedSizeOutputStream = new LimitedSizeOutputStream(stream, upperSizeLimit);

    converter.setInput(new MediaInput.MediaDataSourceMediaInput(dataSource));
    converter.setOutput(limitedSizeOutputStream);
    converter.setVideoResolution(targetQuality.getOutputResolution());
    converter.setVideoBitrate(targetQuality.getTargetVideoBitRate());
    converter.setAudioBitrate(targetQuality.getTargetAudioBitRate());

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

    long  outSize           = limitedSizeOutputStream.written;
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
                             numberFormat.format(VideoBitRateCalculator.bitRate(outSize, duration))));

    if (outSize > upperSizeLimit) {
      throw new VideoSizeException("Size constraints could not be met!");
    }

    stream.flush();
  }

  public boolean isTranscodeRequired() {
    return transcodeRequired;
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

  private static class LimitedSizeOutputStream extends FilterOutputStream {

    private final long sizeLimit;
    private       long written;

    LimitedSizeOutputStream(@NonNull OutputStream inner, long sizeLimit) {
      super(inner);
      this.sizeLimit = sizeLimit;
    }

    @Override public void write(int b) throws IOException {
      incWritten(1);
      out.write(b);
    }

    @Override public void write(byte[] b, int off, int len) throws IOException {
      incWritten(len);
      out.write(b, off, len);
    }

    private void incWritten(int len) throws IOException {
      long newWritten = written + len;
      if (newWritten > sizeLimit) {
        Log.w(TAG, String.format(Locale.US, "File size limit hit. Wrote %d, tried to write %d more. Limit is %d", written, len, sizeLimit));
        throw new VideoSizeException("File size limit hit");
      }
      written = newWritten;
    }
  }
}
