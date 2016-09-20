/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_video/h264/sps_parser.h"

#include "testing/gtest/include/gtest/gtest.h"

#include "webrtc/base/arraysize.h"
#include "webrtc/base/bitbuffer.h"
#include "webrtc/base/buffer.h"
#include "webrtc/common_video/h264/h264_common.h"

namespace webrtc {

// Example SPS can be generated with ffmpeg. Here's an example set of commands,
// runnable on OS X:
// 1) Generate a video, from the camera:
// ffmpeg -f avfoundation -i "0" -video_size 640x360 camera.mov
//
// 2) Scale the video to the desired size:
// ffmpeg -i camera.mov -vf scale=640x360 scaled.mov
//
// 3) Get just the H.264 bitstream in AnnexB:
// ffmpeg -i scaled.mov -vcodec copy -vbsf h264_mp4toannexb -an out.h264
//
// 4) Open out.h264 and find the SPS, generally everything between the first
// two start codes (0 0 0 1 or 0 0 1). The first byte should be 0x67,
// which should be stripped out before being passed to the parser.

static const size_t kSpsBufferMaxSize = 256;

// Generates a fake SPS with basically everything empty but the width/height.
// Pass in a buffer of at least kSpsBufferMaxSize.
// The fake SPS that this generates also always has at least one emulation byte
// at offset 2, since the first two bytes are always 0, and has a 0x3 as the
// level_idc, to make sure the parser doesn't eat all 0x3 bytes.
void GenerateFakeSps(uint16_t width, uint16_t height, rtc::Buffer* out_buffer) {
  uint8_t rbsp[kSpsBufferMaxSize] = {0};
  rtc::BitBufferWriter writer(rbsp, kSpsBufferMaxSize);
  // Profile byte.
  writer.WriteUInt8(0);
  // Constraint sets and reserved zero bits.
  writer.WriteUInt8(0);
  // level_idc.
  writer.WriteUInt8(0x3u);
  // seq_paramter_set_id.
  writer.WriteExponentialGolomb(0);
  // Profile is not special, so we skip all the chroma format settings.

  // Now some bit magic.
  // log2_max_frame_num_minus4: ue(v). 0 is fine.
  writer.WriteExponentialGolomb(0);
  // pic_order_cnt_type: ue(v). 0 is the type we want.
  writer.WriteExponentialGolomb(0);
  // log2_max_pic_order_cnt_lsb_minus4: ue(v). 0 is fine.
  writer.WriteExponentialGolomb(0);
  // max_num_ref_frames: ue(v). 0 is fine.
  writer.WriteExponentialGolomb(0);
  // gaps_in_frame_num_value_allowed_flag: u(1).
  writer.WriteBits(0, 1);
  // Next are width/height. First, calculate the mbs/map_units versions.
  uint16_t width_in_mbs_minus1 = (width + 15) / 16 - 1;

  // For the height, we're going to define frame_mbs_only_flag, so we need to
  // divide by 2. See the parser for the full calculation.
  uint16_t height_in_map_units_minus1 = ((height + 15) / 16 - 1) / 2;
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
  writer.WriteExponentialGolomb(((16 - (width % 16)) % 16) / 2);
  writer.WriteExponentialGolomb(0);
  // Top/bottom.
  writer.WriteExponentialGolomb(((16 - (height % 16)) % 16) / 2);
  writer.WriteExponentialGolomb(0);

  // vui_parameters_present_flag: u(1)
  writer.WriteBits(0, 1);

  // Get the number of bytes written (including the last partial byte).
  size_t byte_count, bit_offset;
  writer.GetCurrentOffset(&byte_count, &bit_offset);
  if (bit_offset > 0) {
    byte_count++;
  }

  H264::WriteRbsp(rbsp, byte_count, out_buffer);
}

class H264SpsParserTest : public ::testing::Test {
 public:
  H264SpsParserTest() {}
  virtual ~H264SpsParserTest() {}

  rtc::Optional<SpsParser::SpsState> sps_;
};

TEST_F(H264SpsParserTest, TestSampleSPSHdLandscape) {
  // SPS for a 1280x720 camera capture from ffmpeg on osx. Contains
  // emulation bytes but no cropping.
  const uint8_t buffer[] = {0x7A, 0x00, 0x1F, 0xBC, 0xD9, 0x40, 0x50, 0x05,
                            0xBA, 0x10, 0x00, 0x00, 0x03, 0x00, 0xC0, 0x00,
                            0x00, 0x2A, 0xE0, 0xF1, 0x83, 0x19, 0x60};
  EXPECT_TRUE(
      static_cast<bool>(sps_ = SpsParser::ParseSps(buffer, arraysize(buffer))));
  EXPECT_EQ(1280u, sps_->width);
  EXPECT_EQ(720u, sps_->height);
}

TEST_F(H264SpsParserTest, TestSampleSPSVgaLandscape) {
  // SPS for a 640x360 camera capture from ffmpeg on osx. Contains emulation
  // bytes and cropping (360 isn't divisible by 16).
  const uint8_t buffer[] = {0x7A, 0x00, 0x1E, 0xBC, 0xD9, 0x40, 0xA0, 0x2F,
                            0xF8, 0x98, 0x40, 0x00, 0x00, 0x03, 0x01, 0x80,
                            0x00, 0x00, 0x56, 0x83, 0xC5, 0x8B, 0x65, 0x80};
  EXPECT_TRUE(
      static_cast<bool>(sps_ = SpsParser::ParseSps(buffer, arraysize(buffer))));
  EXPECT_EQ(640u, sps_->width);
  EXPECT_EQ(360u, sps_->height);
}

TEST_F(H264SpsParserTest, TestSampleSPSWeirdResolution) {
  // SPS for a 200x400 camera capture from ffmpeg on osx. Horizontal and
  // veritcal crop (neither dimension is divisible by 16).
  const uint8_t buffer[] = {0x7A, 0x00, 0x0D, 0xBC, 0xD9, 0x43, 0x43, 0x3E,
                            0x5E, 0x10, 0x00, 0x00, 0x03, 0x00, 0x60, 0x00,
                            0x00, 0x15, 0xA0, 0xF1, 0x42, 0x99, 0x60};
  EXPECT_TRUE(
      static_cast<bool>(sps_ = SpsParser::ParseSps(buffer, arraysize(buffer))));
  EXPECT_EQ(200u, sps_->width);
  EXPECT_EQ(400u, sps_->height);
}

TEST_F(H264SpsParserTest, TestSyntheticSPSQvgaLandscape) {
  rtc::Buffer buffer;
  GenerateFakeSps(320u, 180u, &buffer);
  EXPECT_TRUE(static_cast<bool>(
      sps_ = SpsParser::ParseSps(buffer.data(), buffer.size())));
  EXPECT_EQ(320u, sps_->width);
  EXPECT_EQ(180u, sps_->height);
}

TEST_F(H264SpsParserTest, TestSyntheticSPSWeirdResolution) {
  rtc::Buffer buffer;
  GenerateFakeSps(156u, 122u, &buffer);
  EXPECT_TRUE(static_cast<bool>(
      sps_ = SpsParser::ParseSps(buffer.data(), buffer.size())));
  EXPECT_EQ(156u, sps_->width);
  EXPECT_EQ(122u, sps_->height);
}

}  // namespace webrtc
