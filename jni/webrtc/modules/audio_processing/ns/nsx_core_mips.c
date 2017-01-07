/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <assert.h>
#include <string.h>

#include "webrtc/modules/audio_processing/ns/noise_suppression_x.h"
#include "webrtc/modules/audio_processing/ns/nsx_core.h"

static const int16_t kIndicatorTable[17] = {
  0, 2017, 3809, 5227, 6258, 6963, 7424, 7718,
  7901, 8014, 8084, 8126, 8152, 8168, 8177, 8183, 8187
};

// Compute speech/noise probability
// speech/noise probability is returned in: probSpeechFinal
//snrLocPrior is the prior SNR for each frequency (in Q11)
//snrLocPost is the post SNR for each frequency (in Q11)
void WebRtcNsx_SpeechNoiseProb(NoiseSuppressionFixedC* inst,
                               uint16_t* nonSpeechProbFinal,
                               uint32_t* priorLocSnr,
                               uint32_t* postLocSnr) {
  uint32_t tmpU32no1, tmpU32no2, tmpU32no3;
  int32_t indPriorFX, tmp32no1;
  int32_t logLrtTimeAvgKsumFX;
  int16_t indPriorFX16;
  int16_t tmp16, tmp16no1, tmp16no2, tmpIndFX, tableIndex, frac;
  size_t i;
  int normTmp, nShifts;

  int32_t r0, r1, r2, r3, r4, r5, r6, r7, r8, r9;
  int32_t const_max = 0x7fffffff;
  int32_t const_neg43 = -43;
  int32_t const_5412 = 5412;
  int32_t const_11rsh12 = (11 << 12);
  int32_t const_178 = 178;


  // compute feature based on average LR factor
  // this is the average over all frequencies of the smooth log LRT
  logLrtTimeAvgKsumFX = 0;
  for (i = 0; i < inst->magnLen; i++) {
    r0 = postLocSnr[i]; // Q11
    r1 = priorLocSnr[i];
    r2 = inst->logLrtTimeAvgW32[i];

    __asm __volatile(
      ".set       push                                    \n\t"
      ".set       noreorder                               \n\t"
      "clz        %[r3],    %[r0]                         \n\t"
      "clz        %[r5],    %[r1]                         \n\t"
      "slti       %[r4],    %[r3],    32                  \n\t"
      "slti       %[r6],    %[r5],    32                  \n\t"
      "movz       %[r3],    $0,       %[r4]               \n\t"
      "movz       %[r5],    $0,       %[r6]               \n\t"
      "slti       %[r4],    %[r3],    11                  \n\t"
      "addiu      %[r6],    %[r3],    -11                 \n\t"
      "neg        %[r7],    %[r6]                         \n\t"
      "sllv       %[r6],    %[r1],    %[r6]               \n\t"
      "srav       %[r7],    %[r1],    %[r7]               \n\t"
      "movn       %[r6],    %[r7],    %[r4]               \n\t"
      "sllv       %[r1],    %[r1],    %[r5]               \n\t"
      "and        %[r1],    %[r1],    %[const_max]        \n\t"
      "sra        %[r1],    %[r1],    19                  \n\t"
      "mul        %[r7],    %[r1],    %[r1]               \n\t"
      "sllv       %[r3],    %[r0],    %[r3]               \n\t"
      "divu       %[r8],    %[r3],    %[r6]               \n\t"
      "slti       %[r6],    %[r6],    1                   \n\t"
      "mul        %[r7],    %[r7],    %[const_neg43]      \n\t"
      "sra        %[r7],    %[r7],    19                  \n\t"
      "movz       %[r3],    %[r8],    %[r6]               \n\t"
      "subu       %[r0],    %[r0],    %[r3]               \n\t"
      "movn       %[r0],    $0,       %[r6]               \n\t"
      "mul        %[r1],    %[r1],    %[const_5412]       \n\t"
      "sra        %[r1],    %[r1],    12                  \n\t"
      "addu       %[r7],    %[r7],    %[r1]               \n\t"
      "addiu      %[r1],    %[r7],    37                  \n\t"
      "addiu      %[r5],    %[r5],    -31                 \n\t"
      "neg        %[r5],    %[r5]                         \n\t"
      "sll        %[r5],    %[r5],    12                  \n\t"
      "addu       %[r5],    %[r5],    %[r1]               \n\t"
      "subu       %[r7],    %[r5],    %[const_11rsh12]    \n\t"
      "mul        %[r7],    %[r7],    %[const_178]        \n\t"
      "sra        %[r7],    %[r7],    8                   \n\t"
      "addu       %[r7],    %[r7],    %[r2]               \n\t"
      "sra        %[r7],    %[r7],    1                   \n\t"
      "subu       %[r2],    %[r2],    %[r7]               \n\t"
      "addu       %[r2],    %[r2],    %[r0]               \n\t"
      ".set       pop                                     \n\t"
      : [r0] "+r" (r0), [r1] "+r" (r1), [r2] "+r" (r2),
        [r3] "=&r" (r3), [r4] "=&r" (r4), [r5] "=&r" (r5),
        [r6] "=&r" (r6), [r7] "=&r" (r7), [r8] "=&r" (r8)
      : [const_max] "r" (const_max), [const_neg43] "r" (const_neg43),
        [const_5412] "r" (const_5412), [const_11rsh12] "r" (const_11rsh12),
        [const_178] "r" (const_178)
      : "hi", "lo"
    );
    inst->logLrtTimeAvgW32[i] = r2;
    logLrtTimeAvgKsumFX += r2;
  }

  inst->featureLogLrt = (logLrtTimeAvgKsumFX * BIN_SIZE_LRT) >>
      (inst->stages + 11);

  // done with computation of LR factor

  //
  // compute the indicator functions
  //

  // average LRT feature
  // FLOAT code
  // indicator0 = 0.5 * (tanh(widthPrior *
  //                      (logLrtTimeAvgKsum - threshPrior0)) + 1.0);
  tmpIndFX = 16384; // Q14(1.0)
  tmp32no1 = logLrtTimeAvgKsumFX - inst->thresholdLogLrt; // Q12
  nShifts = 7 - inst->stages; // WIDTH_PR_MAP_SHIFT - inst->stages + 5;
  //use larger width in tanh map for pause regions
  if (tmp32no1 < 0) {
    tmpIndFX = 0;
    tmp32no1 = -tmp32no1;
    //widthPrior = widthPrior * 2.0;
    nShifts++;
  }
  tmp32no1 = WEBRTC_SPL_SHIFT_W32(tmp32no1, nShifts); // Q14
  // compute indicator function: sigmoid map
  if (tmp32no1 < (16 << 14) && tmp32no1 >= 0) {
    tableIndex = (int16_t)(tmp32no1 >> 14);
    tmp16no2 = kIndicatorTable[tableIndex];
    tmp16no1 = kIndicatorTable[tableIndex + 1] - kIndicatorTable[tableIndex];
    frac = (int16_t)(tmp32no1 & 0x00003fff); // Q14
    tmp16no2 += (int16_t)((tmp16no1 * frac) >> 14);
    if (tmpIndFX == 0) {
      tmpIndFX = 8192 - tmp16no2; // Q14
    } else {
      tmpIndFX = 8192 + tmp16no2; // Q14
    }
  }
  indPriorFX = inst->weightLogLrt * tmpIndFX;  // 6*Q14

  //spectral flatness feature
  if (inst->weightSpecFlat) {
    tmpU32no1 = WEBRTC_SPL_UMUL(inst->featureSpecFlat, 400); // Q10
    tmpIndFX = 16384; // Q14(1.0)
    //use larger width in tanh map for pause regions
    tmpU32no2 = inst->thresholdSpecFlat - tmpU32no1; //Q10
    nShifts = 4;
    if (inst->thresholdSpecFlat < tmpU32no1) {
      tmpIndFX = 0;
      tmpU32no2 = tmpU32no1 - inst->thresholdSpecFlat;
      //widthPrior = widthPrior * 2.0;
      nShifts++;
    }
    tmpU32no1 = WebRtcSpl_DivU32U16(tmpU32no2 << nShifts, 25);  //Q14
    // compute indicator function: sigmoid map
    // FLOAT code
    // indicator1 = 0.5 * (tanh(sgnMap * widthPrior *
    //                          (threshPrior1 - tmpFloat1)) + 1.0);
    if (tmpU32no1 < (16 << 14)) {
      tableIndex = (int16_t)(tmpU32no1 >> 14);
      tmp16no2 = kIndicatorTable[tableIndex];
      tmp16no1 = kIndicatorTable[tableIndex + 1] - kIndicatorTable[tableIndex];
      frac = (int16_t)(tmpU32no1 & 0x00003fff); // Q14
      tmp16no2 += (int16_t)((tmp16no1 * frac) >> 14);
      if (tmpIndFX) {
        tmpIndFX = 8192 + tmp16no2; // Q14
      } else {
        tmpIndFX = 8192 - tmp16no2; // Q14
      }
    }
    indPriorFX += inst->weightSpecFlat * tmpIndFX;  // 6*Q14
  }

  //for template spectral-difference
  if (inst->weightSpecDiff) {
    tmpU32no1 = 0;
    if (inst->featureSpecDiff) {
      normTmp = WEBRTC_SPL_MIN(20 - inst->stages,
                               WebRtcSpl_NormU32(inst->featureSpecDiff));
      assert(normTmp >= 0);
      tmpU32no1 = inst->featureSpecDiff << normTmp;  // Q(normTmp-2*stages)
      tmpU32no2 = inst->timeAvgMagnEnergy >> (20 - inst->stages - normTmp);
      if (tmpU32no2 > 0) {
        // Q(20 - inst->stages)
        tmpU32no1 /= tmpU32no2;
      } else {
        tmpU32no1 = (uint32_t)(0x7fffffff);
      }
    }
    tmpU32no3 = (inst->thresholdSpecDiff << 17) / 25;
    tmpU32no2 = tmpU32no1 - tmpU32no3;
    nShifts = 1;
    tmpIndFX = 16384; // Q14(1.0)
    //use larger width in tanh map for pause regions
    if (tmpU32no2 & 0x80000000) {
      tmpIndFX = 0;
      tmpU32no2 = tmpU32no3 - tmpU32no1;
      //widthPrior = widthPrior * 2.0;
      nShifts--;
    }
    tmpU32no1 = tmpU32no2 >> nShifts;
    // compute indicator function: sigmoid map
    /* FLOAT code
     indicator2 = 0.5 * (tanh(widthPrior * (tmpFloat1 - threshPrior2)) + 1.0);
     */
    if (tmpU32no1 < (16 << 14)) {
      tableIndex = (int16_t)(tmpU32no1 >> 14);
      tmp16no2 = kIndicatorTable[tableIndex];
      tmp16no1 = kIndicatorTable[tableIndex + 1] - kIndicatorTable[tableIndex];
      frac = (int16_t)(tmpU32no1 & 0x00003fff); // Q14
      tmp16no2 += (int16_t)WEBRTC_SPL_MUL_16_16_RSFT_WITH_ROUND(
                    tmp16no1, frac, 14);
      if (tmpIndFX) {
        tmpIndFX = 8192 + tmp16no2;
      } else {
        tmpIndFX = 8192 - tmp16no2;
      }
    }
    indPriorFX += inst->weightSpecDiff * tmpIndFX;  // 6*Q14
  }

  //combine the indicator function with the feature weights
  // FLOAT code
  // indPrior = 1 - (weightIndPrior0 * indicator0 + weightIndPrior1 *
  //                 indicator1 + weightIndPrior2 * indicator2);
  indPriorFX16 = WebRtcSpl_DivW32W16ResW16(98307 - indPriorFX, 6); // Q14
  // done with computing indicator function

  //compute the prior probability
  // FLOAT code
  // inst->priorNonSpeechProb += PRIOR_UPDATE *
  //                             (indPriorNonSpeech - inst->priorNonSpeechProb);
  tmp16 = indPriorFX16 - inst->priorNonSpeechProb; // Q14
  inst->priorNonSpeechProb += (int16_t)((PRIOR_UPDATE_Q14 * tmp16) >> 14);

  //final speech probability: combine prior model with LR factor:

  memset(nonSpeechProbFinal, 0, sizeof(uint16_t) * inst->magnLen);

  if (inst->priorNonSpeechProb > 0) {
    r0 = inst->priorNonSpeechProb;
    r1 = 16384 - r0;
    int32_t const_23637 = 23637;
    int32_t const_44 = 44;
    int32_t const_84 = 84;
    int32_t const_1 = 1;
    int32_t const_neg8 = -8;
    for (i = 0; i < inst->magnLen; i++) {
      r2 = inst->logLrtTimeAvgW32[i];
      if (r2 < 65300) {
        __asm __volatile(
          ".set         push                                      \n\t"
          ".set         noreorder                                 \n\t"
          "mul          %[r2],    %[r2],          %[const_23637]  \n\t"
          "sll          %[r6],    %[r1],          16              \n\t"
          "clz          %[r7],    %[r6]                           \n\t"
          "clo          %[r8],    %[r6]                           \n\t"
          "slt          %[r9],    %[r6],          $0              \n\t"
          "movn         %[r7],    %[r8],          %[r9]           \n\t"
          "sra          %[r2],    %[r2],          14              \n\t"
          "andi         %[r3],    %[r2],          0xfff           \n\t"
          "mul          %[r4],    %[r3],          %[r3]           \n\t"
          "mul          %[r3],    %[r3],          %[const_84]     \n\t"
          "sra          %[r2],    %[r2],          12              \n\t"
          "slt          %[r5],    %[r2],          %[const_neg8]   \n\t"
          "movn         %[r2],    %[const_neg8],  %[r5]           \n\t"
          "mul          %[r4],    %[r4],          %[const_44]     \n\t"
          "sra          %[r3],    %[r3],          7               \n\t"
          "addiu        %[r7],    %[r7],          -1              \n\t"
          "slti         %[r9],    %[r7],          31              \n\t"
          "movz         %[r7],    $0,             %[r9]           \n\t"
          "sra          %[r4],    %[r4],          19              \n\t"
          "addu         %[r4],    %[r4],          %[r3]           \n\t"
          "addiu        %[r3],    %[r2],          8               \n\t"
          "addiu        %[r2],    %[r2],          -4              \n\t"
          "neg          %[r5],    %[r2]                           \n\t"
          "sllv         %[r6],    %[r4],          %[r2]           \n\t"
          "srav         %[r5],    %[r4],          %[r5]           \n\t"
          "slt          %[r2],    %[r2],          $0              \n\t"
          "movn         %[r6],    %[r5],          %[r2]           \n\t"
          "sllv         %[r3],    %[const_1],     %[r3]           \n\t"
          "addu         %[r2],    %[r3],          %[r6]           \n\t"
          "clz          %[r4],    %[r2]                           \n\t"
          "clo          %[r5],    %[r2]                           \n\t"
          "slt          %[r8],    %[r2],          $0              \n\t"
          "movn         %[r4],    %[r5],          %[r8]           \n\t"
          "addiu        %[r4],    %[r4],          -1              \n\t"
          "slt          %[r5],    $0,             %[r2]           \n\t"
          "or           %[r5],    %[r5],          %[r7]           \n\t"
          "movz         %[r4],    $0,             %[r5]           \n\t"
          "addiu        %[r6],    %[r7],          -7              \n\t"
          "addu         %[r6],    %[r6],          %[r4]           \n\t"
          "bltz         %[r6],    1f                              \n\t"
          " nop                                                   \n\t"
          "addiu        %[r4],    %[r6],          -8              \n\t"
          "neg          %[r3],    %[r4]                           \n\t"
          "srav         %[r5],    %[r2],          %[r3]           \n\t"
          "mul          %[r5],    %[r5],          %[r1]           \n\t"
          "mul          %[r2],    %[r2],          %[r1]           \n\t"
          "slt          %[r4],    %[r4],          $0              \n\t"
          "srav         %[r5],    %[r5],          %[r6]           \n\t"
          "sra          %[r2],    %[r2],          8               \n\t"
          "movn         %[r2],    %[r5],          %[r4]           \n\t"
          "sll          %[r3],    %[r0],          8               \n\t"
          "addu         %[r2],    %[r0],          %[r2]           \n\t"
          "divu         %[r3],    %[r3],          %[r2]           \n\t"
         "1:                                                      \n\t"
          ".set         pop                                       \n\t"
          : [r2] "+r" (r2), [r3] "=&r" (r3), [r4] "=&r" (r4),
            [r5] "=&r" (r5), [r6] "=&r" (r6), [r7] "=&r" (r7),
            [r8] "=&r" (r8), [r9] "=&r" (r9)
          : [r0] "r" (r0), [r1] "r" (r1), [const_23637] "r" (const_23637),
            [const_neg8] "r" (const_neg8), [const_84] "r" (const_84),
            [const_1] "r" (const_1), [const_44] "r" (const_44)
          : "hi", "lo"
        );
        nonSpeechProbFinal[i] = r3;
      }
    }
  }
}

// Update analysis buffer for lower band, and window data before FFT.
void WebRtcNsx_AnalysisUpdate_mips(NoiseSuppressionFixedC* inst,
                                   int16_t* out,
                                   int16_t* new_speech) {
  int iters, after;
  int anaLen = (int)inst->anaLen;
  int *window = (int*)inst->window;
  int *anaBuf = (int*)inst->analysisBuffer;
  int *outBuf = (int*)out;
  int r0, r1, r2, r3, r4, r5, r6, r7;
#if defined(MIPS_DSP_R1_LE)
  int r8;
#endif

  // For lower band update analysis buffer.
  memcpy(inst->analysisBuffer, inst->analysisBuffer + inst->blockLen10ms,
      (inst->anaLen - inst->blockLen10ms) * sizeof(*inst->analysisBuffer));
  memcpy(inst->analysisBuffer + inst->anaLen - inst->blockLen10ms, new_speech,
      inst->blockLen10ms * sizeof(*inst->analysisBuffer));

  // Window data before FFT.
#if defined(MIPS_DSP_R1_LE)
  __asm __volatile(
    ".set              push                                \n\t"
    ".set              noreorder                           \n\t"
    "sra               %[iters],   %[anaLen],    3         \n\t"
   "1:                                                     \n\t"
    "blez              %[iters],   2f                      \n\t"
    " nop                                                  \n\t"
    "lw                %[r0],      0(%[window])            \n\t"
    "lw                %[r1],      0(%[anaBuf])            \n\t"
    "lw                %[r2],      4(%[window])            \n\t"
    "lw                %[r3],      4(%[anaBuf])            \n\t"
    "lw                %[r4],      8(%[window])            \n\t"
    "lw                %[r5],      8(%[anaBuf])            \n\t"
    "lw                %[r6],      12(%[window])           \n\t"
    "lw                %[r7],      12(%[anaBuf])           \n\t"
    "muleq_s.w.phl     %[r8],      %[r0],        %[r1]     \n\t"
    "muleq_s.w.phr     %[r0],      %[r0],        %[r1]     \n\t"
    "muleq_s.w.phl     %[r1],      %[r2],        %[r3]     \n\t"
    "muleq_s.w.phr     %[r2],      %[r2],        %[r3]     \n\t"
    "muleq_s.w.phl     %[r3],      %[r4],        %[r5]     \n\t"
    "muleq_s.w.phr     %[r4],      %[r4],        %[r5]     \n\t"
    "muleq_s.w.phl     %[r5],      %[r6],        %[r7]     \n\t"
    "muleq_s.w.phr     %[r6],      %[r6],        %[r7]     \n\t"
#if defined(MIPS_DSP_R2_LE)
    "precr_sra_r.ph.w  %[r8],      %[r0],        15        \n\t"
    "precr_sra_r.ph.w  %[r1],      %[r2],        15        \n\t"
    "precr_sra_r.ph.w  %[r3],      %[r4],        15        \n\t"
    "precr_sra_r.ph.w  %[r5],      %[r6],        15        \n\t"
    "sw                %[r8],      0(%[outBuf])            \n\t"
    "sw                %[r1],      4(%[outBuf])            \n\t"
    "sw                %[r3],      8(%[outBuf])            \n\t"
    "sw                %[r5],      12(%[outBuf])           \n\t"
#else
    "shra_r.w          %[r8],      %[r8],        15        \n\t"
    "shra_r.w          %[r0],      %[r0],        15        \n\t"
    "shra_r.w          %[r1],      %[r1],        15        \n\t"
    "shra_r.w          %[r2],      %[r2],        15        \n\t"
    "shra_r.w          %[r3],      %[r3],        15        \n\t"
    "shra_r.w          %[r4],      %[r4],        15        \n\t"
    "shra_r.w          %[r5],      %[r5],        15        \n\t"
    "shra_r.w          %[r6],      %[r6],        15        \n\t"
    "sll               %[r0],      %[r0],        16        \n\t"
    "sll               %[r2],      %[r2],        16        \n\t"
    "sll               %[r4],      %[r4],        16        \n\t"
    "sll               %[r6],      %[r6],        16        \n\t"
    "packrl.ph         %[r0],      %[r8],        %[r0]     \n\t"
    "packrl.ph         %[r2],      %[r1],        %[r2]     \n\t"
    "packrl.ph         %[r4],      %[r3],        %[r4]     \n\t"
    "packrl.ph         %[r6],      %[r5],        %[r6]     \n\t"
    "sw                %[r0],      0(%[outBuf])            \n\t"
    "sw                %[r2],      4(%[outBuf])            \n\t"
    "sw                %[r4],      8(%[outBuf])            \n\t"
    "sw                %[r6],      12(%[outBuf])           \n\t"
#endif
    "addiu             %[window],  %[window],    16        \n\t"
    "addiu             %[anaBuf],  %[anaBuf],    16        \n\t"
    "addiu             %[outBuf],  %[outBuf],    16        \n\t"
    "b                 1b                                  \n\t"
    " addiu            %[iters],   %[iters],     -1        \n\t"
   "2:                                                     \n\t"
    "andi              %[after],   %[anaLen],    7         \n\t"
   "3:                                                     \n\t"
    "blez              %[after],   4f                      \n\t"
    " nop                                                  \n\t"
    "lh                %[r0],      0(%[window])            \n\t"
    "lh                %[r1],      0(%[anaBuf])            \n\t"
    "mul               %[r0],      %[r0],        %[r1]     \n\t"
    "addiu             %[window],  %[window],    2         \n\t"
    "addiu             %[anaBuf],  %[anaBuf],    2         \n\t"
    "addiu             %[outBuf],  %[outBuf],    2         \n\t"
    "shra_r.w          %[r0],      %[r0],        14        \n\t"
    "sh                %[r0],      -2(%[outBuf])           \n\t"
    "b                 3b                                  \n\t"
    " addiu            %[after],   %[after],     -1        \n\t"
   "4:                                                     \n\t"
    ".set              pop                                 \n\t"
    : [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2),
      [r3] "=&r" (r3), [r4] "=&r" (r4), [r5] "=&r" (r5),
      [r6] "=&r" (r6), [r7] "=&r" (r7), [r8] "=&r" (r8),
      [iters] "=&r" (iters), [after] "=&r" (after),
      [window] "+r" (window),[anaBuf] "+r" (anaBuf),
      [outBuf] "+r" (outBuf)
    : [anaLen] "r" (anaLen)
    : "memory", "hi", "lo"
  );
#else
  __asm  __volatile(
    ".set           push                                    \n\t"
    ".set           noreorder                               \n\t"
    "sra            %[iters],   %[anaLen],      2           \n\t"
   "1:                                                      \n\t"
    "blez           %[iters],   2f                          \n\t"
    " nop                                                   \n\t"
    "lh             %[r0],      0(%[window])                \n\t"
    "lh             %[r1],      0(%[anaBuf])                \n\t"
    "lh             %[r2],      2(%[window])                \n\t"
    "lh             %[r3],      2(%[anaBuf])                \n\t"
    "lh             %[r4],      4(%[window])                \n\t"
    "lh             %[r5],      4(%[anaBuf])                \n\t"
    "lh             %[r6],      6(%[window])                \n\t"
    "lh             %[r7],      6(%[anaBuf])                \n\t"
    "mul            %[r0],      %[r0],          %[r1]       \n\t"
    "mul            %[r2],      %[r2],          %[r3]       \n\t"
    "mul            %[r4],      %[r4],          %[r5]       \n\t"
    "mul            %[r6],      %[r6],          %[r7]       \n\t"
    "addiu          %[window],  %[window],      8           \n\t"
    "addiu          %[anaBuf],  %[anaBuf],      8           \n\t"
    "addiu          %[r0],      %[r0],          0x2000      \n\t"
    "addiu          %[r2],      %[r2],          0x2000      \n\t"
    "addiu          %[r4],      %[r4],          0x2000      \n\t"
    "addiu          %[r6],      %[r6],          0x2000      \n\t"
    "sra            %[r0],      %[r0],          14          \n\t"
    "sra            %[r2],      %[r2],          14          \n\t"
    "sra            %[r4],      %[r4],          14          \n\t"
    "sra            %[r6],      %[r6],          14          \n\t"
    "sh             %[r0],      0(%[outBuf])                \n\t"
    "sh             %[r2],      2(%[outBuf])                \n\t"
    "sh             %[r4],      4(%[outBuf])                \n\t"
    "sh             %[r6],      6(%[outBuf])                \n\t"
    "addiu          %[outBuf],  %[outBuf],      8           \n\t"
    "b              1b                                      \n\t"
    " addiu         %[iters],   %[iters],       -1          \n\t"
   "2:                                                      \n\t"
    "andi           %[after],   %[anaLen],      3           \n\t"
   "3:                                                      \n\t"
    "blez           %[after],   4f                          \n\t"
    " nop                                                   \n\t"
    "lh             %[r0],      0(%[window])                \n\t"
    "lh             %[r1],      0(%[anaBuf])                \n\t"
    "mul            %[r0],      %[r0],          %[r1]       \n\t"
    "addiu          %[window],  %[window],      2           \n\t"
    "addiu          %[anaBuf],  %[anaBuf],      2           \n\t"
    "addiu          %[outBuf],  %[outBuf],      2           \n\t"
    "addiu          %[r0],      %[r0],          0x2000      \n\t"
    "sra            %[r0],      %[r0],          14          \n\t"
    "sh             %[r0],      -2(%[outBuf])               \n\t"
    "b              3b                                      \n\t"
    " addiu         %[after],   %[after],       -1          \n\t"
   "4:                                                      \n\t"
    ".set           pop                                     \n\t"
    : [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2),
      [r3] "=&r" (r3), [r4] "=&r" (r4), [r5] "=&r" (r5),
      [r6] "=&r" (r6), [r7] "=&r" (r7), [iters] "=&r" (iters),
      [after] "=&r" (after), [window] "+r" (window),
      [anaBuf] "+r" (anaBuf), [outBuf] "+r" (outBuf)
    : [anaLen] "r" (anaLen)
    : "memory", "hi", "lo"
  );
#endif
}

// For the noise supression process, synthesis, read out fully processed
// segment, and update synthesis buffer.
void WebRtcNsx_SynthesisUpdate_mips(NoiseSuppressionFixedC* inst,
                                    int16_t* out_frame,
                                    int16_t gain_factor) {
  int iters = (int)inst->blockLen10ms >> 2;
  int after = inst->blockLen10ms & 3;
  int r0, r1, r2, r3, r4, r5, r6, r7;
  int16_t *window = (int16_t*)inst->window;
  int16_t *real = inst->real;
  int16_t *synthBuf = inst->synthesisBuffer;
  int16_t *out = out_frame;
  int sat_pos = 0x7fff;
  int sat_neg = 0xffff8000;
  int block10 = (int)inst->blockLen10ms;
  int anaLen = (int)inst->anaLen;

  __asm __volatile(
    ".set       push                                        \n\t"
    ".set       noreorder                                   \n\t"
   "1:                                                      \n\t"
    "blez       %[iters],   2f                              \n\t"
    " nop                                                   \n\t"
    "lh         %[r0],      0(%[window])                    \n\t"
    "lh         %[r1],      0(%[real])                      \n\t"
    "lh         %[r2],      2(%[window])                    \n\t"
    "lh         %[r3],      2(%[real])                      \n\t"
    "lh         %[r4],      4(%[window])                    \n\t"
    "lh         %[r5],      4(%[real])                      \n\t"
    "lh         %[r6],      6(%[window])                    \n\t"
    "lh         %[r7],      6(%[real])                      \n\t"
    "mul        %[r0],      %[r0],          %[r1]           \n\t"
    "mul        %[r2],      %[r2],          %[r3]           \n\t"
    "mul        %[r4],      %[r4],          %[r5]           \n\t"
    "mul        %[r6],      %[r6],          %[r7]           \n\t"
    "addiu      %[r0],      %[r0],          0x2000          \n\t"
    "addiu      %[r2],      %[r2],          0x2000          \n\t"
    "addiu      %[r4],      %[r4],          0x2000          \n\t"
    "addiu      %[r6],      %[r6],          0x2000          \n\t"
    "sra        %[r0],      %[r0],          14              \n\t"
    "sra        %[r2],      %[r2],          14              \n\t"
    "sra        %[r4],      %[r4],          14              \n\t"
    "sra        %[r6],      %[r6],          14              \n\t"
    "mul        %[r0],      %[r0],          %[gain_factor]  \n\t"
    "mul        %[r2],      %[r2],          %[gain_factor]  \n\t"
    "mul        %[r4],      %[r4],          %[gain_factor]  \n\t"
    "mul        %[r6],      %[r6],          %[gain_factor]  \n\t"
    "addiu      %[r0],      %[r0],          0x1000          \n\t"
    "addiu      %[r2],      %[r2],          0x1000          \n\t"
    "addiu      %[r4],      %[r4],          0x1000          \n\t"
    "addiu      %[r6],      %[r6],          0x1000          \n\t"
    "sra        %[r0],      %[r0],          13              \n\t"
    "sra        %[r2],      %[r2],          13              \n\t"
    "sra        %[r4],      %[r4],          13              \n\t"
    "sra        %[r6],      %[r6],          13              \n\t"
    "slt        %[r1],      %[r0],          %[sat_pos]      \n\t"
    "slt        %[r3],      %[r2],          %[sat_pos]      \n\t"
    "slt        %[r5],      %[r4],          %[sat_pos]      \n\t"
    "slt        %[r7],      %[r6],          %[sat_pos]      \n\t"
    "movz       %[r0],      %[sat_pos],     %[r1]           \n\t"
    "movz       %[r2],      %[sat_pos],     %[r3]           \n\t"
    "movz       %[r4],      %[sat_pos],     %[r5]           \n\t"
    "movz       %[r6],      %[sat_pos],     %[r7]           \n\t"
    "lh         %[r1],      0(%[synthBuf])                  \n\t"
    "lh         %[r3],      2(%[synthBuf])                  \n\t"
    "lh         %[r5],      4(%[synthBuf])                  \n\t"
    "lh         %[r7],      6(%[synthBuf])                  \n\t"
    "addu       %[r0],      %[r0],          %[r1]           \n\t"
    "addu       %[r2],      %[r2],          %[r3]           \n\t"
    "addu       %[r4],      %[r4],          %[r5]           \n\t"
    "addu       %[r6],      %[r6],          %[r7]           \n\t"
    "slt        %[r1],      %[r0],          %[sat_pos]      \n\t"
    "slt        %[r3],      %[r2],          %[sat_pos]      \n\t"
    "slt        %[r5],      %[r4],          %[sat_pos]      \n\t"
    "slt        %[r7],      %[r6],          %[sat_pos]      \n\t"
    "movz       %[r0],      %[sat_pos],     %[r1]           \n\t"
    "movz       %[r2],      %[sat_pos],     %[r3]           \n\t"
    "movz       %[r4],      %[sat_pos],     %[r5]           \n\t"
    "movz       %[r6],      %[sat_pos],     %[r7]           \n\t"
    "slt        %[r1],      %[r0],          %[sat_neg]      \n\t"
    "slt        %[r3],      %[r2],          %[sat_neg]      \n\t"
    "slt        %[r5],      %[r4],          %[sat_neg]      \n\t"
    "slt        %[r7],      %[r6],          %[sat_neg]      \n\t"
    "movn       %[r0],      %[sat_neg],     %[r1]           \n\t"
    "movn       %[r2],      %[sat_neg],     %[r3]           \n\t"
    "movn       %[r4],      %[sat_neg],     %[r5]           \n\t"
    "movn       %[r6],      %[sat_neg],     %[r7]           \n\t"
    "sh         %[r0],      0(%[synthBuf])                  \n\t"
    "sh         %[r2],      2(%[synthBuf])                  \n\t"
    "sh         %[r4],      4(%[synthBuf])                  \n\t"
    "sh         %[r6],      6(%[synthBuf])                  \n\t"
    "sh         %[r0],      0(%[out])                       \n\t"
    "sh         %[r2],      2(%[out])                       \n\t"
    "sh         %[r4],      4(%[out])                       \n\t"
    "sh         %[r6],      6(%[out])                       \n\t"
    "addiu      %[window],  %[window],      8               \n\t"
    "addiu      %[real],    %[real],        8               \n\t"
    "addiu      %[synthBuf],%[synthBuf],    8               \n\t"
    "addiu      %[out],     %[out],         8               \n\t"
    "b          1b                                          \n\t"
    " addiu     %[iters],   %[iters],       -1              \n\t"
   "2:                                                      \n\t"
    "blez       %[after],   3f                              \n\t"
    " subu      %[block10], %[anaLen],      %[block10]      \n\t"
    "lh         %[r0],      0(%[window])                    \n\t"
    "lh         %[r1],      0(%[real])                      \n\t"
    "mul        %[r0],      %[r0],          %[r1]           \n\t"
    "addiu      %[window],  %[window],      2               \n\t"
    "addiu      %[real],    %[real],        2               \n\t"
    "addiu      %[r0],      %[r0],          0x2000          \n\t"
    "sra        %[r0],      %[r0],          14              \n\t"
    "mul        %[r0],      %[r0],          %[gain_factor]  \n\t"
    "addiu      %[r0],      %[r0],          0x1000          \n\t"
    "sra        %[r0],      %[r0],          13              \n\t"
    "slt        %[r1],      %[r0],          %[sat_pos]      \n\t"
    "movz       %[r0],      %[sat_pos],     %[r1]           \n\t"
    "lh         %[r1],      0(%[synthBuf])                  \n\t"
    "addu       %[r0],      %[r0],          %[r1]           \n\t"
    "slt        %[r1],      %[r0],          %[sat_pos]      \n\t"
    "movz       %[r0],      %[sat_pos],     %[r1]           \n\t"
    "slt        %[r1],      %[r0],          %[sat_neg]      \n\t"
    "movn       %[r0],      %[sat_neg],     %[r1]           \n\t"
    "sh         %[r0],      0(%[synthBuf])                  \n\t"
    "sh         %[r0],      0(%[out])                       \n\t"
    "addiu      %[synthBuf],%[synthBuf],    2               \n\t"
    "addiu      %[out],     %[out],         2               \n\t"
    "b          2b                                          \n\t"
    " addiu     %[after],   %[after],       -1              \n\t"
   "3:                                                      \n\t"
    "sra        %[iters],   %[block10],     2               \n\t"
   "4:                                                      \n\t"
    "blez       %[iters],   5f                              \n\t"
    " andi      %[after],   %[block10],     3               \n\t"
    "lh         %[r0],      0(%[window])                    \n\t"
    "lh         %[r1],      0(%[real])                      \n\t"
    "lh         %[r2],      2(%[window])                    \n\t"
    "lh         %[r3],      2(%[real])                      \n\t"
    "lh         %[r4],      4(%[window])                    \n\t"
    "lh         %[r5],      4(%[real])                      \n\t"
    "lh         %[r6],      6(%[window])                    \n\t"
    "lh         %[r7],      6(%[real])                      \n\t"
    "mul        %[r0],      %[r0],          %[r1]           \n\t"
    "mul        %[r2],      %[r2],          %[r3]           \n\t"
    "mul        %[r4],      %[r4],          %[r5]           \n\t"
    "mul        %[r6],      %[r6],          %[r7]           \n\t"
    "addiu      %[r0],      %[r0],          0x2000          \n\t"
    "addiu      %[r2],      %[r2],          0x2000          \n\t"
    "addiu      %[r4],      %[r4],          0x2000          \n\t"
    "addiu      %[r6],      %[r6],          0x2000          \n\t"
    "sra        %[r0],      %[r0],          14              \n\t"
    "sra        %[r2],      %[r2],          14              \n\t"
    "sra        %[r4],      %[r4],          14              \n\t"
    "sra        %[r6],      %[r6],          14              \n\t"
    "mul        %[r0],      %[r0],          %[gain_factor]  \n\t"
    "mul        %[r2],      %[r2],          %[gain_factor]  \n\t"
    "mul        %[r4],      %[r4],          %[gain_factor]  \n\t"
    "mul        %[r6],      %[r6],          %[gain_factor]  \n\t"
    "addiu      %[r0],      %[r0],          0x1000          \n\t"
    "addiu      %[r2],      %[r2],          0x1000          \n\t"
    "addiu      %[r4],      %[r4],          0x1000          \n\t"
    "addiu      %[r6],      %[r6],          0x1000          \n\t"
    "sra        %[r0],      %[r0],          13              \n\t"
    "sra        %[r2],      %[r2],          13              \n\t"
    "sra        %[r4],      %[r4],          13              \n\t"
    "sra        %[r6],      %[r6],          13              \n\t"
    "slt        %[r1],      %[r0],          %[sat_pos]      \n\t"
    "slt        %[r3],      %[r2],          %[sat_pos]      \n\t"
    "slt        %[r5],      %[r4],          %[sat_pos]      \n\t"
    "slt        %[r7],      %[r6],          %[sat_pos]      \n\t"
    "movz       %[r0],      %[sat_pos],     %[r1]           \n\t"
    "movz       %[r2],      %[sat_pos],     %[r3]           \n\t"
    "movz       %[r4],      %[sat_pos],     %[r5]           \n\t"
    "movz       %[r6],      %[sat_pos],     %[r7]           \n\t"
    "lh         %[r1],      0(%[synthBuf])                  \n\t"
    "lh         %[r3],      2(%[synthBuf])                  \n\t"
    "lh         %[r5],      4(%[synthBuf])                  \n\t"
    "lh         %[r7],      6(%[synthBuf])                  \n\t"
    "addu       %[r0],      %[r0],          %[r1]           \n\t"
    "addu       %[r2],      %[r2],          %[r3]           \n\t"
    "addu       %[r4],      %[r4],          %[r5]           \n\t"
    "addu       %[r6],      %[r6],          %[r7]           \n\t"
    "slt        %[r1],      %[r0],          %[sat_pos]      \n\t"
    "slt        %[r3],      %[r2],          %[sat_pos]      \n\t"
    "slt        %[r5],      %[r4],          %[sat_pos]      \n\t"
    "slt        %[r7],      %[r6],          %[sat_pos]      \n\t"
    "movz       %[r0],      %[sat_pos],     %[r1]           \n\t"
    "movz       %[r2],      %[sat_pos],     %[r3]           \n\t"
    "movz       %[r4],      %[sat_pos],     %[r5]           \n\t"
    "movz       %[r6],      %[sat_pos],     %[r7]           \n\t"
    "slt        %[r1],      %[r0],          %[sat_neg]      \n\t"
    "slt        %[r3],      %[r2],          %[sat_neg]      \n\t"
    "slt        %[r5],      %[r4],          %[sat_neg]      \n\t"
    "slt        %[r7],      %[r6],          %[sat_neg]      \n\t"
    "movn       %[r0],      %[sat_neg],     %[r1]           \n\t"
    "movn       %[r2],      %[sat_neg],     %[r3]           \n\t"
    "movn       %[r4],      %[sat_neg],     %[r5]           \n\t"
    "movn       %[r6],      %[sat_neg],     %[r7]           \n\t"
    "sh         %[r0],      0(%[synthBuf])                  \n\t"
    "sh         %[r2],      2(%[synthBuf])                  \n\t"
    "sh         %[r4],      4(%[synthBuf])                  \n\t"
    "sh         %[r6],      6(%[synthBuf])                  \n\t"
    "addiu      %[window],  %[window],      8               \n\t"
    "addiu      %[real],    %[real],        8               \n\t"
    "addiu      %[synthBuf],%[synthBuf],    8               \n\t"
    "b          4b                                          \n\t"
    " addiu     %[iters],   %[iters],       -1              \n\t"
   "5:                                                      \n\t"
    "blez       %[after],   6f                              \n\t"
    " nop                                                   \n\t"
    "lh         %[r0],      0(%[window])                    \n\t"
    "lh         %[r1],      0(%[real])                      \n\t"
    "mul        %[r0],      %[r0],          %[r1]           \n\t"
    "addiu      %[window],  %[window],      2               \n\t"
    "addiu      %[real],    %[real],        2               \n\t"
    "addiu      %[r0],      %[r0],          0x2000          \n\t"
    "sra        %[r0],      %[r0],          14              \n\t"
    "mul        %[r0],      %[r0],          %[gain_factor]  \n\t"
    "addiu      %[r0],      %[r0],          0x1000          \n\t"
    "sra        %[r0],      %[r0],          13              \n\t"
    "slt        %[r1],      %[r0],          %[sat_pos]      \n\t"
    "movz       %[r0],      %[sat_pos],     %[r1]           \n\t"
    "lh         %[r1],      0(%[synthBuf])                  \n\t"
    "addu       %[r0],      %[r0],          %[r1]           \n\t"
    "slt        %[r1],      %[r0],          %[sat_pos]      \n\t"
    "movz       %[r0],      %[sat_pos],     %[r1]           \n\t"
    "slt        %[r1],      %[r0],          %[sat_neg]      \n\t"
    "movn       %[r0],      %[sat_neg],     %[r1]           \n\t"
    "sh         %[r0],      0(%[synthBuf])                  \n\t"
    "addiu      %[synthBuf],%[synthBuf],    2               \n\t"
    "b          2b                                          \n\t"
    " addiu     %[after],   %[after],       -1              \n\t"
   "6:                                                      \n\t"
    ".set       pop                                         \n\t"
    : [r0] "=&r" (r0), [r1] "=&r" (r1), [r2] "=&r" (r2),
      [r3] "=&r" (r3), [r4] "=&r" (r4), [r5] "=&r" (r5),
      [r6] "=&r" (r6), [r7] "=&r" (r7), [iters] "+r" (iters),
      [after] "+r" (after), [block10] "+r" (block10),
      [window] "+r" (window), [real] "+r" (real),
      [synthBuf] "+r" (synthBuf), [out] "+r" (out)
    : [gain_factor] "r" (gain_factor), [sat_pos] "r" (sat_pos),
      [sat_neg] "r" (sat_neg), [anaLen] "r" (anaLen)
    : "memory", "hi", "lo"
  );

  // update synthesis buffer
  memcpy(inst->synthesisBuffer, inst->synthesisBuffer + inst->blockLen10ms,
      (inst->anaLen - inst->blockLen10ms) * sizeof(*inst->synthesisBuffer));
  WebRtcSpl_ZerosArrayW16(inst->synthesisBuffer
      + inst->anaLen - inst->blockLen10ms, inst->blockLen10ms);
}

// Filter the data in the frequency domain, and create spectrum.
void WebRtcNsx_PrepareSpectrum_mips(NoiseSuppressionFixedC* inst,
                                    int16_t* freq_buf) {
  uint16_t *noiseSupFilter = inst->noiseSupFilter;
  int16_t *real = inst->real;
  int16_t *imag = inst->imag;
  int32_t loop_count = 2;
  int16_t tmp_1, tmp_2, tmp_3, tmp_4, tmp_5, tmp_6;
  int16_t tmp16 = (int16_t)(inst->anaLen << 1) - 4;
  int16_t* freq_buf_f = freq_buf;
  int16_t* freq_buf_s = &freq_buf[tmp16];

  __asm __volatile (
    ".set       push                                                 \n\t"
    ".set       noreorder                                            \n\t"
    //first sample
    "lh         %[tmp_1],           0(%[noiseSupFilter])             \n\t"
    "lh         %[tmp_2],           0(%[real])                       \n\t"
    "lh         %[tmp_3],           0(%[imag])                       \n\t"
    "mul        %[tmp_2],           %[tmp_2],             %[tmp_1]   \n\t"
    "mul        %[tmp_3],           %[tmp_3],             %[tmp_1]   \n\t"
    "sra        %[tmp_2],           %[tmp_2],             14         \n\t"
    "sra        %[tmp_3],           %[tmp_3],             14         \n\t"
    "sh         %[tmp_2],           0(%[real])                       \n\t"
    "sh         %[tmp_3],           0(%[imag])                       \n\t"
    "negu       %[tmp_3],           %[tmp_3]                         \n\t"
    "sh         %[tmp_2],           0(%[freq_buf_f])                 \n\t"
    "sh         %[tmp_3],           2(%[freq_buf_f])                 \n\t"
    "addiu      %[real],            %[real],              2          \n\t"
    "addiu      %[imag],            %[imag],              2          \n\t"
    "addiu      %[noiseSupFilter],  %[noiseSupFilter],    2          \n\t"
    "addiu      %[freq_buf_f],      %[freq_buf_f],        4          \n\t"
   "1:                                                               \n\t"
    "lh         %[tmp_1],           0(%[noiseSupFilter])             \n\t"
    "lh         %[tmp_2],           0(%[real])                       \n\t"
    "lh         %[tmp_3],           0(%[imag])                       \n\t"
    "lh         %[tmp_4],           2(%[noiseSupFilter])             \n\t"
    "lh         %[tmp_5],           2(%[real])                       \n\t"
    "lh         %[tmp_6],           2(%[imag])                       \n\t"
    "mul        %[tmp_2],           %[tmp_2],             %[tmp_1]   \n\t"
    "mul        %[tmp_3],           %[tmp_3],             %[tmp_1]   \n\t"
    "mul        %[tmp_5],           %[tmp_5],             %[tmp_4]   \n\t"
    "mul        %[tmp_6],           %[tmp_6],             %[tmp_4]   \n\t"
    "addiu      %[loop_count],      %[loop_count],        2          \n\t"
    "sra        %[tmp_2],           %[tmp_2],             14         \n\t"
    "sra        %[tmp_3],           %[tmp_3],             14         \n\t"
    "sra        %[tmp_5],           %[tmp_5],             14         \n\t"
    "sra        %[tmp_6],           %[tmp_6],             14         \n\t"
    "addiu      %[noiseSupFilter],  %[noiseSupFilter],    4          \n\t"
    "sh         %[tmp_2],           0(%[real])                       \n\t"
    "sh         %[tmp_2],           4(%[freq_buf_s])                 \n\t"
    "sh         %[tmp_3],           0(%[imag])                       \n\t"
    "sh         %[tmp_3],           6(%[freq_buf_s])                 \n\t"
    "negu       %[tmp_3],           %[tmp_3]                         \n\t"
    "sh         %[tmp_5],           2(%[real])                       \n\t"
    "sh         %[tmp_5],           0(%[freq_buf_s])                 \n\t"
    "sh         %[tmp_6],           2(%[imag])                       \n\t"
    "sh         %[tmp_6],           2(%[freq_buf_s])                 \n\t"
    "negu       %[tmp_6],           %[tmp_6]                         \n\t"
    "addiu      %[freq_buf_s],      %[freq_buf_s],        -8         \n\t"
    "addiu      %[real],            %[real],              4          \n\t"
    "addiu      %[imag],            %[imag],              4          \n\t"
    "sh         %[tmp_2],           0(%[freq_buf_f])                 \n\t"
    "sh         %[tmp_3],           2(%[freq_buf_f])                 \n\t"
    "sh         %[tmp_5],           4(%[freq_buf_f])                 \n\t"
    "sh         %[tmp_6],           6(%[freq_buf_f])                 \n\t"
    "blt        %[loop_count],      %[loop_size],         1b         \n\t"
    " addiu     %[freq_buf_f],      %[freq_buf_f],        8          \n\t"
    //last two samples:
    "lh         %[tmp_1],           0(%[noiseSupFilter])             \n\t"
    "lh         %[tmp_2],           0(%[real])                       \n\t"
    "lh         %[tmp_3],           0(%[imag])                       \n\t"
    "lh         %[tmp_4],           2(%[noiseSupFilter])             \n\t"
    "lh         %[tmp_5],           2(%[real])                       \n\t"
    "lh         %[tmp_6],           2(%[imag])                       \n\t"
    "mul        %[tmp_2],           %[tmp_2],             %[tmp_1]   \n\t"
    "mul        %[tmp_3],           %[tmp_3],             %[tmp_1]   \n\t"
    "mul        %[tmp_5],           %[tmp_5],             %[tmp_4]   \n\t"
    "mul        %[tmp_6],           %[tmp_6],             %[tmp_4]   \n\t"
    "sra        %[tmp_2],           %[tmp_2],             14         \n\t"
    "sra        %[tmp_3],           %[tmp_3],             14         \n\t"
    "sra        %[tmp_5],           %[tmp_5],             14         \n\t"
    "sra        %[tmp_6],           %[tmp_6],             14         \n\t"
    "sh         %[tmp_2],           0(%[real])                       \n\t"
    "sh         %[tmp_2],           4(%[freq_buf_s])                 \n\t"
    "sh         %[tmp_3],           0(%[imag])                       \n\t"
    "sh         %[tmp_3],           6(%[freq_buf_s])                 \n\t"
    "negu       %[tmp_3],           %[tmp_3]                         \n\t"
    "sh         %[tmp_2],           0(%[freq_buf_f])                 \n\t"
    "sh         %[tmp_3],           2(%[freq_buf_f])                 \n\t"
    "sh         %[tmp_5],           4(%[freq_buf_f])                 \n\t"
    "sh         %[tmp_6],           6(%[freq_buf_f])                 \n\t"
    "sh         %[tmp_5],           2(%[real])                       \n\t"
    "sh         %[tmp_6],           2(%[imag])                       \n\t"
    ".set       pop                                                  \n\t"
    : [real] "+r" (real), [imag] "+r" (imag),
      [freq_buf_f] "+r" (freq_buf_f), [freq_buf_s] "+r" (freq_buf_s),
      [loop_count] "+r" (loop_count), [noiseSupFilter] "+r" (noiseSupFilter),
      [tmp_1] "=&r" (tmp_1), [tmp_2] "=&r" (tmp_2), [tmp_3] "=&r" (tmp_3),
      [tmp_4] "=&r" (tmp_4), [tmp_5] "=&r" (tmp_5), [tmp_6] "=&r" (tmp_6)
    : [loop_size] "r" (inst->anaLen2)
    : "memory", "hi", "lo"
  );
}

#if defined(MIPS_DSP_R1_LE)
// Denormalize the real-valued signal |in|, the output from inverse FFT.
void WebRtcNsx_Denormalize_mips(NoiseSuppressionFixedC* inst,
                                int16_t* in,
                                int factor) {
  int32_t r0, r1, r2, r3, t0;
  int len = (int)inst->anaLen;
  int16_t *out = &inst->real[0];
  int shift = factor - inst->normData;

  __asm __volatile (
    ".set          push                                \n\t"
    ".set          noreorder                           \n\t"
    "beqz          %[len],     8f                      \n\t"
    " nop                                              \n\t"
    "bltz          %[shift],   4f                      \n\t"
    " sra          %[t0],      %[len],      2          \n\t"
    "beqz          %[t0],      2f                      \n\t"
    " andi         %[len],     %[len],      3          \n\t"
   "1:                                                 \n\t"
    "lh            %[r0],      0(%[in])                \n\t"
    "lh            %[r1],      2(%[in])                \n\t"
    "lh            %[r2],      4(%[in])                \n\t"
    "lh            %[r3],      6(%[in])                \n\t"
    "shllv_s.ph    %[r0],      %[r0],       %[shift]   \n\t"
    "shllv_s.ph    %[r1],      %[r1],       %[shift]   \n\t"
    "shllv_s.ph    %[r2],      %[r2],       %[shift]   \n\t"
    "shllv_s.ph    %[r3],      %[r3],       %[shift]   \n\t"
    "addiu         %[in],      %[in],       8          \n\t"
    "addiu         %[t0],      %[t0],       -1         \n\t"
    "sh            %[r0],      0(%[out])               \n\t"
    "sh            %[r1],      2(%[out])               \n\t"
    "sh            %[r2],      4(%[out])               \n\t"
    "sh            %[r3],      6(%[out])               \n\t"
    "bgtz          %[t0],      1b                      \n\t"
    " addiu        %[out],     %[out],      8          \n\t"
   "2:                                                 \n\t"
    "beqz          %[len],     8f                      \n\t"
    " nop                                              \n\t"
   "3:                                                 \n\t"
    "lh            %[r0],      0(%[in])                \n\t"
    "addiu         %[in],      %[in],       2          \n\t"
    "addiu         %[len],     %[len],      -1         \n\t"
    "shllv_s.ph    %[r0],      %[r0],       %[shift]   \n\t"
    "addiu         %[out],     %[out],      2          \n\t"
    "bgtz          %[len],     3b                      \n\t"
    " sh           %[r0],      -2(%[out])              \n\t"
    "b             8f                                  \n\t"
   "4:                                                 \n\t"
    "negu          %[shift],   %[shift]                \n\t"
    "beqz          %[t0],      6f                      \n\t"
    " andi         %[len],     %[len],      3          \n\t"
   "5:                                                 \n\t"
    "lh            %[r0],      0(%[in])                \n\t"
    "lh            %[r1],      2(%[in])                \n\t"
    "lh            %[r2],      4(%[in])                \n\t"
    "lh            %[r3],      6(%[in])                \n\t"
    "srav          %[r0],      %[r0],       %[shift]   \n\t"
    "srav          %[r1],      %[r1],       %[shift]   \n\t"
    "srav          %[r2],      %[r2],       %[shift]   \n\t"
    "srav          %[r3],      %[r3],       %[shift]   \n\t"
    "addiu         %[in],      %[in],       8          \n\t"
    "addiu         %[t0],      %[t0],       -1         \n\t"
    "sh            %[r0],      0(%[out])               \n\t"
    "sh            %[r1],      2(%[out])               \n\t"
    "sh            %[r2],      4(%[out])               \n\t"
    "sh            %[r3],      6(%[out])               \n\t"
    "bgtz          %[t0],      5b                      \n\t"
    " addiu        %[out],     %[out],      8          \n\t"
   "6:                                                 \n\t"
    "beqz          %[len],     8f                      \n\t"
    " nop                                              \n\t"
   "7:                                                 \n\t"
    "lh            %[r0],      0(%[in])                \n\t"
    "addiu         %[in],      %[in],       2          \n\t"
    "addiu         %[len],     %[len],      -1         \n\t"
    "srav          %[r0],      %[r0],       %[shift]   \n\t"
    "addiu         %[out],     %[out],      2          \n\t"
    "bgtz          %[len],     7b                      \n\t"
    " sh           %[r0],      -2(%[out])              \n\t"
   "8:                                                 \n\t"
    ".set          pop                                 \n\t"
    : [t0] "=&r" (t0), [r0] "=&r" (r0), [r1] "=&r" (r1),
      [r2] "=&r" (r2), [r3] "=&r" (r3)
    : [len] "r" (len), [shift] "r" (shift), [in] "r" (in),
      [out] "r" (out)
    : "memory"
  );
}
#endif

// Normalize the real-valued signal |in|, the input to forward FFT.
void WebRtcNsx_NormalizeRealBuffer_mips(NoiseSuppressionFixedC* inst,
                                        const int16_t* in,
                                        int16_t* out) {
  int32_t r0, r1, r2, r3, t0;
  int len = (int)inst->anaLen;
  int shift = inst->normData;

  __asm __volatile (
    ".set          push                                \n\t"
    ".set          noreorder                           \n\t"
    "beqz          %[len],     4f                      \n\t"
    " sra          %[t0],      %[len],      2          \n\t"
    "beqz          %[t0],      2f                      \n\t"
    " andi         %[len],     %[len],      3          \n\t"
   "1:                                                 \n\t"
    "lh            %[r0],      0(%[in])                \n\t"
    "lh            %[r1],      2(%[in])                \n\t"
    "lh            %[r2],      4(%[in])                \n\t"
    "lh            %[r3],      6(%[in])                \n\t"
    "sllv          %[r0],      %[r0],       %[shift]   \n\t"
    "sllv          %[r1],      %[r1],       %[shift]   \n\t"
    "sllv          %[r2],      %[r2],       %[shift]   \n\t"
    "sllv          %[r3],      %[r3],       %[shift]   \n\t"
    "addiu         %[in],      %[in],       8          \n\t"
    "addiu         %[t0],      %[t0],       -1         \n\t"
    "sh            %[r0],      0(%[out])               \n\t"
    "sh            %[r1],      2(%[out])               \n\t"
    "sh            %[r2],      4(%[out])               \n\t"
    "sh            %[r3],      6(%[out])               \n\t"
    "bgtz          %[t0],      1b                      \n\t"
    " addiu        %[out],     %[out],      8          \n\t"
   "2:                                                 \n\t"
    "beqz          %[len],     4f                      \n\t"
    " nop                                              \n\t"
   "3:                                                 \n\t"
    "lh            %[r0],      0(%[in])                \n\t"
    "addiu         %[in],      %[in],       2          \n\t"
    "addiu         %[len],     %[len],      -1         \n\t"
    "sllv          %[r0],      %[r0],       %[shift]   \n\t"
    "addiu         %[out],     %[out],      2          \n\t"
    "bgtz          %[len],     3b                      \n\t"
    " sh           %[r0],      -2(%[out])              \n\t"
   "4:                                                 \n\t"
    ".set          pop                                 \n\t"
    : [t0] "=&r" (t0), [r0] "=&r" (r0), [r1] "=&r" (r1),
      [r2] "=&r" (r2), [r3] "=&r" (r3)
    : [len] "r" (len), [shift] "r" (shift), [in] "r" (in),
      [out] "r" (out)
    : "memory"
  );
}

