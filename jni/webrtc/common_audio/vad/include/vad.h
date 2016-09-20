/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_AUDIO_VAD_INCLUDE_VAD_H_
#define WEBRTC_COMMON_AUDIO_VAD_INCLUDE_VAD_H_

#include <memory>

#include "webrtc/base/checks.h"
#include "webrtc/common_audio/vad/include/webrtc_vad.h"
#include "webrtc/typedefs.h"

namespace webrtc {

class Vad {
 public:
  enum Aggressiveness {
    kVadNormal = 0,
    kVadLowBitrate = 1,
    kVadAggressive = 2,
    kVadVeryAggressive = 3
  };

  enum Activity { kPassive = 0, kActive = 1, kError = -1 };

  virtual ~Vad() = default;

  // Calculates a VAD decision for the given audio frame. Valid sample rates
  // are 8000, 16000, and 32000 Hz; the number of samples must be such that the
  // frame is 10, 20, or 30 ms long.
  virtual Activity VoiceActivity(const int16_t* audio,
                                 size_t num_samples,
                                 int sample_rate_hz) = 0;

  // Resets VAD state.
  virtual void Reset() = 0;
};

// Returns a Vad instance that's implemented on top of WebRtcVad.
std::unique_ptr<Vad> CreateVad(Vad::Aggressiveness aggressiveness);

}  // namespace webrtc

#endif  // WEBRTC_COMMON_AUDIO_VAD_INCLUDE_VAD_H_
