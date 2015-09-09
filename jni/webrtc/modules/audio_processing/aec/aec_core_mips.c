/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * The core AEC algorithm, which is presented with time-aligned signals.
 */

#include "webrtc/modules/audio_processing/aec/aec_core.h"

#include <math.h>

#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
#include "webrtc/modules/audio_processing/aec/aec_core_internal.h"
#include "webrtc/modules/audio_processing/aec/aec_rdft.h"

static const int flagHbandCn = 1; // flag for adding comfort noise in H band
extern const float WebRtcAec_weightCurve[65];
extern const float WebRtcAec_overDriveCurve[65];

void WebRtcAec_ComfortNoise_mips(AecCore* aec,
                                 float efw[2][PART_LEN1],
                                 complex_t* comfortNoiseHband,
                                 const float* noisePow,
                                 const float* lambda) {
  int i, num;
  float rand[PART_LEN];
  float noise, noiseAvg, tmp, tmpAvg;
  int16_t randW16[PART_LEN];
  complex_t u[PART_LEN1];

  const float pi2 = 6.28318530717959f;
  const float pi2t = pi2 / 32768;

  // Generate a uniform random array on [0 1]
  WebRtcSpl_RandUArray(randW16, PART_LEN, &aec->seed);

  int16_t *randWptr = randW16;
  float randTemp, randTemp2, randTemp3, randTemp4;
  short tmp1s, tmp2s, tmp3s, tmp4s;

  for (i = 0; i < PART_LEN; i+=4) {
    __asm __volatile (
      ".set     push                                           \n\t"
      ".set     noreorder                                      \n\t"
      "lh       %[tmp1s],       0(%[randWptr])                 \n\t"
      "lh       %[tmp2s],       2(%[randWptr])                 \n\t"
      "lh       %[tmp3s],       4(%[randWptr])                 \n\t"
      "lh       %[tmp4s],       6(%[randWptr])                 \n\t"
      "mtc1     %[tmp1s],       %[randTemp]                    \n\t"
      "mtc1     %[tmp2s],       %[randTemp2]                   \n\t"
      "mtc1     %[tmp3s],       %[randTemp3]                   \n\t"
      "mtc1     %[tmp4s],       %[randTemp4]                   \n\t"
      "cvt.s.w  %[randTemp],    %[randTemp]                    \n\t"
      "cvt.s.w  %[randTemp2],   %[randTemp2]                   \n\t"
      "cvt.s.w  %[randTemp3],   %[randTemp3]                   \n\t"
      "cvt.s.w  %[randTemp4],   %[randTemp4]                   \n\t"
      "addiu    %[randWptr],    %[randWptr],      8            \n\t"
      "mul.s    %[randTemp],    %[randTemp],      %[pi2t]      \n\t"
      "mul.s    %[randTemp2],   %[randTemp2],     %[pi2t]      \n\t"
      "mul.s    %[randTemp3],   %[randTemp3],     %[pi2t]      \n\t"
      "mul.s    %[randTemp4],   %[randTemp4],     %[pi2t]      \n\t"
      ".set     pop                                            \n\t"
      : [randWptr] "+r" (randWptr), [randTemp] "=&f" (randTemp),
        [randTemp2] "=&f" (randTemp2), [randTemp3] "=&f" (randTemp3),
        [randTemp4] "=&f" (randTemp4), [tmp1s] "=&r" (tmp1s),
        [tmp2s] "=&r" (tmp2s), [tmp3s] "=&r" (tmp3s),
        [tmp4s] "=&r" (tmp4s)
      : [pi2t] "f" (pi2t)
      : "memory"
    );

    u[i+1][0] = (float)cos(randTemp);
    u[i+1][1] = (float)sin(randTemp);
    u[i+2][0] = (float)cos(randTemp2);
    u[i+2][1] = (float)sin(randTemp2);
    u[i+3][0] = (float)cos(randTemp3);
    u[i+3][1] = (float)sin(randTemp3);
    u[i+4][0] = (float)cos(randTemp4);
    u[i+4][1] = (float)sin(randTemp4);
  }

  // Reject LF noise
  float *u_ptr = &u[1][0];
  float noise2, noise3, noise4;
  float tmp1f, tmp2f, tmp3f, tmp4f, tmp5f, tmp6f, tmp7f, tmp8f;

  u[0][0] = 0;
  u[0][1] = 0;
  for (i = 1; i < PART_LEN1; i+=4) {
    __asm __volatile (
      ".set     push                                            \n\t"
      ".set     noreorder                                       \n\t"
      "lwc1     %[noise],       4(%[noisePow])                  \n\t"
      "lwc1     %[noise2],      8(%[noisePow])                  \n\t"
      "lwc1     %[noise3],      12(%[noisePow])                 \n\t"
      "lwc1     %[noise4],      16(%[noisePow])                 \n\t"
      "sqrt.s   %[noise],       %[noise]                        \n\t"
      "sqrt.s   %[noise2],      %[noise2]                       \n\t"
      "sqrt.s   %[noise3],      %[noise3]                       \n\t"
      "sqrt.s   %[noise4],      %[noise4]                       \n\t"
      "lwc1     %[tmp1f],       0(%[u_ptr])                     \n\t"
      "lwc1     %[tmp2f],       4(%[u_ptr])                     \n\t"
      "lwc1     %[tmp3f],       8(%[u_ptr])                     \n\t"
      "lwc1     %[tmp4f],       12(%[u_ptr])                    \n\t"
      "lwc1     %[tmp5f],       16(%[u_ptr])                    \n\t"
      "lwc1     %[tmp6f],       20(%[u_ptr])                    \n\t"
      "lwc1     %[tmp7f],       24(%[u_ptr])                    \n\t"
      "lwc1     %[tmp8f],       28(%[u_ptr])                    \n\t"
      "addiu    %[noisePow],    %[noisePow],      16            \n\t"
      "mul.s    %[tmp1f],       %[tmp1f],         %[noise]      \n\t"
      "mul.s    %[tmp2f],       %[tmp2f],         %[noise]      \n\t"
      "mul.s    %[tmp3f],       %[tmp3f],         %[noise2]     \n\t"
      "mul.s    %[tmp4f],       %[tmp4f],         %[noise2]     \n\t"
      "mul.s    %[tmp5f],       %[tmp5f],         %[noise3]     \n\t"
      "mul.s    %[tmp6f],       %[tmp6f],         %[noise3]     \n\t"
      "swc1     %[tmp1f],       0(%[u_ptr])                     \n\t"
      "swc1     %[tmp3f],       8(%[u_ptr])                     \n\t"
      "mul.s    %[tmp8f],       %[tmp8f],         %[noise4]     \n\t"
      "mul.s    %[tmp7f],       %[tmp7f],         %[noise4]     \n\t"
      "neg.s    %[tmp2f]                                        \n\t"
      "neg.s    %[tmp4f]                                        \n\t"
      "neg.s    %[tmp6f]                                        \n\t"
      "neg.s    %[tmp8f]                                        \n\t"
      "swc1     %[tmp5f],       16(%[u_ptr])                    \n\t"
      "swc1     %[tmp7f],       24(%[u_ptr])                    \n\t"
      "swc1     %[tmp2f],       4(%[u_ptr])                     \n\t"
      "swc1     %[tmp4f],       12(%[u_ptr])                    \n\t"
      "swc1     %[tmp6f],       20(%[u_ptr])                    \n\t"
      "swc1     %[tmp8f],       28(%[u_ptr])                    \n\t"
      "addiu    %[u_ptr],       %[u_ptr],         32            \n\t"
      ".set     pop                                             \n\t"
      : [u_ptr] "+r" (u_ptr),  [noisePow] "+r" (noisePow),
        [noise] "=&f" (noise), [noise2] "=&f" (noise2),
        [noise3] "=&f" (noise3), [noise4] "=&f" (noise4),
        [tmp1f] "=&f" (tmp1f), [tmp2f] "=&f" (tmp2f),
        [tmp3f] "=&f" (tmp3f), [tmp4f] "=&f" (tmp4f),
        [tmp5f] "=&f" (tmp5f), [tmp6f] "=&f" (tmp6f),
        [tmp7f] "=&f" (tmp7f), [tmp8f] "=&f" (tmp8f)
      :
      : "memory"
    );
  }
  u[PART_LEN][1] = 0;
  noisePow -= PART_LEN;

  u_ptr = &u[0][0];
  float *u_ptr_end = &u[PART_LEN][0];
  float *efw_ptr_0 = &efw[0][0];
  float *efw_ptr_1 = &efw[1][0];
  float tmp9f, tmp10f;
  const float tmp1c = 1.0;
  const float tmp2c = 0.0;

  __asm __volatile (
    ".set     push                                                        \n\t"
    ".set     noreorder                                                   \n\t"
   "1:                                                                    \n\t"
    "lwc1     %[tmp1f],       0(%[lambda])                                \n\t"
    "lwc1     %[tmp6f],       4(%[lambda])                                \n\t"
    "addiu    %[lambda],      %[lambda],   8                              \n\t"
    "c.lt.s   %[tmp1f],       %[tmp1c]                                    \n\t"
    "bc1f     4f                                                          \n\t"
    " nop                                                                 \n\t"
    "c.lt.s   %[tmp6f],       %[tmp1c]                                    \n\t"
    "bc1f     3f                                                          \n\t"
    " nop                                                                 \n\t"
   "2:                                                                    \n\t"
    "mul.s    %[tmp1f],       %[tmp1f],         %[tmp1f]                  \n\t"
    "mul.s    %[tmp6f],       %[tmp6f],         %[tmp6f]                  \n\t"
    "sub.s    %[tmp1f],       %[tmp1c],         %[tmp1f]                  \n\t"
    "sub.s    %[tmp6f],       %[tmp1c],         %[tmp6f]                  \n\t"
    "sqrt.s   %[tmp1f],       %[tmp1f]                                    \n\t"
    "sqrt.s   %[tmp6f],       %[tmp6f]                                    \n\t"
    "lwc1     %[tmp2f],       0(%[efw_ptr_0])                             \n\t"
    "lwc1     %[tmp3f],       0(%[u_ptr])                                 \n\t"
    "lwc1     %[tmp7f],       4(%[efw_ptr_0])                             \n\t"
    "lwc1     %[tmp8f],       8(%[u_ptr])                                 \n\t"
    "lwc1     %[tmp4f],       0(%[efw_ptr_1])                             \n\t"
    "lwc1     %[tmp5f],       4(%[u_ptr])                                 \n\t"
    "lwc1     %[tmp9f],       4(%[efw_ptr_1])                             \n\t"
    "lwc1     %[tmp10f],      12(%[u_ptr])                                \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s    %[tmp3f],       %[tmp1f],         %[tmp3f]                  \n\t"
    "add.s    %[tmp2f],       %[tmp2f],         %[tmp3f]                  \n\t"
    "mul.s    %[tmp3f],       %[tmp1f],         %[tmp5f]                  \n\t"
    "add.s    %[tmp4f],       %[tmp4f],         %[tmp3f]                  \n\t"
    "mul.s    %[tmp3f],       %[tmp6f],         %[tmp8f]                  \n\t"
    "add.s    %[tmp7f],       %[tmp7f],         %[tmp3f]                  \n\t"
    "mul.s    %[tmp3f],       %[tmp6f],         %[tmp10f]                 \n\t"
    "add.s    %[tmp9f],       %[tmp9f],         %[tmp3f]                  \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "madd.s   %[tmp2f],       %[tmp2f],         %[tmp1f],     %[tmp3f]    \n\t"
    "madd.s   %[tmp4f],       %[tmp4f],         %[tmp1f],     %[tmp5f]    \n\t"
    "madd.s   %[tmp7f],       %[tmp7f],         %[tmp6f],     %[tmp8f]    \n\t"
    "madd.s   %[tmp9f],       %[tmp9f],         %[tmp6f],     %[tmp10f]   \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "swc1     %[tmp2f],       0(%[efw_ptr_0])                             \n\t"
    "swc1     %[tmp4f],       0(%[efw_ptr_1])                             \n\t"
    "swc1     %[tmp7f],       4(%[efw_ptr_0])                             \n\t"
    "b        5f                                                          \n\t"
    " swc1    %[tmp9f],       4(%[efw_ptr_1])                             \n\t"
   "3:                                                                    \n\t"
    "mul.s    %[tmp1f],       %[tmp1f],         %[tmp1f]                  \n\t"
    "sub.s    %[tmp1f],       %[tmp1c],         %[tmp1f]                  \n\t"
    "sqrt.s   %[tmp1f],       %[tmp1f]                                    \n\t"
    "lwc1     %[tmp2f],       0(%[efw_ptr_0])                             \n\t"
    "lwc1     %[tmp3f],       0(%[u_ptr])                                 \n\t"
    "lwc1     %[tmp4f],       0(%[efw_ptr_1])                             \n\t"
    "lwc1     %[tmp5f],       4(%[u_ptr])                                 \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s    %[tmp3f],       %[tmp1f],         %[tmp3f]                  \n\t"
    "add.s    %[tmp2f],       %[tmp2f],         %[tmp3f]                  \n\t"
    "mul.s    %[tmp3f],       %[tmp1f],         %[tmp5f]                  \n\t"
    "add.s    %[tmp4f],       %[tmp4f],         %[tmp3f]                  \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "madd.s   %[tmp2f],       %[tmp2f],         %[tmp1f],     %[tmp3f]    \n\t"
    "madd.s   %[tmp4f],       %[tmp4f],         %[tmp1f],     %[tmp5f]    \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "swc1     %[tmp2f],       0(%[efw_ptr_0])                             \n\t"
    "b        5f                                                          \n\t"
    " swc1    %[tmp4f],       0(%[efw_ptr_1])                             \n\t"
   "4:                                                                    \n\t"
    "c.lt.s   %[tmp6f],       %[tmp1c]                                    \n\t"
    "bc1f     5f                                                          \n\t"
    " nop                                                                 \n\t"
    "mul.s    %[tmp6f],       %[tmp6f],         %[tmp6f]                  \n\t"
    "sub.s    %[tmp6f],       %[tmp1c],         %[tmp6f]                  \n\t"
    "sqrt.s   %[tmp6f],       %[tmp6f]                                    \n\t"
    "lwc1     %[tmp7f],       4(%[efw_ptr_0])                             \n\t"
    "lwc1     %[tmp8f],       8(%[u_ptr])                                 \n\t"
    "lwc1     %[tmp9f],       4(%[efw_ptr_1])                             \n\t"
    "lwc1     %[tmp10f],      12(%[u_ptr])                                \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s    %[tmp3f],       %[tmp6f],         %[tmp8f]                  \n\t"
    "add.s    %[tmp7f],       %[tmp7f],         %[tmp3f]                  \n\t"
    "mul.s    %[tmp3f],       %[tmp6f],         %[tmp10f]                 \n\t"
    "add.s    %[tmp9f],       %[tmp9f],         %[tmp3f]                  \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "madd.s   %[tmp7f],       %[tmp7f],         %[tmp6f],     %[tmp8f]    \n\t"
    "madd.s   %[tmp9f],       %[tmp9f],         %[tmp6f],     %[tmp10f]   \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "swc1     %[tmp7f],       4(%[efw_ptr_0])                             \n\t"
    "swc1     %[tmp9f],       4(%[efw_ptr_1])                             \n\t"
   "5:                                                                    \n\t"
    "addiu    %[u_ptr],       %[u_ptr],         16                        \n\t"
    "addiu    %[efw_ptr_0],   %[efw_ptr_0],     8                         \n\t"
    "bne      %[u_ptr],       %[u_ptr_end],     1b                        \n\t"
    " addiu   %[efw_ptr_1],   %[efw_ptr_1],     8                         \n\t"
    ".set     pop                                                         \n\t"
    : [lambda] "+r" (lambda), [u_ptr] "+r" (u_ptr),
      [efw_ptr_0] "+r" (efw_ptr_0), [efw_ptr_1] "+r" (efw_ptr_1),
      [tmp1f] "=&f" (tmp1f), [tmp2f] "=&f" (tmp2f), [tmp3f] "=&f" (tmp3f),
      [tmp4f] "=&f" (tmp4f), [tmp5f] "=&f" (tmp5f),
      [tmp6f] "=&f" (tmp6f), [tmp7f] "=&f" (tmp7f), [tmp8f] "=&f" (tmp8f),
      [tmp9f] "=&f" (tmp9f), [tmp10f] "=&f" (tmp10f)
    : [tmp1c] "f" (tmp1c), [tmp2c] "f" (tmp2c), [u_ptr_end] "r" (u_ptr_end)
    : "memory"
  );

  lambda -= PART_LEN;
  tmp = sqrtf(WEBRTC_SPL_MAX(1 - lambda[PART_LEN] * lambda[PART_LEN], 0));
  //tmp = 1 - lambda[i];
  efw[0][PART_LEN] += tmp * u[PART_LEN][0];
  efw[1][PART_LEN] += tmp * u[PART_LEN][1];

  // For H band comfort noise
  // TODO: don't compute noise and "tmp" twice. Use the previous results.
  noiseAvg = 0.0;
  tmpAvg = 0.0;
  num = 0;
  if (aec->sampFreq == 32000 && flagHbandCn == 1) {
    for (i = 0; i < PART_LEN; i++) {
      rand[i] = ((float)randW16[i]) / 32768;
    }

    // average noise scale
    // average over second half of freq spectrum (i.e., 4->8khz)
    // TODO: we shouldn't need num. We know how many elements we're summing.
    for (i = PART_LEN1 >> 1; i < PART_LEN1; i++) {
      num++;
      noiseAvg += sqrtf(noisePow[i]);
    }
    noiseAvg /= (float)num;

    // average nlp scale
    // average over second half of freq spectrum (i.e., 4->8khz)
    // TODO: we shouldn't need num. We know how many elements we're summing.
    num = 0;
    for (i = PART_LEN1 >> 1; i < PART_LEN1; i++) {
      num++;
      tmpAvg += sqrtf(WEBRTC_SPL_MAX(1 - lambda[i] * lambda[i], 0));
    }
    tmpAvg /= (float)num;

    // Use average noise for H band
    // TODO: we should probably have a new random vector here.
    // Reject LF noise
    u[0][0] = 0;
    u[0][1] = 0;
    for (i = 1; i < PART_LEN1; i++) {
      tmp = pi2 * rand[i - 1];

      // Use average noise for H band
      u[i][0] = noiseAvg * (float)cos(tmp);
      u[i][1] = -noiseAvg * (float)sin(tmp);
    }
    u[PART_LEN][1] = 0;

    for (i = 0; i < PART_LEN1; i++) {
      // Use average NLP weight for H band
      comfortNoiseHband[i][0] = tmpAvg * u[i][0];
      comfortNoiseHband[i][1] = tmpAvg * u[i][1];
    }
  }
}

void WebRtcAec_FilterFar_mips(AecCore *aec, float yf[2][PART_LEN1]) {
  int i;
  for (i = 0; i < aec->num_partitions; i++) {
    int xPos = (i + aec->xfBufBlockPos) * PART_LEN1;
    int pos = i * PART_LEN1;
    // Check for wrap
    if (i + aec->xfBufBlockPos >=  aec->num_partitions) {
      xPos -=  aec->num_partitions * (PART_LEN1);
    }
    float *yf0 = yf[0];
    float *yf1 = yf[1];
    float *aRe = aec->xfBuf[0] + xPos;
    float *aIm = aec->xfBuf[1] + xPos;
    float *bRe = aec->wfBuf[0] + pos;
    float *bIm = aec->wfBuf[1] + pos;
    float f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13;
    int len = PART_LEN1 >> 1;
    int len1 = PART_LEN1 & 1;

    __asm __volatile (
      ".set       push                                                \n\t"
      ".set       noreorder                                           \n\t"
     "1:                                                              \n\t"
      "lwc1       %[f0],      0(%[aRe])                               \n\t"
      "lwc1       %[f1],      0(%[bRe])                               \n\t"
      "lwc1       %[f2],      0(%[bIm])                               \n\t"
      "lwc1       %[f3],      0(%[aIm])                               \n\t"
      "lwc1       %[f4],      4(%[aRe])                               \n\t"
      "lwc1       %[f5],      4(%[bRe])                               \n\t"
      "lwc1       %[f6],      4(%[bIm])                               \n\t"
      "mul.s      %[f8],      %[f0],          %[f1]                   \n\t"
      "mul.s      %[f0],      %[f0],          %[f2]                   \n\t"
      "mul.s      %[f9],      %[f4],          %[f5]                   \n\t"
      "mul.s      %[f4],      %[f4],          %[f6]                   \n\t"
      "lwc1       %[f7],      4(%[aIm])                               \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s      %[f12],     %[f2],          %[f3]                   \n\t"
      "mul.s      %[f1],      %[f3],          %[f1]                   \n\t"
      "mul.s      %[f11],     %[f6],          %[f7]                   \n\t"
      "addiu      %[aRe],     %[aRe],         8                       \n\t"
      "addiu      %[aIm],     %[aIm],         8                       \n\t"
      "addiu      %[len],     %[len],         -1                      \n\t"
      "sub.s      %[f8],      %[f8],          %[f12]                  \n\t"
      "mul.s      %[f12],     %[f7],          %[f5]                   \n\t"
      "lwc1       %[f2],      0(%[yf0])                               \n\t"
      "add.s      %[f1],      %[f0],          %[f1]                   \n\t"
      "lwc1       %[f3],      0(%[yf1])                               \n\t"
      "sub.s      %[f9],      %[f9],          %[f11]                  \n\t"
      "lwc1       %[f6],      4(%[yf0])                               \n\t"
      "add.s      %[f4],      %[f4],          %[f12]                  \n\t"
#else // #if !defined(MIPS32_R2_LE)
      "addiu      %[aRe],     %[aRe],         8                       \n\t"
      "addiu      %[aIm],     %[aIm],         8                       \n\t"
      "addiu      %[len],     %[len],         -1                      \n\t"
      "nmsub.s    %[f8],      %[f8],          %[f2],      %[f3]       \n\t"
      "lwc1       %[f2],      0(%[yf0])                               \n\t"
      "madd.s     %[f1],      %[f0],          %[f3],      %[f1]       \n\t"
      "lwc1       %[f3],      0(%[yf1])                               \n\t"
      "nmsub.s    %[f9],      %[f9],          %[f6],      %[f7]       \n\t"
      "lwc1       %[f6],      4(%[yf0])                               \n\t"
      "madd.s     %[f4],      %[f4],          %[f7],      %[f5]       \n\t"
#endif // #if !defined(MIPS32_R2_LE)
      "lwc1       %[f5],      4(%[yf1])                               \n\t"
      "add.s      %[f2],      %[f2],          %[f8]                   \n\t"
      "addiu      %[bRe],     %[bRe],         8                       \n\t"
      "addiu      %[bIm],     %[bIm],         8                       \n\t"
      "add.s      %[f3],      %[f3],          %[f1]                   \n\t"
      "add.s      %[f6],      %[f6],          %[f9]                   \n\t"
      "add.s      %[f5],      %[f5],          %[f4]                   \n\t"
      "swc1       %[f2],      0(%[yf0])                               \n\t"
      "swc1       %[f3],      0(%[yf1])                               \n\t"
      "swc1       %[f6],      4(%[yf0])                               \n\t"
      "swc1       %[f5],      4(%[yf1])                               \n\t"
      "addiu      %[yf0],     %[yf0],         8                       \n\t"
      "bgtz       %[len],     1b                                      \n\t"
      " addiu     %[yf1],     %[yf1],         8                       \n\t"
      "lwc1       %[f0],      0(%[aRe])                               \n\t"
      "lwc1       %[f1],      0(%[bRe])                               \n\t"
      "lwc1       %[f2],      0(%[bIm])                               \n\t"
      "lwc1       %[f3],      0(%[aIm])                               \n\t"
      "mul.s      %[f8],      %[f0],          %[f1]                   \n\t"
      "mul.s      %[f0],      %[f0],          %[f2]                   \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s      %[f12],     %[f2],          %[f3]                   \n\t"
      "mul.s      %[f1],      %[f3],          %[f1]                   \n\t"
      "sub.s      %[f8],      %[f8],          %[f12]                  \n\t"
      "lwc1       %[f2],      0(%[yf0])                               \n\t"
      "add.s      %[f1],      %[f0],          %[f1]                   \n\t"
      "lwc1       %[f3],      0(%[yf1])                               \n\t"
#else // #if !defined(MIPS32_R2_LE)
      "nmsub.s    %[f8],      %[f8],          %[f2],      %[f3]       \n\t"
      "lwc1       %[f2],      0(%[yf0])                               \n\t"
      "madd.s     %[f1],      %[f0],          %[f3],      %[f1]       \n\t"
      "lwc1       %[f3],      0(%[yf1])                               \n\t"
#endif // #if !defined(MIPS32_R2_LE)
      "add.s      %[f2],      %[f2],          %[f8]                   \n\t"
      "add.s      %[f3],      %[f3],          %[f1]                   \n\t"
      "swc1       %[f2],      0(%[yf0])                               \n\t"
      "swc1       %[f3],      0(%[yf1])                               \n\t"
      ".set       pop                                                 \n\t"
      : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2),
        [f3] "=&f" (f3), [f4] "=&f" (f4), [f5] "=&f" (f5),
        [f6] "=&f" (f6), [f7] "=&f" (f7), [f8] "=&f" (f8),
        [f9] "=&f" (f9), [f10] "=&f" (f10), [f11] "=&f" (f11),
        [f12] "=&f" (f12), [f13] "=&f" (f13), [aRe] "+r" (aRe),
        [aIm] "+r" (aIm), [bRe] "+r" (bRe), [bIm] "+r" (bIm),
        [yf0] "+r" (yf0), [yf1] "+r" (yf1), [len] "+r" (len)
      : [len1] "r" (len1)
      : "memory"
    );
  }
}

void WebRtcAec_FilterAdaptation_mips(AecCore *aec,
                                     float *fft,
                                     float ef[2][PART_LEN1]) {
  int i;
  for (i = 0; i < aec->num_partitions; i++) {
    int xPos = (i + aec->xfBufBlockPos)*(PART_LEN1);
    int pos;
    // Check for wrap
    if (i + aec->xfBufBlockPos >= aec->num_partitions) {
      xPos -= aec->num_partitions * PART_LEN1;
    }

    pos = i * PART_LEN1;
    float *aRe = aec->xfBuf[0] + xPos;
    float *aIm = aec->xfBuf[1] + xPos;
    float *bRe = ef[0];
    float *bIm = ef[1];
    float *fft_tmp = fft;

    float f0, f1, f2, f3, f4, f5, f6 ,f7, f8, f9, f10, f11, f12;
    int len = PART_LEN >> 1;

    __asm __volatile (
      ".set       push                                                \n\t"
      ".set       noreorder                                           \n\t"
     "1:                                                              \n\t"
      "lwc1       %[f0],      0(%[aRe])                               \n\t"
      "lwc1       %[f1],      0(%[bRe])                               \n\t"
      "lwc1       %[f2],      0(%[bIm])                               \n\t"
      "lwc1       %[f4],      4(%[aRe])                               \n\t"
      "lwc1       %[f5],      4(%[bRe])                               \n\t"
      "lwc1       %[f6],      4(%[bIm])                               \n\t"
      "addiu      %[aRe],     %[aRe],         8                       \n\t"
      "addiu      %[bRe],     %[bRe],         8                       \n\t"
      "mul.s      %[f8],      %[f0],          %[f1]                   \n\t"
      "mul.s      %[f0],      %[f0],          %[f2]                   \n\t"
      "lwc1       %[f3],      0(%[aIm])                               \n\t"
      "mul.s      %[f9],      %[f4],          %[f5]                   \n\t"
      "lwc1       %[f7],      4(%[aIm])                               \n\t"
      "mul.s      %[f4],      %[f4],          %[f6]                   \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s      %[f10],     %[f3],          %[f2]                   \n\t"
      "mul.s      %[f1],      %[f3],          %[f1]                   \n\t"
      "mul.s      %[f11],     %[f7],          %[f6]                   \n\t"
      "mul.s      %[f5],      %[f7],          %[f5]                   \n\t"
      "addiu      %[aIm],     %[aIm],         8                       \n\t"
      "addiu      %[bIm],     %[bIm],         8                       \n\t"
      "addiu      %[len],     %[len],         -1                      \n\t"
      "add.s      %[f8],      %[f8],          %[f10]                  \n\t"
      "sub.s      %[f1],      %[f0],          %[f1]                   \n\t"
      "add.s      %[f9],      %[f9],          %[f11]                  \n\t"
      "sub.s      %[f5],      %[f4],          %[f5]                   \n\t"
#else // #if !defined(MIPS32_R2_LE)
      "addiu      %[aIm],     %[aIm],         8                       \n\t"
      "addiu      %[bIm],     %[bIm],         8                       \n\t"
      "addiu      %[len],     %[len],         -1                      \n\t"
      "madd.s     %[f8],      %[f8],          %[f3],      %[f2]       \n\t"
      "nmsub.s    %[f1],      %[f0],          %[f3],      %[f1]       \n\t"
      "madd.s     %[f9],      %[f9],          %[f7],      %[f6]       \n\t"
      "nmsub.s    %[f5],      %[f4],          %[f7],      %[f5]       \n\t"
#endif // #if !defined(MIPS32_R2_LE)
      "swc1       %[f8],      0(%[fft_tmp])                           \n\t"
      "swc1       %[f1],      4(%[fft_tmp])                           \n\t"
      "swc1       %[f9],      8(%[fft_tmp])                           \n\t"
      "swc1       %[f5],      12(%[fft_tmp])                          \n\t"
      "bgtz       %[len],     1b                                      \n\t"
      " addiu     %[fft_tmp], %[fft_tmp],     16                      \n\t"
      "lwc1       %[f0],      0(%[aRe])                               \n\t"
      "lwc1       %[f1],      0(%[bRe])                               \n\t"
      "lwc1       %[f2],      0(%[bIm])                               \n\t"
      "lwc1       %[f3],      0(%[aIm])                               \n\t"
      "mul.s      %[f8],      %[f0],          %[f1]                   \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s      %[f10],     %[f3],          %[f2]                   \n\t"
      "add.s      %[f8],      %[f8],          %[f10]                  \n\t"
#else // #if !defined(MIPS32_R2_LE)
      "madd.s     %[f8],      %[f8],          %[f3],      %[f2]       \n\t"
#endif // #if !defined(MIPS32_R2_LE)
      "swc1       %[f8],      4(%[fft])                               \n\t"
      ".set       pop                                                 \n\t"
      : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2),
        [f3] "=&f" (f3), [f4] "=&f" (f4), [f5] "=&f" (f5),
        [f6] "=&f" (f6), [f7] "=&f" (f7), [f8] "=&f" (f8),
        [f9] "=&f" (f9), [f10] "=&f" (f10), [f11] "=&f" (f11),
        [f12] "=&f" (f12), [aRe] "+r" (aRe), [aIm] "+r" (aIm),
        [bRe] "+r" (bRe), [bIm] "+r" (bIm), [fft_tmp] "+r" (fft_tmp),
        [len] "+r" (len), [fft] "=&r" (fft)
      :
      : "memory"
    );

    aec_rdft_inverse_128(fft);
    memset(fft + PART_LEN, 0, sizeof(float) * PART_LEN);

    // fft scaling
    {
      float scale = 2.0f / PART_LEN2;
      __asm __volatile (
        ".set     push                                    \n\t"
        ".set     noreorder                               \n\t"
        "addiu    %[fft_tmp], %[fft],        0            \n\t"
        "addiu    %[len],     $zero,         8            \n\t"
       "1:                                                \n\t"
        "addiu    %[len],     %[len],        -1           \n\t"
        "lwc1     %[f0],      0(%[fft_tmp])               \n\t"
        "lwc1     %[f1],      4(%[fft_tmp])               \n\t"
        "lwc1     %[f2],      8(%[fft_tmp])               \n\t"
        "lwc1     %[f3],      12(%[fft_tmp])              \n\t"
        "mul.s    %[f0],      %[f0],         %[scale]     \n\t"
        "mul.s    %[f1],      %[f1],         %[scale]     \n\t"
        "mul.s    %[f2],      %[f2],         %[scale]     \n\t"
        "mul.s    %[f3],      %[f3],         %[scale]     \n\t"
        "lwc1     %[f4],      16(%[fft_tmp])              \n\t"
        "lwc1     %[f5],      20(%[fft_tmp])              \n\t"
        "lwc1     %[f6],      24(%[fft_tmp])              \n\t"
        "lwc1     %[f7],      28(%[fft_tmp])              \n\t"
        "mul.s    %[f4],      %[f4],         %[scale]     \n\t"
        "mul.s    %[f5],      %[f5],         %[scale]     \n\t"
        "mul.s    %[f6],      %[f6],         %[scale]     \n\t"
        "mul.s    %[f7],      %[f7],         %[scale]     \n\t"
        "swc1     %[f0],      0(%[fft_tmp])               \n\t"
        "swc1     %[f1],      4(%[fft_tmp])               \n\t"
        "swc1     %[f2],      8(%[fft_tmp])               \n\t"
        "swc1     %[f3],      12(%[fft_tmp])              \n\t"
        "swc1     %[f4],      16(%[fft_tmp])              \n\t"
        "swc1     %[f5],      20(%[fft_tmp])              \n\t"
        "swc1     %[f6],      24(%[fft_tmp])              \n\t"
        "swc1     %[f7],      28(%[fft_tmp])              \n\t"
        "bgtz     %[len],     1b                          \n\t"
        " addiu   %[fft_tmp], %[fft_tmp],    32           \n\t"
        ".set     pop                                     \n\t"
        : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2),
          [f3] "=&f" (f3), [f4] "=&f" (f4), [f5] "=&f" (f5),
          [f6] "=&f" (f6), [f7] "=&f" (f7), [len] "=&r" (len),
          [fft_tmp] "=&r" (fft_tmp)
        : [scale] "f" (scale), [fft] "r" (fft)
        : "memory"
      );
    }
    aec_rdft_forward_128(fft);
    aRe = aec->wfBuf[0] + pos;
    aIm = aec->wfBuf[1] + pos;
    __asm __volatile (
      ".set     push                                    \n\t"
      ".set     noreorder                               \n\t"
      "addiu    %[fft_tmp], %[fft],        0            \n\t"
      "addiu    %[len],     $zero,         31           \n\t"
      "lwc1     %[f0],      0(%[aRe])                   \n\t"
      "lwc1     %[f1],      0(%[fft_tmp])               \n\t"
      "lwc1     %[f2],      256(%[aRe])                 \n\t"
      "lwc1     %[f3],      4(%[fft_tmp])               \n\t"
      "lwc1     %[f4],      4(%[aRe])                   \n\t"
      "lwc1     %[f5],      8(%[fft_tmp])               \n\t"
      "lwc1     %[f6],      4(%[aIm])                   \n\t"
      "lwc1     %[f7],      12(%[fft_tmp])              \n\t"
      "add.s    %[f0],      %[f0],         %[f1]        \n\t"
      "add.s    %[f2],      %[f2],         %[f3]        \n\t"
      "add.s    %[f4],      %[f4],         %[f5]        \n\t"
      "add.s    %[f6],      %[f6],         %[f7]        \n\t"
      "addiu    %[fft_tmp], %[fft_tmp],    16           \n\t"
      "swc1     %[f0],      0(%[aRe])                   \n\t"
      "swc1     %[f2],      256(%[aRe])                 \n\t"
      "swc1     %[f4],      4(%[aRe])                   \n\t"
      "addiu    %[aRe],     %[aRe],        8            \n\t"
      "swc1     %[f6],      4(%[aIm])                   \n\t"
      "addiu    %[aIm],     %[aIm],        8            \n\t"
     "1:                                                \n\t"
      "lwc1     %[f0],      0(%[aRe])                   \n\t"
      "lwc1     %[f1],      0(%[fft_tmp])               \n\t"
      "lwc1     %[f2],      0(%[aIm])                   \n\t"
      "lwc1     %[f3],      4(%[fft_tmp])               \n\t"
      "lwc1     %[f4],      4(%[aRe])                   \n\t"
      "lwc1     %[f5],      8(%[fft_tmp])               \n\t"
      "lwc1     %[f6],      4(%[aIm])                   \n\t"
      "lwc1     %[f7],      12(%[fft_tmp])              \n\t"
      "add.s    %[f0],      %[f0],         %[f1]        \n\t"
      "add.s    %[f2],      %[f2],         %[f3]        \n\t"
      "add.s    %[f4],      %[f4],         %[f5]        \n\t"
      "add.s    %[f6],      %[f6],         %[f7]        \n\t"
      "addiu    %[len],     %[len],        -1           \n\t"
      "addiu    %[fft_tmp], %[fft_tmp],    16           \n\t"
      "swc1     %[f0],      0(%[aRe])                   \n\t"
      "swc1     %[f2],      0(%[aIm])                   \n\t"
      "swc1     %[f4],      4(%[aRe])                   \n\t"
      "addiu    %[aRe],     %[aRe],        8            \n\t"
      "swc1     %[f6],      4(%[aIm])                   \n\t"
      "bgtz     %[len],     1b                          \n\t"
      " addiu   %[aIm],     %[aIm],        8            \n\t"
      ".set     pop                                     \n\t"
      : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2),
        [f3] "=&f" (f3), [f4] "=&f" (f4), [f5] "=&f" (f5),
        [f6] "=&f" (f6), [f7] "=&f" (f7), [len] "=&r" (len),
        [fft_tmp] "=&r" (fft_tmp)
      : [aRe] "r" (aRe), [aIm] "r" (aIm), [fft] "r" (fft)
      : "memory"
    );
  }
}

void WebRtcAec_OverdriveAndSuppress_mips(AecCore *aec,
                                         float hNl[PART_LEN1],
                                         const float hNlFb,
                                         float efw[2][PART_LEN1]) {
  int i;
  const float one = 1.0;
  float *p_hNl, *p_efw0, *p_efw1;
  float *p_WebRtcAec_wC;
  float temp1, temp2, temp3, temp4;

  p_hNl = &hNl[0];
  p_efw0 = &efw[0][0];
  p_efw1 = &efw[1][0];
  p_WebRtcAec_wC = (float*)&WebRtcAec_weightCurve[0];

  for (i = 0; i < PART_LEN1; i++) {
    // Weight subbands
    __asm __volatile (
      ".set      push                                              \n\t"
      ".set      noreorder                                         \n\t"
      "lwc1      %[temp1],    0(%[p_hNl])                          \n\t"
      "lwc1      %[temp2],    0(%[p_wC])                           \n\t"
      "c.lt.s    %[hNlFb],    %[temp1]                             \n\t"
      "bc1f      1f                                                \n\t"
      " mul.s    %[temp3],    %[temp2],     %[hNlFb]               \n\t"
      "sub.s     %[temp4],    %[one],       %[temp2]               \n\t"
#if !defined(MIPS32_R2_LE)
      "mul.s     %[temp1],    %[temp1],     %[temp4]               \n\t"
      "add.s     %[temp1],    %[temp3],     %[temp1]               \n\t"
#else // #if !defined(MIPS32_R2_LE)
      "madd.s    %[temp1],    %[temp3],     %[temp1],   %[temp4]   \n\t"
#endif // #if !defined(MIPS32_R2_LE)
      "swc1      %[temp1],    0(%[p_hNl])                          \n\t"
     "1:                                                           \n\t"
      "addiu     %[p_wC],     %[p_wC],      4                      \n\t"
      ".set      pop                                               \n\t"
      : [temp1] "=&f" (temp1), [temp2] "=&f" (temp2), [temp3] "=&f" (temp3),
        [temp4] "=&f" (temp4), [p_wC] "+r" (p_WebRtcAec_wC)
      : [hNlFb] "f" (hNlFb), [one] "f" (one), [p_hNl] "r" (p_hNl)
      : "memory"
    );

    hNl[i] = powf(hNl[i], aec->overDriveSm * WebRtcAec_overDriveCurve[i]);

    __asm __volatile (
      "lwc1      %[temp1],    0(%[p_hNl])              \n\t"
      "lwc1      %[temp3],    0(%[p_efw1])             \n\t"
      "lwc1      %[temp2],    0(%[p_efw0])             \n\t"
      "addiu     %[p_hNl],    %[p_hNl],     4          \n\t"
      "mul.s     %[temp3],    %[temp3],     %[temp1]   \n\t"
      "mul.s     %[temp2],    %[temp2],     %[temp1]   \n\t"
      "addiu     %[p_efw0],   %[p_efw0],    4          \n\t"
      "addiu     %[p_efw1],   %[p_efw1],    4          \n\t"
      "neg.s     %[temp4],    %[temp3]                 \n\t"
      "swc1      %[temp2],    -4(%[p_efw0])            \n\t"
      "swc1      %[temp4],    -4(%[p_efw1])            \n\t"
      : [temp1] "=&f" (temp1), [temp2] "=&f" (temp2), [temp3] "=&f" (temp3),
        [temp4] "=&f" (temp4), [p_efw0] "+r" (p_efw0), [p_efw1] "+r" (p_efw1),
        [p_hNl] "+r" (p_hNl)
      :
      : "memory"
    );
  }
}

void WebRtcAec_ScaleErrorSignal_mips(AecCore *aec, float ef[2][PART_LEN1]) {
  const float mu = aec->extended_filter_enabled ? kExtendedMu : aec->normal_mu;
  const float error_threshold = aec->extended_filter_enabled
                                    ? kExtendedErrorThreshold
                                    : aec->normal_error_threshold;
  int len = (PART_LEN1);
  float *ef0 = ef[0];
  float *ef1 = ef[1];
  float *xPow = aec->xPow;
  float fac1 = 1e-10f;
  float err_th2 = error_threshold * error_threshold;
  float f0, f1, f2;
#if !defined(MIPS32_R2_LE)
  float f3;
#endif

  __asm __volatile (
    ".set       push                                   \n\t"
    ".set       noreorder                              \n\t"
   "1:                                                 \n\t"
    "lwc1       %[f0],     0(%[xPow])                  \n\t"
    "lwc1       %[f1],     0(%[ef0])                   \n\t"
    "lwc1       %[f2],     0(%[ef1])                   \n\t"
    "add.s      %[f0],     %[f0],       %[fac1]        \n\t"
    "div.s      %[f1],     %[f1],       %[f0]          \n\t"
    "div.s      %[f2],     %[f2],       %[f0]          \n\t"
    "mul.s      %[f0],     %[f1],       %[f1]          \n\t"
#if defined(MIPS32_R2_LE)
    "madd.s     %[f0],     %[f0],       %[f2],   %[f2] \n\t"
#else
    "mul.s      %[f3],     %[f2],       %[f2]          \n\t"
    "add.s      %[f0],     %[f0],       %[f3]          \n\t"
#endif
    "c.le.s     %[f0],     %[err_th2]                  \n\t"
    "nop                                               \n\t"
    "bc1t       2f                                     \n\t"
    " nop                                              \n\t"
    "sqrt.s     %[f0],     %[f0]                       \n\t"
    "add.s      %[f0],     %[f0],       %[fac1]        \n\t"
    "div.s      %[f0],     %[err_th],   %[f0]          \n\t"
    "mul.s      %[f1],     %[f1],       %[f0]          \n\t"
    "mul.s      %[f2],     %[f2],       %[f0]          \n\t"
   "2:                                                 \n\t"
    "mul.s      %[f1],     %[f1],       %[mu]          \n\t"
    "mul.s      %[f2],     %[f2],       %[mu]          \n\t"
    "swc1       %[f1],     0(%[ef0])                   \n\t"
    "swc1       %[f2],     0(%[ef1])                   \n\t"
    "addiu      %[len],    %[len],      -1             \n\t"
    "addiu      %[xPow],   %[xPow],     4              \n\t"
    "addiu      %[ef0],    %[ef0],      4              \n\t"
    "bgtz       %[len],    1b                          \n\t"
    " addiu     %[ef1],    %[ef1],      4              \n\t"
    ".set       pop                                    \n\t"
    : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2),
#if !defined(MIPS32_R2_LE)
      [f3] "=&f" (f3),
#endif
      [xPow] "+r" (xPow), [ef0] "+r" (ef0), [ef1] "+r" (ef1),
      [len] "+r" (len)
    : [fac1] "f" (fac1), [err_th2] "f" (err_th2), [mu] "f" (mu),
      [err_th] "f" (error_threshold)
    : "memory"
  );
}

void WebRtcAec_InitAec_mips(void)
{
  WebRtcAec_FilterFar = WebRtcAec_FilterFar_mips;
  WebRtcAec_FilterAdaptation = WebRtcAec_FilterAdaptation_mips;
  WebRtcAec_ScaleErrorSignal = WebRtcAec_ScaleErrorSignal_mips;
  WebRtcAec_ComfortNoise = WebRtcAec_ComfortNoise_mips;
  WebRtcAec_OverdriveAndSuppress = WebRtcAec_OverdriveAndSuppress_mips;
}

