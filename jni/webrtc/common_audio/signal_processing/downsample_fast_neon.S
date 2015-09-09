@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ This file contains the function WebRtcSpl_DownsampleFastNeon(), optimized for
@ ARM Neon platform. The description header can be found in
@ signal_processing_library.h
@
@ The reference C code is in file downsample_fast.c. Bit-exact.

#include "webrtc/system_wrappers/interface/asm_defines.h"

GLOBAL_FUNCTION WebRtcSpl_DownsampleFastNeon
.align  2
DEFINE_FUNCTION WebRtcSpl_DownsampleFastNeon
  push {r4-r11}

  cmp r3, #0                                @ data_out_length <= 0?
  movle r0, #-1
  ble END

  ldrsh r12, [sp, #44]
  ldr r5, [sp, #40]                         @ r5: factor
  add r4, r12, #1                           @ r4: delay + 1
  sub r3, r3, #1                            @ r3: data_out_length - 1
  smulbb r3, r5, r3
  ldr r8, [sp, #32]                         @ &coefficients[0]
  mov r9, r12                               @ Iteration counter for outer loops.
  add r3, r4                                @ delay + factor * (out_length-1) +1

  cmp r3, r1                                @ data_in_length < endpos?
  movgt r0, #-1
  bgt END

  @ Initializations.
  sub r3, r5, asl #3
  add r11, r0, r12, asl #1                  @ &data_in[delay]
  ldr r0, [sp, #36]                         @ coefficients_length
  add r3, r5                                @ endpos - factor * 7

  cmp r0, #0                                @ coefficients_length <= 0 ?
  movle r0, #-1
  ble END

  add r8, r0, asl #1                        @ &coeffieient[coefficients_length]
  cmp r9, r3
  bge POST_LOOP_ENDPOS                      @ branch when Iteration < 8 times.

@
@ First part, unroll the loop 8 times, with 3 subcases (factor == 2, 4, others)
@
  mov r4, #-2

  @ Direct program flow to the right channel.

  @ r10 is an offset to &data_in[] in the loop. After an iteration, we need to
  @ move the pointer back to original after advancing 16 bytes by a vld1, and
  @ then move 2 bytes forward to increment one more sample.
  cmp r5, #2
  moveq r10, #-14
  beq LOOP_ENDPOS_FACTOR2                   @ Branch when factor == 2

  @ Similar here, for r10, we need to move the pointer back to original after
  @ advancing 32 bytes, then move 2 bytes forward to increment one sample.
  cmp r5, #4
  moveq r10, #-30
  beq LOOP_ENDPOS_FACTOR4                   @ Branch when factor == 4

  @ For r10, we need to move the pointer back to original after advancing
  @ (factor * 7 * 2) bytes, then move 2 bytes forward to increment one sample.
  mov r10, r5, asl #4
  rsb r10, #2
  add r10, r5, asl #1
  lsl r5, #1                                @ r5 = factor * sizeof(data_in)

@ The general case (factor != 2 && factor != 4)
LOOP_ENDPOS_GENERAL:
  @ Initializations.
  vmov.i32 q2, #2048
  vmov.i32 q3, #2048
  sub r7, r8, #2
  sub r12, r0, #1                           @ coefficients_length - 1
  sub r1, r11, r12, asl #1                  @ &data_in[i - j]

LOOP_COEFF_LENGTH_GENERAL:
  vld1.16 {d2[], d3[]}, [r7], r4            @ coefficients[j]
  vld1.16 d0[0], [r1], r5                   @ data_in[i - j]
  vld1.16 d0[1], [r1], r5                   @ data_in[i + factor - j]
  vld1.16 d0[2], [r1], r5                   @ data_in[i + factor * 2 - j]
  vld1.16 d0[3], [r1], r5                   @ data_in[i + factor * 3 - j]
  vld1.16 d1[0], [r1], r5                   @ data_in[i + factor * 4 - j]
  vld1.16 d1[1], [r1], r5                   @ data_in[i + factor * 5 - j]
  vld1.16 d1[2], [r1], r5                   @ data_in[i + factor * 6 - j]
  vld1.16 d1[3], [r1], r10                  @ data_in[i + factor * 7 - j]
  subs r12, #1
  vmlal.s16 q2, d0, d2
  vmlal.s16 q3, d1, d3
  bge LOOP_COEFF_LENGTH_GENERAL

  @ Shift, saturate, and store the result.
  vqshrn.s32 d0, q2, #12
  vqshrn.s32 d1, q3, #12
  vst1.16 {d0, d1}, [r2]!

  add r11, r5, asl #3                       @ r11 -> &data_in[i + factor * 8]
  add r9, r5, asl #2                        @ Counter i = delay + factor * 8.
  cmp r9, r3                                @ i < endpos - factor * 7 ?
  blt LOOP_ENDPOS_GENERAL
  asr r5, #1                                @ Restore r5 to the value of factor.
  b POST_LOOP_ENDPOS

@ The case for factor == 2.
LOOP_ENDPOS_FACTOR2:
  @ Initializations.
  vmov.i32 q2, #2048
  vmov.i32 q3, #2048
  sub r7, r8, #2
  sub r12, r0, #1                           @ coefficients_length - 1
  sub r1, r11, r12, asl #1                  @ &data_in[i - j]

LOOP_COEFF_LENGTH_FACTOR2:
  vld1.16 {d16[], d17[]}, [r7], r4          @ coefficients[j]
  vld2.16 {d0, d1}, [r1]!                   @ data_in[]
  vld2.16 {d2, d3}, [r1], r10               @ data_in[]
  subs r12, #1
  vmlal.s16 q2, d0, d16
  vmlal.s16 q3, d2, d17
  bge LOOP_COEFF_LENGTH_FACTOR2

  @ Shift, saturate, and store the result.
  vqshrn.s32 d0, q2, #12
  vqshrn.s32 d1, q3, #12
  vst1.16 {d0, d1}, [r2]!

  add r11, r5, asl #4                       @ r11 -> &data_in[i + factor * 8]
  add r9, r5, asl #3                        @ Counter i = delay + factor * 8.
  cmp r9, r3                                @ i < endpos - factor * 7 ?
  blt LOOP_ENDPOS_FACTOR2
  b POST_LOOP_ENDPOS

@ The case for factor == 4.
LOOP_ENDPOS_FACTOR4:
  @ Initializations.
  vmov.i32 q2, #2048
  vmov.i32 q3, #2048
  sub r7, r8, #2
  sub r12, r0, #1                           @ coefficients_length - 1
  sub r1, r11, r12, asl #1                  @ &data_in[i - j]

LOOP_COEFF_LENGTH_FACTOR4:
  vld1.16 {d16[], d17[]}, [r7], r4          @ coefficients[j]
  vld4.16 {d0, d1, d2, d3}, [r1]!           @ data_in[]
  vld4.16 {d18, d19, d20, d21}, [r1], r10   @ data_in[]
  subs r12, #1
  vmlal.s16 q2, d0, d16
  vmlal.s16 q3, d18, d17
  bge LOOP_COEFF_LENGTH_FACTOR4

  add r11, r5, asl #4                       @ r11 -> &data_in[i + factor * 8]
  add r9, r5, asl #3                        @ Counter i = delay + factor * 8.

  @ Shift, saturate, and store the result.
  vqshrn.s32 d0, q2, #12
  vqshrn.s32 d1, q3, #12
  cmp r9, r3                                @ i < endpos - factor * 7 ?
  vst1.16 {d0, d1}, [r2]!

  blt LOOP_ENDPOS_FACTOR4

@
@ Second part, do the rest iterations (if any).
@

POST_LOOP_ENDPOS:
  add r3, r5, asl #3
  sub r3, r5                                @ Restore r3 to endpos.
  cmp r9, r3
  movge r0, #0
  bge END

LOOP2_ENDPOS:
  @ Initializations.
  mov r7, r8
  sub r12, r0, #1                           @ coefficients_length - 1
  sub r6, r11, r12, asl #1                  @ &data_in[i - j]

  mov r1, #2048

LOOP2_COEFF_LENGTH:
  ldrsh r4, [r7, #-2]!                      @ coefficients[j]
  ldrsh r10, [r6], #2                       @ data_in[i - j]
  smlabb r1, r4, r10, r1
  subs r12, #1
  bge LOOP2_COEFF_LENGTH

  @ Shift, saturate, and store the result.
  ssat r1, #16, r1, asr #12
  strh r1, [r2], #2

  add r11, r5, asl #1                       @ r11 -> &data_in[i + factor]
  add r9, r5                                @ Counter i = delay + factor.
  cmp r9, r3                                @ i < endpos?
  blt LOOP2_ENDPOS

  mov r0, #0

END:
  pop {r4-r11}
  bx  lr
