/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_BUFFER_LEVEL_FILTER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_BUFFER_LEVEL_FILTER_H_

#include "webrtc/modules/audio_coding/neteq/buffer_level_filter.h"

#include "testing/gmock/include/gmock/gmock.h"

namespace webrtc {

class MockBufferLevelFilter : public BufferLevelFilter {
 public:
  virtual ~MockBufferLevelFilter() { Die(); }
  MOCK_METHOD0(Die,
      void());
  MOCK_METHOD0(Reset,
      void());
  MOCK_METHOD3(Update,
      void(size_t buffer_size_packets, int time_stretched_samples,
           size_t packet_len_samples));
  MOCK_METHOD1(SetTargetBufferLevel,
      void(int target_buffer_level));
  MOCK_CONST_METHOD0(filtered_current_level,
      int());
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_MOCK_MOCK_BUFFER_LEVEL_FILTER_H_
