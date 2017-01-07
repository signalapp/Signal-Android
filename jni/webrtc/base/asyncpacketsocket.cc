/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/asyncpacketsocket.h"

namespace rtc {

PacketTimeUpdateParams::PacketTimeUpdateParams()
    : rtp_sendtime_extension_id(-1),
      srtp_auth_tag_len(-1),
      srtp_packet_index(-1) {
}

PacketTimeUpdateParams::~PacketTimeUpdateParams() = default;

AsyncPacketSocket::AsyncPacketSocket() {
}

AsyncPacketSocket::~AsyncPacketSocket() {
}

};  // namespace rtc
