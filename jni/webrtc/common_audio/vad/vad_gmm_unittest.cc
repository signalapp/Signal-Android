/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_audio/vad/vad_unittest.h"
#include "webrtc/typedefs.h"

extern "C" {
#include "webrtc/common_audio/vad/vad_gmm.h"
}

namespace {

TEST_F(VadTest, vad_gmm) {
  int16_t delta = 0;
  // Input value at mean.
  EXPECT_EQ(1048576, WebRtcVad_GaussianProbability(0, 0, 128, &delta));
  EXPECT_EQ(0, delta);
  EXPECT_EQ(1048576, WebRtcVad_GaussianProbability(16, 128, 128, &delta));
  EXPECT_EQ(0, delta);
  EXPECT_EQ(1048576, WebRtcVad_GaussianProbability(-16, -128, 128, &delta));
  EXPECT_EQ(0, delta);

  // Largest possible input to give non-zero probability.
  EXPECT_EQ(1024, WebRtcVad_GaussianProbability(59, 0, 128, &delta));
  EXPECT_EQ(7552, delta);
  EXPECT_EQ(1024, WebRtcVad_GaussianProbability(75, 128, 128, &delta));
  EXPECT_EQ(7552, delta);
  EXPECT_EQ(1024, WebRtcVad_GaussianProbability(-75, -128, 128, &delta));
  EXPECT_EQ(-7552, delta);

  // Too large input, should give zero probability.
  EXPECT_EQ(0, WebRtcVad_GaussianProbability(105, 0, 128, &delta));
  EXPECT_EQ(13440, delta);
}
}  // namespace
