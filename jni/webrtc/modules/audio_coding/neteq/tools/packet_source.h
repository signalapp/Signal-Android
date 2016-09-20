/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_PACKET_SOURCE_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_PACKET_SOURCE_H_

#include <bitset>
#include <memory>

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet.h"
#include "webrtc/typedefs.h"

namespace webrtc {
namespace test {

// Interface class for an object delivering RTP packets to test applications.
class PacketSource {
 public:
  PacketSource() : use_ssrc_filter_(false), ssrc_(0) {}
  virtual ~PacketSource() {}

  // Returns next packet. Returns nullptr if the source is depleted, or if an
  // error occurred.
  virtual std::unique_ptr<Packet> NextPacket() = 0;

  virtual void FilterOutPayloadType(uint8_t payload_type) {
    filter_.set(payload_type, true);
  }

  virtual void SelectSsrc(uint32_t ssrc) {
    use_ssrc_filter_ = true;
    ssrc_ = ssrc;
  }

 protected:
  std::bitset<128> filter_;  // Payload type is 7 bits in the RFC.
  // If SSRC filtering discards all packet that do not match the SSRC.
  bool use_ssrc_filter_;  // True when SSRC filtering is active.
  uint32_t ssrc_;  // The selected SSRC. All other SSRCs will be discarded.

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(PacketSource);
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_PACKET_SOURCE_H_
