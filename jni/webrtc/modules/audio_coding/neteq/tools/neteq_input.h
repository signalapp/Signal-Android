/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_INPUT_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_INPUT_H_

#include <algorithm>
#include <memory>

#include "webrtc/base/buffer.h"
#include "webrtc/base/optional.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet_source.h"
#include "webrtc/modules/include/module_common_types.h"

namespace webrtc {
namespace test {

// Interface class for input to the NetEqTest class.
class NetEqInput {
 public:
  struct PacketData {
    WebRtcRTPHeader header;
    rtc::Buffer payload;
    double time_ms;
  };

  virtual ~NetEqInput() = default;

  // Returns at what time (in ms) NetEq::InsertPacket should be called next, or
  // empty if the source is out of packets.
  virtual rtc::Optional<int64_t> NextPacketTime() const = 0;

  // Returns at what time (in ms) NetEq::GetAudio should be called next, or
  // empty if no more output events are available.
  virtual rtc::Optional<int64_t> NextOutputEventTime() const = 0;

  // Returns the time (in ms) for the next event from either NextPacketTime()
  // or NextOutputEventTime(), or empty if both are out of events.
  rtc::Optional<int64_t> NextEventTime() const {
    const auto a = NextPacketTime();
    const auto b = NextOutputEventTime();
    // Return the minimum of non-empty |a| and |b|, or empty if both are empty.
    if (a) {
      return b ? rtc::Optional<int64_t>(std::min(*a, *b)) : a;
    }
    return b ? b : rtc::Optional<int64_t>();
  }

  // Returns the next packet to be inserted into NetEq. The packet following the
  // returned one is pre-fetched in the NetEqInput object, such that future
  // calls to NextPacketTime() or NextHeader() will return information from that
  // packet.
  virtual std::unique_ptr<PacketData> PopPacket() = 0;

  // Move to the next output event. This will make NextOutputEventTime() return
  // a new value (potentially the same if several output events share the same
  // time).
  virtual void AdvanceOutputEvent() = 0;

  // Returns true if the source has come to an end.
  virtual bool ended() const = 0;

  // Returns the RTP header for the next packet, i.e., the packet that will be
  // delivered next by PopPacket().
  virtual rtc::Optional<RTPHeader> NextHeader() const = 0;
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_INPUT_H_
