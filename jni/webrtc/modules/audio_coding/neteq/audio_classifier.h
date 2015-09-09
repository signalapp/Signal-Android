/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_AUDIO_CLASSIFIER_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_AUDIO_CLASSIFIER_H_

#if defined(__cplusplus)
extern "C" {
#endif
#include "celt.h"
#include "analysis.h"
#include "opus_private.h"
#if defined(__cplusplus)
}
#endif

#include "webrtc/system_wrappers/interface/scoped_ptr.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// This class provides a speech/music classification and is a wrapper over the
// Opus classifier. It currently only supports 48 kHz mono or stereo with a
// frame size of 20 ms.

class AudioClassifier {
 public:
  AudioClassifier();
  virtual ~AudioClassifier();

  // Classifies one frame of audio data in input,
  // input_length   : must be channels * 960;
  // channels       : must be 1 (mono) or 2 (stereo).
  bool Analysis(const int16_t* input, int input_length, int channels);

  // Gets the current classification : true = music, false = speech.
  virtual bool is_music() const { return is_music_; }

  // Gets the current music probability.
  float music_probability() const { return music_probability_; }

 private:
  AnalysisInfo analysis_info_;
  bool is_music_;
  float music_probability_;
  const CELTMode* celt_mode_;
  TonalityAnalysisState analysis_state_;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_AUDIO_CLASSIFIER_H_
