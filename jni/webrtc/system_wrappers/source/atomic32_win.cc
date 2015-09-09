/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/atomic32.h"

#include <assert.h>
#include <windows.h>

#include "webrtc/common_types.h"
#include "webrtc/system_wrappers/interface/compile_assert.h"

namespace webrtc {

Atomic32::Atomic32(int32_t initial_value)
    : value_(initial_value) {
  COMPILE_ASSERT(sizeof(value_) == sizeof(LONG),
                 counter_variable_is_the_expected_size);
  assert(Is32bitAligned());
}

Atomic32::~Atomic32() {
}

int32_t Atomic32::operator++() {
  return static_cast<int32_t>(InterlockedIncrement(
      reinterpret_cast<volatile LONG*>(&value_)));
}

int32_t Atomic32::operator--() {
  return static_cast<int32_t>(InterlockedDecrement(
      reinterpret_cast<volatile LONG*>(&value_)));
}

int32_t Atomic32::operator+=(int32_t value) {
  return InterlockedExchangeAdd(reinterpret_cast<volatile LONG*>(&value_),
                                value);
}

int32_t Atomic32::operator-=(int32_t value) {
  return InterlockedExchangeAdd(reinterpret_cast<volatile LONG*>(&value_),
                                -value);
}

bool Atomic32::CompareExchange(int32_t new_value, int32_t compare_value) {
  const LONG old_value = InterlockedCompareExchange(
      reinterpret_cast<volatile LONG*>(&value_),
      new_value,
      compare_value);

  // If the old value and the compare value is the same an exchange happened.
  return (old_value == compare_value);
}

}  // namespace webrtc
