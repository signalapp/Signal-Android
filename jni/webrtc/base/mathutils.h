/*
 *  Copyright 2005 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_MATHUTILS_H_
#define WEBRTC_BASE_MATHUTILS_H_

#include <math.h>
#include <type_traits>

#include "webrtc/base/checks.h"

#ifndef M_PI
#define M_PI 3.14159265359f
#endif

// Given two numbers |x| and |y| such that x >= y, computes the difference
// x - y without causing undefined behavior due to signed overflow.
template <typename T>
typename std::make_unsigned<T>::type unsigned_difference(T x, T y) {
  static_assert(
      std::is_signed<T>::value,
      "Function unsigned_difference is only meaningful for signed types.");
  RTC_DCHECK_GE(x, y);
  typedef typename std::make_unsigned<T>::type unsigned_type;
  // int -> unsigned conversion repeatedly adds UINT_MAX + 1 until the number
  // can be represented as an unsigned. Since we know that the actual
  // difference x - y can be represented as an unsigned, it is sufficient to
  // compute the difference modulo UINT_MAX + 1, i.e using unsigned arithmetic.
  return static_cast<unsigned_type>(x) - static_cast<unsigned_type>(y);
}

#endif  // WEBRTC_BASE_MATHUTILS_H_
