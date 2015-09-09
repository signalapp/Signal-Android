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

#include <assert.h>
#include <string.h>

namespace webrtc {

static const int kDefaultSampleRateHz = 48000;
static const int kDefaultFrameRateHz = 50;
static const int kDefaultFrameSizeSamples =
    kDefaultSampleRateHz / kDefaultFrameRateHz;
static const float kDefaultThreshold = 0.5f;

AudioClassifier::AudioClassifier()
    : analysis_info_(),
      is_music_(false),
      music_probability_(0),
      // This actually assigns the pointer to a static constant struct
      // rather than creates a struct and |celt_mode_| does not need
      // to be deleted.
      celt_mode_(opus_custom_mode_create(kDefaultSampleRateHz,
                                         kDefaultFrameSizeSamples,
                                         NULL)),
      analysis_state_() {
  assert(celt_mode_);
}

AudioClassifier::~AudioClassifier() {}

bool AudioClassifier::Analysis(const int16_t* input,
                               int input_length,
                               int channels) {
  // Must be 20 ms frames at 48 kHz sampling.
  assert((input_length / channels) == kDefaultFrameSizeSamples);

  // Only mono or stereo are allowed.
  assert(channels == 1 || channels == 2);

  // Call Opus' classifier, defined in
  // "third_party/opus/src/src/analysis.h", with lsb_depth = 16.
  // Also uses a down-mixing function downmix_int, defined in
  // "third_party/opus/src/src/opus_private.h", with
  // constants c1 = 0, and c2 = -2.
  run_analysis(&analysis_state_,
               celt_mode_,
               input,
               kDefaultFrameSizeSamples,
               kDefaultFrameSizeSamples,
               0,
               -2,
               channels,
               kDefaultSampleRateHz,
               16,
               downmix_int,
               &analysis_info_);
  music_probability_ = analysis_info_.music_prob;
  is_music_ = music_probability_ > kDefaultThreshold;
  return is_music_;
}

}  // namespace webrtc
