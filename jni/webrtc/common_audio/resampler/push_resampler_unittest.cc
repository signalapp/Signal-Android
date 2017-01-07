/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/checks.h"  // force defintion of RTC_DCHECK_IS_ON
#include "webrtc/common_audio/resampler/include/push_resampler.h"

// Quality testing of PushResampler is handled through output_mixer_unittest.cc.

namespace webrtc {

// The below tests are temporarily disabled on WEBRTC_WIN due to problems
// with clang debug builds.
// TODO(tommi): Re-enable when we've figured out what the problem is.
// http://crbug.com/615050
#if !defined(WEBRTC_WIN) && defined(__clang__) && !defined(NDEBUG)
TEST(PushResamplerTest, VerifiesInputParameters) {
  PushResampler<int16_t> resampler;
  EXPECT_EQ(0, resampler.InitializeIfNeeded(16000, 16000, 1));
  EXPECT_EQ(0, resampler.InitializeIfNeeded(16000, 16000, 2));
}

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
TEST(PushResamplerTest, VerifiesBadInputParameters1) {
  PushResampler<int16_t> resampler;
  EXPECT_DEATH(resampler.InitializeIfNeeded(-1, 16000, 1),
               "src_sample_rate_hz");
}

TEST(PushResamplerTest, VerifiesBadInputParameters2) {
  PushResampler<int16_t> resampler;
  EXPECT_DEATH(resampler.InitializeIfNeeded(16000, -1, 1),
               "dst_sample_rate_hz");
}

TEST(PushResamplerTest, VerifiesBadInputParameters3) {
  PushResampler<int16_t> resampler;
  EXPECT_DEATH(resampler.InitializeIfNeeded(16000, 16000, 0), "num_channels");
}

TEST(PushResamplerTest, VerifiesBadInputParameters4) {
  PushResampler<int16_t> resampler;
  EXPECT_DEATH(resampler.InitializeIfNeeded(16000, 16000, 3), "num_channels");
}
#endif
#endif

}  // namespace webrtc
