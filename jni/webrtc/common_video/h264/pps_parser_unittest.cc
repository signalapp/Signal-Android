/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_video/h264/pps_parser.h"

#include <limits>

#include "testing/gtest/include/gtest/gtest.h"

#include "webrtc/base/bitbuffer.h"
#include "webrtc/base/buffer.h"
#include "webrtc/common_video/h264/h264_common.h"

namespace webrtc {

static const size_t kPpsBufferMaxSize = 256;
static const uint32_t kIgnored = 0;

void WritePps(const PpsParser::PpsState& pps,
              int slice_group_map_type,
              int num_slice_groups,
              int pic_size_in_map_units,
              rtc::Buffer* out_buffer) {
  uint8_t data[kPpsBufferMaxSize] = {0};
  rtc::BitBufferWriter bit_buffer(data, kPpsBufferMaxSize);

  // pic_parameter_set_id: ue(v)
  bit_buffer.WriteExponentialGolomb(kIgnored);
  // seq_parameter_set_id: ue(v)
  bit_buffer.WriteExponentialGolomb(kIgnored);
  // entropy_coding_mode_flag: u(1)
  bit_buffer.WriteBits(kIgnored, 1);
  // bottom_field_pic_order_in_frame_present_flag: u(1)
  bit_buffer.WriteBits(pps.bottom_field_pic_order_in_frame_present_flag ? 1 : 0,
                       1);
  // num_slice_groups_minus1: ue(v)
  RTC_CHECK_GT(num_slice_groups, 0);
  bit_buffer.WriteExponentialGolomb(num_slice_groups - 1);

  if (num_slice_groups > 1) {
    // slice_group_map_type: ue(v)
    bit_buffer.WriteExponentialGolomb(slice_group_map_type);
    switch (slice_group_map_type) {
      case 0:
        for (int i = 0; i < num_slice_groups; ++i) {
          // run_length_minus1[iGroup]: ue(v)
          bit_buffer.WriteExponentialGolomb(kIgnored);
        }
        break;
      case 2:
        for (int i = 0; i < num_slice_groups; ++i) {
          // top_left[iGroup]: ue(v)
          bit_buffer.WriteExponentialGolomb(kIgnored);
          // bottom_right[iGroup]: ue(v)
          bit_buffer.WriteExponentialGolomb(kIgnored);
        }
        break;
      case 3:
      case 4:
      case 5:
        // slice_group_change_direction_flag: u(1)
        bit_buffer.WriteBits(kIgnored, 1);
        // slice_group_change_rate_minus1: ue(v)
        bit_buffer.WriteExponentialGolomb(kIgnored);
        break;
      case 6: {
        bit_buffer.WriteExponentialGolomb(pic_size_in_map_units - 1);

        uint32_t slice_group_id_bits = 0;
        // If num_slice_groups is not a power of two an additional bit is
        // required
        // to account for the ceil() of log2() below.
        if ((num_slice_groups & (num_slice_groups - 1)) != 0)
          ++slice_group_id_bits;
        while (num_slice_groups > 0) {
          num_slice_groups >>= 1;
          ++slice_group_id_bits;
        }

        for (int i = 0; i < pic_size_in_map_units; ++i) {
          // slice_group_id[i]: u(v)
          // Represented by ceil(log2(num_slice_groups_minus1 + 1)) bits.
          bit_buffer.WriteBits(kIgnored, slice_group_id_bits);
        }
        break;
      }
      default:
        RTC_NOTREACHED();
    }
  }

  // num_ref_idx_l0_default_active_minus1: ue(v)
  bit_buffer.WriteExponentialGolomb(kIgnored);
  // num_ref_idx_l1_default_active_minus1: ue(v)
  bit_buffer.WriteExponentialGolomb(kIgnored);
  // weighted_pred_flag: u(1)
  bit_buffer.WriteBits(pps.weighted_pred_flag ? 1 : 0, 1);
  // weighted_bipred_idc: u(2)
  bit_buffer.WriteBits(pps.weighted_bipred_idc, 2);

  // pic_init_qp_minus26: se(v)
  bit_buffer.WriteSignedExponentialGolomb(pps.pic_init_qp_minus26);
  // pic_init_qs_minus26: se(v)
  bit_buffer.WriteExponentialGolomb(kIgnored);
  // chroma_qp_index_offset: se(v)
  bit_buffer.WriteExponentialGolomb(kIgnored);
  // deblocking_filter_control_present_flag: u(1)
  // constrained_intra_pred_flag: u(1)
  bit_buffer.WriteBits(kIgnored, 2);
  // redundant_pic_cnt_present_flag: u(1)
  bit_buffer.WriteBits(pps.redundant_pic_cnt_present_flag, 1);

  size_t byte_offset;
  size_t bit_offset;
  bit_buffer.GetCurrentOffset(&byte_offset, &bit_offset);
  if (bit_offset > 0) {
    bit_buffer.WriteBits(0, 8 - bit_offset);
    bit_buffer.GetCurrentOffset(&byte_offset, &bit_offset);
  }

  H264::WriteRbsp(data, byte_offset, out_buffer);
}

class PpsParserTest : public ::testing::Test {
 public:
  PpsParserTest() {}
  virtual ~PpsParserTest() {}

  void RunTest() {
    VerifyParsing(generated_pps_, 0, 1, 0);
    const int kMaxSliceGroups = 17;  // Arbitrarily large.
    const int kMaxMapType = 6;
    int slice_group_bits = 0;
    for (int slice_group = 2; slice_group < kMaxSliceGroups; ++slice_group) {
      if ((slice_group & (slice_group - 1)) == 0) {
        // Slice group at a new power of two - increase slice_group_bits.
        ++slice_group_bits;
      }
      for (int map_type = 0; map_type <= kMaxMapType; ++map_type) {
        if (map_type == 1) {
          // TODO(sprang): Implement support for dispersed slice group map type.
          // See 8.2.2.2 Specification for dispersed slice group map type.
          continue;
        } else if (map_type == 6) {
          int max_pic_size = 1 << slice_group_bits;
          for (int pic_size = 1; pic_size < max_pic_size; ++pic_size)
            VerifyParsing(generated_pps_, map_type, slice_group, pic_size);
        } else {
          VerifyParsing(generated_pps_, map_type, slice_group, 0);
        }
      }
    }
  }

  void VerifyParsing(const PpsParser::PpsState& pps,
                     int slice_group_map_type,
                     int num_slice_groups,
                     int pic_size_in_map_units) {
    buffer_.Clear();
    WritePps(pps, slice_group_map_type, num_slice_groups, pic_size_in_map_units,
             &buffer_);
    parsed_pps_ = PpsParser::ParsePps(buffer_.data(), buffer_.size());
    EXPECT_TRUE(static_cast<bool>(parsed_pps_));
    EXPECT_EQ(pps.bottom_field_pic_order_in_frame_present_flag,
              parsed_pps_->bottom_field_pic_order_in_frame_present_flag);
    EXPECT_EQ(pps.weighted_pred_flag, parsed_pps_->weighted_pred_flag);
    EXPECT_EQ(pps.weighted_bipred_idc, parsed_pps_->weighted_bipred_idc);
    EXPECT_EQ(pps.redundant_pic_cnt_present_flag,
              parsed_pps_->redundant_pic_cnt_present_flag);
    EXPECT_EQ(pps.pic_init_qp_minus26, parsed_pps_->pic_init_qp_minus26);
  }

  PpsParser::PpsState generated_pps_;
  rtc::Buffer buffer_;
  rtc::Optional<PpsParser::PpsState> parsed_pps_;
};

TEST_F(PpsParserTest, ZeroPps) {
  RunTest();
}

TEST_F(PpsParserTest, MaxPps) {
  generated_pps_.bottom_field_pic_order_in_frame_present_flag = true;
  generated_pps_.pic_init_qp_minus26 = std::numeric_limits<int32_t>::max();
  generated_pps_.redundant_pic_cnt_present_flag = 1;  // 1 bit value.
  generated_pps_.weighted_bipred_idc = (1 << 2) - 1;  // 2 bit value.
  generated_pps_.weighted_pred_flag = true;
  RunTest();

  generated_pps_.pic_init_qp_minus26 = std::numeric_limits<int32_t>::min() + 1;
  RunTest();
}

}  // namespace webrtc
