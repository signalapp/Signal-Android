/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/nack.h"

#include <assert.h>  // For assert.

#include <algorithm>  // For std::max.

#include "webrtc/modules/interface/module_common_types.h"
#include "webrtc/system_wrappers/interface/logging.h"

namespace webrtc {

namespace acm2 {

namespace {

const int kDefaultSampleRateKhz = 48;
const int kDefaultPacketSizeMs = 20;

}  // namespace

Nack::Nack(int nack_threshold_packets)
    : nack_threshold_packets_(nack_threshold_packets),
      sequence_num_last_received_rtp_(0),
      timestamp_last_received_rtp_(0),
      any_rtp_received_(false),
      sequence_num_last_decoded_rtp_(0),
      timestamp_last_decoded_rtp_(0),
      any_rtp_decoded_(false),
      sample_rate_khz_(kDefaultSampleRateKhz),
      samples_per_packet_(sample_rate_khz_ * kDefaultPacketSizeMs),
      max_nack_list_size_(kNackListSizeLimit) {}

Nack* Nack::Create(int nack_threshold_packets) {
  return new Nack(nack_threshold_packets);
}

void Nack::UpdateSampleRate(int sample_rate_hz) {
  assert(sample_rate_hz > 0);
  sample_rate_khz_ = sample_rate_hz / 1000;
}

void Nack::UpdateLastReceivedPacket(uint16_t sequence_number,
                                    uint32_t timestamp) {
  // Just record the value of sequence number and timestamp if this is the
  // first packet.
  if (!any_rtp_received_) {
    sequence_num_last_received_rtp_ = sequence_number;
    timestamp_last_received_rtp_ = timestamp;
    any_rtp_received_ = true;
    // If no packet is decoded, to have a reasonable estimate of time-to-play
    // use the given values.
    if (!any_rtp_decoded_) {
      sequence_num_last_decoded_rtp_ = sequence_number;
      timestamp_last_decoded_rtp_ = timestamp;
    }
    return;
  }

  if (sequence_number == sequence_num_last_received_rtp_)
    return;

  // Received RTP should not be in the list.
  nack_list_.erase(sequence_number);

  // If this is an old sequence number, no more action is required, return.
  if (IsNewerSequenceNumber(sequence_num_last_received_rtp_, sequence_number))
    return;

  UpdateSamplesPerPacket(sequence_number, timestamp);

  UpdateList(sequence_number);

  sequence_num_last_received_rtp_ = sequence_number;
  timestamp_last_received_rtp_ = timestamp;
  LimitNackListSize();
}

void Nack::UpdateSamplesPerPacket(uint16_t sequence_number_current_received_rtp,
                                  uint32_t timestamp_current_received_rtp) {
  uint32_t timestamp_increase = timestamp_current_received_rtp -
      timestamp_last_received_rtp_;
  uint16_t sequence_num_increase = sequence_number_current_received_rtp -
      sequence_num_last_received_rtp_;

  samples_per_packet_ = timestamp_increase / sequence_num_increase;
}

void Nack::UpdateList(uint16_t sequence_number_current_received_rtp) {
  // Some of the packets which were considered late, now are considered missing.
  ChangeFromLateToMissing(sequence_number_current_received_rtp);

  if (IsNewerSequenceNumber(sequence_number_current_received_rtp,
                            sequence_num_last_received_rtp_ + 1))
    AddToList(sequence_number_current_received_rtp);
}

void Nack::ChangeFromLateToMissing(
    uint16_t sequence_number_current_received_rtp) {
  NackList::const_iterator lower_bound = nack_list_.lower_bound(
      static_cast<uint16_t>(sequence_number_current_received_rtp -
                            nack_threshold_packets_));

  for (NackList::iterator it = nack_list_.begin(); it != lower_bound; ++it)
    it->second.is_missing = true;
}

uint32_t Nack::EstimateTimestamp(uint16_t sequence_num) {
  uint16_t sequence_num_diff = sequence_num - sequence_num_last_received_rtp_;
  return sequence_num_diff * samples_per_packet_ + timestamp_last_received_rtp_;
}

void Nack::AddToList(uint16_t sequence_number_current_received_rtp) {
  assert(!any_rtp_decoded_ || IsNewerSequenceNumber(
      sequence_number_current_received_rtp, sequence_num_last_decoded_rtp_));

  // Packets with sequence numbers older than |upper_bound_missing| are
  // considered missing, and the rest are considered late.
  uint16_t upper_bound_missing = sequence_number_current_received_rtp -
      nack_threshold_packets_;

  for (uint16_t n = sequence_num_last_received_rtp_ + 1;
      IsNewerSequenceNumber(sequence_number_current_received_rtp, n); ++n) {
    bool is_missing = IsNewerSequenceNumber(upper_bound_missing, n);
    uint32_t timestamp = EstimateTimestamp(n);
    NackElement nack_element(TimeToPlay(timestamp), timestamp, is_missing);
    nack_list_.insert(nack_list_.end(), std::make_pair(n, nack_element));
  }
}

void Nack::UpdateEstimatedPlayoutTimeBy10ms() {
  while (!nack_list_.empty() &&
      nack_list_.begin()->second.time_to_play_ms <= 10)
    nack_list_.erase(nack_list_.begin());

  for (NackList::iterator it = nack_list_.begin(); it != nack_list_.end(); ++it)
    it->second.time_to_play_ms -= 10;
}

void Nack::UpdateLastDecodedPacket(uint16_t sequence_number,
                                   uint32_t timestamp) {
  if (IsNewerSequenceNumber(sequence_number, sequence_num_last_decoded_rtp_) ||
      !any_rtp_decoded_) {
    sequence_num_last_decoded_rtp_ = sequence_number;
    timestamp_last_decoded_rtp_ = timestamp;
    // Packets in the list with sequence numbers less than the
    // sequence number of the decoded RTP should be removed from the lists.
    // They will be discarded by the jitter buffer if they arrive.
    nack_list_.erase(nack_list_.begin(), nack_list_.upper_bound(
        sequence_num_last_decoded_rtp_));

    // Update estimated time-to-play.
    for (NackList::iterator it = nack_list_.begin(); it != nack_list_.end();
        ++it)
      it->second.time_to_play_ms = TimeToPlay(it->second.estimated_timestamp);
  } else {
    assert(sequence_number == sequence_num_last_decoded_rtp_);

    // Same sequence number as before. 10 ms is elapsed, update estimations for
    // time-to-play.
    UpdateEstimatedPlayoutTimeBy10ms();

    // Update timestamp for better estimate of time-to-play, for packets which
    // are added to NACK list later on.
    timestamp_last_decoded_rtp_ += sample_rate_khz_ * 10;
  }
  any_rtp_decoded_ = true;
}

Nack::NackList Nack::GetNackList() const {
  return nack_list_;
}

void Nack::Reset() {
  nack_list_.clear();

  sequence_num_last_received_rtp_ = 0;
  timestamp_last_received_rtp_ = 0;
  any_rtp_received_ = false;
  sequence_num_last_decoded_rtp_ = 0;
  timestamp_last_decoded_rtp_ = 0;
  any_rtp_decoded_ = false;
  sample_rate_khz_ = kDefaultSampleRateKhz;
  samples_per_packet_ = sample_rate_khz_ * kDefaultPacketSizeMs;
}

int Nack::SetMaxNackListSize(size_t max_nack_list_size) {
  if (max_nack_list_size == 0 || max_nack_list_size > kNackListSizeLimit)
    return -1;
  max_nack_list_size_ = max_nack_list_size;
  LimitNackListSize();
  return 0;
}

void Nack::LimitNackListSize() {
  uint16_t limit = sequence_num_last_received_rtp_ -
      static_cast<uint16_t>(max_nack_list_size_) - 1;
  nack_list_.erase(nack_list_.begin(), nack_list_.upper_bound(limit));
}

int Nack::TimeToPlay(uint32_t timestamp) const {
  uint32_t timestamp_increase = timestamp - timestamp_last_decoded_rtp_;
  return timestamp_increase / sample_rate_khz_;
}

// We don't erase elements with time-to-play shorter than round-trip-time.
std::vector<uint16_t> Nack::GetNackList(int round_trip_time_ms) const {
  std::vector<uint16_t> sequence_numbers;
  for (NackList::const_iterator it = nack_list_.begin(); it != nack_list_.end();
      ++it) {
    if (it->second.is_missing &&
        it->second.time_to_play_ms > round_trip_time_ms)
      sequence_numbers.push_back(it->first);
  }
  return sequence_numbers;
}

}  // namespace acm2

}  // namespace webrtc
