/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// TODO(bjornv): Make this a comprehensive test.

#include "webrtc/modules/audio_processing/aec/include/echo_cancellation.h"

#include <stdlib.h>
#include <time.h>

extern "C" {
#include "webrtc/modules/audio_processing/aec/aec_core.h"
}

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

TEST(EchoCancellationTest, CreateAndFreeHandlesErrors) {
  EXPECT_EQ(-1, WebRtcAec_Create(NULL));
  void* handle = NULL;
  ASSERT_EQ(0, WebRtcAec_Create(&handle));
  EXPECT_TRUE(handle != NULL);
  EXPECT_EQ(-1, WebRtcAec_Free(NULL));
  EXPECT_EQ(0, WebRtcAec_Free(handle));
}

TEST(EchoCancellationTest, ApplyAecCoreHandle) {
  void* handle = NULL;
  ASSERT_EQ(0, WebRtcAec_Create(&handle));
  EXPECT_TRUE(handle != NULL);
  EXPECT_TRUE(WebRtcAec_aec_core(NULL) == NULL);
  AecCore* aec_core = WebRtcAec_aec_core(handle);
  EXPECT_TRUE(aec_core != NULL);
  // A simple test to verify that we can set and get a value from the lower
  // level |aec_core| handle.
  int delay = 111;
  WebRtcAec_SetSystemDelay(aec_core, delay);
  EXPECT_EQ(delay, WebRtcAec_system_delay(aec_core));
  EXPECT_EQ(0, WebRtcAec_Free(handle));
}

}  // namespace webrtc
