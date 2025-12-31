package org.thoughtcrime.securesms.video.videoconverter.muxer;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mp4parser.boxes.iso14496.part1.objectdescriptors.DecoderSpecificInfo;
import org.mp4parser.streaming.StreamingTrack;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.video.interfaces.Muxer;
import org.thoughtcrime.securesms.video.videoconverter.utils.MediaCodecCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;

public final class StreamingMuxer implements Muxer {
  private static final String TAG = Log.tag(StreamingMuxer.class);
  private final OutputStream          outputStream;
  private final List<MediaCodecTrack> tracks = new ArrayList<>();
  private       Mp4Writer             mp4Writer;

  public StreamingMuxer(OutputStream outputStream) {
    this.outputStream = outputStream;
  }

  @Override
  public void start() throws IOException {
    final List<StreamingTrack> source = new ArrayList<>();
    for (MediaCodecTrack track : tracks) {
      source.add((StreamingTrack) track);
    }
    mp4Writer = new Mp4Writer(source, Channels.newChannel(outputStream));
  }

  @Override
  public long stop() throws IOException {
    if (mp4Writer == null) {
      throw new IllegalStateException("calling stop prior to start");
    }
    for (MediaCodecTrack track : tracks) {
      track.finish();
    }
    mp4Writer.close();
    long mdatLength = mp4Writer.getTotalMdatContentLength();

    mp4Writer = null;

    return mdatLength;
  }

  @Override
  public int addTrack(@NonNull MediaFormat format) throws IOException {

    final String mime = format.getString(MediaFormat.KEY_MIME);
    switch (mime) {
      case "video/avc":
        tracks.add(new MediaCodecAvcTrack(format));
        break;
      case "audio/mp4a-latm":
        tracks.add(MediaCodecAacTrack.create(format));
        break;
      case "video/hevc":
        tracks.add(new MediaCodecHevcTrack(format));
        break;
      default:
        throw new IllegalArgumentException("unknown track format");
    }
    return tracks.size() - 1;
  }

  @Override
  public void writeSampleData(int trackIndex, @NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
    tracks.get(trackIndex).writeSampleData(byteBuf, bufferInfo);
  }

  @Override
  public void release() {
  }

  @Override
  public boolean supportsAudioRemux() {
    return true;
  }

  interface MediaCodecTrack {
    void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException;

    void finish() throws IOException;
  }

  static class MediaCodecAvcTrack extends AvcTrack implements MediaCodecTrack {

    MediaCodecAvcTrack(@NonNull MediaFormat format) {
      super(Utils.subBuffer(format.getByteBuffer("csd-0"), 4), Utils.subBuffer(format.getByteBuffer("csd-1"), 4));
    }

    @Override
    public void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
      final List<ByteBuffer> nals = H264Utils.getNals(byteBuf);
      for (ByteBuffer nal : nals) {
        consumeNal(Utils.clone(nal), bufferInfo.presentationTimeUs);
      }
    }

    @Override
    public void finish() throws IOException {
      consumeLastNal();
    }
  }

  static class MediaCodecHevcTrack extends HevcTrack implements MediaCodecTrack {

    MediaCodecHevcTrack(@NonNull MediaFormat format) throws IOException {
      super(H264Utils.getNals(format.getByteBuffer("csd-0")));
    }

    @Override
    public void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
      final List<ByteBuffer> nals = H264Utils.getNals(byteBuf);
      for (ByteBuffer nal : nals) {
        consumeNal(Utils.clone(nal), bufferInfo.presentationTimeUs);
      }
    }

    @Override
    public void finish() throws IOException {
      consumeLastNal();
    }
  }

  static class MediaCodecAacTrack extends AacTrack implements MediaCodecTrack {

    private MediaCodecAacTrack(long avgBitrate, long maxBitrate, int sampleRate, int channelCount, int aacProfile, @Nullable DecoderSpecificInfo decoderSpecificInfo) {
      super(avgBitrate, maxBitrate, sampleRate, channelCount, aacProfile, decoderSpecificInfo);
    }

    public static MediaCodecAacTrack create(@NonNull MediaFormat format) {
      final int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
      final int maxBitrate;
      if (format.containsKey(MediaCodecCompat.MEDIA_FORMAT_KEY_MAX_BIT_RATE)) {
        maxBitrate = format.getInteger(MediaCodecCompat.MEDIA_FORMAT_KEY_MAX_BIT_RATE);
      } else {
        maxBitrate = bitrate;
      }

      final DecoderSpecificInfo filledDecoderSpecificInfo;
      if (format.containsKey(MediaCodecCompat.MEDIA_FORMAT_KEY_MAX_BIT_RATE)) {
        final ByteBuffer csd = format.getByteBuffer(MediaCodecCompat.MEDIA_FORMAT_KEY_CODEC_SPECIFIC_DATA_0);

        DecoderSpecificInfo decoderSpecificInfo = new DecoderSpecificInfo();
        boolean parseSuccess = false;
        try {
          decoderSpecificInfo.parseDetail(csd);
          parseSuccess = true;
        } catch (IOException e) {
          Log.w(TAG, "Could not parse AAC codec-specific data!", e);
        }
        if (parseSuccess) {
          filledDecoderSpecificInfo = decoderSpecificInfo;
        } else {
          filledDecoderSpecificInfo = null;
        }
      } else {
        filledDecoderSpecificInfo = null;
      }

      return new MediaCodecAacTrack(bitrate, maxBitrate,
                                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                                    format.getInteger(MediaFormat.KEY_AAC_PROFILE), filledDecoderSpecificInfo);
    }

    @Override
    public void writeSampleData(@NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) throws IOException {
      final byte[] buffer = new byte[bufferInfo.size];
      byteBuf.position(bufferInfo.offset);
      byteBuf.get(buffer, 0, bufferInfo.size);
      processSample(ByteBuffer.wrap(buffer));
    }

    @Override
    public void finish() {
    }
  }
}
