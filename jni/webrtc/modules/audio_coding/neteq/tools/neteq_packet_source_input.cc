/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/tools/neteq_packet_source_input.h"

#include <algorithm>
#include <limits>

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_coding/neteq/tools/rtc_event_log_source.h"
#include "webrtc/modules/audio_coding/neteq/tools/rtp_file_source.h"

namespace webrtc {
namespace test {

NetEqPacketSourceInput::NetEqPacketSourceInput() : next_output_event_ms_(0) {}

rtc::Optional<int64_t> NetEqPacketSourceInput::NextPacketTime() const {
  return packet_
             ? rtc::Optional<int64_t>(static_cast<int64_t>(packet_->time_ms()))
             : rtc::Optional<int64_t>();
}

rtc::Optional<RTPHeader> NetEqPacketSourceInput::NextHeader() const {
  return packet_ ? rtc::Optional<RTPHeader>(packet_->header())
                 : rtc::Optional<RTPHeader>();
}

void NetEqPacketSourceInput::LoadNextPacket() {
  packet_ = source()->NextPacket();
}

std::unique_ptr<NetEqInput::PacketData> NetEqPacketSourceInput::PopPacket() {
  if (!packet_) {
    return std::unique_ptr<PacketData>();
  }
  std::unique_ptr<PacketData> packet_data(new PacketData);
  packet_->ConvertHeader(&packet_data->header);
  packet_data->payload.SetData(packet_->payload(),
                               packet_->payload_length_bytes());
  packet_data->time_ms = packet_->time_ms();

  LoadNextPacket();

  return packet_data;
}

NetEqRtpDumpInput::NetEqRtpDumpInput(const std::string& file_name)
    : source_(RtpFileSource::Create(file_name)) {
  LoadNextPacket();
}

rtc::Optional<int64_t> NetEqRtpDumpInput::NextOutputEventTime() const {
  return next_output_event_ms_;
}

void NetEqRtpDumpInput::AdvanceOutputEvent() {
  if (next_output_event_ms_) {
    *next_output_event_ms_ += kOutputPeriodMs;
  }
  if (!NextPacketTime()) {
    next_output_event_ms_ = rtc::Optional<int64_t>();
  }
}

PacketSource* NetEqRtpDumpInput::source() {
  return source_.get();
}

NetEqEventLogInput::NetEqEventLogInput(const std::string& file_name)
    : source_(RtcEventLogSource::Create(file_name)) {
  LoadNextPacket();
  AdvanceOutputEvent();
}

rtc::Optional<int64_t> NetEqEventLogInput::NextOutputEventTime() const {
  return rtc::Optional<int64_t>(next_output_event_ms_);
}

void NetEqEventLogInput::AdvanceOutputEvent() {
  next_output_event_ms_ =
      rtc::Optional<int64_t>(source_->NextAudioOutputEventMs());
  if (*next_output_event_ms_ == std::numeric_limits<int64_t>::max()) {
    next_output_event_ms_ = rtc::Optional<int64_t>();
  }
}

PacketSource* NetEqEventLogInput::source() {
  return source_.get();
}

}  // namespace test
}  // namespace webrtc
