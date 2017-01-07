/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_DEFINES_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_DEFINES_H_

namespace webrtc {

enum Operations {
  kNormal = 0,
  kMerge,
  kExpand,
  kAccelerate,
  kFastAccelerate,
  kPreemptiveExpand,
  kRfc3389Cng,
  kRfc3389CngNoPacket,
  kCodecInternalCng,
  kDtmf,
  kAlternativePlc,
  kAlternativePlcIncreaseTimestamp,
  kAudioRepetition,
  kAudioRepetitionIncreaseTimestamp,
  kUndefined = -1
};

enum Modes {
  kModeNormal = 0,
  kModeExpand,
  kModeMerge,
  kModeAccelerateSuccess,
  kModeAccelerateLowEnergy,
  kModeAccelerateFail,
  kModePreemptiveExpandSuccess,
  kModePreemptiveExpandLowEnergy,
  kModePreemptiveExpandFail,
  kModeRfc3389Cng,
  kModeCodecInternalCng,
  kModeDtmf,
  kModeError,
  kModeUndefined = -1
};

}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_DEFINES_H_
