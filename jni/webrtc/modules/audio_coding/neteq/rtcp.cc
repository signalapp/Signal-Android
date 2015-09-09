/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/rtcp.h"

#include <string.h>

#include <algorithm>

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/interface/module_common_types.h"

namespace webrtc {

void Rtcp::Init(uint16_t start_sequence_number) {
  cycles_ = 0;
  max_seq_no_ = start_sequence_number;
  base_seq_no_ = start_sequence_number;
  received_packets_ = 0;
  received_packets_prior_ = 0;
  expected_prior_ = 0;
  jitter_ = 0;
  transit_ = 0;
}

void Rtcp::Update(const RTPHeader& rtp_header, uint32_t receive_timestamp) {
  // Update number of received packets, and largest packet number received.
  received_packets_++;
  int16_t sn_diff = rtp_header.sequenceNumber - max_seq_no_;
  if (sn_diff >= 0) {
    if (rtp_header.sequenceNumber < max_seq_no_) {
      // Wrap-around detected.
      cycles_++;
    }
    max_seq_no_ = rtp_header.sequenceNumber;
  }

  // Calculate jitter according to RFC 3550, and update previous timestamps.
  // Note that the value in |jitter_| is in Q4.
  if (received_packets_ > 1) {
    int32_t ts_diff = receive_timestamp - (rtp_header.timestamp - transit_);
    ts_diff = WEBRTC_SPL_ABS_W32(ts_diff);
    int32_t jitter_diff = (ts_diff << 4) - jitter_;
    // Calculate 15 * jitter_ / 16 + jitter_diff / 16 (with proper rounding).
    jitter_ = jitter_ + ((jitter_diff + 8) >> 4);
  }
  transit_ = rtp_header.timestamp - receive_timestamp;
}

void Rtcp::GetStatistics(bool no_reset, RtcpStatistics* stats) {
  // Extended highest sequence number received.
  stats->extended_max_sequence_number =
      (static_cast<int>(cycles_) << 16) + max_seq_no_;

  // Calculate expected number of packets and compare it with the number of
  // packets that were actually received. The cumulative number of lost packets
  // can be extracted.
  uint32_t expected_packets =
      stats->extended_max_sequence_number - base_seq_no_ + 1;
  if (received_packets_ == 0) {
    // No packets received, assume none lost.
    stats->cumulative_lost = 0;
  } else if (expected_packets > received_packets_) {
    stats->cumulative_lost = expected_packets - received_packets_;
    if (stats->cumulative_lost > 0xFFFFFF) {
      stats->cumulative_lost = 0xFFFFFF;
    }
  } else {
    stats->cumulative_lost = 0;
  }

  // Fraction lost since last report.
  uint32_t expected_since_last = expected_packets - expected_prior_;
  uint32_t received_since_last = received_packets_ - received_packets_prior_;
  if (!no_reset) {
    expected_prior_ = expected_packets;
    received_packets_prior_ = received_packets_;
  }
  int32_t lost = expected_since_last - received_since_last;
  if (expected_since_last == 0 || lost <= 0 || received_packets_ == 0) {
    stats->fraction_lost = 0;
  } else {
    stats->fraction_lost = std::min(0xFFU, (lost << 8) / expected_since_last);
  }

  stats->jitter = jitter_ >> 4;  // Scaling from Q4.
}

}  // namespace webrtc
