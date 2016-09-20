/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_VIDEO_H264_PPS_PARSER_H_
#define WEBRTC_COMMON_VIDEO_H264_PPS_PARSER_H_

#include "webrtc/base/common.h"
#include "webrtc/base/optional.h"

namespace rtc {
class BitBuffer;
}

namespace webrtc {

// A class for parsing out picture parameter set (PPS) data from a H264 NALU.
class PpsParser {
 public:
  // The parsed state of the PPS. Only some select values are stored.
  // Add more as they are actually needed.
  struct PpsState {
    PpsState() = default;

    bool bottom_field_pic_order_in_frame_present_flag = false;
    bool weighted_pred_flag = false;
    uint32_t weighted_bipred_idc = false;
    uint32_t redundant_pic_cnt_present_flag = 0;
    int pic_init_qp_minus26 = 0;
  };

  // Unpack RBSP and parse PPS state from the supplied buffer.
  static rtc::Optional<PpsState> ParsePps(const uint8_t* data, size_t length);

 protected:
  // Parse the PPS state, for a bit buffer where RBSP decoding has already been
  // performed.
  static rtc::Optional<PpsState> ParseInternal(rtc::BitBuffer* bit_buffer);
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_VIDEO_H264_PPS_PARSER_H_
