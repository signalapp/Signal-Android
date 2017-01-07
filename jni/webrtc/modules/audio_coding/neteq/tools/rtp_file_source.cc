/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/tools/rtp_file_source.h"

#include <assert.h>
#include <string.h>
#ifdef WIN32
#include <winsock2.h>
#else
#include <netinet/in.h>
#endif

#include <memory>

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet.h"
#include "webrtc/modules/rtp_rtcp/include/rtp_header_parser.h"
#include "webrtc/test/rtp_file_reader.h"

namespace webrtc {
namespace test {

RtpFileSource* RtpFileSource::Create(const std::string& file_name) {
  RtpFileSource* source = new RtpFileSource();
  RTC_CHECK(source->OpenFile(file_name));
  return source;
}

bool RtpFileSource::ValidRtpDump(const std::string& file_name) {
  std::unique_ptr<RtpFileReader> temp_file(
      RtpFileReader::Create(RtpFileReader::kRtpDump, file_name));
  return !!temp_file;
}

bool RtpFileSource::ValidPcap(const std::string& file_name) {
  std::unique_ptr<RtpFileReader> temp_file(
      RtpFileReader::Create(RtpFileReader::kPcap, file_name));
  return !!temp_file;
}

RtpFileSource::~RtpFileSource() {
}

bool RtpFileSource::RegisterRtpHeaderExtension(RTPExtensionType type,
                                               uint8_t id) {
  assert(parser_.get());
  return parser_->RegisterRtpHeaderExtension(type, id);
}

std::unique_ptr<Packet> RtpFileSource::NextPacket() {
  while (true) {
    RtpPacket temp_packet;
    if (!rtp_reader_->NextPacket(&temp_packet)) {
      return NULL;
    }
    if (temp_packet.original_length == 0) {
      // May be an RTCP packet.
      // Read the next one.
      continue;
    }
    std::unique_ptr<uint8_t[]> packet_memory(new uint8_t[temp_packet.length]);
    memcpy(packet_memory.get(), temp_packet.data, temp_packet.length);
    std::unique_ptr<Packet> packet(new Packet(
        packet_memory.release(), temp_packet.length,
        temp_packet.original_length, temp_packet.time_ms, *parser_.get()));
    if (!packet->valid_header()) {
      assert(false);
      return NULL;
    }
    if (filter_.test(packet->header().payloadType) ||
        (use_ssrc_filter_ && packet->header().ssrc != ssrc_)) {
      // This payload type should be filtered out. Continue to the next packet.
      continue;
    }
    return packet;
  }
}

RtpFileSource::RtpFileSource()
    : PacketSource(),
      parser_(RtpHeaderParser::Create()) {}

bool RtpFileSource::OpenFile(const std::string& file_name) {
  rtp_reader_.reset(RtpFileReader::Create(RtpFileReader::kRtpDump, file_name));
  if (rtp_reader_)
    return true;
  rtp_reader_.reset(RtpFileReader::Create(RtpFileReader::kPcap, file_name));
  if (!rtp_reader_) {
    FATAL() << "Couldn't open input file as either a rtpdump or .pcap. Note "
               "that .pcapng is not supported.";
  }
  return true;
}

}  // namespace test
}  // namespace webrtc
