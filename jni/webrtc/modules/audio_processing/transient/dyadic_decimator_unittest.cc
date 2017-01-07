/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/transient/dyadic_decimator.h"

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

static const size_t kEvenBufferLength = 6;
static const size_t kOddBufferLength = 5;
static const size_t kOutBufferLength = 3;

int16_t const test_buffer_even_len[] = {0, 1, 2, 3, 4, 5};
int16_t const test_buffer_odd_len[]  = {0, 1, 2, 3, 4};
int16_t test_buffer_out[kOutBufferLength];

TEST(DyadicDecimatorTest, GetOutLengthToDyadicDecimate) {
  EXPECT_EQ(3u, GetOutLengthToDyadicDecimate(6, false));
  EXPECT_EQ(3u, GetOutLengthToDyadicDecimate(6, true));
  EXPECT_EQ(3u, GetOutLengthToDyadicDecimate(5, false));
  EXPECT_EQ(2u, GetOutLengthToDyadicDecimate(5, true));
}


TEST(DyadicDecimatorTest, DyadicDecimateErrorValues) {
  size_t out_samples = 0;

  out_samples = DyadicDecimate(static_cast<int16_t*>(NULL),
                               kEvenBufferLength,
                               false,  // Even sequence.
                               test_buffer_out,
                               kOutBufferLength);
  EXPECT_EQ(0u, out_samples);

  out_samples = DyadicDecimate(test_buffer_even_len,
                               kEvenBufferLength,
                               false,  // Even sequence.
                               static_cast<int16_t*>(NULL),
                               kOutBufferLength);
  EXPECT_EQ(0u, out_samples);

  // Less than required |out_length|.
  out_samples = DyadicDecimate(test_buffer_even_len,
                               kEvenBufferLength,
                               false,  // Even sequence.
                               test_buffer_out,
                               2);
  EXPECT_EQ(0u, out_samples);
}

TEST(DyadicDecimatorTest, DyadicDecimateEvenLengthEvenSequence) {
  size_t expected_out_samples =
      GetOutLengthToDyadicDecimate(kEvenBufferLength, false);

  size_t out_samples = DyadicDecimate(test_buffer_even_len,
                                      kEvenBufferLength,
                                      false,  // Even sequence.
                                      test_buffer_out,
                                      kOutBufferLength);

  EXPECT_EQ(expected_out_samples, out_samples);

  EXPECT_EQ(0, test_buffer_out[0]);
  EXPECT_EQ(2, test_buffer_out[1]);
  EXPECT_EQ(4, test_buffer_out[2]);
}

TEST(DyadicDecimatorTest, DyadicDecimateEvenLengthOddSequence) {
  size_t expected_out_samples =
      GetOutLengthToDyadicDecimate(kEvenBufferLength, true);

  size_t out_samples = DyadicDecimate(test_buffer_even_len,
                                      kEvenBufferLength,
                                      true,  // Odd sequence.
                                      test_buffer_out,
                                      kOutBufferLength);

  EXPECT_EQ(expected_out_samples, out_samples);

  EXPECT_EQ(1, test_buffer_out[0]);
  EXPECT_EQ(3, test_buffer_out[1]);
  EXPECT_EQ(5, test_buffer_out[2]);
}

TEST(DyadicDecimatorTest, DyadicDecimateOddLengthEvenSequence) {
  size_t expected_out_samples =
      GetOutLengthToDyadicDecimate(kOddBufferLength, false);

  size_t out_samples = DyadicDecimate(test_buffer_odd_len,
                                      kOddBufferLength,
                                      false,  // Even sequence.
                                      test_buffer_out,
                                      kOutBufferLength);

  EXPECT_EQ(expected_out_samples, out_samples);

  EXPECT_EQ(0, test_buffer_out[0]);
  EXPECT_EQ(2, test_buffer_out[1]);
  EXPECT_EQ(4, test_buffer_out[2]);
}

TEST(DyadicDecimatorTest, DyadicDecimateOddLengthOddSequence) {
  size_t expected_out_samples =
      GetOutLengthToDyadicDecimate(kOddBufferLength, true);

  size_t out_samples = DyadicDecimate(test_buffer_odd_len,
                                      kOddBufferLength,
                                      true,  // Odd sequence.
                                      test_buffer_out,
                                      kOutBufferLength);

  EXPECT_EQ(expected_out_samples, out_samples);

  EXPECT_EQ(1, test_buffer_out[0]);
  EXPECT_EQ(3, test_buffer_out[1]);
}

}  // namespace webrtc
