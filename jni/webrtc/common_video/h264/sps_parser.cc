/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_video/h264/sps_parser.h"

#include "webrtc/common_video/h264/h264_common.h"
#include "webrtc/base/bitbuffer.h"
#include "webrtc/base/bytebuffer.h"
#include "webrtc/base/logging.h"

typedef rtc::Optional<webrtc::SpsParser::SpsState> OptionalSps;

#define RETURN_EMPTY_ON_FAIL(x) \
  if (!(x)) {                   \
    return OptionalSps();       \
  }

namespace webrtc {

// General note: this is based off the 02/2014 version of the H.264 standard.
// You can find it on this page:
// http://www.itu.int/rec/T-REC-H.264

// Unpack RBSP and parse SPS state from the supplied buffer.
rtc::Optional<SpsParser::SpsState> SpsParser::ParseSps(const uint8_t* data,
                                                       size_t length) {
  std::unique_ptr<rtc::Buffer> unpacked_buffer = H264::ParseRbsp(data, length);
  rtc::BitBuffer bit_buffer(unpacked_buffer->data(), unpacked_buffer->size());
  return ParseSpsUpToVui(&bit_buffer);
}

rtc::Optional<SpsParser::SpsState> SpsParser::ParseSpsUpToVui(
    rtc::BitBuffer* buffer) {
  // Now, we need to use a bit buffer to parse through the actual AVC SPS
  // format. See Section 7.3.2.1.1 ("Sequence parameter set data syntax") of the
  // H.264 standard for a complete description.
  // Since we only care about resolution, we ignore the majority of fields, but
  // we still have to actively parse through a lot of the data, since many of
  // the fields have variable size.
  // We're particularly interested in:
  // chroma_format_idc -> affects crop units
  // pic_{width,height}_* -> resolution of the frame in macroblocks (16x16).
  // frame_crop_*_offset -> crop information

  SpsState sps;

  // The golomb values we have to read, not just consume.
  uint32_t golomb_ignored;

  // chroma_format_idc will be ChromaArrayType if separate_colour_plane_flag is
  // 0. It defaults to 1, when not specified.
  uint32_t chroma_format_idc = 1;

  // profile_idc: u(8). We need it to determine if we need to read/skip chroma
  // formats.
  uint8_t profile_idc;
  RETURN_EMPTY_ON_FAIL(buffer->ReadUInt8(&profile_idc));
  // constraint_set0_flag through constraint_set5_flag + reserved_zero_2bits
  // 1 bit each for the flags + 2 bits = 8 bits = 1 byte.
  RETURN_EMPTY_ON_FAIL(buffer->ConsumeBytes(1));
  // level_idc: u(8)
  RETURN_EMPTY_ON_FAIL(buffer->ConsumeBytes(1));
  // seq_parameter_set_id: ue(v)
  RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&golomb_ignored));
  sps.separate_colour_plane_flag = 0;
  // See if profile_idc has chroma format information.
  if (profile_idc == 100 || profile_idc == 110 || profile_idc == 122 ||
      profile_idc == 244 || profile_idc == 44 || profile_idc == 83 ||
      profile_idc == 86 || profile_idc == 118 || profile_idc == 128 ||
      profile_idc == 138 || profile_idc == 139 || profile_idc == 134) {
    // chroma_format_idc: ue(v)
    RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&chroma_format_idc));
    if (chroma_format_idc == 3) {
      // separate_colour_plane_flag: u(1)
      RETURN_EMPTY_ON_FAIL(
          buffer->ReadBits(&sps.separate_colour_plane_flag, 1));
    }
    // bit_depth_luma_minus8: ue(v)
    RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&golomb_ignored));
    // bit_depth_chroma_minus8: ue(v)
    RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&golomb_ignored));
    // qpprime_y_zero_transform_bypass_flag: u(1)
    RETURN_EMPTY_ON_FAIL(buffer->ConsumeBits(1));
    // seq_scaling_matrix_present_flag: u(1)
    uint32_t seq_scaling_matrix_present_flag;
    RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&seq_scaling_matrix_present_flag, 1));
    if (seq_scaling_matrix_present_flag) {
      // seq_scaling_list_present_flags. Either 8 or 12, depending on
      // chroma_format_idc.
      uint32_t seq_scaling_list_present_flags;
      if (chroma_format_idc != 3) {
        RETURN_EMPTY_ON_FAIL(
            buffer->ReadBits(&seq_scaling_list_present_flags, 8));
      } else {
        RETURN_EMPTY_ON_FAIL(
            buffer->ReadBits(&seq_scaling_list_present_flags, 12));
      }
      // We don't support reading the sequence scaling list, and we don't really
      // see/use them in practice, so we'll just reject the full sps if we see
      // any provided.
      if (seq_scaling_list_present_flags > 0) {
        LOG(LS_WARNING) << "SPS contains scaling lists, which are unsupported.";
        return OptionalSps();
      }
    }
  }
  // log2_max_frame_num_minus4: ue(v)
  RETURN_EMPTY_ON_FAIL(
      buffer->ReadExponentialGolomb(&sps.log2_max_frame_num_minus4));
  // pic_order_cnt_type: ue(v)
  RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&sps.pic_order_cnt_type));
  if (sps.pic_order_cnt_type == 0) {
    // log2_max_pic_order_cnt_lsb_minus4: ue(v)
    RETURN_EMPTY_ON_FAIL(
        buffer->ReadExponentialGolomb(&sps.log2_max_pic_order_cnt_lsb_minus4));
  } else if (sps.pic_order_cnt_type == 1) {
    // delta_pic_order_always_zero_flag: u(1)
    RETURN_EMPTY_ON_FAIL(
        buffer->ReadBits(&sps.delta_pic_order_always_zero_flag, 1));
    // offset_for_non_ref_pic: se(v)
    RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&golomb_ignored));
    // offset_for_top_to_bottom_field: se(v)
    RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&golomb_ignored));
    // num_ref_frames_in_pic_order_cnt_cycle: ue(v)
    uint32_t num_ref_frames_in_pic_order_cnt_cycle;
    RETURN_EMPTY_ON_FAIL(
        buffer->ReadExponentialGolomb(&num_ref_frames_in_pic_order_cnt_cycle));
    for (size_t i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; ++i) {
      // offset_for_ref_frame[i]: se(v)
      RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&golomb_ignored));
    }
  }
  // max_num_ref_frames: ue(v)
  RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&sps.max_num_ref_frames));
  // gaps_in_frame_num_value_allowed_flag: u(1)
  RETURN_EMPTY_ON_FAIL(buffer->ConsumeBits(1));
  //
  // IMPORTANT ONES! Now we're getting to resolution. First we read the pic
  // width/height in macroblocks (16x16), which gives us the base resolution,
  // and then we continue on until we hit the frame crop offsets, which are used
  // to signify resolutions that aren't multiples of 16.
  //
  // pic_width_in_mbs_minus1: ue(v)
  uint32_t pic_width_in_mbs_minus1;
  RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&pic_width_in_mbs_minus1));
  // pic_height_in_map_units_minus1: ue(v)
  uint32_t pic_height_in_map_units_minus1;
  RETURN_EMPTY_ON_FAIL(
      buffer->ReadExponentialGolomb(&pic_height_in_map_units_minus1));
  // frame_mbs_only_flag: u(1)
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&sps.frame_mbs_only_flag, 1));
  if (!sps.frame_mbs_only_flag) {
    // mb_adaptive_frame_field_flag: u(1)
    RETURN_EMPTY_ON_FAIL(buffer->ConsumeBits(1));
  }
  // direct_8x8_inference_flag: u(1)
  RETURN_EMPTY_ON_FAIL(buffer->ConsumeBits(1));
  //
  // MORE IMPORTANT ONES! Now we're at the frame crop information.
  //
  // frame_cropping_flag: u(1)
  uint32_t frame_cropping_flag;
  uint32_t frame_crop_left_offset = 0;
  uint32_t frame_crop_right_offset = 0;
  uint32_t frame_crop_top_offset = 0;
  uint32_t frame_crop_bottom_offset = 0;
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&frame_cropping_flag, 1));
  if (frame_cropping_flag) {
    // frame_crop_{left, right, top, bottom}_offset: ue(v)
    RETURN_EMPTY_ON_FAIL(
        buffer->ReadExponentialGolomb(&frame_crop_left_offset));
    RETURN_EMPTY_ON_FAIL(
        buffer->ReadExponentialGolomb(&frame_crop_right_offset));
    RETURN_EMPTY_ON_FAIL(buffer->ReadExponentialGolomb(&frame_crop_top_offset));
    RETURN_EMPTY_ON_FAIL(
        buffer->ReadExponentialGolomb(&frame_crop_bottom_offset));
  }
  // vui_parameters_present_flag: u(1)
  RETURN_EMPTY_ON_FAIL(buffer->ReadBits(&sps.vui_params_present, 1));

  // Far enough! We don't use the rest of the SPS.

  // Start with the resolution determined by the pic_width/pic_height fields.
  sps.width = 16 * (pic_width_in_mbs_minus1 + 1);
  sps.height =
      16 * (2 - sps.frame_mbs_only_flag) * (pic_height_in_map_units_minus1 + 1);

  // Figure out the crop units in pixels. That's based on the chroma format's
  // sampling, which is indicated by chroma_format_idc.
  if (sps.separate_colour_plane_flag || chroma_format_idc == 0) {
    frame_crop_bottom_offset *= (2 - sps.frame_mbs_only_flag);
    frame_crop_top_offset *= (2 - sps.frame_mbs_only_flag);
  } else if (!sps.separate_colour_plane_flag && chroma_format_idc > 0) {
    // Width multipliers for formats 1 (4:2:0) and 2 (4:2:2).
    if (chroma_format_idc == 1 || chroma_format_idc == 2) {
      frame_crop_left_offset *= 2;
      frame_crop_right_offset *= 2;
    }
    // Height multipliers for format 1 (4:2:0).
    if (chroma_format_idc == 1) {
      frame_crop_top_offset *= 2;
      frame_crop_bottom_offset *= 2;
    }
  }
  // Subtract the crop for each dimension.
  sps.width -= (frame_crop_left_offset + frame_crop_right_offset);
  sps.height -= (frame_crop_top_offset + frame_crop_bottom_offset);

  return OptionalSps(sps);
}

}  // namespace webrtc
