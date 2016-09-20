/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_ARRAY_VIEW_H_
#define WEBRTC_BASE_ARRAY_VIEW_H_

#include "webrtc/base/checks.h"

namespace rtc {

// Many functions read from or write to arrays. The obvious way to do this is
// to use two arguments, a pointer to the first element and an element count:
//
//   bool Contains17(const int* arr, size_t size) {
//     for (size_t i = 0; i < size; ++i) {
//       if (arr[i] == 17)
//         return true;
//     }
//     return false;
//   }
//
// This is flexible, since it doesn't matter how the array is stored (C array,
// std::vector, rtc::Buffer, ...), but it's error-prone because the caller has
// to correctly specify the array length:
//
//   Contains17(arr, arraysize(arr));  // C array
//   Contains17(&arr[0], arr.size());  // std::vector
//   Contains17(arr, size);            // pointer + size
//   ...
//
// It's also kind of messy to have two separate arguments for what is
// conceptually a single thing.
//
// Enter rtc::ArrayView<T>. It contains a T pointer (to an array it doesn't
// own) and a count, and supports the basic things you'd expect, such as
// indexing and iteration. It allows us to write our function like this:
//
//   bool Contains17(rtc::ArrayView<const int> arr) {
//     for (auto e : arr) {
//       if (e == 17)
//         return true;
//     }
//     return false;
//   }
//
// And even better, because a bunch of things will implicitly convert to
// ArrayView, we can call it like this:
//
//   Contains17(arr);                             // C array
//   Contains17(arr);                             // std::vector
//   Contains17(rtc::ArrayView<int>(arr, size));  // pointer + size
//   Contains17(nullptr);                         // nullptr -> empty ArrayView
//   ...
//
// One important point is that ArrayView<T> and ArrayView<const T> are
// different types, which allow and don't allow mutation of the array elements,
// respectively. The implicit conversions work just like you'd hope, so that
// e.g. vector<int> will convert to either ArrayView<int> or ArrayView<const
// int>, but const vector<int> will convert only to ArrayView<const int>.
// (ArrayView itself can be the source type in such conversions, so
// ArrayView<int> will convert to ArrayView<const int>.)
//
// Note: ArrayView is tiny (just a pointer and a count) and trivially copyable,
// so it's probably cheaper to pass it by value than by const reference.
template <typename T>
class ArrayView final {
 public:
  // Construct an empty ArrayView.
  ArrayView() : ArrayView(static_cast<T*>(nullptr), 0) {}
  ArrayView(std::nullptr_t) : ArrayView() {}

  // Construct an ArrayView for a (pointer,size) pair.
  template <typename U>
  ArrayView(U* data, size_t size)
      : data_(size == 0 ? nullptr : data), size_(size) {
    CheckInvariant();
  }

  // Construct an ArrayView for an array.
  template <typename U, size_t N>
  ArrayView(U (&array)[N]) : ArrayView(&array[0], N) {}

  // Construct an ArrayView for any type U that has a size() method whose
  // return value converts implicitly to size_t, and a data() method whose
  // return value converts implicitly to T*. In particular, this means we allow
  // conversion from ArrayView<T> to ArrayView<const T>, but not the other way
  // around. Other allowed conversions include std::vector<T> to ArrayView<T>
  // or ArrayView<const T>, const std::vector<T> to ArrayView<const T>, and
  // rtc::Buffer to ArrayView<uint8_t> (with the same const behavior as
  // std::vector).
  template <typename U>
  ArrayView(U& u) : ArrayView(u.data(), u.size()) {}

  // Indexing, size, and iteration. These allow mutation even if the ArrayView
  // is const, because the ArrayView doesn't own the array. (To prevent
  // mutation, use ArrayView<const T>.)
  size_t size() const { return size_; }
  bool empty() const { return size_ == 0; }
  T* data() const { return data_; }
  T& operator[](size_t idx) const {
    RTC_DCHECK_LT(idx, size_);
    RTC_DCHECK(data_);  // Follows from size_ > idx and the class invariant.
    return data_[idx];
  }
  T* begin() const { return data_; }
  T* end() const { return data_ + size_; }
  const T* cbegin() const { return data_; }
  const T* cend() const { return data_ + size_; }

  // Comparing two ArrayViews compares their (pointer,size) pairs; it does
  // *not* dereference the pointers.
  friend bool operator==(const ArrayView& a, const ArrayView& b) {
    return a.data_ == b.data_ && a.size_ == b.size_;
  }
  friend bool operator!=(const ArrayView& a, const ArrayView& b) {
    return !(a == b);
  }

 private:
  // Invariant: !data_ iff size_ == 0.
  void CheckInvariant() const { RTC_DCHECK_EQ(!data_, size_ == 0); }
  T* data_;
  size_t size_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_ARRAY_VIEW_H_
