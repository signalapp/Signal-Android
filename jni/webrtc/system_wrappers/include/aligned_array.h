/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_SYSTEM_WRAPPERS_INCLUDE_ALIGNED_ARRAY_
#define WEBRTC_SYSTEM_WRAPPERS_INCLUDE_ALIGNED_ARRAY_

#include "webrtc/base/checks.h"
#include "webrtc/system_wrappers/include/aligned_malloc.h"

namespace webrtc {

// Wrapper class for aligned arrays. Every row (and the first dimension) are
// aligned to the given byte alignment.
template<typename T> class AlignedArray {
 public:
  AlignedArray(size_t rows, size_t cols, size_t alignment)
      : rows_(rows),
        cols_(cols) {
    RTC_CHECK_GT(alignment, 0u);
    head_row_ = static_cast<T**>(AlignedMalloc(rows_ * sizeof(*head_row_),
                                               alignment));
    for (size_t i = 0; i < rows_; ++i) {
      head_row_[i] = static_cast<T*>(AlignedMalloc(cols_ * sizeof(**head_row_),
                                                   alignment));
    }
  }

  ~AlignedArray() {
    for (size_t i = 0; i < rows_; ++i) {
      AlignedFree(head_row_[i]);
    }
    AlignedFree(head_row_);
  }

  T* const* Array() {
    return head_row_;
  }

  const T* const* Array() const {
    return head_row_;
  }

  T* Row(size_t row) {
    RTC_CHECK_LE(row, rows_);
    return head_row_[row];
  }

  const T* Row(size_t row) const {
    RTC_CHECK_LE(row, rows_);
    return head_row_[row];
  }

  T& At(size_t row, size_t col) {
    RTC_CHECK_LE(col, cols_);
    return Row(row)[col];
  }

  const T& At(size_t row, size_t col) const {
    RTC_CHECK_LE(col, cols_);
    return Row(row)[col];
  }

  size_t rows() const {
    return rows_;
  }

  size_t cols() const {
    return cols_;
  }

 private:
  size_t rows_;
  size_t cols_;
  T** head_row_;
};

}  // namespace webrtc

#endif  // WEBRTC_SYSTEM_WRAPPERS_INCLUDE_ALIGNED_ARRAY_
