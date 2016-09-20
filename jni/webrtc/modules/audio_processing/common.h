/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_COMMON_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_COMMON_H_

#include <assert.h>

#include "webrtc/modules/audio_processing/include/audio_processing.h"

namespace webrtc {

static inline size_t ChannelsFromLayout(AudioProcessing::ChannelLayout layout) {
  switch (layout) {
    case AudioProcessing::kMono:
    case AudioProcessing::kMonoAndKeyboard:
      return 1;
    case AudioProcessing::kStereo:
    case AudioProcessing::kStereoAndKeyboard:
      return 2;
  }
  assert(false);
  return 0;
}

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_COMMON_H_
