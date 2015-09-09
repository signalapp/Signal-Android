@
@ Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ nsx_core_neon.s
@ This file contains some functions in NS, optimized for ARM Neon
@ platforms. Reference C code is in file nsx_core.c. Bit-exact.

.syntax unified

#include "nsx_core_neon_offsets.h"
#include "webrtc/modules/audio_processing/ns/nsx_defines.h"
#include "webrtc/system_wrappers/interface/asm_defines.h"

GLOBAL_FUNCTION WebRtcNsx_NoiseEstimationNeon
GLOBAL_FUNCTION WebRtcNsx_PrepareSpectrumNeon
GLOBAL_FUNCTION WebRtcNsx_SynthesisUpdateNeon
GLOBAL_FUNCTION WebRtcNsx_AnalysisUpdateNeon
GLOBAL_LABEL WebRtcNsx_kLogTable
GLOBAL_LABEL WebRtcNsx_kCounterDiv
GLOBAL_LABEL WebRtcNsx_kLogTableFrac

.align 2
WebRtcNsx_kLogTableFrac:
_WebRtcNsx_kLogTableFrac:
.short 0, 1, 3, 4, 6, 7, 9, 10, 11, 13, 14, 16, 17, 18, 20, 21, 22, 24, 25, 26
.short 28, 29, 30, 32, 33, 34, 36, 37, 38, 40, 41, 42, 44, 45, 46, 47, 49, 50
.short 51, 52, 54, 55, 56, 57, 59, 60, 61, 62, 63, 65, 66, 67, 68, 69, 71, 72
.short 73, 74, 75, 77, 78, 79, 80, 81, 82, 84, 85, 86, 87, 88, 89, 90, 92, 93
.short 94, 95, 96, 97, 98, 99, 100, 102, 103, 104, 105, 106, 107, 108, 109, 110
.short 111, 112, 113, 114, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126
.short 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141
.short 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 155
.short 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 169
.short 170, 171, 172, 173, 174, 175, 176, 177, 178, 178, 179, 180, 181, 182, 183
.short 184, 185, 185, 186, 187, 188, 189, 190, 191, 192, 192, 193, 194, 195, 196
.short 197, 198, 198, 199, 200, 201, 202, 203, 203, 204, 205, 206, 207, 208, 208
.short 209, 210, 211, 212, 212, 213, 214, 215, 216, 216, 217, 218, 219, 220, 220
.short 221, 222, 223, 224, 224, 225, 226, 227, 228, 228, 229, 230, 231, 231, 232
.short 233, 234, 234, 235, 236, 237, 238, 238, 239, 240, 241, 241, 242, 243, 244
.short 244, 245, 246, 247, 247, 248, 249, 249, 250, 251, 252, 252, 253, 254, 255
.short 255

.align 2
WebRtcNsx_kCounterDiv:
_WebRtcNsx_kCounterDiv:
.short 32767, 16384, 10923, 8192, 6554, 5461, 4681, 4096, 3641, 3277, 2979
.short 2731, 2521, 2341, 2185, 2048, 1928, 1820, 1725, 1638, 1560, 1489
.short 1425, 1365, 1311, 1260, 1214, 1170, 1130, 1092, 1057, 1024, 993, 964
.short 936, 910, 886, 862, 840, 819, 799, 780, 762, 745, 728, 712, 697, 683
.short 669, 655, 643, 630, 618, 607, 596, 585, 575, 565, 555, 546, 537, 529
.short 520, 512, 504, 496, 489, 482, 475, 468, 462, 455, 449, 443, 437, 431
.short 426, 420, 415, 410, 405, 400, 395, 390, 386, 381, 377, 372, 368, 364
.short 360, 356, 352, 349, 345, 341, 338, 334, 331, 328, 324, 321, 318, 315
.short 312, 309, 306, 303, 301, 298, 295, 293, 290, 287, 285, 282, 280, 278
.short 275, 273, 271, 269, 266, 264, 262, 260, 258, 256, 254, 252, 250, 248
.short 246, 245, 243, 241, 239, 237, 236, 234, 232, 231, 229, 228, 226, 224
.short 223, 221, 220, 218, 217, 216, 214, 213, 211, 210, 209, 207, 206, 205
.short 204, 202, 201, 200, 199, 197, 196, 195, 194, 193, 192, 191, 189, 188
.short 187, 186, 185, 184, 183, 182, 181, 180, 179, 178, 177, 176, 175, 174
.short 173, 172, 172, 171, 170, 169, 168, 167, 166, 165, 165, 164, 163

.align 2
WebRtcNsx_kLogTable:
_WebRtcNsx_kLogTable:
.short  0, 177, 355, 532, 710, 887, 1065, 1242, 1420

@ void NoiseEstimationNeon(NsxInst_t* inst,
@                          uint16_t* magn,
@                          uint32_t* noise,
@                          int16_t* q_noise);

@ Register usage (across major loops of NoiseEstimationNeon()):
@ r0-r3: function arguments, and scratch registers.
@ r4: &inst
@ r5: &noiseEstLogQuantile[]
@ r6: inst->magnLen
@ r7: offset
@ r8: s, the loop counter for the LOOP_SIMULT
@ r9: &inst->noiseEstDensity[]
@ r10: &inst->noiseEstCounter[]
@ r11: countDiv
@ r12: i, the loop counter for LOOP_NOISEESTIMATION_MAGNLEN_INNER

.align 2
DEFINE_FUNCTION WebRtcNsx_NoiseEstimationNeon
  push {r4-r12, r14}          @ Make sure 8-byte stack alignment.
  vpush {d8-d15}
  sub sp, #(16 + (HALF_ANAL_BLOCKL + 3) / 4 * 8)

@ [sp, #0]: logval
@ [sp, #4]: noise
@ [sp, #8]: q_noise
@ [sp, #12]: factor
@ [sp, #16 ~ #(16 + (HALF_ANAL_BLOCKL + 3) / 4 * 8)]: lmagn[HALF_ANAL_BLOCKL]

  str r2, [sp, #4]            @ noise
  str r3, [sp, #8]            @ q_noise
  movw r4, #offset_nsx_normData
  ldr r2, [r0, #offset_nsx_stages]            @ inst->stages
  ldr r4, [r0, r4]            @ inst->normData
  adr r12, WebRtcNsx_kLogTable
  subs r3, r2, r4             @ tabind = inst->stages - inst->normData;
  ldr r5, [r0, #offset_nsx_magnLen]            @ magnLen
  rsblt r3, #0
  lsl r3, #1
  ldrh r3, [r12, r3]          @ logval = WebRtcNsx_kLogTable[tabind];
  add r12, sp, #16            @ lmagn[]
  rsblt r3, #0                @ logval = -WebRtcNsx_kLogTable[-tabind];
  str r3, [sp]
  vdup.16 q15, r3

  adr r9, WebRtcNsx_kLogTableFrac

LOOP_SET_LMAGN:
  ldrh r2, [r1], #2           @ magn[i]
  cmp r2, #0
  strheq r3, [r12], #2        @ lmagn[i] = logval;
  beq CHECK_LMAGN_COUNTER

  clz r6, r2
  mov r4, r6                  @ zeros
  rsb r6, #31
  lsl r2, r4
  ubfx r4, r2, #23, #8
  mov r2, r4, lsl #1
  ldrh r4, [r9, r2]           @ WebRtcNsx_kLogTableFrac[frac]
  add r7, r4, r6, lsl #8      @ log2
  movw r2, #22713             @ log2_const
  smulbb r2, r7, r2
  add r2, r3, r2, lsr #15
  strh r2, [r12], #2          @ lmagn[i]

CHECK_LMAGN_COUNTER:
  subs r5, #1
  bgt LOOP_SET_LMAGN

  movw r3, #21845             @ width_factor
  vdup.16 q5, r3
  vmov.s16 q14, #WIDTH_Q8

  movw r5, #offset_nsx_noiseEstLogQuantile
  movw r7, #offset_nsx_blockIndex
  movw r9, #offset_nsx_noiseEstDensity
  add r5, r0
  ldr r6, [r0, #offset_nsx_magnLen]
  ldr r7, [r0, r7]
  add r9, r0
  cmp r7, #END_STARTUP_LONG
  movw r10, #offset_nsx_noiseEstCounter
  add r10, r0
  movge r7, #FACTOR_Q7
  movlt r7, #FACTOR_Q7_STARTUP
  mov r4, r0
  str r7, [sp, #12]           @ factor
  mov r8, #SIMULT
  mov r7, #0

LOOP_SIMULT:
  ldrsh r1, [r10]             @ inst->noiseEstCounter[s]
  adr r3, WebRtcNsx_kCounterDiv
  mov r11, r1, lsl #1         @ counter
  ldrh r11, [r3, r11]         @ countDiv = WebRtcNsx_kCounterDiv[counter];
  sub r12, r6, #1             @ Loop counter.
  smulbb r3, r1, r11          @ countProd
  vdup.16 q11, r11

  vqrdmulh.s16 q11, q5, q11   @ WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(
                              @   width_factor, countDiv, 15);
  vdup.16 d24, r11
  vdup.16 d25, r3

  ldr r3, [sp, #12]           @ factor
  add r1, sp, #16             @ &lmagn[0]
  vdup.16 q9, r3
  vmov.i16 q13, #512
  vmov.i16 q7, #15
  vmov.i32 q6, #FACTOR_Q16

LOOP_NOISEESTIMATION_MAGNLEN_INNER:
  vld1.16 {q0}, [r9]          @ noiseEstDensity[offset + i]

  @ Compute delta in the next two blocks.
  vclz.i16 q4, q0
  vsub.i16 q4, q4, q7         @ Value of the shift factors; likely negative.
  vmovl.s16 q3, d8
  vmovl.s16 q2, d9

  vshl.s32 q1, q6, q3
  vmovn.i32 d8, q1            @ d8 holds shifted FACTOR_Q16.
  vshl.s32 q1, q6, q2
  vcgt.s16 q3, q0, q13        @ Compare noiseEstDensity to 512.
  vmovn.i32 d9, q1            @ d9 holds shifted FACTOR_Q16.
  vmov.i16 q1, q9
  vbit.s16 q1, q4, q3         @ If bigger than 512, delta = shifted FACTOR_Q16.

  vmull.s16 q8, d3, d24
  vmull.s16 q4, d2, d24
  vshrn.i32 d2, q4, #14
  vshrn.i32 d3, q8, #14

  vrshr.s16 q3, q1, #1
  vrshr.s16 q8, q1, #2
  vmull.s16 q4, d7, d28
  vmull.s16 q3, d6, d28
  vld1.16 {q10}, [r5]         @ inst->noiseEstLogQuantile[offset + i]
  vshrn.i32 d4, q3, #1
  vshrn.i32 d5, q4, #1

  vld1.16 {q3}, [r1]!         @ lmagn[i]
  vsub.i16 q4, q10, q2
  vadd.i16 q8, q10, q8
  vsub.i16 q2, q3, q10
  vmax.s16 q4, q4, q15
  vcgt.s16 q1, q2, #0
  vbit q10, q8, q1
  vbif q10, q4, q1

  vsub.i16 q1, q3, q10
  vst1.16 {q10}, [r5]!        @ inst->noiseEstLogQuantile[offset + i]
  vabs.s16 q4, q1
  vqrdmulh.s16 d2, d0, d25
  vqrdmulh.s16 d3, d1, d25
  vcgt.s16 q4, q14, q4
  vadd.i16 q1, q1, q11
  vbit q0, q1, q4
  subs r12, #8
  vst1.16 {q0}, [r9]!         @ noiseEstDensity[offset + i]
  bgt LOOP_NOISEESTIMATION_MAGNLEN_INNER

@
@ Last iteration over magnitude spectrum.
@

COMPUTE_DELTA:
  ldrsh r2, [r9]              @ inst->noiseEstDensity[offset + i]
  cmp r2, #512
  bgt COMPUTE_DELTA_BIGGER_DENSITY

  movw r2, #offset_nsx_blockIndex
  ldr r0, [r4, r2]
  cmp r0, #END_STARTUP_LONG
  movge r0, #FACTOR_Q7          @ delta
  movlt r0, #FACTOR_Q7_STARTUP  @ delta
  b UPDATE_LOG_QUANTILE_ESTIMATE

COMPUTE_DELTA_BIGGER_DENSITY:
  clz r2, r2
  rsb r0, r2, #31             @ 14 - factor
  mov r2, #FACTOR_Q16
  mov r0, r2, lsr r0          @ FACTOR_Q16 >> (14 - factor)

UPDATE_LOG_QUANTILE_ESTIMATE:
  smulbb r12, r0, r11
  ldrsh r1, [r1]              @ lmagn[i]
  ubfx r12, r12, #14, #16     @ tmp16
  ldrsh r2, [r5]              @ inst->noiseEstLogQuantile[offset + i]
  cmp r1, r2
  bgt UPDATE_LOG_QUANTILE_ESTIMATE_BIGGER_LMAGN

  add r12, #1
  ldr r3, [sp]                @ logval
  mov r0, r12, lsr #1         @ tmp16no1
  mov r12, #3
  smulbb r12, r0, r12         @ tmp16no2
  sub r2, r2, r12, lsr #1
  cmp r3, r2
  ldrgt r2, [sp]
  ldrgt r3, [sp]
  b UPDATE_LOG_QUANTILE_ESTIMATE_STORE

UPDATE_LOG_QUANTILE_ESTIMATE_BIGGER_LMAGN:
  add r3, r12, #2
  add r2, r2, r3, lsr #2

UPDATE_LOG_QUANTILE_ESTIMATE_STORE:
  vmov.s16 r0, d25[0]         @ countProd
  strh r2, [r5]
  add r5, #2                  @ increment &noiseEstLogQuantile[offset + i]

UPDATE_DENSITY_ESTIMATE:
  subs r12, r1, r2
  rsblt r12, #0
  cmp r12, #WIDTH_Q8
  bge UPDATE_DENSITY_ESTIMATE_CHECK_COUNTER

  movw r3, #21845             @ width_factor
  ldrh r12, [r9]              @ inst->noiseEstDensity[offset + i]
  smulbb r2, r3, r11
  smulbb r1, r12, r0
  add r0, r2, #1 << 14        @ Rounding
  add r12, r1, #1 << 14
  mov r1, r12, lsr #15
  add r3, r1, r0, lsr #15
  strh r3, [r9]               @ inst->noiseEstDensity[offset + i]

UPDATE_DENSITY_ESTIMATE_CHECK_COUNTER:
  add r9, #2                  @ updata &noiseEstDensity[offset + i]
  ldrsh r3, [r10]             @ inst->noiseEstCounter[s]
  cmp r3, #END_STARTUP_LONG
  blt POST_UPDATE_DENSITY_ESTIMATE

  movw r2, #offset_nsx_blockIndex
  mov r12, #0
  ldr r2, [r4, r2]
  strh r12, [r10]
  cmp r2, #END_STARTUP_LONG
  blt POST_UPDATE_DENSITY_ESTIMATE

  mov r0, r4
  mov r1, r7
  CALL_FUNCTION UpdateNoiseEstimateNeon

POST_UPDATE_DENSITY_ESTIMATE:
  ldrh r3, [r10]
  add r3, #1
  strh r3, [r10], #2
  subs r8, #1
  add r7, r6                  @ offset += inst->magnLen;
  bgt LOOP_SIMULT

  movw r2, #offset_nsx_blockIndex
  ldr r2, [r4, r2]
  cmp r2, #END_STARTUP_LONG
  bge UPDATE_NOISE

  sub r1, r7, r6
  mov r0, r4
  CALL_FUNCTION UpdateNoiseEstimateNeon

UPDATE_NOISE:
  movw r1, #offset_nsx_noiseEstQuantile
  add r1, r4
  ldr r2, [sp, #4]

@ Initial value of loop counter r6 = inst->magnLen.
LOOP_UPDATE_NOISE:
  ldrsh r0, [r1], #2
  subs r6, #1
  str r0, [r2], #4
  bgt LOOP_UPDATE_NOISE

UPDATE_Q_NOISE:
  movw r2, #offset_nsx_qNoise
  ldr r1, [sp, #8]
  ldrh r2, [r4, r2]
  strh r2, [r1]

  add sp, #(16 + (HALF_ANAL_BLOCKL + 3) / 4 * 8)
  vpop {d8-d15}
  pop {r4-r12, pc}

@ static void UpdateNoiseEstimateNeon(NsxInst_t* inst, int offset);
@ Neon registers touched: q0-q3, q8-q13.
.align 2
DEFINE_FUNCTION UpdateNoiseEstimateNeon
  push {r4, r5, r6, r14}
  mov r5, r0

  vmov.i32 q10, #21
  vmov.i32 q11, #0x1FFFFF
  vmov.i32 q9, #0x200000

  movw r0, #offset_nsx_noiseEstLogQuantile
  movw r6, #offset_nsx_magnLen
  add r0, r5                  @ &inst->noiseEstLogQuantile
  add r4, r0, r1, lsl #1      @ &inst->noiseEstLogQuantile[offset]
  ldrsh r6, [r5, r6]          @ &inst->magnLen

  mov r0, r4
  mov r1, r6
  CALL_FUNCTION WebRtcSpl_MaxValueW16Neon

  sub r12, r6, #1             @ Loop counter: inst->magnLen - 1.

  movw r6, #11819             @ kExp2Const in Q13
  movw r2, #offset_nsx_noiseEstQuantile
  vdup.16 d16, r6
  smulbb r3, r6, r0
  add r0, r3, #1 << 20        @ Round
  movw r1, #offset_nsx_qNoise
  mov r0, r0, lsr #21
  rsb r0, r0, #14             @ 14 - (round(kExp2Const * tmp16) >> 21)
  add r2, r5                  @ &inst->noiseEstQuantile
  vdup.32 q13, r0
  str r0, [r5, r1]

LOOP_UPDATE:
  vld1.16 {d0, d1}, [r4]!     @ &inst->noiseEstLogQuantile[offset + i]
  vmull.s16 q1, d0, d16
  vmull.s16 q0, d1, d16
  vshr.s32 q3, q1, #21
  vshr.s32 q2, q0, #21
  vand q1, q1, q11
  vand q0, q0, q11
  vsub.i32 q3, q3, q10
  vsub.i32 q2, q2, q10
  vorr q1, q1, q9
  vorr q0, q0, q9
  vadd.i32 q3, q3, q13
  vadd.i32 q2, q2, q13
  vshl.s32 q1, q1, q3
  vshl.s32 q0, q0, q2
  vqmovn.s32 d1, q0
  vqmovn.s32 d0, q1
  subs r12, #8
  vst1.16 {d0, d1}, [r2]!
  bgt LOOP_UPDATE

POST_LOOP_MAGNLEN:
  ldrh r1, [r4]
  smulbb r3, r6, r1           @ kExp2Const * ptr_noiseEstLogQuantile[offset + i]
  mov r12, #0x00200000
  bfi r12, r3, #0, #21        @ tmp32no1 = 0x00200000 | (tmp32no2 & 0x001FFFFF);
  rsb r0, #21                 @ 21 - &inst->qNoise
  sub r14, r0, r3, lsr #21    @ -tmp16
  mov r0, r12, lsr r14
  ssat r3, #16, r0
  strh r3, [r2]

  pop {r4, r5, r6, pc}

@ void PrepareSpectrumNeon(NsxInst_t* inst, int16_t* freq_buf);
.align 2
DEFINE_FUNCTION WebRtcNsx_PrepareSpectrumNeon
  push {r4-r9}

  movw r2, #offset_nsx_real
  movw r12, #offset_nsx_noiseSupFilter
  movw r4, #offset_nsx_imag
  movw r5, #offset_nsx_magnLen

  add r2, r0                  @ &inst->real[0]
  add r4, r0                  @ &inst->image[0]
  mov r9, r4                  @ &inst->image[0]
  mov r3, r2                  @ &inst->real[0]
  ldr r5, [r0, r5]            @ inst->magnLen
  add r6, r4, #2              @ &inst->image[1]
  sub r5, #1
  add r12, r0                 @ &inst->noiseSupFilter[0]
  add r5, r2, r5, lsl #1      @ &inst->real[inst->magnLen - 1]

LOOP_MAGNLEN:
  @ Filter the elements.
  vld1.16 {d20, d21}, [r2]    @ inst->real[]
  vld1.16 {d24, d25}, [r12]!  @ inst->noiseSupFilter[]
  vld1.16 {d22, d23}, [r4]    @ inst->imag[]
  vmull.s16 q0, d20, d24
  vmull.s16 q1, d21, d25
  vmull.s16 q2, d22, d24
  vmull.s16 q3, d23, d25
  vshrn.s32 d0, q0, #14
  vshrn.s32 d1, q1, #14
  vshrn.s32 d2, q2, #14
  vshrn.s32 d3, q3, #14
  vst1.16 {d0, d1}, [r2]!
  vst1.16 {d2, d3}, [r4]!
  cmp r2, r5
  bcc LOOP_MAGNLEN

  @ Last two elements to filter:
  ldrh r7, [r2]
  ldrh r8, [r12]
  ldrh r5, [r4]
  smulbb r7, r7, r8
  smulbb r5, r5, r8
  mov r7, r7, lsr #14
  mov r8, r5, lsr #14
  strh r7, [r2]
  strh r8, [r4]

  ldr r5, [r0, #offset_nsx_anaLen2]           @ inst->anaLen2
  ldr r7, [r0, #offset_nsx_anaLen]            @ inst->anaLen
  lsr r5, #3                  @ inst->anaLen2 / 8
  sub r5, #1                  @ Loop counter.

@ Process and write the first 2 samples into freq_buf[].
  ldrh r2, [r3], #2           @ inst->real[0]
  ldrh r0, [r9]               @ inst->imag[0]
  strh r2, [r1], #2           @ Store to freq_buf[0]
  rsb r0, r0, #0
  strh r0, [r1], #2           @ Store to freq_buf[1]. Now r1 -> &freq_buf[2]

@ Process and write (inst->anaLen2 * 4 - 32) samples into freq_buf[].
LOOP_ANALEN2:
  vld1.16 d5, [r6]!     @ inst->imag[], starting from inst->imag[1]
  vld1.16 d7, [r6]!
  vneg.s16 d5, d5
  vld1.16 d4, [r3]!     @ inst->real[], starting from inst->real[1]
  vneg.s16 d7, d7
  vld1.16 d6, [r3]!
  vzip.16 d4, d5
  vzip.16 d6, d7
  subs r5, #1
  vst1.16 {d4, d5, d6, d7}, [r1]!
  bgt LOOP_ANALEN2

@ Process and write 32 samples into freq_buf[]. We need to adjust the pointers
@ to overwrite the 2 starting samples in the back half of the buffer.
  vld1.16 d5, [r6]!     @ inst->imag[], starting from inst->imag[1]
  vld1.16 d7, [r6]!
  vneg.s16 d5, d5
  vld1.16 d4, [r3]!     @ inst->real[], starting from inst->real[1]
  vneg.s16 d7, d7
  vld1.16 d6, [r3]!
  vzip.16 d4, d5
  vzip.16 d6, d7
  vst1.16 {d4, d5, d6, d7}, [r1]

  pop {r4-r9}
  bx r14

@ void SynthesisUpdateNeon(NsxInst_t* inst,
@                          int16_t* out_frame,
@                          int16_t gain_factor);
.align 2
DEFINE_FUNCTION WebRtcNsx_SynthesisUpdateNeon
  push {r4, r5}

  vdup.16 d31, r2

  movw r2, #offset_nsx_anaLen
  movw r4, #offset_nsx_real
  movw r12, #offset_nsx_synthesisBuffer

  ldrsh r5, [r0, r2]          @ inst->anaLen
  add r12, r0                 @ &inst->synthesisBuffer[0];
  ldr r3, [r0, #offset_nsx_window]            @ &inst->window[0]
  add r4, r0                  @ &inst->real[0]
  add r5, r12, r5, lsl #1     @ &inst->synthesisBuffer[inst->anaLen]

  mov r2, r12                 @ &inst->synthesisBuffer[0];

LOOP_SYNTHESIS:
  vld1.16 {d0, d1}, [r4]!     @ inst->real[]
  vld1.16 {d2, d3}, [r3]!     @ inst->window[]
  vld1.16 {d4, d5}, [r2]      @ inst->synthesisBuffer[];
  vmull.s16 q3, d0, d2
  vmull.s16 q8, d1, d3
  vrshrn.i32 d0, q3, #14
  vrshrn.i32 d1, q8, #14
  vmull.s16 q3, d31, d0
  vmull.s16 q8, d31, d1
  vqrshrn.s32 d0, q3, #13
  vqrshrn.s32 d1, q8, #13
  vqadd.s16 d4, d0
  vqadd.s16 d5, d1
  vst1.16 {d4, d5}, [r2]!
  cmp r2, r5
  blt LOOP_SYNTHESIS

POST_LOOP_SYNTHESIS:
  movw r3, #offset_nsx_blockLen10ms
  ldr r2, [r0, r3]
  mov r3, r12                 @ &inst->synthesisBuffer[0];
  add r0, r12, r2, lsl #1     @ &inst->synthesisBuffer[inst->blockLen10ms]

LOOP_BLOCKLEN10MS:
  vld1.16 {q0, q1}, [r3]!     @ inst->synthesisBuffer[];
  cmp r3, r0
  vst1.16 {q0, q1}, [r1]!     @ out_frame[]
  blt LOOP_BLOCKLEN10MS

  cmp r0, r5
  bge POST_LOOP_MEMCPY

LOOP_MEMCPY:
  vld1.16 {q0, q1}, [r0]!     @ inst->synthesisBuffer[i + inst->blockLen10ms]
  cmp r0, r5
  vst1.16 {q0, q1}, [r12]!    @ inst->synthesisBuffer[i]
  blt LOOP_MEMCPY

POST_LOOP_MEMCPY:
  cmp r12, r5
  vmov.i16 q10, #0
  vmov.i16 q11, #0
  bge EXIT_SYNTHESISUPDATE

LOOP_ZEROSARRAY:
  vst1.16 {q10, q11}, [r12]!  @ inst->synthesisBuffer[i + inst->anaLen]
  cmp r12, r5
  blt LOOP_ZEROSARRAY

EXIT_SYNTHESISUPDATE:
  pop {r4, r5}
  bx r14

@ void AnalysisUpdateNeon(NsxInst_t* inst, int16_t* out, int16_t* new_speech);
.align 2
DEFINE_FUNCTION WebRtcNsx_AnalysisUpdateNeon
  push {r4-r6}

  movw r3, #offset_nsx_analysisBuffer
  movw r4, #offset_nsx_anaLen
  movw r12, #offset_nsx_blockLen10ms
  add r3, r0                  @ &inst->analysisBuffer[0]
  ldrsh r4, [r0, r4]          @ inst->anaLen
  ldr r12, [r0, r12]          @ inst->blockLen10ms
  sub r6, r4, r12
  add r6, r3, r6, lsl #1      @ &inst->analysisBuffer[inst->anaLen
                              @     - inst->blockLen10ms]
  cmp r3, r6
  mov r5, r3
  bge POST_LOOP_MEMCPY_1

  add r12, r3, r12, lsl #1    @ &inst->analysisBuffer[inst->blockLen10ms]

LOOP_MEMCPY_1:
  vld1.16 {q10, q11}, [r12]!  @ inst->analysisBuffer[i + inst->blockLen10ms]
  vst1.16 {q10, q11}, [r5]!   @ inst->analysisBuffer[i]
  cmp r5, r6
  blt LOOP_MEMCPY_1

POST_LOOP_MEMCPY_1:
  add r12, r3, r4, lsl #1     @ &inst->analysisBuffer[inst->anaLen]
  cmp r5, r12
  bge POST_LOOP_MEMCPY_2

LOOP_MEMCPY_2:
  vld1.16 {q10, q11}, [r2]!   @ new_speech[i]
  vst1.16 {q10, q11}, [r5]!   @ inst->analysisBuffer[
                              @     i + inst->anaLen - inst->blockLen10ms]
  cmp r5, r12
  blt LOOP_MEMCPY_2

POST_LOOP_MEMCPY_2:
  add r4, r1, r4, lsl #1      @ &out[inst->anaLen]
  cmp r1, r4
  ldr r2, [r0, #offset_nsx_window]            @ &inst->window[0]
  bge POST_LOOP_WINDOW_DATA

LOOP_WINDOW_DATA:
  vld1.16 {d4, d5}, [r3]!     @ inst->analysisBuffer[]
  vld1.16 {d6, d7}, [r2]!     @ inst->window[]
  vmull.s16 q0, d4, d6
  vmull.s16 q1, d5, d7
  vrshrn.i32 d4, q0, #14
  vrshrn.i32 d5, q1, #14
  vst1.16 {d4, d5}, [r1]!     @ out[]
  cmp r1, r4
  blt LOOP_WINDOW_DATA

POST_LOOP_WINDOW_DATA:
  pop {r4-r6}
  bx r14
