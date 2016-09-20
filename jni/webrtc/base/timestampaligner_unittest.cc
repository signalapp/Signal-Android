/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <math.h>

#include <algorithm>

#include "webrtc/base/gunit.h"
#include "webrtc/base/random.h"
#include "webrtc/base/timestampaligner.h"

namespace rtc {

namespace {
// Computes the difference x_k - mean(x), when x_k is the linear sequence x_k =
// k, and the "mean" is plain mean for the first |window_size| samples, followed
// by exponential averaging with weight 1 / |window_size| for each new sample.
// This is needed to predict the effect of camera clock drift on the timestamp
// translation. See the comment on TimestampAligner::UpdateOffset for more
// context.
double MeanTimeDifference(int nsamples, int window_size) {
  if (nsamples <= window_size) {
    // Plain averaging.
    return nsamples / 2.0;
  } else {
    // Exponential convergence towards
    // interval_error * (window_size - 1)
    double alpha = 1.0 - 1.0 / window_size;

    return ((window_size - 1) -
            (window_size / 2.0 - 1) * pow(alpha, nsamples - window_size));
  }
}

}  // Anonymous namespace

class TimestampAlignerTest : public testing::Test {
 protected:
  void TestTimestampFilter(double rel_freq_error) {
    const int64_t kEpoch = 10000;
    const int64_t kJitterUs = 5000;
    const int64_t kIntervalUs = 33333;  // 30 FPS
    const int kWindowSize = 100;
    const int kNumFrames = 3 * kWindowSize;

    int64_t interval_error_us = kIntervalUs * rel_freq_error;
    int64_t system_start_us = rtc::TimeMicros();
    webrtc::Random random(17);

    int64_t prev_translated_time_us = system_start_us;

    for (int i = 0; i < kNumFrames; i++) {
      // Camera time subject to drift.
      int64_t camera_time_us = kEpoch + i * (kIntervalUs + interval_error_us);
      int64_t system_time_us = system_start_us + i * kIntervalUs;
      // And system time readings are subject to jitter.
      int64_t system_measured_us = system_time_us + random.Rand(kJitterUs);

      int64_t offset_us =
          timestamp_aligner_.UpdateOffset(camera_time_us, system_measured_us);

      int64_t filtered_time_us = camera_time_us + offset_us;
      int64_t translated_time_us = timestamp_aligner_.ClipTimestamp(
          filtered_time_us, system_measured_us);

      EXPECT_LE(translated_time_us, system_measured_us);
      EXPECT_GE(translated_time_us, prev_translated_time_us);

      // The relative frequency error contributes to the expected error
      // by a factor which is the difference between the current time
      // and the average of earlier sample times.
      int64_t expected_error_us =
          kJitterUs / 2 +
          rel_freq_error * kIntervalUs * MeanTimeDifference(i, kWindowSize);

      int64_t bias_us = filtered_time_us - translated_time_us;
      EXPECT_GE(bias_us, 0);

      if (i == 0) {
        EXPECT_EQ(translated_time_us, system_measured_us);
      } else {
        EXPECT_NEAR(filtered_time_us, system_time_us + expected_error_us,
                    2.0 * kJitterUs / sqrt(std::max(i, kWindowSize)));
      }
      // If the camera clock runs too fast (rel_freq_error > 0.0), The
      // bias is expected to roughly cancel the expected error from the
      // clock drift, as this grows. Otherwise, it reflects the
      // measurement noise. The tolerances here were selected after some
      // trial and error.
      if (i < 10 || rel_freq_error <= 0.0) {
        EXPECT_LE(bias_us, 3000);
      } else {
        EXPECT_NEAR(bias_us, expected_error_us, 1500);
      }
      prev_translated_time_us = translated_time_us;
    }
  }

 private:
  TimestampAligner timestamp_aligner_;
};

TEST_F(TimestampAlignerTest, AttenuateTimestampJitterNoDrift) {
  TestTimestampFilter(0.0);
}

// 100 ppm is a worst case for a reasonable crystal.
TEST_F(TimestampAlignerTest, AttenuateTimestampJitterSmallPosDrift) {
  TestTimestampFilter(0.0001);
}

TEST_F(TimestampAlignerTest, AttenuateTimestampJitterSmallNegDrift) {
  TestTimestampFilter(-0.0001);
}

// 3000 ppm, 3 ms / s, is the worst observed drift, see
// https://bugs.chromium.org/p/webrtc/issues/detail?id=5456
TEST_F(TimestampAlignerTest, AttenuateTimestampJitterLargePosDrift) {
  TestTimestampFilter(0.003);
}

TEST_F(TimestampAlignerTest, AttenuateTimestampJitterLargeNegDrift) {
  TestTimestampFilter(-0.003);
}

}  // namespace rtc
