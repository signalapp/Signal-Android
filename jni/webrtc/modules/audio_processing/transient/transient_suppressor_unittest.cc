/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/transient/transient_suppressor.h"

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_processing/transient/common.h"

namespace webrtc {

TEST(TransientSuppressorTest, TypingDetectionLogicWorksAsExpectedForMono) {
  static const int kNumChannels = 1;

  TransientSuppressor ts;
  ts.Initialize(ts::kSampleRate16kHz, ts::kSampleRate16kHz, kNumChannels);

  // Each key-press enables detection.
  EXPECT_FALSE(ts.detection_enabled_);
  ts.UpdateKeypress(true);
  EXPECT_TRUE(ts.detection_enabled_);

  // It takes four seconds without any key-press to disable the detection
  for (int time_ms = 0; time_ms < 3990; time_ms += ts::kChunkSizeMs) {
    ts.UpdateKeypress(false);
    EXPECT_TRUE(ts.detection_enabled_);
  }
  ts.UpdateKeypress(false);
  EXPECT_FALSE(ts.detection_enabled_);

  // Key-presses that are more than a second apart from each other don't enable
  // suppression.
  for (int i = 0; i < 100; ++i) {
    EXPECT_FALSE(ts.suppression_enabled_);
    ts.UpdateKeypress(true);
    EXPECT_TRUE(ts.detection_enabled_);
    EXPECT_FALSE(ts.suppression_enabled_);
    for (int time_ms = 0; time_ms < 990; time_ms += ts::kChunkSizeMs) {
      ts.UpdateKeypress(false);
      EXPECT_TRUE(ts.detection_enabled_);
      EXPECT_FALSE(ts.suppression_enabled_);
    }
    ts.UpdateKeypress(false);
  }

  // Two consecutive key-presses is enough to enable the suppression.
  ts.UpdateKeypress(true);
  EXPECT_FALSE(ts.suppression_enabled_);
  ts.UpdateKeypress(true);
  EXPECT_TRUE(ts.suppression_enabled_);

  // Key-presses that are less than a second apart from each other don't disable
  // detection nor suppression.
  for (int i = 0; i < 100; ++i) {
    for (int time_ms = 0; time_ms < 1000; time_ms += ts::kChunkSizeMs) {
      ts.UpdateKeypress(false);
      EXPECT_TRUE(ts.detection_enabled_);
      EXPECT_TRUE(ts.suppression_enabled_);
    }
    ts.UpdateKeypress(true);
    EXPECT_TRUE(ts.detection_enabled_);
    EXPECT_TRUE(ts.suppression_enabled_);
  }

  // It takes four seconds without any key-press to disable the detection and
  // suppression.
  for (int time_ms = 0; time_ms < 3990; time_ms += ts::kChunkSizeMs) {
    ts.UpdateKeypress(false);
    EXPECT_TRUE(ts.detection_enabled_);
    EXPECT_TRUE(ts.suppression_enabled_);
  }
  for (int time_ms = 0; time_ms < 1000; time_ms += ts::kChunkSizeMs) {
    ts.UpdateKeypress(false);
    EXPECT_FALSE(ts.detection_enabled_);
    EXPECT_FALSE(ts.suppression_enabled_);
  }
}

}  // namespace webrtc
