/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/acm2/acm_send_test_oldapi.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_coding/codecs/audio_encoder.h"
#include "webrtc/modules/audio_coding/include/audio_coding_module.h"
#include "webrtc/modules/audio_coding/neteq/tools/input_audio_file.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet.h"

namespace webrtc {
namespace test {

AcmSendTestOldApi::AcmSendTestOldApi(InputAudioFile* audio_source,
                                     int source_rate_hz,
                                     int test_duration_ms)
    : clock_(0),
      acm_(webrtc::AudioCodingModule::Create(0, &clock_)),
      audio_source_(audio_source),
      source_rate_hz_(source_rate_hz),
      input_block_size_samples_(
          static_cast<size_t>(source_rate_hz_ * kBlockSizeMs / 1000)),
      codec_registered_(false),
      test_duration_ms_(test_duration_ms),
      frame_type_(kAudioFrameSpeech),
      payload_type_(0),
      timestamp_(0),
      sequence_number_(0) {
  input_frame_.sample_rate_hz_ = source_rate_hz_;
  input_frame_.num_channels_ = 1;
  input_frame_.samples_per_channel_ = input_block_size_samples_;
  assert(input_block_size_samples_ * input_frame_.num_channels_ <=
         AudioFrame::kMaxDataSizeSamples);
  acm_->RegisterTransportCallback(this);
}

bool AcmSendTestOldApi::RegisterCodec(const char* payload_name,
                                      int sampling_freq_hz,
                                      int channels,
                                      int payload_type,
                                      int frame_size_samples) {
  CodecInst codec;
  RTC_CHECK_EQ(0, AudioCodingModule::Codec(payload_name, &codec,
                                           sampling_freq_hz, channels));
  codec.pltype = payload_type;
  codec.pacsize = frame_size_samples;
  codec_registered_ = (acm_->RegisterSendCodec(codec) == 0);
  input_frame_.num_channels_ = channels;
  assert(input_block_size_samples_ * input_frame_.num_channels_ <=
         AudioFrame::kMaxDataSizeSamples);
  return codec_registered_;
}

bool AcmSendTestOldApi::RegisterExternalCodec(
    AudioEncoder* external_speech_encoder) {
  acm_->RegisterExternalSendCodec(external_speech_encoder);
  input_frame_.num_channels_ = external_speech_encoder->NumChannels();
  assert(input_block_size_samples_ * input_frame_.num_channels_ <=
         AudioFrame::kMaxDataSizeSamples);
  return codec_registered_ = true;
}

std::unique_ptr<Packet> AcmSendTestOldApi::NextPacket() {
  assert(codec_registered_);
  if (filter_.test(static_cast<size_t>(payload_type_))) {
    // This payload type should be filtered out. Since the payload type is the
    // same throughout the whole test run, no packet at all will be delivered.
    // We can just as well signal that the test is over by returning NULL.
    return nullptr;
  }
  // Insert audio and process until one packet is produced.
  while (clock_.TimeInMilliseconds() < test_duration_ms_) {
    clock_.AdvanceTimeMilliseconds(kBlockSizeMs);
    RTC_CHECK(
        audio_source_->Read(input_block_size_samples_, input_frame_.data_));
    if (input_frame_.num_channels_ > 1) {
      InputAudioFile::DuplicateInterleaved(input_frame_.data_,
                                           input_block_size_samples_,
                                           input_frame_.num_channels_,
                                           input_frame_.data_);
    }
    data_to_send_ = false;
    RTC_CHECK_GE(acm_->Add10MsData(input_frame_), 0);
    input_frame_.timestamp_ += static_cast<uint32_t>(input_block_size_samples_);
    if (data_to_send_) {
      // Encoded packet received.
      return CreatePacket();
    }
  }
  // Test ended.
  return nullptr;
}

// This method receives the callback from ACM when a new packet is produced.
int32_t AcmSendTestOldApi::SendData(
    FrameType frame_type,
    uint8_t payload_type,
    uint32_t timestamp,
    const uint8_t* payload_data,
    size_t payload_len_bytes,
    const RTPFragmentationHeader* fragmentation) {
  // Store the packet locally.
  frame_type_ = frame_type;
  payload_type_ = payload_type;
  timestamp_ = timestamp;
  last_payload_vec_.assign(payload_data, payload_data + payload_len_bytes);
  assert(last_payload_vec_.size() == payload_len_bytes);
  data_to_send_ = true;
  return 0;
}

std::unique_ptr<Packet> AcmSendTestOldApi::CreatePacket() {
  const size_t kRtpHeaderSize = 12;
  size_t allocated_bytes = last_payload_vec_.size() + kRtpHeaderSize;
  uint8_t* packet_memory = new uint8_t[allocated_bytes];
  // Populate the header bytes.
  packet_memory[0] = 0x80;
  packet_memory[1] = static_cast<uint8_t>(payload_type_);
  packet_memory[2] = (sequence_number_ >> 8) & 0xFF;
  packet_memory[3] = (sequence_number_) & 0xFF;
  packet_memory[4] = (timestamp_ >> 24) & 0xFF;
  packet_memory[5] = (timestamp_ >> 16) & 0xFF;
  packet_memory[6] = (timestamp_ >> 8) & 0xFF;
  packet_memory[7] = timestamp_ & 0xFF;
  // Set SSRC to 0x12345678.
  packet_memory[8] = 0x12;
  packet_memory[9] = 0x34;
  packet_memory[10] = 0x56;
  packet_memory[11] = 0x78;

  ++sequence_number_;

  // Copy the payload data.
  memcpy(packet_memory + kRtpHeaderSize,
         &last_payload_vec_[0],
         last_payload_vec_.size());
  std::unique_ptr<Packet> packet(
      new Packet(packet_memory, allocated_bytes, clock_.TimeInMilliseconds()));
  RTC_DCHECK(packet);
  RTC_DCHECK(packet->valid_header());
  return packet;
}

}  // namespace test
}  // namespace webrtc
