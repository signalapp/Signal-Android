package org.thoughtcrime.securesms.video.videoconverter.muxer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mp4parser.boxes.iso14496.part12.SampleDescriptionBox;
import org.mp4parser.boxes.iso14496.part15.AvcConfigurationBox;
import org.mp4parser.boxes.sampleentry.VisualSampleEntry;
import org.mp4parser.streaming.SampleExtension;
import org.mp4parser.streaming.StreamingSample;
import org.mp4parser.streaming.extensions.CompositionTimeSampleExtension;
import org.mp4parser.streaming.extensions.CompositionTimeTrackExtension;
import org.mp4parser.streaming.extensions.DimensionTrackExtension;
import org.mp4parser.streaming.extensions.SampleFlagsSampleExtension;
import org.mp4parser.streaming.input.AbstractStreamingTrack;
import org.mp4parser.streaming.input.StreamingSampleImpl;
import org.mp4parser.streaming.input.h264.H264NalUnitHeader;
import org.mp4parser.streaming.input.h264.H264NalUnitTypes;
import org.mp4parser.streaming.input.h264.spspps.PictureParameterSet;
import org.mp4parser.streaming.input.h264.spspps.SeqParameterSet;
import org.mp4parser.streaming.input.h264.spspps.SliceHeader;
import org.signal.core.util.logging.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

abstract class AvcTrack extends AbstractStreamingTrack {

  private static final String TAG = "AvcTrack";

  private       int                   maxDecFrameBuffering = 16;
  private final List<StreamingSample> decFrameBuffer       = new ArrayList<>();
  private final List<StreamingSample> decFrameBuffer2      = new ArrayList<>();

  private final LinkedHashMap<Integer, ByteBuffer>          spsIdToSpsBytes = new LinkedHashMap<>();
  private final LinkedHashMap<Integer, SeqParameterSet>     spsIdToSps      = new LinkedHashMap<>();
  private final LinkedHashMap<Integer, ByteBuffer>          ppsIdToPpsBytes = new LinkedHashMap<>();
  private final LinkedHashMap<Integer, PictureParameterSet> ppsIdToPps      = new LinkedHashMap<>();

  private int timescale = 90000;
  private int frametick = 3000;

  private final SampleDescriptionBox stsd;

  private final List<ByteBuffer>    bufferedNals = new ArrayList<>();
  private       FirstVclNalDetector fvnd;
  private       H264NalUnitHeader   sliceNalUnitHeader;
  private       long                currentPresentationTimeUs;

  AvcTrack(final @NonNull ByteBuffer spsBuffer, final @NonNull ByteBuffer ppsBuffer) {

    handlePPS(ppsBuffer);

    final SeqParameterSet sps = handleSPS(spsBuffer);

    int width = (sps.pic_width_in_mbs_minus1 + 1) * 16;
    int mult  = 2;
    if (sps.frame_mbs_only_flag) {
      mult = 1;
    }
    int height = 16 * (sps.pic_height_in_map_units_minus1 + 1) * mult;
    if (sps.frame_cropping_flag) {
      int chromaArrayType = 0;
      if (!sps.residual_color_transform_flag) {
        chromaArrayType = sps.chroma_format_idc.getId();
      }
      int cropUnitX = 1;
      int cropUnitY = mult;
      if (chromaArrayType != 0) {
        cropUnitX = sps.chroma_format_idc.getSubWidth();
        cropUnitY = sps.chroma_format_idc.getSubHeight() * mult;
      }

      width -= cropUnitX * (sps.frame_crop_left_offset + sps.frame_crop_right_offset);
      height -= cropUnitY * (sps.frame_crop_top_offset + sps.frame_crop_bottom_offset);
    }


    final VisualSampleEntry visualSampleEntry = new VisualSampleEntry("avc1");
    visualSampleEntry.setDataReferenceIndex(1);
    visualSampleEntry.setDepth(24);
    visualSampleEntry.setFrameCount(1);
    visualSampleEntry.setHorizresolution(72);
    visualSampleEntry.setVertresolution(72);
    final DimensionTrackExtension dte = this.getTrackExtension(DimensionTrackExtension.class);
    if (dte == null) {
      this.addTrackExtension(new DimensionTrackExtension(width, height));
    }
    visualSampleEntry.setWidth(width);
    visualSampleEntry.setHeight(height);

    visualSampleEntry.setCompressorname("AVC Coding");

    final AvcConfigurationBox avcConfigurationBox = new AvcConfigurationBox();

    avcConfigurationBox.setSequenceParameterSets(Collections.singletonList(spsBuffer));
    avcConfigurationBox.setPictureParameterSets(Collections.singletonList(ppsBuffer));
    avcConfigurationBox.setAvcLevelIndication(sps.level_idc);
    avcConfigurationBox.setAvcProfileIndication(sps.profile_idc);
    avcConfigurationBox.setBitDepthLumaMinus8(sps.bit_depth_luma_minus8);
    avcConfigurationBox.setBitDepthChromaMinus8(sps.bit_depth_chroma_minus8);
    avcConfigurationBox.setChromaFormat(sps.chroma_format_idc.getId());
    avcConfigurationBox.setConfigurationVersion(1);
    avcConfigurationBox.setLengthSizeMinusOne(3);


    avcConfigurationBox.setProfileCompatibility(
            (sps.constraint_set_0_flag ? 128 : 0) +
            (sps.constraint_set_1_flag ? 64 : 0) +
            (sps.constraint_set_2_flag ? 32 : 0) +
            (sps.constraint_set_3_flag ? 16 : 0) +
            (sps.constraint_set_4_flag ? 8 : 0) +
            (int) (sps.reserved_zero_2bits & 0x3)
    );

    visualSampleEntry.addBox(avcConfigurationBox);
    stsd = new SampleDescriptionBox();
    stsd.addBox(visualSampleEntry);

    int _timescale;
    int _frametick;
    if (sps.vuiParams != null) {
      _timescale = sps.vuiParams.time_scale >> 1; // Not sure why, but I found this in several places, and it works...
      _frametick = sps.vuiParams.num_units_in_tick;
      if (_timescale == 0 || _frametick == 0) {
        Log.w(TAG, "vuiParams contain invalid values: time_scale: " + _timescale + " and frame_tick: " + _frametick + ". Setting frame rate to 30fps");
        _timescale = 0;
        _frametick = 0;
      }
      if (_frametick > 0) {
        if (_timescale / _frametick > 100) {
          Log.w(TAG, "Framerate is " + (_timescale / _frametick) + ". That is suspicious.");
        }
      } else {
        Log.w(TAG, "Frametick is " + _frametick + ". That is suspicious.");
      }
      if (sps.vuiParams.bitstreamRestriction != null) {
        maxDecFrameBuffering = sps.vuiParams.bitstreamRestriction.max_dec_frame_buffering;
      }
    } else {
      Log.w(TAG, "Can't determine frame rate as SPS does not contain vuiParama");
      _timescale = 0;
      _frametick = 0;
    }
    if (_timescale != 0 && _frametick != 0) {
      timescale = _timescale;
      frametick = _frametick;
    }
    if (sps.pic_order_cnt_type == 0) {
      addTrackExtension(new CompositionTimeTrackExtension());
    } else if (sps.pic_order_cnt_type == 1) {
      throw new MuxingException("Have not yet imlemented pic_order_cnt_type 1");
    }
  }

  public long getTimescale() {
    return timescale;
  }

  public String getHandler() {
    return "vide";
  }

  public String getLanguage() {
    return "\u0060\u0060\u0060"; // 0 in Iso639
  }

  public SampleDescriptionBox getSampleDescriptionBox() {
    return stsd;
  }

  public void close() {
  }

  private static H264NalUnitHeader getNalUnitHeader(@NonNull final ByteBuffer nal) {
    final H264NalUnitHeader nalUnitHeader = new H264NalUnitHeader();
    final int               type          = nal.get(0);
    nalUnitHeader.nal_ref_idc   = (type >> 5) & 3;
    nalUnitHeader.nal_unit_type = type & 0x1f;
    return nalUnitHeader;
  }

  void consumeNal(@NonNull final ByteBuffer nal, final long presentationTimeUs) throws IOException {

    final H264NalUnitHeader nalUnitHeader = getNalUnitHeader(nal);
    switch (nalUnitHeader.nal_unit_type) {
      case H264NalUnitTypes.CODED_SLICE_NON_IDR:
      case H264NalUnitTypes.CODED_SLICE_DATA_PART_A:
      case H264NalUnitTypes.CODED_SLICE_DATA_PART_B:
      case H264NalUnitTypes.CODED_SLICE_DATA_PART_C:
      case H264NalUnitTypes.CODED_SLICE_IDR:
        final FirstVclNalDetector current = new FirstVclNalDetector(nal, nalUnitHeader.nal_ref_idc, nalUnitHeader.nal_unit_type);
        if (fvnd != null && fvnd.isFirstInNew(current)) {
          pushSample(createSample(bufferedNals, fvnd.sliceHeader, sliceNalUnitHeader, presentationTimeUs - currentPresentationTimeUs), false, false);
          bufferedNals.clear();
        }
        currentPresentationTimeUs = Math.max(currentPresentationTimeUs, presentationTimeUs);
        sliceNalUnitHeader = nalUnitHeader;
        fvnd = current;
        bufferedNals.add(nal);
        break;

      case H264NalUnitTypes.SEI:
      case H264NalUnitTypes.AU_UNIT_DELIMITER:
        if (fvnd != null) {
          pushSample(createSample(bufferedNals, fvnd.sliceHeader, sliceNalUnitHeader, presentationTimeUs - currentPresentationTimeUs), false, false);
          bufferedNals.clear();
          fvnd = null;
        }
        bufferedNals.add(nal);
        break;

      case H264NalUnitTypes.SEQ_PARAMETER_SET:
        if (fvnd != null) {
          pushSample(createSample(bufferedNals, fvnd.sliceHeader, sliceNalUnitHeader, presentationTimeUs - currentPresentationTimeUs), false, false);
          bufferedNals.clear();
          fvnd = null;
        }
        handleSPS(nal);
        break;

      case H264NalUnitTypes.PIC_PARAMETER_SET:
        if (fvnd != null) {
          pushSample(createSample(bufferedNals, fvnd.sliceHeader, sliceNalUnitHeader, presentationTimeUs - currentPresentationTimeUs), false, false);
          bufferedNals.clear();
          fvnd = null;
        }
        handlePPS(nal);
        break;

      case H264NalUnitTypes.END_OF_SEQUENCE:
      case H264NalUnitTypes.END_OF_STREAM:
        return;

      case H264NalUnitTypes.SEQ_PARAMETER_SET_EXT:
        throw new IOException("Sequence parameter set extension is not yet handled. Needs TLC.");

      default:
        Log.w(TAG, "Unknown NAL unit type: " + nalUnitHeader.nal_unit_type);

    }
  }

  void consumeLastNal() throws IOException {
    pushSample(createSample(bufferedNals, fvnd.sliceHeader, sliceNalUnitHeader, 0), true, true);
  }

  private void pushSample(final StreamingSample ss, final boolean all, final boolean force) throws IOException {
    if (ss != null) {
      decFrameBuffer.add(ss);
    }
    if (all) {
      while (decFrameBuffer.size() > 0) {
        pushSample(null, false, true);
      }
    } else {
      if ((decFrameBuffer.size() - 1 > maxDecFrameBuffering) || force) {
        final StreamingSample                       first   = decFrameBuffer.remove(0);
        final PictureOrderCountType0SampleExtension poct0se = first.getSampleExtension(PictureOrderCountType0SampleExtension.class);
        if (poct0se == null) {
          sampleSink.acceptSample(first, this);
        } else {
          int delay = 0;
          for (StreamingSample streamingSample : decFrameBuffer) {
            if (poct0se.getPoc() > streamingSample.getSampleExtension(PictureOrderCountType0SampleExtension.class).getPoc()) {
              delay++;
            }
          }
          for (StreamingSample streamingSample : decFrameBuffer2) {
            if (poct0se.getPoc() < streamingSample.getSampleExtension(PictureOrderCountType0SampleExtension.class).getPoc()) {
              delay--;
            }
          }
          decFrameBuffer2.add(first);
          if (decFrameBuffer2.size() > maxDecFrameBuffering) {
            decFrameBuffer2.remove(0).removeSampleExtension(PictureOrderCountType0SampleExtension.class);
          }

          first.addSampleExtension(CompositionTimeSampleExtension.create(delay * frametick));
          sampleSink.acceptSample(first, this);
        }
      }
    }

  }

  private SampleFlagsSampleExtension createSampleFlagsSampleExtension(H264NalUnitHeader nu, SliceHeader sliceHeader) {
    final SampleFlagsSampleExtension sampleFlagsSampleExtension = new SampleFlagsSampleExtension();
    if (nu.nal_ref_idc == 0) {
      sampleFlagsSampleExtension.setSampleIsDependedOn(2);
    } else {
      sampleFlagsSampleExtension.setSampleIsDependedOn(1);
    }
    if ((sliceHeader.slice_type == SliceHeader.SliceType.I) || (sliceHeader.slice_type == SliceHeader.SliceType.SI)) {
      sampleFlagsSampleExtension.setSampleDependsOn(2);
    } else {
      sampleFlagsSampleExtension.setSampleDependsOn(1);
    }
    sampleFlagsSampleExtension.setSampleIsNonSyncSample(H264NalUnitTypes.CODED_SLICE_IDR != nu.nal_unit_type);
    return sampleFlagsSampleExtension;
  }

  private PictureOrderCountType0SampleExtension createPictureOrderCountType0SampleExtension(SliceHeader sliceHeader) {
    if (sliceHeader.sps.pic_order_cnt_type == 0) {
      return new PictureOrderCountType0SampleExtension(
              sliceHeader, decFrameBuffer.size() > 0 ?
                           decFrameBuffer.get(decFrameBuffer.size() - 1).getSampleExtension(PictureOrderCountType0SampleExtension.class) :
                           null);
/*            decFrameBuffer.add(ssi);
            if (decFrameBuffer.size() - 1 > maxDecFrameBuffering) { // just added one
                drainDecPictureBuffer(false);
            }*/
    } else if (sliceHeader.sps.pic_order_cnt_type == 1) {
      throw new MuxingException("pic_order_cnt_type == 1 needs to be implemented");
    } else if (sliceHeader.sps.pic_order_cnt_type == 2) {
      return null; // no ctts
    }
    throw new MuxingException("I don't know sliceHeader.sps.pic_order_cnt_type of " + sliceHeader.sps.pic_order_cnt_type);
  }


  private StreamingSample createSample(List<ByteBuffer> nals, SliceHeader sliceHeader, H264NalUnitHeader nu, long sampleDurationNs) {
    final long            sampleDuration = getTimescale() * Math.max(0, sampleDurationNs) / 1000000L;
    final StreamingSample ss             = new StreamingSampleImpl(nals, sampleDuration);
    ss.addSampleExtension(createSampleFlagsSampleExtension(nu, sliceHeader));
    final SampleExtension pictureOrderCountType0SampleExtension = createPictureOrderCountType0SampleExtension(sliceHeader);
    if (pictureOrderCountType0SampleExtension != null) {
      ss.addSampleExtension(pictureOrderCountType0SampleExtension);
    }
    return ss;
  }

  private void handlePPS(final @NonNull ByteBuffer nal) {
    nal.position(1);
    try {
      final PictureParameterSet _pictureParameterSet = PictureParameterSet.read(nal);
      final ByteBuffer          oldPpsSameId         = ppsIdToPpsBytes.get(_pictureParameterSet.pic_parameter_set_id);
      if (oldPpsSameId != null && !oldPpsSameId.equals(nal)) {
        throw new MuxingException("OMG - I got two SPS with same ID but different settings! (AVC3 is the solution)");
      } else {
        ppsIdToPpsBytes.put(_pictureParameterSet.pic_parameter_set_id, nal);
        ppsIdToPps.put(_pictureParameterSet.pic_parameter_set_id, _pictureParameterSet);
      }
    } catch (IOException e) {
      throw new MuxingException("That's surprising to get IOException when working on ByteArrayInputStream", e);
    }


  }

  private @NonNull SeqParameterSet handleSPS(final @NonNull ByteBuffer nal) {
    nal.position(1);
    try {
      final SeqParameterSet seqParameterSet = SeqParameterSet.read(nal);
      final ByteBuffer      oldSpsSameId    = spsIdToSpsBytes.get(seqParameterSet.seq_parameter_set_id);
      if (oldSpsSameId != null && !oldSpsSameId.equals(nal)) {
        throw new MuxingException("OMG - I got two SPS with same ID but different settings!");
      } else {
        spsIdToSpsBytes.put(seqParameterSet.seq_parameter_set_id, nal);
        spsIdToSps.put(seqParameterSet.seq_parameter_set_id, seqParameterSet);
      }
      return seqParameterSet;
    } catch (IOException e) {
      throw new MuxingException("That's surprising to get IOException when working on ByteArrayInputStream", e);
    }

  }

  class FirstVclNalDetector {

    final SliceHeader sliceHeader;
    final int         frame_num;
    final int         pic_parameter_set_id;
    final boolean     field_pic_flag;
    final boolean     bottom_field_flag;
    final int         nal_ref_idc;
    final int         pic_order_cnt_type;
    final int         delta_pic_order_cnt_bottom;
    final int         pic_order_cnt_lsb;
    final int         delta_pic_order_cnt_0;
    final int         delta_pic_order_cnt_1;
    final int         idr_pic_id;

    FirstVclNalDetector(ByteBuffer nal, int nal_ref_idc, int nal_unit_type) {

      SliceHeader sh = new SliceHeader(nal, spsIdToSps, ppsIdToPps, nal_unit_type == 5);
      this.sliceHeader                = sh;
      this.frame_num                  = sh.frame_num;
      this.pic_parameter_set_id       = sh.pic_parameter_set_id;
      this.field_pic_flag             = sh.field_pic_flag;
      this.bottom_field_flag          = sh.bottom_field_flag;
      this.nal_ref_idc                = nal_ref_idc;
      this.pic_order_cnt_type         = spsIdToSps.get(ppsIdToPps.get(sh.pic_parameter_set_id).seq_parameter_set_id).pic_order_cnt_type;
      this.delta_pic_order_cnt_bottom = sh.delta_pic_order_cnt_bottom;
      this.pic_order_cnt_lsb          = sh.pic_order_cnt_lsb;
      this.delta_pic_order_cnt_0      = sh.delta_pic_order_cnt_0;
      this.delta_pic_order_cnt_1      = sh.delta_pic_order_cnt_1;
      this.idr_pic_id                 = sh.idr_pic_id;
    }

    boolean isFirstInNew(FirstVclNalDetector nu) {
      if (nu.frame_num != frame_num) {
        return true;
      }
      if (nu.pic_parameter_set_id != pic_parameter_set_id) {
        return true;
      }
      if (nu.field_pic_flag != field_pic_flag) {
        return true;
      }
      if (nu.field_pic_flag) {
        if (nu.bottom_field_flag != bottom_field_flag) {
          return true;
        }
      }
      if (nu.nal_ref_idc != nal_ref_idc) {
        return true;
      }
      if (nu.pic_order_cnt_type == 0 && pic_order_cnt_type == 0) {
        if (nu.pic_order_cnt_lsb != pic_order_cnt_lsb) {
          return true;
        }
        if (nu.delta_pic_order_cnt_bottom != delta_pic_order_cnt_bottom) {
          return true;
        }
      }
      if (nu.pic_order_cnt_type == 1 && pic_order_cnt_type == 1) {
        if (nu.delta_pic_order_cnt_0 != delta_pic_order_cnt_0) {
          return true;
        }
        if (nu.delta_pic_order_cnt_1 != delta_pic_order_cnt_1) {
          return true;
        }
      }
      return false;
    }
  }

  static class PictureOrderCountType0SampleExtension implements SampleExtension {
    int picOrderCntMsb;
    int picOrderCountLsb;

    PictureOrderCountType0SampleExtension(final @NonNull SliceHeader currentSlice, final @Nullable PictureOrderCountType0SampleExtension previous) {
      int prevPicOrderCntLsb = 0;
      int prevPicOrderCntMsb = 0;
      if (previous != null) {
        prevPicOrderCntLsb = previous.picOrderCountLsb;
        prevPicOrderCntMsb = previous.picOrderCntMsb;
      }

      final int maxPicOrderCountLsb = (1 << (currentSlice.sps.log2_max_pic_order_cnt_lsb_minus4 + 4));
      // System.out.print(" pic_order_cnt_lsb " + pic_order_cnt_lsb + " " + max_pic_order_count);
      picOrderCountLsb = currentSlice.pic_order_cnt_lsb;
      picOrderCntMsb   = 0;
      if ((picOrderCountLsb < prevPicOrderCntLsb) && ((prevPicOrderCntLsb - picOrderCountLsb) >= (maxPicOrderCountLsb / 2))) {
        picOrderCntMsb = prevPicOrderCntMsb + maxPicOrderCountLsb;
      } else if ((picOrderCountLsb > prevPicOrderCntLsb) && ((picOrderCountLsb - prevPicOrderCntLsb) > (maxPicOrderCountLsb / 2))) {
        picOrderCntMsb = prevPicOrderCntMsb - maxPicOrderCountLsb;
      } else {
        picOrderCntMsb = prevPicOrderCntMsb;
      }
    }

    int getPoc() {
      return picOrderCntMsb + picOrderCountLsb;
    }

    @NonNull
    @Override
    public String toString() {
      return "picOrderCntMsb=" + picOrderCntMsb + ", picOrderCountLsb=" + picOrderCountLsb;
    }
  }
}
