package org.thoughtcrime.securesms.video.videoconverter.muxer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mp4parser.boxes.iso14496.part12.SampleDescriptionBox;
import org.mp4parser.boxes.iso14496.part15.HevcConfigurationBox;
import org.mp4parser.boxes.iso14496.part15.HevcDecoderConfigurationRecord;
import org.mp4parser.boxes.sampleentry.VisualSampleEntry;
import org.mp4parser.muxer.tracks.CleanInputStream;
import org.mp4parser.muxer.tracks.h265.H265NalUnitHeader;
import org.mp4parser.muxer.tracks.h265.H265NalUnitTypes;
import org.mp4parser.muxer.tracks.h265.SequenceParameterSetRbsp;
import org.mp4parser.streaming.StreamingSample;
import org.mp4parser.streaming.extensions.DimensionTrackExtension;
import org.mp4parser.streaming.extensions.SampleFlagsSampleExtension;
import org.mp4parser.streaming.input.AbstractStreamingTrack;
import org.mp4parser.streaming.input.StreamingSampleImpl;
import org.mp4parser.tools.ByteBufferByteChannel;
import org.mp4parser.tools.IsoTypeReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

abstract class HevcTrack extends AbstractStreamingTrack implements H265NalUnitTypes {

  private final ArrayList<ByteBuffer> bufferedNals = new ArrayList<>();
  private       boolean               vclNalUnitSeenInAU;
  private       boolean               isIdr        = true;
  private       long                  currentPresentationTimeUs;
  private final SampleDescriptionBox  stsd;

  HevcTrack(final @NonNull List<ByteBuffer> csd) throws IOException {
    final ArrayList<ByteBuffer> sps       = new ArrayList<>();
    final ArrayList<ByteBuffer> pps       = new ArrayList<>();
    final ArrayList<ByteBuffer> vps       = new ArrayList<>();
    SequenceParameterSetRbsp    spsStruct = null;
    for (ByteBuffer nal : csd) {
      final H265NalUnitHeader unitHeader = getNalUnitHeader(nal);
      nal.position(0);
      // collect sps/vps/pps
      switch (unitHeader.nalUnitType) {
        case NAL_TYPE_PPS_NUT:
          pps.add(nal.duplicate());
          break;
        case NAL_TYPE_VPS_NUT:
          vps.add(nal.duplicate());
          break;
        case NAL_TYPE_SPS_NUT:
          sps.add(nal.duplicate());
          nal.position(2);
          spsStruct = new SequenceParameterSetRbsp(new CleanInputStream(Channels.newInputStream(new ByteBufferByteChannel(nal.slice()))));
          break;
        case NAL_TYPE_PREFIX_SEI_NUT:
          //new SEIMessage(new BitReaderBuffer(nal.slice()));
          break;
      }
    }

    stsd = new SampleDescriptionBox();
    stsd.addBox(createSampleEntry(sps, pps, vps, spsStruct));

  }

  @Override
  public long getTimescale() {
    return 90000;
  }

  @Override
  public String getHandler() {
    return "vide";
  }

  @Override
  public String getLanguage() {
    return "\u0060\u0060\u0060"; // 0 in Iso639
  }

  @Override
  public SampleDescriptionBox getSampleDescriptionBox() {
    return stsd;
  }

  @Override
  public void close() {
  }

  void consumeLastNal() throws IOException {
    wrapUp(bufferedNals, currentPresentationTimeUs);
  }

  void consumeNal(final @NonNull ByteBuffer nal, final long presentationTimeUs) throws IOException {

    final H265NalUnitHeader unitHeader = getNalUnitHeader(nal);
    final boolean           isVcl      = isVcl(unitHeader);
    //
    if (vclNalUnitSeenInAU) { // we need at least 1 VCL per AU
      // This branch checks if we encountered the start of a samples/AU
      if (isVcl) {
        if ((nal.get(2) & -128) != 0) { // this is: first_slice_segment_in_pic_flag  u(1)
          wrapUp(bufferedNals, presentationTimeUs);
        }
      } else {
        switch (unitHeader.nalUnitType) {
          case NAL_TYPE_PREFIX_SEI_NUT:
          case NAL_TYPE_AUD_NUT:
          case NAL_TYPE_PPS_NUT:
          case NAL_TYPE_VPS_NUT:
          case NAL_TYPE_SPS_NUT:
          case NAL_TYPE_RSV_NVCL41:
          case NAL_TYPE_RSV_NVCL42:
          case NAL_TYPE_RSV_NVCL43:
          case NAL_TYPE_RSV_NVCL44:
          case NAL_TYPE_UNSPEC48:
          case NAL_TYPE_UNSPEC49:
          case NAL_TYPE_UNSPEC50:
          case NAL_TYPE_UNSPEC51:
          case NAL_TYPE_UNSPEC52:
          case NAL_TYPE_UNSPEC53:
          case NAL_TYPE_UNSPEC54:
          case NAL_TYPE_UNSPEC55:

          case NAL_TYPE_EOB_NUT: // a bit special but also causes a sample to be formed
          case NAL_TYPE_EOS_NUT:
            wrapUp(bufferedNals, presentationTimeUs);
            break;
        }
      }
    }


    switch (unitHeader.nalUnitType) {
      case NAL_TYPE_SPS_NUT:
      case NAL_TYPE_VPS_NUT:
      case NAL_TYPE_PPS_NUT:
      case NAL_TYPE_EOB_NUT:
      case NAL_TYPE_EOS_NUT:
      case NAL_TYPE_AUD_NUT:
      case NAL_TYPE_FD_NUT:
        // ignore these
        break;
      default:
        bufferedNals.add(nal);
        break;
    }

    if (isVcl) {
      isIdr              = unitHeader.nalUnitType == NAL_TYPE_IDR_W_RADL || unitHeader.nalUnitType == NAL_TYPE_IDR_N_LP;
      vclNalUnitSeenInAU = true;
    }
  }

  private void wrapUp(final @NonNull List<ByteBuffer> nals, final long presentationTimeUs) throws IOException {

    final long duration = presentationTimeUs - currentPresentationTimeUs;
    currentPresentationTimeUs = presentationTimeUs;

    final StreamingSample sample = new StreamingSampleImpl(
            nals, getTimescale() * Math.max(0, duration) / 1000000L);

    final SampleFlagsSampleExtension sampleFlagsSampleExtension = new SampleFlagsSampleExtension();
    sampleFlagsSampleExtension.setSampleIsNonSyncSample(!isIdr);

    sample.addSampleExtension(sampleFlagsSampleExtension);

    sampleSink.acceptSample(sample, this);

    vclNalUnitSeenInAU = false;
    isIdr              = true;
    nals.clear();
  }

  private static @NonNull H265NalUnitHeader getNalUnitHeader(final @NonNull ByteBuffer nal) {
    nal.position(0);
    final int               nalUnitHeaderValue = IsoTypeReader.readUInt16(nal);
    final H265NalUnitHeader nalUnitHeader      = new H265NalUnitHeader();
    nalUnitHeader.forbiddenZeroFlag    = (nalUnitHeaderValue & 0x8000) >> 15;
    nalUnitHeader.nalUnitType          = (nalUnitHeaderValue & 0x7E00) >> 9;
    nalUnitHeader.nuhLayerId           = (nalUnitHeaderValue & 0x1F8) >> 3;
    nalUnitHeader.nuhTemporalIdPlusOne = (nalUnitHeaderValue & 0x7);
    return nalUnitHeader;
  }

  private @NonNull VisualSampleEntry createSampleEntry(
          final @NonNull ArrayList<ByteBuffer> sps,
          final @NonNull ArrayList<ByteBuffer> pps,
          final @NonNull ArrayList<ByteBuffer> vps,
          final @Nullable SequenceParameterSetRbsp spsStruct)
  {
    final VisualSampleEntry visualSampleEntry = new VisualSampleEntry("hvc1");
    visualSampleEntry.setDataReferenceIndex(1);
    visualSampleEntry.setDepth(24);
    visualSampleEntry.setFrameCount(1);
    visualSampleEntry.setHorizresolution(72);
    visualSampleEntry.setVertresolution(72);
    visualSampleEntry.setCompressorname("HEVC Coding");

    final HevcConfigurationBox hevcConfigurationBox = new HevcConfigurationBox();
    hevcConfigurationBox.getHevcDecoderConfigurationRecord().setConfigurationVersion(1);

    if (spsStruct != null) {
      visualSampleEntry.setWidth(spsStruct.pic_width_in_luma_samples);
      visualSampleEntry.setHeight(spsStruct.pic_height_in_luma_samples);
      final DimensionTrackExtension dte = this.getTrackExtension(DimensionTrackExtension.class);
      if (dte == null) {
        this.addTrackExtension(new DimensionTrackExtension(spsStruct.pic_width_in_luma_samples, spsStruct.pic_height_in_luma_samples));
      }
      final HevcDecoderConfigurationRecord hevcDecoderConfigurationRecord = hevcConfigurationBox.getHevcDecoderConfigurationRecord();
      hevcDecoderConfigurationRecord.setChromaFormat(spsStruct.chroma_format_idc);
      hevcDecoderConfigurationRecord.setGeneral_profile_idc(spsStruct.general_profile_idc);
      hevcDecoderConfigurationRecord.setGeneral_profile_compatibility_flags(spsStruct.general_profile_compatibility_flags);
      hevcDecoderConfigurationRecord.setGeneral_constraint_indicator_flags(spsStruct.general_constraint_indicator_flags);
      hevcDecoderConfigurationRecord.setGeneral_level_idc(spsStruct.general_level_idc);
      hevcDecoderConfigurationRecord.setGeneral_tier_flag(spsStruct.general_tier_flag);
      hevcDecoderConfigurationRecord.setGeneral_profile_space(spsStruct.general_profile_space);
      hevcDecoderConfigurationRecord.setBitDepthChromaMinus8(spsStruct.bit_depth_chroma_minus8);
      hevcDecoderConfigurationRecord.setBitDepthLumaMinus8(spsStruct.bit_depth_luma_minus8);
      hevcDecoderConfigurationRecord.setTemporalIdNested(spsStruct.sps_temporal_id_nesting_flag);
    }

    hevcConfigurationBox.getHevcDecoderConfigurationRecord().setLengthSizeMinusOne(3);

    final HevcDecoderConfigurationRecord.Array vpsArray = new HevcDecoderConfigurationRecord.Array();
    vpsArray.array_completeness = false;
    vpsArray.nal_unit_type      = NAL_TYPE_VPS_NUT;
    vpsArray.nalUnits           = new ArrayList<>();
    for (ByteBuffer vp : vps) {
      vpsArray.nalUnits.add(Utils.toArray(vp));
    }

    final HevcDecoderConfigurationRecord.Array spsArray = new HevcDecoderConfigurationRecord.Array();
    spsArray.array_completeness = false;
    spsArray.nal_unit_type      = NAL_TYPE_SPS_NUT;
    spsArray.nalUnits           = new ArrayList<>();
    for (ByteBuffer sp : sps) {
      spsArray.nalUnits.add(Utils.toArray(sp));
    }

    final HevcDecoderConfigurationRecord.Array ppsArray = new HevcDecoderConfigurationRecord.Array();
    ppsArray.array_completeness = false;
    ppsArray.nal_unit_type      = NAL_TYPE_PPS_NUT;
    ppsArray.nalUnits           = new ArrayList<>();
    for (ByteBuffer pp : pps) {
      ppsArray.nalUnits.add(Utils.toArray(pp));
    }

    hevcConfigurationBox.getArrays().addAll(Arrays.asList(spsArray, vpsArray, ppsArray));

    visualSampleEntry.addBox(hevcConfigurationBox);
    return visualSampleEntry;
  }

  private boolean isVcl(final @NonNull H265NalUnitHeader nalUnitHeader) {
    return nalUnitHeader.nalUnitType >= 0 && nalUnitHeader.nalUnitType <= 31;
  }
}
