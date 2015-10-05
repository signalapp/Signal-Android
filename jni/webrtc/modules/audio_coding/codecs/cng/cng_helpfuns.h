/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_CNG_CNG_HELPFUNS_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_CNG_CNG_HELPFUNS_H_

#include "typedefs.h"

#ifdef __cplusplus
extern "C" {
#endif

void WebRtcCng_K2a16(int16_t* k, int useOrder, int16_t* a);

#ifdef __cplusplus
}
#endif

#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_CNG_CNG_HELPFUNS_H_
