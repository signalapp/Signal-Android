@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@
@ Reference code in transform.c. Bit not exact due to how rounding is
@ done in C code and ARM instructions, but quality by assembly code is
@ not worse.

#include "webrtc/modules/audio_coding/codecs/isac/fix/source/settings.h"
#include "webrtc/system_wrappers/interface/asm_defines.h"

GLOBAL_FUNCTION WebRtcIsacfix_Spec2TimeNeon
GLOBAL_FUNCTION WebRtcIsacfix_Time2SpecNeon
GLOBAL_LABEL WebRtcIsacfix_kSinTab1
GLOBAL_LABEL WebRtcIsacfix_kCosTab1
GLOBAL_LABEL WebRtcIsacfix_kSinTab2

@ void WebRtcIsacfix_Time2SpecNeon(int16_t* inre1Q9,
@                                  int16_t* inre2Q9,
@                                  int16_t* outreQ7,
@                                  int16_t* outimQ7);

DEFINE_FUNCTION WebRtcIsacfix_Time2SpecNeon
.align  2
  push {r4-r11,lr}
  sub sp, sp, #(16 + FRAMESAMPLES * 4)

  str r0, [sp]                @ inre1Q9
  str r1, [sp, #4]            @ inre2Q9
  str r2, [sp, #8]            @ outreQ7
  str r3, [sp, #12]           @ outimQ7

  mov r8, #(FRAMESAMPLES - 16)
  add r12, r0, r8             @ &inreQ7[FRAMESAMPLES / 2 - 4]
  add r11, r1, r8             @ &inimQ7[FRAMESAMPLES / 2 - 4]
  add r4, sp, #16             @ tmpreQ16;
  add r5, sp, #(16 + FRAMESAMPLES * 2)  @ tmpimQ16;

  adr r9, WebRtcIsacfix_kCosTab1
#if defined(__APPLE__)
  mov r6, #:lower16:(WebRtcIsacfix_kSinTab1 - WebRtcIsacfix_kCosTab1)
#else
  mov r6, #(WebRtcIsacfix_kSinTab1 - WebRtcIsacfix_kCosTab1)
#endif
  add r10, r9, r6             @ WebRtcIsacfix_kSinTab1

  vmov.u32 q14, #0            @ Initialize the maximum values for tmpInIm.
  vmov.u32 q15, #0            @ Initialize the maximum values for tmpInRe.
  movw r6, #16921             @ 0.5 / sqrt(240) in Q19
  lsl r6, #5                  @ Together with vqdmulh, net effect is ">> 26".
  mov r8, #(FRAMESAMPLES / 2) @ loop counter
  vdup.s32 q11, r6

Time2Spec_TransformAndFindMax:
@ Use ">> 26", instead of ">> 7", ">> 16" and then ">> 3" as in the C code.

  subs r8, #8

  vld1.16 {q0}, [r9, :64]!    @ WebRtcIsacfix_kCosTab1[]
  vld1.16 {q2}, [r0]!         @ inre1Q9[]
  vmull.s16 q8, d0, d4        @ WebRtcIsacfix_kCosTab1[k] * inre1Q9[k]
  vld1.16 {q1}, [r10, :64]!   @ WebRtcIsacfix_kSinTab1[]
  vmull.s16 q9, d1, d5        @ WebRtcIsacfix_kCosTab1[k] * inre1Q9[k]
  vld1.16 {q3}, [r1]!         @ inre2Q9[]
  vmlal.s16 q8, d2, d6        @ WebRtcIsacfix_kSinTab1[k] * inre2Q9[k]
  vmlal.s16 q9, d3, d7        @ WebRtcIsacfix_kSinTab1[k] * inre2Q9[k]
  vmull.s16 q12, d0, d6       @ WebRtcIsacfix_kCosTab1[k] * inre2Q9[k]
  vmull.s16 q13, d1, d7       @ WebRtcIsacfix_kCosTab1[k] * inre2Q9[k]
  vmlsl.s16 q12, d2, d4       @ WebRtcIsacfix_kSinTab1[k] * inre1Q9[k]
  vmlsl.s16 q13, d3, d5       @ WebRtcIsacfix_kSinTab1[k] * inre1Q9[k]

  vqdmulh.s32 q0, q8, q11     @ xrQ16 * factQ19
  vqdmulh.s32 q1, q9, q11     @ xrQ16 * factQ19
  vqdmulh.s32 q2, q12, q11    @ xrQ16 * factQ19
  vqdmulh.s32 q3, q13, q11    @ xrQ16 * factQ19

  @ Find the absolute maximum in the vectors and store them.
  vabs.s32 q8, q0
  vabs.s32 q9, q1
  vabs.s32 q12, q2
  vst1.32  {q0, q1}, [r4]!    @ tmpreQ16[k]
  vabs.s32 q13, q3
  vmax.u32 q14, q8            @ Use u32 so we don't lose the value 0x80000000.
  vmax.u32 q15, q12
  vst1.32  {q2, q3}, [r5]!    @ tmpimQ16[k]
  vmax.u32 q15, q13
  vmax.u32 q14, q9            @ Maximum for outre1Q16[].

  bgt Time2Spec_TransformAndFindMax

  @ Find the maximum value in the Neon registers
  vmax.u32 d28, d29
  vmax.u32 d30, d31
  vpmax.u32 d28, d28, d28     @ Both 32 bits words hold the same value tmpInIm.
  vpmax.u32 d30, d30, d30     @ Both 32 bits words hold the same value tmpInRe.
  vmax.s32 d30, d28, d30      @ if (yrQ16 > xrQ16) {xrQ16 = yrQ16};

  ldr r4, [sp]                @ inre1Q9
  vcls.s32  d31, d30          @ sh = WebRtcSpl_NormW32(tmpInRe);
  ldr r5, [sp, #4]            @ inre2Q9
  vmov.i32  d30, #24
  add r6, sp, #16             @ tmpreQ16;
  vsub.s32  d31, d31, d30     @ sh = sh - 24;
  add r7, sp, #(16 + FRAMESAMPLES * 2)  @ tmpimQ16;
  vdup.s32  q8, d31[0]                  @ sh

  mov r8, #(FRAMESAMPLES / 2) @ loop counter

Time2Spec_PreFftShift:
  subs r8, #16

  vld1.32 {q0, q1}, [r6]!     @ tmpreQ16[]
  vrshl.s32 q0, q0, q8
  vld1.32 {q2, q3}, [r6]!     @ tmpreQ16[]
  vrshl.s32 q1, q1, q8
  vld1.32 {q10, q11}, [r7]!   @ tmpimQ16[]
  vrshl.s32 q2, q2, q8
  vld1.32 {q12, q13}, [r7]!   @ tmpimQ16[]
  vrshl.s32 q3, q3, q8
  vrshl.s32 q10, q10, q8
  vrshl.s32 q11, q11, q8
  vrshl.s32 q12, q12, q8
  vrshl.s32 q13, q13, q8

  vmovn.s32 d0, q0
  vmovn.s32 d1, q1
  vmovn.s32 d2, q2
  vmovn.s32 d3, q3
  vmovn.s32 d4, q10
  vmovn.s32 d5, q11
  vmovn.s32 d6, q12
  vmovn.s32 d7, q13

  vst1.16 {q0, q1}, [r4]!     @ inre1Q9[]
  vst1.16 {q2, q3}, [r5]!     @ inre2Q9[]

  bgt Time2Spec_PreFftShift

  vmov.s32 r10, d16[0]        @ Store value of sh.
  ldr r0, [sp]                @ inre1Q9
  ldr r1, [sp, #4]            @ inre2Q9
  mov r2, #-1
  CALL_FUNCTION WebRtcIsacfix_FftRadix16Fastest

  vdup.s32 q8, r10            @ sh
  mov r8, #(FRAMESAMPLES - 8)
  ldr r2, [sp, #8]            @ outreQ7
  ldr r3, [sp, #12]           @ outimQ7
  add r11, r2, r8             @ &outRe1Q16[FRAMESAMPLES / 2 - 4]
  add r12, r3, r8             @ &outim2Q16[FRAMESAMPLES / 2 - 4]
  ldr r6, [sp]                @ inre1Q9
  ldr r7, [sp, #4]            @ inre2Q9
  add r4, r6, r8              @ &inre1Q9[FRAMESAMPLES / 2 - 4]
  add r5, r7, r8              @ &inre2Q9[FRAMESAMPLES / 2 - 4]
  adr r10, WebRtcIsacfix_kSinTab2

  add r9, r10, #(120*2 - 8)   @ &WebRtcIsacfix_kSinTab2[119 - 4]

  vneg.s32 q15, q8            @ -sh
  vmov.i32 q0, #23
  vsub.s32 q15, q15, q0       @ -sh - 23

  mov r8, #(FRAMESAMPLES / 4) @ loop counter

  @ Pre-load variables.
  vld1.16 {d2}, [r4]          @ inre1Q9[FRAMESAMPLES / 2 - 4 - i]
  vld1.16 {d3}, [r5]          @ inre2Q9[FRAMESAMPLES / 2 - 4 - i]
  vld1.16 {d0}, [r6]!         @ inre1Q9
  vld1.16 {d1}, [r7]!         @ inre2Q9

Time2Spec_PostFftTransform:
@ By vshl, we effectively did "<< (-sh - 23)", instead of "<< (-sh)",
@ ">> 14" and then ">> 9" as in the C code.

  vld1.16 {d6}, [r9, :64]     @ kCosTab2[]
  vneg.s16 d6, d6
  vld1.16 {d7}, [r10, :64]!   @ WebRtcIsacfix_kSinTab2[]
  vrev64.16 q1, q1            @ Reverse samples in 2nd half of xrQ16[].
  vqadd.s16 d4, d0, d2        @ xrQ16
  vqsub.s16 d5, d1, d3        @ xiQ16
  vrev64.16 d6, d6

  sub r9, #8                  @ Update pointers for kCosTab2[].
  sub r4, #8                  @ Update pointers for inre1Q9[].
  sub r5, #8                  @ Update pointers for inr22Q9[].
  subs r8, #4                 @ Update loop counter.

  vqadd.s16 d1, d1, d3        @ yrQ16
  vqsub.s16 d0, d2, d0        @ yiQ16

  vmull.s16 q12, d6, d4       @ kCosTab2[k] * xrQ16
  vmlsl.s16 q12, d7, d5       @ WebRtcIsacfix_kSinTab2[k] * xiQ16
  vmull.s16 q13, d7, d4       @ WebRtcIsacfix_kSinTab2[k] * xrQ16
  vmlal.s16 q13, d6, d5       @ kCosTab2[k] * xiQ16
  vmull.s16 q9, d7, d1        @ WebRtcIsacfix_kSinTab2[k] * yrQ16
  vmlal.s16 q9, d6, d0        @ kCosTab2[k] * yiQ16
  vmull.s16 q10, d7, d0       @ WebRtcIsacfix_kSinTab2[k] * yiQ16
  vmlsl.s16 q10, d6, d1       @ kCosTab2[k] * yrQ16

  vshl.s32 q12, q12, q15
  vshl.s32 q13, q13, q15
  vshl.s32 q9, q9, q15
  vshl.s32 q10, q10, q15

  vneg.s32 q8, q9
  vld1.16 {d0}, [r6]!         @ inre1Q9
  vmovn.s32 d24, q12
  vld1.16 {d1}, [r7]!         @ inre2Q9
  vmovn.s32 d25, q13
  vld1.16 {d2}, [r4]          @ inre1Q9[FRAMESAMPLES / 2 - 4 - i]
  vmovn.s32 d5, q10
  vld1.16 {d3}, [r5]          @ inre2Q9[FRAMESAMPLES / 2 - 4 - i]
  vmovn.s32 d4, q8
  vst1.16  {d24}, [r2]!       @ outreQ7[k]
  vrev64.16 q2, q2            @ Reverse the order of the samples.
  vst1.16  {d25}, [r3]!       @ outimQ7[k]
  vst1.16 {d4}, [r11]         @ outreQ7[FRAMESAMPLES / 2 - 1 - k]
  vst1.16 {d5}, [r12]         @ outimQ7[FRAMESAMPLES / 2 - 1 - k]
  sub r11, #8                 @ Update pointers for outreQ7[].
  sub r12, #8                 @ Update pointers for outimQ7[].

  bgt Time2Spec_PostFftTransform

  add sp, sp, #(16 + FRAMESAMPLES * 4)
  pop {r4-r11,pc}

.align  8
@ Cosine table 1 in Q14
WebRtcIsacfix_kCosTab1:
_WebRtcIsacfix_kCosTab1:  @ Label for iOS
  .short 16384, 16383, 16378, 16371, 16362, 16349, 16333, 16315
  .short 16294, 16270, 16244, 16214, 16182, 16147, 16110, 16069
  .short 16026, 15980, 15931, 15880, 15826, 15769, 15709, 15647
  .short 15582, 15515, 15444, 15371, 15296, 15218, 15137, 15053
  .short 14968, 14879, 14788, 14694, 14598, 14500, 14399, 14295
  .short 14189, 14081, 13970, 13856, 13741, 13623, 13502, 13380
  .short 13255, 13128, 12998, 12867, 12733, 12597, 12458, 12318
  .short 12176, 12031, 11885, 11736, 11585, 11433, 11278, 11121
  .short 10963, 10803, 10641, 10477, 10311, 10143, 9974, 9803
  .short 9630, 9456, 9280, 9102, 8923, 8743, 8561, 8377
  .short 8192, 8006, 7818, 7629, 7438, 7246, 7053, 6859
  .short 6664, 6467, 6270, 6071, 5872, 5671, 5469, 5266
  .short 5063, 4859, 4653, 4447, 4240, 4033, 3825, 3616
  .short 3406, 3196, 2986, 2775, 2563, 2351, 2139, 1926
  .short 1713, 1499, 1285, 1072,  857,  643,  429,  214
  .short 0, -214, -429, -643, -857, -1072, -1285, -1499
  .short -1713, -1926, -2139, -2351, -2563, -2775, -2986, -3196
  .short -3406, -3616, -3825, -4033, -4240, -4447, -4653, -4859
  .short -5063, -5266, -5469, -5671, -5872, -6071, -6270, -6467
  .short -6664, -6859, -7053, -7246, -7438, -7629, -7818, -8006
  .short -8192, -8377, -8561, -8743, -8923, -9102, -9280, -9456
  .short -9630, -9803, -9974, -10143, -10311, -10477, -10641, -10803
  .short -10963, -11121, -11278, -11433, -11585, -11736, -11885, -12031
  .short -12176, -12318, -12458, -12597, -12733, -12867, -12998, -13128
  .short -13255, -13380, -13502, -13623, -13741, -13856, -13970, -14081
  .short -14189, -14295, -14399, -14500, -14598, -14694, -14788, -14879
  .short -14968, -15053, -15137, -15218, -15296, -15371, -15444, -15515
  .short -15582, -15647, -15709, -15769, -15826, -15880, -15931, -15980
  .short -16026, -16069, -16110, -16147, -16182, -16214, -16244, -16270
  .short -16294, -16315, -16333, -16349, -16362, -16371, -16378, -16383

.align  8
@ Sine table 2 in Q14
WebRtcIsacfix_kSinTab2:
_WebRtcIsacfix_kSinTab2:  @ Label for iOS
  .short 16384, -16381, 16375, -16367, 16356, -16342, 16325, -16305
  .short 16283, -16257, 16229, -16199, 16165, -16129, 16090, -16048
  .short 16003, -15956, 15906, -15853, 15798, -15739, 15679, -15615
  .short 15549, -15480, 15408, -15334, 15257, -15178, 15095, -15011
  .short 14924, -14834, 14741, -14647, 14549, -14449, 14347, -14242
  .short 14135, -14025, 13913, -13799, 13682, -13563, 13441, -13318
  .short 13192, -13063, 12933, -12800, 12665, -12528, 12389, -12247
  .short 12104, -11958, 11810, -11661, 11509, -11356, 11200, -11042
  .short 10883, -10722, 10559, -10394, 10227, -10059, 9889, -9717
  .short 9543, -9368, 9191, -9013, 8833, -8652, 8469, -8285
  .short 8099, -7912, 7723, -7534, 7342, -7150, 6957, -6762
  .short 6566, -6369, 6171, -5971, 5771, -5570, 5368, -5165
  .short 4961, -4756, 4550, -4344, 4137, -3929, 3720, -3511
  .short 3301, -3091, 2880, -2669, 2457, -2245, 2032, -1819
  .short 1606, -1392, 1179, -965, 750, -536, 322, -107

@ Table kCosTab2 was removed since its data is redundant with kSinTab2.

.align  8
@ Sine table 1 in Q14
WebRtcIsacfix_kSinTab1:
_WebRtcIsacfix_kSinTab1:  @ Label for iOS
  .short 0, 214, 429, 643, 857, 1072, 1285, 1499
  .short 1713, 1926, 2139, 2351, 2563, 2775, 2986, 3196
  .short 3406, 3616, 3825, 4033, 4240, 4447, 4653, 4859
  .short 5063, 5266, 5469, 5671, 5872, 6071, 6270, 6467
  .short 6664, 6859, 7053, 7246, 7438, 7629, 7818, 8006
  .short 8192, 8377, 8561, 8743, 8923, 9102, 9280, 9456
  .short 9630, 9803, 9974, 10143, 10311, 10477, 10641, 10803
  .short 10963, 11121, 11278, 11433, 11585, 11736, 11885, 12031
  .short 12176, 12318, 12458, 12597, 12733, 12867, 12998, 13128
  .short 13255, 13380, 13502, 13623, 13741, 13856, 13970, 14081
  .short 14189, 14295, 14399, 14500, 14598, 14694, 14788, 14879
  .short 14968, 15053, 15137, 15218, 15296, 15371, 15444, 15515
  .short 15582, 15647, 15709, 15769, 15826, 15880, 15931, 15980
  .short 16026, 16069, 16110, 16147, 16182, 16214, 16244, 16270
  .short 16294, 16315, 16333, 16349, 16362, 16371, 16378, 16383
  .short 16384, 16383, 16378, 16371, 16362, 16349, 16333, 16315
  .short 16294, 16270, 16244, 16214, 16182, 16147, 16110, 16069
  .short 16026, 15980, 15931, 15880, 15826, 15769, 15709, 15647
  .short 15582, 15515, 15444, 15371, 15296, 15218, 15137, 15053
  .short 14968, 14879, 14788, 14694, 14598, 14500, 14399, 14295
  .short 14189, 14081, 13970, 13856, 13741, 13623, 13502, 13380
  .short 13255, 13128, 12998, 12867, 12733, 12597, 12458, 12318
  .short 12176, 12031, 11885, 11736, 11585, 11433, 11278, 11121
  .short 10963, 10803, 10641, 10477, 10311, 10143, 9974, 9803
  .short 9630, 9456, 9280, 9102, 8923, 8743, 8561, 8377
  .short 8192, 8006, 7818, 7629, 7438, 7246, 7053, 6859
  .short 6664, 6467, 6270, 6071, 5872, 5671, 5469, 5266
  .short 5063, 4859, 4653, 4447, 4240, 4033, 3825, 3616
  .short 3406, 3196, 2986, 2775, 2563, 2351, 2139, 1926
  .short 1713, 1499, 1285, 1072, 857, 643, 429, 214

@ void WebRtcIsacfix_Spec2TimeNeon(int16_t *inreQ7,
@                                  int16_t *inimQ7,
@                                  int32_t *outre1Q16,
@                                  int32_t *outre2Q16);

DEFINE_FUNCTION WebRtcIsacfix_Spec2TimeNeon
.align  2
  push {r4-r11,lr}

  sub sp, sp, #16
  str r0, [sp]                @ inreQ7
  str r1, [sp, #4]            @ inimQ7
  str r2, [sp, #8]            @ outre1Q16
  str r3, [sp, #12]           @ outre2Q16

  mov r8, #(FRAMESAMPLES - 16)
  add r12, r0, r8             @ &inreQ7[FRAMESAMPLES / 2 - 8]
  add r11, r1, r8             @ &inimQ7[FRAMESAMPLES / 2 - 8]
  add r4, r2, r8, lsl #1      @ &outRe1Q16[FRAMESAMPLES / 2 - 8]
  add r6, r3, r8, lsl #1      @ &outRe2Q16[FRAMESAMPLES / 2 - 8]

  mov r8, #(FRAMESAMPLES / 2) @ loop counter
  adr r10, WebRtcIsacfix_kSinTab2
  add r9, r10, #(120*2 - 16)  @ &WebRtcIsacfix_kSinTab2[119 - 8]

  vpush {q4-q7}

  mov r5, #-32
  mov r7, #-16
  vmov.u32 q6, #0             @ Initialize the maximum values for tmpInIm.
  vmov.u32 q7, #0             @ Initialize the maximum values for tmpInRe.

TransformAndFindMax:
@ Use ">> 5", instead of "<< 9" and then ">> 14" as in the C code.
@ Bit-exact.

  subs r8, #16

  vld1.16 {q0}, [r9, :64]     @ kCosTab2[]
  sub r9, #16
  vld1.16 {q2}, [r0]!         @ inreQ7[]
  vneg.s16 q0, q0
  vld1.16 {q3}, [r1]!         @ inimQ7[]
  vrev64.16 d0, d0
  vrev64.16 d1, d1
  vld1.16 {q1}, [r10, :64]!   @ WebRtcIsacfix_kSinTab2[]
  vswp d0, d1

  vmull.s16 q8, d2, d6        @ WebRtcIsacfix_kSinTab2[k] * inimQ7[k]
  vmull.s16 q9, d3, d7        @ WebRtcIsacfix_kSinTab2[k] * inimQ7[k]
  vmlal.s16 q8, d0, d4        @ kCosTab2[k] * inreQ7[k]
  vmlal.s16 q9, d1, d5        @ kCosTab2[k] * inreQ7[k]
  vmull.s16 q12, d0, d6       @ kCosTab2[k] * inimQ7[k]
  vmull.s16 q13, d1, d7       @ kCosTab2[k] * inimQ7[k]
  vmlsl.s16 q12, d2, d4       @ WebRtcIsacfix_kSinTab2[k] * inreQ7[k]
  vmlsl.s16 q13, d3, d5       @ WebRtcIsacfix_kSinTab2[k] * inreQ7[k]

  vld1.16 {q2}, [r11], r7     @ inimQ7[FRAMESAMPLES / 2 - 8 + i]
  vld1.16 {q3}, [r12], r7     @ inreQ7[FRAMESAMPLES / 2 - 8 + i]

  vrev64.16 q2, q2            @ Reverse the order of the samples
  vrev64.16 q3, q3            @ Reverse the order of the samples

  vmull.s16 q14, d2, d5       @ WebRtcIsacfix_kSinTab2[k] * inimQ7[k]
  vmull.s16 q15, d3, d4       @ WebRtcIsacfix_kSinTab2[k] * inimQ7[k]
  vmlsl.s16 q14, d0, d7       @ q14 -= kCosTab2[k] * inreQ7[k]
  vmlsl.s16 q15, d1, d6       @ q15 -= kCosTab2[k] * inreQ7[k]

  vmull.s16 q10, d0, d5       @ kCosTab2[k] * inimQ7[]
  vmull.s16 q11, d1, d4       @ kCosTab2[k] * inimQ7[]
  vmlal.s16 q10, d2, d7       @ q10 += WebRtcIsacfix_kSinTab2[k] * inreQ7[]
  vmlal.s16 q11, d3, d6       @ q11 += WebRtcIsacfix_kSinTab2[k] * inreQ7[]

  vshr.s32 q8, q8, #5         @ xrQ16
  vshr.s32 q9, q9, #5         @ xrQ16
  vshr.s32 q12, q12, #5       @ xiQ16
  vshr.s32 q13, q13, #5       @ xiQ16
  vshr.s32 q14, q14, #5       @ yiQ16
  vshr.s32 q15, q15, #5       @ yiQ16

  vneg.s32 q10, q10
  vneg.s32 q11, q11

  @ xrQ16 - yiQ16
  vsub.s32 q0, q8, q14
  vsub.s32 q1, q9, q15

  vshr.s32 q10, q10, #5       @ yrQ16
  vshr.s32 q11, q11, #5       @ yrQ16

  @ xrQ16 + yiQ16
  vadd.s32 q3, q8, q14
  vadd.s32 q2, q9, q15

  @ yrQ16 + xiQ16
  vadd.s32 q4, q10, q12
  vadd.s32 q5, q11, q13

  @ yrQ16 - xiQ16
  vsub.s32 q8, q11, q13
  vsub.s32 q9, q10, q12

  @ Reverse the order of the samples
  vrev64.32 q2, q2
  vrev64.32 q3, q3
  vrev64.32 q8, q8
  vrev64.32 q9, q9
  vswp d4, d5
  vswp d6, d7

  vst1.32  {q0, q1}, [r2]!    @ outre1Q16[k]
  vswp d16, d17
  vswp d18, d19
  vst1.32  {q2, q3}, [r4], r5 @ outre1Q16[FRAMESAMPLES / 2 - 1 - k]

  @ Find the absolute maximum in the vectors and store them in q6 and q7.
  vabs.s32 q10, q0
  vabs.s32 q14, q4
  vabs.s32 q11, q1
  vabs.s32 q15, q5
  vabs.s32 q12, q2
  vmax.u32 q6, q10            @ Use u32 so we don't lose the value 0x80000000.
  vmax.u32 q7, q14            @ Maximum for outre2Q16[].
  vabs.s32 q0, q8
  vmax.u32 q6, q11            @ Maximum for outre1Q16[].
  vmax.u32 q7, q15
  vabs.s32 q13, q3
  vmax.u32 q6, q12
  vmax.u32 q7, q0
  vabs.s32 q1, q9
  vst1.32  {q4, q5}, [r3]!    @ outre2Q16[k]
  vst1.32  {q8, q9}, [r6], r5 @ outre2Q16[FRAMESAMPLES / 2 - 1 - k]
  vmax.u32 q6, q13
  vmax.u32 q7, q1

  bgt TransformAndFindMax

  adr r10, WebRtcIsacfix_kSinTab1
#if defined(__APPLE__)
  mov r2, #:lower16:(WebRtcIsacfix_kSinTab1 - WebRtcIsacfix_kCosTab1)
#else
  mov r2, #(WebRtcIsacfix_kSinTab1 - WebRtcIsacfix_kCosTab1)
#endif

  sub r11, r10, r2            @ WebRtcIsacfix_kCosTab1

  @ Find the maximum value in the Neon registers
  vmax.u32 d12, d13
  vmax.u32 d14, d15
  vpmax.u32 d12, d12, d12     @ Both 32 bits words hold the same value tmpInIm.
  vpmax.u32 d14, d14, d14     @ Both 32 bits words hold the same value tmpInRe.
  vmax.s32 d0, d12, d14       @ if (tmpInIm>tmpInRe) tmpInRe = tmpInIm;

  vpop {q4-q7}

  ldr r4, [sp]                @ inreQ7
  vcls.s32  d1, d0            @ sh = WebRtcSpl_NormW32(tmpInRe);
  ldr r5, [sp, #4]            @ inimQ7
  vmov.i32  d0, #24           @ sh = sh-24;
  ldr r6, [sp, #8]            @ outre1Q16
  vsub.s32  d1, d1, d0
  ldr r7, [sp, #12]           @ outre2Q16
  vdup.s32  q8, d1[0]         @ sh

  mov r8, #(FRAMESAMPLES / 2)

PreFftShift:
  subs r8, #16
  vld1.32 {q0, q1}, [r6]!     @ outre1Q16[]
  vld1.32 {q2, q3}, [r6]!     @ outre1Q16[]
  vrshl.s32 q0, q0, q8
  vrshl.s32 q1, q1, q8
  vrshl.s32 q2, q2, q8
  vrshl.s32 q3, q3, q8
  vld1.32 {q10, q11}, [r7]!   @ outre2Q16[]
  vld1.32 {q12, q13}, [r7]!   @ outre2Q16[]
  vrshl.s32 q10, q10, q8
  vrshl.s32 q11, q11, q8
  vrshl.s32 q12, q12, q8
  vrshl.s32 q13, q13, q8

  vmovn.s32 d0, q0
  vmovn.s32 d1, q1
  vmovn.s32 d2, q2
  vmovn.s32 d3, q3
  vmovn.s32 d4, q10
  vmovn.s32 d5, q11
  vmovn.s32 d6, q12
  vmovn.s32 d7, q13

  vst1.16 {q0, q1}, [r4]!     @ inreQ7[]
  vst1.16 {q2, q3}, [r5]!     @ inimQ7[]

  bgt PreFftShift

  vmov.s32 r8, d16[0]         @ Store value of sh.
  ldr r0, [sp]                @ inreQ7
  ldr r1, [sp, #4]            @ inimQ7
  mov r2, #1
  CALL_FUNCTION WebRtcIsacfix_FftRadix16Fastest

  vdup.s32 q8, r8             @ sh
  mov r9, r11                 @ WebRtcIsacfix_kCosTab1
  ldr r4, [sp]                @ inreQ7
  ldr r5, [sp, #4]            @ inimQ7
  ldr r6, [sp, #8]            @ outre1Q16
  ldr r7, [sp, #12]           @ outre2Q16
  mov r8, #(FRAMESAMPLES / 2)
  vneg.s32 q15, q8            @ -sh
  movw r0, #273
  lsl r0, #15                 @ Together with vqdmulh, net effect is ">> 16".
  vdup.s32 q14, r0

PostFftShiftDivide:
  subs r8, #16

  vld1.16 {q0, q1}, [r4]!     @ inreQ7
  vmovl.s16 q10, d0
  vmovl.s16 q11, d1
  vld1.16 {q2, q3}, [r5]!     @ inimQ7
  vmovl.s16 q8, d2
  vmovl.s16 q9, d3

  vshl.s32 q10, q10, q15
  vshl.s32 q11, q11, q15
  vshl.s32 q8, q8, q15
  vshl.s32 q9, q9, q15

  vqdmulh.s32 q10, q10, q14
  vqdmulh.s32 q11, q11, q14
  vqdmulh.s32 q8, q8, q14
  vqdmulh.s32 q9, q9, q14

  vmovl.s16 q0, d4
  vmovl.s16 q1, d5
  vmovl.s16 q2, d6
  vmovl.s16 q3, d7

  vshl.s32 q0, q0, q15
  vshl.s32 q1, q1, q15
  vshl.s32 q2, q2, q15
  vshl.s32 q3, q3, q15

  @ WEBRTC_SPL_MUL_16_32_RSFT16(273, outre2Q16[k])
  vqdmulh.s32 q0, q0, q14
  vqdmulh.s32 q1, q1, q14
  vst1.32 {q10, q11}, [r6]!   @ outre1Q16[]
  vqdmulh.s32 q2, q2, q14
  vqdmulh.s32 q3, q3, q14
  vst1.32 {q8, q9}, [r6]!     @ outre1Q16[]
  vst1.32 {q0, q1}, [r7]!     @ outre2Q16[]
  vst1.32 {q2, q3}, [r7]!     @ outre2Q16[]

  bgt PostFftShiftDivide

  mov r8, #(FRAMESAMPLES / 2)
  ldr r2, [sp, #8]            @ outre1Q16
  ldr r3, [sp, #12]           @ outre2Q16
  movw r0, #31727
  lsl r0, #16                 @ With vqdmulh and vrshrn, net effect is ">> 25".

DemodulateAndSeparate:
  subs r8, #8

  vld1.16 {q0}, [r9, :64]!    @ WebRtcIsacfix_kCosTab1[]
  vmovl.s16 q10, d0           @ WebRtcIsacfix_kCosTab1[]
  vld1.16 {q1}, [r10, :64]!   @ WebRtcIsacfix_kSinTab1[]
  vmovl.s16 q11, d1           @ WebRtcIsacfix_kCosTab1[]
  vld1.32 {q2, q3}, [r2]      @ outre1Q16
  vmovl.s16 q12, d2           @ WebRtcIsacfix_kSinTab1[]
  vld1.32 {q14, q15}, [r3]    @ outre2Q16
  vmovl.s16 q13, d3           @ WebRtcIsacfix_kSinTab1[]

  vmull.s32 q0, d20, d4       @ WebRtcIsacfix_kCosTab1[k] * outre1Q16[k]
  vmull.s32 q1, d21, d5       @ WebRtcIsacfix_kCosTab1[k] * outre1Q16[k]
  vmull.s32 q8, d22, d6       @ WebRtcIsacfix_kCosTab1[k] * outre1Q16[k]
  vmull.s32 q9, d23, d7       @ WebRtcIsacfix_kCosTab1[k] * outre1Q16[k]

  vmlsl.s32 q0, d24, d28      @ += WebRtcIsacfix_kSinTab1[k] * outre2Q16[k]
  vmlsl.s32 q1, d25, d29      @ += WebRtcIsacfix_kSinTab1[k] * outre2Q16[k]
  vmlsl.s32 q8, d26, d30      @ += WebRtcIsacfix_kSinTab1[k] * outre2Q16[k]
  vmlsl.s32 q9, d27, d31      @ += WebRtcIsacfix_kSinTab1[k] * outre2Q16[k]

  vrshrn.s64 d0, q0, #10      @ xrQ16
  vrshrn.s64 d1, q1, #10      @ xrQ16
  vrshrn.s64 d2, q8, #10      @ xrQ16
  vrshrn.s64 d3, q9, #10      @ xrQ16

  vmull.s32 q8, d20, d28      @ WebRtcIsacfix_kCosTab1[k] * outre2Q16[k]
  vmull.s32 q9, d21, d29      @ WebRtcIsacfix_kCosTab1[k] * outre2Q16[k]
  vmull.s32 q14, d22, d30     @ WebRtcIsacfix_kCosTab1[k] * outre2Q16[k]
  vmull.s32 q15, d23, d31     @ WebRtcIsacfix_kCosTab1[k] * outre2Q16[k]

  vmlal.s32 q8, d24, d4       @ += WebRtcIsacfix_kSinTab1[k] * outre1Q16[k]
  vmlal.s32 q9, d25, d5       @ += WebRtcIsacfix_kSinTab1[k] * outre1Q16[k]
  vmlal.s32 q14, d26, d6      @ += WebRtcIsacfix_kSinTab1[k] * outre1Q16[k]
  vmlal.s32 q15, d27, d7      @ += WebRtcIsacfix_kSinTab1[k] * outre1Q16[k]

  vdup.s32 q11, r0            @ generic -> Neon doesn't cost extra cycles.

  vrshrn.s64 d24, q8, #10     @ xiQ16
  vrshrn.s64 d25, q9, #10     @ xiQ16
  vqdmulh.s32 q0, q0, q11
  vrshrn.s64 d26, q14, #10    @ xiQ16
  vrshrn.s64 d27, q15, #10    @ xiQ16

  @ WEBRTC_SPL_MUL_16_32_RSFT11(factQ11, xrQ16)
  @ WEBRTC_SPL_MUL_16_32_RSFT11(factQ11, xiQ16)

  vqdmulh.s32 q1, q1, q11
  vqdmulh.s32 q2, q12, q11
  vqdmulh.s32 q3, q13, q11

  vst1.16 {q0, q1}, [r2]!     @ outre1Q16[]
  vst1.16 {q2, q3}, [r3]!     @ outre2Q16[]

  bgt DemodulateAndSeparate

  add sp, sp, #16
  pop {r4-r11,pc}
