/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_STATISTICS_CALCULATOR_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_STATISTICS_CALCULATOR_H_

#include <vector>

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/neteq/interface/neteq.h"
#include "webrtc/typedefs.h"

namespace webrtc {

// Forward declarations.
class DecisionLogic;
class DelayManager;

// This class handles various network statistics in NetEq.
class StatisticsCalculator {
 public:
  StatisticsCalculator();

  virtual ~StatisticsCalculator() {}

  // Resets most of the counters.
  void Reset();

  // Resets the counters that are not handled by Reset().
  void ResetMcu();

  // Resets the waiting time statistics.
  void ResetWaitingTimeStatistics();

  // Reports that |num_samples| samples were produced through expansion, and
  // that the expansion produced other than just noise samples.
  void ExpandedVoiceSamples(int num_samples);

  // Reports that |num_samples| samples were produced through expansion, and
  // that the expansion produced only noise samples.
  void ExpandedNoiseSamples(int num_samples);

  // Reports that |num_samples| samples were produced through preemptive
  // expansion.
  void PreemptiveExpandedSamples(int num_samples);

  // Reports that |num_samples| samples were removed through accelerate.
  void AcceleratedSamples(int num_samples);

  // Reports that |num_samples| zeros were inserted into the output.
  void AddZeros(int num_samples);

  // Reports that |num_packets| packets were discarded.
  void PacketsDiscarded(int num_packets);

  // Reports that |num_samples| were lost.
  void LostSamples(int num_samples);

  // Increases the report interval counter with |num_samples| at a sample rate
  // of |fs_hz|.
  void IncreaseCounter(int num_samples, int fs_hz);

  // Stores new packet waiting time in waiting time statistics.
  void StoreWaitingTime(int waiting_time_ms);

  // Returns the current network statistics in |stats|. The current sample rate
  // is |fs_hz|, the total number of samples in packet buffer and sync buffer
  // yet to play out is |num_samples_in_buffers|, and the number of samples per
  // packet is |samples_per_packet|.
  void GetNetworkStatistics(int fs_hz,
                            int num_samples_in_buffers,
                            int samples_per_packet,
                            const DelayManager& delay_manager,
                            const DecisionLogic& decision_logic,
                            NetEqNetworkStatistics *stats);

  void WaitingTimes(std::vector<int>* waiting_times);

 private:
  static const int kMaxReportPeriod = 60;  // Seconds before auto-reset.
  static const int kLenWaitingTimes = 100;

  // Calculates numerator / denominator, and returns the value in Q14.
  static int CalculateQ14Ratio(uint32_t numerator, uint32_t denominator);

  uint32_t preemptive_samples_;
  uint32_t accelerate_samples_;
  int added_zero_samples_;
  uint32_t expanded_voice_samples_;
  uint32_t expanded_noise_samples_;
  int discarded_packets_;
  uint32_t lost_timestamps_;
  uint32_t last_report_timestamp_;
  int waiting_times_[kLenWaitingTimes];  // Used as a circular buffer.
  int len_waiting_times_;
  int next_waiting_time_index_;

  DISALLOW_COPY_AND_ASSIGN(StatisticsCalculator);
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_STATISTICS_CALCULATOR_H_
