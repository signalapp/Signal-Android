/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/vad/standalone_vad.h"

#include <string.h>

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

TEST(StandaloneVadTest, Api) {
  std::unique_ptr<StandaloneVad> vad(StandaloneVad::Create());
  int16_t data[kLength10Ms] = {0};

  // Valid frame length (for 32 kHz rate), but not what the VAD is expecting.
  EXPECT_EQ(-1, vad->AddAudio(data, 320));

  const size_t kMaxNumFrames = 3;
  double p[kMaxNumFrames];
  for (size_t n = 0; n < kMaxNumFrames; n++)
    EXPECT_EQ(0, vad->AddAudio(data, kLength10Ms));

  // Pretend |p| is shorter that it should be.
  EXPECT_EQ(-1, vad->GetActivity(p, kMaxNumFrames - 1));

  EXPECT_EQ(0, vad->GetActivity(p, kMaxNumFrames));

  // Ask for activity when buffer is empty.
  EXPECT_EQ(-1, vad->GetActivity(p, kMaxNumFrames));

  // Should reset and result in one buffer.
  for (size_t n = 0; n < kMaxNumFrames + 1; n++)
    EXPECT_EQ(0, vad->AddAudio(data, kLength10Ms));
  EXPECT_EQ(0, vad->GetActivity(p, 1));

  // Wrong modes
  EXPECT_EQ(-1, vad->set_mode(-1));
  EXPECT_EQ(-1, vad->set_mode(4));

  // Valid mode.
  const int kMode = 2;
  EXPECT_EQ(0, vad->set_mode(kMode));
  EXPECT_EQ(kMode, vad->mode());
}

#if defined(WEBRTC_IOS)
TEST(StandaloneVadTest, DISABLED_ActivityDetection) {
#else
TEST(StandaloneVadTest, ActivityDetection) {
#endif
  std::unique_ptr<StandaloneVad> vad(StandaloneVad::Create());
  const size_t kDataLength = kLength10Ms;
  int16_t data[kDataLength] = {0};

  FILE* pcm_file =
      fopen(test::ResourcePath("audio_processing/agc/agc_audio", "pcm").c_str(),
            "rb");
  ASSERT_TRUE(pcm_file != NULL);

  FILE* reference_file = fopen(
      test::ResourcePath("audio_processing/agc/agc_vad", "dat").c_str(), "rb");
  ASSERT_TRUE(reference_file != NULL);

  // Reference activities are prepared with 0 aggressiveness.
  ASSERT_EQ(0, vad->set_mode(0));

  // Stand-alone VAD can operate on 1, 2 or 3 frames of length 10 ms. The
  // reference file is created for 30 ms frame.
  const int kNumVadFramesToProcess = 3;
  int num_frames = 0;
  while (fread(data, sizeof(int16_t), kDataLength, pcm_file) == kDataLength) {
    vad->AddAudio(data, kDataLength);
    num_frames++;
    if (num_frames == kNumVadFramesToProcess) {
      num_frames = 0;
      int referece_activity;
      double p[kNumVadFramesToProcess];
      EXPECT_EQ(1u, fread(&referece_activity, sizeof(referece_activity), 1,
                          reference_file));
      int activity = vad->GetActivity(p, kNumVadFramesToProcess);
      EXPECT_EQ(referece_activity, activity);
      if (activity != 0) {
        // When active, probabilities are set to 0.5.
        for (int n = 0; n < kNumVadFramesToProcess; n++)
          EXPECT_EQ(0.5, p[n]);
      } else {
        // When inactive, probabilities are set to 0.01.
        for (int n = 0; n < kNumVadFramesToProcess; n++)
          EXPECT_EQ(0.01, p[n]);
      }
    }
  }
  fclose(reference_file);
  fclose(pcm_file);
}
}  // namespace webrtc
