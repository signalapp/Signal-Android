/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/common_audio/fir_filter.h"

#include <string.h>

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {
namespace {

static const float kCoefficients[] = {0.2f, 0.3f, 0.5f, 0.7f, 0.11f};
static const size_t kCoefficientsLength = sizeof(kCoefficients) /
                                       sizeof(kCoefficients[0]);

static const float kInput[] = {1.f, 2.f, 3.f, 4.f, 5.f, 6.f, 7.f,
                                      8.f, 9.f, 10.f};
static const size_t kInputLength = sizeof(kInput) /
                                      sizeof(kInput[0]);

void VerifyOutput(const float* expected_output,
                  const float* output,
                  size_t length) {
  EXPECT_EQ(0, memcmp(expected_output,
                      output,
                      length * sizeof(expected_output[0])));
}

}  // namespace

TEST(FIRFilterTest, FilterAsIdentity) {
  const float kCoefficients[] = {1.f, 0.f, 0.f, 0.f, 0.f};
  float output[kInputLength];
  std::unique_ptr<FIRFilter> filter(
      FIRFilter::Create(kCoefficients, kCoefficientsLength, kInputLength));
  filter->Filter(kInput, kInputLength, output);

  VerifyOutput(kInput, output, kInputLength);
}

TEST(FIRFilterTest, FilterUsedAsScalarMultiplication) {
  const float kCoefficients[] = {5.f, 0.f, 0.f, 0.f, 0.f};
  float output[kInputLength];
  std::unique_ptr<FIRFilter> filter(
      FIRFilter::Create(kCoefficients, kCoefficientsLength, kInputLength));
  filter->Filter(kInput, kInputLength, output);

  EXPECT_FLOAT_EQ(5.f, output[0]);
  EXPECT_FLOAT_EQ(20.f, output[3]);
  EXPECT_FLOAT_EQ(25.f, output[4]);
  EXPECT_FLOAT_EQ(50.f, output[kInputLength - 1]);
}

TEST(FIRFilterTest, FilterUsedAsInputShifting) {
  const float kCoefficients[] = {0.f, 0.f, 0.f, 0.f, 1.f};
  float output[kInputLength];
  std::unique_ptr<FIRFilter> filter(
      FIRFilter::Create(kCoefficients, kCoefficientsLength, kInputLength));
  filter->Filter(kInput, kInputLength, output);

  EXPECT_FLOAT_EQ(0.f, output[0]);
  EXPECT_FLOAT_EQ(0.f, output[3]);
  EXPECT_FLOAT_EQ(1.f, output[4]);
  EXPECT_FLOAT_EQ(2.f, output[5]);
  EXPECT_FLOAT_EQ(6.f, output[kInputLength - 1]);
}

TEST(FIRFilterTest, FilterUsedAsArbitraryWeighting) {
  float output[kInputLength];
  std::unique_ptr<FIRFilter> filter(
      FIRFilter::Create(kCoefficients, kCoefficientsLength, kInputLength));
  filter->Filter(kInput, kInputLength, output);

  EXPECT_FLOAT_EQ(0.2f, output[0]);
  EXPECT_FLOAT_EQ(3.4f, output[3]);
  EXPECT_FLOAT_EQ(5.21f, output[4]);
  EXPECT_FLOAT_EQ(7.02f, output[5]);
  EXPECT_FLOAT_EQ(14.26f, output[kInputLength - 1]);
}

TEST(FIRFilterTest, FilterInLengthLesserOrEqualToCoefficientsLength) {
  float output[kInputLength];
  std::unique_ptr<FIRFilter> filter(
      FIRFilter::Create(kCoefficients, kCoefficientsLength, 2));
  filter->Filter(kInput, 2, output);

  EXPECT_FLOAT_EQ(0.2f, output[0]);
  EXPECT_FLOAT_EQ(0.7f, output[1]);
  filter.reset(FIRFilter::Create(
      kCoefficients, kCoefficientsLength, kCoefficientsLength));
  filter->Filter(kInput, kCoefficientsLength, output);

  EXPECT_FLOAT_EQ(0.2f, output[0]);
  EXPECT_FLOAT_EQ(3.4f, output[3]);
  EXPECT_FLOAT_EQ(5.21f, output[4]);
}

TEST(FIRFilterTest, MultipleFilterCalls) {
  float output[kInputLength];
  std::unique_ptr<FIRFilter> filter(
      FIRFilter::Create(kCoefficients, kCoefficientsLength, 3));
  filter->Filter(kInput, 2, output);
  EXPECT_FLOAT_EQ(0.2f, output[0]);
  EXPECT_FLOAT_EQ(0.7f, output[1]);

  filter->Filter(kInput, 2, output);
  EXPECT_FLOAT_EQ(1.3f, output[0]);
  EXPECT_FLOAT_EQ(2.4f, output[1]);

  filter->Filter(kInput, 2, output);
  EXPECT_FLOAT_EQ(2.81f, output[0]);
  EXPECT_FLOAT_EQ(2.62f, output[1]);

  filter->Filter(kInput, 2, output);
  EXPECT_FLOAT_EQ(2.81f, output[0]);
  EXPECT_FLOAT_EQ(2.62f, output[1]);

  filter->Filter(&kInput[3], 3, output);
  EXPECT_FLOAT_EQ(3.41f, output[0]);
  EXPECT_FLOAT_EQ(4.12f, output[1]);
  EXPECT_FLOAT_EQ(6.21f, output[2]);

  filter->Filter(&kInput[3], 3, output);
  EXPECT_FLOAT_EQ(8.12f, output[0]);
  EXPECT_FLOAT_EQ(9.14f, output[1]);
  EXPECT_FLOAT_EQ(9.45f, output[2]);
}

TEST(FIRFilterTest, VerifySampleBasedVsBlockBasedFiltering) {
  float output_block_based[kInputLength];
  std::unique_ptr<FIRFilter> filter(
      FIRFilter::Create(kCoefficients, kCoefficientsLength, kInputLength));
  filter->Filter(kInput, kInputLength, output_block_based);

  float output_sample_based[kInputLength];
  filter.reset(FIRFilter::Create(kCoefficients, kCoefficientsLength, 1));
  for (size_t i = 0; i < kInputLength; ++i) {
    filter->Filter(&kInput[i], 1, &output_sample_based[i]);
  }

  EXPECT_EQ(0, memcmp(output_sample_based,
                      output_block_based,
                      kInputLength));
}

TEST(FIRFilterTest, SimplestHighPassFilter) {
  const float kCoefficients[] = {1.f, -1.f};
  const size_t kCoefficientsLength = sizeof(kCoefficients) /
                                  sizeof(kCoefficients[0]);

  float kConstantInput[] = {1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f};
  const size_t kConstantInputLength = sizeof(kConstantInput) /
      sizeof(kConstantInput[0]);

  float output[kConstantInputLength];
  std::unique_ptr<FIRFilter> filter(FIRFilter::Create(
      kCoefficients, kCoefficientsLength, kConstantInputLength));
  filter->Filter(kConstantInput, kConstantInputLength, output);
  EXPECT_FLOAT_EQ(1.f, output[0]);
  for (size_t i = kCoefficientsLength - 1; i < kConstantInputLength; ++i) {
    EXPECT_FLOAT_EQ(0.f, output[i]);
  }
}

TEST(FIRFilterTest, SimplestLowPassFilter) {
  const float kCoefficients[] = {1.f, 1.f};
  const size_t kCoefficientsLength = sizeof(kCoefficients) /
                                  sizeof(kCoefficients[0]);

  float kHighFrequencyInput[] = {-1.f, 1.f, -1.f, 1.f, -1.f, 1.f, -1.f, 1.f};
  const size_t kHighFrequencyInputLength = sizeof(kHighFrequencyInput) /
                                        sizeof(kHighFrequencyInput[0]);

  float output[kHighFrequencyInputLength];
  std::unique_ptr<FIRFilter> filter(FIRFilter::Create(
      kCoefficients, kCoefficientsLength, kHighFrequencyInputLength));
  filter->Filter(kHighFrequencyInput, kHighFrequencyInputLength, output);
  EXPECT_FLOAT_EQ(-1.f, output[0]);
  for (size_t i = kCoefficientsLength - 1; i < kHighFrequencyInputLength; ++i) {
    EXPECT_FLOAT_EQ(0.f, output[i]);
  }
}

TEST(FIRFilterTest, SameOutputWhenSwapedCoefficientsAndInput) {
  float output[kCoefficientsLength];
  float output_swaped[kCoefficientsLength];
  std::unique_ptr<FIRFilter> filter(FIRFilter::Create(
      kCoefficients, kCoefficientsLength, kCoefficientsLength));
  // Use kCoefficientsLength for in_length to get same-length outputs.
  filter->Filter(kInput, kCoefficientsLength, output);

  filter.reset(FIRFilter::Create(
      kInput, kCoefficientsLength, kCoefficientsLength));
  filter->Filter(kCoefficients, kCoefficientsLength, output_swaped);

  for (size_t i = 0 ; i < kCoefficientsLength; ++i) {
    EXPECT_FLOAT_EQ(output[i], output_swaped[i]);
  }
}

}  // namespace webrtc
