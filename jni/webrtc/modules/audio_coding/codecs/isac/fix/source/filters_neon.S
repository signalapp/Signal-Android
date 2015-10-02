@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@
@ Reference code in filters.c. Output is bit-exact.

#include "webrtc/system_wrappers/interface/asm_defines.h"

GLOBAL_FUNCTION WebRtcIsacfix_AutocorrNeon
.align  2

@ int WebRtcIsacfix_AutocorrNeon(
@     int32_t* __restrict r,
@     const int16_t* __restrict x,
@     int16_t N,
@     int16_t order,
@     int16_t* __restrict scale);

DEFINE_FUNCTION WebRtcIsacfix_AutocorrNeon
  push       {r3 - r12}

  @ Constant initializations
  mov        r4, #33
  vmov.i32   d0, #0
  vmov.i32   q8, #0
  vmov.i32   d29, #0               @ Initialize (-scale).
  vmov.u8    d30, #255             @ Initialize d30 as -1.
  vmov.i32   d0[0], r4             @ d0: 00000033 (low), 00000000 (high)
  vmov.i32   d25, #32

  mov        r5, r1                @ x
  mov        r6, r2                @ N

@ Generate the first coefficient r0.
LOOP_R0:
  vld1.16    {d18}, [r5]!          @ x[]
  subs       r6, r6, #4
  vmull.s16  q9, d18, d18
  vpadal.s32 q8, q9
  bgt        LOOP_R0

  vadd.i64   d16, d16, d17

  @ Calculate scaling (the value of shifting).
  vmov       d17, d16

  @ Check overflow and determine the value for 'scale'.
  @ vclz cannot deal with a 64-bit, so we have to do vclz on both the upper and
  @ lower 32-bit words. Note that we don't care about the value of the upper
  @ word in d17.

  @ Check the case of 1 bit overflow. If it occurs store the results for
  @ scale and r[0] in d17 and d29.

  vshr.u64   d3, d16, #1
  vclt.s32   d1, d16, #0           @ < 0 ?
  vbit       d17, d3, d1           @ For r[0]
  vbit       d29, d30, d1          @ -scale = -1

  @ For the case of more than 1 bit overflow. If it occurs overwrite the
  @ results for scale and r[0] in d17 and d29.
  vclz.s32   d5, d16               @ Leading zeros of the two 32 bit words.
  vshr.s64   d26, d5, #32          @ Keep only the upper 32 bits.
  vsub.i64   d31, d26, d0          @ zeros - 33
  vshl.i64   d27, d26, #32
  vorr       d27, d26              @ Duplicate the high word with its low one.
  vshl.u64   d2, d16, d31          @ Shift by (-scale).
  vclt.s32   d1, d27, d25          @ < 32 ?
  vbit       d17, d2, d1           @ For r[0]
  vbit       d29, d31, d1          @ -scale

  vst1.32    d17[0], [r0]!         @ r[0]
  mov        r5, #1                @ outer loop counter

@ Generate rest of the coefficients
LOOP_R:
  vmov.i32   q8, #0                @ Initialize the accumulation result.
  vmov.i32   q9, #0                @ Initialize the accumulation result.
  mov        r7, r1                @ &x[0]
  add        r6, r7, r5, lsl #1    @ x[i]
  sub        r12, r2, r5           @ N - i
  lsr        r8, r12, #3           @ inner loop counter
  sub        r12, r8, lsl #3       @ Leftover samples to be processed

LOOP_8X_SAMPLES:                   @ Multiple of 8 samples
  vld1.16    {d20, d21}, [r7]!     @ x[0, ...]
  vld1.16    {d22, d23}, [r6]!     @ x[i, ...]
  vmull.s16  q12, d20, d22
  vmull.s16  q13, d21, d23
  subs       r8, #1
  vpadal.s32 q8, q12
  vpadal.s32 q9, q13
  bgt        LOOP_8X_SAMPLES

  cmp r12, #4
  blt REST_SAMPLES

Four_SAMPLES:
  vld1.16    d20, [r7]!
  vld1.16    d22, [r6]!
  vmull.s16  q12, d20, d22
  vpadal.s32 q8, q12
  sub r12, #4

REST_SAMPLES:
  mov        r8, #0                @ Initialize lower word of the accumulation.
  mov        r4, #0                @ Initialize upper word of the accumulation.
  cmp r12, #0
  ble SUMUP

LOOP_REST_SAMPLES:
  ldrh       r9, [r7], #2          @ x[0, ...]
  ldrh       r10, [r6], #2         @ x[i, ...]
  smulbb     r11, r9, r10
  adds       r8, r8, r11           @ lower word of the accumulation.
  adc        r4, r4, r11, asr #31  @ upper word of the accumulation.
  subs       r12, #1
  bgt        LOOP_REST_SAMPLES

@ Added the multiplication results together and do a shift.
SUMUP:
  vadd.i64   d16, d17
  vadd.i64   d18, d19
  vadd.i64   d18, d16
  vmov       d17, r8, r4
  vadd.i64   d18, d17
  vshl.s64   d18, d29              @ Shift left by (-scale).
  vst1.32    d18[0], [r0]!         @ r[i]

  add        r5, #1
  cmp        r5, r3
  ble        LOOP_R

  vneg.s32   d29, d29              @ Get value for 'scale'.
  ldr        r2, [sp, #40]         @ &scale
  add        r0, r3, #1            @ return (order + 1)
  vst1.s16   d29[0], [r2]          @ Store 'scale'

  pop        {r3 - r12}
  bx         lr
