/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/tools/packet_source.h"

namespace webrtc {
namespace test {

PacketSource::PacketSource() : use_ssrc_filter_(false), ssrc_(0) {}

PacketSource::~PacketSource() = default;

void PacketSource::FilterOutPayloadType(uint8_t payload_type) {
  filter_.set(payload_type, true);
}

void PacketSource::SelectSsrc(uint32_t ssrc) {
  use_ssrc_filter_ = true;
  ssrc_ = ssrc;
}

}  // namespace test
}  // namespace webrtc
