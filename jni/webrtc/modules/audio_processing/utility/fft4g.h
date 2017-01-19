/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_UTILITY_FFT4G_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_UTILITY_FFT4G_H_

void WebRtc_rdft(int, int, float *, int *, float *);
void WebRtc_cdft(int, int, float *, int *, float *);

#endif
