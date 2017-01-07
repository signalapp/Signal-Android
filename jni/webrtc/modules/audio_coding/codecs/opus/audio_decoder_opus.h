/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_OPUS_AUDIO_DECODER_OPUS_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_OPUS_AUDIO_DECODER_OPUS_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"
#include "webrtc/modules/audio_coding/codecs/opus/opus_interface.h"

namespace webrtc {

class AudioDecoderOpus final : public AudioDecoder {
 public:
  explicit AudioDecoderOpus(size_t num_channels);
  ~AudioDecoderOpus() override;

  void Reset() override;
  int PacketDuration(const uint8_t* encoded, size_t encoded_len) const override;
  int PacketDurationRedundant(const uint8_t* encoded,
                              size_t encoded_len) const override;
  bool PacketHasFec(const uint8_t* encoded, size_t encoded_len) const override;
  int SampleRateHz() const override;
  size_t Channels() const override;

 protected:
  int DecodeInternal(const uint8_t* encoded,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override;
  int DecodeRedundantInternal(const uint8_t* encoded,
                              size_t encoded_len,
                              int sample_rate_hz,
                              int16_t* decoded,
                              SpeechType* speech_type) override;

 private:
  OpusDecInst* dec_state_;
  const size_t channels_;
  RTC_DISALLOW_COPY_AND_ASSIGN(AudioDecoderOpus);
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_OPUS_AUDIO_DECODER_OPUS_H_
