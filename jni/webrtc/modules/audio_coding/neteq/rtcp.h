/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_RTCP_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_RTCP_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/interface/neteq.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Forward declaration.
struct RTPHeader;

class Rtcp {
 public:
  Rtcp() {
    Init(0);
  }

  ~Rtcp() {}

  // Resets the RTCP statistics, and sets the first received sequence number.
  void Init(uint16_t start_sequence_number);

  // Updates the RTCP statistics with a new received packet.
  void Update(const RTPHeader& rtp_header, uint32_t receive_timestamp);

  // Returns the current RTCP statistics. If |no_reset| is true, the statistics
  // are not reset, otherwise they are.
  void GetStatistics(bool no_reset, RtcpStatistics* stats);

 private:
  uint16_t cycles_;  // The number of wrap-arounds for the sequence number.
  uint16_t max_seq_no_;  // The maximum sequence number received. Starts over
                         // from 0 after wrap-around.
  uint16_t base_seq_no_;  // The sequence number of the first received packet.
  uint32_t received_packets_;  // The number of packets that have been received.
  uint32_t received_packets_prior_;  // Number of packets received when last
                                     // report was generated.
  uint32_t expected_prior_;  // Expected number of packets, at the time of the
                             // last report.
  uint32_t jitter_;  // Current jitter value.
  int32_t transit_;  // Clock difference for previous packet.

  DISALLOW_COPY_AND_ASSIGN(Rtcp);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_RTCP_H_
