/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/checks.h"
#include "webrtc/base/nullsocketserver.h"

namespace rtc {

NullSocketServer::NullSocketServer() : event_(false, false) {}
NullSocketServer::~NullSocketServer() {}

bool NullSocketServer::Wait(int cms, bool process_io) {
  event_.Wait(cms);
  return true;
}

void NullSocketServer::WakeUp() {
  event_.Set();
}

rtc::Socket* NullSocketServer::CreateSocket(int /* type */) {
  RTC_NOTREACHED();
  return nullptr;
}

rtc::Socket* NullSocketServer::CreateSocket(int /* family */, int /* type */) {
  RTC_NOTREACHED();
  return nullptr;
}

rtc::AsyncSocket* NullSocketServer::CreateAsyncSocket(int /* type */) {
  RTC_NOTREACHED();
  return nullptr;
}

rtc::AsyncSocket* NullSocketServer::CreateAsyncSocket(int /* family */,
                                                      int /* type */) {
  RTC_NOTREACHED();
  return nullptr;
}

}  // namespace rtc
