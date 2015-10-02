/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_AEC_MAIN_SOURCE_AEC_RDFT_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_AEC_MAIN_SOURCE_AEC_RDFT_H_

// These intrinsics were unavailable before VS 2008.
// TODO(andrew): move to a common file.
#if defined(_MSC_VER) && _MSC_VER < 1500
#include <emmintrin.h>
static __inline __m128 _mm_castsi128_ps(__m128i a) { return *(__m128*)&a; }
static __inline __m128i _mm_castps_si128(__m128 a) { return *(__m128i*)&a; }
#endif

#ifdef _MSC_VER /* visual c++ */
#define ALIGN16_BEG __declspec(align(16))
#define ALIGN16_END
#else /* gcc or icc */
#define ALIGN16_BEG
#define ALIGN16_END __attribute__((aligned(16)))
#endif

// constants shared by all paths (C, SSE2).
extern float rdft_w[64];
// constants used by the C path.
extern float rdft_wk3ri_first[32];
extern float rdft_wk3ri_second[32];
// constants used by SSE2 but initialized in C path.
extern ALIGN16_BEG float ALIGN16_END rdft_wk1r[32];
extern ALIGN16_BEG float ALIGN16_END rdft_wk2r[32];
extern ALIGN16_BEG float ALIGN16_END rdft_wk3r[32];
extern ALIGN16_BEG float ALIGN16_END rdft_wk1i[32];
extern ALIGN16_BEG float ALIGN16_END rdft_wk2i[32];
extern ALIGN16_BEG float ALIGN16_END rdft_wk3i[32];
extern ALIGN16_BEG float ALIGN16_END cftmdl_wk1r[4];

// code path selection function pointers
typedef void (*rft_sub_128_t)(float* a);
extern rft_sub_128_t rftfsub_128;
extern rft_sub_128_t rftbsub_128;
extern rft_sub_128_t cft1st_128;
extern rft_sub_128_t cftmdl_128;
extern rft_sub_128_t cftfsub_128;
extern rft_sub_128_t cftbsub_128;
extern rft_sub_128_t bitrv2_128;

// entry points
void aec_rdft_init(void);
void aec_rdft_init_sse2(void);
void aec_rdft_forward_128(float* a);
void aec_rdft_inverse_128(float* a);

#if defined(MIPS_FPU_LE)
void aec_rdft_init_mips(void);
#endif

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_AEC_MAIN_SOURCE_AEC_RDFT_H_
