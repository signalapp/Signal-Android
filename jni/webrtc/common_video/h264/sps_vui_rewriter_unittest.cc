/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <vector>

#include "testing/gtest/include/gtest/gtest.h"

#include "webrtc/base/bitbuffer.h"
#include "webrtc/base/buffer.h"
#include "webrtc/base/fileutils.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/pathutils.h"
#include "webrtc/base/stream.h"

#include "webrtc/common_video/h264/sps_vui_rewriter.h"
#include "webrtc/common_video/h264/h264_common.h"

namespace webrtc {

enum SpsMode {
  kNoRewriteRequired_PocCorrect,
  kNoRewriteRequired_VuiOptimal,
  kRewriteRequired_NoVui,
  kRewriteRequired_NoBitstreamRestriction,
  kRewriteRequired_VuiSuboptimal,
};

static const size_t kSpsBufferMaxSize = 256;
static const size_t kWidth = 640;
static const size_t kHeight = 480;

// Generates a fake SPS with basically everything empty and with characteristics
// based off SpsMode.
// Pass in a buffer of at least kSpsBufferMaxSize.
// The fake SPS that this generates also always has at least one emulation byte
// at offset 2, since the first two bytes are always 0, and has a 0x3 as the
// level_idc, to make sure the parser doesn't eat all 0x3 bytes.
void GenerateFakeSps(SpsMode mode, rtc::Buffer* out_buffer) {
  uint8_t rbsp[kSpsBufferMaxSize] = {0};
  rtc::BitBufferWriter writer(rbsp, kSpsBufferMaxSize);
  // Profile byte.
  writer.WriteUInt8(0);
  // Constraint sets and reserved zero bits.
  writer.WriteUInt8(0);
  // level_idc.
  writer.WriteUInt8(3);
  // seq_paramter_set_id.
  writer.WriteExponentialGolomb(0);
  // Profile is not special, so we skip all the chroma format settings.

  // Now some bit magic.
  // log2_max_frame_num_minus4: ue(v). 0 is fine.
  writer.WriteExponentialGolomb(0);
  // pic_order_cnt_type: ue(v).
  // POC type 2 is the one that doesn't need to be rewritten.
  if (mode == kNoRewriteRequired_PocCorrect) {
    writer.WriteExponentialGolomb(2);
  } else {
    writer.WriteExponentialGolomb(0);
    // log2_max_pic_order_cnt_lsb_minus4: ue(v). 0 is fine.
    writer.WriteExponentialGolomb(0);
  }
  // max_num_ref_frames: ue(v). Use 1, to make optimal/suboptimal more obvious.
  writer.WriteExponentialGolomb(1);
  // gaps_in_frame_num_value_allowed_flag: u(1).
  writer.WriteBits(0, 1);
  // Next are width/height. First, calculate the mbs/map_units versions.
  uint16_t width_in_mbs_minus1 = (kWidth + 15) / 16 - 1;

  // For the height, we're going to define frame_mbs_only_flag, so we need to
  // divide by 2. See the parser for the full calculation.
  uint16_t height_in_map_units_minus1 = ((kHeight + 15) / 16 - 1) / 2;
  // Write each as ue(v).
  writer.WriteExponentialGolomb(width_in_mbs_minus1);
  writer.WriteExponentialGolomb(height_in_map_units_minus1);
  // frame_mbs_only_flag: u(1). Needs to be false.
  writer.WriteBits(0, 1);
  // mb_adaptive_frame_field_flag: u(1).
  writer.WriteBits(0, 1);
  // direct_8x8_inferene_flag: u(1).
  writer.WriteBits(0, 1);
  // frame_cropping_flag: u(1). 1, so we can supply crop.
  writer.WriteBits(1, 1);
  // Now we write the left/right/top/bottom crop. For simplicity, we'll put all
  // the crop at the left/top.
  // We picked a 4:2:0 format, so the crops are 1/2 the pixel crop values.
  // Left/right.
  writer.WriteExponentialGolomb(((16 - (kWidth % 16)) % 16) / 2);
  writer.WriteExponentialGolomb(0);
  // Top/bottom.
  writer.WriteExponentialGolomb(((16 - (kHeight % 16)) % 16) / 2);
  writer.WriteExponentialGolomb(0);

  // Finally! The VUI.
  // vui_parameters_present_flag: u(1)
  if (mode == kNoRewriteRequired_PocCorrect || mode == kRewriteRequired_NoVui) {
    writer.WriteBits(0, 1);
  } else {
    writer.WriteBits(1, 1);
    // VUI time. 8 flags to ignore followed by the bitstream restriction flag.
    writer.WriteBits(0, 8);
    if (mode == kRewriteRequired_NoBitstreamRestriction) {
      writer.WriteBits(0, 1);
    } else {
      writer.WriteBits(1, 1);
      // Write some defaults. Shouldn't matter for parsing, though.
      // motion_vectors_over_pic_boundaries_flag: u(1)
      writer.WriteBits(1, 1);
      // max_bytes_per_pic_denom: ue(v)
      writer.WriteExponentialGolomb(2);
      // max_bits_per_mb_denom: ue(v)
      writer.WriteExponentialGolomb(1);
      // log2_max_mv_length_horizontal: ue(v)
      // log2_max_mv_length_vertical: ue(v)
      writer.WriteExponentialGolomb(16);
      writer.WriteExponentialGolomb(16);

      // Next are the limits we care about.
      // max_num_reorder_frames: ue(v)
      // max_dec_frame_buffering: ue(v)
      if (mode == kRewriteRequired_VuiSuboptimal) {
        writer.WriteExponentialGolomb(4);
        writer.WriteExponentialGolomb(4);
      } else if (kNoRewriteRequired_VuiOptimal) {
        writer.WriteExponentialGolomb(0);
        writer.WriteExponentialGolomb(1);
      }
    }
  }

  // Get the number of bytes written (including the last partial byte).
  size_t byte_count, bit_offset;
  writer.GetCurrentOffset(&byte_count, &bit_offset);
  if (bit_offset > 0) {
    byte_count++;
  }

  // Write the NALU header and type; {0 0 0 1} and 7 for the SPS header type.
  uint8_t header[] = {0, 0, 0, 1, 7};
  out_buffer->AppendData(header, sizeof(header));

  H264::WriteRbsp(rbsp, byte_count, out_buffer);
}

void TestSps(SpsMode mode, SpsVuiRewriter::ParseResult expected_parse_result) {
  rtc::LogMessage::LogToDebug(rtc::LS_VERBOSE);
  rtc::Buffer buffer;
  GenerateFakeSps(mode, &buffer);
  std::vector<H264::NaluIndex> start_offsets =
      H264::FindNaluIndices(buffer.data(), buffer.size());
  EXPECT_EQ(1u, start_offsets.size());
  H264::NaluIndex index = start_offsets[0];

  H264::NaluType nal_type =
      H264::ParseNaluType(buffer[index.payload_start_offset]);
  EXPECT_EQ(H264::kSps, nal_type);
  index.payload_start_offset += H264::kNaluTypeSize;
  index.payload_size -= H264::kNaluTypeSize;

  std::unique_ptr<rtc::Buffer> rbsp_decoded =
      H264::ParseRbsp(&buffer[index.payload_start_offset], index.payload_size);
  rtc::Optional<SpsParser::SpsState> sps;
  rtc::Buffer out_buffer;
  SpsVuiRewriter::ParseResult result = SpsVuiRewriter::ParseAndRewriteSps(
      rbsp_decoded->data(), rbsp_decoded->size(), &sps, &out_buffer);
  EXPECT_EQ(expected_parse_result, result);
}

#define REWRITE_TEST(test_name, mode, expected_parse_result) \
  TEST(SpsVuiRewriterTest, test_name) { TestSps(mode, expected_parse_result); }

REWRITE_TEST(PocCorrect,
             kNoRewriteRequired_PocCorrect,
             SpsVuiRewriter::ParseResult::kPocOk);
REWRITE_TEST(VuiAlreadyOptimal,
             kNoRewriteRequired_VuiOptimal,
             SpsVuiRewriter::ParseResult::kVuiOk);
REWRITE_TEST(RewriteFullVui,
             kRewriteRequired_NoVui,
             SpsVuiRewriter::ParseResult::kVuiRewritten);
REWRITE_TEST(AddBitstreamRestriction,
             kRewriteRequired_NoBitstreamRestriction,
             SpsVuiRewriter::ParseResult::kVuiRewritten);
REWRITE_TEST(RewriteSuboptimalVui,
             kRewriteRequired_VuiSuboptimal,
             SpsVuiRewriter::ParseResult::kVuiRewritten);

}  // namespace webrtc
