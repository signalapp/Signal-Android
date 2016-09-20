/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// We don't test the value of pitch gain and lags as they are created by iSAC
// routines. However, interpolation of pitch-gain and lags is in a separate
// class and has its own unit-test.

#include "webrtc/modules/audio_processing/vad/vad_audio_proc.h"

#include <math.h>
#include <stdio.h>

#include <string>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_processing/vad/common.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

TEST(AudioProcessingTest, DISABLED_ComputingFirstSpectralPeak) {
  VadAudioProc audioproc;

  std::string peak_file_name =
      test::ResourcePath("audio_processing/agc/agc_spectral_peak", "dat");
  FILE* peak_file = fopen(peak_file_name.c_str(), "rb");
  ASSERT_TRUE(peak_file != NULL);

  std::string pcm_file_name =
      test::ResourcePath("audio_processing/agc/agc_audio", "pcm");
  FILE* pcm_file = fopen(pcm_file_name.c_str(), "rb");
  ASSERT_TRUE(pcm_file != NULL);

  // Read 10 ms audio in each iteration.
  const size_t kDataLength = kLength10Ms;
  int16_t data[kDataLength] = {0};
  AudioFeatures features;
  double sp[kMaxNumFrames];
  while (fread(data, sizeof(int16_t), kDataLength, pcm_file) == kDataLength) {
    audioproc.ExtractFeatures(data, kDataLength, &features);
    if (features.num_frames > 0) {
      ASSERT_LT(features.num_frames, kMaxNumFrames);
      // Read reference values.
      const size_t num_frames = features.num_frames;
      ASSERT_EQ(num_frames, fread(sp, sizeof(sp[0]), num_frames, peak_file));
      for (size_t n = 0; n < features.num_frames; n++)
        EXPECT_NEAR(features.spectral_peak[n], sp[n], 3);
    }
  }

  fclose(peak_file);
  fclose(pcm_file);
}

}  // namespace webrtc
