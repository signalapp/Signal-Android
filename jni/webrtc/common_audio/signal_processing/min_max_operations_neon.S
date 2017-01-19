@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ This file contains some minimum and maximum functions, optimized for
@ ARM Neon platform. The description header can be found in
@ signal_processing_library.h
@
@ The reference C code is in file min_max_operations.c. Code here is basically
@ a loop unrolling by 8 with Neon instructions. Bit-exact.

#include "webrtc/system_wrappers/interface/asm_defines.h"

GLOBAL_FUNCTION WebRtcSpl_MaxAbsValueW16Neon
GLOBAL_FUNCTION WebRtcSpl_MaxAbsValueW32Neon
GLOBAL_FUNCTION WebRtcSpl_MaxValueW16Neon
GLOBAL_FUNCTION WebRtcSpl_MaxValueW32Neon
GLOBAL_FUNCTION WebRtcSpl_MinValueW16Neon
GLOBAL_FUNCTION WebRtcSpl_MinValueW32Neon

.align  2
@ int16_t WebRtcSpl_MaxAbsValueW16Neon(const int16_t* vector, int length);
DEFINE_FUNCTION WebRtcSpl_MaxAbsValueW16Neon
  mov r2, #-1                 @ Initialize the return value.
  cmp r0, #0
  beq END_MAX_ABS_VALUE_W16
  cmp r1, #0
  ble END_MAX_ABS_VALUE_W16

  cmp r1, #8
  blt LOOP_MAX_ABS_VALUE_W16

  vmov.i16 q12, #0
  sub r1, #8                  @ Counter for loops

LOOP_UNROLLED_BY_8_MAX_ABS_VALUE_W16:
  vld1.16 {q13}, [r0]!
  subs r1, #8
  vabs.s16 q13, q13           @ Note vabs doesn't change the value of -32768.
  vmax.u16 q12, q13           @ Use u16 so we don't lose the value -32768.
  bge LOOP_UNROLLED_BY_8_MAX_ABS_VALUE_W16

  @ Find the maximum value in the Neon registers and move it to r2.
  vmax.u16 d24, d25
  vpmax.u16 d24, d24, d24
  vpmax.u16 d24, d24, d24
  adds r1, #8
  vmov.u16 r2, d24[0]
  beq END_MAX_ABS_VALUE_W16

LOOP_MAX_ABS_VALUE_W16:
  ldrsh r3, [r0], #2
  eor r12, r3, r3, asr #31    @ eor and then sub, to get absolute value.
  sub r12, r12, r3, asr #31
  cmp r2, r12
  movlt r2, r12
  subs r1, #1
  bne LOOP_MAX_ABS_VALUE_W16

END_MAX_ABS_VALUE_W16:
  cmp r2, #0x8000             @ Guard against the case for -32768.
  subeq r2, #1
  mov r0, r2
  bx  lr



@ int32_t WebRtcSpl_MaxAbsValueW32Neon(const int32_t* vector, int length);
DEFINE_FUNCTION WebRtcSpl_MaxAbsValueW32Neon
  cmp r0, #0
  moveq r0, #-1
  beq EXIT                    @ Return -1 for a NULL pointer.
  cmp r1, #0                  @ length
  movle r0, #-1
  ble EXIT                    @ Return -1 if length <= 0.

  vmov.i32 q11, #0
  vmov.i32 q12, #0
  cmp r1, #8
  blt LOOP_MAX_ABS_VALUE_W32

  sub r1, #8                  @ Counter for loops

LOOP_UNROLLED_BY_8_MAX_ABS_VALUE_W32:
  vld1.32 {q13, q14}, [r0]!
  subs r1, #8                 @ Counter for loops
  vabs.s32 q13, q13           @ vabs doesn't change the value of 0x80000000.
  vabs.s32 q14, q14
  vmax.u32 q11, q13           @ Use u32 so we don't lose the value 0x80000000.
  vmax.u32 q12, q14
  bge LOOP_UNROLLED_BY_8_MAX_ABS_VALUE_W32

  @ Find the maximum value in the Neon registers and move it to r2.
  vmax.u32 q12, q11
  vmax.u32 d24, d25
  vpmax.u32 d24, d24, d24
  adds r1, #8
  vmov.u32 r2, d24[0]
  beq END_MAX_ABS_VALUE_W32

LOOP_MAX_ABS_VALUE_W32:
  ldr r3, [r0], #4
  eor r12, r3, r3, asr #31    @ eor and then sub, to get absolute value.
  sub r12, r12, r3, asr #31
  cmp r2, r12
  movcc r2, r12
  subs r1, #1
  bne LOOP_MAX_ABS_VALUE_W32

END_MAX_ABS_VALUE_W32:
  mvn r0, #0x80000000         @ Guard against the case for 0x80000000.
  cmp r2, r0
  movcc r0, r2

EXIT:
  bx  lr

@ int16_t WebRtcSpl_MaxValueW16Neon(const int16_t* vector, int length);
DEFINE_FUNCTION WebRtcSpl_MaxValueW16Neon
  mov r2, #0x8000             @ Initialize the return value.
  cmp r0, #0
  beq END_MAX_VALUE_W16
  cmp r1, #0
  ble END_MAX_VALUE_W16

  vmov.i16 q12, #0x8000
  cmp r1, #8
  blt LOOP_MAX_VALUE_W16

  sub r1, #8                  @ Counter for loops

LOOP_UNROLLED_BY_8_MAX_VALUE_W16:
  vld1.16 {q13}, [r0]!
  subs r1, #8
  vmax.s16 q12, q13
  bge LOOP_UNROLLED_BY_8_MAX_VALUE_W16

  @ Find the maximum value in the Neon registers and move it to r2.
  vmax.s16 d24, d25
  vpmax.s16 d24, d24, d24
  vpmax.s16 d24, d24, d24
  adds r1, #8
  vmov.u16 r2, d24[0]
  beq END_MAX_VALUE_W16

LOOP_MAX_VALUE_W16:
  ldrsh r3, [r0], #2
  cmp r2, r3
  movlt r2, r3
  subs r1, #1
  bne LOOP_MAX_VALUE_W16

END_MAX_VALUE_W16:
  mov r0, r2
  bx  lr

@ int32_t WebRtcSpl_MaxValueW32Neon(const int32_t* vector, int length);
DEFINE_FUNCTION WebRtcSpl_MaxValueW32Neon
  mov r2, #0x80000000         @ Initialize the return value.
  cmp r0, #0
  beq END_MAX_VALUE_W32
  cmp r1, #0
  ble END_MAX_VALUE_W32

  vmov.i32 q11, #0x80000000
  vmov.i32 q12, #0x80000000
  cmp r1, #8
  blt LOOP_MAX_VALUE_W32

  sub r1, #8                  @ Counter for loops

LOOP_UNROLLED_BY_8_MAX_VALUE_W32:
  vld1.32 {q13, q14}, [r0]!
  subs r1, #8
  vmax.s32 q11, q13
  vmax.s32 q12, q14
  bge LOOP_UNROLLED_BY_8_MAX_VALUE_W32

  @ Find the maximum value in the Neon registers and move it to r2.
  vmax.s32 q12, q11
  vpmax.s32 d24, d24, d25
  vpmax.s32 d24, d24, d24
  adds r1, #8
  vmov.s32 r2, d24[0]
  beq END_MAX_VALUE_W32

LOOP_MAX_VALUE_W32:
  ldr r3, [r0], #4
  cmp r2, r3
  movlt r2, r3
  subs r1, #1
  bne LOOP_MAX_VALUE_W32

END_MAX_VALUE_W32:
  mov r0, r2
  bx  lr

@ int16_t WebRtcSpl_MinValueW16Neon(const int16_t* vector, int length);
DEFINE_FUNCTION WebRtcSpl_MinValueW16Neon
  movw r2, #0x7FFF            @ Initialize the return value.
  cmp r0, #0
  beq END_MIN_VALUE_W16
  cmp r1, #0
  ble END_MIN_VALUE_W16

  vmov.i16 q12, #0x7FFF
  cmp r1, #8
  blt LOOP_MIN_VALUE_W16

  sub r1, #8                  @ Counter for loops

LOOP_UNROLLED_BY_8_MIN_VALUE_W16:
  vld1.16 {q13}, [r0]!
  subs r1, #8
  vmin.s16 q12, q13
  bge LOOP_UNROLLED_BY_8_MIN_VALUE_W16

  @ Find the maximum value in the Neon registers and move it to r2.
  vmin.s16 d24, d25
  vpmin.s16 d24, d24, d24
  vpmin.s16 d24, d24, d24
  adds r1, #8
  vmov.s16 r2, d24[0]
  sxth  r2, r2
  beq END_MIN_VALUE_W16

LOOP_MIN_VALUE_W16:
  ldrsh r3, [r0], #2
  cmp r2, r3
  movge r2, r3
  subs r1, #1
  bne LOOP_MIN_VALUE_W16

END_MIN_VALUE_W16:
  mov r0, r2
  bx  lr

@ int32_t WebRtcSpl_MinValueW32Neon(const int32_t* vector, int length);
DEFINE_FUNCTION WebRtcSpl_MinValueW32Neon
  mov r2, #0x7FFFFFFF         @ Initialize the return value.
  cmp r0, #0
  beq END_MIN_VALUE_W32
  cmp r1, #0
  ble END_MIN_VALUE_W32

  vdup.32 q11, r2
  vdup.32 q12, r2
  cmp r1, #8
  blt LOOP_MIN_VALUE_W32

  sub r1, #8                  @ Counter for loops

LOOP_UNROLLED_BY_8_MIN_VALUE_W32:
  vld1.32 {q13, q14}, [r0]!
  subs r1, #8
  vmin.s32 q11, q13
  vmin.s32 q12, q14
  bge LOOP_UNROLLED_BY_8_MIN_VALUE_W32

  @ Find the maximum value in the Neon registers and move it to r2.
  vmin.s32 q12, q11
  vpmin.s32 d24, d24, d25
  vpmin.s32 d24, d24, d24
  adds r1, #8
  vmov.s32 r2, d24[0]
  beq END_MIN_VALUE_W32

LOOP_MIN_VALUE_W32:
  ldr r3, [r0], #4
  cmp r2, r3
  movge r2, r3
  subs r1, #1
  bne LOOP_MIN_VALUE_W32

END_MIN_VALUE_W32:
  mov r0, r2
  bx  lr
