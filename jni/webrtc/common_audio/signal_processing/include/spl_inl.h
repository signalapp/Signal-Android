/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */


// This header file includes the inline functions in
// the fix point signal processing library.

#ifndef WEBRTC_SPL_SPL_INL_H_
#define WEBRTC_SPL_SPL_INL_H_

#ifdef WEBRTC_ARCH_ARM_V7
#include "webrtc/common_audio/signal_processing/include/spl_inl_armv7.h"
#else

#if defined(MIPS32_LE)
#include "webrtc/common_audio/signal_processing/include/spl_inl_mips.h"
#endif

#if !defined(MIPS_DSP_R1_LE)
static __inline int16_t WebRtcSpl_SatW32ToW16(int32_t value32) {
  int16_t out16 = (int16_t) value32;

  if (value32 > 32767)
    out16 = 32767;
  else if (value32 < -32768)
    out16 = -32768;

  return out16;
}

static __inline int32_t WebRtcSpl_AddSatW32(int32_t l_var1, int32_t l_var2) {
  int32_t l_sum;

  // Perform long addition
  l_sum = l_var1 + l_var2;

  if (l_var1 < 0) {  // Check for underflow.
    if ((l_var2 < 0) && (l_sum >= 0)) {
        l_sum = (int32_t)0x80000000;
    }
  } else {  // Check for overflow.
    if ((l_var2 > 0) && (l_sum < 0)) {
        l_sum = (int32_t)0x7FFFFFFF;
    }
  }

  return l_sum;
}

static __inline int32_t WebRtcSpl_SubSatW32(int32_t l_var1, int32_t l_var2) {
  int32_t l_diff;

  // Perform subtraction.
  l_diff = l_var1 - l_var2;

  if (l_var1 < 0) {  // Check for underflow.
    if ((l_var2 > 0) && (l_diff > 0)) {
      l_diff = (int32_t)0x80000000;
    }
  } else {  // Check for overflow.
    if ((l_var2 < 0) && (l_diff < 0)) {
      l_diff = (int32_t)0x7FFFFFFF;
    }
  }

  return l_diff;
}

static __inline int16_t WebRtcSpl_AddSatW16(int16_t a, int16_t b) {
  return WebRtcSpl_SatW32ToW16((int32_t) a + (int32_t) b);
}

static __inline int16_t WebRtcSpl_SubSatW16(int16_t var1, int16_t var2) {
  return WebRtcSpl_SatW32ToW16((int32_t) var1 - (int32_t) var2);
}
#endif  // #if !defined(MIPS_DSP_R1_LE)

#if !defined(MIPS32_LE)
static __inline int16_t WebRtcSpl_GetSizeInBits(uint32_t n) {
  int bits;

  if (0xFFFF0000 & n) {
    bits = 16;
  } else {
    bits = 0;
  }
  if (0x0000FF00 & (n >> bits)) bits += 8;
  if (0x000000F0 & (n >> bits)) bits += 4;
  if (0x0000000C & (n >> bits)) bits += 2;
  if (0x00000002 & (n >> bits)) bits += 1;
  if (0x00000001 & (n >> bits)) bits += 1;

  return bits;
}

static __inline int WebRtcSpl_NormW32(int32_t a) {
  int zeros;

  if (a == 0) {
    return 0;
  }
  else if (a < 0) {
    a = ~a;
  }

  if (!(0xFFFF8000 & a)) {
    zeros = 16;
  } else {
    zeros = 0;
  }
  if (!(0xFF800000 & (a << zeros))) zeros += 8;
  if (!(0xF8000000 & (a << zeros))) zeros += 4;
  if (!(0xE0000000 & (a << zeros))) zeros += 2;
  if (!(0xC0000000 & (a << zeros))) zeros += 1;

  return zeros;
}

static __inline int WebRtcSpl_NormU32(uint32_t a) {
  int zeros;

  if (a == 0) return 0;

  if (!(0xFFFF0000 & a)) {
    zeros = 16;
  } else {
    zeros = 0;
  }
  if (!(0xFF000000 & (a << zeros))) zeros += 8;
  if (!(0xF0000000 & (a << zeros))) zeros += 4;
  if (!(0xC0000000 & (a << zeros))) zeros += 2;
  if (!(0x80000000 & (a << zeros))) zeros += 1;

  return zeros;
}

static __inline int WebRtcSpl_NormW16(int16_t a) {
  int zeros;

  if (a == 0) {
    return 0;
  }
  else if (a < 0) {
    a = ~a;
  }

  if (!(0xFF80 & a)) {
    zeros = 8;
  } else {
    zeros = 0;
  }
  if (!(0xF800 & (a << zeros))) zeros += 4;
  if (!(0xE000 & (a << zeros))) zeros += 2;
  if (!(0xC000 & (a << zeros))) zeros += 1;

  return zeros;
}

static __inline int32_t WebRtc_MulAccumW16(int16_t a, int16_t b, int32_t c) {
  return (a * b + c);
}
#endif  // #if !defined(MIPS32_LE)

#endif  // WEBRTC_ARCH_ARM_V7

#endif  // WEBRTC_SPL_SPL_INL_H_
