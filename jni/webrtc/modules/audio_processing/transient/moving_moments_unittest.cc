/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/transient/moving_moments.h"

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

static const float kTolerance = 0.0001f;

class MovingMomentsTest : public ::testing::Test {
 protected:
  static const size_t kMovingMomentsBufferLength = 5;
  static const size_t kMaxOutputLength = 20;  // Valid for this tests only.

  virtual void SetUp();
  // Calls CalculateMoments and verifies that it produces the expected
  // outputs.
  void CalculateMomentsAndVerify(const float* input, size_t input_length,
                                 const float* expected_mean,
                                 const float* expected_mean_squares);

  std::unique_ptr<MovingMoments> moving_moments_;
  float output_mean_[kMaxOutputLength];
  float output_mean_squares_[kMaxOutputLength];
};

const size_t MovingMomentsTest::kMaxOutputLength;

void MovingMomentsTest::SetUp() {
  moving_moments_.reset(new MovingMoments(kMovingMomentsBufferLength));
}

void MovingMomentsTest::CalculateMomentsAndVerify(
    const float* input, size_t input_length,
    const float* expected_mean,
    const float* expected_mean_squares) {
  ASSERT_LE(input_length, kMaxOutputLength);

  moving_moments_->CalculateMoments(input,
                                    input_length,
                                    output_mean_,
                                    output_mean_squares_);

  for (size_t i = 1; i < input_length; ++i) {
    EXPECT_NEAR(expected_mean[i], output_mean_[i], kTolerance);
    EXPECT_NEAR(expected_mean_squares[i], output_mean_squares_[i], kTolerance);
  }
}

TEST_F(MovingMomentsTest, CorrectMomentsOfAnAllZerosBuffer) {
  const float kInput[] = {0.f, 0.f, 0.f, 0.f, 0.f};
  const size_t kInputLength = sizeof(kInput) / sizeof(kInput[0]);

  const float expected_mean[kInputLength] = {0.f, 0.f, 0.f, 0.f, 0.f};
  const float expected_mean_squares[kInputLength] = {0.f, 0.f, 0.f, 0.f, 0.f};

  CalculateMomentsAndVerify(kInput, kInputLength, expected_mean,
                            expected_mean_squares);
}

TEST_F(MovingMomentsTest, CorrectMomentsOfAConstantBuffer) {
  const float kInput[] = {5.f, 5.f, 5.f, 5.f, 5.f, 5.f, 5.f, 5.f, 5.f, 5.f};
  const size_t kInputLength = sizeof(kInput) / sizeof(kInput[0]);

  const float expected_mean[kInputLength] =
      {1.f, 2.f, 3.f, 4.f, 5.f, 5.f, 5.f, 5.f, 5.f, 5.f};
  const float expected_mean_squares[kInputLength] =
      {5.f, 10.f, 15.f, 20.f, 25.f, 25.f, 25.f, 25.f, 25.f, 25.f};

  CalculateMomentsAndVerify(kInput, kInputLength, expected_mean,
                            expected_mean_squares);
}

TEST_F(MovingMomentsTest, CorrectMomentsOfAnIncreasingBuffer) {
  const float kInput[] = {1.f, 2.f, 3.f, 4.f, 5.f, 6.f, 7.f, 8.f, 9.f};
  const size_t kInputLength = sizeof(kInput) / sizeof(kInput[0]);

  const float expected_mean[kInputLength] =
      {0.2f, 0.6f, 1.2f, 2.f, 3.f, 4.f, 5.f, 6.f, 7.f};
  const float expected_mean_squares[kInputLength] =
      {0.2f, 1.f, 2.8f, 6.f, 11.f, 18.f, 27.f, 38.f, 51.f};

  CalculateMomentsAndVerify(kInput, kInputLength, expected_mean,
                            expected_mean_squares);
}

TEST_F(MovingMomentsTest, CorrectMomentsOfADecreasingBuffer) {
  const float kInput[] =
      {-1.f, -2.f, -3.f, -4.f, -5.f, -6.f, -7.f, -8.f, -9.f};
  const size_t kInputLength = sizeof(kInput) / sizeof(kInput[0]);

  const float expected_mean[kInputLength] =
      {-0.2f, -0.6f, -1.2f, -2.f, -3.f, -4.f, -5.f, -6.f, -7.f};
  const float expected_mean_squares[kInputLength] =
      {0.2f, 1.f, 2.8f, 6.f, 11.f, 18.f, 27.f, 38.f, 51.f};

  CalculateMomentsAndVerify(kInput, kInputLength, expected_mean,
                            expected_mean_squares);
}

TEST_F(MovingMomentsTest, CorrectMomentsOfAZeroMeanSequence) {
  const size_t kMovingMomentsBufferLength = 4;
  moving_moments_.reset(new MovingMoments(kMovingMomentsBufferLength));
  const float kInput[] =
      {1.f, -1.f, 1.f, -1.f, 1.f, -1.f, 1.f, -1.f, 1.f, -1.f};
  const size_t kInputLength = sizeof(kInput) / sizeof(kInput[0]);

  const float expected_mean[kInputLength] =
      {0.25f, 0.f, 0.25f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f};
  const float expected_mean_squares[kInputLength] =
      {0.25f, 0.5f, 0.75f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f, 1.f};

  CalculateMomentsAndVerify(kInput, kInputLength, expected_mean,
                            expected_mean_squares);
}

TEST_F(MovingMomentsTest, CorrectMomentsOfAnArbitraryBuffer) {
  const float kInput[] =
      {0.2f, 0.3f, 0.5f, 0.7f, 0.11f, 0.13f, 0.17f, 0.19f, 0.23f};
  const size_t kInputLength = sizeof(kInput) / sizeof(kInput[0]);

  const float expected_mean[kInputLength] =
      {0.04f, 0.1f, 0.2f, 0.34f, 0.362f, 0.348f, 0.322f, 0.26f, 0.166f};
  const float expected_mean_squares[kInputLength] =
      {0.008f, 0.026f, 0.076f, 0.174f, 0.1764f, 0.1718f, 0.1596f, 0.1168f,
      0.0294f};

  CalculateMomentsAndVerify(kInput, kInputLength, expected_mean,
                            expected_mean_squares);
}

TEST_F(MovingMomentsTest, MutipleCalculateMomentsCalls) {
  const float kInputFirstCall[] =
      {0.2f, 0.3f, 0.5f, 0.7f, 0.11f, 0.13f, 0.17f, 0.19f, 0.23f};
  const size_t kInputFirstCallLength = sizeof(kInputFirstCall) /
                                    sizeof(kInputFirstCall[0]);
  const float kInputSecondCall[] = {0.29f, 0.31f};
  const size_t kInputSecondCallLength = sizeof(kInputSecondCall) /
                                     sizeof(kInputSecondCall[0]);
  const float kInputThirdCall[] = {0.37f, 0.41f, 0.43f, 0.47f};
  const size_t kInputThirdCallLength = sizeof(kInputThirdCall) /
                                    sizeof(kInputThirdCall[0]);

  const float expected_mean_first_call[kInputFirstCallLength] =
      {0.04f, 0.1f, 0.2f, 0.34f, 0.362f, 0.348f, 0.322f, 0.26f, 0.166f};
  const float expected_mean_squares_first_call[kInputFirstCallLength] =
      {0.008f, 0.026f, 0.076f, 0.174f, 0.1764f, 0.1718f, 0.1596f, 0.1168f,
      0.0294f};

  const float expected_mean_second_call[kInputSecondCallLength] =
      {0.202f, 0.238f};
  const float expected_mean_squares_second_call[kInputSecondCallLength] =
      {0.0438f, 0.0596f};

  const float expected_mean_third_call[kInputThirdCallLength] =
      {0.278f, 0.322f, 0.362f, 0.398f};
  const float expected_mean_squares_third_call[kInputThirdCallLength] =
      {0.0812f, 0.1076f, 0.134f, 0.1614f};

  CalculateMomentsAndVerify(kInputFirstCall, kInputFirstCallLength,
      expected_mean_first_call, expected_mean_squares_first_call);

  CalculateMomentsAndVerify(kInputSecondCall, kInputSecondCallLength,
      expected_mean_second_call, expected_mean_squares_second_call);

  CalculateMomentsAndVerify(kInputThirdCall, kInputThirdCallLength,
      expected_mean_third_call, expected_mean_squares_third_call);
}

TEST_F(MovingMomentsTest,
       VerifySampleBasedVsBlockBasedCalculation) {
  const float kInput[] =
      {0.2f, 0.3f, 0.5f, 0.7f, 0.11f, 0.13f, 0.17f, 0.19f, 0.23f};
  const size_t kInputLength = sizeof(kInput) / sizeof(kInput[0]);

  float output_mean_block_based[kInputLength];
  float output_mean_squares_block_based[kInputLength];

  float output_mean_sample_based;
  float output_mean_squares_sample_based;

  moving_moments_->CalculateMoments(
      kInput, kInputLength, output_mean_block_based,
      output_mean_squares_block_based);
  moving_moments_.reset(new MovingMoments(kMovingMomentsBufferLength));
  for (size_t i = 0; i < kInputLength; ++i) {
    moving_moments_->CalculateMoments(
        &kInput[i], 1, &output_mean_sample_based,
        &output_mean_squares_sample_based);
    EXPECT_FLOAT_EQ(output_mean_block_based[i], output_mean_sample_based);
    EXPECT_FLOAT_EQ(output_mean_squares_block_based[i],
                     output_mean_squares_sample_based);
  }
}

}  // namespace webrtc
