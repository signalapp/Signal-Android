/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_RMS_LEVEL_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_RMS_LEVEL_H_

#include <cstddef>

#include "webrtc/typedefs.h"

namespace webrtc {

// Computes the root mean square (RMS) level in dBFs (decibels from digital
// full-scale) of audio data. The computation follows RFC 6465:
// https://tools.ietf.org/html/rfc6465
// with the intent that it can provide the RTP audio level indication.
//
// The expected approach is to provide constant-sized chunks of audio to
// Process(). When enough chunks have been accumulated to form a packet, call
// RMS() to get the audio level indicator for the RTP header.
class RMSLevel {
 public:
  static const int kMinLevel = 127;

  RMSLevel();
  ~RMSLevel();

  // Can be called to reset internal states, but is not required during normal
  // operation.
  void Reset();

  // Pass each chunk of audio to Process() to accumulate the level.
  void Process(const int16_t* data, size_t length);

  // If all samples with the given |length| have a magnitude of zero, this is
  // a shortcut to avoid some computation.
  void ProcessMuted(size_t length);

  // Computes the RMS level over all data passed to Process() since the last
  // call to RMS(). The returned value is positive but should be interpreted as
  // negative as per the RFC. It is constrained to [0, 127].
  int RMS();

 private:
  float sum_square_;
  size_t sample_count_;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_RMS_LEVEL_H_

