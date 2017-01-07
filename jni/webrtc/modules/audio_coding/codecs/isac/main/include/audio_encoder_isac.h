/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_INCLUDE_AUDIO_ENCODER_ISAC_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_INCLUDE_AUDIO_ENCODER_ISAC_H_

#include "webrtc/modules/audio_coding/codecs/isac/audio_encoder_isac_t.h"
#include "webrtc/modules/audio_coding/codecs/isac/main/source/isac_float_type.h"

namespace webrtc {

using AudioEncoderIsac = AudioEncoderIsacT<IsacFloat>;

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_ISAC_MAIN_INCLUDE_AUDIO_ENCODER_ISAC_H_
