/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/statistics_calculator.h"

#include <assert.h>
#include <string.h>  // memset

#include "webrtc/modules/audio_coding/neteq/decision_logic.h"
#include "webrtc/modules/audio_coding/neteq/delay_manager.h"

namespace webrtc {

StatisticsCalculator::StatisticsCalculator()
    : preemptive_samples_(0),
      accelerate_samples_(0),
      added_zero_samples_(0),
      expanded_voice_samples_(0),
      expanded_noise_samples_(0),
      discarded_packets_(0),
      lost_timestamps_(0),
      last_report_timestamp_(0),
      len_waiting_times_(0),
      next_waiting_time_index_(0) {
  memset(waiting_times_, 0, kLenWaitingTimes * sizeof(waiting_times_[0]));
}

void StatisticsCalculator::Reset() {
  preemptive_samples_ = 0;
  accelerate_samples_ = 0;
  added_zero_samples_ = 0;
  expanded_voice_samples_ = 0;
  expanded_noise_samples_ = 0;
}

void StatisticsCalculator::ResetMcu() {
  discarded_packets_ = 0;
  lost_timestamps_ = 0;
  last_report_timestamp_ = 0;
}

void StatisticsCalculator::ResetWaitingTimeStatistics() {
  memset(waiting_times_, 0, kLenWaitingTimes * sizeof(waiting_times_[0]));
  len_waiting_times_ = 0;
  next_waiting_time_index_ = 0;
}

void StatisticsCalculator::ExpandedVoiceSamples(int num_samples) {
  expanded_voice_samples_ += num_samples;
}

void StatisticsCalculator::ExpandedNoiseSamples(int num_samples) {
  expanded_noise_samples_ += num_samples;
}

void StatisticsCalculator::PreemptiveExpandedSamples(int num_samples) {
  preemptive_samples_ += num_samples;
}

void StatisticsCalculator::AcceleratedSamples(int num_samples) {
  accelerate_samples_ += num_samples;
}

void StatisticsCalculator::AddZeros(int num_samples) {
  added_zero_samples_ += num_samples;
}

void StatisticsCalculator::PacketsDiscarded(int num_packets) {
  discarded_packets_ += num_packets;
}

void StatisticsCalculator::LostSamples(int num_samples) {
  lost_timestamps_ += num_samples;
}

void StatisticsCalculator::IncreaseCounter(int num_samples, int fs_hz) {
  last_report_timestamp_ += num_samples;
  if (last_report_timestamp_ >
      static_cast<uint32_t>(fs_hz * kMaxReportPeriod)) {
    lost_timestamps_ = 0;
    last_report_timestamp_ = 0;
    discarded_packets_ = 0;
  }
}

void StatisticsCalculator::StoreWaitingTime(int waiting_time_ms) {
  assert(next_waiting_time_index_ < kLenWaitingTimes);
  waiting_times_[next_waiting_time_index_] = waiting_time_ms;
  next_waiting_time_index_++;
  if (next_waiting_time_index_ >= kLenWaitingTimes) {
    next_waiting_time_index_ = 0;
  }
  if (len_waiting_times_ < kLenWaitingTimes) {
    len_waiting_times_++;
  }
}

void StatisticsCalculator::GetNetworkStatistics(
    int fs_hz,
    int num_samples_in_buffers,
    int samples_per_packet,
    const DelayManager& delay_manager,
    const DecisionLogic& decision_logic,
    NetEqNetworkStatistics *stats) {
  if (fs_hz <= 0 || !stats) {
    assert(false);
    return;
  }

  stats->added_zero_samples = added_zero_samples_;
  stats->current_buffer_size_ms = num_samples_in_buffers * 1000 / fs_hz;
  const int ms_per_packet = decision_logic.packet_length_samples() /
      (fs_hz / 1000);
  stats->preferred_buffer_size_ms = (delay_manager.TargetLevel() >> 8) *
      ms_per_packet;
  stats->jitter_peaks_found = delay_manager.PeakFound();
  stats->clockdrift_ppm = delay_manager.AverageIAT();

  stats->packet_loss_rate = CalculateQ14Ratio(lost_timestamps_,
                                              last_report_timestamp_);

  const unsigned discarded_samples = discarded_packets_ * samples_per_packet;
  stats->packet_discard_rate = CalculateQ14Ratio(discarded_samples,
                                                 last_report_timestamp_);

  stats->accelerate_rate = CalculateQ14Ratio(accelerate_samples_,
                                             last_report_timestamp_);

  stats->preemptive_rate = CalculateQ14Ratio(preemptive_samples_,
                                             last_report_timestamp_);

  stats->expand_rate = CalculateQ14Ratio(expanded_voice_samples_ +
                                         expanded_noise_samples_,
                                         last_report_timestamp_);

  // Reset counters.
  ResetMcu();
  Reset();
}

void StatisticsCalculator::WaitingTimes(std::vector<int>* waiting_times) {
  if (!waiting_times) {
    return;
  }
  waiting_times->assign(waiting_times_, waiting_times_ + len_waiting_times_);
  ResetWaitingTimeStatistics();
}

int StatisticsCalculator::CalculateQ14Ratio(uint32_t numerator,
                                            uint32_t denominator) {
  if (numerator == 0) {
    return 0;
  } else if (numerator < denominator) {
    // Ratio must be smaller than 1 in Q14.
    assert((numerator << 14) / denominator < (1 << 14));
    return (numerator << 14) / denominator;
  } else {
    // Will not produce a ratio larger than 1, since this is probably an error.
    return 1 << 14;
  }
}

}  // namespace webrtc
