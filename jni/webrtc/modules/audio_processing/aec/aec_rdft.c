/*
 * http://www.kurims.kyoto-u.ac.jp/~ooura/fft.html
 * Copyright Takuya OOURA, 1996-2001
 *
 * You may use, copy, modify and distribute this code for any purpose (include
 * commercial use) and without fee. Please refer to this package when you modify
 * this code.
 *
 * Changes by the WebRTC authors:
 *    - Trivial type modifications.
 *    - Minimal code subset to do rdft of length 128.
 *    - Optimizations because of known length.
 *
 *  All changes are covered by the WebRTC license and IP grant:
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_processing/aec/aec_rdft.h"

#include <math.h>

#include "webrtc/system_wrappers/interface/cpu_features_wrapper.h"
#include "webrtc/typedefs.h"

// constants shared by all paths (C, SSE2).
float rdft_w[64];
// constants used by the C path.
float rdft_wk3ri_first[32];
float rdft_wk3ri_second[32];
// constants used by SSE2 but initialized in C path.
ALIGN16_BEG float ALIGN16_END rdft_wk1r[32];
ALIGN16_BEG float ALIGN16_END rdft_wk2r[32];
ALIGN16_BEG float ALIGN16_END rdft_wk3r[32];
ALIGN16_BEG float ALIGN16_END rdft_wk1i[32];
ALIGN16_BEG float ALIGN16_END rdft_wk2i[32];
ALIGN16_BEG float ALIGN16_END rdft_wk3i[32];
ALIGN16_BEG float ALIGN16_END cftmdl_wk1r[4];

static int ip[16];

static void bitrv2_32(int* ip, float* a) {
  const int n = 32;
  int j, j1, k, k1, m, m2;
  float xr, xi, yr, yi;

  ip[0] = 0;
  {
    int l = n;
    m = 1;
    while ((m << 3) < l) {
      l >>= 1;
      for (j = 0; j < m; j++) {
        ip[m + j] = ip[j] + l;
      }
      m <<= 1;
    }
  }
  m2 = 2 * m;
  for (k = 0; k < m; k++) {
    for (j = 0; j < k; j++) {
      j1 = 2 * j + ip[k];
      k1 = 2 * k + ip[j];
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 += m2;
      k1 += 2 * m2;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 += m2;
      k1 -= m2;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
      j1 += m2;
      k1 += 2 * m2;
      xr = a[j1];
      xi = a[j1 + 1];
      yr = a[k1];
      yi = a[k1 + 1];
      a[j1] = yr;
      a[j1 + 1] = yi;
      a[k1] = xr;
      a[k1 + 1] = xi;
    }
    j1 = 2 * k + m2 + ip[k];
    k1 = j1 + m2;
    xr = a[j1];
    xi = a[j1 + 1];
    yr = a[k1];
    yi = a[k1 + 1];
    a[j1] = yr;
    a[j1 + 1] = yi;
    a[k1] = xr;
    a[k1 + 1] = xi;
  }
}

static void bitrv2_128_C(float* a) {
  /*
      Following things have been attempted but are no faster:
      (a) Storing the swap indexes in a LUT (index calculations are done
          for 'free' while waiting on memory/L1).
      (b) Consolidate the load/store of two consecutive floats by a 64 bit
          integer (execution is memory/L1 bound).
      (c) Do a mix of floats and 64 bit integer to maximize register
          utilization (execution is memory/L1 bound).
      (d) Replacing ip[i] by ((k<<31)>>25) + ((k >> 1)<<5).
      (e) Hard-coding of the offsets to completely eliminates index
          calculations.
  */

  unsigned int j, j1, k, k1;
  float xr, xi, yr, yi;

  static const int ip[4] = {0, 64, 32, 96};
  for (k = 0; k < 4; k++) {
    for (j = 0; j < k; j++) {
      j1 = 2 * j + ip[k];
      k1 = 2 * k + ip[j];
      xr = a[j1 + 0];
      xi = a[j1 + 1];
      yr = a[k1 + 0];
      yi = a[k1 + 1];
      a[j1 + 0] = yr;
      a[j1 + 1] = yi;
      a[k1 + 0] = xr;
      a[k1 + 1] = xi;
      j1 += 8;
      k1 += 16;
      xr = a[j1 + 0];
      xi = a[j1 + 1];
      yr = a[k1 + 0];
      yi = a[k1 + 1];
      a[j1 + 0] = yr;
      a[j1 + 1] = yi;
      a[k1 + 0] = xr;
      a[k1 + 1] = xi;
      j1 += 8;
      k1 -= 8;
      xr = a[j1 + 0];
      xi = a[j1 + 1];
      yr = a[k1 + 0];
      yi = a[k1 + 1];
      a[j1 + 0] = yr;
      a[j1 + 1] = yi;
      a[k1 + 0] = xr;
      a[k1 + 1] = xi;
      j1 += 8;
      k1 += 16;
      xr = a[j1 + 0];
      xi = a[j1 + 1];
      yr = a[k1 + 0];
      yi = a[k1 + 1];
      a[j1 + 0] = yr;
      a[j1 + 1] = yi;
      a[k1 + 0] = xr;
      a[k1 + 1] = xi;
    }
    j1 = 2 * k + 8 + ip[k];
    k1 = j1 + 8;
    xr = a[j1 + 0];
    xi = a[j1 + 1];
    yr = a[k1 + 0];
    yi = a[k1 + 1];
    a[j1 + 0] = yr;
    a[j1 + 1] = yi;
    a[k1 + 0] = xr;
    a[k1 + 1] = xi;
  }
}

static void makewt_32(void) {
  const int nw = 32;
  int j, nwh;
  float delta, x, y;

  ip[0] = nw;
  ip[1] = 1;
  nwh = nw >> 1;
  delta = atanf(1.0f) / nwh;
  rdft_w[0] = 1;
  rdft_w[1] = 0;
  rdft_w[nwh] = cosf(delta * nwh);
  rdft_w[nwh + 1] = rdft_w[nwh];
  for (j = 2; j < nwh; j += 2) {
    x = cosf(delta * j);
    y = sinf(delta * j);
    rdft_w[j] = x;
    rdft_w[j + 1] = y;
    rdft_w[nw - j] = y;
    rdft_w[nw - j + 1] = x;
  }
  bitrv2_32(ip + 2, rdft_w);

  // pre-calculate constants used by cft1st_128 and cftmdl_128...
  cftmdl_wk1r[0] = rdft_w[2];
  cftmdl_wk1r[1] = rdft_w[2];
  cftmdl_wk1r[2] = rdft_w[2];
  cftmdl_wk1r[3] = -rdft_w[2];
  {
    int k1;

    for (k1 = 0, j = 0; j < 128; j += 16, k1 += 2) {
      const int k2 = 2 * k1;
      const float wk2r = rdft_w[k1 + 0];
      const float wk2i = rdft_w[k1 + 1];
      float wk1r, wk1i;
      // ... scalar version.
      wk1r = rdft_w[k2 + 0];
      wk1i = rdft_w[k2 + 1];
      rdft_wk3ri_first[k1 + 0] = wk1r - 2 * wk2i * wk1i;
      rdft_wk3ri_first[k1 + 1] = 2 * wk2i * wk1r - wk1i;
      wk1r = rdft_w[k2 + 2];
      wk1i = rdft_w[k2 + 3];
      rdft_wk3ri_second[k1 + 0] = wk1r - 2 * wk2r * wk1i;
      rdft_wk3ri_second[k1 + 1] = 2 * wk2r * wk1r - wk1i;
      // ... vector version.
      rdft_wk1r[k2 + 0] = rdft_w[k2 + 0];
      rdft_wk1r[k2 + 1] = rdft_w[k2 + 0];
      rdft_wk1r[k2 + 2] = rdft_w[k2 + 2];
      rdft_wk1r[k2 + 3] = rdft_w[k2 + 2];
      rdft_wk2r[k2 + 0] = rdft_w[k1 + 0];
      rdft_wk2r[k2 + 1] = rdft_w[k1 + 0];
      rdft_wk2r[k2 + 2] = -rdft_w[k1 + 1];
      rdft_wk2r[k2 + 3] = -rdft_w[k1 + 1];
      rdft_wk3r[k2 + 0] = rdft_wk3ri_first[k1 + 0];
      rdft_wk3r[k2 + 1] = rdft_wk3ri_first[k1 + 0];
      rdft_wk3r[k2 + 2] = rdft_wk3ri_second[k1 + 0];
      rdft_wk3r[k2 + 3] = rdft_wk3ri_second[k1 + 0];
      rdft_wk1i[k2 + 0] = -rdft_w[k2 + 1];
      rdft_wk1i[k2 + 1] = rdft_w[k2 + 1];
      rdft_wk1i[k2 + 2] = -rdft_w[k2 + 3];
      rdft_wk1i[k2 + 3] = rdft_w[k2 + 3];
      rdft_wk2i[k2 + 0] = -rdft_w[k1 + 1];
      rdft_wk2i[k2 + 1] = rdft_w[k1 + 1];
      rdft_wk2i[k2 + 2] = -rdft_w[k1 + 0];
      rdft_wk2i[k2 + 3] = rdft_w[k1 + 0];
      rdft_wk3i[k2 + 0] = -rdft_wk3ri_first[k1 + 1];
      rdft_wk3i[k2 + 1] = rdft_wk3ri_first[k1 + 1];
      rdft_wk3i[k2 + 2] = -rdft_wk3ri_second[k1 + 1];
      rdft_wk3i[k2 + 3] = rdft_wk3ri_second[k1 + 1];
    }
  }
}

static void makect_32(void) {
  float* c = rdft_w + 32;
  const int nc = 32;
  int j, nch;
  float delta;

  ip[1] = nc;
  nch = nc >> 1;
  delta = atanf(1.0f) / nch;
  c[0] = cosf(delta * nch);
  c[nch] = 0.5f * c[0];
  for (j = 1; j < nch; j++) {
    c[j] = 0.5f * cosf(delta * j);
    c[nc - j] = 0.5f * sinf(delta * j);
  }
}

static void cft1st_128_C(float* a) {
  const int n = 128;
  int j, k1, k2;
  float wk1r, wk1i, wk2r, wk2i, wk3r, wk3i;
  float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

  x0r = a[0] + a[2];
  x0i = a[1] + a[3];
  x1r = a[0] - a[2];
  x1i = a[1] - a[3];
  x2r = a[4] + a[6];
  x2i = a[5] + a[7];
  x3r = a[4] - a[6];
  x3i = a[5] - a[7];
  a[0] = x0r + x2r;
  a[1] = x0i + x2i;
  a[4] = x0r - x2r;
  a[5] = x0i - x2i;
  a[2] = x1r - x3i;
  a[3] = x1i + x3r;
  a[6] = x1r + x3i;
  a[7] = x1i - x3r;
  wk1r = rdft_w[2];
  x0r = a[8] + a[10];
  x0i = a[9] + a[11];
  x1r = a[8] - a[10];
  x1i = a[9] - a[11];
  x2r = a[12] + a[14];
  x2i = a[13] + a[15];
  x3r = a[12] - a[14];
  x3i = a[13] - a[15];
  a[8] = x0r + x2r;
  a[9] = x0i + x2i;
  a[12] = x2i - x0i;
  a[13] = x0r - x2r;
  x0r = x1r - x3i;
  x0i = x1i + x3r;
  a[10] = wk1r * (x0r - x0i);
  a[11] = wk1r * (x0r + x0i);
  x0r = x3i + x1r;
  x0i = x3r - x1i;
  a[14] = wk1r * (x0i - x0r);
  a[15] = wk1r * (x0i + x0r);
  k1 = 0;
  for (j = 16; j < n; j += 16) {
    k1 += 2;
    k2 = 2 * k1;
    wk2r = rdft_w[k1 + 0];
    wk2i = rdft_w[k1 + 1];
    wk1r = rdft_w[k2 + 0];
    wk1i = rdft_w[k2 + 1];
    wk3r = rdft_wk3ri_first[k1 + 0];
    wk3i = rdft_wk3ri_first[k1 + 1];
    x0r = a[j + 0] + a[j + 2];
    x0i = a[j + 1] + a[j + 3];
    x1r = a[j + 0] - a[j + 2];
    x1i = a[j + 1] - a[j + 3];
    x2r = a[j + 4] + a[j + 6];
    x2i = a[j + 5] + a[j + 7];
    x3r = a[j + 4] - a[j + 6];
    x3i = a[j + 5] - a[j + 7];
    a[j + 0] = x0r + x2r;
    a[j + 1] = x0i + x2i;
    x0r -= x2r;
    x0i -= x2i;
    a[j + 4] = wk2r * x0r - wk2i * x0i;
    a[j + 5] = wk2r * x0i + wk2i * x0r;
    x0r = x1r - x3i;
    x0i = x1i + x3r;
    a[j + 2] = wk1r * x0r - wk1i * x0i;
    a[j + 3] = wk1r * x0i + wk1i * x0r;
    x0r = x1r + x3i;
    x0i = x1i - x3r;
    a[j + 6] = wk3r * x0r - wk3i * x0i;
    a[j + 7] = wk3r * x0i + wk3i * x0r;
    wk1r = rdft_w[k2 + 2];
    wk1i = rdft_w[k2 + 3];
    wk3r = rdft_wk3ri_second[k1 + 0];
    wk3i = rdft_wk3ri_second[k1 + 1];
    x0r = a[j + 8] + a[j + 10];
    x0i = a[j + 9] + a[j + 11];
    x1r = a[j + 8] - a[j + 10];
    x1i = a[j + 9] - a[j + 11];
    x2r = a[j + 12] + a[j + 14];
    x2i = a[j + 13] + a[j + 15];
    x3r = a[j + 12] - a[j + 14];
    x3i = a[j + 13] - a[j + 15];
    a[j + 8] = x0r + x2r;
    a[j + 9] = x0i + x2i;
    x0r -= x2r;
    x0i -= x2i;
    a[j + 12] = -wk2i * x0r - wk2r * x0i;
    a[j + 13] = -wk2i * x0i + wk2r * x0r;
    x0r = x1r - x3i;
    x0i = x1i + x3r;
    a[j + 10] = wk1r * x0r - wk1i * x0i;
    a[j + 11] = wk1r * x0i + wk1i * x0r;
    x0r = x1r + x3i;
    x0i = x1i - x3r;
    a[j + 14] = wk3r * x0r - wk3i * x0i;
    a[j + 15] = wk3r * x0i + wk3i * x0r;
  }
}

static void cftmdl_128_C(float* a) {
  const int l = 8;
  const int n = 128;
  const int m = 32;
  int j0, j1, j2, j3, k, k1, k2, m2;
  float wk1r, wk1i, wk2r, wk2i, wk3r, wk3i;
  float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

  for (j0 = 0; j0 < l; j0 += 2) {
    j1 = j0 + 8;
    j2 = j0 + 16;
    j3 = j0 + 24;
    x0r = a[j0 + 0] + a[j1 + 0];
    x0i = a[j0 + 1] + a[j1 + 1];
    x1r = a[j0 + 0] - a[j1 + 0];
    x1i = a[j0 + 1] - a[j1 + 1];
    x2r = a[j2 + 0] + a[j3 + 0];
    x2i = a[j2 + 1] + a[j3 + 1];
    x3r = a[j2 + 0] - a[j3 + 0];
    x3i = a[j2 + 1] - a[j3 + 1];
    a[j0 + 0] = x0r + x2r;
    a[j0 + 1] = x0i + x2i;
    a[j2 + 0] = x0r - x2r;
    a[j2 + 1] = x0i - x2i;
    a[j1 + 0] = x1r - x3i;
    a[j1 + 1] = x1i + x3r;
    a[j3 + 0] = x1r + x3i;
    a[j3 + 1] = x1i - x3r;
  }
  wk1r = rdft_w[2];
  for (j0 = m; j0 < l + m; j0 += 2) {
    j1 = j0 + 8;
    j2 = j0 + 16;
    j3 = j0 + 24;
    x0r = a[j0 + 0] + a[j1 + 0];
    x0i = a[j0 + 1] + a[j1 + 1];
    x1r = a[j0 + 0] - a[j1 + 0];
    x1i = a[j0 + 1] - a[j1 + 1];
    x2r = a[j2 + 0] + a[j3 + 0];
    x2i = a[j2 + 1] + a[j3 + 1];
    x3r = a[j2 + 0] - a[j3 + 0];
    x3i = a[j2 + 1] - a[j3 + 1];
    a[j0 + 0] = x0r + x2r;
    a[j0 + 1] = x0i + x2i;
    a[j2 + 0] = x2i - x0i;
    a[j2 + 1] = x0r - x2r;
    x0r = x1r - x3i;
    x0i = x1i + x3r;
    a[j1 + 0] = wk1r * (x0r - x0i);
    a[j1 + 1] = wk1r * (x0r + x0i);
    x0r = x3i + x1r;
    x0i = x3r - x1i;
    a[j3 + 0] = wk1r * (x0i - x0r);
    a[j3 + 1] = wk1r * (x0i + x0r);
  }
  k1 = 0;
  m2 = 2 * m;
  for (k = m2; k < n; k += m2) {
    k1 += 2;
    k2 = 2 * k1;
    wk2r = rdft_w[k1 + 0];
    wk2i = rdft_w[k1 + 1];
    wk1r = rdft_w[k2 + 0];
    wk1i = rdft_w[k2 + 1];
    wk3r = rdft_wk3ri_first[k1 + 0];
    wk3i = rdft_wk3ri_first[k1 + 1];
    for (j0 = k; j0 < l + k; j0 += 2) {
      j1 = j0 + 8;
      j2 = j0 + 16;
      j3 = j0 + 24;
      x0r = a[j0 + 0] + a[j1 + 0];
      x0i = a[j0 + 1] + a[j1 + 1];
      x1r = a[j0 + 0] - a[j1 + 0];
      x1i = a[j0 + 1] - a[j1 + 1];
      x2r = a[j2 + 0] + a[j3 + 0];
      x2i = a[j2 + 1] + a[j3 + 1];
      x3r = a[j2 + 0] - a[j3 + 0];
      x3i = a[j2 + 1] - a[j3 + 1];
      a[j0 + 0] = x0r + x2r;
      a[j0 + 1] = x0i + x2i;
      x0r -= x2r;
      x0i -= x2i;
      a[j2 + 0] = wk2r * x0r - wk2i * x0i;
      a[j2 + 1] = wk2r * x0i + wk2i * x0r;
      x0r = x1r - x3i;
      x0i = x1i + x3r;
      a[j1 + 0] = wk1r * x0r - wk1i * x0i;
      a[j1 + 1] = wk1r * x0i + wk1i * x0r;
      x0r = x1r + x3i;
      x0i = x1i - x3r;
      a[j3 + 0] = wk3r * x0r - wk3i * x0i;
      a[j3 + 1] = wk3r * x0i + wk3i * x0r;
    }
    wk1r = rdft_w[k2 + 2];
    wk1i = rdft_w[k2 + 3];
    wk3r = rdft_wk3ri_second[k1 + 0];
    wk3i = rdft_wk3ri_second[k1 + 1];
    for (j0 = k + m; j0 < l + (k + m); j0 += 2) {
      j1 = j0 + 8;
      j2 = j0 + 16;
      j3 = j0 + 24;
      x0r = a[j0 + 0] + a[j1 + 0];
      x0i = a[j0 + 1] + a[j1 + 1];
      x1r = a[j0 + 0] - a[j1 + 0];
      x1i = a[j0 + 1] - a[j1 + 1];
      x2r = a[j2 + 0] + a[j3 + 0];
      x2i = a[j2 + 1] + a[j3 + 1];
      x3r = a[j2 + 0] - a[j3 + 0];
      x3i = a[j2 + 1] - a[j3 + 1];
      a[j0 + 0] = x0r + x2r;
      a[j0 + 1] = x0i + x2i;
      x0r -= x2r;
      x0i -= x2i;
      a[j2 + 0] = -wk2i * x0r - wk2r * x0i;
      a[j2 + 1] = -wk2i * x0i + wk2r * x0r;
      x0r = x1r - x3i;
      x0i = x1i + x3r;
      a[j1 + 0] = wk1r * x0r - wk1i * x0i;
      a[j1 + 1] = wk1r * x0i + wk1i * x0r;
      x0r = x1r + x3i;
      x0i = x1i - x3r;
      a[j3 + 0] = wk3r * x0r - wk3i * x0i;
      a[j3 + 1] = wk3r * x0i + wk3i * x0r;
    }
  }
}

static void cftfsub_128_C(float* a) {
  int j, j1, j2, j3, l;
  float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

  cft1st_128(a);
  cftmdl_128(a);
  l = 32;
  for (j = 0; j < l; j += 2) {
    j1 = j + l;
    j2 = j1 + l;
    j3 = j2 + l;
    x0r = a[j] + a[j1];
    x0i = a[j + 1] + a[j1 + 1];
    x1r = a[j] - a[j1];
    x1i = a[j + 1] - a[j1 + 1];
    x2r = a[j2] + a[j3];
    x2i = a[j2 + 1] + a[j3 + 1];
    x3r = a[j2] - a[j3];
    x3i = a[j2 + 1] - a[j3 + 1];
    a[j] = x0r + x2r;
    a[j + 1] = x0i + x2i;
    a[j2] = x0r - x2r;
    a[j2 + 1] = x0i - x2i;
    a[j1] = x1r - x3i;
    a[j1 + 1] = x1i + x3r;
    a[j3] = x1r + x3i;
    a[j3 + 1] = x1i - x3r;
  }
}

static void cftbsub_128_C(float* a) {
  int j, j1, j2, j3, l;
  float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

  cft1st_128(a);
  cftmdl_128(a);
  l = 32;

  for (j = 0; j < l; j += 2) {
    j1 = j + l;
    j2 = j1 + l;
    j3 = j2 + l;
    x0r = a[j] + a[j1];
    x0i = -a[j + 1] - a[j1 + 1];
    x1r = a[j] - a[j1];
    x1i = -a[j + 1] + a[j1 + 1];
    x2r = a[j2] + a[j3];
    x2i = a[j2 + 1] + a[j3 + 1];
    x3r = a[j2] - a[j3];
    x3i = a[j2 + 1] - a[j3 + 1];
    a[j] = x0r + x2r;
    a[j + 1] = x0i - x2i;
    a[j2] = x0r - x2r;
    a[j2 + 1] = x0i + x2i;
    a[j1] = x1r - x3i;
    a[j1 + 1] = x1i - x3r;
    a[j3] = x1r + x3i;
    a[j3 + 1] = x1i + x3r;
  }
}

static void rftfsub_128_C(float* a) {
  const float* c = rdft_w + 32;
  int j1, j2, k1, k2;
  float wkr, wki, xr, xi, yr, yi;

  for (j1 = 1, j2 = 2; j2 < 64; j1 += 1, j2 += 2) {
    k2 = 128 - j2;
    k1 = 32 - j1;
    wkr = 0.5f - c[k1];
    wki = c[j1];
    xr = a[j2 + 0] - a[k2 + 0];
    xi = a[j2 + 1] + a[k2 + 1];
    yr = wkr * xr - wki * xi;
    yi = wkr * xi + wki * xr;
    a[j2 + 0] -= yr;
    a[j2 + 1] -= yi;
    a[k2 + 0] += yr;
    a[k2 + 1] -= yi;
  }
}

static void rftbsub_128_C(float* a) {
  const float* c = rdft_w + 32;
  int j1, j2, k1, k2;
  float wkr, wki, xr, xi, yr, yi;

  a[1] = -a[1];
  for (j1 = 1, j2 = 2; j2 < 64; j1 += 1, j2 += 2) {
    k2 = 128 - j2;
    k1 = 32 - j1;
    wkr = 0.5f - c[k1];
    wki = c[j1];
    xr = a[j2 + 0] - a[k2 + 0];
    xi = a[j2 + 1] + a[k2 + 1];
    yr = wkr * xr + wki * xi;
    yi = wkr * xi - wki * xr;
    a[j2 + 0] = a[j2 + 0] - yr;
    a[j2 + 1] = yi - a[j2 + 1];
    a[k2 + 0] = yr + a[k2 + 0];
    a[k2 + 1] = yi - a[k2 + 1];
  }
  a[65] = -a[65];
}

void aec_rdft_forward_128(float* a) {
  float xi;
  bitrv2_128(a);
  cftfsub_128(a);
  rftfsub_128(a);
  xi = a[0] - a[1];
  a[0] += a[1];
  a[1] = xi;
}

void aec_rdft_inverse_128(float* a) {
  a[1] = 0.5f * (a[0] - a[1]);
  a[0] -= a[1];
  rftbsub_128(a);
  bitrv2_128(a);
  cftbsub_128(a);
}

// code path selection
rft_sub_128_t cft1st_128;
rft_sub_128_t cftmdl_128;
rft_sub_128_t rftfsub_128;
rft_sub_128_t rftbsub_128;
rft_sub_128_t cftfsub_128;
rft_sub_128_t cftbsub_128;
rft_sub_128_t bitrv2_128;

void aec_rdft_init(void) {
  cft1st_128 = cft1st_128_C;
  cftmdl_128 = cftmdl_128_C;
  rftfsub_128 = rftfsub_128_C;
  rftbsub_128 = rftbsub_128_C;
  cftfsub_128 = cftfsub_128_C;
  cftbsub_128 = cftbsub_128_C;
  bitrv2_128 = bitrv2_128_C;
#if defined(WEBRTC_ARCH_X86_FAMILY)
  if (WebRtc_GetCPUInfo(kSSE2)) {
    aec_rdft_init_sse2();
  }
#endif
#if defined(MIPS_FPU_LE)
  aec_rdft_init_mips();
#endif
  // init library constants.
  makewt_32();
  makect_32();
}
