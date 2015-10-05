@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ Contains a function for WebRtcIsacfix_AllpassFilter2FixDec16Neon()
@ in iSAC codec, optimized for ARM Neon platform. Bit exact with function
@ WebRtcIsacfix_AllpassFilter2FixDec16Neon() in filterbanks.c. Prototype
@ C code is at end of this file.

#include "webrtc/system_wrappers/interface/asm_defines.h"

GLOBAL_FUNCTION WebRtcIsacfix_AllpassFilter2FixDec16Neon
.align  2

@void WebRtcIsacfix_AllpassFilter2FixDec16Neon(
@    int16_t *data_ch1,  // Input and output in channel 1, in Q0
@    int16_t *data_ch2,  // Input and output in channel 2, in Q0
@    const int16_t *factor_ch1,  // Scaling factor for channel 1, in Q15
@    const int16_t *factor_ch2,  // Scaling factor for channel 2, in Q15
@    const int length,           // Length of the data buffers
@    int32_t *filter_state_ch1,  // Filter state for channel 1, in Q16
@    int32_t *filter_state_ch2); // Filter state for channel 2, in Q16

DEFINE_FUNCTION WebRtcIsacfix_AllpassFilter2FixDec16Neon
  push {r4 - r7}

  ldr r5, [sp, #24]           @ filter_state_ch2
  ldr r6, [sp, #20]           @ filter_state_ch1

  @ Initialize the Neon registers.
  vld1.16 d0[0], [r0]!        @ data_ch1[0]
  vld1.16 d0[2], [r1]!        @ data_ch2[0]
  vld1.32 d30[0], [r2]        @ factor_ch1[0], factor_ch1[1]
  vld1.32 d30[1], [r3]        @ factor_ch2[0], factor_ch2[1]
  vld1.32 d16[0], [r6]!       @ filter_state_ch1[0]
  vld1.32 d17[0], [r5]!       @ filter_state_ch2[0]
  vneg.s16 d31, d30

  ldr r3, [sp, #16]           @ length
  mov r4, #4                  @ Post offset value for the loop
  mov r2, #-2                 @ Post offset value for the loop
  sub r3, #2                  @ Loop counter

  @ Loop unrolling pre-processing.
  vqdmull.s16 q1, d30, d0
  vshll.s16 q0, d0, #16
  vqadd.s32 q2, q1, q8
  vshrn.i32 d6, q2, #16
  vmull.s16 q1, d31, d6
  vshl.s32 q1, #1
  vqadd.s32 q8, q1, q0
  vld1.32 d16[1], [r6]        @ filter_state_ch1[1]
  vld1.32 d17[1], [r5]        @ filter_state_ch2[1]
  sub r6, #4                  @ &filter_state_ch1[0]
  sub r5, #4                  @ &filter_state_ch2[0]
  vld1.16 d6[1], [r0], r2     @ data_ch1[1]
  vld1.16 d6[3], [r1], r2     @ data_ch2[1]
  vrev32.16 d0, d6

FOR_LOOP:
  vqdmull.s16 q1, d30, d0
  vshll.s16 q0, d0, #16
  vqadd.s32 q2, q1, q8
  vshrn.i32 d4, q2, #16
  vmull.s16 q1, d31, d4
  vst1.16 d4[1], [r0], r4     @ Store data_ch1[n]
  vst1.16 d4[3], [r1], r4     @ Store data_ch2[n]
  vshl.s32 q1, #1
  vld1.16 d4[1], [r0], r2     @ Load data_ch1[n + 2]
  vld1.16 d4[3], [r1], r2     @ Load data_ch2[n + 2]
  vqadd.s32 q8, q1, q0
  vrev32.16 d0, d4
  vqdmull.s16 q1, d30, d0
  subs r3, #2
  vqadd.s32 q2, q1, q8
  vshrn.i32 d6, q2, #16
  vmull.s16 q1, d31, d6
  vshll.s16 q0, d0, #16
  vst1.16 d6[1], [r0], r4     @ Store data_ch1[n + 1]
  vst1.16 d6[3], [r1], r4     @ Store data_ch2[n + 1]
  vshl.s32 q1, #1
  vld1.16 d6[1], [r0], r2     @ Load data_ch1[n + 3]
  vld1.16 d6[3], [r1], r2     @ Load data_ch2[n + 3]
  vqadd.s32 q8, q1, q0
  vrev32.16 d0, d6
  bgt FOR_LOOP

  @ Loop unrolling post-processing.
  vqdmull.s16 q1, d30, d0
  vshll.s16 q0, d0, #16
  vqadd.s32 q2, q1, q8
  vshrn.i32 d4, q2, #16
  vmull.s16 q1, d31, d4
  vst1.16 d4[1], [r0]!        @ Store data_ch1[n]
  vst1.16 d4[3], [r1]!        @ Store data_ch2[n]
  vshl.s32 q1, #1
  vqadd.s32 q8, q1, q0
  vrev32.16 d0, d4
  vqdmull.s16 q1, d30, d0
  vshll.s16 q0, d0, #16
  vqadd.s32 q2, q1, q8
  vshrn.i32 d6, q2, #16
  vmull.s16 q1, d31, d6
  vst1.16 d6[1], [r0]         @ Store data_ch1[n + 1]
  vst1.16 d6[3], [r1]         @ Store data_ch2[n + 1]
  vshl.s32 q1, #1
  vst1.32 d16[0], [r6]!       @ Store filter_state_ch1[0]
  vqadd.s32 q9, q1, q0
  vst1.32 d17[0], [r5]!       @ Store filter_state_ch1[1]
  vst1.32 d18[1], [r6]        @ Store filter_state_ch2[0]
  vst1.32 d19[1], [r5]        @ Store filter_state_ch2[1]

  pop {r4 - r7}
  bx lr

@void AllpassFilter2FixDec16BothChannels(
@    int16_t *data_ch1,  // Input and output in channel 1, in Q0
@    int16_t *data_ch2,  // Input and output in channel 2, in Q0
@    const int16_t *factor_ch1,  // Scaling factor for channel 1, in Q15
@    const int16_t *factor_ch2,  // Scaling factor for channel 2, in Q15
@    const int length,  // Length of the data buffers
@    int32_t *filter_state_ch1,  // Filter state for channel 1, in Q16
@    int32_t *filter_state_ch2) {  // Filter state for channel 2, in Q16
@  int n = 0;
@  int32_t state0_ch1 = filter_state_ch1[0], state1_ch1 = filter_state_ch1[1];
@  int32_t state0_ch2 = filter_state_ch2[0], state1_ch2 = filter_state_ch2[1];
@  int16_t sample0_ch1 = 0, sample0_ch2 = 0;
@  int16_t sample1_ch1 = 0, sample1_ch2  = 0;
@  int32_t a0_ch1 = 0, a0_ch2 = 0;
@  int32_t b0_ch1 = 0, b0_ch2 = 0;
@
@  int32_t a1_ch1 = 0, a1_ch2 = 0;
@  int32_t b1_ch1 = 0, b1_ch2 = 0;
@  int32_t b2_ch1  = 0, b2_ch2 = 0;
@
@  // Loop unrolling preprocessing.
@
@  sample0_ch1 = data_ch1[n];
@  sample0_ch2 = data_ch2[n];
@
@  a0_ch1 = WEBRTC_SPL_MUL_16_16(factor_ch1[0], sample0_ch1) << 1;
@  a0_ch2 = WEBRTC_SPL_MUL_16_16(factor_ch2[0], sample0_ch2) << 1;
@
@  b0_ch1 = WebRtcSpl_AddSatW32(a0_ch1, state0_ch1);
@  b0_ch2 = WebRtcSpl_AddSatW32(a0_ch2, state0_ch2); //Q16+Q16=Q16
@
@  a0_ch1 = WEBRTC_SPL_MUL_16_16(-factor_ch1[0], (int16_t) (b0_ch1 >> 16));
@  a0_ch2 = WEBRTC_SPL_MUL_16_16(-factor_ch2[0], (int16_t) (b0_ch2 >> 16));
@
@  state0_ch1 = WebRtcSpl_AddSatW32(a0_ch1 <<1, (uint32_t)sample0_ch1 << 16);
@  state0_ch2 = WebRtcSpl_AddSatW32(a0_ch2 <<1, (uint32_t)sample0_ch2 << 16);
@
@  sample1_ch1 = data_ch1[n + 1];
@  sample0_ch1 = (int16_t) (b0_ch1 >> 16); //Save as Q0
@  sample1_ch2  = data_ch2[n + 1];
@  sample0_ch2 = (int16_t) (b0_ch2 >> 16); //Save as Q0
@
@
@  for (n = 0; n < length - 2; n += 2) {
@    a1_ch1 = WEBRTC_SPL_MUL_16_16(factor_ch1[0], sample1_ch1) << 1;
@    a0_ch1 = WEBRTC_SPL_MUL_16_16(factor_ch1[1], sample0_ch1) << 1;
@    a1_ch2 = WEBRTC_SPL_MUL_16_16(factor_ch2[0], sample1_ch2 ) << 1;
@    a0_ch2 = WEBRTC_SPL_MUL_16_16(factor_ch2[1], sample0_ch2) << 1;
@
@    b1_ch1 = WebRtcSpl_AddSatW32(a1_ch1, state0_ch1);
@    b0_ch1 = WebRtcSpl_AddSatW32(a0_ch1, state1_ch1); //Q16+Q16=Q16
@    b1_ch2 = WebRtcSpl_AddSatW32(a1_ch2, state0_ch2); //Q16+Q16=Q16
@    b0_ch2 = WebRtcSpl_AddSatW32(a0_ch2, state1_ch2); //Q16+Q16=Q16
@
@    a1_ch1 = WEBRTC_SPL_MUL_16_16(-factor_ch1[0], (int16_t) (b1_ch1 >> 16));
@    a0_ch1 = WEBRTC_SPL_MUL_16_16(-factor_ch1[1], (int16_t) (b0_ch1 >> 16));
@    a1_ch2 = WEBRTC_SPL_MUL_16_16(-factor_ch2[0], (int16_t) (b1_ch2 >> 16));
@    a0_ch2 = WEBRTC_SPL_MUL_16_16(-factor_ch2[1], (int16_t) (b0_ch2 >> 16));
@
@    state0_ch1 = WebRtcSpl_AddSatW32(a1_ch1<<1, (uint32_t)sample1_ch1 <<16);
@    state1_ch1 = WebRtcSpl_AddSatW32(a0_ch1<<1, (uint32_t)sample0_ch1 <<16);
@    state0_ch2 = WebRtcSpl_AddSatW32(a1_ch2<<1, (uint32_t)sample1_ch2 <<16);
@    state1_ch2 = WebRtcSpl_AddSatW32(a0_ch2<<1, (uint32_t)sample0_ch2 <<16);
@
@    sample0_ch1 = data_ch1[n + 2];
@    sample1_ch1 = (int16_t) (b1_ch1 >> 16); //Save as Q0
@    sample0_ch2 = data_ch2[n + 2];
@    sample1_ch2  = (int16_t) (b1_ch2 >> 16); //Save as Q0
@
@    a0_ch1 = WEBRTC_SPL_MUL_16_16(factor_ch1[0], sample0_ch1) << 1;
@    a1_ch1 = WEBRTC_SPL_MUL_16_16(factor_ch1[1], sample1_ch1) << 1;
@    a0_ch2 = WEBRTC_SPL_MUL_16_16(factor_ch2[0], sample0_ch2) << 1;
@    a1_ch2 = WEBRTC_SPL_MUL_16_16(factor_ch2[1], sample1_ch2 ) << 1;
@
@    b2_ch1 = WebRtcSpl_AddSatW32(a0_ch1, state0_ch1);
@    b1_ch1 = WebRtcSpl_AddSatW32(a1_ch1, state1_ch1); //Q16+Q16=Q16
@    b2_ch2 = WebRtcSpl_AddSatW32(a0_ch2, state0_ch2); //Q16+Q16=Q16
@    b1_ch2 = WebRtcSpl_AddSatW32(a1_ch2, state1_ch2); //Q16+Q16=Q16
@
@    a0_ch1 = WEBRTC_SPL_MUL_16_16(-factor_ch1[0], (int16_t) (b2_ch1 >> 16));
@    a1_ch1 = WEBRTC_SPL_MUL_16_16(-factor_ch1[1], (int16_t) (b1_ch1 >> 16));
@    a0_ch2 = WEBRTC_SPL_MUL_16_16(-factor_ch2[0], (int16_t) (b2_ch2 >> 16));
@    a1_ch2 = WEBRTC_SPL_MUL_16_16(-factor_ch2[1], (int16_t) (b1_ch2 >> 16));
@
@    state0_ch1 = WebRtcSpl_AddSatW32(a0_ch1<<1, (uint32_t)sample0_ch1<<16);
@    state1_ch1 = WebRtcSpl_AddSatW32(a1_ch1<<1, (uint32_t)sample1_ch1<<16);
@    state0_ch2 = WebRtcSpl_AddSatW32(a0_ch2<<1, (uint32_t)sample0_ch2<<16);
@    state1_ch2 = WebRtcSpl_AddSatW32(a1_ch2<<1, (uint32_t)sample1_ch2<<16);
@
@
@    sample1_ch1 = data_ch1[n + 3];
@    sample0_ch1 = (int16_t) (b2_ch1  >> 16); //Save as Q0
@    sample1_ch2 = data_ch2[n + 3];
@    sample0_ch2 = (int16_t) (b2_ch2 >> 16); //Save as Q0
@
@    data_ch1[n]     = (int16_t) (b0_ch1 >> 16); //Save as Q0
@    data_ch1[n + 1] = (int16_t) (b1_ch1 >> 16); //Save as Q0
@    data_ch2[n]     = (int16_t) (b0_ch2 >> 16);
@    data_ch2[n + 1] = (int16_t) (b1_ch2 >> 16);
@  }
@
@  // Loop unrolling post-processing.
@
@  a1_ch1 = WEBRTC_SPL_MUL_16_16(factor_ch1[0], sample1_ch1) << 1;
@  a0_ch1 = WEBRTC_SPL_MUL_16_16(factor_ch1[1], sample0_ch1) << 1;
@  a1_ch2 = WEBRTC_SPL_MUL_16_16(factor_ch2[0], sample1_ch2 ) << 1;
@  a0_ch2 = WEBRTC_SPL_MUL_16_16(factor_ch2[1], sample0_ch2) << 1;
@
@  b1_ch1 = WebRtcSpl_AddSatW32(a1_ch1, state0_ch1);
@  b0_ch1 = WebRtcSpl_AddSatW32(a0_ch1, state1_ch1);
@  b1_ch2 = WebRtcSpl_AddSatW32(a1_ch2, state0_ch2);
@  b0_ch2 = WebRtcSpl_AddSatW32(a0_ch2, state1_ch2);
@
@  a1_ch1 = WEBRTC_SPL_MUL_16_16(-factor_ch1[0], (int16_t) (b1_ch1 >> 16));
@  a0_ch1 = WEBRTC_SPL_MUL_16_16(-factor_ch1[1], (int16_t) (b0_ch1 >> 16));
@  a1_ch2 = WEBRTC_SPL_MUL_16_16(-factor_ch2[0], (int16_t) (b1_ch2 >> 16));
@  a0_ch2 = WEBRTC_SPL_MUL_16_16(-factor_ch2[1], (int16_t) (b0_ch2 >> 16));
@
@  state0_ch1 = WebRtcSpl_AddSatW32(a1_ch1<<1, (uint32_t)sample1_ch1 << 16);
@  state1_ch1 = WebRtcSpl_AddSatW32(a0_ch1<<1, (uint32_t)sample0_ch1 << 16);
@  state0_ch2 = WebRtcSpl_AddSatW32(a1_ch2<<1, (uint32_t)sample1_ch2 << 16);
@  state1_ch2 = WebRtcSpl_AddSatW32(a0_ch2<<1, (uint32_t)sample0_ch2 << 16);
@
@  data_ch1[n] = (int16_t) (b0_ch1 >> 16); //Save as Q0
@  data_ch2[n] = (int16_t) (b0_ch2 >> 16);
@
@  sample1_ch1 = (int16_t) (b1_ch1 >> 16); //Save as Q0
@  sample1_ch2  = (int16_t) (b1_ch2 >> 16); //Save as Q0
@
@  a1_ch1 = WEBRTC_SPL_MUL_16_16(factor_ch1[1], sample1_ch1) << 1;
@  a1_ch2 = WEBRTC_SPL_MUL_16_16(factor_ch2[1], sample1_ch2 ) << 1;
@
@  b1_ch1 = WebRtcSpl_AddSatW32(a1_ch1, state1_ch1); //Q16+Q16=Q16
@  b1_ch2 = WebRtcSpl_AddSatW32(a1_ch2, state1_ch2); //Q16+Q16=Q16
@
@  a1_ch1 = WEBRTC_SPL_MUL_16_16(-factor_ch1[1], (int16_t) (b1_ch1 >> 16));
@  a1_ch2 = WEBRTC_SPL_MUL_16_16(-factor_ch2[1], (int16_t) (b1_ch2 >> 16));
@
@  state1_ch1 = WebRtcSpl_AddSatW32(a1_ch1<<1, (uint32_t)sample1_ch1<<16);
@  state1_ch2 = WebRtcSpl_AddSatW32(a1_ch2<<1, (uint32_t)sample1_ch2<<16);
@
@  data_ch1[n + 1] = (int16_t) (b1_ch1 >> 16); //Save as Q0
@  data_ch2[n + 1] = (int16_t) (b1_ch2 >> 16);
@
@  filter_state_ch1[0] = state0_ch1;
@  filter_state_ch1[1] = state1_ch1;
@  filter_state_ch2[0] = state0_ch2;
@  filter_state_ch2[1] = state1_ch2;
@}
