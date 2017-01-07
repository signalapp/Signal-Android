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

#include "webrtc/modules/audio_processing/aec/aec_common.h"

// These intrinsics were unavailable before VS 2008.
// TODO(andrew): move to a common file.
#if defined(_MSC_VER) && _MSC_VER < 1500
#include <emmintrin.h>
static __inline __m128 _mm_castsi128_ps(__m128i a) { return *(__m128*)&a; }
static __inline __m128i _mm_castps_si128(__m128 a) { return *(__m128i*)&a; }
#endif

// Constants shared by all paths (C, SSE2, NEON).
extern const float rdft_w[64];
// Constants used by the C path.
extern const float rdft_wk3ri_first[16];
extern const float rdft_wk3ri_second[16];
// Constants used by SSE2 and NEON but initialized in the C path.
extern ALIGN16_BEG const float ALIGN16_END rdft_wk1r[32];
extern ALIGN16_BEG const float ALIGN16_END rdft_wk2r[32];
extern ALIGN16_BEG const float ALIGN16_END rdft_wk3r[32];
extern ALIGN16_BEG const float ALIGN16_END rdft_wk1i[32];
extern ALIGN16_BEG const float ALIGN16_END rdft_wk2i[32];
extern ALIGN16_BEG const float ALIGN16_END rdft_wk3i[32];
extern ALIGN16_BEG const float ALIGN16_END cftmdl_wk1r[4];

// code path selection function pointers
typedef void (*RftSub128)(float* a);
extern RftSub128 rftfsub_128;
extern RftSub128 rftbsub_128;
extern RftSub128 cft1st_128;
extern RftSub128 cftmdl_128;
extern RftSub128 cftfsub_128;
extern RftSub128 cftbsub_128;
extern RftSub128 bitrv2_128;

// entry points
void aec_rdft_init(void);
void aec_rdft_init_sse2(void);
void aec_rdft_forward_128(float* a);
void aec_rdft_inverse_128(float* a);

#if defined(MIPS_FPU_LE)
void aec_rdft_init_mips(void);
#endif
#if defined(WEBRTC_HAS_NEON)
void aec_rdft_init_neon(void);
#endif

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_AEC_MAIN_SOURCE_AEC_RDFT_H_
