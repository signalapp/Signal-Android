/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_INCLUDE_AUDIO_CODING_MODULE_TYPEDEFS_H_
#define WEBRTC_MODULES_AUDIO_CODING_INCLUDE_AUDIO_CODING_MODULE_TYPEDEFS_H_

#include <map>

#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/typedefs.h"

namespace webrtc {

///////////////////////////////////////////////////////////////////////////
// enum ACMVADMode
// An enumerator for aggressiveness of VAD
// -VADNormal                : least aggressive mode.
// -VADLowBitrate            : more aggressive than "VADNormal" to save on
//                             bit-rate.
// -VADAggr                  : an aggressive mode.
// -VADVeryAggr              : the most agressive mode.
//
enum ACMVADMode {
  VADNormal = 0,
  VADLowBitrate = 1,
  VADAggr = 2,
  VADVeryAggr = 3
};

///////////////////////////////////////////////////////////////////////////
//
// Enumeration of Opus mode for intended application.
//
// kVoip              : optimized for voice signals.
// kAudio             : optimized for non-voice signals like music.
//
enum OpusApplicationMode {
 kVoip = 0,
 kAudio = 1,
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_INCLUDE_AUDIO_CODING_MODULE_TYPEDEFS_H_
