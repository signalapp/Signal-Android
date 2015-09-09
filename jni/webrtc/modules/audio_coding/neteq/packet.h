/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_PACKET_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_PACKET_H_

#include <list>

#include "webrtc/modules/interface/module_common_types.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Struct for holding RTP packets.
struct Packet {
  RTPHeader header;
  uint8_t* payload;  // Datagram excluding RTP header and header extension.
  int payload_length;
  bool primary;  // Primary, i.e., not redundant payload.
  int waiting_time;
  bool sync_packet;

  // Constructor.
  Packet()
      : payload(NULL),
        payload_length(0),
        primary(true),
        waiting_time(0),
        sync_packet(false) {
  }

  // Comparison operators. Establish a packet ordering based on (1) timestamp,
  // (2) sequence number, (3) regular packet vs sync-packet and (4) redundancy.
  // Timestamp and sequence numbers are compared taking wrap-around into
  // account. If both timestamp and sequence numbers are identical and one of
  // the packets is sync-packet, the regular packet is considered earlier. For
  // two regular packets with the same sequence number and timestamp a primary
  // payload is considered "smaller" than a secondary.
  bool operator==(const Packet& rhs) const {
    return (this->header.timestamp == rhs.header.timestamp &&
        this->header.sequenceNumber == rhs.header.sequenceNumber &&
        this->primary == rhs.primary &&
        this->sync_packet == rhs.sync_packet);
  }
  bool operator!=(const Packet& rhs) const { return !operator==(rhs); }
  bool operator<(const Packet& rhs) const {
    if (this->header.timestamp == rhs.header.timestamp) {
      if (this->header.sequenceNumber == rhs.header.sequenceNumber) {
        // Timestamp and sequence numbers are identical. A sync packet should
        // be recognized "larger" (i.e. "later") compared to a "network packet"
        // (regular packet from network not sync-packet). If none of the packets
        // are sync-packets, then deem the left hand side to be "smaller"
        // (i.e., "earlier") if it is  primary, and right hand side is not.
        //
        // The condition on sync packets to be larger than "network packets,"
        // given same RTP sequence number and timestamp, guarantees that a
        // "network packet" to be inserted in an earlier position into
        // |packet_buffer_| compared to a sync packet of same timestamp and
        // sequence number.
        if (rhs.sync_packet)
          return true;
        if (this->sync_packet)
          return false;
        return (this->primary && !rhs.primary);
      }
      return (static_cast<uint16_t>(rhs.header.sequenceNumber
          - this->header.sequenceNumber) < 0xFFFF / 2);
    }
    return (static_cast<uint32_t>(rhs.header.timestamp
        - this->header.timestamp) < 0xFFFFFFFF / 2);
  }
  bool operator>(const Packet& rhs) const { return rhs.operator<(*this); }
  bool operator<=(const Packet& rhs) const { return !operator>(rhs); }
  bool operator>=(const Packet& rhs) const { return !operator<(rhs); }
};

// A list of packets.
typedef std::list<Packet*> PacketList;

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_PACKET_H_
