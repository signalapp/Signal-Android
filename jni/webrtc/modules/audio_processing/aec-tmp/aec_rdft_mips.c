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

void aec_rdft_init_mips(void) {
  cftfsub_128 = cftfsub_128_mips;
  bitrv2_128 = bitrv2_128_mips;
}
