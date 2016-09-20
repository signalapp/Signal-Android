/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "webrtc/common_audio/sparse_fir_filter.h"

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/arraysize.h"
#include "webrtc/common_audio/fir_filter.h"

namespace webrtc {
namespace {

static const float kCoeffs[] = {0.2f, 0.3f, 0.5f, 0.7f, 0.11f};
static const float kInput[] =
    {1.f, 2.f, 3.f, 4.f, 5.f, 6.f, 7.f, 8.f, 9.f, 10.f};

template <size_t N>
void VerifyOutput(const float (&expected_output)[N], const float (&output)[N]) {
  EXPECT_EQ(0, memcmp(expected_output, output, sizeof(output)));
}

}  // namespace

TEST(SparseFIRFilterTest, FilterAsIdentity) {
  const float kCoeff = 1.f;
  const size_t kNumCoeff = 1;
  const size_t kSparsity = 3;
  const size_t kOffset = 0;
  float output[arraysize(kInput)];
  SparseFIRFilter filter(&kCoeff, kNumCoeff, kSparsity, kOffset);
  filter.Filter(kInput, arraysize(kInput), output);
  VerifyOutput(kInput, output);
}

TEST(SparseFIRFilterTest, SameOutputForScalarCoefficientAndDifferentSparsity) {
  const float kCoeff = 2.f;
  const size_t kNumCoeff = 1;
  const size_t kLowSparsity = 1;
  const size_t kHighSparsity = 7;
  const size_t kOffset = 0;
  float low_sparsity_output[arraysize(kInput)];
  float high_sparsity_output[arraysize(kInput)];
  SparseFIRFilter low_sparsity_filter(&kCoeff,
                                      kNumCoeff,
                                      kLowSparsity,
                                      kOffset);
  SparseFIRFilter high_sparsity_filter(&kCoeff,
                                       kNumCoeff,
                                       kHighSparsity,
                                       kOffset);
  low_sparsity_filter.Filter(kInput, arraysize(kInput), low_sparsity_output);
  high_sparsity_filter.Filter(kInput, arraysize(kInput), high_sparsity_output);
  VerifyOutput(low_sparsity_output, high_sparsity_output);
}

TEST(SparseFIRFilterTest, FilterUsedAsScalarMultiplication) {
  const float kCoeff = 5.f;
  const size_t kNumCoeff = 1;
  const size_t kSparsity = 5;
  const size_t kOffset = 0;
  float output[arraysize(kInput)];
  SparseFIRFilter filter(&kCoeff, kNumCoeff, kSparsity, kOffset);
  filter.Filter(kInput, arraysize(kInput), output);
  EXPECT_FLOAT_EQ(5.f, output[0]);
  EXPECT_FLOAT_EQ(20.f, output[3]);
  EXPECT_FLOAT_EQ(25.f, output[4]);
  EXPECT_FLOAT_EQ(50.f, output[arraysize(kInput) - 1]);
}

TEST(SparseFIRFilterTest, FilterUsedAsInputShifting) {
  const float kCoeff = 1.f;
  const size_t kNumCoeff = 1;
  const size_t kSparsity = 1;
  const size_t kOffset = 4;
  float output[arraysize(kInput)];
  SparseFIRFilter filter(&kCoeff, kNumCoeff, kSparsity, kOffset);
  filter.Filter(kInput, arraysize(kInput), output);
  EXPECT_FLOAT_EQ(0.f, output[0]);
  EXPECT_FLOAT_EQ(0.f, output[3]);
  EXPECT_FLOAT_EQ(1.f, output[4]);
  EXPECT_FLOAT_EQ(2.f, output[5]);
  EXPECT_FLOAT_EQ(6.f, output[arraysize(kInput) - 1]);
}

TEST(SparseFIRFilterTest, FilterUsedAsArbitraryWeighting) {
  const size_t kSparsity = 2;
  const size_t kOffset = 1;
  float output[arraysize(kInput)];
  SparseFIRFilter filter(kCoeffs, arraysize(kCoeffs), kSparsity, kOffset);
  filter.Filter(kInput, arraysize(kInput), output);
  EXPECT_FLOAT_EQ(0.f, output[0]);
  EXPECT_FLOAT_EQ(0.9f, output[3]);
  EXPECT_FLOAT_EQ(1.4f, output[4]);
  EXPECT_FLOAT_EQ(2.4f, output[5]);
  EXPECT_FLOAT_EQ(8.61f, output[arraysize(kInput) - 1]);
}

TEST(SparseFIRFilterTest, FilterInLengthLesserOrEqualToCoefficientsLength) {
  const size_t kSparsity = 1;
  const size_t kOffset = 0;
  float output[arraysize(kInput)];
  SparseFIRFilter filter(kCoeffs, arraysize(kCoeffs), kSparsity, kOffset);
  filter.Filter(kInput, 2, output);
  EXPECT_FLOAT_EQ(0.2f, output[0]);
  EXPECT_FLOAT_EQ(0.7f, output[1]);
}

TEST(SparseFIRFilterTest, MultipleFilterCalls) {
  const size_t kSparsity = 1;
  const size_t kOffset = 0;
  float output[arraysize(kInput)];
  SparseFIRFilter filter(kCoeffs, arraysize(kCoeffs), kSparsity, kOffset);
  filter.Filter(kInput, 2, output);
  EXPECT_FLOAT_EQ(0.2f, output[0]);
  EXPECT_FLOAT_EQ(0.7f, output[1]);
  filter.Filter(kInput, 2, output);
  EXPECT_FLOAT_EQ(1.3f, output[0]);
  EXPECT_FLOAT_EQ(2.4f, output[1]);
  filter.Filter(kInput, 2, output);
  EXPECT_FLOAT_EQ(2.81f, output[0]);
  EXPECT_FLOAT_EQ(2.62f, output[1]);
  filter.Filter(kInput, 2, output);
  EXPECT_FLOAT_EQ(2.81f, output[0]);
  EXPECT_FLOAT_EQ(2.62f, output[1]);
  filter.Filter(&kInput[3], 3, output);
  EXPECT_FLOAT_EQ(3.41f, output[0]);
  EXPECT_FLOAT_EQ(4.12f, output[1]);
  EXPECT_FLOAT_EQ(6.21f, output[2]);
  filter.Filter(&kInput[3], 3, output);
  EXPECT_FLOAT_EQ(8.12f, output[0]);
  EXPECT_FLOAT_EQ(9.14f, output[1]);
  EXPECT_FLOAT_EQ(9.45f, output[2]);
}

TEST(SparseFIRFilterTest, VerifySampleBasedVsBlockBasedFiltering) {
  const size_t kSparsity = 3;
  const size_t kOffset = 1;
  float output_block_based[arraysize(kInput)];
  SparseFIRFilter filter_block(kCoeffs,
                               arraysize(kCoeffs),
                               kSparsity,
                               kOffset);
  filter_block.Filter(kInput, arraysize(kInput), output_block_based);
  float output_sample_based[arraysize(kInput)];
  SparseFIRFilter filter_sample(kCoeffs,
                                arraysize(kCoeffs),
                                kSparsity,
                                kOffset);
  for (size_t i = 0; i < arraysize(kInput); ++i)
    filter_sample.Filter(&kInput[i], 1, &output_sample_based[i]);
  VerifyOutput(output_block_based, output_sample_based);
}

TEST(SparseFIRFilterTest, SimpleHighPassFilter) {
  const size_t kSparsity = 2;
  const size_t kOffset = 2;
  const float kHPCoeffs[] = {1.f, -1.f};
  const float kConstantInput[] =
      {1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f};
  float output[arraysize(kConstantInput)];
  SparseFIRFilter filter(kHPCoeffs, arraysize(kHPCoeffs), kSparsity, kOffset);
  filter.Filter(kConstantInput, arraysize(kConstantInput), output);
  EXPECT_FLOAT_EQ(0.f, output[0]);
  EXPECT_FLOAT_EQ(0.f, output[1]);
  EXPECT_FLOAT_EQ(1.f, output[2]);
  EXPECT_FLOAT_EQ(1.f, output[3]);
  for (size_t i = kSparsity + kOffset; i < arraysize(kConstantInput); ++i)
    EXPECT_FLOAT_EQ(0.f, output[i]);
}

TEST(SparseFIRFilterTest, SimpleLowPassFilter) {
  const size_t kSparsity = 2;
  const size_t kOffset = 2;
  const float kLPCoeffs[] = {1.f, 1.f};
  const float kHighFrequencyInput[] =
      {1.f, 1.f, -1.f, -1.f, 1.f, 1.f, -1.f, -1.f, 1.f, 1.f};
  float output[arraysize(kHighFrequencyInput)];
  SparseFIRFilter filter(kLPCoeffs, arraysize(kLPCoeffs), kSparsity, kOffset);
  filter.Filter(kHighFrequencyInput, arraysize(kHighFrequencyInput), output);
  EXPECT_FLOAT_EQ(0.f, output[0]);
  EXPECT_FLOAT_EQ(0.f, output[1]);
  EXPECT_FLOAT_EQ(1.f, output[2]);
  EXPECT_FLOAT_EQ(1.f, output[3]);
  for (size_t i = kSparsity + kOffset; i < arraysize(kHighFrequencyInput); ++i)
    EXPECT_FLOAT_EQ(0.f, output[i]);
}

TEST(SparseFIRFilterTest, SameOutputWhenSwappedCoefficientsAndInput) {
  const size_t kSparsity = 1;
  const size_t kOffset = 0;
  float output[arraysize(kCoeffs)];
  float output_swapped[arraysize(kCoeffs)];
  SparseFIRFilter filter(kCoeffs, arraysize(kCoeffs), kSparsity, kOffset);
  // Use arraysize(kCoeffs) for in_length to get same-length outputs.
  filter.Filter(kInput, arraysize(kCoeffs), output);
  SparseFIRFilter filter_swapped(kInput,
                                 arraysize(kCoeffs),
                                 kSparsity,
                                 kOffset);
  filter_swapped.Filter(kCoeffs, arraysize(kCoeffs), output_swapped);
  VerifyOutput(output, output_swapped);
}

TEST(SparseFIRFilterTest, SameOutputAsFIRFilterWhenSparsityOneAndOffsetZero) {
  const size_t kSparsity = 1;
  const size_t kOffset = 0;
  float output[arraysize(kInput)];
  float sparse_output[arraysize(kInput)];
  std::unique_ptr<FIRFilter> filter(
      FIRFilter::Create(kCoeffs, arraysize(kCoeffs), arraysize(kInput)));
  SparseFIRFilter sparse_filter(kCoeffs,
                                arraysize(kCoeffs),
                                kSparsity,
                                kOffset);
  filter->Filter(kInput, arraysize(kInput), output);
  sparse_filter.Filter(kInput, arraysize(kInput), sparse_output);
  for (size_t i = 0; i < arraysize(kInput); ++i) {
    EXPECT_FLOAT_EQ(output[i], sparse_output[i]);
  }
}

}  // namespace webrtc
