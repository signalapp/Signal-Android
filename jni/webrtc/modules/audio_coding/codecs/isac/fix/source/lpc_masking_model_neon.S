@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ Contains a function for WebRtcIsacfix_CalculateResidualEnergyNeon() in
@ iSAC codec, optimized for ARM Neon platform. Reference code in
@ lpc_masking_model.c.

#include "webrtc/system_wrappers/interface/asm_defines.h"

GLOBAL_FUNCTION WebRtcIsacfix_CalculateResidualEnergyNeon
.align  2

@ int32_t WebRtcIsacfix_CalculateResidualEnergyNeon(int lpc_order,
@                                                   int32_t q_val_corr,
@                                                   int q_val_polynomial,
@                                                   int16_t* a_polynomial,
@                                                   int32_t* corr_coeffs,
@                                                   int* q_val_residual_energy);
DEFINE_FUNCTION WebRtcIsacfix_CalculateResidualEnergyNeon
  push {r4-r11}

  sub r13, r13, #16
  str r1, [r13, #8]
  str r2, [r13, #12]

  mov r4, #1
  vmov.s64 q11, #0            @ Initialize shift_internal.
  vmov.s64 q13, #0            @ Initialize sum64.
  vmov.s64 q10, #0
  vmov.u8 d20[0], r4          @ Set q10 to 1.

  cmp r0, #0
  blt POST_LOOP_I

  add r9, r3, r0, asl #1      @ &a_polynomial[lpc_order]
  mov r6, #0                  @ Loop counter i.
  ldr r11, [r13, #48]
  sub r10, r0, #1
  mov r7, r3                  @ &a_polynomial[0]
  str r9, [r13, #4]

LOOP_I:
  ldr r2, [r11], #4            @ corr_coeffs[i]
  vmov.s64 q15, #0            @ Initialize the sum64_tmp.
  vdup.s32 d25, r2

  cmp r0, r6                  @ Compare lpc_order to i.
  movle r2, r6
  ble POST_LOOP_J

  mov r1, r6                  @ j = i;
  mov r12, r7                  @ &a_polynomial[i]
  mov r4, r3                  @ &a_polynomial[j - i]

LOOP_J:
  ldr r8, [r12], #4
  ldr r5, [r4], #4
  vmov.u32 d0[0], r8
  vmov.u32 d1[0], r5
  vmull.s16 q0, d0, d1
  vmull.s32 q0, d0, d25
  cmp r6, #0                  @ i == 0?
  vshl.s64 q0, q11
  beq SUM1
  vshl.s64 q0, #1

SUM1:
  vqadd.s64 q14, q0, q15      @ Sum and test overflow.
  add r1, r1, #2
  bvc MOV1                    @ Skip the shift if there's no overflow.
  vshr.s64 q0, #1
  vshr.s64 q15, #1
  vadd.s64 q14, q0, q15
  vsub.s64 q11, q10

MOV1:
  cmp r0, r1                  @ Compare lpc_order to j.
  vmov.s64 q15, q14
  bgt LOOP_J

  bic r1, r10, #1
  add r2, r6, #2
  add r2, r1, r2

POST_LOOP_J:
  vqadd.s64 q0, q13, q15      @ Sum and test overflow.
  bvc MOV2                    @ Skip the shift if there's no overflow.
  vshr.s64 q13, #1
  vshr.s64 q15, #1
  vadd.s64 q0, q13, q15
  vsub.s64 q11, q10

MOV2:
  vmov.s64 q13, q0            @ update sum64.
  cmp r2, r0
  bne CHECK_LOOP_CONDITION

  @ Last sample in the inner loop.
  ldr r4, [r13, #4]
  ldrsh r8, [r4]
  ldrsh r12, [r9]
  mul r8, r8, r12
  vmov.s32 d0[0], r8
  vmull.s32 q0, d0, d25
  cmp r6, #0                  @ i == 0?
  vshl.s64 q0, q11
  beq SUM2
  vshl.s64 q0, #1

SUM2:
  vqadd.s64 d1, d0, d26       @ Sum and test overflow.
  bvc MOV3                    @ Skip the shift if there's no overflow.
  vshr.s64 q13, #1
  vshr.s64 d0, #1
  vadd.s64 d1, d0, d26
  vsub.s64 q11, q10

MOV3:
  vmov.s64 d26, d1            @ update sum64.

CHECK_LOOP_CONDITION:
  add r6, r6, #1
  sub r9, r9, #2
  cmp r0, r6                  @ Compare i to lpc_order.
  sub r10, r10, #1
  add r7, r7, #2
  bge LOOP_I

POST_LOOP_I:
  mov r3, #0
  vqadd.s64 d0, d26, d27      @ Sum and test overflow.
  bvc GET_SHIFT_NORM          @ Skip the shift if there's no overflow.
  vshr.s64 q13, #1
  vadd.s64 d0, d26, d27
  vsub.s64 q11, q10

GET_SHIFT_NORM:
  vcls.s32 d1, d0             @ Count leading extra sign bits.
  vmov.32 r2, d1[1]           @ Store # of sign bits of only the 32 MSBs.
  vmovl.s32 q1, d1
  vshl.s64 d0, d3             @ d3 contains # of sign bits of the 32 MSBs.

  vcls.s32 d1, d0             @ Count again the leading extra sign bits.
  vmov.s32 r1, d1[1]          @ Store # of sign bits of only the 32 MSBs.
  vmovl.s32 q1, d1
  vshl.s64 d0, d3             @ d3 contains # of sign bits of the 32 MSBs.

  vmov.s32 r0, d0[1]          @ residual_energy
  vmov.s32 r3, d22[0]         @ shift_internal

  @ Calculate the value for q_val_residual_energy.
  ldr r4, [r13, #8]            @ q_val_corr
  ldr r5, [r13, #12]           @ q_val_polynomial
  sub r12, r4, #32
  add r12, r12, r5, asl #1
  add r1, r12, r1              @ add 1st part of shift_internal.
  add r12, r1, r2              @ add 2nd part of shift_internal.
  ldr r2, [r13, #52]
  add r3, r12, r3              @ value for q_val_residual_energy.
  str r3, [r2, #0]

  add r13, r13, #16
  pop {r4-r11}
  bx  r14


