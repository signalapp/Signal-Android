/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/tools/neteq_test.h"

#include <iostream>

#include "webrtc/modules/audio_coding/codecs/builtin_audio_decoder_factory.h"

namespace webrtc {
namespace test {

void DefaultNetEqTestErrorCallback::OnInsertPacketError(
    int error_code,
    const NetEqInput::PacketData& packet) {
  if (error_code == NetEq::kUnknownRtpPayloadType) {
    std::cerr << "RTP Payload type "
              << static_cast<int>(packet.header.header.payloadType)
              << " is unknown." << std::endl;
  } else {
    std::cerr << "InsertPacket returned error code " << error_code << std::endl;
    std::cerr << "Header data:" << std::endl;
    std::cerr << "  PT = " << static_cast<int>(packet.header.header.payloadType)
              << std::endl;
    std::cerr << "  SN = " << packet.header.header.sequenceNumber << std::endl;
    std::cerr << "  TS = " << packet.header.header.timestamp << std::endl;
  }
  FATAL();
}

void DefaultNetEqTestErrorCallback::OnGetAudioError(int error_code) {
  std::cerr << "GetAudio returned error code " << error_code << std::endl;
  FATAL();
}

NetEqTest::NetEqTest(const NetEq::Config& config,
                     const DecoderMap& codecs,
                     const ExtDecoderMap& ext_codecs,
                     std::unique_ptr<NetEqInput> input,
                     std::unique_ptr<AudioSink> output,
                     NetEqTestErrorCallback* error_callback)
    : neteq_(NetEq::Create(config, CreateBuiltinAudioDecoderFactory())),
      input_(std::move(input)),
      output_(std::move(output)),
      error_callback_(error_callback),
      sample_rate_hz_(config.sample_rate_hz) {
  RTC_CHECK(!config.enable_muted_state)
      << "The code does not handle enable_muted_state";
  RegisterDecoders(codecs);
  RegisterExternalDecoders(ext_codecs);
}

int64_t NetEqTest::Run() {
  const int64_t start_time_ms = *input_->NextEventTime();
  int64_t time_now_ms = start_time_ms;

  while (!input_->ended()) {
    // Advance time to next event.
    RTC_DCHECK(input_->NextEventTime());
    time_now_ms = *input_->NextEventTime();
    // Check if it is time to insert packet.
    if (input_->NextPacketTime() && time_now_ms >= *input_->NextPacketTime()) {
      std::unique_ptr<NetEqInput::PacketData> packet_data = input_->PopPacket();
      RTC_CHECK(packet_data);
      int error = neteq_->InsertPacket(
          packet_data->header,
          rtc::ArrayView<const uint8_t>(packet_data->payload),
          static_cast<uint32_t>(packet_data->time_ms * sample_rate_hz_ / 1000));
      if (error != NetEq::kOK && error_callback_) {
        error_callback_->OnInsertPacketError(neteq_->LastError(), *packet_data);
      }
    }

    // Check if it is time to get output audio.
    if (input_->NextOutputEventTime() &&
        time_now_ms >= *input_->NextOutputEventTime()) {
      AudioFrame out_frame;
      bool muted;
      int error = neteq_->GetAudio(&out_frame, &muted);
      RTC_CHECK(!muted) << "The code does not handle enable_muted_state";
      if (error != NetEq::kOK) {
        if (error_callback_) {
          error_callback_->OnGetAudioError(neteq_->LastError());
        }
      } else {
        sample_rate_hz_ = out_frame.sample_rate_hz_;
      }

      if (output_) {
        RTC_CHECK(output_->WriteArray(
            out_frame.data_,
            out_frame.samples_per_channel_ * out_frame.num_channels_));
      }

      input_->AdvanceOutputEvent();
    }
  }
  return time_now_ms - start_time_ms;
}

NetEqNetworkStatistics NetEqTest::SimulationStats() {
  NetEqNetworkStatistics stats;
  RTC_CHECK_EQ(neteq_->NetworkStatistics(&stats), 0);
  return stats;
}

void NetEqTest::RegisterDecoders(const DecoderMap& codecs) {
  for (const auto& c : codecs) {
    RTC_CHECK_EQ(
        neteq_->RegisterPayloadType(c.second.first, c.second.second, c.first),
        NetEq::kOK)
        << "Cannot register " << c.second.second << " to payload type "
        << c.first;
  }
}

void NetEqTest::RegisterExternalDecoders(const ExtDecoderMap& codecs) {
  for (const auto& c : codecs) {
    RTC_CHECK_EQ(
        neteq_->RegisterExternalDecoder(c.second.decoder, c.second.codec,
                                        c.second.codec_name, c.first),
        NetEq::kOK)
        << "Cannot register " << c.second.codec_name << " to payload type "
        << c.first;
  }
}

}  // namespace test
}  // namespace webrtc
