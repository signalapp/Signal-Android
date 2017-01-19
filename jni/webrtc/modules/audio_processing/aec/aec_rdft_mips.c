/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/aec/aec_rdft.h"
#include "webrtc/typedefs.h"

static void bitrv2_128_mips(float *a) {
  // n is 128
  float xr, xi, yr, yi;

  xr = a[8];
  xi = a[9];
  yr = a[16];
  yi = a[17];
  a[8] = yr;
  a[9] = yi;
  a[16] = xr;
  a[17] = xi;

  xr = a[64];
  xi = a[65];
  yr = a[2];
  yi = a[3];
  a[64] = yr;
  a[65] = yi;
  a[2] = xr;
  a[3] = xi;

  xr = a[72];
  xi = a[73];
  yr = a[18];
  yi = a[19];
  a[72] = yr;
  a[73] = yi;
  a[18] = xr;
  a[19] = xi;

  xr = a[80];
  xi = a[81];
  yr = a[10];
  yi = a[11];
  a[80] = yr;
  a[81] = yi;
  a[10] = xr;
  a[11] = xi;

  xr = a[88];
  xi = a[89];
  yr = a[26];
  yi = a[27];
  a[88] = yr;
  a[89] = yi;
  a[26] = xr;
  a[27] = xi;

  xr = a[74];
  xi = a[75];
  yr = a[82];
  yi = a[83];
  a[74] = yr;
  a[75] = yi;
  a[82] = xr;
  a[83] = xi;

  xr = a[32];
  xi = a[33];
  yr = a[4];
  yi = a[5];
  a[32] = yr;
  a[33] = yi;
  a[4] = xr;
  a[5] = xi;

  xr = a[40];
  xi = a[41];
  yr = a[20];
  yi = a[21];
  a[40] = yr;
  a[41] = yi;
  a[20] = xr;
  a[21] = xi;

  xr = a[48];
  xi = a[49];
  yr = a[12];
  yi = a[13];
  a[48] = yr;
  a[49] = yi;
  a[12] = xr;
  a[13] = xi;

  xr = a[56];
  xi = a[57];
  yr = a[28];
  yi = a[29];
  a[56] = yr;
  a[57] = yi;
  a[28] = xr;
  a[29] = xi;

  xr = a[34];
  xi = a[35];
  yr = a[68];
  yi = a[69];
  a[34] = yr;
  a[35] = yi;
  a[68] = xr;
  a[69] = xi;

  xr = a[42];
  xi = a[43];
  yr = a[84];
  yi = a[85];
  a[42] = yr;
  a[43] = yi;
  a[84] = xr;
  a[85] = xi;

  xr = a[50];
  xi = a[51];
  yr = a[76];
  yi = a[77];
  a[50] = yr;
  a[51] = yi;
  a[76] = xr;
  a[77] = xi;

  xr = a[58];
  xi = a[59];
  yr = a[92];
  yi = a[93];
  a[58] = yr;
  a[59] = yi;
  a[92] = xr;
  a[93] = xi;

  xr = a[44];
  xi = a[45];
  yr = a[52];
  yi = a[53];
  a[44] = yr;
  a[45] = yi;
  a[52] = xr;
  a[53] = xi;

  xr = a[96];
  xi = a[97];
  yr = a[6];
  yi = a[7];
  a[96] = yr;
  a[97] = yi;
  a[6] = xr;
  a[7] = xi;

  xr = a[104];
  xi = a[105];
  yr = a[22];
  yi = a[23];
  a[104] = yr;
  a[105] = yi;
  a[22] = xr;
  a[23] = xi;

  xr = a[112];
  xi = a[113];
  yr = a[14];
  yi = a[15];
  a[112] = yr;
  a[113] = yi;
  a[14] = xr;
  a[15] = xi;

  xr = a[120];
  xi = a[121];
  yr = a[30];
  yi = a[31];
  a[120] = yr;
  a[121] = yi;
  a[30] = xr;
  a[31] = xi;

  xr = a[98];
  xi = a[99];
  yr = a[70];
  yi = a[71];
  a[98] = yr;
  a[99] = yi;
  a[70] = xr;
  a[71] = xi;

  xr = a[106];
  xi = a[107];
  yr = a[86];
  yi = a[87];
  a[106] = yr;
  a[107] = yi;
  a[86] = xr;
  a[87] = xi;

  xr = a[114];
  xi = a[115];
  yr = a[78];
  yi = a[79];
  a[114] = yr;
  a[115] = yi;
  a[78] = xr;
  a[79] = xi;

  xr = a[122];
  xi = a[123];
  yr = a[94];
  yi = a[95];
  a[122] = yr;
  a[123] = yi;
  a[94] = xr;
  a[95] = xi;

  xr = a[100];
  xi = a[101];
  yr = a[38];
  yi = a[39];
  a[100] = yr;
  a[101] = yi;
  a[38] = xr;
  a[39] = xi;

  xr = a[108];
  xi = a[109];
  yr = a[54];
  yi = a[55];
  a[108] = yr;
  a[109] = yi;
  a[54] = xr;
  a[55] = xi;

  xr = a[116];
  xi = a[117];
  yr = a[46];
  yi = a[47];
  a[116] = yr;
  a[117] = yi;
  a[46] = xr;
  a[47] = xi;

  xr = a[124];
  xi = a[125];
  yr = a[62];
  yi = a[63];
  a[124] = yr;
  a[125] = yi;
  a[62] = xr;
  a[63] = xi;

  xr = a[110];
  xi = a[111];
  yr = a[118];
  yi = a[119];
  a[110] = yr;
  a[111] = yi;
  a[118] = xr;
  a[119] = xi;
}

static void cft1st_128_mips(float *a) {
  float wk1r, wk1i, wk2r, wk2i, wk3r, wk3i;
  float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

  float f0, f1, f2, f3, f4, f5, f6, f7;
  int a_ptr, p1_rdft, p2_rdft, count;

  __asm __volatile (
    ".set       push                                                       \n\t"
    ".set       noreorder                                                  \n\t"
    // first 16
    "lwc1       %[f0],          0(%[a])                                    \n\t"
    "lwc1       %[f1],          4(%[a])                                    \n\t"
    "lwc1       %[f2],          8(%[a])                                    \n\t"
    "lwc1       %[f3],          12(%[a])                                   \n\t"
    "lwc1       %[f4],          16(%[a])                                   \n\t"
    "lwc1       %[f5],          20(%[a])                                   \n\t"
    "lwc1       %[f6],          24(%[a])                                   \n\t"
    "lwc1       %[f7],          28(%[a])                                   \n\t"
    "add.s      %[x0r],         %[f0],           %[f2]                     \n\t"
    "add.s      %[x0i],         %[f1],           %[f3]                     \n\t"
    "sub.s      %[x1r],         %[f0],           %[f2]                     \n\t"
    "add.s      %[x2r],         %[f4],           %[f6]                     \n\t"
    "add.s      %[x2i],         %[f5],           %[f7]                     \n\t"
    "sub.s      %[x1i],         %[f1],           %[f3]                     \n\t"
    "sub.s      %[x3r],         %[f4],           %[f6]                     \n\t"
    "sub.s      %[x3i],         %[f5],           %[f7]                     \n\t"
    "add.s      %[f0],          %[x0r],          %[x2r]                    \n\t"
    "add.s      %[f1],          %[x0i],          %[x2i]                    \n\t"
    "sub.s      %[f4],          %[x0r],          %[x2r]                    \n\t"
    "sub.s      %[f5],          %[x0i],          %[x2i]                    \n\t"
    "sub.s      %[f2],          %[x1r],          %[x3i]                    \n\t"
    "add.s      %[f3],          %[x1i],          %[x3r]                    \n\t"
    "add.s      %[f6],          %[x1r],          %[x3i]                    \n\t"
    "sub.s      %[f7],          %[x1i],          %[x3r]                    \n\t"
    "swc1       %[f0],          0(%[a])                                    \n\t"
    "swc1       %[f1],          4(%[a])                                    \n\t"
    "swc1       %[f2],          8(%[a])                                    \n\t"
    "swc1       %[f3],          12(%[a])                                   \n\t"
    "swc1       %[f4],          16(%[a])                                   \n\t"
    "swc1       %[f5],          20(%[a])                                   \n\t"
    "swc1       %[f6],          24(%[a])                                   \n\t"
    "swc1       %[f7],          28(%[a])                                   \n\t"
    "lwc1       %[f0],          32(%[a])                                   \n\t"
    "lwc1       %[f1],          36(%[a])                                   \n\t"
    "lwc1       %[f2],          40(%[a])                                   \n\t"
    "lwc1       %[f3],          44(%[a])                                   \n\t"
    "lwc1       %[f4],          48(%[a])                                   \n\t"
    "lwc1       %[f5],          52(%[a])                                   \n\t"
    "lwc1       %[f6],          56(%[a])                                   \n\t"
    "lwc1       %[f7],          60(%[a])                                   \n\t"
    "add.s      %[x0r],         %[f0],           %[f2]                     \n\t"
    "add.s      %[x0i],         %[f1],           %[f3]                     \n\t"
    "sub.s      %[x1r],         %[f0],           %[f2]                     \n\t"
    "sub.s      %[x1i],         %[f1],           %[f3]                     \n\t"
    "sub.s      %[x3r],         %[f4],           %[f6]                     \n\t"
    "sub.s      %[x3i],         %[f5],           %[f7]                     \n\t"
    "add.s      %[x2r],         %[f4],           %[f6]                     \n\t"
    "add.s      %[x2i],         %[f5],           %[f7]                     \n\t"
    "lwc1       %[wk2r],        8(%[rdft_w])                               \n\t"
    "add.s      %[f3],          %[x1i],          %[x3r]                    \n\t"
    "sub.s      %[f2],          %[x1r],          %[x3i]                    \n\t"
    "add.s      %[f6],          %[x3i],          %[x1r]                    \n\t"
    "sub.s      %[f7],          %[x3r],          %[x1i]                    \n\t"
    "add.s      %[f0],          %[x0r],          %[x2r]                    \n\t"
    "add.s      %[f1],          %[x0i],          %[x2i]                    \n\t"
    "sub.s      %[x1r],         %[f2],           %[f3]                     \n\t"
    "add.s      %[x1i],         %[f3],           %[f2]                     \n\t"
    "sub.s      %[x3r],         %[f7],           %[f6]                     \n\t"
    "add.s      %[x3i],         %[f7],           %[f6]                     \n\t"
    "sub.s      %[f4],          %[x0r],          %[x2r]                    \n\t"
    "mul.s      %[f2],          %[wk2r],         %[x1r]                    \n\t"
    "mul.s      %[f3],          %[wk2r],         %[x1i]                    \n\t"
    "mul.s      %[f6],          %[wk2r],         %[x3r]                    \n\t"
    "mul.s      %[f7],          %[wk2r],         %[x3i]                    \n\t"
    "sub.s      %[f5],          %[x2i],          %[x0i]                    \n\t"
    "swc1       %[f0],          32(%[a])                                   \n\t"
    "swc1       %[f1],          36(%[a])                                   \n\t"
    "swc1       %[f2],          40(%[a])                                   \n\t"
    "swc1       %[f3],          44(%[a])                                   \n\t"
    "swc1       %[f5],          48(%[a])                                   \n\t"
    "swc1       %[f4],          52(%[a])                                   \n\t"
    "swc1       %[f6],          56(%[a])                                   \n\t"
    "swc1       %[f7],          60(%[a])                                   \n\t"
    // prepare for loop
    "addiu      %[a_ptr],       %[a],            64                        \n\t"
    "addiu      %[p1_rdft],     %[rdft_w],       8                         \n\t"
    "addiu      %[p2_rdft],     %[rdft_w],       16                        \n\t"
    "addiu      %[count],       $zero,           7                         \n\t"
    // loop
   "1:                                                                     \n\t"
    "lwc1       %[f0],          0(%[a_ptr])                                \n\t"
    "lwc1       %[f1],          4(%[a_ptr])                                \n\t"
    "lwc1       %[f2],          8(%[a_ptr])                                \n\t"
    "lwc1       %[f3],          12(%[a_ptr])                               \n\t"
    "lwc1       %[f4],          16(%[a_ptr])                               \n\t"
    "lwc1       %[f5],          20(%[a_ptr])                               \n\t"
    "lwc1       %[f6],          24(%[a_ptr])                               \n\t"
    "lwc1       %[f7],          28(%[a_ptr])                               \n\t"
    "add.s      %[x0r],         %[f0],           %[f2]                     \n\t"
    "add.s      %[x2r],         %[f4],           %[f6]                     \n\t"
    "add.s      %[x0i],         %[f1],           %[f3]                     \n\t"
    "add.s      %[x2i],         %[f5],           %[f7]                     \n\t"
    "sub.s      %[x1r],         %[f0],           %[f2]                     \n\t"
    "sub.s      %[x1i],         %[f1],           %[f3]                     \n\t"
    "sub.s      %[x3r],         %[f4],           %[f6]                     \n\t"
    "sub.s      %[x3i],         %[f5],           %[f7]                     \n\t"
    "lwc1       %[wk2i],        4(%[p1_rdft])                              \n\t"
    "sub.s      %[f0],          %[x0r],          %[x2r]                    \n\t"
    "sub.s      %[f1],          %[x0i],          %[x2i]                    \n\t"
    "add.s      %[f2],          %[x1i],          %[x3r]                    \n\t"
    "sub.s      %[f3],          %[x1r],          %[x3i]                    \n\t"
    "lwc1       %[wk1r],        0(%[p2_rdft])                              \n\t"
    "add.s      %[f4],          %[x1r],          %[x3i]                    \n\t"
    "sub.s      %[f5],          %[x1i],          %[x3r]                    \n\t"
    "lwc1       %[wk3r],        8(%[first])                                \n\t"
    "mul.s      %[x3r],         %[wk2r],         %[f0]                     \n\t"
    "mul.s      %[x3i],         %[wk2r],         %[f1]                     \n\t"
    "mul.s      %[x1r],         %[wk1r],         %[f3]                     \n\t"
    "mul.s      %[x1i],         %[wk1r],         %[f2]                     \n\t"
    "lwc1       %[wk1i],        4(%[p2_rdft])                              \n\t"
    "mul.s      %[f6],          %[wk3r],         %[f4]                     \n\t"
    "mul.s      %[f7],          %[wk3r],         %[f5]                     \n\t"
    "lwc1       %[wk3i],        12(%[first])                               \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s      %[wk1r],        %[wk2i],         %[f1]                     \n\t"
    "mul.s      %[f0],          %[wk2i],         %[f0]                     \n\t"
    "sub.s      %[x3r],         %[x3r],          %[wk1r]                   \n\t"
    "add.s      %[x3i],         %[x3i],          %[f0]                     \n\t"
    "add.s      %[f0],          %[x0r],          %[x2r]                    \n\t"
    "add.s      %[f1],          %[x0i],          %[x2i]                    \n\t"
    "mul.s      %[x0r],         %[wk1i],         %[f2]                     \n\t"
    "mul.s      %[f3],          %[wk1i],         %[f3]                     \n\t"
    "mul.s      %[x2r],         %[wk3i],         %[f5]                     \n\t"
    "mul.s      %[f4],          %[wk3i],         %[f4]                     \n\t"
    "sub.s      %[x1r],         %[x1r],          %[x0r]                    \n\t"
    "add.s      %[x1i],         %[x1i],          %[f3]                     \n\t"
    "sub.s      %[f6],          %[f6],           %[x2r]                    \n\t"
    "add.s      %[f7],          %[f7],           %[f4]                     \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "nmsub.s    %[x3r],         %[x3r],          %[wk2i],        %[f1]     \n\t"
    "madd.s     %[x3i],         %[x3i],          %[wk2i],        %[f0]     \n\t"
    "add.s      %[f0],          %[x0r],          %[x2r]                    \n\t"
    "add.s      %[f1],          %[x0i],          %[x2i]                    \n\t"
    "nmsub.s    %[x1r],         %[x1r],          %[wk1i],        %[f2]     \n\t"
    "madd.s     %[x1i],         %[x1i],          %[wk1i],        %[f3]     \n\t"
    "nmsub.s    %[f6],          %[f6],           %[wk3i],        %[f5]     \n\t"
    "madd.s     %[f7],          %[f7],           %[wk3i],        %[f4]     \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "swc1       %[f0],          0(%[a_ptr])                                \n\t"
    "swc1       %[f1],          4(%[a_ptr])                                \n\t"
    "swc1       %[x1r],         8(%[a_ptr])                                \n\t"
    "swc1       %[x1i],         12(%[a_ptr])                               \n\t"
    "swc1       %[x3r],         16(%[a_ptr])                               \n\t"
    "swc1       %[x3i],         20(%[a_ptr])                               \n\t"
    "swc1       %[f6],          24(%[a_ptr])                               \n\t"
    "swc1       %[f7],          28(%[a_ptr])                               \n\t"
    "lwc1       %[f0],          32(%[a_ptr])                               \n\t"
    "lwc1       %[f1],          36(%[a_ptr])                               \n\t"
    "lwc1       %[f2],          40(%[a_ptr])                               \n\t"
    "lwc1       %[f3],          44(%[a_ptr])                               \n\t"
    "lwc1       %[f4],          48(%[a_ptr])                               \n\t"
    "lwc1       %[f5],          52(%[a_ptr])                               \n\t"
    "lwc1       %[f6],          56(%[a_ptr])                               \n\t"
    "lwc1       %[f7],          60(%[a_ptr])                               \n\t"
    "add.s      %[x0r],         %[f0],           %[f2]                     \n\t"
    "add.s      %[x2r],         %[f4],           %[f6]                     \n\t"
    "add.s      %[x0i],         %[f1],           %[f3]                     \n\t"
    "add.s      %[x2i],         %[f5],           %[f7]                     \n\t"
    "sub.s      %[x1r],         %[f0],           %[f2]                     \n\t"
    "sub.s      %[x1i],         %[f1],           %[f3]                     \n\t"
    "sub.s      %[x3r],         %[f4],           %[f6]                     \n\t"
    "sub.s      %[x3i],         %[f5],           %[f7]                     \n\t"
    "lwc1       %[wk1r],        8(%[p2_rdft])                              \n\t"
    "sub.s      %[f0],          %[x0r],          %[x2r]                    \n\t"
    "sub.s      %[f1],          %[x0i],          %[x2i]                    \n\t"
    "add.s      %[f2],          %[x1i],          %[x3r]                    \n\t"
    "sub.s      %[f3],          %[x1r],          %[x3i]                    \n\t"
    "add.s      %[f4],          %[x1r],          %[x3i]                    \n\t"
    "sub.s      %[f5],          %[x1i],          %[x3r]                    \n\t"
    "lwc1       %[wk3r],        8(%[second])                               \n\t"
    "mul.s      %[x3r],         %[wk2i],         %[f0]                     \n\t"
    "mul.s      %[x3i],         %[wk2i],         %[f1]                     \n\t"
    "mul.s      %[x1r],         %[wk1r],         %[f3]                     \n\t"
    "mul.s      %[x1i],         %[wk1r],         %[f2]                     \n\t"
    "mul.s      %[f6],          %[wk3r],         %[f4]                     \n\t"
    "mul.s      %[f7],          %[wk3r],         %[f5]                     \n\t"
    "lwc1       %[wk1i],        12(%[p2_rdft])                             \n\t"
    "lwc1       %[wk3i],        12(%[second])                              \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s      %[wk1r],        %[wk2r],         %[f1]                     \n\t"
    "mul.s      %[f0],          %[wk2r],         %[f0]                     \n\t"
    "add.s      %[x3r],         %[x3r],          %[wk1r]                   \n\t"
    "neg.s      %[x3r],         %[x3r]                                     \n\t"
    "sub.s      %[x3i],         %[f0],           %[x3i]                    \n\t"
    "add.s      %[f0],          %[x0r],          %[x2r]                    \n\t"
    "add.s      %[f1],          %[x0i],          %[x2i]                    \n\t"
    "mul.s      %[x0r],         %[wk1i],         %[f2]                     \n\t"
    "mul.s      %[f3],          %[wk1i],         %[f3]                     \n\t"
    "mul.s      %[x2r],         %[wk3i],         %[f5]                     \n\t"
    "mul.s      %[f4],          %[wk3i],         %[f4]                     \n\t"
    "sub.s      %[x1r],         %[x1r],          %[x0r]                    \n\t"
    "add.s      %[x1i],         %[x1i],          %[f3]                     \n\t"
    "sub.s      %[f6],          %[f6],           %[x2r]                    \n\t"
    "add.s      %[f7],          %[f7],           %[f4]                     \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "nmadd.s    %[x3r],         %[x3r],          %[wk2r],        %[f1]     \n\t"
    "msub.s     %[x3i],         %[x3i],          %[wk2r],        %[f0]     \n\t"
    "add.s      %[f0],          %[x0r],          %[x2r]                    \n\t"
    "add.s      %[f1],          %[x0i],          %[x2i]                    \n\t"
    "nmsub.s    %[x1r],         %[x1r],          %[wk1i],        %[f2]     \n\t"
    "madd.s     %[x1i],         %[x1i],          %[wk1i],        %[f3]     \n\t"
    "nmsub.s    %[f6],          %[f6],           %[wk3i],        %[f5]     \n\t"
    "madd.s     %[f7],          %[f7],           %[wk3i],        %[f4]     \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "addiu      %[count],       %[count],        -1                        \n\t"
    "lwc1       %[wk2r],        8(%[p1_rdft])                              \n\t"
    "addiu      %[a_ptr],       %[a_ptr],        64                        \n\t"
    "addiu      %[p1_rdft],     %[p1_rdft],      8                         \n\t"
    "addiu      %[p2_rdft],     %[p2_rdft],      16                        \n\t"
    "addiu      %[first],       %[first],        8                         \n\t"
    "swc1       %[f0],          -32(%[a_ptr])                              \n\t"
    "swc1       %[f1],          -28(%[a_ptr])                              \n\t"
    "swc1       %[x1r],         -24(%[a_ptr])                              \n\t"
    "swc1       %[x1i],         -20(%[a_ptr])                              \n\t"
    "swc1       %[x3r],         -16(%[a_ptr])                              \n\t"
    "swc1       %[x3i],         -12(%[a_ptr])                              \n\t"
    "swc1       %[f6],          -8(%[a_ptr])                               \n\t"
    "swc1       %[f7],          -4(%[a_ptr])                               \n\t"
    "bgtz       %[count],       1b                                         \n\t"
    " addiu     %[second],      %[second],       8                         \n\t"
    ".set       pop                                                        \n\t"
    : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2), [f3] "=&f" (f3),
      [f4] "=&f" (f4), [f5] "=&f" (f5), [f6] "=&f" (f6), [f7] "=&f" (f7),
      [x0r] "=&f" (x0r), [x0i] "=&f" (x0i), [x1r] "=&f" (x1r),
      [x1i] "=&f" (x1i), [x2r] "=&f" (x2r), [x2i] "=&f" (x2i),
      [x3r] "=&f" (x3r), [x3i] "=&f" (x3i), [wk1r] "=&f" (wk1r),
      [wk1i] "=&f" (wk1i), [wk2r] "=&f" (wk2r), [wk2i] "=&f" (wk2i),
      [wk3r] "=&f" (wk3r), [wk3i] "=&f" (wk3i), [a_ptr] "=&r" (a_ptr),
      [p1_rdft] "=&r" (p1_rdft), [p2_rdft] "=&r" (p2_rdft),
      [count] "=&r" (count)
    : [a] "r" (a), [rdft_w] "r" (rdft_w), [first] "r" (rdft_wk3ri_first),
      [second] "r" (rdft_wk3ri_second)
    : "memory"
  );
}

static void cftmdl_128_mips(float *a) {
  float wk1r, wk1i, wk2r, wk2i, wk3r, wk3i;
  float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;
  float f0, f1, f2, f3, f4, f5, f6, f7;
  int tmp_a, count;

  __asm __volatile (
    ".set       push                                      \n\t"
    ".set       noreorder                                 \n\t"
    "addiu      %[tmp_a],   %[a],         0               \n\t"
    "addiu      %[count],   $zero,        4               \n\t"
   "1:                                                    \n\t"
    "addiu      %[count],   %[count],     -1              \n\t"
    "lwc1       %[f0],      0(%[tmp_a])                   \n\t"
    "lwc1       %[f1],      4(%[tmp_a])                   \n\t"
    "lwc1       %[f2],      32(%[tmp_a])                  \n\t"
    "lwc1       %[f3],      36(%[tmp_a])                  \n\t"
    "lwc1       %[f4],      64(%[tmp_a])                  \n\t"
    "lwc1       %[f5],      68(%[tmp_a])                  \n\t"
    "lwc1       %[f6],      96(%[tmp_a])                  \n\t"
    "lwc1       %[f7],      100(%[tmp_a])                 \n\t"
    "add.s      %[x0r],     %[f0],        %[f2]           \n\t"
    "add.s      %[x0i],     %[f1],        %[f3]           \n\t"
    "add.s      %[x2r],     %[f4],        %[f6]           \n\t"
    "add.s      %[x2i],     %[f5],        %[f7]           \n\t"
    "sub.s      %[x1r],     %[f0],        %[f2]           \n\t"
    "sub.s      %[x1i],     %[f1],        %[f3]           \n\t"
    "sub.s      %[x3r],     %[f4],        %[f6]           \n\t"
    "sub.s      %[x3i],     %[f5],        %[f7]           \n\t"
    "add.s      %[f0],      %[x0r],       %[x2r]          \n\t"
    "add.s      %[f1],      %[x0i],       %[x2i]          \n\t"
    "sub.s      %[f4],      %[x0r],       %[x2r]          \n\t"
    "sub.s      %[f5],      %[x0i],       %[x2i]          \n\t"
    "sub.s      %[f2],      %[x1r],       %[x3i]          \n\t"
    "add.s      %[f3],      %[x1i],       %[x3r]          \n\t"
    "add.s      %[f6],      %[x1r],       %[x3i]          \n\t"
    "sub.s      %[f7],      %[x1i],       %[x3r]          \n\t"
    "swc1       %[f0],      0(%[tmp_a])                   \n\t"
    "swc1       %[f1],      4(%[tmp_a])                   \n\t"
    "swc1       %[f2],      32(%[tmp_a])                  \n\t"
    "swc1       %[f3],      36(%[tmp_a])                  \n\t"
    "swc1       %[f4],      64(%[tmp_a])                  \n\t"
    "swc1       %[f5],      68(%[tmp_a])                  \n\t"
    "swc1       %[f6],      96(%[tmp_a])                  \n\t"
    "swc1       %[f7],      100(%[tmp_a])                 \n\t"
    "bgtz       %[count],   1b                            \n\t"
    " addiu     %[tmp_a],   %[tmp_a],     8               \n\t"
    ".set       pop                                       \n\t"
    : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2), [f3] "=&f" (f3),
      [f4] "=&f" (f4), [f5] "=&f" (f5), [f6] "=&f" (f6), [f7] "=&f" (f7),
      [x0r] "=&f" (x0r), [x0i] "=&f" (x0i), [x1r] "=&f" (x1r),
      [x1i] "=&f" (x1i), [x2r] "=&f" (x2r), [x2i] "=&f" (x2i),
      [x3r] "=&f" (x3r), [x3i] "=&f" (x3i), [tmp_a] "=&r" (tmp_a),
      [count] "=&r" (count)
    : [a] "r" (a)
    : "memory"
  );
  wk2r = rdft_w[2];
  __asm __volatile (
    ".set   push                                      \n\t"
    ".set   noreorder                                 \n\t"
    "addiu  %[tmp_a],   %[a],         128             \n\t"
    "addiu  %[count],   $zero,        4               \n\t"
   "1:                                                \n\t"
    "addiu  %[count],   %[count],     -1              \n\t"
    "lwc1   %[f0],      0(%[tmp_a])                   \n\t"
    "lwc1   %[f1],      4(%[tmp_a])                   \n\t"
    "lwc1   %[f2],      32(%[tmp_a])                  \n\t"
    "lwc1   %[f3],      36(%[tmp_a])                  \n\t"
    "lwc1   %[f4],      64(%[tmp_a])                  \n\t"
    "lwc1   %[f5],      68(%[tmp_a])                  \n\t"
    "lwc1   %[f6],      96(%[tmp_a])                  \n\t"
    "lwc1   %[f7],      100(%[tmp_a])                 \n\t"
    "sub.s  %[x1r],     %[f0],        %[f2]           \n\t"
    "sub.s  %[x1i],     %[f1],        %[f3]           \n\t"
    "sub.s  %[x3r],     %[f4],        %[f6]           \n\t"
    "sub.s  %[x3i],     %[f5],        %[f7]           \n\t"
    "add.s  %[x0r],     %[f0],        %[f2]           \n\t"
    "add.s  %[x0i],     %[f1],        %[f3]           \n\t"
    "add.s  %[x2r],     %[f4],        %[f6]           \n\t"
    "add.s  %[x2i],     %[f5],        %[f7]           \n\t"
    "sub.s  %[f0],      %[x1r],       %[x3i]          \n\t"
    "add.s  %[f1],      %[x1i],       %[x3r]          \n\t"
    "sub.s  %[f2],      %[x3r],       %[x1i]          \n\t"
    "add.s  %[f3],      %[x3i],       %[x1r]          \n\t"
    "add.s  %[f4],      %[x0r],       %[x2r]          \n\t"
    "add.s  %[f5],      %[x0i],       %[x2i]          \n\t"
    "sub.s  %[f6],      %[f0],        %[f1]           \n\t"
    "add.s  %[f0],      %[f0],        %[f1]           \n\t"
    "sub.s  %[f7],      %[f2],        %[f3]           \n\t"
    "add.s  %[f2],      %[f2],        %[f3]           \n\t"
    "sub.s  %[f1],      %[x2i],       %[x0i]          \n\t"
    "mul.s  %[f6],      %[f6],        %[wk2r]         \n\t"
    "mul.s  %[f0],      %[f0],        %[wk2r]         \n\t"
    "sub.s  %[f3],      %[x0r],       %[x2r]          \n\t"
    "mul.s  %[f7],      %[f7],        %[wk2r]         \n\t"
    "mul.s  %[f2],      %[f2],        %[wk2r]         \n\t"
    "swc1   %[f4],      0(%[tmp_a])                   \n\t"
    "swc1   %[f5],      4(%[tmp_a])                   \n\t"
    "swc1   %[f6],      32(%[tmp_a])                  \n\t"
    "swc1   %[f0],      36(%[tmp_a])                  \n\t"
    "swc1   %[f1],      64(%[tmp_a])                  \n\t"
    "swc1   %[f3],      68(%[tmp_a])                  \n\t"
    "swc1   %[f7],      96(%[tmp_a])                  \n\t"
    "swc1   %[f2],      100(%[tmp_a])                 \n\t"
    "bgtz   %[count],   1b                            \n\t"
    " addiu %[tmp_a],   %[tmp_a],     8               \n\t"
    ".set   pop                                       \n\t"
    : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2), [f3] "=&f" (f3),
      [f4] "=&f" (f4), [f5] "=&f" (f5), [f6] "=&f" (f6), [f7] "=&f" (f7),
      [x0r] "=&f" (x0r), [x0i] "=&f" (x0i), [x1r] "=&f" (x1r),
      [x1i] "=&f" (x1i), [x2r] "=&f" (x2r), [x2i] "=&f" (x2i),
      [x3r] "=&f" (x3r), [x3i] "=&f" (x3i), [tmp_a] "=&r" (tmp_a),
      [count] "=&r" (count)
    : [a] "r" (a), [wk2r] "f" (wk2r)
    : "memory"
  );
  wk2i = rdft_w[3];
  wk1r = rdft_w[4];
  wk1i = rdft_w[5];
  wk3r = rdft_wk3ri_first[2];
  wk3i = rdft_wk3ri_first[3];

  __asm __volatile (
    ".set       push                                                       \n\t"
    ".set       noreorder                                                  \n\t"
    "addiu      %[tmp_a],       %[a],           256                        \n\t"
    "addiu      %[count],       $zero,          4                          \n\t"
   "1:                                                                     \n\t"
    "addiu      %[count],       %[count],       -1                         \n\t"
    "lwc1       %[f0],          0(%[tmp_a])                                \n\t"
    "lwc1       %[f1],          4(%[tmp_a])                                \n\t"
    "lwc1       %[f2],          32(%[tmp_a])                               \n\t"
    "lwc1       %[f3],          36(%[tmp_a])                               \n\t"
    "lwc1       %[f4],          64(%[tmp_a])                               \n\t"
    "lwc1       %[f5],          68(%[tmp_a])                               \n\t"
    "lwc1       %[f6],          96(%[tmp_a])                               \n\t"
    "lwc1       %[f7],          100(%[tmp_a])                              \n\t"
    "add.s      %[x0r],         %[f0],          %[f2]                      \n\t"
    "add.s      %[x2r],         %[f4],          %[f6]                      \n\t"
    "add.s      %[x0i],         %[f1],          %[f3]                      \n\t"
    "add.s      %[x2i],         %[f5],          %[f7]                      \n\t"
    "sub.s      %[x1r],         %[f0],          %[f2]                      \n\t"
    "sub.s      %[x1i],         %[f1],          %[f3]                      \n\t"
    "sub.s      %[x3r],         %[f4],          %[f6]                      \n\t"
    "sub.s      %[x3i],         %[f5],          %[f7]                      \n\t"
    "sub.s      %[f0],          %[x0r],         %[x2r]                     \n\t"
    "sub.s      %[f1],          %[x0i],         %[x2i]                     \n\t"
    "add.s      %[f2],          %[x1i],         %[x3r]                     \n\t"
    "sub.s      %[f3],          %[x1r],         %[x3i]                     \n\t"
    "add.s      %[f4],          %[x1r],         %[x3i]                     \n\t"
    "sub.s      %[f5],          %[x1i],         %[x3r]                     \n\t"
    "mul.s      %[x3r],         %[wk2r],        %[f0]                      \n\t"
    "mul.s      %[x3i],         %[wk2r],        %[f1]                      \n\t"
    "mul.s      %[x1r],         %[wk1r],        %[f3]                      \n\t"
    "mul.s      %[x1i],         %[wk1r],        %[f2]                      \n\t"
    "mul.s      %[f6],          %[wk3r],        %[f4]                      \n\t"
    "mul.s      %[f7],          %[wk3r],        %[f5]                      \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s      %[f1],          %[wk2i],        %[f1]                      \n\t"
    "mul.s      %[f0],          %[wk2i],        %[f0]                      \n\t"
    "sub.s      %[x3r],         %[x3r],         %[f1]                      \n\t"
    "add.s      %[x3i],         %[x3i],         %[f0]                      \n\t"
    "add.s      %[f0],          %[x0r],         %[x2r]                     \n\t"
    "add.s      %[f1],          %[x0i],         %[x2i]                     \n\t"
    "mul.s      %[f2],          %[wk1i],        %[f2]                      \n\t"
    "mul.s      %[f3],          %[wk1i],        %[f3]                      \n\t"
    "mul.s      %[f5],          %[wk3i],        %[f5]                      \n\t"
    "mul.s      %[f4],          %[wk3i],        %[f4]                      \n\t"
    "sub.s      %[x1r],         %[x1r],         %[f2]                      \n\t"
    "add.s      %[x1i],         %[x1i],         %[f3]                      \n\t"
    "sub.s      %[f6],          %[f6],          %[f5]                      \n\t"
    "add.s      %[f7],          %[f7],          %[f4]                      \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "nmsub.s    %[x3r],         %[x3r],         %[wk2i],        %[f1]      \n\t"
    "madd.s     %[x3i],         %[x3i],         %[wk2i],        %[f0]      \n\t"
    "add.s      %[f0],          %[x0r],         %[x2r]                     \n\t"
    "add.s      %[f1],          %[x0i],         %[x2i]                     \n\t"
    "nmsub.s    %[x1r],         %[x1r],         %[wk1i],        %[f2]      \n\t"
    "madd.s     %[x1i],         %[x1i],         %[wk1i],        %[f3]      \n\t"
    "nmsub.s    %[f6],          %[f6],          %[wk3i],        %[f5]      \n\t"
    "madd.s     %[f7],          %[f7],          %[wk3i],        %[f4]      \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "swc1       %[f0],          0(%[tmp_a])                                \n\t"
    "swc1       %[f1],          4(%[tmp_a])                                \n\t"
    "swc1       %[x1r],         32(%[tmp_a])                               \n\t"
    "swc1       %[x1i],         36(%[tmp_a])                               \n\t"
    "swc1       %[x3r],         64(%[tmp_a])                               \n\t"
    "swc1       %[x3i],         68(%[tmp_a])                               \n\t"
    "swc1       %[f6],          96(%[tmp_a])                               \n\t"
    "swc1       %[f7],          100(%[tmp_a])                              \n\t"
    "bgtz       %[count],       1b                                         \n\t"
    " addiu     %[tmp_a],       %[tmp_a],       8                          \n\t"
    ".set       pop                                                        \n\t"
    : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2), [f3] "=&f" (f3),
      [f4] "=&f" (f4), [f5] "=&f" (f5), [f6] "=&f" (f6), [f7] "=&f" (f7),
      [x0r] "=&f" (x0r), [x0i] "=&f" (x0i), [x1r] "=&f" (x1r),
      [x1i] "=&f" (x1i), [x2r] "=&f" (x2r), [x2i] "=&f" (x2i),
      [x3r] "=&f" (x3r), [x3i] "=&f" (x3i), [tmp_a] "=&r" (tmp_a),
      [count] "=&r" (count)
    : [a] "r" (a),  [wk1r] "f" (wk1r), [wk1i] "f" (wk1i), [wk2r] "f" (wk2r),
      [wk2i] "f" (wk2i), [wk3r] "f" (wk3r), [wk3i] "f" (wk3i)
    : "memory"
  );

  wk1r = rdft_w[6];
  wk1i = rdft_w[7];
  wk3r = rdft_wk3ri_second[2];
  wk3i = rdft_wk3ri_second[3];

  __asm __volatile (
    ".set       push                                                       \n\t"
    ".set       noreorder                                                  \n\t"
    "addiu      %[tmp_a],       %[a],           384                        \n\t"
    "addiu      %[count],       $zero,          4                          \n\t"
   "1:                                                                     \n\t"
    "addiu      %[count],       %[count],       -1                         \n\t"
    "lwc1       %[f0],          0(%[tmp_a])                                \n\t"
    "lwc1       %[f1],          4(%[tmp_a])                                \n\t"
    "lwc1       %[f2],          32(%[tmp_a])                               \n\t"
    "lwc1       %[f3],          36(%[tmp_a])                               \n\t"
    "lwc1       %[f4],          64(%[tmp_a])                               \n\t"
    "lwc1       %[f5],          68(%[tmp_a])                               \n\t"
    "lwc1       %[f6],          96(%[tmp_a])                               \n\t"
    "lwc1       %[f7],          100(%[tmp_a])                              \n\t"
    "add.s      %[x0r],         %[f0],          %[f2]                      \n\t"
    "add.s      %[x2r],         %[f4],          %[f6]                      \n\t"
    "add.s      %[x0i],         %[f1],          %[f3]                      \n\t"
    "add.s      %[x2i],         %[f5],          %[f7]                      \n\t"
    "sub.s      %[x1r],         %[f0],          %[f2]                      \n\t"
    "sub.s      %[x1i],         %[f1],          %[f3]                      \n\t"
    "sub.s      %[x3r],         %[f4],          %[f6]                      \n\t"
    "sub.s      %[x3i],         %[f5],          %[f7]                      \n\t"
    "sub.s      %[f0],          %[x0r],         %[x2r]                     \n\t"
    "sub.s      %[f1],          %[x0i],         %[x2i]                     \n\t"
    "add.s      %[f2],          %[x1i],         %[x3r]                     \n\t"
    "sub.s      %[f3],          %[x1r],         %[x3i]                     \n\t"
    "add.s      %[f4],          %[x1r],         %[x3i]                     \n\t"
    "sub.s      %[f5],          %[x1i],         %[x3r]                     \n\t"
    "mul.s      %[x3r],         %[wk2i],        %[f0]                      \n\t"
    "mul.s      %[x3i],         %[wk2i],        %[f1]                      \n\t"
    "mul.s      %[x1r],         %[wk1r],        %[f3]                      \n\t"
    "mul.s      %[x1i],         %[wk1r],        %[f2]                      \n\t"
    "mul.s      %[f6],          %[wk3r],        %[f4]                      \n\t"
    "mul.s      %[f7],          %[wk3r],        %[f5]                      \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s      %[f1],          %[wk2r],        %[f1]                      \n\t"
    "mul.s      %[f0],          %[wk2r],        %[f0]                      \n\t"
    "add.s      %[x3r],         %[x3r],         %[f1]                      \n\t"
    "neg.s      %[x3r],         %[x3r]                                     \n\t"
    "sub.s      %[x3i],         %[f0],          %[x3i]                     \n\t"
    "add.s      %[f0],          %[x0r],         %[x2r]                     \n\t"
    "add.s      %[f1],          %[x0i],         %[x2i]                     \n\t"
    "mul.s      %[f2],          %[wk1i],        %[f2]                      \n\t"
    "mul.s      %[f3],          %[wk1i],        %[f3]                      \n\t"
    "mul.s      %[f5],          %[wk3i],        %[f5]                      \n\t"
    "mul.s      %[f4],          %[wk3i],        %[f4]                      \n\t"
    "sub.s      %[x1r],         %[x1r],         %[f2]                      \n\t"
    "add.s      %[x1i],         %[x1i],         %[f3]                      \n\t"
    "sub.s      %[f6],          %[f6],          %[f5]                      \n\t"
    "add.s      %[f7],          %[f7],          %[f4]                      \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "nmadd.s    %[x3r],         %[x3r],         %[wk2r],        %[f1]      \n\t"
    "msub.s     %[x3i],         %[x3i],         %[wk2r],        %[f0]      \n\t"
    "add.s      %[f0],          %[x0r],         %[x2r]                     \n\t"
    "add.s      %[f1],          %[x0i],         %[x2i]                     \n\t"
    "nmsub.s    %[x1r],         %[x1r],         %[wk1i],        %[f2]      \n\t"
    "madd.s     %[x1i],         %[x1i],         %[wk1i],        %[f3]      \n\t"
    "nmsub.s    %[f6],          %[f6],          %[wk3i],        %[f5]      \n\t"
    "madd.s     %[f7],          %[f7],          %[wk3i],        %[f4]      \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "swc1       %[f0],          0(%[tmp_a])                                \n\t"
    "swc1       %[f1],          4(%[tmp_a])                                \n\t"
    "swc1       %[x1r],         32(%[tmp_a])                               \n\t"
    "swc1       %[x1i],         36(%[tmp_a])                               \n\t"
    "swc1       %[x3r],         64(%[tmp_a])                               \n\t"
    "swc1       %[x3i],         68(%[tmp_a])                               \n\t"
    "swc1       %[f6],          96(%[tmp_a])                               \n\t"
    "swc1       %[f7],          100(%[tmp_a])                              \n\t"
    "bgtz       %[count],       1b                                         \n\t"
    " addiu     %[tmp_a],       %[tmp_a],       8                          \n\t"
    ".set       pop                                                        \n\t"
    : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2), [f3] "=&f" (f3),
      [f4] "=&f" (f4), [f5] "=&f" (f5), [f6] "=&f" (f6), [f7] "=&f" (f7),
      [x0r] "=&f" (x0r), [x0i] "=&f" (x0i), [x1r] "=&f" (x1r),
      [x1i] "=&f" (x1i), [x2r] "=&f" (x2r), [x2i] "=&f" (x2i),
      [x3r] "=&f" (x3r), [x3i] "=&f" (x3i), [tmp_a] "=&r" (tmp_a),
      [count] "=&r" (count)
    : [a] "r" (a), [wk1r] "f" (wk1r), [wk1i] "f" (wk1i), [wk2r] "f" (wk2r),
      [wk2i] "f" (wk2i), [wk3r] "f" (wk3r), [wk3i] "f" (wk3i)
    : "memory"
  );
}

static void cftfsub_128_mips(float *a) {
  float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;
  float f0, f1, f2, f3, f4, f5, f6, f7;
  int tmp_a, count;

  cft1st_128(a);
  cftmdl_128(a);

  __asm __volatile (
    ".set       push                                      \n\t"
    ".set       noreorder                                 \n\t"
    "addiu      %[tmp_a],       %[a],         0           \n\t"
    "addiu      %[count],       $zero,        16          \n\t"
   "1:                                                    \n\t"
    "addiu      %[count],       %[count],     -1          \n\t"
    "lwc1       %[f0],          0(%[tmp_a])               \n\t"
    "lwc1       %[f1],          4(%[tmp_a])               \n\t"
    "lwc1       %[f2],          128(%[tmp_a])             \n\t"
    "lwc1       %[f3],          132(%[tmp_a])             \n\t"
    "lwc1       %[f4],          256(%[tmp_a])             \n\t"
    "lwc1       %[f5],          260(%[tmp_a])             \n\t"
    "lwc1       %[f6],          384(%[tmp_a])             \n\t"
    "lwc1       %[f7],          388(%[tmp_a])             \n\t"
    "add.s      %[x0r],         %[f0],        %[f2]       \n\t"
    "add.s      %[x0i],         %[f1],        %[f3]       \n\t"
    "add.s      %[x2r],         %[f4],        %[f6]       \n\t"
    "add.s      %[x2i],         %[f5],        %[f7]       \n\t"
    "sub.s      %[x1r],         %[f0],        %[f2]       \n\t"
    "sub.s      %[x1i],         %[f1],        %[f3]       \n\t"
    "sub.s      %[x3r],         %[f4],        %[f6]       \n\t"
    "sub.s      %[x3i],         %[f5],        %[f7]       \n\t"
    "add.s      %[f0],          %[x0r],       %[x2r]      \n\t"
    "add.s      %[f1],          %[x0i],       %[x2i]      \n\t"
    "sub.s      %[f4],          %[x0r],       %[x2r]      \n\t"
    "sub.s      %[f5],          %[x0i],       %[x2i]      \n\t"
    "sub.s      %[f2],          %[x1r],       %[x3i]      \n\t"
    "add.s      %[f3],          %[x1i],       %[x3r]      \n\t"
    "add.s      %[f6],          %[x1r],       %[x3i]      \n\t"
    "sub.s      %[f7],          %[x1i],       %[x3r]      \n\t"
    "swc1       %[f0],          0(%[tmp_a])               \n\t"
    "swc1       %[f1],          4(%[tmp_a])               \n\t"
    "swc1       %[f2],          128(%[tmp_a])             \n\t"
    "swc1       %[f3],          132(%[tmp_a])             \n\t"
    "swc1       %[f4],          256(%[tmp_a])             \n\t"
    "swc1       %[f5],          260(%[tmp_a])             \n\t"
    "swc1       %[f6],          384(%[tmp_a])             \n\t"
    "swc1       %[f7],          388(%[tmp_a])             \n\t"
    "bgtz       %[count],       1b                        \n\t"
    " addiu     %[tmp_a],       %[tmp_a],   8             \n\t"
    ".set       pop                                       \n\t"
    : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2), [f3] "=&f" (f3),
      [f4] "=&f" (f4), [f5] "=&f" (f5), [f6] "=&f" (f6), [f7] "=&f" (f7),
      [x0r] "=&f" (x0r), [x0i] "=&f" (x0i), [x1r] "=&f" (x1r),
      [x1i] "=&f" (x1i), [x2r] "=&f" (x2r), [x2i] "=&f" (x2i),
      [x3r] "=&f" (x3r), [x3i] "=&f" (x3i), [tmp_a] "=&r" (tmp_a),
      [count] "=&r" (count)
    : [a] "r" (a)
    : "memory"
  );
}

static void cftbsub_128_mips(float *a) {
  float f0, f1, f2, f3, f4, f5, f6, f7;
  float f8, f9, f10, f11, f12, f13, f14, f15;
  float f16, f17, f18, f19, f20, f21, f22, f23;
  int tmp_a, count;

  cft1st_128(a);
  cftmdl_128(a);

  __asm __volatile (
    ".set       push                                        \n\t"
    ".set       noreorder                                   \n\t"
    "addiu      %[tmp_a],   %[a],           0               \n\t"
    "addiu      %[count],   $zero,          8               \n\t"
   "1:                                                      \n\t"
    "addiu      %[count],   %[count],       -1              \n\t"
    "lwc1       %[f0],      0(%[tmp_a])                     \n\t"
    "lwc1       %[f1],      4(%[tmp_a])                     \n\t"
    "lwc1       %[f2],      128(%[tmp_a])                   \n\t"
    "lwc1       %[f3],      132(%[tmp_a])                   \n\t"
    "lwc1       %[f4],      256(%[tmp_a])                   \n\t"
    "lwc1       %[f5],      260(%[tmp_a])                   \n\t"
    "lwc1       %[f6],      384(%[tmp_a])                   \n\t"
    "lwc1       %[f7],      388(%[tmp_a])                   \n\t"
    "lwc1       %[f8],      8(%[tmp_a])                     \n\t"
    "lwc1       %[f9],      12(%[tmp_a])                    \n\t"
    "lwc1       %[f10],     136(%[tmp_a])                   \n\t"
    "lwc1       %[f11],     140(%[tmp_a])                   \n\t"
    "lwc1       %[f12],     264(%[tmp_a])                   \n\t"
    "lwc1       %[f13],     268(%[tmp_a])                   \n\t"
    "lwc1       %[f14],     392(%[tmp_a])                   \n\t"
    "lwc1       %[f15],     396(%[tmp_a])                   \n\t"
    "add.s      %[f16],     %[f0],          %[f2]           \n\t"
    "add.s      %[f17],     %[f1],          %[f3]           \n\t"
    "add.s      %[f18],     %[f4],          %[f6]           \n\t"
    "add.s      %[f19],     %[f5],          %[f7]           \n\t"
    "sub.s      %[f20],     %[f0],          %[f2]           \n\t"
    "sub.s      %[f21],     %[f3],          %[f1]           \n\t"
    "sub.s      %[f22],     %[f4],          %[f6]           \n\t"
    "sub.s      %[f23],     %[f5],          %[f7]           \n\t"
    "add.s      %[f0],      %[f8],          %[f10]          \n\t"
    "add.s      %[f1],      %[f9],          %[f11]          \n\t"
    "add.s      %[f2],      %[f12],         %[f14]          \n\t"
    "add.s      %[f3],      %[f13],         %[f15]          \n\t"
    "sub.s      %[f4],      %[f8],          %[f10]          \n\t"
    "sub.s      %[f5],      %[f11],         %[f9]           \n\t"
    "sub.s      %[f6],      %[f12],         %[f14]          \n\t"
    "sub.s      %[f7],      %[f13],         %[f15]          \n\t"
    "add.s      %[f8],      %[f16],         %[f18]          \n\t"
    "add.s      %[f9],      %[f17],         %[f19]          \n\t"
    "sub.s      %[f12],     %[f16],         %[f18]          \n\t"
    "sub.s      %[f13],     %[f19],         %[f17]          \n\t"
    "sub.s      %[f10],     %[f20],         %[f23]          \n\t"
    "sub.s      %[f11],     %[f21],         %[f22]          \n\t"
    "add.s      %[f14],     %[f20],         %[f23]          \n\t"
    "add.s      %[f15],     %[f21],         %[f22]          \n\t"
    "neg.s      %[f9],      %[f9]                           \n\t"
    "add.s      %[f16],      %[f0],         %[f2]           \n\t"
    "add.s      %[f17],      %[f1],         %[f3]           \n\t"
    "sub.s      %[f20],      %[f0],         %[f2]           \n\t"
    "sub.s      %[f21],      %[f3],         %[f1]           \n\t"
    "sub.s      %[f18],      %[f4],         %[f7]           \n\t"
    "sub.s      %[f19],      %[f5],         %[f6]           \n\t"
    "add.s      %[f22],      %[f4],         %[f7]           \n\t"
    "add.s      %[f23],      %[f5],         %[f6]           \n\t"
    "neg.s      %[f17],      %[f17]                         \n\t"
    "swc1       %[f8],      0(%[tmp_a])                     \n\t"
    "swc1       %[f10],     128(%[tmp_a])                   \n\t"
    "swc1       %[f11],     132(%[tmp_a])                   \n\t"
    "swc1       %[f12],     256(%[tmp_a])                   \n\t"
    "swc1       %[f13],     260(%[tmp_a])                   \n\t"
    "swc1       %[f14],     384(%[tmp_a])                   \n\t"
    "swc1       %[f15],     388(%[tmp_a])                   \n\t"
    "swc1       %[f9],      4(%[tmp_a])                     \n\t"
    "swc1       %[f16],     8(%[tmp_a])                     \n\t"
    "swc1       %[f18],     136(%[tmp_a])                   \n\t"
    "swc1       %[f19],     140(%[tmp_a])                   \n\t"
    "swc1       %[f20],     264(%[tmp_a])                   \n\t"
    "swc1       %[f21],     268(%[tmp_a])                   \n\t"
    "swc1       %[f22],     392(%[tmp_a])                   \n\t"
    "swc1       %[f23],     396(%[tmp_a])                   \n\t"
    "swc1       %[f17],     12(%[tmp_a])                    \n\t"
    "bgtz       %[count],   1b                              \n\t"
    " addiu     %[tmp_a],   %[tmp_a],       16              \n\t"
    ".set       pop                                         \n\t"
    : [f0] "=&f" (f0), [f1] "=&f" (f1), [f2] "=&f" (f2), [f3] "=&f" (f3),
      [f4] "=&f" (f4), [f5] "=&f" (f5), [f6] "=&f" (f6), [f7] "=&f" (f7),
      [f8] "=&f" (f8), [f9] "=&f" (f9), [f10] "=&f" (f10), [f11] "=&f" (f11),
      [f12] "=&f" (f12), [f13] "=&f" (f13), [f14] "=&f" (f14),
      [f15] "=&f" (f15), [f16] "=&f" (f16), [f17] "=&f" (f17),
      [f18] "=&f" (f18), [f19] "=&f" (f19), [f20] "=&f" (f20),
      [f21] "=&f" (f21), [f22] "=&f" (f22), [f23] "=&f" (f23),
      [tmp_a] "=&r" (tmp_a), [count] "=&r" (count)
    : [a] "r" (a)
    : "memory"
  );
}

static void rftfsub_128_mips(float *a) {
  const float *c = rdft_w + 32;
  float wkr, wki, xr, xi, yr, yi;
  const float temp = 0.5f;
  float aj20=0, aj21=0, ak20=0, ak21=0, ck1=0;
  float *a1 = a;
  float *a2 = a;
  float *c1 = rdft_w + 33;
  float *c2 = c1 + 30;

  __asm __volatile (
    ".set      push                                             \n\t"
    ".set      noreorder                                        \n\t"
    "lwc1      %[aj20],     8(%[a2])                            \n\t"
    "lwc1      %[ak20],     504(%[a1])                          \n\t"
    "lwc1      %[ck1],      0(%[c2])                            \n\t"
    "lwc1      %[aj21],     12(%[a2])                           \n\t"
    "lwc1      %[ak21],     508(%[a1])                          \n\t"
    "sub.s     %[wkr],      %[temp],      %[ck1]                \n\t"
    "sub.s     %[xr],       %[aj20],      %[ak20]               \n\t"
    "add.s     %[xi],       %[aj21],      %[ak21]               \n\t"
    "lwc1      %[wki],      0(%[c1])                            \n\t"
    "addiu     %[c2],       %[c2],-4                            \n\t"
    "mul.s     %[yr],       %[wkr],       %[xr]                 \n\t"
    "mul.s     %[yi],       %[wkr],       %[xi]                 \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s     %[xi],       %[wki],       %[xi]                 \n\t"
    "mul.s     %[xr],       %[wki],       %[xr]                 \n\t"
    "sub.s     %[yr],       %[yr],        %[xi]                 \n\t"
    "add.s     %[yi],       %[yi],        %[xr]                 \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "nmsub.s   %[yr],       %[yr],        %[wki],     %[xi]     \n\t"
    "madd.s    %[yi],       %[yi],        %[wki],     %[xr]     \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "addiu     %[c1],       %[c1],        4                     \n\t"
    "sub.s     %[aj20],     %[aj20],      %[yr]                 \n\t"
    "sub.s     %[aj21],     %[aj21],      %[yi]                 \n\t"
    "add.s     %[ak20],     %[ak20],      %[yr]                 \n\t"
    "sub.s     %[ak21],     %[ak21],      %[yi]                 \n\t"
    "addiu     %[a2],       %[a2],        8                     \n\t"
    "swc1      %[aj20],     0(%[a2])                            \n\t"
    "swc1      %[aj21],     4(%[a2])                            \n\t"
    "swc1      %[ak20],     504(%[a1])                          \n\t"
    "swc1      %[ak21],     508(%[a1])                          \n\t"
    "addiu     %[a1],       %[a1],        -8                    \n\t"
    //15x2 passes:
   "1:                                                          \n\t"
    "lwc1      %[ck1],      0(%[c2])                            \n\t"
    "lwc1      %[aj20],     8(%[a2])                            \n\t"
    "lwc1      %[aj21],     12(%[a2])                           \n\t"
    "lwc1      %[ak20],     504(%[a1])                          \n\t"
    "lwc1      %[ak21],     508(%[a1])                          \n\t"
    "lwc1      $f0,         -4(%[c2])                           \n\t"
    "lwc1      $f2,         16(%[a2])                           \n\t"
    "lwc1      $f3,         20(%[a2])                           \n\t"
    "lwc1      $f8,         496(%[a1])                          \n\t"
    "lwc1      $f7,         500(%[a1])                          \n\t"
    "sub.s     %[wkr],      %[temp],      %[ck1]                \n\t"
    "sub.s     %[xr],       %[aj20],      %[ak20]               \n\t"
    "add.s     %[xi],       %[aj21],      %[ak21]               \n\t"
    "lwc1      %[wki],      0(%[c1])                            \n\t"
    "sub.s     $f0,         %[temp],      $f0                   \n\t"
    "sub.s     $f6,         $f2,          $f8                   \n\t"
    "add.s     $f4,         $f3,          $f7                   \n\t"
    "lwc1      $f5,         4(%[c1])                            \n\t"
    "mul.s     %[yr],       %[wkr],       %[xr]                 \n\t"
    "mul.s     %[yi],       %[wkr],       %[xi]                 \n\t"
    "mul.s     $f1,         $f0,          $f6                   \n\t"
    "mul.s     $f0,         $f0,          $f4                   \n\t"
    "addiu     %[c2],       %[c2],        -8                    \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s     %[xi],       %[wki],       %[xi]                 \n\t"
    "mul.s     %[xr],       %[wki],       %[xr]                 \n\t"
    "mul.s     $f4,         $f5,          $f4                   \n\t"
    "mul.s     $f6,         $f5,          $f6                   \n\t"
    "sub.s     %[yr],       %[yr],        %[xi]                 \n\t"
    "add.s     %[yi],       %[yi],        %[xr]                 \n\t"
    "sub.s     $f1,         $f1,          $f4                   \n\t"
    "add.s     $f0,         $f0,          $f6                   \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "nmsub.s   %[yr],       %[yr],        %[wki],     %[xi]     \n\t"
    "madd.s    %[yi],       %[yi],        %[wki],     %[xr]     \n\t"
    "nmsub.s   $f1,         $f1,          $f5,        $f4       \n\t"
    "madd.s    $f0,         $f0,          $f5,        $f6       \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "addiu     %[c1],       %[c1],        8                     \n\t"
    "sub.s     %[aj20],     %[aj20],      %[yr]                 \n\t"
    "sub.s     %[aj21],     %[aj21],      %[yi]                 \n\t"
    "add.s     %[ak20],     %[ak20],      %[yr]                 \n\t"
    "sub.s     %[ak21],     %[ak21],      %[yi]                 \n\t"
    "sub.s     $f2,         $f2,          $f1                   \n\t"
    "sub.s     $f3,         $f3,          $f0                   \n\t"
    "add.s     $f1,         $f8,          $f1                   \n\t"
    "sub.s     $f0,         $f7,          $f0                   \n\t"
    "swc1      %[aj20],     8(%[a2])                            \n\t"
    "swc1      %[aj21],     12(%[a2])                           \n\t"
    "swc1      %[ak20],     504(%[a1])                          \n\t"
    "swc1      %[ak21],     508(%[a1])                          \n\t"
    "swc1      $f2,         16(%[a2])                           \n\t"
    "swc1      $f3,         20(%[a2])                           \n\t"
    "swc1      $f1,         496(%[a1])                          \n\t"
    "swc1      $f0,         500(%[a1])                          \n\t"
    "addiu     %[a2],       %[a2],        16                    \n\t"
    "bne       %[c2],       %[c],         1b                    \n\t"
    " addiu    %[a1],       %[a1],        -16                   \n\t"
    ".set      pop                                              \n\t"
    : [a] "+r" (a), [c] "+r" (c), [a1] "+r" (a1), [a2] "+r" (a2),
      [c1] "+r" (c1), [c2] "+r" (c2), [wkr] "=&f" (wkr), [wki] "=&f" (wki),
      [xr] "=&f" (xr), [xi] "=&f" (xi), [yr] "=&f" (yr), [yi] "=&f" (yi),
      [aj20] "=&f" (aj20), [aj21] "=&f" (aj21), [ak20] "=&f" (ak20),
      [ak21] "=&f" (ak21), [ck1] "=&f" (ck1)
    : [temp] "f" (temp)
    : "memory", "$f0", "$f1", "$f2", "$f3", "$f4", "$f5", "$f6", "$f7", "$f8"
  );
}

static void rftbsub_128_mips(float *a) {
  const float *c = rdft_w + 32;
  float wkr, wki, xr, xi, yr, yi;
  a[1] = -a[1];
  a[65] = -a[65];
  const float temp = 0.5f;
  float aj20=0, aj21=0, ak20=0, ak21=0, ck1=0;
  float *a1 = a;
  float *a2 = a;
  float *c1 = rdft_w + 33;
  float *c2 = c1 + 30;

  __asm __volatile (
    ".set      push                                           \n\t"
    ".set      noreorder                                      \n\t"
    "lwc1      %[aj20],     8(%[a2])                          \n\t"
    "lwc1      %[ak20],     504(%[a1])                        \n\t"
    "lwc1      %[ck1],      0(%[c2])                          \n\t"
    "lwc1      %[aj21],     12(%[a2])                         \n\t"
    "lwc1      %[ak21],     508(%[a1])                        \n\t"
    "sub.s     %[wkr],      %[temp],    %[ck1]                \n\t"
    "sub.s     %[xr],       %[aj20],    %[ak20]               \n\t"
    "add.s     %[xi],       %[aj21],    %[ak21]               \n\t"
    "lwc1      %[wki],      0(%[c1])                          \n\t"
    "addiu     %[c2],       %[c2],       -4                   \n\t"
    "mul.s     %[yr],       %[wkr],     %[xr]                 \n\t"
    "mul.s     %[yi],       %[wkr],     %[xi]                 \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s     %[xi],       %[wki],     %[xi]                 \n\t"
    "mul.s     %[xr],       %[wki],     %[xr]                 \n\t"
    "add.s     %[yr],       %[yr],      %[xi]                 \n\t"
    "sub.s     %[yi],       %[yi],      %[xr]                 \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "madd.s    %[yr],       %[yr],      %[wki],     %[xi]     \n\t"
    "nmsub.s   %[yi],       %[yi],      %[wki],     %[xr]     \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "addiu     %[c1],       %[c1],4                           \n\t"
    "sub.s     %[aj20],     %[aj20],    %[yr]                 \n\t"
    "sub.s     %[aj21],     %[yi],      %[aj21]               \n\t"
    "add.s     %[ak20],     %[ak20],    %[yr]                 \n\t"
    "sub.s     %[ak21],     %[yi],      %[ak21]               \n\t"
    "addiu     %[a2],       %[a2],      8                     \n\t"
    "swc1      %[aj20],     0(%[a2])                          \n\t"
    "swc1      %[aj21],     4(%[a2])                          \n\t"
    "swc1      %[ak20],     504(%[a1])                        \n\t"
    "swc1      %[ak21],     508(%[a1])                        \n\t"
    "addiu     %[a1],       %[a1],      -8                    \n\t"
    //15x2 passes:
   "1:                                                        \n\t"
    "lwc1      %[ck1],      0(%[c2])                          \n\t"
    "lwc1      %[aj20],     8(%[a2])                          \n\t"
    "lwc1      %[aj21],     12(%[a2])                         \n\t"
    "lwc1      %[ak20],     504(%[a1])                        \n\t"
    "lwc1      %[ak21],     508(%[a1])                        \n\t"
    "lwc1      $f0,         -4(%[c2])                         \n\t"
    "lwc1      $f2,         16(%[a2])                         \n\t"
    "lwc1      $f3,         20(%[a2])                         \n\t"
    "lwc1      $f8,         496(%[a1])                        \n\t"
    "lwc1      $f7,         500(%[a1])                        \n\t"
    "sub.s     %[wkr],      %[temp],    %[ck1]                \n\t"
    "sub.s     %[xr],       %[aj20],    %[ak20]               \n\t"
    "add.s     %[xi],       %[aj21],    %[ak21]               \n\t"
    "lwc1      %[wki],      0(%[c1])                          \n\t"
    "sub.s     $f0,         %[temp],    $f0                   \n\t"
    "sub.s     $f6,         $f2,        $f8                   \n\t"
    "add.s     $f4,         $f3,        $f7                   \n\t"
    "lwc1      $f5,         4(%[c1])                          \n\t"
    "mul.s     %[yr],       %[wkr],     %[xr]                 \n\t"
    "mul.s     %[yi],       %[wkr],     %[xi]                 \n\t"
    "mul.s     $f1,         $f0,        $f6                   \n\t"
    "mul.s     $f0,         $f0,        $f4                   \n\t"
    "addiu     %[c2],       %[c2],      -8                    \n\t"
#if !defined(MIPS32_R2_LE)
    "mul.s     %[xi],       %[wki],     %[xi]                 \n\t"
    "mul.s     %[xr],       %[wki],     %[xr]                 \n\t"
    "mul.s     $f4,         $f5,        $f4                   \n\t"
    "mul.s     $f6,         $f5,        $f6                   \n\t"
    "add.s     %[yr],       %[yr],      %[xi]                 \n\t"
    "sub.s     %[yi],       %[yi],      %[xr]                 \n\t"
    "add.s     $f1,         $f1,        $f4                   \n\t"
    "sub.s     $f0,         $f0,        $f6                   \n\t"
#else // #if !defined(MIPS32_R2_LE)
    "madd.s    %[yr],       %[yr],      %[wki],     %[xi]     \n\t"
    "nmsub.s   %[yi],       %[yi],      %[wki],     %[xr]     \n\t"
    "madd.s    $f1,         $f1,        $f5,        $f4       \n\t"
    "nmsub.s   $f0,         $f0,        $f5,        $f6       \n\t"
#endif // #if !defined(MIPS32_R2_LE)
    "addiu     %[c1],       %[c1],      8                     \n\t"
    "sub.s     %[aj20],     %[aj20],    %[yr]                 \n\t"
    "sub.s     %[aj21],     %[yi],      %[aj21]               \n\t"
    "add.s     %[ak20],     %[ak20],    %[yr]                 \n\t"
    "sub.s     %[ak21],     %[yi],      %[ak21]               \n\t"
    "sub.s     $f2,         $f2,        $f1                   \n\t"
    "sub.s     $f3,         $f0,        $f3                   \n\t"
    "add.s     $f1,         $f8,        $f1                   \n\t"
    "sub.s     $f0,         $f0,        $f7                   \n\t"
    "swc1      %[aj20],     8(%[a2])                          \n\t"
    "swc1      %[aj21],     12(%[a2])                         \n\t"
    "swc1      %[ak20],     504(%[a1])                        \n\t"
    "swc1      %[ak21],     508(%[a1])                        \n\t"
    "swc1      $f2,         16(%[a2])                         \n\t"
    "swc1      $f3,         20(%[a2])                         \n\t"
    "swc1      $f1,         496(%[a1])                        \n\t"
    "swc1      $f0,         500(%[a1])                        \n\t"
    "addiu     %[a2],       %[a2],      16                    \n\t"
    "bne       %[c2],       %[c],       1b                    \n\t"
    " addiu    %[a1],       %[a1],      -16                   \n\t"
    ".set      pop                                            \n\t"
    : [a] "+r" (a), [c] "+r" (c), [a1] "+r" (a1), [a2] "+r" (a2),
      [c1] "+r" (c1), [c2] "+r" (c2), [wkr] "=&f" (wkr), [wki] "=&f" (wki),
      [xr] "=&f" (xr), [xi] "=&f" (xi), [yr] "=&f" (yr), [yi] "=&f" (yi),
      [aj20] "=&f" (aj20), [aj21] "=&f" (aj21), [ak20] "=&f" (ak20),
      [ak21] "=&f" (ak21), [ck1] "=&f" (ck1)
    : [temp] "f" (temp)
    : "memory", "$f0", "$f1", "$f2", "$f3", "$f4", "$f5", "$f6", "$f7", "$f8"
  );
}

void aec_rdft_init_mips(void) {
  cft1st_128 = cft1st_128_mips;
  cftmdl_128 = cftmdl_128_mips;
  rftfsub_128 = rftfsub_128_mips;
  rftbsub_128 = rftbsub_128_mips;
  cftfsub_128 = cftfsub_128_mips;
  cftbsub_128 = cftbsub_128_mips;
  bitrv2_128 = bitrv2_128_mips;
}
