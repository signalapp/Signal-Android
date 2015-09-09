/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/ns/nsx_core.h"

#include <arm_neon.h>
#include <assert.h>

// Constants to compensate for shifting signal log(2^shifts).
const int16_t WebRtcNsx_kLogTable[9] = {
  0, 177, 355, 532, 710, 887, 1065, 1242, 1420
};

const int16_t WebRtcNsx_kCounterDiv[201] = {
  32767, 16384, 10923, 8192, 6554, 5461, 4681, 4096, 3641, 3277, 2979, 2731,
  2521, 2341, 2185, 2048, 1928, 1820, 1725, 1638, 1560, 1489, 1425, 1365, 1311,
  1260, 1214, 1170, 1130, 1092, 1057, 1024, 993, 964, 936, 910, 886, 862, 840,
  819, 799, 780, 762, 745, 728, 712, 697, 683, 669, 655, 643, 630, 618, 607,
  596, 585, 575, 565, 555, 546, 537, 529, 520, 512, 504, 496, 489, 482, 475,
  468, 462, 455, 449, 443, 437, 431, 426, 420, 415, 410, 405, 400, 395, 390,
  386, 381, 377, 372, 368, 364, 360, 356, 352, 349, 345, 341, 338, 334, 331,
  328, 324, 321, 318, 315, 312, 309, 306, 303, 301, 298, 295, 293, 290, 287,
  285, 282, 280, 278, 275, 273, 271, 269, 266, 264, 262, 260, 258, 256, 254,
  252, 250, 248, 246, 245, 243, 241, 239, 237, 236, 234, 232, 231, 229, 228,
  226, 224, 223, 221, 220, 218, 217, 216, 214, 213, 211, 210, 209, 207, 206,
  205, 204, 202, 201, 200, 199, 197, 196, 195, 194, 193, 192, 191, 189, 188,
  187, 186, 185, 184, 183, 182, 181, 180, 179, 178, 177, 176, 175, 174, 173,
  172, 172, 171, 170, 169, 168, 167, 166, 165, 165, 164, 163
};

const int16_t WebRtcNsx_kLogTableFrac[256] = {
  0, 1, 3, 4, 6, 7, 9, 10, 11, 13, 14, 16, 17, 18, 20, 21,
  22, 24, 25, 26, 28, 29, 30, 32, 33, 34, 36, 37, 38, 40, 41, 42,
  44, 45, 46, 47, 49, 50, 51, 52, 54, 55, 56, 57, 59, 60, 61, 62,
  63, 65, 66, 67, 68, 69, 71, 72, 73, 74, 75, 77, 78, 79, 80, 81,
  82, 84, 85, 86, 87, 88, 89, 90, 92, 93, 94, 95, 96, 97, 98, 99,
  100, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 116,
  117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131,
  132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146,
  147, 148, 149, 150, 151, 152, 153, 154, 155, 155, 156, 157, 158, 159, 160,
  161, 162, 163, 164, 165, 166, 167, 168, 169, 169, 170, 171, 172, 173, 174,
  175, 176, 177, 178, 178, 179, 180, 181, 182, 183, 184, 185, 185, 186, 187,
  188, 189, 190, 191, 192, 192, 193, 194, 195, 196, 197, 198, 198, 199, 200,
  201, 202, 203, 203, 204, 205, 206, 207, 208, 208, 209, 210, 211, 212, 212,
  213, 214, 215, 216, 216, 217, 218, 219, 220, 220, 221, 222, 223, 224, 224,
  225, 226, 227, 228, 228, 229, 230, 231, 231, 232, 233, 234, 234, 235, 236,
  237, 238, 238, 239, 240, 241, 241, 242, 243, 244, 244, 245, 246, 247, 247,
  248, 249, 249, 250, 251, 252, 252, 253, 254, 255, 255
};

// Update the noise estimation information.
static void UpdateNoiseEstimateNeon(NsxInst_t* inst, int offset) {
  const int16_t kExp2Const = 11819; // Q13
  int16_t* ptr_noiseEstLogQuantile = NULL;
  int16_t* ptr_noiseEstQuantile = NULL;
  int16x4_t kExp2Const16x4 = vdup_n_s16(kExp2Const);
  int32x4_t twentyOne32x4 = vdupq_n_s32(21);
  int32x4_t constA32x4 = vdupq_n_s32(0x1fffff);
  int32x4_t constB32x4 = vdupq_n_s32(0x200000);

  int16_t tmp16 = WebRtcSpl_MaxValueW16(inst->noiseEstLogQuantile + offset,
                                        inst->magnLen);

  // Guarantee a Q-domain as high as possible and still fit in int16
  inst->qNoise = 14 - (int) WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(kExp2Const,
                                                                 tmp16,
                                                                 21);

  int32x4_t qNoise32x4 = vdupq_n_s32(inst->qNoise);

  for (ptr_noiseEstLogQuantile = &inst->noiseEstLogQuantile[offset],
       ptr_noiseEstQuantile = &inst->noiseEstQuantile[0];
       ptr_noiseEstQuantile < &inst->noiseEstQuantile[inst->magnLen - 3];
       ptr_noiseEstQuantile += 4, ptr_noiseEstLogQuantile += 4) {

    // tmp32no2 = WEBRTC_SPL_MUL_16_16(kExp2Const,
    //                                inst->noiseEstLogQuantile[offset + i]);
    int16x4_t v16x4 = vld1_s16(ptr_noiseEstLogQuantile);
    int32x4_t v32x4B = vmull_s16(v16x4, kExp2Const16x4);

    // tmp32no1 = (0x00200000 | (tmp32no2 & 0x001FFFFF)); // 2^21 + frac
    int32x4_t v32x4A = vandq_s32(v32x4B, constA32x4);
    v32x4A = vorrq_s32(v32x4A, constB32x4);

    // tmp16 = (int16_t) WEBRTC_SPL_RSHIFT_W32(tmp32no2, 21);
    v32x4B = vshrq_n_s32(v32x4B, 21);

    // tmp16 -= 21;// shift 21 to get result in Q0
    v32x4B = vsubq_s32(v32x4B, twentyOne32x4);

    // tmp16 += (int16_t) inst->qNoise;
    // shift to get result in Q(qNoise)
    v32x4B = vaddq_s32(v32x4B, qNoise32x4);

    // if (tmp16 < 0) {
    //   tmp32no1 = WEBRTC_SPL_RSHIFT_W32(tmp32no1, -tmp16);
    // } else {
    //   tmp32no1 = WEBRTC_SPL_LSHIFT_W32(tmp32no1, tmp16);
    // }
    v32x4B = vshlq_s32(v32x4A, v32x4B);

    // tmp16 = WebRtcSpl_SatW32ToW16(tmp32no1);
    v16x4 = vqmovn_s32(v32x4B);

    //inst->noiseEstQuantile[i] = tmp16;
    vst1_s16(ptr_noiseEstQuantile, v16x4);
  }

  // Last iteration:

  // inst->quantile[i]=exp(inst->lquantile[offset+i]);
  // in Q21
  int32_t tmp32no2 = WEBRTC_SPL_MUL_16_16(kExp2Const,
                                          *ptr_noiseEstLogQuantile);
  int32_t tmp32no1 = (0x00200000 | (tmp32no2 & 0x001FFFFF)); // 2^21 + frac

  tmp16 = (int16_t) WEBRTC_SPL_RSHIFT_W32(tmp32no2, 21);
  tmp16 -= 21;// shift 21 to get result in Q0
  tmp16 += (int16_t) inst->qNoise; //shift to get result in Q(qNoise)
  if (tmp16 < 0) {
    tmp32no1 = WEBRTC_SPL_RSHIFT_W32(tmp32no1, -tmp16);
  } else {
    tmp32no1 = WEBRTC_SPL_LSHIFT_W32(tmp32no1, tmp16);
  }
  *ptr_noiseEstQuantile = WebRtcSpl_SatW32ToW16(tmp32no1);
}

// Noise Estimation
void WebRtcNsx_NoiseEstimationNeon(NsxInst_t* inst,
                                   uint16_t* magn,
                                   uint32_t* noise,
                                   int16_t* q_noise) {
  int16_t lmagn[HALF_ANAL_BLOCKL], counter, countDiv;
  int16_t countProd, delta, zeros, frac;
  int16_t log2, tabind, logval, tmp16, tmp16no1, tmp16no2;
  const int16_t log2_const = 22713;
  const int16_t width_factor = 21845;

  int i, s, offset;

  tabind = inst->stages - inst->normData;
  assert(tabind < 9);
  assert(tabind > -9);
  if (tabind < 0) {
    logval = -WebRtcNsx_kLogTable[-tabind];
  } else {
    logval = WebRtcNsx_kLogTable[tabind];
  }

  int16x8_t logval_16x8 = vdupq_n_s16(logval);

  // lmagn(i)=log(magn(i))=log(2)*log2(magn(i))
  // magn is in Q(-stages), and the real lmagn values are:
  // real_lmagn(i)=log(magn(i)*2^stages)=log(magn(i))+log(2^stages)
  // lmagn in Q8
  for (i = 0; i < inst->magnLen; i++) {
    if (magn[i]) {
      zeros = WebRtcSpl_NormU32((uint32_t)magn[i]);
      frac = (int16_t)((((uint32_t)magn[i] << zeros)
                        & 0x7FFFFFFF) >> 23);
      assert(frac < 256);
      // log2(magn(i))
      log2 = (int16_t)(((31 - zeros) << 8)
                       + WebRtcNsx_kLogTableFrac[frac]);
      // log2(magn(i))*log(2)
      lmagn[i] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(log2, log2_const, 15);
      // + log(2^stages)
      lmagn[i] += logval;
    } else {
      lmagn[i] = logval;
    }
  }

  int16x4_t Q3_16x4  = vdup_n_s16(3);
  int16x8_t WIDTHQ8_16x8 = vdupq_n_s16(WIDTH_Q8);
  int16x8_t WIDTHFACTOR_16x8 = vdupq_n_s16(width_factor);

  int16_t factor = FACTOR_Q7;
  if (inst->blockIndex < END_STARTUP_LONG)
    factor = FACTOR_Q7_STARTUP;

  // Loop over simultaneous estimates
  for (s = 0; s < SIMULT; s++) {
    offset = s * inst->magnLen;

    // Get counter values from state
    counter = inst->noiseEstCounter[s];
    assert(counter < 201);
    countDiv = WebRtcNsx_kCounterDiv[counter];
    countProd = (int16_t)WEBRTC_SPL_MUL_16_16(counter, countDiv);

    // quant_est(...)
    int16_t deltaBuff[8];
    int16x4_t tmp16x4_0;
    int16x4_t tmp16x4_1;
    int16x4_t countDiv_16x4 = vdup_n_s16(countDiv);
    int16x8_t countProd_16x8 = vdupq_n_s16(countProd);
    int16x8_t tmp16x8_0 = vdupq_n_s16(countDiv);
    int16x8_t prod16x8 = vqrdmulhq_s16(WIDTHFACTOR_16x8, tmp16x8_0);
    int16x8_t tmp16x8_1;
    int16x8_t tmp16x8_2;
    int16x8_t tmp16x8_3;
    // Initialize tmp16x8_4 to zero to avoid compilaton error.
    int16x8_t tmp16x8_4 = vdupq_n_s16(0);
    int16x8_t tmp16x8_5;
    int32x4_t tmp32x4;

    for (i = 0; i < inst->magnLen - 7; i += 8) {
      // Compute delta.
      // Smaller step size during startup. This prevents from using
      // unrealistic values causing overflow.
      tmp16x8_0 = vdupq_n_s16(factor);
      vst1q_s16(deltaBuff, tmp16x8_0);

      int j;
      for (j = 0; j < 8; j++) {
        if (inst->noiseEstDensity[offset + i + j] > 512) {
          // Get values for deltaBuff by shifting intead of dividing.
          int factor = WebRtcSpl_NormW16(inst->noiseEstDensity[offset + i + j]);
          deltaBuff[j] = (int16_t)(FACTOR_Q16 >> (14 - factor));
        }
      }

      // Update log quantile estimate

      // tmp16 = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(delta, countDiv, 14);
      tmp32x4 = vmull_s16(vld1_s16(&deltaBuff[0]), countDiv_16x4);
      tmp16x4_1 = vshrn_n_s32(tmp32x4, 14);
      tmp32x4 = vmull_s16(vld1_s16(&deltaBuff[4]), countDiv_16x4);
      tmp16x4_0 = vshrn_n_s32(tmp32x4, 14);
      tmp16x8_0 = vcombine_s16(tmp16x4_1, tmp16x4_0); // Keep for several lines.

      // prepare for the "if" branch
      // tmp16 += 2;
      // tmp16_1 = (Word16)(tmp16>>2);
      tmp16x8_1 = vrshrq_n_s16(tmp16x8_0, 2);

      // inst->noiseEstLogQuantile[offset+i] + tmp16_1;
      tmp16x8_2 = vld1q_s16(&inst->noiseEstLogQuantile[offset + i]); // Keep
      tmp16x8_1 = vaddq_s16(tmp16x8_2, tmp16x8_1); // Keep for several lines

      // Prepare for the "else" branch
      // tmp16 += 1;
      // tmp16_1 = (Word16)(tmp16>>1);
      tmp16x8_0 = vrshrq_n_s16(tmp16x8_0, 1);

      // tmp16_2 = (Word16)WEBRTC_SPL_MUL_16_16_RSFT(tmp16_1,3,1);
      tmp32x4 = vmull_s16(vget_low_s16(tmp16x8_0), Q3_16x4);
      tmp16x4_1 = vshrn_n_s32(tmp32x4, 1);

      // tmp16_2 = (Word16)WEBRTC_SPL_MUL_16_16_RSFT(tmp16_1,3,1);
      tmp32x4 = vmull_s16(vget_high_s16(tmp16x8_0), Q3_16x4);
      tmp16x4_0 = vshrn_n_s32(tmp32x4, 1);

      // inst->noiseEstLogQuantile[offset + i] - tmp16_2;
      tmp16x8_0 = vcombine_s16(tmp16x4_1, tmp16x4_0); // keep
      tmp16x8_0 = vsubq_s16(tmp16x8_2, tmp16x8_0);

      // logval is the smallest fixed point representation we can have. Values
      // below that will correspond to values in the interval [0, 1], which
      // can't possibly occur.
      tmp16x8_0 = vmaxq_s16(tmp16x8_0, logval_16x8);

      // Do the if-else branches:
      tmp16x8_3 = vld1q_s16(&lmagn[i]); // keep for several lines
      tmp16x8_5 = vsubq_s16(tmp16x8_3, tmp16x8_2);
      __asm__("vcgt.s16 %q0, %q1, #0"::"w"(tmp16x8_4), "w"(tmp16x8_5));
      __asm__("vbit %q0, %q1, %q2"::
              "w"(tmp16x8_2), "w"(tmp16x8_1), "w"(tmp16x8_4));
      __asm__("vbif %q0, %q1, %q2"::
              "w"(tmp16x8_2), "w"(tmp16x8_0), "w"(tmp16x8_4));
      vst1q_s16(&inst->noiseEstLogQuantile[offset + i], tmp16x8_2);

      // Update density estimate
      // tmp16_1 + tmp16_2
      tmp16x8_1 = vld1q_s16(&inst->noiseEstDensity[offset + i]);
      tmp16x8_0 = vqrdmulhq_s16(tmp16x8_1, countProd_16x8);
      tmp16x8_0 = vaddq_s16(tmp16x8_0, prod16x8);

      // lmagn[i] - inst->noiseEstLogQuantile[offset + i]
      tmp16x8_3 = vsubq_s16(tmp16x8_3, tmp16x8_2);
      tmp16x8_3 = vabsq_s16(tmp16x8_3);
      tmp16x8_4 = vcgtq_s16(WIDTHQ8_16x8, tmp16x8_3);
      __asm__("vbit %q0, %q1, %q2"::
              "w"(tmp16x8_1), "w"(tmp16x8_0), "w"(tmp16x8_4));
      vst1q_s16(&inst->noiseEstDensity[offset + i], tmp16x8_1);
    }  // End loop over magnitude spectrum

    // Last iteration over magnitude spectrum:
    // compute delta
    if (inst->noiseEstDensity[offset + i] > 512) {
      // Get values for deltaBuff by shifting intead of dividing.
      int factor = WebRtcSpl_NormW16(inst->noiseEstDensity[offset + i]);
      delta = (int16_t)(FACTOR_Q16 >> (14 - factor));
    } else {
      delta = FACTOR_Q7;
      if (inst->blockIndex < END_STARTUP_LONG) {
        // Smaller step size during startup. This prevents from using
        // unrealistic values causing overflow.
        delta = FACTOR_Q7_STARTUP;
      }
    }
    // update log quantile estimate
    tmp16 = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(delta, countDiv, 14);
    if (lmagn[i] > inst->noiseEstLogQuantile[offset + i]) {
      // +=QUANTILE*delta/(inst->counter[s]+1) QUANTILE=0.25, =1 in Q2
      // CounterDiv=1/(inst->counter[s]+1) in Q15
      tmp16 += 2;
      tmp16no1 = WEBRTC_SPL_RSHIFT_W16(tmp16, 2);
      inst->noiseEstLogQuantile[offset + i] += tmp16no1;
    } else {
      tmp16 += 1;
      tmp16no1 = WEBRTC_SPL_RSHIFT_W16(tmp16, 1);
      // *(1-QUANTILE), in Q2 QUANTILE=0.25, 1-0.25=0.75=3 in Q2
      tmp16no2 = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(tmp16no1, 3, 1);
      inst->noiseEstLogQuantile[offset + i] -= tmp16no2;
      if (inst->noiseEstLogQuantile[offset + i] < logval) {
        // logval is the smallest fixed point representation we can have.
        // Values below that will correspond to values in the interval
        // [0, 1], which can't possibly occur.
        inst->noiseEstLogQuantile[offset + i] = logval;
      }
    }

    // update density estimate
    if (WEBRTC_SPL_ABS_W16(lmagn[i] - inst->noiseEstLogQuantile[offset + i])
        < WIDTH_Q8) {
      tmp16no1 = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(
                   inst->noiseEstDensity[offset + i], countProd, 15);
      tmp16no2 = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(
                   width_factor, countDiv, 15);
      inst->noiseEstDensity[offset + i] = tmp16no1 + tmp16no2;
    }


    if (counter >= END_STARTUP_LONG) {
      inst->noiseEstCounter[s] = 0;
      if (inst->blockIndex >= END_STARTUP_LONG) {
        UpdateNoiseEstimateNeon(inst, offset);
      }
    }
    inst->noiseEstCounter[s]++;

  }  // end loop over simultaneous estimates

  // Sequentially update the noise during startup
  if (inst->blockIndex < END_STARTUP_LONG) {
    UpdateNoiseEstimateNeon(inst, offset);
  }

  for (i = 0; i < inst->magnLen; i++) {
    noise[i] = (uint32_t)(inst->noiseEstQuantile[i]); // Q(qNoise)
  }
  (*q_noise) = (int16_t)inst->qNoise;
}

// Filter the data in the frequency domain, and create spectrum.
void WebRtcNsx_PrepareSpectrumNeon(NsxInst_t* inst, int16_t* freq_buf) {
  assert(inst->magnLen % 8 == 1);
  assert(inst->anaLen2 % 16 == 0);

  // (1) Filtering.

  // Fixed point C code for the next block is as follows:
  // for (i = 0; i < inst->magnLen; i++) {
  //   inst->real[i] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(inst->real[i],
  //      (int16_t)(inst->noiseSupFilter[i]), 14); // Q(normData-stages)
  //   inst->imag[i] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(inst->imag[i],
  //      (int16_t)(inst->noiseSupFilter[i]), 14); // Q(normData-stages)
  // }

  int16_t* preal = &inst->real[0];
  int16_t* pimag = &inst->imag[0];
  int16_t* pns_filter = (int16_t*)&inst->noiseSupFilter[0];
  int16_t* pimag_end = pimag + inst->magnLen - 4;

  while (pimag < pimag_end) {
    int16x8_t real = vld1q_s16(preal);
    int16x8_t imag = vld1q_s16(pimag);
    int16x8_t ns_filter = vld1q_s16(pns_filter);

    int32x4_t tmp_r_0 = vmull_s16(vget_low_s16(real), vget_low_s16(ns_filter));
    int32x4_t tmp_i_0 = vmull_s16(vget_low_s16(imag), vget_low_s16(ns_filter));
    int32x4_t tmp_r_1 = vmull_s16(vget_high_s16(real),
                                  vget_high_s16(ns_filter));
    int32x4_t tmp_i_1 = vmull_s16(vget_high_s16(imag),
                                  vget_high_s16(ns_filter));

    int16x4_t result_r_0 = vshrn_n_s32(tmp_r_0, 14);
    int16x4_t result_i_0 = vshrn_n_s32(tmp_i_0, 14);
    int16x4_t result_r_1 = vshrn_n_s32(tmp_r_1, 14);
    int16x4_t result_i_1 = vshrn_n_s32(tmp_i_1, 14);

    vst1q_s16(preal, vcombine_s16(result_r_0, result_r_1));
    vst1q_s16(pimag, vcombine_s16(result_i_0, result_i_1));
    preal += 8;
    pimag += 8;
    pns_filter += 8;
  }

  // Filter the last element
  *preal = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(*preal, *pns_filter, 14);
  *pimag = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT(*pimag, *pns_filter, 14);

  // (2) Create spectrum.

  // Fixed point C code for the rest of the function is as follows:
  // freq_buf[0] = inst->real[0];
  // freq_buf[1] = -inst->imag[0];
  // for (i = 1, j = 2; i < inst->anaLen2; i += 1, j += 2) {
  //   freq_buf[j] = inst->real[i];
  //   freq_buf[j + 1] = -inst->imag[i];
  // }
  // freq_buf[inst->anaLen] = inst->real[inst->anaLen2];
  // freq_buf[inst->anaLen + 1] = -inst->imag[inst->anaLen2];

  preal = &inst->real[0];
  pimag = &inst->imag[0];
  pimag_end = pimag + inst->anaLen2;
  int16_t * freq_buf_start = freq_buf;
  while (pimag < pimag_end) {
    // loop unroll
    int16x8x2_t real_imag_0;
    int16x8x2_t real_imag_1;
    real_imag_0.val[1] = vld1q_s16(pimag);
    real_imag_0.val[0] = vld1q_s16(preal);
    preal += 8;
    pimag += 8;
    real_imag_1.val[1] = vld1q_s16(pimag);
    real_imag_1.val[0] = vld1q_s16(preal);
    preal += 8;
    pimag += 8;

    real_imag_0.val[1] = vnegq_s16(real_imag_0.val[1]);
    real_imag_1.val[1] = vnegq_s16(real_imag_1.val[1]);
    vst2q_s16(freq_buf_start, real_imag_0);
    freq_buf_start += 16;
    vst2q_s16(freq_buf_start, real_imag_1);
    freq_buf_start += 16;
  }
  freq_buf[inst->anaLen] = inst->real[inst->anaLen2];
  freq_buf[inst->anaLen + 1] = -inst->imag[inst->anaLen2];
}

// Denormalize the input buffer.
void WebRtcNsx_DenormalizeNeon(NsxInst_t* inst, int16_t* in, int factor) {
  int16_t* ptr_real = &inst->real[0];
  int16_t* ptr_in = &in[0];

  __asm__ __volatile__("vdup.32 q10, %0" ::
                       "r"((int32_t)(factor - inst->normData)) : "q10");
  for (; ptr_real < &inst->real[inst->anaLen];) {

    // Loop unrolled once. Both pointers are incremented.
    __asm__ __volatile__(
      // tmp32 = WEBRTC_SPL_SHIFT_W32((int32_t)in[j],
      //                             factor - inst->normData);
      "vld2.16 {d24, d25}, [%[ptr_in]]!\n\t"
      "vmovl.s16 q12, d24\n\t"
      "vshl.s32 q12, q10\n\t"
      // inst->real[i] = WebRtcSpl_SatW32ToW16(tmp32); // Q0
      "vqmovn.s32 d24, q12\n\t"
      "vst1.16 d24, [%[ptr_real]]!\n\t"

      // tmp32 = WEBRTC_SPL_SHIFT_W32((int32_t)in[j],
      //                             factor - inst->normData);
      "vld2.16 {d22, d23}, [%[ptr_in]]!\n\t"
      "vmovl.s16 q11, d22\n\t"
      "vshl.s32 q11, q10\n\t"
      // inst->real[i] = WebRtcSpl_SatW32ToW16(tmp32); // Q0
      "vqmovn.s32 d22, q11\n\t"
      "vst1.16 d22, [%[ptr_real]]!\n\t"

      // Specify constraints.
      :[ptr_in]"+r"(ptr_in),
       [ptr_real]"+r"(ptr_real)
      :
      :"d22", "d23", "d24", "d25"
    );
  }
}

// For the noise supress process, synthesis, read out fully processed segment,
// and update synthesis buffer.
void WebRtcNsx_SynthesisUpdateNeon(NsxInst_t* inst,
                                   int16_t* out_frame,
                                   int16_t gain_factor) {
  int16_t* ptr_real = &inst->real[0];
  int16_t* ptr_syn = &inst->synthesisBuffer[0];
  const int16_t* ptr_window = &inst->window[0];

  // synthesis
  __asm__ __volatile__("vdup.16 d24, %0" : : "r"(gain_factor) : "d24");
  // Loop unrolled once. All pointers are incremented in the assembly code.
  for (; ptr_syn < &inst->synthesisBuffer[inst->anaLen];) {
    __asm__ __volatile__(
      // Load variables.
      "vld1.16 d22, [%[ptr_real]]!\n\t"
      "vld1.16 d23, [%[ptr_window]]!\n\t"
      "vld1.16 d25, [%[ptr_syn]]\n\t"
      // tmp16a = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(
      //           inst->window[i], inst->real[i], 14); // Q0, window in Q14
      "vmull.s16 q11, d22, d23\n\t"
      "vrshrn.i32 d22, q11, #14\n\t"
      // tmp32 = WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(tmp16a, gain_factor, 13);
      "vmull.s16 q11, d24, d22\n\t"
      // tmp16b = WebRtcSpl_SatW32ToW16(tmp32); // Q0
      "vqrshrn.s32 d22, q11, #13\n\t"
      // inst->synthesisBuffer[i] = WebRtcSpl_AddSatW16(
      //     inst->synthesisBuffer[i], tmp16b); // Q0
      "vqadd.s16 d25, d22\n\t"
      "vst1.16 d25, [%[ptr_syn]]!\n\t"

      // Load variables.
      "vld1.16 d26, [%[ptr_real]]!\n\t"
      "vld1.16 d27, [%[ptr_window]]!\n\t"
      "vld1.16 d28, [%[ptr_syn]]\n\t"
      // tmp16a = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(
      //           inst->window[i], inst->real[i], 14); // Q0, window in Q14
      "vmull.s16 q13, d26, d27\n\t"
      "vrshrn.i32 d26, q13, #14\n\t"
      // tmp32 = WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(tmp16a, gain_factor, 13);
      "vmull.s16 q13, d24, d26\n\t"
      // tmp16b = WebRtcSpl_SatW32ToW16(tmp32); // Q0
      "vqrshrn.s32 d26, q13, #13\n\t"
      // inst->synthesisBuffer[i] = WebRtcSpl_AddSatW16(
      //     inst->synthesisBuffer[i], tmp16b); // Q0
      "vqadd.s16 d28, d26\n\t"
      "vst1.16 d28, [%[ptr_syn]]!\n\t"

      // Specify constraints.
      :[ptr_real]"+r"(ptr_real),
       [ptr_window]"+r"(ptr_window),
       [ptr_syn]"+r"(ptr_syn)
      :
      :"d22", "d23", "d24", "d25", "d26", "d27", "d28", "q11", "q12", "q13"
    );
  }

  int16_t* ptr_out = &out_frame[0];
  ptr_syn = &inst->synthesisBuffer[0];
  // read out fully processed segment
  for (; ptr_syn < &inst->synthesisBuffer[inst->blockLen10ms];) {
    // Loop unrolled once. Both pointers are incremented in the assembly code.
    __asm__ __volatile__(
      // out_frame[i] = inst->synthesisBuffer[i]; // Q0
      "vld1.16 {d22, d23}, [%[ptr_syn]]!\n\t"
      "vld1.16 {d24, d25}, [%[ptr_syn]]!\n\t"
      "vst1.16 {d22, d23}, [%[ptr_out]]!\n\t"
      "vst1.16 {d24, d25}, [%[ptr_out]]!\n\t"
      :[ptr_syn]"+r"(ptr_syn),
       [ptr_out]"+r"(ptr_out)
      :
      :"d22", "d23", "d24", "d25"
    );
  }

  // Update synthesis buffer.
  // C code:
  // WEBRTC_SPL_MEMCPY_W16(inst->synthesisBuffer,
  //                      inst->synthesisBuffer + inst->blockLen10ms,
  //                      inst->anaLen - inst->blockLen10ms);
  ptr_out = &inst->synthesisBuffer[0],
  ptr_syn = &inst->synthesisBuffer[inst->blockLen10ms];
  for (; ptr_syn < &inst->synthesisBuffer[inst->anaLen];) {
    // Loop unrolled once. Both pointers are incremented in the assembly code.
    __asm__ __volatile__(
      "vld1.16 {d22, d23}, [%[ptr_syn]]!\n\t"
      "vld1.16 {d24, d25}, [%[ptr_syn]]!\n\t"
      "vst1.16 {d22, d23}, [%[ptr_out]]!\n\t"
      "vst1.16 {d24, d25}, [%[ptr_out]]!\n\t"
      :[ptr_syn]"+r"(ptr_syn),
       [ptr_out]"+r"(ptr_out)
      :
      :"d22", "d23", "d24", "d25"
    );
  }

  // C code:
  // WebRtcSpl_ZerosArrayW16(inst->synthesisBuffer
  //    + inst->anaLen - inst->blockLen10ms, inst->blockLen10ms);
  __asm__ __volatile__("vdup.16 q10, %0" : : "r"(0) : "q10");
  for (; ptr_out < &inst->synthesisBuffer[inst->anaLen];) {
    // Loop unrolled once. Pointer is incremented in the assembly code.
    __asm__ __volatile__(
      "vst1.16 {d20, d21}, [%[ptr_out]]!\n\t"
      "vst1.16 {d20, d21}, [%[ptr_out]]!\n\t"
      :[ptr_out]"+r"(ptr_out)
      :
      :"d20", "d21"
    );
  }
}

// Update analysis buffer for lower band, and window data before FFT.
void WebRtcNsx_AnalysisUpdateNeon(NsxInst_t* inst,
                                  int16_t* out,
                                  int16_t* new_speech) {

  int16_t* ptr_ana = &inst->analysisBuffer[inst->blockLen10ms];
  int16_t* ptr_out = &inst->analysisBuffer[0];

  // For lower band update analysis buffer.
  // WEBRTC_SPL_MEMCPY_W16(inst->analysisBuffer,
  //                      inst->analysisBuffer + inst->blockLen10ms,
  //                      inst->anaLen - inst->blockLen10ms);
  for (; ptr_out < &inst->analysisBuffer[inst->anaLen - inst->blockLen10ms];) {
    // Loop unrolled once, so both pointers are incremented by 8 twice.
    __asm__ __volatile__(
      "vld1.16 {d20, d21}, [%[ptr_ana]]!\n\t"
      "vst1.16 {d20, d21}, [%[ptr_out]]!\n\t"
      "vld1.16 {d22, d23}, [%[ptr_ana]]!\n\t"
      "vst1.16 {d22, d23}, [%[ptr_out]]!\n\t"
      :[ptr_ana]"+r"(ptr_ana),
       [ptr_out]"+r"(ptr_out)
      :
      :"d20", "d21", "d22", "d23"
    );
  }

  // WEBRTC_SPL_MEMCPY_W16(inst->analysisBuffer
  //    + inst->anaLen - inst->blockLen10ms, new_speech, inst->blockLen10ms);
  for (ptr_ana = new_speech; ptr_out < &inst->analysisBuffer[inst->anaLen];) {
    // Loop unrolled once, so both pointers are incremented by 8 twice.
    __asm__ __volatile__(
      "vld1.16 {d20, d21}, [%[ptr_ana]]!\n\t"
      "vst1.16 {d20, d21}, [%[ptr_out]]!\n\t"
      "vld1.16 {d22, d23}, [%[ptr_ana]]!\n\t"
      "vst1.16 {d22, d23}, [%[ptr_out]]!\n\t"
      :[ptr_ana]"+r"(ptr_ana),
       [ptr_out]"+r"(ptr_out)
      :
      :"d20", "d21", "d22", "d23"
    );
  }

  // Window data before FFT
  const int16_t* ptr_window = &inst->window[0];
  ptr_out = &out[0];
  ptr_ana = &inst->analysisBuffer[0];
  for (; ptr_out < &out[inst->anaLen];) {

    // Loop unrolled once, so all pointers are incremented by 4 twice.
    __asm__ __volatile__(
      "vld1.16 d20, [%[ptr_ana]]!\n\t"
      "vld1.16 d21, [%[ptr_window]]!\n\t"
      // out[i] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(
      //           inst->window[i], inst->analysisBuffer[i], 14); // Q0
      "vmull.s16 q10, d20, d21\n\t"
      "vrshrn.i32 d20, q10, #14\n\t"
      "vst1.16 d20, [%[ptr_out]]!\n\t"

      "vld1.16 d22, [%[ptr_ana]]!\n\t"
      "vld1.16 d23, [%[ptr_window]]!\n\t"
      // out[i] = (int16_t)WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(
      //           inst->window[i], inst->analysisBuffer[i], 14); // Q0
      "vmull.s16 q11, d22, d23\n\t"
      "vrshrn.i32 d22, q11, #14\n\t"
      "vst1.16 d22, [%[ptr_out]]!\n\t"

      // Specify constraints.
      :[ptr_ana]"+r"(ptr_ana),
       [ptr_window]"+r"(ptr_window),
       [ptr_out]"+r"(ptr_out)
      :
      :"d20", "d21", "d22", "d23", "q10", "q11"
    );
  }
}

// Create a complex number buffer (out[]) as the intput (in[]) interleaved with
// zeros, and normalize it.
void WebRtcNsx_CreateComplexBufferNeon(NsxInst_t* inst,
                                       int16_t* in,
                                       int16_t* out) {
  int16_t* ptr_out = &out[0];
  int16_t* ptr_in = &in[0];

  __asm__ __volatile__("vdup.16 d25, %0" : : "r"(0) : "d25");
  __asm__ __volatile__("vdup.16 q10, %0" : : "r"(inst->normData) : "q10");
  for (; ptr_in < &in[inst->anaLen];) {

    // Loop unrolled once, so ptr_in is incremented by 8 twice,
    // and ptr_out is incremented by 8 four times.
    __asm__ __volatile__(
      // out[j] = WEBRTC_SPL_LSHIFT_W16(in[i], inst->normData); // Q(normData)
      "vld1.16 {d22, d23}, [%[ptr_in]]!\n\t"
      "vshl.s16 q11, q10\n\t"
      "vmov d24, d23\n\t"

      // out[j + 1] = 0; // Insert zeros in imaginary part
      "vmov d23, d25\n\t"
      "vst2.16 {d22, d23}, [%[ptr_out]]!\n\t"
      "vst2.16 {d24, d25}, [%[ptr_out]]!\n\t"

      // out[j] = WEBRTC_SPL_LSHIFT_W16(in[i], inst->normData); // Q(normData)
      "vld1.16 {d22, d23}, [%[ptr_in]]!\n\t"
      "vshl.s16 q11, q10\n\t"
      "vmov d24, d23\n\t"

      // out[j + 1] = 0; // Insert zeros in imaginary part
      "vmov d23, d25\n\t"
      "vst2.16 {d22, d23}, [%[ptr_out]]!\n\t"
      "vst2.16 {d24, d25}, [%[ptr_out]]!\n\t"

      // Specify constraints.
      :[ptr_in]"+r"(ptr_in),
       [ptr_out]"+r"(ptr_out)
      :
      :"d22", "d23", "d24", "d25", "q10", "q11"
    );
  }
}
