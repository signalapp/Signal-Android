/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <limits.h>

#include "webrtc/base/bandwidthsmoother.h"
#include "webrtc/base/gunit.h"

namespace rtc {

static const int kTimeBetweenIncrease = 10;
static const double kPercentIncrease = 1.1;
static const size_t kSamplesCountToAverage = 2;
static const double kMinSampleCountPercent = 1.0;

TEST(BandwidthSmootherTest, TestSampleIncrease) {
  BandwidthSmoother mon(1000,  // initial_bandwidth_guess
                        kTimeBetweenIncrease,
                        kPercentIncrease,
                        kSamplesCountToAverage,
                        kMinSampleCountPercent);

  int bandwidth_sample = 1000;
  EXPECT_EQ(bandwidth_sample, mon.get_bandwidth_estimation());
  bandwidth_sample =
      static_cast<int>(bandwidth_sample * kPercentIncrease);
  EXPECT_FALSE(mon.Sample(9, bandwidth_sample));
  EXPECT_TRUE(mon.Sample(10, bandwidth_sample));
  EXPECT_EQ(bandwidth_sample, mon.get_bandwidth_estimation());
  int next_expected_est =
      static_cast<int>(bandwidth_sample * kPercentIncrease);
  bandwidth_sample *= 2;
  EXPECT_TRUE(mon.Sample(20, bandwidth_sample));
  EXPECT_EQ(next_expected_est, mon.get_bandwidth_estimation());
}

TEST(BandwidthSmootherTest, TestSampleIncreaseFromZero) {
  BandwidthSmoother mon(0,  // initial_bandwidth_guess
                        kTimeBetweenIncrease,
                        kPercentIncrease,
                        kSamplesCountToAverage,
                        kMinSampleCountPercent);

  const int kBandwidthSample = 1000;
  EXPECT_EQ(0, mon.get_bandwidth_estimation());
  EXPECT_FALSE(mon.Sample(9, kBandwidthSample));
  EXPECT_TRUE(mon.Sample(10, kBandwidthSample));
  EXPECT_EQ(kBandwidthSample, mon.get_bandwidth_estimation());
}

TEST(BandwidthSmootherTest, TestSampleDecrease) {
  BandwidthSmoother mon(1000,  // initial_bandwidth_guess
                        kTimeBetweenIncrease,
                        kPercentIncrease,
                        kSamplesCountToAverage,
                        kMinSampleCountPercent);

  const int kBandwidthSample = 999;
  EXPECT_EQ(1000, mon.get_bandwidth_estimation());
  EXPECT_FALSE(mon.Sample(1, kBandwidthSample));
  EXPECT_EQ(1000, mon.get_bandwidth_estimation());
  EXPECT_TRUE(mon.Sample(2, kBandwidthSample));
  EXPECT_EQ(kBandwidthSample, mon.get_bandwidth_estimation());
}

TEST(BandwidthSmootherTest, TestSampleTooFewSamples) {
  BandwidthSmoother mon(1000,  // initial_bandwidth_guess
                        kTimeBetweenIncrease,
                        kPercentIncrease,
                        10,  // 10 samples.
                        0.5);  // 5 min samples.

  const int kBandwidthSample = 500;
  EXPECT_EQ(1000, mon.get_bandwidth_estimation());
  EXPECT_FALSE(mon.Sample(1, kBandwidthSample));
  EXPECT_FALSE(mon.Sample(2, kBandwidthSample));
  EXPECT_FALSE(mon.Sample(3, kBandwidthSample));
  EXPECT_FALSE(mon.Sample(4, kBandwidthSample));
  EXPECT_EQ(1000, mon.get_bandwidth_estimation());
  EXPECT_TRUE(mon.Sample(5, kBandwidthSample));
  EXPECT_EQ(kBandwidthSample, mon.get_bandwidth_estimation());
}

// Disabled for UBSan: https://bugs.chromium.org/p/webrtc/issues/detail?id=5491
#ifdef UNDEFINED_SANITIZER
#define MAYBE_TestSampleRollover DISABLED_TestSampleRollover
#else
#define MAYBE_TestSampleRollover TestSampleRollover
#endif
TEST(BandwidthSmootherTest, MAYBE_TestSampleRollover) {
  const int kHugeBandwidth = 2000000000;  // > INT_MAX/1.1
  BandwidthSmoother mon(kHugeBandwidth,
                        kTimeBetweenIncrease,
                        kPercentIncrease,
                        kSamplesCountToAverage,
                        kMinSampleCountPercent);

  EXPECT_FALSE(mon.Sample(10, INT_MAX));
  EXPECT_FALSE(mon.Sample(11, INT_MAX));
  EXPECT_EQ(kHugeBandwidth, mon.get_bandwidth_estimation());
}

TEST(BandwidthSmootherTest, TestSampleNegative) {
  BandwidthSmoother mon(1000,  // initial_bandwidth_guess
                        kTimeBetweenIncrease,
                        kPercentIncrease,
                        kSamplesCountToAverage,
                        kMinSampleCountPercent);

  EXPECT_FALSE(mon.Sample(10, -1));
  EXPECT_FALSE(mon.Sample(11, -1));
  EXPECT_EQ(1000, mon.get_bandwidth_estimation());
}

}  // namespace rtc
