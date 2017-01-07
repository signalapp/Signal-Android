/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_BEAMFORMER_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_BEAMFORMER_H_

#include "webrtc/common_audio/channel_buffer.h"
#include "webrtc/modules/audio_processing/beamformer/array_util.h"

namespace webrtc {

template<typename T>
class Beamformer {
 public:
  virtual ~Beamformer() {}

  // Process one time-domain chunk of audio. The audio is expected to be split
  // into frequency bands inside the ChannelBuffer. The number of frames and
  // channels must correspond to the constructor parameters. The same
  // ChannelBuffer can be passed in as |input| and |output|.
  virtual void ProcessChunk(const ChannelBuffer<T>& input,
                            ChannelBuffer<T>* output) = 0;

  // Sample rate corresponds to the lower band.
  // Needs to be called before the the Beamformer can be used.
  virtual void Initialize(int chunk_size_ms, int sample_rate_hz) = 0;

  // Aim the beamformer at a point in space.
  virtual void AimAt(const SphericalPointf& spherical_point) = 0;

  // Indicates whether a given point is inside of the beam.
  virtual bool IsInBeam(const SphericalPointf& spherical_point) { return true; }

  // Returns true if the current data contains the target signal.
  // Which signals are considered "targets" is implementation dependent.
  virtual bool is_target_present() = 0;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_BEAMFORMER_H_
