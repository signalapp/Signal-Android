/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_ENCODER_ISAC_T_IMPL_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_ENCODER_ISAC_T_IMPL_H_

#include "webrtc/modules/audio_coding/codecs/isac/main/include/audio_encoder_isac.h"

#include "webrtc/base/checks.h"
#include "webrtc/common_types.h"

namespace webrtc {

template <typename T>
typename AudioEncoderIsacT<T>::Config CreateIsacConfig(
    const CodecInst& codec_inst,
    const rtc::scoped_refptr<LockedIsacBandwidthInfo>& bwinfo) {
  typename AudioEncoderIsacT<T>::Config config;
  config.bwinfo = bwinfo;
  config.payload_type = codec_inst.pltype;
  config.sample_rate_hz = codec_inst.plfreq;
  config.frame_size_ms =
      rtc::CheckedDivExact(1000 * codec_inst.pacsize, config.sample_rate_hz);
  config.adaptive_mode = (codec_inst.rate == -1);
  if (codec_inst.rate != -1)
    config.bit_rate = codec_inst.rate;
  return config;
}

template <typename T>
bool AudioEncoderIsacT<T>::Config::IsOk() const {
  if (max_bit_rate < 32000 && max_bit_rate != -1)
    return false;
  if (max_payload_size_bytes < 120 && max_payload_size_bytes != -1)
    return false;
  if (adaptive_mode && !bwinfo)
    return false;
  switch (sample_rate_hz) {
    case 16000:
      if (max_bit_rate > 53400)
        return false;
      if (max_payload_size_bytes > 400)
        return false;
      return (frame_size_ms == 30 || frame_size_ms == 60) &&
             (bit_rate == 0 || (bit_rate >= 10000 && bit_rate <= 32000));
    case 32000:
      if (max_bit_rate > 160000)
        return false;
      if (max_payload_size_bytes > 600)
        return false;
      return T::has_swb &&
             (frame_size_ms == 30 &&
              (bit_rate == 0 || (bit_rate >= 10000 && bit_rate <= 56000)));
    default:
      return false;
  }
}

template <typename T>
AudioEncoderIsacT<T>::AudioEncoderIsacT(const Config& config) {
  RecreateEncoderInstance(config);
}

template <typename T>
AudioEncoderIsacT<T>::AudioEncoderIsacT(
    const CodecInst& codec_inst,
    const rtc::scoped_refptr<LockedIsacBandwidthInfo>& bwinfo)
    : AudioEncoderIsacT(CreateIsacConfig<T>(codec_inst, bwinfo)) {}

template <typename T>
AudioEncoderIsacT<T>::~AudioEncoderIsacT() {
  RTC_CHECK_EQ(0, T::Free(isac_state_));
}

template <typename T>
int AudioEncoderIsacT<T>::SampleRateHz() const {
  return T::EncSampRate(isac_state_);
}

template <typename T>
size_t AudioEncoderIsacT<T>::NumChannels() const {
  return 1;
}

template <typename T>
size_t AudioEncoderIsacT<T>::Num10MsFramesInNextPacket() const {
  const int samples_in_next_packet = T::GetNewFrameLen(isac_state_);
  return static_cast<size_t>(
      rtc::CheckedDivExact(samples_in_next_packet,
                           rtc::CheckedDivExact(SampleRateHz(), 100)));
}

template <typename T>
size_t AudioEncoderIsacT<T>::Max10MsFramesInAPacket() const {
  return 6;  // iSAC puts at most 60 ms in a packet.
}

template <typename T>
int AudioEncoderIsacT<T>::GetTargetBitrate() const {
  if (config_.adaptive_mode)
    return -1;
  return config_.bit_rate == 0 ? kDefaultBitRate : config_.bit_rate;
}

template <typename T>
AudioEncoder::EncodedInfo AudioEncoderIsacT<T>::EncodeImpl(
    uint32_t rtp_timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {
  if (!packet_in_progress_) {
    // Starting a new packet; remember the timestamp for later.
    packet_in_progress_ = true;
    packet_timestamp_ = rtp_timestamp;
  }
  if (bwinfo_) {
    IsacBandwidthInfo bwinfo = bwinfo_->Get();
    T::SetBandwidthInfo(isac_state_, &bwinfo);
  }

  size_t encoded_bytes = encoded->AppendData(
      kSufficientEncodeBufferSizeBytes,
      [&] (rtc::ArrayView<uint8_t> encoded) {
        int r = T::Encode(isac_state_, audio.data(), encoded.data());

        RTC_CHECK_GE(r, 0) << "Encode failed (error code "
                           << T::GetErrorCode(isac_state_) << ")";

        return static_cast<size_t>(r);
      });

  if (encoded_bytes == 0)
    return EncodedInfo();

  // Got enough input to produce a packet. Return the saved timestamp from
  // the first chunk of input that went into the packet.
  packet_in_progress_ = false;
  EncodedInfo info;
  info.encoded_bytes = encoded_bytes;
  info.encoded_timestamp = packet_timestamp_;
  info.payload_type = config_.payload_type;
  info.encoder_type = CodecType::kIsac;
  return info;
}

template <typename T>
void AudioEncoderIsacT<T>::Reset() {
  RecreateEncoderInstance(config_);
}

template <typename T>
void AudioEncoderIsacT<T>::RecreateEncoderInstance(const Config& config) {
  RTC_CHECK(config.IsOk());
  packet_in_progress_ = false;
  bwinfo_ = config.bwinfo;
  if (isac_state_)
    RTC_CHECK_EQ(0, T::Free(isac_state_));
  RTC_CHECK_EQ(0, T::Create(&isac_state_));
  RTC_CHECK_EQ(0, T::EncoderInit(isac_state_, config.adaptive_mode ? 0 : 1));
  RTC_CHECK_EQ(0, T::SetEncSampRate(isac_state_, config.sample_rate_hz));
  const int bit_rate = config.bit_rate == 0 ? kDefaultBitRate : config.bit_rate;
  if (config.adaptive_mode) {
    RTC_CHECK_EQ(0, T::ControlBwe(isac_state_, bit_rate, config.frame_size_ms,
                                  config.enforce_frame_size));
  } else {
    RTC_CHECK_EQ(0, T::Control(isac_state_, bit_rate, config.frame_size_ms));
  }
  if (config.max_payload_size_bytes != -1)
    RTC_CHECK_EQ(
        0, T::SetMaxPayloadSize(isac_state_, config.max_payload_size_bytes));
  if (config.max_bit_rate != -1)
    RTC_CHECK_EQ(0, T::SetMaxRate(isac_state_, config.max_bit_rate));

  // Set the decoder sample rate even though we just use the encoder. This
  // doesn't appear to be necessary to produce a valid encoding, but without it
  // we get an encoding that isn't bit-for-bit identical with what a combined
  // encoder+decoder object produces.
  RTC_CHECK_EQ(0, T::SetDecSampRate(isac_state_, config.sample_rate_hz));

  config_ = config;
}

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_ENCODER_ISAC_T_IMPL_H_
