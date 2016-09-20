/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_RTC_EVENT_LOG_SOURCE_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_RTC_EVENT_LOG_SOURCE_H_

#include <memory>
#include <string>

#include "webrtc/base/constructormagic.h"
#include "webrtc/call/rtc_event_log_parser.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet_source.h"
#include "webrtc/modules/rtp_rtcp/include/rtp_rtcp_defines.h"

namespace webrtc {

class RtpHeaderParser;

namespace test {

class Packet;

class RtcEventLogSource : public PacketSource {
 public:
  // Creates an RtcEventLogSource reading from |file_name|. If the file cannot
  // be opened, or has the wrong format, NULL will be returned.
  static RtcEventLogSource* Create(const std::string& file_name);

  virtual ~RtcEventLogSource();

  // Registers an RTP header extension and binds it to |id|.
  virtual bool RegisterRtpHeaderExtension(RTPExtensionType type, uint8_t id);

  std::unique_ptr<Packet> NextPacket() override;

  // Returns the timestamp of the next audio output event, in milliseconds. The
  // maximum value of int64_t is returned if there are no more audio output
  // events available.
  int64_t NextAudioOutputEventMs();

 private:
  RtcEventLogSource();

  bool OpenFile(const std::string& file_name);

  size_t rtp_packet_index_ = 0;
  size_t audio_output_index_ = 0;

  ParsedRtcEventLog parsed_stream_;
  std::unique_ptr<RtpHeaderParser> parser_;

  RTC_DISALLOW_COPY_AND_ASSIGN(RtcEventLogSource);
};

}  // namespace test
}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_RTC_EVENT_LOG_SOURCE_H_
