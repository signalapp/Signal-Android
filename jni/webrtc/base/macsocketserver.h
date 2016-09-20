/*
 *  Copyright 2007 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef WEBRTC_BASE_MACSOCKETSERVER_H__
#define WEBRTC_BASE_MACSOCKETSERVER_H__

#include <set>
#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS) // Invalid on IOS
#include <Carbon/Carbon.h>
#endif
#include "webrtc/base/physicalsocketserver.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// MacBaseSocketServer
///////////////////////////////////////////////////////////////////////////////
class MacAsyncSocket;

class MacBaseSocketServer : public PhysicalSocketServer {
 public:
  MacBaseSocketServer();
  ~MacBaseSocketServer() override;

  // SocketServer Interface
  Socket* CreateSocket(int type) override;
  Socket* CreateSocket(int family, int type) override;

  AsyncSocket* CreateAsyncSocket(int type) override;
  AsyncSocket* CreateAsyncSocket(int family, int type) override;

  bool Wait(int cms, bool process_io) override = 0;
  void WakeUp() override = 0;

  void RegisterSocket(MacAsyncSocket* socket);
  void UnregisterSocket(MacAsyncSocket* socket);

  // PhysicalSocketServer Overrides
  bool SetPosixSignalHandler(int signum, void (*handler)(int)) override;

 protected:
  void EnableSocketCallbacks(bool enable);
  const std::set<MacAsyncSocket*>& sockets() {
    return sockets_;
  }

 private:
  static void FileDescriptorCallback(CFFileDescriptorRef ref,
                                     CFOptionFlags flags,
                                     void* context);

  std::set<MacAsyncSocket*> sockets_;
};

// Core Foundation implementation of the socket server. While idle it
// will run the current CF run loop. When the socket server has work
// to do the run loop will be paused. Does not support Carbon or Cocoa
// UI interaction.
class MacCFSocketServer : public MacBaseSocketServer {
 public:
  MacCFSocketServer();
  ~MacCFSocketServer() override;

  // SocketServer Interface
  bool Wait(int cms, bool process_io) override;
  void WakeUp() override;
  void OnWakeUpCallback();

 private:
  CFRunLoopRef run_loop_;
  CFRunLoopSourceRef wake_up_;
};

#ifndef CARBON_DEPRECATED

///////////////////////////////////////////////////////////////////////////////
// MacCarbonSocketServer
///////////////////////////////////////////////////////////////////////////////

// Interacts with the Carbon event queue. While idle it will block,
// waiting for events. When the socket server has work to do, it will
// post a 'wake up' event to the queue, causing the thread to exit the
// event loop until the next call to Wait. Other events are dispatched
// to their target. Supports Carbon and Cocoa UI interaction.
class MacCarbonSocketServer : public MacBaseSocketServer {
 public:
  MacCarbonSocketServer();
  virtual ~MacCarbonSocketServer();

  // SocketServer Interface
  virtual bool Wait(int cms, bool process_io);
  virtual void WakeUp();

 private:
  EventQueueRef event_queue_;
  EventRef wake_up_;
};

///////////////////////////////////////////////////////////////////////////////
// MacCarbonAppSocketServer
///////////////////////////////////////////////////////////////////////////////

// Runs the Carbon application event loop on the current thread while
// idle. When the socket server has work to do, it will post an event
// to the queue, causing the thread to exit the event loop until the
// next call to Wait. Other events are automatically dispatched to
// their target.
class MacCarbonAppSocketServer : public MacBaseSocketServer {
 public:
  MacCarbonAppSocketServer();
  virtual ~MacCarbonAppSocketServer();

  // SocketServer Interface
  virtual bool Wait(int cms, bool process_io);
  virtual void WakeUp();

 private:
  static OSStatus WakeUpEventHandler(EventHandlerCallRef next, EventRef event,
                                     void *data);
  static void TimerHandler(EventLoopTimerRef timer, void *data);

  EventQueueRef event_queue_;
  EventHandlerRef event_handler_;
  EventLoopTimerRef timer_;
};

#endif
} // namespace rtc

#endif  // WEBRTC_BASE_MACSOCKETSERVER_H__
