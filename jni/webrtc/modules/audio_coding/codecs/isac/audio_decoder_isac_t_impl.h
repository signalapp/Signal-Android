/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_DECODER_ISAC_T_IMPL_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_DECODER_ISAC_T_IMPL_H_

#include "webrtc/modules/audio_coding/codecs/isac/main/include/audio_decoder_isac.h"

#include "webrtc/base/checks.h"

namespace webrtc {

template <typename T>
AudioDecoderIsacT<T>::AudioDecoderIsacT(int sample_rate_hz)
    : AudioDecoderIsacT(sample_rate_hz, nullptr) {}

template <typename T>
AudioDecoderIsacT<T>::AudioDecoderIsacT(
    int sample_rate_hz,
    const rtc::scoped_refptr<LockedIsacBandwidthInfo>& bwinfo)
    : sample_rate_hz_(sample_rate_hz), bwinfo_(bwinfo) {
  RTC_CHECK(sample_rate_hz == 16000 || sample_rate_hz == 32000)
      << "Unsupported sample rate " << sample_rate_hz;
  RTC_CHECK_EQ(0, T::Create(&isac_state_));
  T::DecoderInit(isac_state_);
  if (bwinfo_) {
    IsacBandwidthInfo bi;
    T::GetBandwidthInfo(isac_state_, &bi);
    bwinfo_->Set(bi);
  }
  RTC_CHECK_EQ(0, T::SetDecSampRate(isac_state_, sample_rate_hz_));
}

template <typename T>
AudioDecoderIsacT<T>::~AudioDecoderIsacT() {
  RTC_CHECK_EQ(0, T::Free(isac_state_));
}

template <typename T>
int AudioDecoderIsacT<T>::DecodeInternal(const uint8_t* encoded,
                                         size_t encoded_len,
                                         int sample_rate_hz,
                                         int16_t* decoded,
                                         SpeechType* speech_type) {
  RTC_CHECK_EQ(sample_rate_hz_, sample_rate_hz);
  int16_t temp_type = 1;  // Default is speech.
  int ret =
      T::DecodeInternal(isac_state_, encoded, encoded_len, decoded, &temp_type);
  *speech_type = ConvertSpeechType(temp_type);
  return ret;
}

template <typename T>
bool AudioDecoderIsacT<T>::HasDecodePlc() const {
  return false;
}

template <typename T>
size_t AudioDecoderIsacT<T>::DecodePlc(size_t num_frames, int16_t* decoded) {
  return T::DecodePlc(isac_state_, decoded, num_frames);
}

template <typename T>
void AudioDecoderIsacT<T>::Reset() {
  T::DecoderInit(isac_state_);
}

template <typename T>
int AudioDecoderIsacT<T>::IncomingPacket(const uint8_t* payload,
                                         size_t payload_len,
                                         uint16_t rtp_sequence_number,
                                         uint32_t rtp_timestamp,
                                         uint32_t arrival_timestamp) {
  int ret = T::UpdateBwEstimate(isac_state_, payload, payload_len,
                                rtp_sequence_number, rtp_timestamp,
                                arrival_timestamp);
  if (bwinfo_) {
    IsacBandwidthInfo bwinfo;
    T::GetBandwidthInfo(isac_state_, &bwinfo);
    bwinfo_->Set(bwinfo);
  }
  return ret;
}

template <typename T>
int AudioDecoderIsacT<T>::ErrorCode() {
  return T::GetErrorCode(isac_state_);
}

template <typename T>
int AudioDecoderIsacT<T>::SampleRateHz() const {
  return sample_rate_hz_;
}

template <typename T>
size_t AudioDecoderIsacT<T>::Channels() const {
  return 1;
}

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_DECODER_ISAC_T_IMPL_H_
