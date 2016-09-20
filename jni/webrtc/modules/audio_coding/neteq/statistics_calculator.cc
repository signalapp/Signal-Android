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
#include <algorithm>

#include "webrtc/base/checks.h"
#include "webrtc/base/safe_conversions.h"
#include "webrtc/modules/audio_coding/neteq/decision_logic.h"
#include "webrtc/modules/audio_coding/neteq/delay_manager.h"
#include "webrtc/system_wrappers/include/metrics.h"

namespace webrtc {

// Allocating the static const so that it can be passed by reference to
// RTC_DCHECK.
const size_t StatisticsCalculator::kLenWaitingTimes;

StatisticsCalculator::PeriodicUmaLogger::PeriodicUmaLogger(
    const std::string& uma_name,
    int report_interval_ms,
    int max_value)
    : uma_name_(uma_name),
      report_interval_ms_(report_interval_ms),
      max_value_(max_value),
      timer_(0) {
}

StatisticsCalculator::PeriodicUmaLogger::~PeriodicUmaLogger() = default;

void StatisticsCalculator::PeriodicUmaLogger::AdvanceClock(int step_ms) {
  timer_ += step_ms;
  if (timer_ < report_interval_ms_) {
    return;
  }
  LogToUma(Metric());
  Reset();
  timer_ -= report_interval_ms_;
  RTC_DCHECK_GE(timer_, 0);
}

void StatisticsCalculator::PeriodicUmaLogger::LogToUma(int value) const {
  RTC_HISTOGRAM_COUNTS_SPARSE(uma_name_, value, 1, max_value_, 50);
}

StatisticsCalculator::PeriodicUmaCount::PeriodicUmaCount(
    const std::string& uma_name,
    int report_interval_ms,
    int max_value)
    : PeriodicUmaLogger(uma_name, report_interval_ms, max_value) {
}

StatisticsCalculator::PeriodicUmaCount::~PeriodicUmaCount() {
  // Log the count for the current (incomplete) interval.
  LogToUma(Metric());
}

void StatisticsCalculator::PeriodicUmaCount::RegisterSample() {
  ++counter_;
}

int StatisticsCalculator::PeriodicUmaCount::Metric() const {
  return counter_;
}

void StatisticsCalculator::PeriodicUmaCount::Reset() {
  counter_ = 0;
}

StatisticsCalculator::PeriodicUmaAverage::PeriodicUmaAverage(
    const std::string& uma_name,
    int report_interval_ms,
    int max_value)
    : PeriodicUmaLogger(uma_name, report_interval_ms, max_value) {
}

StatisticsCalculator::PeriodicUmaAverage::~PeriodicUmaAverage() {
  // Log the average for the current (incomplete) interval.
  LogToUma(Metric());
}

void StatisticsCalculator::PeriodicUmaAverage::RegisterSample(int value) {
  sum_ += value;
  ++counter_;
}

int StatisticsCalculator::PeriodicUmaAverage::Metric() const {
  return counter_ == 0 ? 0 : static_cast<int>(sum_ / counter_);
}

void StatisticsCalculator::PeriodicUmaAverage::Reset() {
  sum_ = 0.0;
  counter_ = 0;
}

StatisticsCalculator::StatisticsCalculator()
    : preemptive_samples_(0),
      accelerate_samples_(0),
      added_zero_samples_(0),
      expanded_speech_samples_(0),
      expanded_noise_samples_(0),
      discarded_packets_(0),
      lost_timestamps_(0),
      timestamps_since_last_report_(0),
      secondary_decoded_samples_(0),
      delayed_packet_outage_counter_(
          "WebRTC.Audio.DelayedPacketOutageEventsPerMinute",
          60000,  // 60 seconds report interval.
          100),
      excess_buffer_delay_("WebRTC.Audio.AverageExcessBufferDelayMs",
                           60000,  // 60 seconds report interval.
                           1000) {
}

StatisticsCalculator::~StatisticsCalculator() = default;

void StatisticsCalculator::Reset() {
  preemptive_samples_ = 0;
  accelerate_samples_ = 0;
  added_zero_samples_ = 0;
  expanded_speech_samples_ = 0;
  expanded_noise_samples_ = 0;
  secondary_decoded_samples_ = 0;
  waiting_times_.clear();
}

void StatisticsCalculator::ResetMcu() {
  discarded_packets_ = 0;
  lost_timestamps_ = 0;
  timestamps_since_last_report_ = 0;
}

void StatisticsCalculator::ExpandedVoiceSamples(size_t num_samples) {
  expanded_speech_samples_ += num_samples;
}

void StatisticsCalculator::ExpandedNoiseSamples(size_t num_samples) {
  expanded_noise_samples_ += num_samples;
}

void StatisticsCalculator::PreemptiveExpandedSamples(size_t num_samples) {
  preemptive_samples_ += num_samples;
}

void StatisticsCalculator::AcceleratedSamples(size_t num_samples) {
  accelerate_samples_ += num_samples;
}

void StatisticsCalculator::AddZeros(size_t num_samples) {
  added_zero_samples_ += num_samples;
}

void StatisticsCalculator::PacketsDiscarded(size_t num_packets) {
  discarded_packets_ += num_packets;
}

void StatisticsCalculator::LostSamples(size_t num_samples) {
  lost_timestamps_ += num_samples;
}

void StatisticsCalculator::IncreaseCounter(size_t num_samples, int fs_hz) {
  const int time_step_ms =
      rtc::CheckedDivExact(static_cast<int>(1000 * num_samples), fs_hz);
  delayed_packet_outage_counter_.AdvanceClock(time_step_ms);
  excess_buffer_delay_.AdvanceClock(time_step_ms);
  timestamps_since_last_report_ += static_cast<uint32_t>(num_samples);
  if (timestamps_since_last_report_ >
      static_cast<uint32_t>(fs_hz * kMaxReportPeriod)) {
    lost_timestamps_ = 0;
    timestamps_since_last_report_ = 0;
    discarded_packets_ = 0;
  }
}

void StatisticsCalculator::SecondaryDecodedSamples(int num_samples) {
  secondary_decoded_samples_ += num_samples;
}

void StatisticsCalculator::LogDelayedPacketOutageEvent(int outage_duration_ms) {
  RTC_HISTOGRAM_COUNTS("WebRTC.Audio.DelayedPacketOutageEventMs",
                       outage_duration_ms, 1 /* min */, 2000 /* max */,
                       100 /* bucket count */);
  delayed_packet_outage_counter_.RegisterSample();
}

void StatisticsCalculator::StoreWaitingTime(int waiting_time_ms) {
  excess_buffer_delay_.RegisterSample(waiting_time_ms);
  RTC_DCHECK_LE(waiting_times_.size(), kLenWaitingTimes);
  if (waiting_times_.size() == kLenWaitingTimes) {
    // Erase first value.
    waiting_times_.pop_front();
  }
  waiting_times_.push_back(waiting_time_ms);
}

void StatisticsCalculator::GetNetworkStatistics(
    int fs_hz,
    size_t num_samples_in_buffers,
    size_t samples_per_packet,
    const DelayManager& delay_manager,
    const DecisionLogic& decision_logic,
    NetEqNetworkStatistics *stats) {
  if (fs_hz <= 0 || !stats) {
    assert(false);
    return;
  }

  stats->added_zero_samples = added_zero_samples_;
  stats->current_buffer_size_ms =
      static_cast<uint16_t>(num_samples_in_buffers * 1000 / fs_hz);
  const int ms_per_packet = rtc::checked_cast<int>(
      decision_logic.packet_length_samples() / (fs_hz / 1000));
  stats->preferred_buffer_size_ms = (delay_manager.TargetLevel() >> 8) *
      ms_per_packet;
  stats->jitter_peaks_found = delay_manager.PeakFound();
  stats->clockdrift_ppm = delay_manager.AverageIAT();

  stats->packet_loss_rate =
      CalculateQ14Ratio(lost_timestamps_, timestamps_since_last_report_);

  const size_t discarded_samples = discarded_packets_ * samples_per_packet;
  stats->packet_discard_rate =
      CalculateQ14Ratio(discarded_samples, timestamps_since_last_report_);

  stats->accelerate_rate =
      CalculateQ14Ratio(accelerate_samples_, timestamps_since_last_report_);

  stats->preemptive_rate =
      CalculateQ14Ratio(preemptive_samples_, timestamps_since_last_report_);

  stats->expand_rate =
      CalculateQ14Ratio(expanded_speech_samples_ + expanded_noise_samples_,
                        timestamps_since_last_report_);

  stats->speech_expand_rate =
      CalculateQ14Ratio(expanded_speech_samples_,
                        timestamps_since_last_report_);

  stats->secondary_decoded_rate =
      CalculateQ14Ratio(secondary_decoded_samples_,
                        timestamps_since_last_report_);

  if (waiting_times_.size() == 0) {
    stats->mean_waiting_time_ms = -1;
    stats->median_waiting_time_ms = -1;
    stats->min_waiting_time_ms = -1;
    stats->max_waiting_time_ms = -1;
  } else {
    std::sort(waiting_times_.begin(), waiting_times_.end());
    // Find mid-point elements. If the size is odd, the two values
    // |middle_left| and |middle_right| will both be the one middle element; if
    // the size is even, they will be the the two neighboring elements at the
    // middle of the list.
    const int middle_left = waiting_times_[(waiting_times_.size() - 1) / 2];
    const int middle_right = waiting_times_[waiting_times_.size() / 2];
    // Calculate the average of the two. (Works also for odd sizes.)
    stats->median_waiting_time_ms = (middle_left + middle_right) / 2;
    stats->min_waiting_time_ms = waiting_times_.front();
    stats->max_waiting_time_ms = waiting_times_.back();
    double sum = 0;
    for (auto time : waiting_times_) {
      sum += time;
    }
    stats->mean_waiting_time_ms = static_cast<int>(sum / waiting_times_.size());
  }

  // Reset counters.
  ResetMcu();
  Reset();
}

uint16_t StatisticsCalculator::CalculateQ14Ratio(size_t numerator,
                                                 uint32_t denominator) {
  if (numerator == 0) {
    return 0;
  } else if (numerator < denominator) {
    // Ratio must be smaller than 1 in Q14.
    assert((numerator << 14) / denominator < (1 << 14));
    return static_cast<uint16_t>((numerator << 14) / denominator);
  } else {
    // Will not produce a ratio larger than 1, since this is probably an error.
    return 1 << 14;
  }
}

}  // namespace webrtc
