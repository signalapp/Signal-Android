/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/vad/pole_zero_filter.h"

#include <math.h>
#include <stdio.h>

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_processing/vad/vad_audio_proc_internal.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

static const int kInputSamples = 50;

static const int16_t kInput[kInputSamples] = {
    -2136,  -7116, 10715,  2464,   3164,   8139,   11393, 24013, -32117, -5544,
    -27740, 10181, 14190,  -24055, -15912, 17393,  6359,  -9950, -13894, 32432,
    -23944, 3437,  -8381,  19768,  3087,   -19795, -5920, 13310, 1407,   3876,
    4059,   3524,  -23130, 19121,  -27900, -24840, 4089,  21422, -3625,  3015,
    -11236, 28856, 13424,  6571,   -19761, -6361,  15821, -9469, 29727,  32229};

static const float kReferenceOutput[kInputSamples] = {
    -2082.230472f,  -6878.572941f,  10697.090871f,  2358.373952f,
    2973.936512f,   7738.580650f,   10690.803213f,  22687.091576f,
    -32676.684717f, -5879.621684f,  -27359.297432f, 10368.735888f,
    13994.584604f,  -23676.126249f, -15078.250390f, 17818.253338f,
    6577.743123f,   -9498.369315f,  -13073.651079f, 32460.026588f,
    -23391.849347f, 3953.805667f,   -7667.761363f,  19995.153447f,
    3185.575477f,   -19207.365160f, -5143.103201f,  13756.317237f,
    1779.654794f,   4142.269755f,   4209.475034f,   3572.991789f,
    -22509.089546f, 19307.878964f,  -27060.439759f, -23319.042810f,
    5547.685267f,   22312.718676f,  -2707.309027f,  3852.358490f,
    -10135.510093f, 29241.509970f,  13394.397233f,  6340.721417f,
    -19510.207905f, -5908.442086f,  15882.301634f,  -9211.335255f,
    29253.056735f,  30874.443046f};

class PoleZeroFilterTest : public ::testing::Test {
 protected:
  PoleZeroFilterTest()
      : my_filter_(PoleZeroFilter::Create(kCoeffNumerator,
                                          kFilterOrder,
                                          kCoeffDenominator,
                                          kFilterOrder)) {}

  ~PoleZeroFilterTest() {}

  void FilterSubframes(int num_subframes);

 private:
  void TestClean();
  std::unique_ptr<PoleZeroFilter> my_filter_;
};

void PoleZeroFilterTest::FilterSubframes(int num_subframes) {
  float output[kInputSamples];
  const int num_subframe_samples = kInputSamples / num_subframes;
  EXPECT_EQ(num_subframe_samples * num_subframes, kInputSamples);

  for (int n = 0; n < num_subframes; n++) {
    my_filter_->Filter(&kInput[n * num_subframe_samples], num_subframe_samples,
                       &output[n * num_subframe_samples]);
  }
  for (int n = 0; n < kInputSamples; n++) {
    EXPECT_NEAR(output[n], kReferenceOutput[n], 1);
  }
}

TEST_F(PoleZeroFilterTest, OneSubframe) {
  FilterSubframes(1);
}

TEST_F(PoleZeroFilterTest, TwoSubframes) {
  FilterSubframes(2);
}

TEST_F(PoleZeroFilterTest, FiveSubframes) {
  FilterSubframes(5);
}

TEST_F(PoleZeroFilterTest, TenSubframes) {
  FilterSubframes(10);
}

TEST_F(PoleZeroFilterTest, TwentyFiveSubframes) {
  FilterSubframes(25);
}

TEST_F(PoleZeroFilterTest, FiftySubframes) {
  FilterSubframes(50);
}

}  // namespace webrtc
