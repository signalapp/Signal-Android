/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/audio_classifier.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <memory>
#include <string>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

static const size_t kFrameSize = 960;

TEST(AudioClassifierTest, AllZeroInput) {
  int16_t in_mono[kFrameSize] = {0};

  // Test all-zero vectors and let the classifier converge from its default
  // to the expected value.
  AudioClassifier zero_classifier;
  for (int i = 0; i < 100; ++i) {
    zero_classifier.Analysis(in_mono, kFrameSize, 1);
  }
  EXPECT_TRUE(zero_classifier.is_music());
}

void RunAnalysisTest(const std::string& audio_filename,
                     const std::string& data_filename,
                     size_t channels) {
  AudioClassifier classifier;
  std::unique_ptr<int16_t[]> in(new int16_t[channels * kFrameSize]);
  bool is_music_ref;

  FILE* audio_file = fopen(audio_filename.c_str(), "rb");
  ASSERT_TRUE(audio_file != NULL) << "Failed to open file " << audio_filename
                                  << std::endl;
  FILE* data_file = fopen(data_filename.c_str(), "rb");
  ASSERT_TRUE(audio_file != NULL) << "Failed to open file " << audio_filename
                                  << std::endl;
  while (fread(in.get(), sizeof(int16_t), channels * kFrameSize, audio_file) ==
         channels * kFrameSize) {
    bool is_music =
        classifier.Analysis(in.get(), channels * kFrameSize, channels);
    EXPECT_EQ(is_music, classifier.is_music());
    ASSERT_EQ(1u, fread(&is_music_ref, sizeof(is_music_ref), 1, data_file));
    EXPECT_EQ(is_music_ref, is_music);
  }
  fclose(audio_file);
  fclose(data_file);
}

TEST(AudioClassifierTest, DoAnalysisMono) {
#if defined(WEBRTC_ARCH_ARM) || defined(WEBRTC_ARCH_ARM64)
  RunAnalysisTest(test::ResourcePath("short_mixed_mono_48", "pcm"),
                  test::ResourcePath("short_mixed_mono_48_arm", "dat"),
                  1);
#else
  RunAnalysisTest(test::ResourcePath("short_mixed_mono_48", "pcm"),
                  test::ResourcePath("short_mixed_mono_48", "dat"),
                  1);
#endif // WEBRTC_ARCH_ARM
}

TEST(AudioClassifierTest, DoAnalysisStereo) {
  RunAnalysisTest(test::ResourcePath("short_mixed_stereo_48", "pcm"),
                  test::ResourcePath("short_mixed_stereo_48", "dat"),
                  2);
}

}  // namespace webrtc
