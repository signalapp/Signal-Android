/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/ilbc/audio_encoder_ilbc.h"

#include <algorithm>
#include <limits>
#include "webrtc/base/checks.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/codecs/ilbc/ilbc.h"

namespace webrtc {

namespace {

const int kSampleRateHz = 8000;

AudioEncoderIlbc::Config CreateConfig(const CodecInst& codec_inst) {
  AudioEncoderIlbc::Config config;
  config.frame_size_ms = codec_inst.pacsize / 8;
  config.payload_type = codec_inst.pltype;
  return config;
}

}  // namespace

// static
const size_t AudioEncoderIlbc::kMaxSamplesPerPacket;

bool AudioEncoderIlbc::Config::IsOk() const {
  return (frame_size_ms == 20 || frame_size_ms == 30 || frame_size_ms == 40 ||
          frame_size_ms == 60) &&
      static_cast<size_t>(kSampleRateHz / 100 * (frame_size_ms / 10)) <=
          kMaxSamplesPerPacket;
}

AudioEncoderIlbc::AudioEncoderIlbc(const Config& config)
    : config_(config),
      num_10ms_frames_per_packet_(
          static_cast<size_t>(config.frame_size_ms / 10)),
      encoder_(nullptr) {
  Reset();
}

AudioEncoderIlbc::AudioEncoderIlbc(const CodecInst& codec_inst)
    : AudioEncoderIlbc(CreateConfig(codec_inst)) {}

AudioEncoderIlbc::~AudioEncoderIlbc() {
  RTC_CHECK_EQ(0, WebRtcIlbcfix_EncoderFree(encoder_));
}

int AudioEncoderIlbc::SampleRateHz() const {
  return kSampleRateHz;
}

size_t AudioEncoderIlbc::NumChannels() const {
  return 1;
}

size_t AudioEncoderIlbc::Num10MsFramesInNextPacket() const {
  return num_10ms_frames_per_packet_;
}

size_t AudioEncoderIlbc::Max10MsFramesInAPacket() const {
  return num_10ms_frames_per_packet_;
}

int AudioEncoderIlbc::GetTargetBitrate() const {
  switch (num_10ms_frames_per_packet_) {
    case 2: case 4:
      // 38 bytes per frame of 20 ms => 15200 bits/s.
      return 15200;
    case 3: case 6:
      // 50 bytes per frame of 30 ms => (approx) 13333 bits/s.
      return 13333;
    default:
      FATAL();
  }
}

AudioEncoder::EncodedInfo AudioEncoderIlbc::EncodeImpl(
    uint32_t rtp_timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {

  // Save timestamp if starting a new packet.
  if (num_10ms_frames_buffered_ == 0)
    first_timestamp_in_buffer_ = rtp_timestamp;

  // Buffer input.
  std::copy(audio.cbegin(), audio.cend(),
            input_buffer_ + kSampleRateHz / 100 * num_10ms_frames_buffered_);

  // If we don't yet have enough buffered input for a whole packet, we're done
  // for now.
  if (++num_10ms_frames_buffered_ < num_10ms_frames_per_packet_) {
    return EncodedInfo();
  }

  // Encode buffered input.
  RTC_DCHECK_EQ(num_10ms_frames_buffered_, num_10ms_frames_per_packet_);
  num_10ms_frames_buffered_ = 0;
  size_t encoded_bytes =
      encoded->AppendData(
          RequiredOutputSizeBytes(),
          [&] (rtc::ArrayView<uint8_t> encoded) {
            const int r = WebRtcIlbcfix_Encode(
                encoder_,
                input_buffer_,
                kSampleRateHz / 100 * num_10ms_frames_per_packet_,
                encoded.data());
            RTC_CHECK_GE(r, 0);

            return static_cast<size_t>(r);
          });

  RTC_DCHECK_EQ(encoded_bytes, RequiredOutputSizeBytes());

  EncodedInfo info;
  info.encoded_bytes = encoded_bytes;
  info.encoded_timestamp = first_timestamp_in_buffer_;
  info.payload_type = config_.payload_type;
  info.encoder_type = CodecType::kIlbc;
  return info;
}

void AudioEncoderIlbc::Reset() {
  if (encoder_)
    RTC_CHECK_EQ(0, WebRtcIlbcfix_EncoderFree(encoder_));
  RTC_CHECK(config_.IsOk());
  RTC_CHECK_EQ(0, WebRtcIlbcfix_EncoderCreate(&encoder_));
  const int encoder_frame_size_ms = config_.frame_size_ms > 30
                                        ? config_.frame_size_ms / 2
                                        : config_.frame_size_ms;
  RTC_CHECK_EQ(0, WebRtcIlbcfix_EncoderInit(encoder_, encoder_frame_size_ms));
  num_10ms_frames_buffered_ = 0;
}

size_t AudioEncoderIlbc::RequiredOutputSizeBytes() const {
  switch (num_10ms_frames_per_packet_) {
    case 2:   return 38;
    case 3:   return 50;
    case 4:   return 2 * 38;
    case 6:   return 2 * 50;
    default:  FATAL();
  }
}

}  // namespace webrtc
