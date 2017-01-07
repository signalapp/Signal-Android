/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/tools/rtc_event_log_source.h"

#include <assert.h>
#include <string.h>
#include <iostream>
#include <limits>

#include "webrtc/base/checks.h"
#include "webrtc/call.h"
#include "webrtc/call/rtc_event_log.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet.h"
#include "webrtc/modules/rtp_rtcp/include/rtp_header_parser.h"


namespace webrtc {
namespace test {

RtcEventLogSource* RtcEventLogSource::Create(const std::string& file_name) {
  RtcEventLogSource* source = new RtcEventLogSource();
  RTC_CHECK(source->OpenFile(file_name));
  return source;
}

RtcEventLogSource::~RtcEventLogSource() {}

bool RtcEventLogSource::RegisterRtpHeaderExtension(RTPExtensionType type,
                                                   uint8_t id) {
  RTC_CHECK(parser_.get());
  return parser_->RegisterRtpHeaderExtension(type, id);
}

std::unique_ptr<Packet> RtcEventLogSource::NextPacket() {
  while (rtp_packet_index_ < parsed_stream_.GetNumberOfEvents()) {
    if (parsed_stream_.GetEventType(rtp_packet_index_) ==
        ParsedRtcEventLog::RTP_EVENT) {
      PacketDirection direction;
      MediaType media_type;
      size_t header_length;
      size_t packet_length;
      uint64_t timestamp_us = parsed_stream_.GetTimestamp(rtp_packet_index_);
      parsed_stream_.GetRtpHeader(rtp_packet_index_, &direction, &media_type,
                                  nullptr, &header_length, &packet_length);
      if (direction == kIncomingPacket && media_type == MediaType::AUDIO) {
        uint8_t* packet_header = new uint8_t[header_length];
        parsed_stream_.GetRtpHeader(rtp_packet_index_, nullptr, nullptr,
                                    packet_header, nullptr, nullptr);
        std::unique_ptr<Packet> packet(new Packet(
            packet_header, header_length, packet_length,
            static_cast<double>(timestamp_us) / 1000, *parser_.get()));
        if (packet->valid_header()) {
          // Check if the packet should not be filtered out.
          if (!filter_.test(packet->header().payloadType) &&
              !(use_ssrc_filter_ && packet->header().ssrc != ssrc_)) {
            rtp_packet_index_++;
            return packet;
          }
        } else {
          std::cout << "Warning: Packet with index " << rtp_packet_index_
                    << " has an invalid header and will be ignored."
                    << std::endl;
        }
      }
    }
    rtp_packet_index_++;
  }
  return nullptr;
}

int64_t RtcEventLogSource::NextAudioOutputEventMs() {
  while (audio_output_index_ < parsed_stream_.GetNumberOfEvents()) {
    if (parsed_stream_.GetEventType(audio_output_index_) ==
        ParsedRtcEventLog::AUDIO_PLAYOUT_EVENT) {
      uint64_t timestamp_us = parsed_stream_.GetTimestamp(audio_output_index_);
      // We call GetAudioPlayout only to check that the protobuf event is
      // well-formed.
      parsed_stream_.GetAudioPlayout(audio_output_index_, nullptr);
      audio_output_index_++;
      return timestamp_us / 1000;
    }
    audio_output_index_++;
  }
  return std::numeric_limits<int64_t>::max();
}

RtcEventLogSource::RtcEventLogSource()
    : PacketSource(), parser_(RtpHeaderParser::Create()) {}

bool RtcEventLogSource::OpenFile(const std::string& file_name) {
  return parsed_stream_.ParseFile(file_name);
}

}  // namespace test
}  // namespace webrtc
