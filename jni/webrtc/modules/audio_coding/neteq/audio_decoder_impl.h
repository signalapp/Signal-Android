/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_AUDIO_DECODER_IMPL_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_AUDIO_DECODER_IMPL_H_

#include <assert.h>

#include "webrtc/engine_configurations.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/codecs/audio_decoder.h"
#ifdef WEBRTC_CODEC_G722
#include "webrtc/modules/audio_coding/codecs/g722/g722_interface.h"
#endif
#include "webrtc/modules/audio_coding/acm2/rent_a_codec.h"
#include "webrtc/typedefs.h"

namespace webrtc {

using NetEqDecoder = acm2::RentACodec::NetEqDecoder;

// Returns true if |codec_type| is supported.
bool CodecSupported(NetEqDecoder codec_type);

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_AUDIO_DECODER_IMPL_H_
