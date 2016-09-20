/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "testing/gtest/include/gtest/gtest.h"

#include "webrtc/common_video/include/bitrate_adjuster.h"
#include "webrtc/system_wrappers/include/clock.h"

namespace webrtc {

class BitrateAdjusterTest : public ::testing::Test {
 public:
  BitrateAdjusterTest()
      : clock_(0),
        adjuster_(&clock_, kMinAdjustedBitratePct, kMaxAdjustedBitratePct) {}

  // Simulate an output bitrate for one update cycle of BitrateAdjuster.
  void SimulateBitrateBps(uint32_t bitrate_bps) {
    const uint32_t update_interval_ms =
        BitrateAdjuster::kBitrateUpdateIntervalMs;
    const uint32_t update_frame_interval =
        BitrateAdjuster::kBitrateUpdateFrameInterval;
    // Round up frame interval so we get one cycle passes.
    const uint32_t frame_interval_ms =
        (update_interval_ms + update_frame_interval - 1) /
        update_frame_interval;
    const size_t frame_size_bytes =
        (bitrate_bps * frame_interval_ms) / (8 * 1000);
    for (size_t i = 0; i < update_frame_interval; ++i) {
      clock_.AdvanceTimeMilliseconds(frame_interval_ms);
      adjuster_.Update(frame_size_bytes);
    }
  }

  uint32_t GetTargetBitrateBpsPct(float pct) {
    return pct * adjuster_.GetTargetBitrateBps();
  }

  void VerifyAdjustment() {
    // The adjusted bitrate should be between the estimated bitrate and the
    // target bitrate within clamp.
    uint32_t target_bitrate_bps = adjuster_.GetTargetBitrateBps();
    uint32_t adjusted_bitrate_bps = adjuster_.GetAdjustedBitrateBps();
    uint32_t estimated_bitrate_bps =
        adjuster_.GetEstimatedBitrateBps().value_or(target_bitrate_bps);
    uint32_t adjusted_lower_bound_bps =
        GetTargetBitrateBpsPct(kMinAdjustedBitratePct);
    uint32_t adjusted_upper_bound_bps =
        GetTargetBitrateBpsPct(kMaxAdjustedBitratePct);
    EXPECT_LE(adjusted_bitrate_bps, adjusted_upper_bound_bps);
    EXPECT_GE(adjusted_bitrate_bps, adjusted_lower_bound_bps);
    if (estimated_bitrate_bps > target_bitrate_bps) {
      EXPECT_LT(adjusted_bitrate_bps, target_bitrate_bps);
    }
  }

 protected:
  static const float kMinAdjustedBitratePct;
  static const float kMaxAdjustedBitratePct;
  SimulatedClock clock_;
  BitrateAdjuster adjuster_;
};

const float BitrateAdjusterTest::kMinAdjustedBitratePct = .5f;
const float BitrateAdjusterTest::kMaxAdjustedBitratePct = .95f;

TEST_F(BitrateAdjusterTest, VaryingBitrates) {
  const uint32_t target_bitrate_bps = 640000;
  adjuster_.SetTargetBitrateBps(target_bitrate_bps);

  // Grossly overshoot for a little while. Adjusted bitrate should decrease.
  uint32_t actual_bitrate_bps = 2 * target_bitrate_bps;
  uint32_t last_adjusted_bitrate_bps = 0;
  uint32_t adjusted_bitrate_bps = 0;

  SimulateBitrateBps(actual_bitrate_bps);
  VerifyAdjustment();
  last_adjusted_bitrate_bps = adjuster_.GetAdjustedBitrateBps();

  SimulateBitrateBps(actual_bitrate_bps);
  VerifyAdjustment();
  adjusted_bitrate_bps = adjuster_.GetAdjustedBitrateBps();
  EXPECT_LE(adjusted_bitrate_bps, last_adjusted_bitrate_bps);
  last_adjusted_bitrate_bps = adjusted_bitrate_bps;
  // After two cycles we should've stabilized and hit the lower bound.
  EXPECT_EQ(GetTargetBitrateBpsPct(kMinAdjustedBitratePct),
            adjusted_bitrate_bps);

  // Simulate encoder settling down. Adjusted bitrate should increase.
  SimulateBitrateBps(target_bitrate_bps);
  adjusted_bitrate_bps = adjuster_.GetAdjustedBitrateBps();
  VerifyAdjustment();
  EXPECT_GT(adjusted_bitrate_bps, last_adjusted_bitrate_bps);
  last_adjusted_bitrate_bps = adjusted_bitrate_bps;

  SimulateBitrateBps(target_bitrate_bps);
  adjusted_bitrate_bps = adjuster_.GetAdjustedBitrateBps();
  VerifyAdjustment();
  EXPECT_GT(adjusted_bitrate_bps, last_adjusted_bitrate_bps);
  last_adjusted_bitrate_bps = adjusted_bitrate_bps;
  // After two cycles we should've stabilized and hit the upper bound.
  EXPECT_EQ(GetTargetBitrateBpsPct(kMaxAdjustedBitratePct),
            adjusted_bitrate_bps);
}

// Tests that large changes in target bitrate will result in immediate change
// in adjusted bitrate.
TEST_F(BitrateAdjusterTest, LargeTargetDelta) {
  uint32_t target_bitrate_bps = 640000;
  adjuster_.SetTargetBitrateBps(target_bitrate_bps);
  EXPECT_EQ(target_bitrate_bps, adjuster_.GetAdjustedBitrateBps());

  float delta_pct = BitrateAdjuster::kBitrateTolerancePct * 2;

  target_bitrate_bps = (1 + delta_pct) * target_bitrate_bps;
  adjuster_.SetTargetBitrateBps(target_bitrate_bps);
  EXPECT_EQ(target_bitrate_bps, adjuster_.GetAdjustedBitrateBps());

  target_bitrate_bps = (1 - delta_pct) * target_bitrate_bps;
  adjuster_.SetTargetBitrateBps(target_bitrate_bps);
  EXPECT_EQ(target_bitrate_bps, adjuster_.GetAdjustedBitrateBps());
}

// Tests that small changes in target bitrate within tolerance will not affect
// adjusted bitrate immediately.
TEST_F(BitrateAdjusterTest, SmallTargetDelta) {
  const uint32_t initial_target_bitrate_bps = 640000;
  uint32_t target_bitrate_bps = initial_target_bitrate_bps;
  adjuster_.SetTargetBitrateBps(target_bitrate_bps);
  EXPECT_EQ(initial_target_bitrate_bps, adjuster_.GetAdjustedBitrateBps());

  float delta_pct = BitrateAdjuster::kBitrateTolerancePct / 2;

  target_bitrate_bps = (1 + delta_pct) * target_bitrate_bps;
  adjuster_.SetTargetBitrateBps(target_bitrate_bps);
  EXPECT_EQ(initial_target_bitrate_bps, adjuster_.GetAdjustedBitrateBps());

  target_bitrate_bps = (1 - delta_pct) * target_bitrate_bps;
  adjuster_.SetTargetBitrateBps(target_bitrate_bps);
  EXPECT_EQ(initial_target_bitrate_bps, adjuster_.GetAdjustedBitrateBps());
}

TEST_F(BitrateAdjusterTest, SmallTargetDeltaOverflow) {
  const uint32_t initial_target_bitrate_bps = 640000;
  uint32_t target_bitrate_bps = initial_target_bitrate_bps;
  adjuster_.SetTargetBitrateBps(target_bitrate_bps);
  EXPECT_EQ(initial_target_bitrate_bps, adjuster_.GetAdjustedBitrateBps());

  float delta_pct = BitrateAdjuster::kBitrateTolerancePct / 2;

  target_bitrate_bps = (1 + delta_pct) * target_bitrate_bps;
  adjuster_.SetTargetBitrateBps(target_bitrate_bps);
  EXPECT_EQ(initial_target_bitrate_bps, adjuster_.GetAdjustedBitrateBps());

  // 1.05 * 1.05 is 1.1 which is greater than tolerance for the initial target
  // bitrate. Since we didn't advance the clock the adjuster never updated.
  target_bitrate_bps = (1 + delta_pct) * target_bitrate_bps;
  adjuster_.SetTargetBitrateBps(target_bitrate_bps);
  EXPECT_EQ(target_bitrate_bps, adjuster_.GetAdjustedBitrateBps());
}

}  // namespace webrtc
