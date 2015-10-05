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
#include <libkern/OSAtomic.h>
#include <stdlib.h>

#include "webrtc/common_types.h"

namespace webrtc {

Atomic32::Atomic32(int32_t initial_value)
    : value_(initial_value) {
  assert(Is32bitAligned());
}

Atomic32::~Atomic32() {
}

int32_t Atomic32::operator++() {
  return OSAtomicIncrement32Barrier(&value_);
}

int32_t Atomic32::operator--() {
  return OSAtomicDecrement32Barrier(&value_);
}

int32_t Atomic32::operator+=(int32_t value) {
  return OSAtomicAdd32Barrier(value, &value_);
}

int32_t Atomic32::operator-=(int32_t value) {
  return OSAtomicAdd32Barrier(-value, &value_);
}

bool Atomic32::CompareExchange(int32_t new_value, int32_t compare_value) {
  return OSAtomicCompareAndSwap32Barrier(compare_value, new_value, &value_);
}

}  // namespace webrtc
