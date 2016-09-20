/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <cmath>
#include <complex>
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/arraysize.h"
#include "webrtc/modules/audio_processing/intelligibility/intelligibility_utils.h"

namespace webrtc {

namespace intelligibility {

std::vector<std::vector<std::complex<float>>> GenerateTestData(size_t freqs,
                                                               size_t samples) {
  std::vector<std::vector<std::complex<float>>> data(samples);
  for (size_t i = 0; i < samples; ++i) {
    for (size_t j = 0; j < freqs; ++j) {
      const float val = 0.99f / ((i + 1) * (j + 1));
      data[i].push_back(std::complex<float>(val, val));
    }
  }
  return data;
}

// Tests PowerEstimator, for all power step types.
TEST(IntelligibilityUtilsTest, TestPowerEstimator) {
  const size_t kFreqs = 10;
  const size_t kSamples = 100;
  const float kDecay = 0.5f;
  const std::vector<std::vector<std::complex<float>>> test_data(
      GenerateTestData(kFreqs, kSamples));
  PowerEstimator<std::complex<float>> power_estimator(kFreqs, kDecay);
  EXPECT_EQ(0, power_estimator.power()[0]);

  // Makes sure Step is doing something.
  power_estimator.Step(test_data[0].data());
  for (size_t i = 1; i < kSamples; ++i) {
    power_estimator.Step(test_data[i].data());
    for (size_t j = 0; j < kFreqs; ++j) {
      EXPECT_GE(power_estimator.power()[j], 0.f);
      EXPECT_LE(power_estimator.power()[j], 1.f);
    }
  }
}

// Tests gain applier.
TEST(IntelligibilityUtilsTest, TestGainApplier) {
  const size_t kFreqs = 10;
  const size_t kSamples = 100;
  const float kChangeLimit = 0.1f;
  GainApplier gain_applier(kFreqs, kChangeLimit);
  const std::vector<std::vector<std::complex<float>>> in_data(
      GenerateTestData(kFreqs, kSamples));
  std::vector<std::vector<std::complex<float>>> out_data(
      GenerateTestData(kFreqs, kSamples));
  for (size_t i = 0; i < kSamples; ++i) {
    gain_applier.Apply(in_data[i].data(), out_data[i].data());
    for (size_t j = 0; j < kFreqs; ++j) {
      EXPECT_GT(out_data[i][j].real(), 0.f);
      EXPECT_LT(out_data[i][j].real(), 1.f);
      EXPECT_GT(out_data[i][j].imag(), 0.f);
      EXPECT_LT(out_data[i][j].imag(), 1.f);
    }
  }
}

}  // namespace intelligibility

}  // namespace webrtc
