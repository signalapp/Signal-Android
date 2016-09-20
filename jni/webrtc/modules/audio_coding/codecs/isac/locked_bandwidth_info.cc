/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/codecs/isac/locked_bandwidth_info.h"

namespace webrtc {

LockedIsacBandwidthInfo::LockedIsacBandwidthInfo() : ref_count_(0) {
  bwinfo_.in_use = 0;
}

LockedIsacBandwidthInfo::~LockedIsacBandwidthInfo() = default;

}  // namespace webrtc
