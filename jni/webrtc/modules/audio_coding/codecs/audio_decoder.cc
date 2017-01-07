/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"

#include <assert.h>

#include "webrtc/base/checks.h"
#include "webrtc/base/trace_event.h"

namespace webrtc {

int AudioDecoder::Decode(const uint8_t* encoded, size_t encoded_len,
                         int sample_rate_hz, size_t max_decoded_bytes,
                         int16_t* decoded, SpeechType* speech_type) {
  TRACE_EVENT0("webrtc", "AudioDecoder::Decode");
  int duration = PacketDuration(encoded, encoded_len);
  if (duration >= 0 &&
      duration * Channels() * sizeof(int16_t) > max_decoded_bytes) {
    return -1;
  }
  return DecodeInternal(encoded, encoded_len, sample_rate_hz, decoded,
                        speech_type);
}

int AudioDecoder::DecodeRedundant(const uint8_t* encoded, size_t encoded_len,
                                  int sample_rate_hz, size_t max_decoded_bytes,
                                  int16_t* decoded, SpeechType* speech_type) {
  TRACE_EVENT0("webrtc", "AudioDecoder::DecodeRedundant");
  int duration = PacketDurationRedundant(encoded, encoded_len);
  if (duration >= 0 &&
      duration * Channels() * sizeof(int16_t) > max_decoded_bytes) {
    return -1;
  }
  return DecodeRedundantInternal(encoded, encoded_len, sample_rate_hz, decoded,
                                 speech_type);
}

int AudioDecoder::DecodeRedundantInternal(const uint8_t* encoded,
                                          size_t encoded_len,
                                          int sample_rate_hz, int16_t* decoded,
                                          SpeechType* speech_type) {
  return DecodeInternal(encoded, encoded_len, sample_rate_hz, decoded,
                        speech_type);
}

bool AudioDecoder::HasDecodePlc() const { return false; }

size_t AudioDecoder::DecodePlc(size_t num_frames, int16_t* decoded) {
  return 0;
}

int AudioDecoder::IncomingPacket(const uint8_t* payload,
                                 size_t payload_len,
                                 uint16_t rtp_sequence_number,
                                 uint32_t rtp_timestamp,
                                 uint32_t arrival_timestamp) {
  return 0;
}

int AudioDecoder::ErrorCode() { return 0; }

int AudioDecoder::PacketDuration(const uint8_t* encoded,
                                 size_t encoded_len) const {
  return kNotImplemented;
}

int AudioDecoder::PacketDurationRedundant(const uint8_t* encoded,
                                          size_t encoded_len) const {
  return kNotImplemented;
}

bool AudioDecoder::PacketHasFec(const uint8_t* encoded,
                                size_t encoded_len) const {
  return false;
}

AudioDecoder::SpeechType AudioDecoder::ConvertSpeechType(int16_t type) {
  switch (type) {
    case 0:  // TODO(hlundin): Both iSAC and Opus return 0 for speech.
    case 1:
      return kSpeech;
    case 2:
      return kComfortNoise;
    default:
      assert(false);
      return kSpeech;
  }
}

}  // namespace webrtc
