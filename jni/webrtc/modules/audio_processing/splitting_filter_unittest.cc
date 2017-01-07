/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// MSVC++ requires this to be set before any other includes to get M_PI.
#define _USE_MATH_DEFINES

#include <cmath>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_audio/channel_buffer.h"
#include "webrtc/modules/audio_processing/splitting_filter.h"

namespace webrtc {
namespace {

const size_t kSamplesPer16kHzChannel = 160;
const size_t kSamplesPer48kHzChannel = 480;

}  // namespace

// Generates a signal from presence or absence of sine waves of different
// frequencies.
// Splits into 3 bands and checks their presence or absence.
// Recombines the bands.
// Calculates the delay.
// Checks that the cross correlation of input and output is high enough at the
// calculated delay.
TEST(SplittingFilterTest, SplitsIntoThreeBandsAndReconstructs) {
  static const int kChannels = 1;
  static const int kSampleRateHz = 48000;
  static const size_t kNumBands = 3;
  static const int kFrequenciesHz[kNumBands] = {1000, 12000, 18000};
  static const float kAmplitude = 8192.f;
  static const size_t kChunks = 8;
  SplittingFilter splitting_filter(kChannels,
                                   kNumBands,
                                   kSamplesPer48kHzChannel);
  IFChannelBuffer in_data(kSamplesPer48kHzChannel, kChannels, kNumBands);
  IFChannelBuffer bands(kSamplesPer48kHzChannel, kChannels, kNumBands);
  IFChannelBuffer out_data(kSamplesPer48kHzChannel, kChannels, kNumBands);
  for (size_t i = 0; i < kChunks; ++i) {
    // Input signal generation.
    bool is_present[kNumBands];
    memset(in_data.fbuf()->channels()[0],
           0,
           kSamplesPer48kHzChannel * sizeof(in_data.fbuf()->channels()[0][0]));
    for (size_t j = 0; j < kNumBands; ++j) {
      is_present[j] = i & (static_cast<size_t>(1) << j);
      float amplitude = is_present[j] ? kAmplitude : 0.f;
      for (size_t k = 0; k < kSamplesPer48kHzChannel; ++k) {
        in_data.fbuf()->channels()[0][k] +=
            amplitude * sin(2.f * M_PI * kFrequenciesHz[j] *
                (i * kSamplesPer48kHzChannel + k) / kSampleRateHz);
      }
    }
    // Three band splitting filter.
    splitting_filter.Analysis(&in_data, &bands);
    // Energy calculation.
    float energy[kNumBands];
    for (size_t j = 0; j < kNumBands; ++j) {
      energy[j] = 0.f;
      for (size_t k = 0; k < kSamplesPer16kHzChannel; ++k) {
        energy[j] += bands.fbuf_const()->channels(j)[0][k] *
                     bands.fbuf_const()->channels(j)[0][k];
      }
      energy[j] /= kSamplesPer16kHzChannel;
      if (is_present[j]) {
        EXPECT_GT(energy[j], kAmplitude * kAmplitude / 4);
      } else {
        EXPECT_LT(energy[j], kAmplitude * kAmplitude / 4);
      }
    }
    // Three band merge.
    splitting_filter.Synthesis(&bands, &out_data);
    // Delay and cross correlation estimation.
    float xcorr = 0.f;
    for (size_t delay = 0; delay < kSamplesPer48kHzChannel; ++delay) {
      float tmpcorr = 0.f;
      for (size_t j = delay; j < kSamplesPer48kHzChannel; ++j) {
        tmpcorr += in_data.fbuf_const()->channels()[0][j - delay] *
                   out_data.fbuf_const()->channels()[0][j];
      }
      tmpcorr /= kSamplesPer48kHzChannel;
      if (tmpcorr > xcorr) {
        xcorr = tmpcorr;
      }
    }
    // High cross correlation check.
    bool any_present = false;
    for (size_t j = 0; j < kNumBands; ++j) {
      any_present |= is_present[j];
    }
    if (any_present) {
      EXPECT_GT(xcorr, kAmplitude * kAmplitude / 4);
    }
  }
}

}  // namespace webrtc
