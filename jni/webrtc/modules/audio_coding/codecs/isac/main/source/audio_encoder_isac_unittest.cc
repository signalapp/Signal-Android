/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <limits>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/codecs/isac/main/include/audio_encoder_isac.h"

namespace webrtc {

namespace {

void TestBadConfig(const AudioEncoderIsac::Config& config) {
  EXPECT_FALSE(config.IsOk());
}

void TestGoodConfig(const AudioEncoderIsac::Config& config) {
  EXPECT_TRUE(config.IsOk());
  AudioEncoderIsac aei(config);
}

// Wrap subroutine calls that test things in this, so that the error messages
// will be accompanied by stack traces that make it possible to tell which
// subroutine invocation caused the failure.
#define S(x) do { SCOPED_TRACE(#x); x; } while (0)

}  // namespace

TEST(AudioEncoderIsacTest, TestConfigBitrate) {
  AudioEncoderIsac::Config config;

  // The default value is some real, positive value.
  EXPECT_GT(config.bit_rate, 1);
  S(TestGoodConfig(config));

  // 0 is another way to ask for the default value.
  config.bit_rate = 0;
  S(TestGoodConfig(config));

  // Try some unreasonable values and watch them fail.
  config.bit_rate = -1;
  S(TestBadConfig(config));
  config.bit_rate = 1;
  S(TestBadConfig(config));
  config.bit_rate = std::numeric_limits<int>::max();
  S(TestBadConfig(config));
}

}  // namespace webrtc
