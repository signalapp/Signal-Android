/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
// MacAsyncSocket is a kind of AsyncSocket. It only creates sockets
// of the TCP type, and does not (yet) support listen and accept. It works
// asynchronously, which means that users of this socket should connect to
// the various events declared in asyncsocket.h to receive notifications about
// this socket.

#ifndef WEBRTC_BASE_MACASYNCSOCKET_H__
#define WEBRTC_BASE_MACASYNCSOCKET_H__

#include <CoreFoundation/CoreFoundation.h>

#include "webrtc/base/asyncsocket.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/nethelpers.h"

namespace rtc {

class MacBaseSocketServer;

class MacAsyncSocket : public AsyncSocket, public sigslot::has_slots<> {
 public:
  MacAsyncSocket(MacBaseSocketServer* ss, int family);
  ~MacAsyncSocket() override;

  bool valid() const { return source_ != NULL; }

  // Socket interface
  SocketAddress GetLocalAddress() const override;
  SocketAddress GetRemoteAddress() const override;
  int Bind(const SocketAddress& addr) override;
  int Connect(const SocketAddress& addr) override;
  int Send(const void* buffer, size_t length) override;
  int SendTo(const void* buffer,
             size_t length,
             const SocketAddress& addr) override;
  int Recv(void* buffer, size_t length, int64_t* timestamp) override;
  int RecvFrom(void* buffer,
               size_t length,
               SocketAddress* out_addr,
               int64_t* timestamp) override;
  int Listen(int backlog) override;
  MacAsyncSocket* Accept(SocketAddress* out_addr) override;
  int Close() override;
  int GetError() const override;
  void SetError(int error) override;
  ConnState GetState() const override;
  int EstimateMTU(uint16_t* mtu) override;
  int GetOption(Option opt, int* value) override;
  int SetOption(Option opt, int value) override;

  // For the MacBaseSocketServer to disable callbacks when process_io is false.
  void EnableCallbacks();
  void DisableCallbacks();

 protected:
  void OnResolveResult(SignalThread* thread);
  int DoConnect(const SocketAddress& addr);

 private:
  // Creates an async socket from an existing bsd socket
  MacAsyncSocket(MacBaseSocketServer* ss, int family, int native_socket);

   // Attaches the socket to the CFRunloop and sets the wrapped bsd socket
  // to async mode
  void Initialize(int family);

  // Translate the SocketAddress into a CFDataRef to pass to CF socket
  // functions. Caller must call CFRelease on the result when done.
  static CFDataRef CopyCFAddress(const SocketAddress& address);

  // Callback for the underlying CFSocketRef.
  static void MacAsyncSocketCallBack(CFSocketRef s,
                                     CFSocketCallBackType callbackType,
                                     CFDataRef address,
                                     const void* data,
                                     void* info);

  MacBaseSocketServer* ss_;
  CFSocketRef socket_;
  int native_socket_;
  CFRunLoopSourceRef source_;
  int current_callbacks_;
  bool disabled_;
  int error_;
  ConnState state_;
  AsyncResolver* resolver_;

  RTC_DISALLOW_COPY_AND_ASSIGN(MacAsyncSocket);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_MACASYNCSOCKET_H__
