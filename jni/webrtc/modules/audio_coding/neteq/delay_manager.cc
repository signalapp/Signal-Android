/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/delay_manager.h"

#include <assert.h>
#include <math.h>

#include <algorithm>  // max, min

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_coding/neteq/delay_peak_detector.h"
#include "webrtc/modules/interface/module_common_types.h"
#include "webrtc/system_wrappers/interface/logging.h"

namespace webrtc {

DelayManager::DelayManager(int max_packets_in_buffer,
                           DelayPeakDetector* peak_detector)
    : first_packet_received_(false),
      max_packets_in_buffer_(max_packets_in_buffer),
      iat_vector_(kMaxIat + 1, 0),
      iat_factor_(0),
      packet_iat_count_ms_(0),
      base_target_level_(4),  // In Q0 domain.
      target_level_(base_target_level_ << 8),  // In Q8 domain.
      packet_len_ms_(0),
      streaming_mode_(false),
      last_seq_no_(0),
      last_timestamp_(0),
      minimum_delay_ms_(0),
      least_required_delay_ms_(target_level_),
      maximum_delay_ms_(target_level_),
      iat_cumulative_sum_(0),
      max_iat_cumulative_sum_(0),
      max_timer_ms_(0),
      peak_detector_(*peak_detector),
      last_pack_cng_or_dtmf_(1) {
  assert(peak_detector);  // Should never be NULL.
  Reset();
}

DelayManager::~DelayManager() {}

const DelayManager::IATVector& DelayManager::iat_vector() const {
  return iat_vector_;
}

// Set the histogram vector to an exponentially decaying distribution
// iat_vector_[i] = 0.5^(i+1), i = 0, 1, 2, ...
// iat_vector_ is in Q30.
void DelayManager::ResetHistogram() {
  // Set temp_prob to (slightly more than) 1 in Q14. This ensures that the sum
  // of iat_vector_ is 1.
  uint16_t temp_prob = 0x4002;  // 16384 + 2 = 100000000000010 binary.
  IATVector::iterator it = iat_vector_.begin();
  for (; it < iat_vector_.end(); it++) {
    temp_prob >>= 1;
    (*it) = temp_prob << 16;
  }
  base_target_level_ = 4;
  target_level_ = base_target_level_ << 8;
}

int DelayManager::Update(uint16_t sequence_number,
                         uint32_t timestamp,
                         int sample_rate_hz) {
  if (sample_rate_hz <= 0) {
    return -1;
  }

  if (!first_packet_received_) {
    // Prepare for next packet arrival.
    packet_iat_count_ms_ = 0;
    last_seq_no_ = sequence_number;
    last_timestamp_ = timestamp;
    first_packet_received_ = true;
    return 0;
  }

  // Try calculating packet length from current and previous timestamps.
  int packet_len_ms;
  if (!IsNewerTimestamp(timestamp, last_timestamp_) ||
      !IsNewerSequenceNumber(sequence_number, last_seq_no_)) {
    // Wrong timestamp or sequence order; use stored value.
    packet_len_ms = packet_len_ms_;
  } else {
    // Calculate timestamps per packet and derive packet length in ms.
    int packet_len_samp =
        static_cast<uint32_t>(timestamp - last_timestamp_) /
        static_cast<uint16_t>(sequence_number - last_seq_no_);
    packet_len_ms = (1000 * packet_len_samp) / sample_rate_hz;
  }

  if (packet_len_ms > 0) {
    // Cannot update statistics unless |packet_len_ms| is valid.
    // Calculate inter-arrival time (IAT) in integer "packet times"
    // (rounding down). This is the value used as index to the histogram
    // vector |iat_vector_|.
    int iat_packets = packet_iat_count_ms_ / packet_len_ms;

    if (streaming_mode_) {
      UpdateCumulativeSums(packet_len_ms, sequence_number);
    }

    // Check for discontinuous packet sequence and re-ordering.
    if (IsNewerSequenceNumber(sequence_number, last_seq_no_ + 1)) {
      // Compensate for gap in the sequence numbers. Reduce IAT with the
      // expected extra time due to lost packets, but ensure that the IAT is
      // not negative.
      iat_packets -= static_cast<uint16_t>(sequence_number - last_seq_no_ - 1);
      iat_packets = std::max(iat_packets, 0);
    } else if (!IsNewerSequenceNumber(sequence_number, last_seq_no_)) {
      iat_packets += static_cast<uint16_t>(last_seq_no_ + 1 - sequence_number);
    }

    // Saturate IAT at maximum value.
    const int max_iat = kMaxIat;
    iat_packets = std::min(iat_packets, max_iat);
    UpdateHistogram(iat_packets);
    // Calculate new |target_level_| based on updated statistics.
    target_level_ = CalculateTargetLevel(iat_packets);
    if (streaming_mode_) {
      target_level_ = std::max(target_level_, max_iat_cumulative_sum_);
    }

    LimitTargetLevel();
  }  // End if (packet_len_ms > 0).

  // Prepare for next packet arrival.
  packet_iat_count_ms_ = 0;
  last_seq_no_ = sequence_number;
  last_timestamp_ = timestamp;
  return 0;
}

void DelayManager::UpdateCumulativeSums(int packet_len_ms,
                                        uint16_t sequence_number) {
  // Calculate IAT in Q8, including fractions of a packet (i.e., more
  // accurate than |iat_packets|.
  int iat_packets_q8 = (packet_iat_count_ms_ << 8) / packet_len_ms;
  // Calculate cumulative sum IAT with sequence number compensation. The sum
  // is zero if there is no clock-drift.
  iat_cumulative_sum_ += (iat_packets_q8 -
      (static_cast<int>(sequence_number - last_seq_no_) << 8));
  // Subtract drift term.
  iat_cumulative_sum_ -= kCumulativeSumDrift;
  // Ensure not negative.
  iat_cumulative_sum_ = std::max(iat_cumulative_sum_, 0);
  if (iat_cumulative_sum_ > max_iat_cumulative_sum_) {
    // Found a new maximum.
    max_iat_cumulative_sum_ = iat_cumulative_sum_;
    max_timer_ms_ = 0;
  }
  if (max_timer_ms_ > kMaxStreamingPeakPeriodMs) {
    // Too long since the last maximum was observed; decrease max value.
    max_iat_cumulative_sum_ -= kCumulativeSumDrift;
  }
}

// Each element in the vector is first multiplied by the forgetting factor
// |iat_factor_|. Then the vector element indicated by |iat_packets| is then
// increased (additive) by 1 - |iat_factor_|. This way, the probability of
// |iat_packets| is slightly increased, while the sum of the histogram remains
// constant (=1).
// Due to inaccuracies in the fixed-point arithmetic, the histogram may no
// longer sum up to 1 (in Q30) after the update. To correct this, a correction
// term is added or subtracted from the first element (or elements) of the
// vector.
// The forgetting factor |iat_factor_| is also updated. When the DelayManager
// is reset, the factor is set to 0 to facilitate rapid convergence in the
// beginning. With each update of the histogram, the factor is increased towards
// the steady-state value |kIatFactor_|.
void DelayManager::UpdateHistogram(size_t iat_packets) {
  assert(iat_packets < iat_vector_.size());
  int vector_sum = 0;  // Sum up the vector elements as they are processed.
  // Multiply each element in |iat_vector_| with |iat_factor_|.
  for (IATVector::iterator it = iat_vector_.begin();
      it != iat_vector_.end(); ++it) {
    *it = (static_cast<int64_t>(*it) * iat_factor_) >> 15;
    vector_sum += *it;
  }

  // Increase the probability for the currently observed inter-arrival time
  // by 1 - |iat_factor_|. The factor is in Q15, |iat_vector_| in Q30.
  // Thus, left-shift 15 steps to obtain result in Q30.
  iat_vector_[iat_packets] += (32768 - iat_factor_) << 15;
  vector_sum += (32768 - iat_factor_) << 15;  // Add to vector sum.

  // |iat_vector_| should sum up to 1 (in Q30), but it may not due to
  // fixed-point rounding errors.
  vector_sum -= 1 << 30;  // Should be zero. Compensate if not.
  if (vector_sum != 0) {
    // Modify a few values early in |iat_vector_|.
    int flip_sign = vector_sum > 0 ? -1 : 1;
    IATVector::iterator it = iat_vector_.begin();
    while (it != iat_vector_.end() && abs(vector_sum) > 0) {
      // Add/subtract 1/16 of the element, but not more than |vector_sum|.
      int correction = flip_sign * std::min(abs(vector_sum), (*it) >> 4);
      *it += correction;
      vector_sum += correction;
      ++it;
    }
  }
  assert(vector_sum == 0);  // Verify that the above is correct.

  // Update |iat_factor_| (changes only during the first seconds after a reset).
  // The factor converges to |kIatFactor_|.
  iat_factor_ += (kIatFactor_ - iat_factor_ + 3) >> 2;
}

// Enforces upper and lower limits for |target_level_|. The upper limit is
// chosen to be minimum of i) 75% of |max_packets_in_buffer_|, to leave some
// headroom for natural fluctuations around the target, and ii) equivalent of
// |maximum_delay_ms_| in packets. Note that in practice, if no
// |maximum_delay_ms_| is specified, this does not have any impact, since the
// target level is far below the buffer capacity in all reasonable cases.
// The lower limit is equivalent of |minimum_delay_ms_| in packets. We update
// |least_required_level_| while the above limits are applied.
// TODO(hlundin): Move this check to the buffer logistics class.
void DelayManager::LimitTargetLevel() {
  least_required_delay_ms_ = (target_level_ * packet_len_ms_) >> 8;

  if (packet_len_ms_ > 0 && minimum_delay_ms_ > 0) {
    int minimum_delay_packet_q8 =  (minimum_delay_ms_ << 8) / packet_len_ms_;
    target_level_ = std::max(target_level_, minimum_delay_packet_q8);
  }

  if (maximum_delay_ms_ > 0 && packet_len_ms_ > 0) {
    int maximum_delay_packet_q8 = (maximum_delay_ms_ << 8) / packet_len_ms_;
    target_level_ = std::min(target_level_, maximum_delay_packet_q8);
  }

  // Shift to Q8, then 75%.;
  int max_buffer_packets_q8 = (3 * (max_packets_in_buffer_ << 8)) / 4;
  target_level_ = std::min(target_level_, max_buffer_packets_q8);

  // Sanity check, at least 1 packet (in Q8).
  target_level_ = std::max(target_level_, 1 << 8);
}

int DelayManager::CalculateTargetLevel(int iat_packets) {
  int limit_probability = kLimitProbability;
  if (streaming_mode_) {
    limit_probability = kLimitProbabilityStreaming;
  }

  // Calculate target buffer level from inter-arrival time histogram.
  // Find the |iat_index| for which the probability of observing an
  // inter-arrival time larger than or equal to |iat_index| is less than or
  // equal to |limit_probability|. The sought probability is estimated using
  // the histogram as the reverse cumulant PDF, i.e., the sum of elements from
  // the end up until |iat_index|. Now, since the sum of all elements is 1
  // (in Q30) by definition, and since the solution is often a low value for
  // |iat_index|, it is more efficient to start with |sum| = 1 and subtract
  // elements from the start of the histogram.
  size_t index = 0;  // Start from the beginning of |iat_vector_|.
  int sum = 1 << 30;  // Assign to 1 in Q30.
  sum -= iat_vector_[index];  // Ensure that target level is >= 1.

  do {
    // Subtract the probabilities one by one until the sum is no longer greater
    // than limit_probability.
    ++index;
    sum -= iat_vector_[index];
  } while ((sum > limit_probability) && (index < iat_vector_.size() - 1));

  // This is the base value for the target buffer level.
  int target_level = static_cast<int>(index);
  base_target_level_ = static_cast<int>(index);

  // Update detector for delay peaks.
  bool delay_peak_found = peak_detector_.Update(iat_packets, target_level);
  if (delay_peak_found) {
    target_level = std::max(target_level, peak_detector_.MaxPeakHeight());
  }

  // Sanity check. |target_level| must be strictly positive.
  target_level = std::max(target_level, 1);
  // Scale to Q8 and assign to member variable.
  target_level_ = target_level << 8;
  return target_level_;
}

int DelayManager::SetPacketAudioLength(int length_ms) {
  if (length_ms <= 0) {
    LOG_F(LS_ERROR) << "length_ms = " << length_ms;
    return -1;
  }
  packet_len_ms_ = length_ms;
  peak_detector_.SetPacketAudioLength(packet_len_ms_);
  packet_iat_count_ms_ = 0;
  last_pack_cng_or_dtmf_ = 1;  // TODO(hlundin): Legacy. Remove?
  return 0;
}


void DelayManager::Reset() {
  packet_len_ms_ = 0;  // Packet size unknown.
  streaming_mode_ = false;
  peak_detector_.Reset();
  ResetHistogram();  // Resets target levels too.
  iat_factor_ = 0;  // Adapt the histogram faster for the first few packets.
  packet_iat_count_ms_ = 0;
  max_timer_ms_ = 0;
  iat_cumulative_sum_ = 0;
  max_iat_cumulative_sum_ = 0;
  last_pack_cng_or_dtmf_ = 1;
}

int DelayManager::AverageIAT() const {
  int32_t sum_q24 = 0;
  // Using an int for the upper limit of the following for-loop so the
  // loop-counter can be int. Otherwise we need a cast where |sum_q24| is
  // updated.
  const int iat_vec_size = static_cast<int>(iat_vector_.size());
  assert(iat_vector_.size() == 65);  // Algorithm is hard-coded for this size.
  for (int i = 0; i < iat_vec_size; ++i) {
    // Shift 6 to fit worst case: 2^30 * 64.
    sum_q24 += (iat_vector_[i] >> 6) * i;
  }
  // Subtract the nominal inter-arrival time 1 = 2^24 in Q24.
  sum_q24 -= (1 << 24);
  // Multiply with 1000000 / 2^24 = 15625 / 2^18 to get in parts-per-million.
  // Shift 7 to Q17 first, then multiply with 15625 and shift another 11.
  return ((sum_q24 >> 7) * 15625) >> 11;
}

bool DelayManager::PeakFound() const {
  return peak_detector_.peak_found();
}

void DelayManager::UpdateCounters(int elapsed_time_ms) {
  packet_iat_count_ms_ += elapsed_time_ms;
  peak_detector_.IncrementCounter(elapsed_time_ms);
  max_timer_ms_ += elapsed_time_ms;
}

void DelayManager::ResetPacketIatCount() { packet_iat_count_ms_ = 0; }

// Note that |low_limit| and |higher_limit| are not assigned to
// |minimum_delay_ms_| and |maximum_delay_ms_| defined by the client of this
// class. They are computed from |target_level_| and used for decision making.
void DelayManager::BufferLimits(int* lower_limit, int* higher_limit) const {
  if (!lower_limit || !higher_limit) {
    LOG_F(LS_ERROR) << "NULL pointers supplied as input";
    assert(false);
    return;
  }

  int window_20ms = 0x7FFF;  // Default large value for legacy bit-exactness.
  if (packet_len_ms_ > 0) {
    window_20ms = (20 << 8) / packet_len_ms_;
  }

  // |target_level_| is in Q8 already.
  *lower_limit = (target_level_ * 3) / 4;
  // |higher_limit| is equal to |target_level_|, but should at
  // least be 20 ms higher than |lower_limit_|.
  *higher_limit = std::max(target_level_, *lower_limit + window_20ms);
}

int DelayManager::TargetLevel() const {
  return target_level_;
}

void DelayManager::LastDecoderType(NetEqDecoder decoder_type) {
  if (decoder_type == kDecoderAVT ||
      decoder_type == kDecoderCNGnb ||
      decoder_type == kDecoderCNGwb ||
      decoder_type == kDecoderCNGswb32kHz ||
      decoder_type == kDecoderCNGswb48kHz) {
    last_pack_cng_or_dtmf_ = 1;
  } else if (last_pack_cng_or_dtmf_ != 0) {
    last_pack_cng_or_dtmf_ = -1;
  }
}

bool DelayManager::SetMinimumDelay(int delay_ms) {
  // Minimum delay shouldn't be more than maximum delay, if any maximum is set.
  // Also, if possible check |delay| to less than 75% of
  // |max_packets_in_buffer_|.
  if ((maximum_delay_ms_ > 0 && delay_ms > maximum_delay_ms_) ||
      (packet_len_ms_ > 0 &&
          delay_ms > 3 * max_packets_in_buffer_ * packet_len_ms_ / 4)) {
    return false;
  }
  minimum_delay_ms_ = delay_ms;
  return true;
}

bool DelayManager::SetMaximumDelay(int delay_ms) {
  if (delay_ms == 0) {
    // Zero input unsets the maximum delay.
    maximum_delay_ms_ = 0;
    return true;
  } else if (delay_ms < minimum_delay_ms_ || delay_ms < packet_len_ms_) {
    // Maximum delay shouldn't be less than minimum delay or less than a packet.
    return false;
  }
  maximum_delay_ms_ = delay_ms;
  return true;
}

int DelayManager::least_required_delay_ms() const {
  return least_required_delay_ms_;
}

int DelayManager::base_target_level() const { return base_target_level_; }
void DelayManager::set_streaming_mode(bool value) { streaming_mode_ = value; }
int DelayManager::last_pack_cng_or_dtmf() const {
  return last_pack_cng_or_dtmf_;
}

void DelayManager::set_last_pack_cng_or_dtmf(int value) {
  last_pack_cng_or_dtmf_ = value;
}
}  // namespace webrtc
