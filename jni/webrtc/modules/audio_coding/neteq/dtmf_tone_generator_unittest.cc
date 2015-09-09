/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for DtmfToneGenerator class.

#include "webrtc/modules/audio_coding/neteq/dtmf_tone_generator.h"

#include <math.h>

#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/audio_multi_vector.h"

namespace webrtc {

TEST(DtmfToneGenerator, CreateAndDestroy) {
  DtmfToneGenerator* tone_gen = new DtmfToneGenerator();
  delete tone_gen;
}

TEST(DtmfToneGenerator, TestErrors) {
  DtmfToneGenerator tone_gen;
  const int kNumSamples = 10;
  AudioMultiVector signal(1);  // One channel.

  // Try to generate tones without initializing.
  EXPECT_EQ(DtmfToneGenerator::kNotInitialized,
            tone_gen.Generate(kNumSamples, &signal));

  const int fs = 16000;       // Valid sample rate.
  const int event = 7;        // Valid event.
  const int attenuation = 0;  // Valid attenuation.
  // Initialize with invalid event -1.
  EXPECT_EQ(DtmfToneGenerator::kParameterError,
            tone_gen.Init(fs, -1, attenuation));
  // Initialize with invalid event 16.
  EXPECT_EQ(DtmfToneGenerator::kParameterError,
            tone_gen.Init(fs, 16, attenuation));
  // Initialize with invalid attenuation -1.
  EXPECT_EQ(DtmfToneGenerator::kParameterError, tone_gen.Init(fs, event, -1));
  // Initialize with invalid attenuation 37.
  EXPECT_EQ(DtmfToneGenerator::kParameterError, tone_gen.Init(fs, event, 37));
  EXPECT_FALSE(tone_gen.initialized());  // Should still be uninitialized.

  // Initialize with valid parameters.
  ASSERT_EQ(0, tone_gen.Init(fs, event, attenuation));
  EXPECT_TRUE(tone_gen.initialized());
  // Negative number of samples.
  EXPECT_EQ(DtmfToneGenerator::kParameterError, tone_gen.Generate(-1, &signal));
  // NULL pointer to destination.
  EXPECT_EQ(DtmfToneGenerator::kParameterError,
            tone_gen.Generate(kNumSamples, NULL));
}

TEST(DtmfToneGenerator, TestTones) {
  DtmfToneGenerator tone_gen;
  const int kAttenuation = 0;
  const int kNumSamples = 10;
  AudioMultiVector signal(1);  // One channel.

  // Low and high frequencies for events 0 through 15.
  const double low_freq_hz[] = { 941.0, 697.0, 697.0, 697.0, 770.0, 770.0,
      770.0, 852.0, 852.0, 852.0, 941.0, 941.0, 697.0, 770.0, 852.0, 941.0 };
  const double hi_freq_hz[] = { 1336.0, 1209.0, 1336.0, 1477.0, 1209.0, 1336.0,
      1477.0, 1209.0, 1336.0, 1477.0, 1209.0, 1477.0, 1633.0, 1633.0, 1633.0,
      1633.0 };
  const double attenuate_3dB = 23171.0 / 32768;  // 3 dB attenuation.
  const double base_attenuation = 16141.0 / 16384.0;  // This is the attenuation
                                                      // applied to all cases.
  const int fs_vec[] = { 8000, 16000, 32000, 48000 };
  for (int f = 0; f < 4; ++f) {
    int fs = fs_vec[f];
    for (int event = 0; event <= 15; ++event) {
      std::ostringstream ss;
      ss << "Checking event " << event << " at sample rate " << fs;
      SCOPED_TRACE(ss.str());
      ASSERT_EQ(0, tone_gen.Init(fs, event, kAttenuation));
      EXPECT_TRUE(tone_gen.initialized());
      EXPECT_EQ(kNumSamples, tone_gen.Generate(kNumSamples, &signal));

      double f1 = low_freq_hz[event];
      double f2 = hi_freq_hz[event];
      const double pi = 3.14159265358979323846;

      for (int n = 0; n < kNumSamples; ++n) {
        double x = attenuate_3dB * sin(2.0 * pi * f1 / fs * (-n - 1))
            + sin(2.0 * pi * f2 / fs * (-n - 1));
        x *= base_attenuation;
        x = ldexp(x, 14);  // Scale to Q14.
        static const int kChannel = 0;
        EXPECT_NEAR(x, static_cast<double>(signal[kChannel][n]), 25);
      }

      tone_gen.Reset();
      EXPECT_FALSE(tone_gen.initialized());
    }
  }
}

TEST(DtmfToneGenerator, TestAmplitudes) {
  DtmfToneGenerator tone_gen;
  const int kNumSamples = 10;
  AudioMultiVector signal(1);  // One channel.
  AudioMultiVector ref_signal(1);  // One channel.

  const int fs_vec[] = { 8000, 16000, 32000, 48000 };
  const int event_vec[] = { 0, 4, 9, 13 };  // Test a few events.
  for (int f = 0; f < 4; ++f) {
    int fs = fs_vec[f];
    int event = event_vec[f];
    // Create full-scale reference.
    ASSERT_EQ(0, tone_gen.Init(fs, event, 0));  // 0 attenuation.
    EXPECT_EQ(kNumSamples, tone_gen.Generate(kNumSamples, &ref_signal));
    // Test every 5 steps (to save time).
    for (int attenuation = 1; attenuation <= 36; attenuation += 5) {
      std::ostringstream ss;
      ss << "Checking event " << event << " at sample rate " << fs;
      ss << "; attenuation " << attenuation;
      SCOPED_TRACE(ss.str());
      ASSERT_EQ(0, tone_gen.Init(fs, event, attenuation));
      EXPECT_EQ(kNumSamples, tone_gen.Generate(kNumSamples, &signal));
      for (int n = 0; n < kNumSamples; ++n) {
        double attenuation_factor =
            pow(10, -static_cast<double>(attenuation)/20);
        // Verify that the attenuation is correct.
        static const int kChannel = 0;
        EXPECT_NEAR(attenuation_factor * ref_signal[kChannel][n],
                    signal[kChannel][n], 2);
      }

      tone_gen.Reset();
    }
  }
}

}  // namespace webrtc
