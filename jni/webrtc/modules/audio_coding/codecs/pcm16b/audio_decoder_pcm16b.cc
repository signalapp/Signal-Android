/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/pcm16b/audio_decoder_pcm16b.h"

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_coding/codecs/pcm16b/pcm16b.h"

namespace webrtc {

AudioDecoderPcm16B::AudioDecoderPcm16B(int sample_rate_hz, size_t num_channels)
    : sample_rate_hz_(sample_rate_hz), num_channels_(num_channels) {
  RTC_DCHECK(sample_rate_hz == 8000 || sample_rate_hz == 16000 ||
             sample_rate_hz == 32000 || sample_rate_hz == 48000)
      << "Unsupported sample rate " << sample_rate_hz;
  RTC_DCHECK_GE(num_channels, 1u);
}

void AudioDecoderPcm16B::Reset() {}

int AudioDecoderPcm16B::SampleRateHz() const {
  return sample_rate_hz_;
}

size_t AudioDecoderPcm16B::Channels() const {
  return num_channels_;
}

int AudioDecoderPcm16B::DecodeInternal(const uint8_t* encoded,
                                       size_t encoded_len,
                                       int sample_rate_hz,
                                       int16_t* decoded,
                                       SpeechType* speech_type) {
  RTC_DCHECK_EQ(sample_rate_hz_, sample_rate_hz);
  size_t ret = WebRtcPcm16b_Decode(encoded, encoded_len, decoded);
  *speech_type = ConvertSpeechType(1);
  return static_cast<int>(ret);
}

int AudioDecoderPcm16B::PacketDuration(const uint8_t* encoded,
                                       size_t encoded_len) const {
  // Two encoded byte per sample per channel.
  return static_cast<int>(encoded_len / (2 * Channels()));
}

}  // namespace webrtc
