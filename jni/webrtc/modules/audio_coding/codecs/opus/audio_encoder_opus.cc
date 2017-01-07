/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/opus/audio_encoder_opus.h"

#include <algorithm>

#include "webrtc/base/checks.h"
#include "webrtc/base/safe_conversions.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/codecs/opus/opus_interface.h"

namespace webrtc {

namespace {

const int kSampleRateHz = 48000;
const int kMinBitrateBps = 500;
const int kMaxBitrateBps = 512000;

AudioEncoderOpus::Config CreateConfig(const CodecInst& codec_inst) {
  AudioEncoderOpus::Config config;
  config.frame_size_ms = rtc::CheckedDivExact(codec_inst.pacsize, 48);
  config.num_channels = codec_inst.channels;
  config.bitrate_bps = rtc::Optional<int>(codec_inst.rate);
  config.payload_type = codec_inst.pltype;
  config.application = config.num_channels == 1 ? AudioEncoderOpus::kVoip
                                                : AudioEncoderOpus::kAudio;
  return config;
}

// Optimize the loss rate to configure Opus. Basically, optimized loss rate is
// the input loss rate rounded down to various levels, because a robustly good
// audio quality is achieved by lowering the packet loss down.
// Additionally, to prevent toggling, margins are used, i.e., when jumping to
// a loss rate from below, a higher threshold is used than jumping to the same
// level from above.
double OptimizePacketLossRate(double new_loss_rate, double old_loss_rate) {
  RTC_DCHECK_GE(new_loss_rate, 0.0);
  RTC_DCHECK_LE(new_loss_rate, 1.0);
  RTC_DCHECK_GE(old_loss_rate, 0.0);
  RTC_DCHECK_LE(old_loss_rate, 1.0);
  const double kPacketLossRate20 = 0.20;
  const double kPacketLossRate10 = 0.10;
  const double kPacketLossRate5 = 0.05;
  const double kPacketLossRate1 = 0.01;
  const double kLossRate20Margin = 0.02;
  const double kLossRate10Margin = 0.01;
  const double kLossRate5Margin = 0.01;
  if (new_loss_rate >=
      kPacketLossRate20 +
          kLossRate20Margin *
              (kPacketLossRate20 - old_loss_rate > 0 ? 1 : -1)) {
    return kPacketLossRate20;
  } else if (new_loss_rate >=
             kPacketLossRate10 +
                 kLossRate10Margin *
                     (kPacketLossRate10 - old_loss_rate > 0 ? 1 : -1)) {
    return kPacketLossRate10;
  } else if (new_loss_rate >=
             kPacketLossRate5 +
                 kLossRate5Margin *
                     (kPacketLossRate5 - old_loss_rate > 0 ? 1 : -1)) {
    return kPacketLossRate5;
  } else if (new_loss_rate >= kPacketLossRate1) {
    return kPacketLossRate1;
  } else {
    return 0.0;
  }
}

}  // namespace

AudioEncoderOpus::Config::Config() = default;
AudioEncoderOpus::Config::Config(const Config&) = default;
AudioEncoderOpus::Config::~Config() = default;
auto AudioEncoderOpus::Config::operator=(const Config&) -> Config& = default;

bool AudioEncoderOpus::Config::IsOk() const {
  if (frame_size_ms <= 0 || frame_size_ms % 10 != 0)
    return false;
  if (num_channels != 1 && num_channels != 2)
    return false;
  if (bitrate_bps &&
      (*bitrate_bps < kMinBitrateBps || *bitrate_bps > kMaxBitrateBps))
    return false;
  if (complexity < 0 || complexity > 10)
    return false;
  return true;
}

int AudioEncoderOpus::Config::GetBitrateBps() const {
  RTC_DCHECK(IsOk());
  if (bitrate_bps)
    return *bitrate_bps;  // Explicitly set value.
  else
    return num_channels == 1 ? 32000 : 64000;  // Default value.
}

AudioEncoderOpus::AudioEncoderOpus(const Config& config)
    : packet_loss_rate_(0.0), inst_(nullptr) {
  RTC_CHECK(RecreateEncoderInstance(config));
}

AudioEncoderOpus::AudioEncoderOpus(const CodecInst& codec_inst)
    : AudioEncoderOpus(CreateConfig(codec_inst)) {}

AudioEncoderOpus::~AudioEncoderOpus() {
  RTC_CHECK_EQ(0, WebRtcOpus_EncoderFree(inst_));
}

int AudioEncoderOpus::SampleRateHz() const {
  return kSampleRateHz;
}

size_t AudioEncoderOpus::NumChannels() const {
  return config_.num_channels;
}

size_t AudioEncoderOpus::Num10MsFramesInNextPacket() const {
  return Num10msFramesPerPacket();
}

size_t AudioEncoderOpus::Max10MsFramesInAPacket() const {
  return Num10msFramesPerPacket();
}

int AudioEncoderOpus::GetTargetBitrate() const {
  return config_.GetBitrateBps();
}

void AudioEncoderOpus::Reset() {
  RTC_CHECK(RecreateEncoderInstance(config_));
}

bool AudioEncoderOpus::SetFec(bool enable) {
  auto conf = config_;
  conf.fec_enabled = enable;
  return RecreateEncoderInstance(conf);
}

bool AudioEncoderOpus::SetDtx(bool enable) {
  auto conf = config_;
  conf.dtx_enabled = enable;
  return RecreateEncoderInstance(conf);
}

bool AudioEncoderOpus::SetApplication(Application application) {
  auto conf = config_;
  switch (application) {
    case Application::kSpeech:
      conf.application = AudioEncoderOpus::kVoip;
      break;
    case Application::kAudio:
      conf.application = AudioEncoderOpus::kAudio;
      break;
  }
  return RecreateEncoderInstance(conf);
}

void AudioEncoderOpus::SetMaxPlaybackRate(int frequency_hz) {
  auto conf = config_;
  conf.max_playback_rate_hz = frequency_hz;
  RTC_CHECK(RecreateEncoderInstance(conf));
}

void AudioEncoderOpus::SetProjectedPacketLossRate(double fraction) {
  double opt_loss_rate = OptimizePacketLossRate(fraction, packet_loss_rate_);
  if (packet_loss_rate_ != opt_loss_rate) {
    packet_loss_rate_ = opt_loss_rate;
    RTC_CHECK_EQ(
        0, WebRtcOpus_SetPacketLossRate(
               inst_, static_cast<int32_t>(packet_loss_rate_ * 100 + .5)));
  }
}

void AudioEncoderOpus::SetTargetBitrate(int bits_per_second) {
  config_.bitrate_bps = rtc::Optional<int>(
      std::max(std::min(bits_per_second, kMaxBitrateBps), kMinBitrateBps));
  RTC_DCHECK(config_.IsOk());
  RTC_CHECK_EQ(0, WebRtcOpus_SetBitRate(inst_, config_.GetBitrateBps()));
}

AudioEncoder::EncodedInfo AudioEncoderOpus::EncodeImpl(
    uint32_t rtp_timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {

  if (input_buffer_.empty())
    first_timestamp_in_buffer_ = rtp_timestamp;

  input_buffer_.insert(input_buffer_.end(), audio.cbegin(), audio.cend());
  if (input_buffer_.size() <
      (Num10msFramesPerPacket() * SamplesPer10msFrame())) {
    return EncodedInfo();
  }
  RTC_CHECK_EQ(input_buffer_.size(),
               Num10msFramesPerPacket() * SamplesPer10msFrame());

  const size_t max_encoded_bytes = SufficientOutputBufferSize();
  EncodedInfo info;
  info.encoded_bytes =
      encoded->AppendData(
          max_encoded_bytes, [&] (rtc::ArrayView<uint8_t> encoded) {
            int status = WebRtcOpus_Encode(
                inst_, &input_buffer_[0],
                rtc::CheckedDivExact(input_buffer_.size(),
                                     config_.num_channels),
                rtc::saturated_cast<int16_t>(max_encoded_bytes),
                encoded.data());

            RTC_CHECK_GE(status, 0);  // Fails only if fed invalid data.

            return static_cast<size_t>(status);
          });
  input_buffer_.clear();

  info.encoded_timestamp = first_timestamp_in_buffer_;
  info.payload_type = config_.payload_type;
  info.send_even_if_empty = true;  // Allows Opus to send empty packets.
  info.speech = (info.encoded_bytes > 0);
  info.encoder_type = CodecType::kOpus;
  return info;
}

size_t AudioEncoderOpus::Num10msFramesPerPacket() const {
  return static_cast<size_t>(rtc::CheckedDivExact(config_.frame_size_ms, 10));
}

size_t AudioEncoderOpus::SamplesPer10msFrame() const {
  return rtc::CheckedDivExact(kSampleRateHz, 100) * config_.num_channels;
}

size_t AudioEncoderOpus::SufficientOutputBufferSize() const {
  // Calculate the number of bytes we expect the encoder to produce,
  // then multiply by two to give a wide margin for error.
  const size_t bytes_per_millisecond =
      static_cast<size_t>(config_.GetBitrateBps() / (1000 * 8) + 1);
  const size_t approx_encoded_bytes =
      Num10msFramesPerPacket() * 10 * bytes_per_millisecond;
  return 2 * approx_encoded_bytes;
}

// If the given config is OK, recreate the Opus encoder instance with those
// settings, save the config, and return true. Otherwise, do nothing and return
// false.
bool AudioEncoderOpus::RecreateEncoderInstance(const Config& config) {
  if (!config.IsOk())
    return false;
  if (inst_)
    RTC_CHECK_EQ(0, WebRtcOpus_EncoderFree(inst_));
  input_buffer_.clear();
  input_buffer_.reserve(Num10msFramesPerPacket() * SamplesPer10msFrame());
  RTC_CHECK_EQ(0, WebRtcOpus_EncoderCreate(&inst_, config.num_channels,
                                           config.application));
  RTC_CHECK_EQ(0, WebRtcOpus_SetBitRate(inst_, config.GetBitrateBps()));
  if (config.fec_enabled) {
    RTC_CHECK_EQ(0, WebRtcOpus_EnableFec(inst_));
  } else {
    RTC_CHECK_EQ(0, WebRtcOpus_DisableFec(inst_));
  }
  RTC_CHECK_EQ(
      0, WebRtcOpus_SetMaxPlaybackRate(inst_, config.max_playback_rate_hz));
  RTC_CHECK_EQ(0, WebRtcOpus_SetComplexity(inst_, config.complexity));
  if (config.dtx_enabled) {
    RTC_CHECK_EQ(0, WebRtcOpus_EnableDtx(inst_));
  } else {
    RTC_CHECK_EQ(0, WebRtcOpus_DisableDtx(inst_));
  }
  RTC_CHECK_EQ(0,
               WebRtcOpus_SetPacketLossRate(
                   inst_, static_cast<int32_t>(packet_loss_rate_ * 100 + .5)));
  config_ = config;
  return true;
}

}  // namespace webrtc
