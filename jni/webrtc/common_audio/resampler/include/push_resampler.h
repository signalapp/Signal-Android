/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_AUDIO_RESAMPLER_INCLUDE_PUSH_RESAMPLER_H_
#define WEBRTC_COMMON_AUDIO_RESAMPLER_INCLUDE_PUSH_RESAMPLER_H_

#include <memory>

#include "webrtc/typedefs.h"

namespace webrtc {

class PushSincResampler;

// Wraps PushSincResampler to provide stereo support.
// TODO(ajm): add support for an arbitrary number of channels.
template <typename T>
class PushResampler {
 public:
  PushResampler();
  virtual ~PushResampler();

  // Must be called whenever the parameters change. Free to be called at any
  // time as it is a no-op if parameters have not changed since the last call.
  int InitializeIfNeeded(int src_sample_rate_hz, int dst_sample_rate_hz,
                         size_t num_channels);

  // Returns the total number of samples provided in destination (e.g. 32 kHz,
  // 2 channel audio gives 640 samples).
  int Resample(const T* src, size_t src_length, T* dst, size_t dst_capacity);

 private:
  std::unique_ptr<PushSincResampler> sinc_resampler_;
  std::unique_ptr<PushSincResampler> sinc_resampler_right_;
  int src_sample_rate_hz_;
  int dst_sample_rate_hz_;
  size_t num_channels_;
  std::unique_ptr<T[]> src_left_;
  std::unique_ptr<T[]> src_right_;
  std::unique_ptr<T[]> dst_left_;
  std::unique_ptr<T[]> dst_right_;
};

}  // namespace webrtc

#endif  // WEBRTC_COMMON_AUDIO_RESAMPLER_INCLUDE_PUSH_RESAMPLER_H_
