/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_TEST_AUDIO_BUFFER_TOOLS_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_TEST_AUDIO_BUFFER_TOOLS_H_

#include <vector>
#include "webrtc/base/array_view.h"
#include "webrtc/modules/audio_processing/audio_buffer.h"
#include "webrtc/modules/audio_processing/include/audio_processing.h"

namespace webrtc {
namespace test {

// Copies a vector into an audiobuffer.
void CopyVectorToAudioBuffer(const StreamConfig& stream_config,
                             rtc::ArrayView<const float> source,
                             AudioBuffer* destination);

// Extracts a vector from an audiobuffer.
void ExtractVectorFromAudioBuffer(const StreamConfig& stream_config,
                                  AudioBuffer* source,
                                  std::vector<float>* destination);

}  // namespace test
}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_TEST_AUDIO_BUFFER_TOOLS_H_
