/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_G711_AUDIO_DECODER_PCM_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_G711_AUDIO_DECODER_PCM_H_

#include "webrtc/base/checks.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"

namespace webrtc {

class AudioDecoderPcmU final : public AudioDecoder {
 public:
  explicit AudioDecoderPcmU(size_t num_channels) : num_channels_(num_channels) {
    RTC_DCHECK_GE(num_channels, 1u);
  }
  void Reset() override;
  int PacketDuration(const uint8_t* encoded, size_t encoded_len) const override;
  int SampleRateHz() const override;
  size_t Channels() const override;

 protected:
  int DecodeInternal(const uint8_t* encoded,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override;

 private:
  const size_t num_channels_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AudioDecoderPcmU);
};

class AudioDecoderPcmA final : public AudioDecoder {
 public:
  explicit AudioDecoderPcmA(size_t num_channels) : num_channels_(num_channels) {
    RTC_DCHECK_GE(num_channels, 1u);
  }
  void Reset() override;
  int PacketDuration(const uint8_t* encoded, size_t encoded_len) const override;
  int SampleRateHz() const override;
  size_t Channels() const override;

 protected:
  int DecodeInternal(const uint8_t* encoded,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override;

 private:
  const size_t num_channels_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AudioDecoderPcmA);
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_G711_AUDIO_DECODER_PCM_H_
