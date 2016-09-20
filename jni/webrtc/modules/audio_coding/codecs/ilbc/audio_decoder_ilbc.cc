/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/ilbc/audio_decoder_ilbc.h"

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_coding/codecs/ilbc/ilbc.h"

namespace webrtc {

AudioDecoderIlbc::AudioDecoderIlbc() {
  WebRtcIlbcfix_DecoderCreate(&dec_state_);
  WebRtcIlbcfix_Decoderinit30Ms(dec_state_);
}

AudioDecoderIlbc::~AudioDecoderIlbc() {
  WebRtcIlbcfix_DecoderFree(dec_state_);
}

bool AudioDecoderIlbc::HasDecodePlc() const {
  return true;
}

int AudioDecoderIlbc::DecodeInternal(const uint8_t* encoded,
                                     size_t encoded_len,
                                     int sample_rate_hz,
                                     int16_t* decoded,
                                     SpeechType* speech_type) {
  RTC_DCHECK_EQ(sample_rate_hz, 8000);
  int16_t temp_type = 1;  // Default is speech.
  int ret = WebRtcIlbcfix_Decode(dec_state_, encoded, encoded_len, decoded,
                                 &temp_type);
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

size_t AudioDecoderIlbc::DecodePlc(size_t num_frames, int16_t* decoded) {
  return WebRtcIlbcfix_NetEqPlc(dec_state_, decoded, num_frames);
}

void AudioDecoderIlbc::Reset() {
  WebRtcIlbcfix_Decoderinit30Ms(dec_state_);
}

int AudioDecoderIlbc::SampleRateHz() const {
  return 8000;
}

size_t AudioDecoderIlbc::Channels() const {
  return 1;
}

}  // namespace webrtc
