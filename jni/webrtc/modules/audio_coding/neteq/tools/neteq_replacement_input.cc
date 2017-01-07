/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/tools/neteq_replacement_input.h"

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_coding/neteq/tools/fake_decode_from_file.h"

namespace webrtc {
namespace test {

NetEqReplacementInput::NetEqReplacementInput(
    std::unique_ptr<NetEqInput> source,
    uint8_t replacement_payload_type,
    const std::set<uint8_t>& comfort_noise_types,
    const std::set<uint8_t>& forbidden_types)
    : source_(std::move(source)),
      replacement_payload_type_(replacement_payload_type),
      comfort_noise_types_(comfort_noise_types),
      forbidden_types_(forbidden_types) {
  RTC_CHECK(source_);
  packet_ = source_->PopPacket();
  ReplacePacket();
  RTC_CHECK(packet_);
}

rtc::Optional<int64_t> NetEqReplacementInput::NextPacketTime() const {
  return packet_
             ? rtc::Optional<int64_t>(static_cast<int64_t>(packet_->time_ms))
             : rtc::Optional<int64_t>();
}

rtc::Optional<int64_t> NetEqReplacementInput::NextOutputEventTime() const {
  return source_->NextOutputEventTime();
}

std::unique_ptr<NetEqInput::PacketData> NetEqReplacementInput::PopPacket() {
  std::unique_ptr<PacketData> to_return = std::move(packet_);
  packet_ = source_->PopPacket();
  ReplacePacket();
  return to_return;
}

void NetEqReplacementInput::AdvanceOutputEvent() {
  source_->AdvanceOutputEvent();
}

bool NetEqReplacementInput::ended() const {
  return source_->ended();
}

rtc::Optional<RTPHeader> NetEqReplacementInput::NextHeader() const {
  return source_->NextHeader();
}

void NetEqReplacementInput::ReplacePacket() {
  if (!source_->NextPacketTime()) {
    // End of input. Cannot do proper replacement on the very last packet, so we
    // delete it instead.
    packet_.reset();
    return;
  }

  RTC_DCHECK(packet_);

  RTC_CHECK_EQ(forbidden_types_.count(packet_->header.header.payloadType), 0u)
      << "Payload type " << static_cast<int>(packet_->header.header.payloadType)
      << " is forbidden.";

  // Check if this packet is comfort noise.
  if (comfort_noise_types_.count(packet_->header.header.payloadType) != 0) {
    // If CNG, simply insert a zero-energy one-byte payload.
    uint8_t cng_payload[1] = {127};  // Max attenuation of CNG.
    packet_->payload.SetData(cng_payload);
    return;
  }

  rtc::Optional<RTPHeader> next_hdr = source_->NextHeader();
  RTC_DCHECK(next_hdr);
  uint8_t payload[8];
  uint32_t input_frame_size_timestamps =
      next_hdr->timestamp - packet_->header.header.timestamp;
  FakeDecodeFromFile::PrepareEncoded(packet_->header.header.timestamp,
                                     input_frame_size_timestamps, payload);
  packet_->payload.SetData(payload);
  packet_->header.header.payloadType = replacement_payload_type_;
  return;
}

}  // namespace test
}  // namespace webrtc
