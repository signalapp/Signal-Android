/*
 *  Copyright 2011 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/gunit.h"
#include "webrtc/base/rollingaccumulator.h"

namespace rtc {

namespace {

const double kLearningRate = 0.5;

}  // namespace

TEST(RollingAccumulatorTest, ZeroSamples) {
  RollingAccumulator<int> accum(10);

  EXPECT_EQ(0U, accum.count());
  EXPECT_DOUBLE_EQ(0.0, accum.ComputeMean());
  EXPECT_DOUBLE_EQ(0.0, accum.ComputeVariance());
  EXPECT_EQ(0, accum.ComputeMin());
  EXPECT_EQ(0, accum.ComputeMax());
}

TEST(RollingAccumulatorTest, SomeSamples) {
  RollingAccumulator<int> accum(10);
  for (int i = 0; i < 4; ++i) {
    accum.AddSample(i);
  }

  EXPECT_EQ(4U, accum.count());
  EXPECT_EQ(6, accum.ComputeSum());
  EXPECT_DOUBLE_EQ(1.5, accum.ComputeMean());
  EXPECT_NEAR(2.26666, accum.ComputeWeightedMean(kLearningRate), 0.01);
  EXPECT_DOUBLE_EQ(1.25, accum.ComputeVariance());
  EXPECT_EQ(0, accum.ComputeMin());
  EXPECT_EQ(3, accum.ComputeMax());
}

TEST(RollingAccumulatorTest, RollingSamples) {
  RollingAccumulator<int> accum(10);
  for (int i = 0; i < 12; ++i) {
    accum.AddSample(i);
  }

  EXPECT_EQ(10U, accum.count());
  EXPECT_EQ(65, accum.ComputeSum());
  EXPECT_DOUBLE_EQ(6.5, accum.ComputeMean());
  EXPECT_NEAR(10.0, accum.ComputeWeightedMean(kLearningRate), 0.01);
  EXPECT_NEAR(9.0, accum.ComputeVariance(), 1.0);
  EXPECT_EQ(2, accum.ComputeMin());
  EXPECT_EQ(11, accum.ComputeMax());
}

TEST(RollingAccumulatorTest, ResetSamples) {
  RollingAccumulator<int> accum(10);

  for (int i = 0; i < 10; ++i) {
    accum.AddSample(100);
  }
  EXPECT_EQ(10U, accum.count());
  EXPECT_DOUBLE_EQ(100.0, accum.ComputeMean());
  EXPECT_EQ(100, accum.ComputeMin());
  EXPECT_EQ(100, accum.ComputeMax());

  accum.Reset();
  EXPECT_EQ(0U, accum.count());

  for (int i = 0; i < 5; ++i) {
    accum.AddSample(i);
  }

  EXPECT_EQ(5U, accum.count());
  EXPECT_EQ(10, accum.ComputeSum());
  EXPECT_DOUBLE_EQ(2.0, accum.ComputeMean());
  EXPECT_EQ(0, accum.ComputeMin());
  EXPECT_EQ(4, accum.ComputeMax());
}

TEST(RollingAccumulatorTest, RollingSamplesDouble) {
  RollingAccumulator<double> accum(10);
  for (int i = 0; i < 23; ++i) {
    accum.AddSample(5 * i);
  }

  EXPECT_EQ(10u, accum.count());
  EXPECT_DOUBLE_EQ(875.0, accum.ComputeSum());
  EXPECT_DOUBLE_EQ(87.5, accum.ComputeMean());
  EXPECT_NEAR(105.049, accum.ComputeWeightedMean(kLearningRate), 0.1);
  EXPECT_NEAR(229.166667, accum.ComputeVariance(), 25);
  EXPECT_DOUBLE_EQ(65.0, accum.ComputeMin());
  EXPECT_DOUBLE_EQ(110.0, accum.ComputeMax());
}

TEST(RollingAccumulatorTest, ComputeWeightedMeanCornerCases) {
  RollingAccumulator<int> accum(10);
  EXPECT_DOUBLE_EQ(0.0, accum.ComputeWeightedMean(kLearningRate));
  EXPECT_DOUBLE_EQ(0.0, accum.ComputeWeightedMean(0.0));
  EXPECT_DOUBLE_EQ(0.0, accum.ComputeWeightedMean(1.1));

  for (int i = 0; i < 8; ++i) {
    accum.AddSample(i);
  }

  EXPECT_DOUBLE_EQ(3.5, accum.ComputeMean());
  EXPECT_DOUBLE_EQ(3.5, accum.ComputeWeightedMean(0));
  EXPECT_DOUBLE_EQ(3.5, accum.ComputeWeightedMean(1.1));
  EXPECT_NEAR(6.0, accum.ComputeWeightedMean(kLearningRate), 0.1);
}

}  // namespace rtc
