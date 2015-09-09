/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdlib.h>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_audio/vad/vad_unittest.h"
#include "webrtc/typedefs.h"

extern "C" {
#include "webrtc/common_audio/vad/vad_core.h"
#include "webrtc/common_audio/vad/vad_sp.h"
}

namespace {

TEST_F(VadTest, vad_sp) {
  VadInstT* self = reinterpret_cast<VadInstT*>(malloc(sizeof(VadInstT)));
  const int kMaxFrameLenSp = 960;  // Maximum frame length in this unittest.
  int16_t zeros[kMaxFrameLenSp] = { 0 };
  int32_t state[2] = { 0 };
  int16_t data_in[kMaxFrameLenSp];
  int16_t data_out[kMaxFrameLenSp];

  // We expect the first value to be 1600 as long as |frame_counter| is zero,
  // which is true for the first iteration.
  static const int16_t kReferenceMin[32] = {
      1600, 720, 509, 512, 532, 552, 570, 588,
       606, 624, 642, 659, 675, 691, 707, 723,
      1600, 544, 502, 522, 542, 561, 579, 597,
       615, 633, 651, 667, 683, 699, 715, 731
  };

  // Construct a speech signal that will trigger the VAD in all modes. It is
  // known that (i * i) will wrap around, but that doesn't matter in this case.
  for (int16_t i = 0; i < kMaxFrameLenSp; ++i) {
    data_in[i] = (i * i);
  }
  // Input values all zeros, expect all zeros out.
  WebRtcVad_Downsampling(zeros, data_out, state, kMaxFrameLenSp);
  EXPECT_EQ(0, state[0]);
  EXPECT_EQ(0, state[1]);
  for (int16_t i = 0; i < kMaxFrameLenSp / 2; ++i) {
    EXPECT_EQ(0, data_out[i]);
  }
  // Make a simple non-zero data test.
  WebRtcVad_Downsampling(data_in, data_out, state, kMaxFrameLenSp);
  EXPECT_EQ(207, state[0]);
  EXPECT_EQ(2270, state[1]);

  ASSERT_EQ(0, WebRtcVad_InitCore(self));
  // TODO(bjornv): Replace this part of the test with taking values from an
  // array and calculate the reference value here. Make sure the values are not
  // ordered.
  for (int16_t i = 0; i < 16; ++i) {
    int16_t value = 500 * (i + 1);
    for (int j = 0; j < kNumChannels; ++j) {
      // Use values both above and below initialized value.
      EXPECT_EQ(kReferenceMin[i], WebRtcVad_FindMinimum(self, value, j));
      EXPECT_EQ(kReferenceMin[i + 16], WebRtcVad_FindMinimum(self, 12000, j));
    }
    self->frame_counter++;
  }

  free(self);
}
}  // namespace
