/*
 *  Copyright 2009 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/win32socketinit.h"

#include "webrtc/base/win32.h"

namespace rtc {

// Please don't remove this function.
void EnsureWinsockInit() {
  // The default implementation uses a global initializer, so WSAStartup
  // happens at module load time.  Thus we don't need to do anything here.
  // The hook is provided so that a client that statically links with
  // libjingle can override it, to provide its own initialization.
}

#if defined(WEBRTC_WIN)
class WinsockInitializer {
 public:
  WinsockInitializer() {
    WSADATA wsaData;
    WORD wVersionRequested = MAKEWORD(1, 0);
    err_ = WSAStartup(wVersionRequested, &wsaData);
  }
  ~WinsockInitializer() {
    if (!err_)
      WSACleanup();
  }
  int error() {
    return err_;
  }
 private:
  int err_;
};
WinsockInitializer g_winsockinit;
#endif

}  // namespace rtc
