/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_AUDIO_VAD_MOCK_MOCK_VAD_H_
#define WEBRTC_COMMON_AUDIO_VAD_MOCK_MOCK_VAD_H_

#include "webrtc/common_audio/vad/include/vad.h"

#include "testing/gmock/include/gmock/gmock.h"

namespace webrtc {

class MockVad : public Vad {
 public:
  virtual ~MockVad() { Die(); }
  MOCK_METHOD0(Die, void());

  MOCK_METHOD3(VoiceActivity,
               enum Activity(const int16_t* audio,
                             size_t num_samples,
                             int sample_rate_hz));
  MOCK_METHOD0(Reset, void());
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_AUDIO_VAD_MOCK_MOCK_VAD_H_
