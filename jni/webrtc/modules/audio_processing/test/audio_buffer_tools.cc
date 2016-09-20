/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/test/audio_buffer_tools.h"

#include <string.h>

namespace webrtc {
namespace test {

void SetupFrame(const StreamConfig& stream_config,
                std::vector<float*>* frame,
                std::vector<float>* frame_samples) {
  frame_samples->resize(stream_config.num_channels() *
                        stream_config.num_frames());
  frame->resize(stream_config.num_channels());
  for (size_t ch = 0; ch < stream_config.num_channels(); ++ch) {
    (*frame)[ch] = &(*frame_samples)[ch * stream_config.num_frames()];
  }
}

void CopyVectorToAudioBuffer(const StreamConfig& stream_config,
                             rtc::ArrayView<const float> source,
                             AudioBuffer* destination) {
  std::vector<float*> input;
  std::vector<float> input_samples;

  SetupFrame(stream_config, &input, &input_samples);

  RTC_CHECK_EQ(input_samples.size(), source.size());
  memcpy(input_samples.data(), source.data(),
         source.size() * sizeof(source[0]));

  destination->CopyFrom(&input[0], stream_config);
}

void ExtractVectorFromAudioBuffer(const StreamConfig& stream_config,
                                  AudioBuffer* source,
                                  std::vector<float>* destination) {
  std::vector<float*> output;

  SetupFrame(stream_config, &output, destination);

  source->CopyTo(stream_config, &output[0]);
}

}  // namespace test
}  // namespace webrtc
