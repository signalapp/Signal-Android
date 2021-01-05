package org.thoughtcrime.securesms.video.videoconverter.muxer;

import android.util.SparseIntArray;

import org.mp4parser.boxes.iso14496.part1.objectdescriptors.AudioSpecificConfig;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.DecoderConfigDescriptor;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.ESDescriptor;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.SLConfigDescriptor;
import org.mp4parser.boxes.iso14496.part12.SampleDescriptionBox;
import org.mp4parser.boxes.iso14496.part14.ESDescriptorBox;
import org.mp4parser.boxes.sampleentry.AudioSampleEntry;
import org.mp4parser.streaming.extensions.DefaultSampleFlagsTrackExtension;
import org.mp4parser.streaming.input.AbstractStreamingTrack;
import org.mp4parser.streaming.input.StreamingSampleImpl;

import java.io.IOException;
import java.nio.ByteBuffer;

abstract class AacTrack extends AbstractStreamingTrack {

  private static final SparseIntArray SAMPLING_FREQUENCY_INDEX_MAP = new SparseIntArray();

  static {
    SAMPLING_FREQUENCY_INDEX_MAP.put(96000, 0);
    SAMPLING_FREQUENCY_INDEX_MAP.put(88200, 1);
    SAMPLING_FREQUENCY_INDEX_MAP.put(64000, 2);
    SAMPLING_FREQUENCY_INDEX_MAP.put(48000, 3);
    SAMPLING_FREQUENCY_INDEX_MAP.put(44100, 4);
    SAMPLING_FREQUENCY_INDEX_MAP.put(32000, 5);
    SAMPLING_FREQUENCY_INDEX_MAP.put(24000, 6);
    SAMPLING_FREQUENCY_INDEX_MAP.put(22050, 7);
    SAMPLING_FREQUENCY_INDEX_MAP.put(16000, 8);
    SAMPLING_FREQUENCY_INDEX_MAP.put(12000, 9);
    SAMPLING_FREQUENCY_INDEX_MAP.put(11025, 10);
    SAMPLING_FREQUENCY_INDEX_MAP.put(8000, 11);
  }

  private final SampleDescriptionBox stsd;

  private int sampleRate;

  AacTrack(long avgBitrate, long maxBitrate, int sampleRate, int channelCount, int aacProfile) {
    this.sampleRate = sampleRate;

    final DefaultSampleFlagsTrackExtension defaultSampleFlagsTrackExtension = new DefaultSampleFlagsTrackExtension();
    defaultSampleFlagsTrackExtension.setIsLeading(2);
    defaultSampleFlagsTrackExtension.setSampleDependsOn(2);
    defaultSampleFlagsTrackExtension.setSampleIsDependedOn(2);
    defaultSampleFlagsTrackExtension.setSampleHasRedundancy(2);
    defaultSampleFlagsTrackExtension.setSampleIsNonSyncSample(false);
    this.addTrackExtension(defaultSampleFlagsTrackExtension);

    stsd = new SampleDescriptionBox();
    final AudioSampleEntry audioSampleEntry = new AudioSampleEntry("mp4a");
    if (channelCount == 7) {
      audioSampleEntry.setChannelCount(8);
    } else {
      audioSampleEntry.setChannelCount(channelCount);
    }
    audioSampleEntry.setSampleRate(sampleRate);
    audioSampleEntry.setDataReferenceIndex(1);
    audioSampleEntry.setSampleSize(16);


    final ESDescriptorBox esds       = new ESDescriptorBox();
    ESDescriptor          descriptor = new ESDescriptor();
    descriptor.setEsId(0);

    final SLConfigDescriptor slConfigDescriptor = new SLConfigDescriptor();
    slConfigDescriptor.setPredefined(2);
    descriptor.setSlConfigDescriptor(slConfigDescriptor);

    final DecoderConfigDescriptor decoderConfigDescriptor = new DecoderConfigDescriptor();
    decoderConfigDescriptor.setObjectTypeIndication(0x40 /*Audio ISO/IEC 14496-3*/);
    decoderConfigDescriptor.setStreamType(5 /*audio stream*/);
    decoderConfigDescriptor.setBufferSizeDB(1536);
    decoderConfigDescriptor.setMaxBitRate(maxBitrate);
    decoderConfigDescriptor.setAvgBitRate(avgBitrate);

    final AudioSpecificConfig audioSpecificConfig = new AudioSpecificConfig();
    audioSpecificConfig.setOriginalAudioObjectType(aacProfile);
    audioSpecificConfig.setSamplingFrequencyIndex(SAMPLING_FREQUENCY_INDEX_MAP.get(sampleRate));
    audioSpecificConfig.setChannelConfiguration(channelCount);
    decoderConfigDescriptor.setAudioSpecificInfo(audioSpecificConfig);

    descriptor.setDecoderConfigDescriptor(decoderConfigDescriptor);

    esds.setEsDescriptor(descriptor);

    audioSampleEntry.addBox(esds);
    stsd.addBox(audioSampleEntry);
  }

  public long getTimescale() {
    return sampleRate;
  }

  public String getHandler() {
    return "soun";
  }

  public String getLanguage() {
    return "\u0060\u0060\u0060"; // 0 in Iso639
  }

  public synchronized SampleDescriptionBox getSampleDescriptionBox() {
    return stsd;
  }

  public void close() {
  }

  void processSample(ByteBuffer frame) throws IOException {
    sampleSink.acceptSample(new StreamingSampleImpl(frame, 1024), this);
  }
}
