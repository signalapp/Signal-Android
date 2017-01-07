/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/opus/audio_decoder_opus.h"

#include "webrtc/base/checks.h"

namespace webrtc {

AudioDecoderOpus::AudioDecoderOpus(size_t num_channels)
    : channels_(num_channels) {
  RTC_DCHECK(num_channels == 1 || num_channels == 2);
  WebRtcOpus_DecoderCreate(&dec_state_, channels_);
  WebRtcOpus_DecoderInit(dec_state_);
}

AudioDecoderOpus::~AudioDecoderOpus() {
  WebRtcOpus_DecoderFree(dec_state_);
}

int AudioDecoderOpus::DecodeInternal(const uint8_t* encoded,
                                     size_t encoded_len,
                                     int sample_rate_hz,
                                     int16_t* decoded,
                                     SpeechType* speech_type) {
  RTC_DCHECK_EQ(sample_rate_hz, 48000);
  int16_t temp_type = 1;  // Default is speech.
  int ret =
      WebRtcOpus_Decode(dec_state_, encoded, encoded_len, decoded, &temp_type);
  if (ret > 0)
    ret *= static_cast<int>(channels_);  // Return total number of samples.
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

int AudioDecoderOpus::DecodeRedundantInternal(const uint8_t* encoded,
                                              size_t encoded_len,
                                              int sample_rate_hz,
                                              int16_t* decoded,
                                              SpeechType* speech_type) {
  if (!PacketHasFec(encoded, encoded_len)) {
    // This packet is a RED packet.
    return DecodeInternal(encoded, encoded_len, sample_rate_hz, decoded,
                          speech_type);
  }

  RTC_DCHECK_EQ(sample_rate_hz, 48000);
  int16_t temp_type = 1;  // Default is speech.
  int ret = WebRtcOpus_DecodeFec(dec_state_, encoded, encoded_len, decoded,
                                 &temp_type);
  if (ret > 0)
    ret *= static_cast<int>(channels_);  // Return total number of samples.
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

void AudioDecoderOpus::Reset() {
  WebRtcOpus_DecoderInit(dec_state_);
}

int AudioDecoderOpus::PacketDuration(const uint8_t* encoded,
                                     size_t encoded_len) const {
  return WebRtcOpus_DurationEst(dec_state_, encoded, encoded_len);
}

int AudioDecoderOpus::PacketDurationRedundant(const uint8_t* encoded,
                                              size_t encoded_len) const {
  if (!PacketHasFec(encoded, encoded_len)) {
    // This packet is a RED packet.
    return PacketDuration(encoded, encoded_len);
  }

  return WebRtcOpus_FecDurationEst(encoded, encoded_len);
}

bool AudioDecoderOpus::PacketHasFec(const uint8_t* encoded,
                                    size_t encoded_len) const {
  int fec;
  fec = WebRtcOpus_PacketHasFec(encoded, encoded_len);
  return (fec == 1);
}

int AudioDecoderOpus::SampleRateHz() const {
  return 48000;
}

size_t AudioDecoderOpus::Channels() const {
  return channels_;
}

}  // namespace webrtc
