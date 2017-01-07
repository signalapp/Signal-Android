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

extern "C" {
#include "webrtc/common_audio/signal_processing/include/signal_processing_library.h"
}
#include "webrtc/modules/audio_processing/aec/aec_core_optimized_methods.h"
#include "webrtc/modules/audio_processing/aec/aec_rdft.h"

namespace webrtc {

extern const float WebRtcAec_weightCurve[65];
extern const float WebRtcAec_overDriveCurve[65];

void WebRtcAec_FilterFar_mips(
    int num_partitions,
    int x_fft_buf_block_pos,
    float x_fft_buf[2][kExtendedNumPartitions * PART_LEN1],
    float h_fft_buf[2][kExtendedNumPartitions * PART_LEN1],
    float y_fft[2][PART_LEN1]) {
  int i;
  for (i = 0; i < num_partitions; i++) {
    int xPos = (i + x_fft_buf_block_pos) * PART_LEN1;
    int pos = i * PART_LEN1;
    // Check for wrap
    if (i + x_fft_buf_block_pos >= num_partitions) {
      xPos -= num_partitions * (PART_LEN1);
    }
    float* yf0 = y_fft[0];
    float* yf1 = y_fft[1];
    float* aRe = x_fft_buf[0] + xPos;
    float* aIm = x_fft_buf[1] + xPos;
    float* bRe = h_fft_buf[0] + pos;
    float* bIm = h_fft_buf[1] + pos;
    float f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13;
    int len = PART_LEN1 >> 1;

    __asm __volatile(
      ".set       push                                                \n\t"
      ".set       noreorder                                           \n\t"
      "1:                                                             \n\t"
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
#else  // #if !defined(MIPS32_R2_LE)
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
#endif  // #if !defined(MIPS32_R2_LE)
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
#else  // #if !defined(MIPS32_R2_LE)
      "nmsub.s    %[f8],      %[f8],          %[f2],      %[f3]       \n\t"
      "lwc1       %[f2],      0(%[yf0])                               \n\t"
      "madd.s     %[f1],      %[f0],          %[f3],      %[f1]       \n\t"
      "lwc1       %[f3],      0(%[yf1])                               \n\t"
#endif  // #if !defined(MIPS32_R2_LE)
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
      :
      : "memory");
  }
}

void WebRtcAec_FilterAdaptation_mips(
    int num_partitions,
    int x_fft_buf_block_pos,
    float x_fft_buf[2][kExtendedNumPartitions * PART_LEN1],
    float e_fft[2][PART_LEN1],
    float h_fft_buf[2][kExtendedNumPartitions * PART_LEN1]) {
  float fft[PART_LEN2];
  int i;
  for (i = 0; i < num_partitions; i++) {
    int xPos = (i + x_fft_buf_block_pos) * (PART_LEN1);
    int pos;
    // Check for wrap
    if (i + x_fft_buf_block_pos >= num_partitions) {
      xPos -= num_partitions * PART_LEN1;
    }

    pos = i * PART_LEN1;
    float* aRe = x_fft_buf[0] + xPos;
    float* aIm = x_fft_buf[1] + xPos;
    float* bRe = e_fft[0];
    float* bIm = e_fft[1];
    float* fft_tmp;

    float f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12;
    int len = PART_LEN >> 1;

    __asm __volatile(
      ".set       push                                                \n\t"
      ".set       noreorder                                           \n\t"
      "addiu      %[fft_tmp], %[fft],         0                       \n\t"
      "1:                                                             \n\t"
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
#else  // #if !defined(MIPS32_R2_LE)
      "addiu      %[aIm],     %[aIm],         8                       \n\t"
      "addiu      %[bIm],     %[bIm],         8                       \n\t"
      "addiu      %[len],     %[len],         -1                      \n\t"
      "madd.s     %[f8],      %[f8],          %[f3],      %[f2]       \n\t"
      "nmsub.s    %[f1],      %[f0],          %[f3],      %[f1]       \n\t"
      "madd.s     %[f9],      %[f9],          %[f7],      %[f6]       \n\t"
      "nmsub.s    %[f5],      %[f4],          %[f7],      %[f5]       \n\t"
#endif  // #if !defined(MIPS32_R2_LE)
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
#else  // #if !defined(MIPS32_R2_LE)
      "madd.s     %[f8],      %[f8],          %[f3],      %[f2]       \n\t"
#endif  // #if !defined(MIPS32_R2_LE)
      "swc1       %[f8],      4(%[fft])                               \n\t"
      ".set       pop                                                 \n\t"
      : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2),
        [f3] "=&f" (f3), [f4] "=&f" (f4), [f5] "=&f" (f5),
        [f6] "=&f" (f6), [f7] "=&f" (f7), [f8] "=&f" (f8),
        [f9] "=&f" (f9), [f10] "=&f" (f10), [f11] "=&f" (f11),
        [f12] "=&f" (f12), [aRe] "+r" (aRe), [aIm] "+r" (aIm),
        [bRe] "+r" (bRe), [bIm] "+r" (bIm), [fft_tmp] "=&r" (fft_tmp),
        [len] "+r" (len)
      : [fft] "r" (fft)
      : "memory");

    aec_rdft_inverse_128(fft);
    memset(fft + PART_LEN, 0, sizeof(float) * PART_LEN);

    // fft scaling
    {
      float scale = 2.0f / PART_LEN2;
      __asm __volatile(
        ".set     push                                    \n\t"
        ".set     noreorder                               \n\t"
        "addiu    %[fft_tmp], %[fft],        0            \n\t"
        "addiu    %[len],     $zero,         8            \n\t"
        "1:                                               \n\t"
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
        : "memory");
    }
    aec_rdft_forward_128(fft);
    aRe = h_fft_buf[0] + pos;
    aIm = h_fft_buf[1] + pos;
    __asm __volatile(
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
      "1:                                               \n\t"
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
        [fft_tmp] "=&r" (fft_tmp), [aRe] "+r" (aRe), [aIm] "+r" (aIm)
      : [fft] "r" (fft)
      : "memory");
  }
}

void WebRtcAec_Overdrive_mips(float overdrive_scaling,
                              float hNlFb,
                              float hNl[PART_LEN1]) {
  const float one = 1.0;
  float* p_hNl;
  const float* p_WebRtcAec_wC;
  float temp1, temp2, temp3, temp4;

  p_hNl = &hNl[0];
  p_WebRtcAec_wC = &WebRtcAec_weightCurve[0];

  for (int i = 0; i < PART_LEN1; ++i) {
    // Weight subbands
    __asm __volatile(
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
#else  // #if !defined(MIPS32_R2_LE)
      "madd.s    %[temp1],    %[temp3],     %[temp1],   %[temp4]   \n\t"
#endif  // #if !defined(MIPS32_R2_LE)
      "swc1      %[temp1],    0(%[p_hNl])                          \n\t"
     "1:                                                           \n\t"
      "addiu     %[p_wC],     %[p_wC],      4                      \n\t"
      ".set      pop                                               \n\t"
      : [temp1] "=&f" (temp1), [temp2] "=&f" (temp2), [temp3] "=&f" (temp3),
        [temp4] "=&f" (temp4), [p_wC] "+r" (p_WebRtcAec_wC)
      : [hNlFb] "f" (hNlFb), [one] "f" (one), [p_hNl] "r" (p_hNl)
      : "memory");

    hNl[i] = powf(hNl[i], overdrive_scaling * WebRtcAec_overDriveCurve[i]);
  }
}

void WebRtcAec_Suppress_mips(const float hNl[PART_LEN1],
                             float efw[2][PART_LEN1]) {
  const float* p_hNl;
  float* p_efw0;
  float* p_efw1;
  float temp1, temp2, temp3, temp4;

  p_hNl = &hNl[0];
  p_efw0 = &efw[0][0];
  p_efw1 = &efw[1][0];

  for (int i = 0; i < PART_LEN1; ++i) {
    __asm __volatile(
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
      : "memory");
  }
}

void WebRtcAec_ScaleErrorSignal_mips(float mu,
                                     float error_threshold,
                                     float x_pow[PART_LEN1],
                                     float ef[2][PART_LEN1]) {
  int len = (PART_LEN1);
  float* ef0 = ef[0];
  float* ef1 = ef[1];
  float fac1 = 1e-10f;
  float err_th2 = error_threshold * error_threshold;
  float f0, f1, f2;
#if !defined(MIPS32_R2_LE)
  float f3;
#endif

  __asm __volatile(
    ".set       push                                   \n\t"
    ".set       noreorder                              \n\t"
    "1:                                                \n\t"
    "lwc1       %[f0],     0(%[x_pow])                 \n\t"
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
    "2:                                                \n\t"
    "mul.s      %[f1],     %[f1],       %[mu]          \n\t"
    "mul.s      %[f2],     %[f2],       %[mu]          \n\t"
    "swc1       %[f1],     0(%[ef0])                   \n\t"
    "swc1       %[f2],     0(%[ef1])                   \n\t"
    "addiu      %[len],    %[len],      -1             \n\t"
    "addiu      %[x_pow],  %[x_pow],    4              \n\t"
    "addiu      %[ef0],    %[ef0],      4              \n\t"
    "bgtz       %[len],    1b                          \n\t"
    " addiu     %[ef1],    %[ef1],      4              \n\t"
    ".set       pop                                    \n\t"
    : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2),
#if !defined(MIPS32_R2_LE)
      [f3] "=&f" (f3),
#endif
      [x_pow] "+r" (x_pow), [ef0] "+r" (ef0), [ef1] "+r" (ef1),
      [len] "+r" (len)
    : [fac1] "f" (fac1), [err_th2] "f" (err_th2), [mu] "f" (mu),
      [err_th] "f" (error_threshold)
    : "memory");
}

void WebRtcAec_InitAec_mips(void) {
  WebRtcAec_FilterFar = WebRtcAec_FilterFar_mips;
  WebRtcAec_FilterAdaptation = WebRtcAec_FilterAdaptation_mips;
  WebRtcAec_ScaleErrorSignal = WebRtcAec_ScaleErrorSignal_mips;
  WebRtcAec_Overdrive = WebRtcAec_Overdrive_mips;
  WebRtcAec_Suppress = WebRtcAec_Suppress_mips;
}
}  // namespace webrtc
