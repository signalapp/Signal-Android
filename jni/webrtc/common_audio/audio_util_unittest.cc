/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_audio/include/audio_util.h"
#include "webrtc/typedefs.h"

namespace webrtc {
namespace {

using ::testing::ElementsAreArray;

void ExpectArraysEq(const int16_t* ref, const int16_t* test, size_t length) {
  for (size_t i = 0; i < length; ++i) {
    EXPECT_EQ(ref[i], test[i]);
  }
}

void ExpectArraysEq(const float* ref, const float* test, size_t length) {
  for (size_t i = 0; i < length; ++i) {
    EXPECT_FLOAT_EQ(ref[i], test[i]);
  }
}

TEST(AudioUtilTest, FloatToS16) {
  const size_t kSize = 9;
  const float kInput[kSize] = {0.f,
                               0.4f / 32767.f,
                               0.6f / 32767.f,
                               -0.4f / 32768.f,
                               -0.6f / 32768.f,
                               1.f,
                               -1.f,
                               1.1f,
                               -1.1f};
  const int16_t kReference[kSize] = {0,     0,      1,     0,     -1,
                                     32767, -32768, 32767, -32768};
  int16_t output[kSize];
  FloatToS16(kInput, kSize, output);
  ExpectArraysEq(kReference, output, kSize);
}

TEST(AudioUtilTest, S16ToFloat) {
  const size_t kSize = 7;
  const int16_t kInput[kSize] = {0, 1, -1, 16384, -16384, 32767, -32768};
  const float kReference[kSize] = {
      0.f, 1.f / 32767.f, -1.f / 32768.f, 16384.f / 32767.f, -0.5f, 1.f, -1.f};
  float output[kSize];
  S16ToFloat(kInput, kSize, output);
  ExpectArraysEq(kReference, output, kSize);
}

TEST(AudioUtilTest, FloatS16ToS16) {
  const size_t kSize = 7;
  const float kInput[kSize] = {0.f,   0.4f,    0.5f,    -0.4f,
                               -0.5f, 32768.f, -32769.f};
  const int16_t kReference[kSize] = {0, 0, 1, 0, -1, 32767, -32768};
  int16_t output[kSize];
  FloatS16ToS16(kInput, kSize, output);
  ExpectArraysEq(kReference, output, kSize);
}

TEST(AudioUtilTest, FloatToFloatS16) {
  const size_t kSize = 9;
  const float kInput[kSize] = {0.f,
                               0.4f / 32767.f,
                               0.6f / 32767.f,
                               -0.4f / 32768.f,
                               -0.6f / 32768.f,
                               1.f,
                               -1.f,
                               1.1f,
                               -1.1f};
  const float kReference[kSize] = {0.f,     0.4f,     0.6f,     -0.4f,    -0.6f,
                                   32767.f, -32768.f, 36043.7f, -36044.8f};
  float output[kSize];
  FloatToFloatS16(kInput, kSize, output);
  ExpectArraysEq(kReference, output, kSize);
}

TEST(AudioUtilTest, FloatS16ToFloat) {
  const size_t kSize = 9;
  const float kInput[kSize] = {0.f,     0.4f,     0.6f,     -0.4f,    -0.6f,
                               32767.f, -32768.f, 36043.7f, -36044.8f};
  const float kReference[kSize] = {0.f,
                                   0.4f / 32767.f,
                                   0.6f / 32767.f,
                                   -0.4f / 32768.f,
                                   -0.6f / 32768.f,
                                   1.f,
                                   -1.f,
                                   1.1f,
                                   -1.1f};
  float output[kSize];
  FloatS16ToFloat(kInput, kSize, output);
  ExpectArraysEq(kReference, output, kSize);
}

TEST(AudioUtilTest, InterleavingStereo) {
  const int16_t kInterleaved[] = {2, 3, 4, 9, 8, 27, 16, 81};
  const size_t kSamplesPerChannel = 4;
  const int kNumChannels = 2;
  const size_t kLength = kSamplesPerChannel * kNumChannels;
  int16_t left[kSamplesPerChannel], right[kSamplesPerChannel];
  int16_t* deinterleaved[] = {left, right};
  Deinterleave(kInterleaved, kSamplesPerChannel, kNumChannels, deinterleaved);
  const int16_t kRefLeft[] = {2, 4, 8, 16};
  const int16_t kRefRight[] = {3, 9, 27, 81};
  ExpectArraysEq(kRefLeft, left, kSamplesPerChannel);
  ExpectArraysEq(kRefRight, right, kSamplesPerChannel);

  int16_t interleaved[kLength];
  Interleave(deinterleaved, kSamplesPerChannel, kNumChannels, interleaved);
  ExpectArraysEq(kInterleaved, interleaved, kLength);
}

TEST(AudioUtilTest, InterleavingMonoIsIdentical) {
  const int16_t kInterleaved[] = {1, 2, 3, 4, 5};
  const size_t kSamplesPerChannel = 5;
  const int kNumChannels = 1;
  int16_t mono[kSamplesPerChannel];
  int16_t* deinterleaved[] = {mono};
  Deinterleave(kInterleaved, kSamplesPerChannel, kNumChannels, deinterleaved);
  ExpectArraysEq(kInterleaved, mono, kSamplesPerChannel);

  int16_t interleaved[kSamplesPerChannel];
  Interleave(deinterleaved, kSamplesPerChannel, kNumChannels, interleaved);
  ExpectArraysEq(mono, interleaved, kSamplesPerChannel);
}

TEST(AudioUtilTest, DownmixInterleavedToMono) {
  {
    const size_t kNumFrames = 4;
    const int kNumChannels = 1;
    const int16_t interleaved[kNumChannels * kNumFrames] = {1, 2, -1, -3};
    int16_t deinterleaved[kNumFrames];

    DownmixInterleavedToMono(interleaved, kNumFrames, kNumChannels,
                             deinterleaved);

    EXPECT_THAT(deinterleaved, ElementsAreArray(interleaved));
  }
  {
    const size_t kNumFrames = 2;
    const int kNumChannels = 2;
    const int16_t interleaved[kNumChannels * kNumFrames] = {10, 20, -10, -30};
    int16_t deinterleaved[kNumFrames];

    DownmixInterleavedToMono(interleaved, kNumFrames, kNumChannels,
                             deinterleaved);
    const int16_t expected[kNumFrames] = {15, -20};

    EXPECT_THAT(deinterleaved, ElementsAreArray(expected));
  }
  {
    const size_t kNumFrames = 3;
    const int kNumChannels = 3;
    const int16_t interleaved[kNumChannels * kNumFrames] = {
        30000, 30000, 24001, -5, -10, -20, -30000, -30999, -30000};
    int16_t deinterleaved[kNumFrames];

    DownmixInterleavedToMono(interleaved, kNumFrames, kNumChannels,
                             deinterleaved);
    const int16_t expected[kNumFrames] = {28000, -11, -30333};

    EXPECT_THAT(deinterleaved, ElementsAreArray(expected));
  }
}

TEST(AudioUtilTest, DownmixToMonoTest) {
  {
    const size_t kNumFrames = 4;
    const int kNumChannels = 1;
    const float input_data[kNumChannels][kNumFrames] = {{1.f, 2.f, -1.f, -3.f}};
    const float* input[kNumChannels];
    for (int i = 0; i < kNumChannels; ++i) {
      input[i] = input_data[i];
    }

    float downmixed[kNumFrames];

    DownmixToMono<float, float>(input, kNumFrames, kNumChannels, downmixed);

    EXPECT_THAT(downmixed, ElementsAreArray(input_data[0]));
  }
  {
    const size_t kNumFrames = 3;
    const int kNumChannels = 2;
    const float input_data[kNumChannels][kNumFrames] = {{1.f, 2.f, -1.f},
                                                        {3.f, 0.f, 1.f}};
    const float* input[kNumChannels];
    for (int i = 0; i < kNumChannels; ++i) {
      input[i] = input_data[i];
    }

    float downmixed[kNumFrames];
    const float expected[kNumFrames] = {2.f, 1.f, 0.f};

    DownmixToMono<float, float>(input, kNumFrames, kNumChannels, downmixed);

    EXPECT_THAT(downmixed, ElementsAreArray(expected));
  }
  {
    const size_t kNumFrames = 3;
    const int kNumChannels = 3;
    const int16_t input_data[kNumChannels][kNumFrames] = {
        {30000, -5, -30000}, {30000, -10, -30999}, {24001, -20, -30000}};
    const int16_t* input[kNumChannels];
    for (int i = 0; i < kNumChannels; ++i) {
      input[i] = input_data[i];
    }

    int16_t downmixed[kNumFrames];
    const int16_t expected[kNumFrames] = {28000, -11, -30333};

    DownmixToMono<int16_t, int32_t>(input, kNumFrames, kNumChannels, downmixed);

    EXPECT_THAT(downmixed, ElementsAreArray(expected));
  }
}

}  // namespace
}  // namespace webrtc
