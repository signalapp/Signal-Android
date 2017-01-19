@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ vector_scaling_operations_neon.s
@ This file contains the function WebRtcSpl_ScaleAndAddVectorsWithRoundNeon(),
@ optimized for ARM Neon platform. Output is bit-exact with the reference
@ C code in vector_scaling_operations.c.

#include "webrtc/system_wrappers/interface/asm_defines.h"

GLOBAL_FUNCTION WebRtcSpl_ScaleAndAddVectorsWithRoundNeon
.align  2
DEFINE_FUNCTION WebRtcSpl_ScaleAndAddVectorsWithRoundNeon
  push {r4-r9}

  ldr r4, [sp, #32]           @ length
  ldr r5, [sp, #28]           @ out_vector
  ldrsh r6, [sp, #24]         @ right_shifts

  cmp r4, #0
  ble END                     @ Return if length <= 0.

  cmp r4, #8
  blt SET_ROUND_VALUE

  vdup.16 d26, r1             @ in_vector1_scale
  vdup.16 d27, r3             @ in_vector2_scale

  @ Neon instructions can only right shift by an immediate value. To shift right
  @ by a register value, we have to do a left shift left by the negative value.
  rsb r7, r6, #0
  vdup.16 q12, r7             @ -right_shifts

  bic r7, r4, #7              @ Counter for LOOP_UNROLLED_BY_8: length / 8 * 8.

LOOP_UNROLLED_BY_8:
  vld1.16 {d28, d29}, [r0]!   @ in_vector1[]
  vld1.16 {d30, d31}, [r2]!   @ in_vector2[]
  vmull.s16 q0, d28, d26
  vmull.s16 q1, d29, d26
  vmull.s16 q2, d30, d27
  vmull.s16 q3, d31, d27
  vadd.s32 q0, q2
  vadd.s32 q1, q3
  vrshl.s32 q0, q12           @ Round shift right by right_shifts.
  vrshl.s32 q1, q12
  vmovn.i32 d0, q0            @ Cast to 16 bit values.
  vmovn.i32 d1, q1
  subs r7, #8
  vst1.16 {d0, d1}, [r5]!
  bgt LOOP_UNROLLED_BY_8

  ands r4, #0xFF              @ Counter for LOOP_NO_UNROLLING: length % 8.
  beq END

SET_ROUND_VALUE:
  mov r9, #1
  lsl r9, r6
  lsr r9, #1

LOOP_NO_UNROLLING:
  ldrh  r7, [r0], #2
  ldrh  r8, [r2], #2
  smulbb r7, r7, r1
  smulbb r8, r8, r3
  subs r4, #1
  add r7, r9
  add r7, r8
  asr r7, r6
  strh r7, [r5], #2
  bne LOOP_NO_UNROLLING

END:
  pop {r4-r9}
  bx  lr
