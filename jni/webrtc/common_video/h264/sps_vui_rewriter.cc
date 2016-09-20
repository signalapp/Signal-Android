/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#include "webrtc/common_video/h264/sps_vui_rewriter.h"

#include <algorithm>
#include <memory>

#include "webrtc/base/bitbuffer.h"
#include "webrtc/base/checks.h"
#include "webrtc/base/logging.h"

#include "webrtc/common_video/h264/h264_common.h"
#include "webrtc/common_video/h264/sps_parser.h"

namespace webrtc {

// The maximum expected growth from adding a VUI to the SPS. It's actually
// closer to 24 or so, but better safe than sorry.
const size_t kMaxVuiSpsIncrease = 64;

#define RETURN_FALSE_ON_FAIL(x)                                  \
  if (!(x)) {                                                    \
    LOG_F(LS_ERROR) << " (line:" << __LINE__ << ") FAILED: " #x; \
    return false;                                                \
  }

#define COPY_UINT8(src, dest, tmp)                   \
  do {                                               \
    RETURN_FALSE_ON_FAIL((src)->ReadUInt8(&tmp));    \
    if (dest)                                        \
      RETURN_FALSE_ON_FAIL((dest)->WriteUInt8(tmp)); \
  } while (0)

#define COPY_EXP_GOLOMB(src, dest, tmp)                          \
  do {                                                           \
    RETURN_FALSE_ON_FAIL((src)->ReadExponentialGolomb(&tmp));    \
    if (dest)                                                    \
      RETURN_FALSE_ON_FAIL((dest)->WriteExponentialGolomb(tmp)); \
  } while (0)

#define COPY_BITS(src, dest, tmp, bits)                   \
  do {                                                    \
    RETURN_FALSE_ON_FAIL((src)->ReadBits(&tmp, bits));    \
    if (dest)                                             \
      RETURN_FALSE_ON_FAIL((dest)->WriteBits(tmp, bits)); \
  } while (0)

typedef const SpsParser::SpsState& Sps;

bool CopyAndRewriteVui(Sps sps,
                       rtc::BitBuffer* source,
                       rtc::BitBufferWriter* destination,
                       SpsVuiRewriter::ParseResult* out_vui_rewritten);
bool CopyHrdParameters(rtc::BitBuffer* source,
                       rtc::BitBufferWriter* destination);
bool AddBitstreamRestriction(rtc::BitBufferWriter* destination,
                             uint32_t max_num_ref_frames);
bool CopyRemainingBits(rtc::BitBuffer* source,
                       rtc::BitBufferWriter* destination);

SpsVuiRewriter::ParseResult SpsVuiRewriter::ParseAndRewriteSps(
    const uint8_t* buffer,
    size_t length,
    rtc::Optional<SpsParser::SpsState>* sps,
    rtc::Buffer* destination) {
  rtc::BitBuffer source_buffer(buffer, length);
  rtc::Optional<SpsParser::SpsState> sps_state =
      SpsParser::ParseSpsUpToVui(&source_buffer);
  if (!sps_state)
    return ParseResult::kFailure;

  *sps = sps_state;

  if (sps_state->pic_order_cnt_type >= 2) {
    // No need to rewrite VUI in this case.
    return ParseResult::kPocOk;
  }

  // We're going to completely muck up alignment, so we need a BitBuffer to
  // write with.
  rtc::Buffer out_buffer(length + kMaxVuiSpsIncrease);
  rtc::BitBufferWriter sps_writer(out_buffer.data(), out_buffer.size());

  // Check how far the SpsParser has read, and copy that data in bulk.
  size_t byte_offset;
  size_t bit_offset;
  source_buffer.GetCurrentOffset(&byte_offset, &bit_offset);
  memcpy(out_buffer.data(), buffer,
         byte_offset + (bit_offset > 0 ? 1 : 0));  // OK to copy the last bits.

  // SpsParser will have read the vui_params_present flag, which we want to
  // modify, so back off a bit;
  if (bit_offset == 0) {
    --byte_offset;
    bit_offset = 7;
  } else {
    --bit_offset;
  }
  sps_writer.Seek(byte_offset, bit_offset);

  ParseResult vui_updated;
  if (!CopyAndRewriteVui(*sps_state, &source_buffer, &sps_writer,
                         &vui_updated)) {
    LOG(LS_ERROR) << "Failed to parse/copy SPS VUI.";
    return ParseResult::kFailure;
  }

  if (vui_updated == ParseResult::kVuiOk) {
    // No update necessary after all, just return.
    return vui_updated;
  }

  if (!CopyRemainingBits(&source_buffer, &sps_writer)) {
    LOG(LS_ERROR) << "Failed to parse/copy SPS VUI.";
    return ParseResult::kFailure;
  }

  // Pad up to next byte with zero bits.
  sps_writer.GetCurrentOffset(&byte_offset, &bit_offset);
  if (bit_offset > 0) {
    sps_writer.WriteBits(0, 8 - bit_offset);
    ++byte_offset;
    bit_offset = 0;
  }

  RTC_DCHECK(byte_offset <= length + kMaxVuiSpsIncrease);
  RTC_CHECK(destination != nullptr);

  out_buffer.SetSize(byte_offset);

  // Write updates SPS to destination with added RBSP
  H264::WriteRbsp(out_buffer.data(), out_buffer.size(), destination);

  return ParseResult::kVuiRewritten;
}

bool CopyAndRewriteVui(Sps sps,
                       rtc::BitBuffer* source,
                       rtc::BitBufferWriter* destination,
                       SpsVuiRewriter::ParseResult* out_vui_rewritten) {
  uint32_t golomb_tmp;
  uint32_t bits_tmp;

  //
  // vui_parameters_present_flag: u(1)
  //
  RETURN_FALSE_ON_FAIL(destination->WriteBits(1, 1));

  // ********* IMPORTANT! **********
  // Now we're at the VUI, so we want to (1) add it if it isn't present, and
  // (2) rewrite frame reordering values so no reordering is allowed.
  if (!sps.vui_params_present) {
    // Write a simple VUI with the parameters we want and 0 for all other flags.
    // There are 8 flags to be off before the bitstream restriction flag.
    RETURN_FALSE_ON_FAIL(destination->WriteBits(0, 8));
    // bitstream_restriction_flag: u(1)
    RETURN_FALSE_ON_FAIL(destination->WriteBits(1, 1));
    RETURN_FALSE_ON_FAIL(
        AddBitstreamRestriction(destination, sps.max_num_ref_frames));
  } else {
    // Parse out the full VUI.
    // aspect_ratio_info_present_flag: u(1)
    COPY_BITS(source, destination, bits_tmp, 1);
    if (bits_tmp == 1) {
      // aspect_ratio_idc: u(8)
      COPY_BITS(source, destination, bits_tmp, 8);
      if (bits_tmp == 255u) {  // Extended_SAR
        // sar_width/sar_height: u(16) each.
        COPY_BITS(source, destination, bits_tmp, 32);
      }
    }
    // overscan_info_present_flag: u(1)
    COPY_BITS(source, destination, bits_tmp, 1);
    if (bits_tmp == 1) {
      // overscan_appropriate_flag: u(1)
      COPY_BITS(source, destination, bits_tmp, 1);
    }
    // video_signal_type_present_flag: u(1)
    COPY_BITS(source, destination, bits_tmp, 1);
    if (bits_tmp == 1) {
      // video_format + video_full_range_flag: u(3) + u(1)
      COPY_BITS(source, destination, bits_tmp, 4);
      // colour_description_present_flag: u(1)
      COPY_BITS(source, destination, bits_tmp, 1);
      if (bits_tmp == 1) {
        // colour_primaries, transfer_characteristics, matrix_coefficients:
        // u(8) each.
        COPY_BITS(source, destination, bits_tmp, 24);
      }
    }
    // chroma_loc_info_present_flag: u(1)
    COPY_BITS(source, destination, bits_tmp, 1);
    if (bits_tmp == 1) {
      // chroma_sample_loc_type_(top|bottom)_field: ue(v) each.
      COPY_EXP_GOLOMB(source, destination, golomb_tmp);
      COPY_EXP_GOLOMB(source, destination, golomb_tmp);
    }
    // timing_info_present_flag: u(1)
    COPY_BITS(source, destination, bits_tmp, 1);
    if (bits_tmp == 1) {
      // num_units_in_tick, time_scale: u(32) each
      COPY_BITS(source, destination, bits_tmp, 32);
      COPY_BITS(source, destination, bits_tmp, 32);
      // fixed_frame_rate_flag: u(1)
      COPY_BITS(source, destination, bits_tmp, 1);
    }
    // nal_hrd_parameters_present_flag: u(1)
    uint32_t nal_hrd_parameters_present_flag;
    COPY_BITS(source, destination, nal_hrd_parameters_present_flag, 1);
    if (nal_hrd_parameters_present_flag == 1) {
      RETURN_FALSE_ON_FAIL(CopyHrdParameters(source, destination));
    }
    // vcl_hrd_parameters_present_flag: u(1)
    uint32_t vcl_hrd_parameters_present_flag;
    COPY_BITS(source, destination, vcl_hrd_parameters_present_flag, 1);
    if (vcl_hrd_parameters_present_flag == 1) {
      RETURN_FALSE_ON_FAIL(CopyHrdParameters(source, destination));
    }
    if (nal_hrd_parameters_present_flag == 1 ||
        vcl_hrd_parameters_present_flag == 1) {
      // low_delay_hrd_flag: u(1)
      COPY_BITS(source, destination, bits_tmp, 1);
    }
    // pic_struct_present_flag: u(1)
    COPY_BITS(source, destination, bits_tmp, 1);

    // bitstream_restriction_flag: u(1)
    uint32_t bitstream_restriction_flag;
    RETURN_FALSE_ON_FAIL(source->ReadBits(&bitstream_restriction_flag, 1));
    RETURN_FALSE_ON_FAIL(destination->WriteBits(1, 1));
    if (bitstream_restriction_flag == 0) {
      // We're adding one from scratch.
      RETURN_FALSE_ON_FAIL(
          AddBitstreamRestriction(destination, sps.max_num_ref_frames));
    } else {
      // We're replacing.
      // motion_vectors_over_pic_boundaries_flag: u(1)
      COPY_BITS(source, destination, bits_tmp, 1);
      // max_bytes_per_pic_denom: ue(v)
      COPY_EXP_GOLOMB(source, destination, golomb_tmp);
      // max_bits_per_mb_denom: ue(v)
      COPY_EXP_GOLOMB(source, destination, golomb_tmp);
      // log2_max_mv_length_horizontal: ue(v)
      COPY_EXP_GOLOMB(source, destination, golomb_tmp);
      // log2_max_mv_length_vertical: ue(v)
      COPY_EXP_GOLOMB(source, destination, golomb_tmp);
      // ********* IMPORTANT! **********
      // The next two are the ones we need to set to low numbers:
      // max_num_reorder_frames: ue(v)
      // max_dec_frame_buffering: ue(v)
      // However, if they are already set to no greater than the numbers we
      // want, then we don't need to be rewriting.
      uint32_t max_num_reorder_frames, max_dec_frame_buffering;
      RETURN_FALSE_ON_FAIL(
          source->ReadExponentialGolomb(&max_num_reorder_frames));
      RETURN_FALSE_ON_FAIL(
          source->ReadExponentialGolomb(&max_dec_frame_buffering));
      if (max_num_reorder_frames == 0 &&
          max_dec_frame_buffering <= sps.max_num_ref_frames) {
        LOG(LS_INFO) << "VUI bitstream already contains an optimal VUI.";
        *out_vui_rewritten = SpsVuiRewriter::ParseResult::kVuiOk;
        return true;
      }
      RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(0));
      RETURN_FALSE_ON_FAIL(
          destination->WriteExponentialGolomb(sps.max_num_ref_frames));
    }
  }
  *out_vui_rewritten = SpsVuiRewriter::ParseResult::kVuiRewritten;
  return true;
}

// Copies a VUI HRD parameters segment.
bool CopyHrdParameters(rtc::BitBuffer* source,
                       rtc::BitBufferWriter* destination) {
  uint32_t golomb_tmp;
  uint32_t bits_tmp;

  // cbp_cnt_minus1: ue(v)
  uint32_t cbp_cnt_minus1;
  COPY_EXP_GOLOMB(source, destination, cbp_cnt_minus1);
  // bit_rate_scale and cbp_size_scale: u(4) each
  COPY_BITS(source, destination, bits_tmp, 8);
  for (size_t i = 0; i <= cbp_cnt_minus1; ++i) {
    // bit_rate_value_minus1 and cbp_size_value_minus1: ue(v) each
    COPY_EXP_GOLOMB(source, destination, golomb_tmp);
    COPY_EXP_GOLOMB(source, destination, golomb_tmp);
    // cbr_flag: u(1)
    COPY_BITS(source, destination, bits_tmp, 1);
  }
  // initial_cbp_removal_delay_length_minus1: u(5)
  COPY_BITS(source, destination, bits_tmp, 5);
  // cbp_removal_delay_length_minus1: u(5)
  COPY_BITS(source, destination, bits_tmp, 5);
  // dbp_output_delay_length_minus1: u(5)
  COPY_BITS(source, destination, bits_tmp, 5);
  // time_offset_length: u(5)
  COPY_BITS(source, destination, bits_tmp, 5);
  return true;
}

// These functions are similar to webrtc::H264SpsParser::Parse, and based on the
// same version of the H.264 standard. You can find it here:
// http://www.itu.int/rec/T-REC-H.264

// Adds a bitstream restriction VUI segment.
bool AddBitstreamRestriction(rtc::BitBufferWriter* destination,
                             uint32_t max_num_ref_frames) {
  // motion_vectors_over_pic_boundaries_flag: u(1)
  // Default is 1 when not present.
  RETURN_FALSE_ON_FAIL(destination->WriteBits(1, 1));
  // max_bytes_per_pic_denom: ue(v)
  // Default is 2 when not present.
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(2));
  // max_bits_per_mb_denom: ue(v)
  // Default is 1 when not present.
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(1));
  // log2_max_mv_length_horizontal: ue(v)
  // log2_max_mv_length_vertical: ue(v)
  // Both default to 16 when not present.
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(16));
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(16));

  // ********* IMPORTANT! **********
  // max_num_reorder_frames: ue(v)
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(0));
  // max_dec_frame_buffering: ue(v)
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(max_num_ref_frames));
  return true;
}

bool CopyRemainingBits(rtc::BitBuffer* source,
                       rtc::BitBufferWriter* destination) {
  uint32_t bits_tmp;
  // Try to get at least the destination aligned.
  if (source->RemainingBitCount() > 0 && source->RemainingBitCount() % 8 != 0) {
    size_t misaligned_bits = source->RemainingBitCount() % 8;
    COPY_BITS(source, destination, bits_tmp, misaligned_bits);
  }
  while (source->RemainingBitCount() > 0) {
    size_t count = std::min(static_cast<size_t>(32u),
                            static_cast<size_t>(source->RemainingBitCount()));
    COPY_BITS(source, destination, bits_tmp, count);
  }
  // TODO(noahric): The last byte could be all zeroes now, which we should just
  // strip.
  return true;
}

}  // namespace webrtc
