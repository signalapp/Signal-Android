/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/rate_statistics.h"

#include <algorithm>

#include "webrtc/base/checks.h"

namespace webrtc {

RateStatistics::RateStatistics(int64_t window_size_ms, float scale)
    : buckets_(new Bucket[window_size_ms]()),
      accumulated_count_(0),
      num_samples_(0),
      oldest_time_(-window_size_ms),
      oldest_index_(0),
      scale_(scale),
      max_window_size_ms_(window_size_ms),
      current_window_size_ms_(max_window_size_ms_) {}

RateStatistics::~RateStatistics() {}

void RateStatistics::Reset() {
  accumulated_count_ = 0;
  num_samples_ = 0;
  oldest_time_ = -max_window_size_ms_;
  oldest_index_ = 0;
  current_window_size_ms_ = max_window_size_ms_;
  for (int64_t i = 0; i < max_window_size_ms_; i++)
    buckets_[i] = Bucket();
}

void RateStatistics::Update(size_t count, int64_t now_ms) {
  if (now_ms < oldest_time_) {
    // Too old data is ignored.
    return;
  }

  EraseOld(now_ms);

  // First ever sample, reset window to start now.
  if (!IsInitialized())
    oldest_time_ = now_ms;

  uint32_t now_offset = static_cast<uint32_t>(now_ms - oldest_time_);
  RTC_DCHECK_LT(now_offset, max_window_size_ms_);
  uint32_t index = oldest_index_ + now_offset;
  if (index >= max_window_size_ms_)
    index -= max_window_size_ms_;
  buckets_[index].sum += count;
  ++buckets_[index].samples;
  accumulated_count_ += count;
  ++num_samples_;
}

rtc::Optional<uint32_t> RateStatistics::Rate(int64_t now_ms) {
  EraseOld(now_ms);

  // If window is a single bucket or there is only one sample in a data set that
  // has not grown to the full window size, treat this as rate unavailable.
  int64_t active_window_size = now_ms - oldest_time_ + 1;
  if (num_samples_ == 0 || active_window_size <= 1 ||
      (num_samples_ <= 1 && active_window_size < current_window_size_ms_)) {
    return rtc::Optional<uint32_t>();
  }

  float scale = scale_ / active_window_size;
  return rtc::Optional<uint32_t>(
      static_cast<uint32_t>(accumulated_count_ * scale + 0.5f));
}

void RateStatistics::EraseOld(int64_t now_ms) {
  if (!IsInitialized())
    return;

  // New oldest time that is included in data set.
  int64_t new_oldest_time = now_ms - current_window_size_ms_ + 1;

  // New oldest time is older than the current one, no need to cull data.
  if (new_oldest_time <= oldest_time_)
    return;

  // Loop over buckets and remove too old data points.
  while (num_samples_ > 0 && oldest_time_ < new_oldest_time) {
    const Bucket& oldest_bucket = buckets_[oldest_index_];
    RTC_DCHECK_GE(accumulated_count_, oldest_bucket.sum);
    RTC_DCHECK_GE(num_samples_, oldest_bucket.samples);
    accumulated_count_ -= oldest_bucket.sum;
    num_samples_ -= oldest_bucket.samples;
    buckets_[oldest_index_] = Bucket();
    if (++oldest_index_ >= max_window_size_ms_)
      oldest_index_ = 0;
    ++oldest_time_;
  }
  oldest_time_ = new_oldest_time;
}

bool RateStatistics::SetWindowSize(int64_t window_size_ms, int64_t now_ms) {
  if (window_size_ms <= 0 || window_size_ms > max_window_size_ms_)
    return false;

  current_window_size_ms_ = window_size_ms;
  EraseOld(now_ms);
  return true;
}

bool RateStatistics::IsInitialized() {
  return oldest_time_ != -max_window_size_ms_;
}

}  // namespace webrtc
