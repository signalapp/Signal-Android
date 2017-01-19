/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_CODECS_OPUS_OPUS_INST_H_
#define WEBRTC_MODULES_AUDIO_CODING_CODECS_OPUS_OPUS_INST_H_

#include "opus.h"

struct WebRtcOpusEncInst {
  OpusEncoder* encoder;
};

struct WebRtcOpusDecInst {
  OpusDecoder* decoder_left;
  OpusDecoder* decoder_right;
  int prev_decoded_samples;
  int channels;
};


#endif  // WEBRTC_MODULES_AUDIO_CODING_CODECS_OPUS_OPUS_INST_H_
