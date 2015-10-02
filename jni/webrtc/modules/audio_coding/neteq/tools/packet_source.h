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

#include "webrtc/base/constructormagic.h"
#include "webrtc/typedefs.h"

namespace webrtc {
namespace test {

class Packet;

// Interface class for an object delivering RTP packets to test applications.
class PacketSource {
 public:
  PacketSource() {}
  virtual ~PacketSource() {}

  // Returns a pointer to the next packet. Returns NULL if the source is
  // depleted, or if an error occurred.
  virtual Packet* NextPacket() = 0;

  virtual void FilterOutPayloadType(uint8_t payload_type) {
    filter_.set(payload_type, true);
  }

 protected:
  std::bitset<128> filter_;  // Payload type is 7 bits in the RFC.

 private:
  DISALLOW_COPY_AND_ASSIGN(PacketSource);
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_PACKET_SOURCE_H_
