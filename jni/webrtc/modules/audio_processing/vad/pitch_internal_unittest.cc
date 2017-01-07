/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/vad/pitch_internal.h"

#include <math.h>

#include "testing/gtest/include/gtest/gtest.h"

TEST(PitchInternalTest, test) {
  const int kSamplingRateHz = 8000;
  const int kNumInputParameters = 4;
  const int kNumOutputParameters = 3;
  // Inputs
  double log_old_gain = log(0.5);
  double gains[] = {0.6, 0.2, 0.5, 0.4};

  double old_lag = 70;
  double lags[] = {90, 111, 122, 50};

  // Expected outputs
  double expected_log_pitch_gain[] = {
      -0.541212549898316, -1.45672279045507, -0.80471895621705};
  double expected_log_old_gain = log(gains[kNumInputParameters - 1]);

  double expected_pitch_lag_hz[] = {
      92.3076923076923, 70.9010339734121, 93.0232558139535};
  double expected_old_lag = lags[kNumInputParameters - 1];

  double log_pitch_gain[kNumOutputParameters];
  double pitch_lag_hz[kNumInputParameters];

  GetSubframesPitchParameters(kSamplingRateHz, gains, lags, kNumInputParameters,
                              kNumOutputParameters, &log_old_gain, &old_lag,
                              log_pitch_gain, pitch_lag_hz);

  for (int n = 0; n < 3; n++) {
    EXPECT_NEAR(pitch_lag_hz[n], expected_pitch_lag_hz[n], 1e-6);
    EXPECT_NEAR(log_pitch_gain[n], expected_log_pitch_gain[n], 1e-8);
  }
  EXPECT_NEAR(old_lag, expected_old_lag, 1e-6);
  EXPECT_NEAR(log_old_gain, expected_log_old_gain, 1e-8);
}
