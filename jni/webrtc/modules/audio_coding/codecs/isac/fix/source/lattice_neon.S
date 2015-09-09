@
@ Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
@
@ Use of this source code is governed by a BSD-style license
@ that can be found in the LICENSE file in the root of the source
@ tree. An additional intellectual property rights grant can be found
@ in the file PATENTS.  All contributing project authors may
@ be found in the AUTHORS file in the root of the source tree.
@

@ lattice_neon.s
@
@ Contains a function for the core loop in the normalized lattice MA 
@ filter routine for iSAC codec, optimized for ARM Neon platform.
@ void WebRtcIsacfix_FilterMaLoopNeon(int16_t input0,
@                                     int16_t input1,
@                                     int32_t input2,
@                                     int32_t* ptr0,
@                                     int32_t* ptr1,
@                                     int32_t* __restrict ptr2);
@ It calculates
@   *ptr2 = input2 * (*ptr2) + input0 * (*ptr0));
@   *ptr1 = input1 * (*ptr0) + input0 * (*ptr2);
@ in Q15 domain.
@
@ Reference code in lattice.c.
@ Output is not bit-exact with the reference C code, due to the replacement
@ of WEBRTC_SPL_MUL_16_32_RSFT15 and LATTICE_MUL_32_32_RSFT16 with Neon
@ instructions, smulwb, and smull. Speech quality was not degraded by
@ testing speech and tone vectors.

#include "webrtc/system_wrappers/interface/asm_defines.h"
#include "settings.h"

GLOBAL_FUNCTION WebRtcIsacfix_FilterMaLoopNeon
.align  2
DEFINE_FUNCTION WebRtcIsacfix_FilterMaLoopNeon
  push        {r4-r8}

  vdup.32     d28, r0             @ Initialize Neon register with input0
  vdup.32     d29, r1             @ Initialize Neon register with input1
  vdup.32     d30, r2             @ Initialize Neon register with input2
  ldr         r4, [sp, #20]       @ ptr1
  ldr         r12, [sp, #24]      @ ptr2

  @ Number of loop iterations after unrolling: r5 = (HALF_SUBFRAMELEN - 1) >> 2
  @ Leftover samples after the loop, in r6:
  @    r6 = (HALF_SUBFRAMELEN - 1) - (HALF_SUBFRAMELEN - 1) >> 2 << 2
  mov         r6, #HALF_SUBFRAMELEN
  sub         r6, #1
  lsr         r5, r6, #2
  sub         r6, r5, lsl #2

  @ First r5 iterations in a loop.

LOOP:
  vld1.32     {d0, d1}, [r3]!     @ *ptr0

  vmull.s32   q10, d0, d28        @ tmp32a = input0 * (*ptr0)
  vmull.s32   q11, d1, d28        @ tmp32a = input0 * (*ptr0)
  vmull.s32   q12, d0, d29        @ input1 * (*ptr0)
  vmull.s32   q13, d1, d29        @ input1 * (*ptr0)
                                  
  vrshrn.i64  d4, q10, #15        
  vrshrn.i64  d5, q11, #15        
                                  
  vld1.32     {d2, d3}, [r12]     @ *ptr2
  vadd.i32    q3, q2, q1          @ tmp32b = *ptr2 + tmp32a
                                  
  vrshrn.i64  d0, q12, #15        
                                  
  vmull.s32   q10, d6, d30        @ input2 * (*ptr2 + tmp32b)
  vmull.s32   q11, d7, d30        @ input2 * (*ptr2 + tmp32b)

  vrshrn.i64  d16, q10, #16
  vrshrn.i64  d17, q11, #16

  vmull.s32   q10, d16, d28       @ input0 * (*ptr2)
  vmull.s32   q11, d17, d28       @ input0 * (*ptr2)

  vrshrn.i64  d1, q13, #15
  vrshrn.i64  d18, q10, #15
  vrshrn.i64  d19, q11, #15

  vst1.32     {d16, d17}, [r12]!  @ *ptr2

  vadd.i32    q9, q0, q9
  subs        r5, #1
  vst1.32     {d18, d19}, [r4]!   @ *ptr1

  bgt         LOOP

  @ Check how many samples still need to be processed.
  subs        r6, #2
  blt         LAST_SAMPLE

  @ Process two more samples:
  vld1.32     d0, [r3]!           @ *ptr0

  vmull.s32   q11, d0, d28        @ tmp32a = input0 * (*ptr0)
  vmull.s32   q13, d0, d29        @ input1 * (*ptr0)

  vld1.32     d18, [r12]          @ *ptr2
  vrshrn.i64  d4, q11, #15

  vadd.i32    d7, d4, d18         @ tmp32b = *ptr2 + tmp32a
  vmull.s32   q11, d7, d30        @ input2 * (*ptr2 + tmp32b)
  vrshrn.i64  d16, q11, #16

  vmull.s32   q11, d16, d28       @ input0 * (*ptr2)
  vst1.32     d16, [r12]!         @ *ptr2

  vrshrn.i64  d0, q13, #15
  vrshrn.i64  d19, q11, #15
  vadd.i32    d19, d0, d19

  vst1.32     d19, [r4]!          @ *ptr1

  @ If there's still one more sample, process it here.
LAST_SAMPLE:
  cmp         r6, #1
  bne         END

  @ *ptr2 = input2 * (*ptr2 + input0 * (*ptr0));
  
  ldr         r7, [r3]            @ *ptr0
  ldr         r8, [r12]           @ *ptr2

  smulwb      r5, r7, r0          @ tmp32a = *ptr0 * input0 >> 16
  add         r8, r8, r5, lsl #1  @ tmp32b = *ptr2 + (tmp32a << 1)
  smull       r5, r6, r8, r2      @ tmp32b * input2, in 64 bits
  lsl         r6, #16
  add         r6, r5, lsr #16     @ Only take the middle 32 bits
  str         r6, [r12]           @ Output (*ptr2, as 32 bits)

  @ *ptr1 = input1 * (*ptr0) + input0 * (*ptr2);

  smulwb      r5, r7, r1          @ tmp32a = *ptr0 * input1 >> 16
  smulwb      r6, r6, r0          @ tmp32b = *ptr2 * input0 >> 16
  lsl         r5, r5, #1
  add         r5, r6, lsl #1
  str         r5, [r4]            @ Output (*ptr1)

END:
  pop         {r4-r8}
  bx          lr
