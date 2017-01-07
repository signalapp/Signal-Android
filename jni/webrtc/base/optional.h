/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_OPTIONAL_H_
#define WEBRTC_BASE_OPTIONAL_H_

#include <algorithm>
#include <memory>
#include <utility>

#include "webrtc/base/checks.h"

namespace rtc {

// Simple std::optional-wannabe. It either contains a T or not.
//
// A moved-from Optional<T> may only be destroyed, and assigned to if T allows
// being assigned to after having been moved from. Specifically, you may not
// assume that it just doesn't contain a value anymore.
//
// Examples of good places to use Optional:
//
// - As a class or struct member, when the member doesn't always have a value:
//     struct Prisoner {
//       std::string name;
//       Optional<int> cell_number;  // Empty if not currently incarcerated.
//     };
//
// - As a return value for functions that may fail to return a value on all
//   allowed inputs. For example, a function that searches an array might
//   return an Optional<size_t> (the index where it found the element, or
//   nothing if it didn't find it); and a function that parses numbers might
//   return Optional<double> (the parsed number, or nothing if parsing failed).
//
// Examples of bad places to use Optional:
//
// - As a return value for functions that may fail because of disallowed
//   inputs. For example, a string length function should not return
//   Optional<size_t> so that it can return nothing in case the caller passed
//   it a null pointer; the function should probably use RTC_[D]CHECK instead,
//   and return plain size_t.
//
// - As a return value for functions that may fail to return a value on all
//   allowed inputs, but need to tell the caller what went wrong. Returning
//   Optional<double> when parsing a single number as in the example above
//   might make sense, but any larger parse job is probably going to need to
//   tell the caller what the problem was, not just that there was one.
//
// TODO(kwiberg): Get rid of this class when the standard library has
// std::optional (and we're allowed to use it).
template <typename T>
class Optional final {
 public:
  // Construct an empty Optional.
  Optional() : has_value_(false) {}

  // Construct an Optional that contains a value.
  explicit Optional(const T& value) : has_value_(true) {
    new (&value_) T(value);
  }
  explicit Optional(T&& value) : has_value_(true) {
    new (&value_) T(std::move(value));
  }

  // Copy constructor: copies the value from m if it has one.
  Optional(const Optional& m) : has_value_(m.has_value_) {
    if (has_value_)
      new (&value_) T(m.value_);
  }

  // Move constructor: if m has a value, moves the value from m, leaving m
  // still in a state where it has a value, but a moved-from one (the
  // properties of which depends on T; the only general guarantee is that we
  // can destroy m).
  Optional(Optional&& m) : has_value_(m.has_value_) {
    if (has_value_)
      new (&value_) T(std::move(m.value_));
  }

  ~Optional() {
    if (has_value_)
      value_.~T();
  }

  // Copy assignment. Uses T's copy assignment if both sides have a value, T's
  // copy constructor if only the right-hand side has a value.
  Optional& operator=(const Optional& m) {
    if (m.has_value_) {
      if (has_value_) {
        value_ = m.value_;  // T's copy assignment.
      } else {
        new (&value_) T(m.value_);  // T's copy constructor.
        has_value_ = true;
      }
    } else if (has_value_) {
      value_.~T();
      has_value_ = false;
    }
    return *this;
  }

  // Move assignment. Uses T's move assignment if both sides have a value, T's
  // move constructor if only the right-hand side has a value. The state of m
  // after it's been moved from is as for the move constructor.
  Optional& operator=(Optional&& m) {
    if (m.has_value_) {
      if (has_value_) {
        value_ = std::move(m.value_);  // T's move assignment.
      } else {
        new (&value_) T(std::move(m.value_));  // T's move constructor.
        has_value_ = true;
      }
    } else if (has_value_) {
      value_.~T();
      has_value_ = false;
    }
    return *this;
  }

  // Swap the values if both m1 and m2 have values; move the value if only one
  // of them has one.
  friend void swap(Optional& m1, Optional& m2) {
    if (m1.has_value_) {
      if (m2.has_value_) {
        // Both have values: swap.
        using std::swap;
        swap(m1.value_, m2.value_);
      } else {
        // Only m1 has a value: move it to m2.
        new (&m2.value_) T(std::move(m1.value_));
        m1.value_.~T();  // Destroy the moved-from value.
        m1.has_value_ = false;
        m2.has_value_ = true;
      }
    } else if (m2.has_value_) {
      // Only m2 has a value: move it to m1.
      new (&m1.value_) T(std::move(m2.value_));
      m2.value_.~T();  // Destroy the moved-from value.
      m1.has_value_ = true;
      m2.has_value_ = false;
    }
  }

  // Conversion to bool to test if we have a value.
  explicit operator bool() const { return has_value_; }

  // Dereferencing. Only allowed if we have a value.
  const T* operator->() const {
    RTC_DCHECK(has_value_);
    return &value_;
  }
  T* operator->() {
    RTC_DCHECK(has_value_);
    return &value_;
  }
  const T& operator*() const {
    RTC_DCHECK(has_value_);
    return value_;
  }
  T& operator*() {
    RTC_DCHECK(has_value_);
    return value_;
  }

  // Dereference with a default value in case we don't have a value.
  const T& value_or(const T& default_val) const {
    return has_value_ ? value_ : default_val;
  }

  // Equality tests. Two Optionals are equal if they contain equivalent values,
  // or
  // if they're both empty.
  friend bool operator==(const Optional& m1, const Optional& m2) {
    return m1.has_value_ && m2.has_value_ ? m1.value_ == m2.value_
                                          : m1.has_value_ == m2.has_value_;
  }
  friend bool operator!=(const Optional& m1, const Optional& m2) {
    return m1.has_value_ && m2.has_value_ ? m1.value_ != m2.value_
                                          : m1.has_value_ != m2.has_value_;
  }

 private:
  bool has_value_;  // True iff value_ contains a live value.
  union {
    // By placing value_ in a union, we get to manage its construction and
    // destruction manually: the Optional constructors won't automatically
    // construct it, and the Optional destructor won't automatically destroy
    // it. Basically, this just allocates a properly sized and aligned block of
    // memory in which we can manually put a T with placement new.
    T value_;
  };
};

}  // namespace rtc

#endif  // WEBRTC_BASE_OPTIONAL_H_
