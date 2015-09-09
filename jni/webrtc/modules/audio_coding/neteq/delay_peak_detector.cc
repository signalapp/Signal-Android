/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/delay_peak_detector.h"

#include <algorithm>  // max

namespace webrtc {

// The DelayPeakDetector keeps track of severe inter-arrival times, called
// delay peaks. When a peak is observed, the "height" (the time elapsed since
// the previous packet arrival) and the peak "period" (the time since the last
// observed peak) is recorded in a vector. When enough peaks have been observed,
// peak-mode is engaged and the DelayManager asks the DelayPeakDetector for
// the worst peak height.

DelayPeakDetector::DelayPeakDetector()
  : peak_found_(false),
    peak_detection_threshold_(0),
    peak_period_counter_ms_(-1) {
}

void DelayPeakDetector::Reset() {
  peak_period_counter_ms_ = -1;  // Indicate that next peak is the first.
  peak_found_ = false;
  peak_history_.clear();
}

// Calculates the threshold in number of packets.
void DelayPeakDetector::SetPacketAudioLength(int length_ms) {
  if (length_ms > 0) {
    peak_detection_threshold_ = kPeakHeightMs / length_ms;
  }
}

int DelayPeakDetector::MaxPeakHeight() const {
  int max_height = -1;  // Returns -1 for an empty history.
  std::list<Peak>::const_iterator it;
  for (it = peak_history_.begin(); it != peak_history_.end(); ++it) {
    max_height = std::max(max_height, it->peak_height_packets);
  }
  return max_height;
}

int DelayPeakDetector::MaxPeakPeriod() const {
  int max_period = -1;  // Returns -1 for an empty history.
  std::list<Peak>::const_iterator it;
  for (it = peak_history_.begin(); it != peak_history_.end(); ++it) {
    max_period = std::max(max_period, it->period_ms);
  }
  return max_period;
}

bool DelayPeakDetector::Update(int inter_arrival_time, int target_level) {
  if (inter_arrival_time > target_level + peak_detection_threshold_ ||
      inter_arrival_time > 2 * target_level) {
    // A delay peak is observed.
    if (peak_period_counter_ms_ == -1) {
      // This is the first peak. Reset the period counter.
      peak_period_counter_ms_ = 0;
    } else if (peak_period_counter_ms_ <= kMaxPeakPeriodMs) {
      // This is not the first peak, and the period is valid.
      // Store peak data in the vector.
      Peak peak_data;
      peak_data.period_ms = peak_period_counter_ms_;
      peak_data.peak_height_packets = inter_arrival_time;
      peak_history_.push_back(peak_data);
      while (peak_history_.size() > kMaxNumPeaks) {
        // Delete the oldest data point.
        peak_history_.pop_front();
      }
      peak_period_counter_ms_ = 0;
    } else if (peak_period_counter_ms_ <= 2 * kMaxPeakPeriodMs) {
      // Invalid peak due to too long period. Reset period counter and start
      // looking for next peak.
      peak_period_counter_ms_ = 0;
    } else {
      // More than 2 times the maximum period has elapsed since the last peak
      // was registered. It seams that the network conditions have changed.
      // Reset the peak statistics.
      Reset();
    }
  }
  return CheckPeakConditions();
}

void DelayPeakDetector::IncrementCounter(int inc_ms) {
  if (peak_period_counter_ms_ >= 0) {
    peak_period_counter_ms_ += inc_ms;
  }
}

bool DelayPeakDetector::CheckPeakConditions() {
  size_t s = peak_history_.size();
  if (s >= kMinPeaksToTrigger &&
      peak_period_counter_ms_ <= 2 * MaxPeakPeriod()) {
    peak_found_ = true;
  } else {
    peak_found_ = false;
  }
  return peak_found_;
}
}  // namespace webrtc
