/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdlib.h>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_audio/vad/vad_unittest.h"
#include "webrtc/typedefs.h"

extern "C" {
#include "webrtc/common_audio/vad/vad_core.h"
}

namespace {

TEST_F(VadTest, InitCore) {
  // Test WebRtcVad_InitCore().
  VadInstT* self = reinterpret_cast<VadInstT*>(malloc(sizeof(VadInstT)));

  // NULL pointer test.
  EXPECT_EQ(-1, WebRtcVad_InitCore(NULL));

  // Verify return = 0 for non-NULL pointer.
  EXPECT_EQ(0, WebRtcVad_InitCore(self));
  // Verify init_flag is set.
  EXPECT_EQ(42, self->init_flag);

  free(self);
}

TEST_F(VadTest, set_mode_core) {
  VadInstT* self = reinterpret_cast<VadInstT*>(malloc(sizeof(VadInstT)));

  // TODO(bjornv): Add NULL pointer check if we take care of it in
  // vad_core.c

  ASSERT_EQ(0, WebRtcVad_InitCore(self));
  // Test WebRtcVad_set_mode_core().
  // Invalid modes should return -1.
  EXPECT_EQ(-1, WebRtcVad_set_mode_core(self, -1));
  EXPECT_EQ(-1, WebRtcVad_set_mode_core(self, 1000));
  // Valid modes should return 0.
  for (size_t j = 0; j < kModesSize; ++j) {
    EXPECT_EQ(0, WebRtcVad_set_mode_core(self, kModes[j]));
  }

  free(self);
}

TEST_F(VadTest, CalcVad) {
  VadInstT* self = reinterpret_cast<VadInstT*>(malloc(sizeof(VadInstT)));
  int16_t speech[kMaxFrameLength];

  // TODO(bjornv): Add NULL pointer check if we take care of it in
  // vad_core.c

  // Test WebRtcVad_CalcVadXXkhz()
  // Verify that all zeros in gives VAD = 0 out.
  memset(speech, 0, sizeof(speech));
  ASSERT_EQ(0, WebRtcVad_InitCore(self));
  for (size_t j = 0; j < kFrameLengthsSize; ++j) {
    if (ValidRatesAndFrameLengths(8000, kFrameLengths[j])) {
      EXPECT_EQ(0, WebRtcVad_CalcVad8khz(self, speech, kFrameLengths[j]));
    }
    if (ValidRatesAndFrameLengths(16000, kFrameLengths[j])) {
      EXPECT_EQ(0, WebRtcVad_CalcVad16khz(self, speech, kFrameLengths[j]));
    }
    if (ValidRatesAndFrameLengths(32000, kFrameLengths[j])) {
      EXPECT_EQ(0, WebRtcVad_CalcVad32khz(self, speech, kFrameLengths[j]));
    }
    if (ValidRatesAndFrameLengths(48000, kFrameLengths[j])) {
      EXPECT_EQ(0, WebRtcVad_CalcVad48khz(self, speech, kFrameLengths[j]));
    }
  }

  // Construct a speech signal that will trigger the VAD in all modes. It is
  // known that (i * i) will wrap around, but that doesn't matter in this case.
  for (int16_t i = 0; i < kMaxFrameLength; ++i) {
    speech[i] = (i * i);
  }
  for (size_t j = 0; j < kFrameLengthsSize; ++j) {
    if (ValidRatesAndFrameLengths(8000, kFrameLengths[j])) {
      EXPECT_EQ(1, WebRtcVad_CalcVad8khz(self, speech, kFrameLengths[j]));
    }
    if (ValidRatesAndFrameLengths(16000, kFrameLengths[j])) {
      EXPECT_EQ(1, WebRtcVad_CalcVad16khz(self, speech, kFrameLengths[j]));
    }
    if (ValidRatesAndFrameLengths(32000, kFrameLengths[j])) {
      EXPECT_EQ(1, WebRtcVad_CalcVad32khz(self, speech, kFrameLengths[j]));
    }
    if (ValidRatesAndFrameLengths(48000, kFrameLengths[j])) {
      EXPECT_EQ(1, WebRtcVad_CalcVad48khz(self, speech, kFrameLengths[j]));
    }
  }

  free(self);
}
}  // namespace
