@
@ Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ cross_correlation_neon.s
@ This file contains the function WebRtcSpl_CrossCorrelationNeon(),
@ optimized for ARM Neon platform.
@
@ Reference Ccode at end of this file.
@ Output is bit-exact with the reference C code, but not with the generic
@ C code in file cross_correlation.c, due to reduction of shift operations
@ from using Neon registers.

@ Register usage:
@
@ r0: *cross_correlation (function argument)
@ r1: *seq1 (function argument)
@ r2: *seq2 (function argument)
@ r3: dim_seq (function argument); then, total iteration of LOOP_DIM_SEQ
@ r4: counter for LOOP_DIM_CROSS_CORRELATION
@ r5: seq2_ptr
@ r6: seq1_ptr
@ r7: Total iteration of LOOP_DIM_SEQ_RESIDUAL
@ r8, r9, r10, r11, r12: scratch

#include "webrtc/system_wrappers/interface/asm_defines.h"

GLOBAL_FUNCTION WebRtcSpl_CrossCorrelationNeon
.align  2
DEFINE_FUNCTION WebRtcSpl_CrossCorrelationNeon
  push {r4-r11}

  @ Put the shift value (-right_shifts) into a Neon register.
  ldrsh r10, [sp, #36]
  rsb r10, r10, #0
  mov r8, r10, asr #31
  vmov d16, r10, r8

  @ Initialize loop counters.
  and r7, r3, #7              @ inner_loop_len2 = dim_seq % 8;
  asr r3, r3, #3              @ inner_loop_len1 = dim_seq / 8;
  ldrsh r4, [sp, #32]         @ dim_cross_correlation

LOOP_DIM_CROSS_CORRELATION:
  vmov.i32 q9, #0
  vmov.i32 q14, #0
  movs r8, r3                 @ inner_loop_len1
  mov r6, r1                  @ seq1_ptr
  mov r5, r2                  @ seq2_ptr
  ble POST_LOOP_DIM_SEQ

LOOP_DIM_SEQ:
  vld1.16 {d20, d21}, [r6]!   @ seq1_ptr
  vld1.16 {d22, d23}, [r5]!   @ seq2_ptr
  subs r8, r8, #1
  vmull.s16 q12, d20, d22
  vmull.s16 q13, d21, d23
  vpadal.s32 q9, q12
  vpadal.s32 q14, q13
  bgt LOOP_DIM_SEQ

POST_LOOP_DIM_SEQ:
  movs r10, r7                @ Loop counter
  mov r12, #0
  mov r8, #0
  ble POST_LOOP_DIM_SEQ_RESIDUAL

LOOP_DIM_SEQ_RESIDUAL:
  ldrh r11, [r6], #2
  ldrh r9, [r5], #2
  smulbb r11, r11, r9
  adds r8, r8, r11
  adc r12, r12, r11, asr #31
  subs r10, #1
  bgt LOOP_DIM_SEQ_RESIDUAL

POST_LOOP_DIM_SEQ_RESIDUAL:   @ Sum the results up and do the shift.
  vadd.i64 d18, d19
  vadd.i64 d28, d29
  vadd.i64 d18, d28
  vmov.32 d17[0], r8
  vmov.32 d17[1], r12
  vadd.i64 d17, d18
  vshl.s64 d17, d16
  vst1.32 d17[0], [r0]!       @ Store the output

  ldr r8, [sp, #40]           @ step_seq2
  add r2, r8, lsl #1          @ prepare for seq2_ptr(r5) in the next loop.

  subs r4, #1
  bgt LOOP_DIM_CROSS_CORRELATION

  pop {r4-r11}
  bx  lr

@ TODO(kma): Place this piece of reference code into a C code file.
@ void WebRtcSpl_CrossCorrelationNeon(int32_t* cross_correlation,
@                                     int16_t* seq1,
@                                     int16_t* seq2,
@                                     int16_t dim_seq,
@                                     int16_t dim_cross_correlation,
@                                     int16_t right_shifts,
@                                     int16_t step_seq2) {
@   int i = 0;
@   int j = 0;
@   int inner_loop_len1 = dim_seq >> 3;
@   int inner_loop_len2 = dim_seq - (inner_loop_len1 << 3);
@
@   assert(dim_cross_correlation > 0);
@   assert(dim_seq > 0);
@
@   for (i = 0; i < dim_cross_correlation; i++) {
@     int16_t *seq1_ptr = seq1;
@     int16_t *seq2_ptr = seq2 + (step_seq2 * i);
@     int64_t sum = 0;
@
@     for (j = inner_loop_len1; j > 0; j -= 1) {
@       sum += WEBRTC_SPL_MUL_16_16(*seq1_ptr, *seq2_ptr);
@       seq1_ptr++;
@       seq2_ptr++;
@       sum += WEBRTC_SPL_MUL_16_16(*seq1_ptr, *seq2_ptr);
@       seq1_ptr++;
@       seq2_ptr++;
@       sum += WEBRTC_SPL_MUL_16_16(*seq1_ptr, *seq2_ptr);
@       seq1_ptr++;
@       seq2_ptr++;
@       sum += WEBRTC_SPL_MUL_16_16(*seq1_ptr, *seq2_ptr);
@       seq1_ptr++;
@       seq2_ptr++;
@       sum += WEBRTC_SPL_MUL_16_16(*seq1_ptr, *seq2_ptr);
@       seq1_ptr++;
@       seq2_ptr++;
@       sum += WEBRTC_SPL_MUL_16_16(*seq1_ptr, *seq2_ptr);
@       seq1_ptr++;
@       seq2_ptr++;
@       sum += WEBRTC_SPL_MUL_16_16(*seq1_ptr, *seq2_ptr);
@       seq1_ptr++;
@       seq2_ptr++;
@       sum += WEBRTC_SPL_MUL_16_16(*seq1_ptr, *seq2_ptr);
@       seq1_ptr++;
@       seq2_ptr++;
@     }
@
@     // Calculate the rest of the samples.
@     for (j = inner_loop_len2; j > 0; j -= 1) {
@       sum += WEBRTC_SPL_MUL_16_16(*seq1_ptr, *seq2_ptr);
@       seq1_ptr++;
@       seq2_ptr++;
@     }
@
@     *cross_correlation++ = (int32_t)(sum >> right_shifts);
@   }
@ }
