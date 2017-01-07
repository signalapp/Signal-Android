/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "testing/gtest/include/gtest/gtest.h"

#include "webrtc/modules/audio_processing/utility/block_mean_calculator.h"

namespace webrtc {

TEST(MeanCalculatorTest, Correctness) {
  const size_t kBlockLength = 10;
  BlockMeanCalculator mean_calculator(kBlockLength);
  size_t i = 0;
  float reference = 0.0;

  for (; i < kBlockLength - 1; ++i) {
    mean_calculator.AddValue(static_cast<float>(i));
    EXPECT_FALSE(mean_calculator.EndOfBlock());
  }
  mean_calculator.AddValue(static_cast<float>(i++));
  EXPECT_TRUE(mean_calculator.EndOfBlock());

  for (; i < 3 * kBlockLength; ++i) {
    const bool end_of_block = i % kBlockLength == 0;
    if (end_of_block) {
      // Sum of (i - kBlockLength) ... (i - 1)
      reference = i - 0.5 * (1 + kBlockLength);
    }
    EXPECT_EQ(mean_calculator.EndOfBlock(), end_of_block);
    EXPECT_EQ(reference, mean_calculator.GetLatestMean());
    mean_calculator.AddValue(static_cast<float>(i));
  }
}

TEST(MeanCalculatorTest, Reset) {
  const size_t kBlockLength = 10;
  BlockMeanCalculator mean_calculator(kBlockLength);
  for (size_t i = 0; i < kBlockLength - 1; ++i) {
    mean_calculator.AddValue(static_cast<float>(i));
  }
  mean_calculator.Reset();
  size_t i = 0;
  for (; i < kBlockLength - 1; ++i) {
    mean_calculator.AddValue(static_cast<float>(i));
    EXPECT_FALSE(mean_calculator.EndOfBlock());
  }
  mean_calculator.AddValue(static_cast<float>(i));
  EXPECT_TRUE(mean_calculator.EndOfBlock());
  EXPECT_EQ(mean_calculator.GetLatestMean(), 0.5 * (kBlockLength - 1));
}

}  // namespace webrtc
