/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_COMMON_AUDIO_FFT4G_H_
#define WEBRTC_COMMON_AUDIO_FFT4G_H_

#if defined(__cplusplus)
extern "C" {
#endif

// Refer to fft4g.c for documentation.
void WebRtc_rdft(size_t n, int isgn, float *a, size_t *ip, float *w);

#if defined(__cplusplus)
}
#endif

#endif  // WEBRTC_COMMON_AUDIO_FFT4G_H_
