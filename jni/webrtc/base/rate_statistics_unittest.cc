/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <algorithm>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/rate_statistics.h"

namespace {

using webrtc::RateStatistics;

const int64_t kWindowMs = 500;

class RateStatisticsTest : public ::testing::Test {
 protected:
  RateStatisticsTest() : stats_(kWindowMs, 8000) {}
  RateStatistics stats_;
};

TEST_F(RateStatisticsTest, TestStrictMode) {
  int64_t now_ms = 0;
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));

  const uint32_t kPacketSize = 1500u;
  const uint32_t kExpectedRateBps = kPacketSize * 1000 * 8;

  // Single data point is not enough for valid estimate.
  stats_.Update(kPacketSize, now_ms++);
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));

  // Expecting 1200 kbps since the window is initially kept small and grows as
  // we have more data.
  stats_.Update(kPacketSize, now_ms);
  EXPECT_EQ(kExpectedRateBps, *stats_.Rate(now_ms));

  stats_.Reset();
  // Expecting 0 after init.
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));

  const int kInterval = 10;
  for (int i = 0; i < 100000; ++i) {
    if (i % kInterval == 0)
      stats_.Update(kPacketSize, now_ms);

    // Approximately 1200 kbps expected. Not exact since when packets
    // are removed we will jump 10 ms to the next packet.
    if (i > kInterval) {
      rtc::Optional<uint32_t> rate = stats_.Rate(now_ms);
      EXPECT_TRUE(static_cast<bool>(rate));
      uint32_t samples = i / kInterval + 1;
      uint64_t total_bits = samples * kPacketSize * 8;
      uint32_t rate_bps = static_cast<uint32_t>((1000 * total_bits) / (i + 1));
      EXPECT_NEAR(rate_bps, *rate, 22000u);
    }
    now_ms += 1;
  }
  now_ms += kWindowMs;
  // The window is 2 seconds. If nothing has been received for that time
  // the estimate should be 0.
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));
}

TEST_F(RateStatisticsTest, IncreasingThenDecreasingBitrate) {
  int64_t now_ms = 0;
  stats_.Reset();
  // Expecting 0 after init.
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));

  stats_.Update(1000, ++now_ms);
  const uint32_t kExpectedBitrate = 8000000;
  // 1000 bytes per millisecond until plateau is reached.
  int prev_error = kExpectedBitrate;
  rtc::Optional<uint32_t> bitrate;
  while (++now_ms < 10000) {
    stats_.Update(1000, now_ms);
    bitrate = stats_.Rate(now_ms);
    EXPECT_TRUE(static_cast<bool>(bitrate));
    int error = kExpectedBitrate - *bitrate;
    error = std::abs(error);
    // Expect the estimation error to decrease as the window is extended.
    EXPECT_LE(error, prev_error + 1);
    prev_error = error;
  }
  // Window filled, expect to be close to 8000000.
  EXPECT_EQ(kExpectedBitrate, *bitrate);

  // 1000 bytes per millisecond until 10-second mark, 8000 kbps expected.
  while (++now_ms < 10000) {
    stats_.Update(1000, now_ms);
    bitrate = stats_.Rate(now_ms);
    EXPECT_EQ(kExpectedBitrate, *bitrate);
  }

  // Zero bytes per millisecond until 0 is reached.
  while (++now_ms < 20000) {
    stats_.Update(0, now_ms);
    rtc::Optional<uint32_t> new_bitrate = stats_.Rate(now_ms);
    if (static_cast<bool>(new_bitrate) && *new_bitrate != *bitrate) {
      // New bitrate must be lower than previous one.
      EXPECT_LT(*new_bitrate, *bitrate);
    } else {
      // 0 kbps expected.
      EXPECT_EQ(0u, *new_bitrate);
      break;
    }
    bitrate = new_bitrate;
  }

  // Zero bytes per millisecond until 20-second mark, 0 kbps expected.
  while (++now_ms < 20000) {
    stats_.Update(0, now_ms);
    EXPECT_EQ(0u, *stats_.Rate(now_ms));
  }
}

TEST_F(RateStatisticsTest, ResetAfterSilence) {
  int64_t now_ms = 0;
  stats_.Reset();
  // Expecting 0 after init.
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));

  const uint32_t kExpectedBitrate = 8000000;
  // 1000 bytes per millisecond until the window has been filled.
  int prev_error = kExpectedBitrate;
  rtc::Optional<uint32_t> bitrate;
  while (++now_ms < 10000) {
    stats_.Update(1000, now_ms);
    bitrate = stats_.Rate(now_ms);
    if (bitrate) {
      int error = kExpectedBitrate - *bitrate;
      error = std::abs(error);
      // Expect the estimation error to decrease as the window is extended.
      EXPECT_LE(error, prev_error + 1);
      prev_error = error;
    }
  }
  // Window filled, expect to be close to 8000000.
  EXPECT_EQ(kExpectedBitrate, *bitrate);

  now_ms += kWindowMs + 1;
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));
  stats_.Update(1000, now_ms);
  ++now_ms;
  stats_.Update(1000, now_ms);
  // We expect two samples of 1000 bytes, and that the bitrate is measured over
  // 500 ms, i.e. 2 * 8 * 1000 / 0.500 = 32000.
  EXPECT_EQ(32000u, *stats_.Rate(now_ms));

  // Reset, add the same samples again.
  stats_.Reset();
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));
  stats_.Update(1000, now_ms);
  ++now_ms;
  stats_.Update(1000, now_ms);
  // We expect two samples of 1000 bytes, and that the bitrate is measured over
  // 2 ms (window size has been reset) i.e. 2 * 8 * 1000 / 0.002 = 8000000.
  EXPECT_EQ(kExpectedBitrate, *stats_.Rate(now_ms));
}

TEST_F(RateStatisticsTest, HandlesChangingWindowSize) {
  int64_t now_ms = 0;
  stats_.Reset();

  // Sanity test window size.
  EXPECT_TRUE(stats_.SetWindowSize(kWindowMs, now_ms));
  EXPECT_FALSE(stats_.SetWindowSize(kWindowMs + 1, now_ms));
  EXPECT_FALSE(stats_.SetWindowSize(0, now_ms));
  EXPECT_TRUE(stats_.SetWindowSize(1, now_ms));
  EXPECT_TRUE(stats_.SetWindowSize(kWindowMs, now_ms));

  // Fill the buffer at a rate of 1 byte / millisecond (8 kbps).
  const int kBatchSize = 10;
  for (int i = 0; i <= kWindowMs; i += kBatchSize)
    stats_.Update(kBatchSize, now_ms += kBatchSize);
  EXPECT_EQ(static_cast<uint32_t>(8000), *stats_.Rate(now_ms));

  // Halve the window size, rate should stay the same.
  EXPECT_TRUE(stats_.SetWindowSize(kWindowMs / 2, now_ms));
  EXPECT_EQ(static_cast<uint32_t>(8000), *stats_.Rate(now_ms));

  // Double the window size again, rate should stay the same. (As the window
  // won't actually expand until new bit and bobs fall into it.
  EXPECT_TRUE(stats_.SetWindowSize(kWindowMs, now_ms));
  EXPECT_EQ(static_cast<uint32_t>(8000), *stats_.Rate(now_ms));

  // Fill the now empty half with bits it twice the rate.
  for (int i = 0; i < kWindowMs / 2; i += kBatchSize)
    stats_.Update(kBatchSize * 2, now_ms += kBatchSize);

  // Rate should have increase be 50%.
  EXPECT_EQ(static_cast<uint32_t>((8000 * 3) / 2), *stats_.Rate(now_ms));
}

TEST_F(RateStatisticsTest, RespectsWindowSizeEdges) {
  int64_t now_ms = 0;
  stats_.Reset();
  // Expecting 0 after init.
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));

  // One byte per ms, using one big sample.
  stats_.Update(kWindowMs, now_ms);
  now_ms += kWindowMs - 2;
  // Shouldn't work! (Only one sample, not full window size.)
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));

  // Window size should be full, and the single data point should be accepted.
  ++now_ms;
  rtc::Optional<uint32_t> bitrate = stats_.Rate(now_ms);
  EXPECT_TRUE(static_cast<bool>(bitrate));
  EXPECT_EQ(1000 * 8u, *bitrate);

  // Add another, now we have twice the bitrate.
  stats_.Update(kWindowMs, now_ms);
  bitrate = stats_.Rate(now_ms);
  EXPECT_TRUE(static_cast<bool>(bitrate));
  EXPECT_EQ(2 * 1000 * 8u, *bitrate);

  // Now that first sample should drop out...
  now_ms += 1;
  bitrate = stats_.Rate(now_ms);
  EXPECT_TRUE(static_cast<bool>(bitrate));
  EXPECT_EQ(1000 * 8u, *bitrate);
}

TEST_F(RateStatisticsTest, HandlesZeroCounts) {
  int64_t now_ms = 0;
  stats_.Reset();
  // Expecting 0 after init.
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));

  stats_.Update(kWindowMs, now_ms);
  now_ms += kWindowMs - 1;
  stats_.Update(0, now_ms);
  rtc::Optional<uint32_t> bitrate = stats_.Rate(now_ms);
  EXPECT_TRUE(static_cast<bool>(bitrate));
  EXPECT_EQ(1000 * 8u, *bitrate);

  // Move window along so first data point falls out.
  ++now_ms;
  bitrate = stats_.Rate(now_ms);
  EXPECT_TRUE(static_cast<bool>(bitrate));
  EXPECT_EQ(0u, *bitrate);

  // Move window so last data point falls out.
  now_ms += kWindowMs;
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));
}

TEST_F(RateStatisticsTest, HandlesQuietPeriods) {
  int64_t now_ms = 0;
  stats_.Reset();
  // Expecting 0 after init.
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));

  stats_.Update(0, now_ms);
  now_ms += kWindowMs - 1;
  rtc::Optional<uint32_t> bitrate = stats_.Rate(now_ms);
  EXPECT_TRUE(static_cast<bool>(bitrate));
  EXPECT_EQ(0u, *bitrate);

  // Move window along so first data point falls out.
  ++now_ms;
  EXPECT_FALSE(static_cast<bool>(stats_.Rate(now_ms)));

  // Move window a long way out.
  now_ms += 2 * kWindowMs;
  stats_.Update(0, now_ms);
  bitrate = stats_.Rate(now_ms);
  EXPECT_TRUE(static_cast<bool>(bitrate));
  EXPECT_EQ(0u, *bitrate);
}
}  // namespace
