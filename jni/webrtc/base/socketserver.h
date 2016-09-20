/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_SOCKETSERVER_H_
#define WEBRTC_BASE_SOCKETSERVER_H_

#include <memory>
#include "webrtc/base/socketfactory.h"

namespace rtc {

class MessageQueue;
class NetworkBinderInterface;

// Provides the ability to wait for activity on a set of sockets.  The Thread
// class provides a nice wrapper on a socket server.
//
// The server is also a socket factory.  The sockets it creates will be
// notified of asynchronous I/O from this server's Wait method.
class SocketServer : public SocketFactory {
 public:
  static const int kForever = -1;

  static std::unique_ptr<SocketServer> CreateDefault();
  // When the socket server is installed into a Thread, this function is
  // called to allow the socket server to use the thread's message queue for
  // any messaging that it might need to perform.
  virtual void SetMessageQueue(MessageQueue* queue) {}

  // Sleeps until:
  //  1) cms milliseconds have elapsed (unless cms == kForever)
  //  2) WakeUp() is called
  // While sleeping, I/O is performed if process_io is true.
  virtual bool Wait(int cms, bool process_io) = 0;

  // Causes the current wait (if one is in progress) to wake up.
  virtual void WakeUp() = 0;

  // A network binder will bind the created sockets to a network.
  // It is only used in PhysicalSocketServer.
  void set_network_binder(NetworkBinderInterface* binder) {
    network_binder_ = binder;
  }
  NetworkBinderInterface* network_binder() const { return network_binder_; }

 private:
  NetworkBinderInterface* network_binder_ = nullptr;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_SOCKETSERVER_H_
