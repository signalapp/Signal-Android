/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_ACM2_ACM_COMMON_DEFS_H_
#define WEBRTC_MODULES_AUDIO_CODING_ACM2_ACM_COMMON_DEFS_H_

#include "webrtc/engine_configurations.h"

// Checks for enabled codecs, we prevent enabling codecs which are not
// compatible.
#if ((defined WEBRTC_CODEC_ISAC) && (defined WEBRTC_CODEC_ISACFX))
#error iSAC and iSACFX codecs cannot be enabled at the same time
#endif

namespace webrtc {

// General codec specific defines
const int kIsacWbDefaultRate = 32000;
const int kIsacSwbDefaultRate = 56000;
const int kIsacPacSize480 = 480;
const int kIsacPacSize960 = 960;

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_ACM2_ACM_COMMON_DEFS_H_
