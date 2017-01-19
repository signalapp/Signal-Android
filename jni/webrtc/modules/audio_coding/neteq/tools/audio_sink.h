/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_AUDIO_SINK_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_AUDIO_SINK_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/interface/module_common_types.h"
#include "webrtc/typedefs.h"

namespace webrtc {
namespace test {

// Interface class for an object receiving raw output audio from test
// applications.
class AudioSink {
 public:
  AudioSink() {}
  virtual ~AudioSink() {}

  // Writes |num_samples| from |audio| to the AudioSink. Returns true if
  // successful, otherwise false.
  virtual bool WriteArray(const int16_t* audio, size_t num_samples) = 0;

  // Writes |audio_frame| to the AudioSink. Returns true if successful,
  // otherwise false.
  bool WriteAudioFrame(const AudioFrame& audio_frame) {
    return WriteArray(
        audio_frame.data_,
        audio_frame.samples_per_channel_ * audio_frame.num_channels_);
  }

 private:
  DISALLOW_COPY_AND_ASSIGN(AudioSink);
};

// Forks the output audio to two AudioSink objects.
class AudioSinkFork : public AudioSink {
 public:
  AudioSinkFork(AudioSink* left, AudioSink* right)
      : left_sink_(left), right_sink_(right) {}

  virtual bool WriteArray(const int16_t* audio, size_t num_samples) OVERRIDE {
    return left_sink_->WriteArray(audio, num_samples) &&
           right_sink_->WriteArray(audio, num_samples);
  }

 private:
  AudioSink* left_sink_;
  AudioSink* right_sink_;

  DISALLOW_COPY_AND_ASSIGN(AudioSinkFork);
};
}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_AUDIO_SINK_H_
