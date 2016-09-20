/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_audio/window_generator.h"

#include <cstring>

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

TEST(WindowGeneratorTest, KaiserBesselDerived) {
  float window[7];

  memset(window, 0, sizeof(window));

  WindowGenerator::KaiserBesselDerived(0.397856f, 2, window);
  ASSERT_NEAR(window[0], 0.707106f, 1e-6f);
  ASSERT_NEAR(window[1], 0.707106f, 1e-6f);
  ASSERT_NEAR(window[2], 0.0f, 1e-6f);
  ASSERT_NEAR(window[3], 0.0f, 1e-6f);
  ASSERT_NEAR(window[4], 0.0f, 1e-6f);
  ASSERT_NEAR(window[5], 0.0f, 1e-6f);
  ASSERT_NEAR(window[6], 0.0f, 1e-6f);

  WindowGenerator::KaiserBesselDerived(0.397856f, 3, window);
  ASSERT_NEAR(window[0], 0.598066f, 1e-6f);
  ASSERT_NEAR(window[1], 0.922358f, 1e-6f);
  ASSERT_NEAR(window[2], 0.598066f, 1e-6f);
  ASSERT_NEAR(window[3], 0.0f, 1e-6f);
  ASSERT_NEAR(window[4], 0.0f, 1e-6f);
  ASSERT_NEAR(window[5], 0.0f, 1e-6f);
  ASSERT_NEAR(window[6], 0.0f, 1e-6f);

  WindowGenerator::KaiserBesselDerived(0.397856f, 6, window);
  ASSERT_NEAR(window[0], 0.458495038865344f, 1e-6f);
  ASSERT_NEAR(window[1], 0.707106781186548f, 1e-6f);
  ASSERT_NEAR(window[2], 0.888696967101760f, 1e-6f);
  ASSERT_NEAR(window[3], 0.888696967101760f, 1e-6f);
  ASSERT_NEAR(window[4], 0.707106781186548f, 1e-6f);
  ASSERT_NEAR(window[5], 0.458495038865344f, 1e-6f);
  ASSERT_NEAR(window[6], 0.0f, 1e-6f);
}

TEST(WindowGeneratorTest, Hanning) {
  float window[7];

  memset(window, 0, sizeof(window));

  window[0] = -1.0f;
  window[1] = -1.0f;
  WindowGenerator::Hanning(2, window);
  ASSERT_NEAR(window[0], 0.0f, 1e-6f);
  ASSERT_NEAR(window[1], 0.0f, 1e-6f);
  ASSERT_NEAR(window[2], 0.0f, 1e-6f);
  ASSERT_NEAR(window[3], 0.0f, 1e-6f);
  ASSERT_NEAR(window[4], 0.0f, 1e-6f);
  ASSERT_NEAR(window[5], 0.0f, 1e-6f);
  ASSERT_NEAR(window[6], 0.0f, 1e-6f);

  window[0] = -1.0f;
  window[2] = -1.0f;
  WindowGenerator::Hanning(3, window);
  ASSERT_NEAR(window[0], 0.0f, 1e-6f);
  ASSERT_NEAR(window[1], 1.0f, 1e-6f);
  ASSERT_NEAR(window[2], 0.0f, 1e-6f);
  ASSERT_NEAR(window[3], 0.0f, 1e-6f);
  ASSERT_NEAR(window[4], 0.0f, 1e-6f);
  ASSERT_NEAR(window[5], 0.0f, 1e-6f);
  ASSERT_NEAR(window[6], 0.0f, 1e-6f);

  window[0] = -1.0f;
  window[5] = -1.0f;
  WindowGenerator::Hanning(6, window);
  ASSERT_NEAR(window[0], 0.0f, 1e-6f);
  ASSERT_NEAR(window[1], 0.345491f, 1e-6f);
  ASSERT_NEAR(window[2], 0.904508f, 1e-6f);
  ASSERT_NEAR(window[3], 0.904508f, 1e-6f);
  ASSERT_NEAR(window[4], 0.345491f, 1e-6f);
  ASSERT_NEAR(window[5], 0.0f, 1e-6f);
  ASSERT_NEAR(window[6], 0.0f, 1e-6f);
}

}  // namespace webrtc

