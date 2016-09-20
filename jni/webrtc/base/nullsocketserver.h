/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_NULLSOCKETSERVER_H_
#define WEBRTC_BASE_NULLSOCKETSERVER_H_

#include "webrtc/base/event.h"
#include "webrtc/base/socketserver.h"

namespace rtc {

class NullSocketServer : public SocketServer {
 public:
  NullSocketServer();
  ~NullSocketServer() override;

  bool Wait(int cms, bool process_io) override;
  void WakeUp() override;

  Socket* CreateSocket(int type) override;
  Socket* CreateSocket(int family, int type) override;
  AsyncSocket* CreateAsyncSocket(int type) override;
  AsyncSocket* CreateAsyncSocket(int family, int type) override;

 private:
  Event event_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_NULLSOCKETSERVER_H_
