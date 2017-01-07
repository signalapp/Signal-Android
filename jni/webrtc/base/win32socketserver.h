/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_WIN32SOCKETSERVER_H_
#define WEBRTC_BASE_WIN32SOCKETSERVER_H_

#if defined(WEBRTC_WIN)
#include "webrtc/base/asyncsocket.h"
#include "webrtc/base/criticalsection.h"
#include "webrtc/base/messagequeue.h"
#include "webrtc/base/socketserver.h"
#include "webrtc/base/socketfactory.h"
#include "webrtc/base/socket.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/win32window.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// Win32Socket
///////////////////////////////////////////////////////////////////////////////

class Win32Socket : public AsyncSocket {
 public:
  Win32Socket();
  virtual ~Win32Socket();

  bool CreateT(int family, int type);

  int Attach(SOCKET s);
  void SetTimeout(int ms);

  // AsyncSocket Interface
  virtual SocketAddress GetLocalAddress() const;
  virtual SocketAddress GetRemoteAddress() const;
  virtual int Bind(const SocketAddress& addr);
  virtual int Connect(const SocketAddress& addr);
  virtual int Send(const void *buffer, size_t length);
  virtual int SendTo(const void *buffer, size_t length, const SocketAddress& addr);
  virtual int Recv(void* buffer, size_t length, int64_t* timestamp);
  virtual int RecvFrom(void* buffer,
                       size_t length,
                       SocketAddress* out_addr,
                       int64_t* timestamp);
  virtual int Listen(int backlog);
  virtual Win32Socket *Accept(SocketAddress *out_addr);
  virtual int Close();
  virtual int GetError() const;
  virtual void SetError(int error);
  virtual ConnState GetState() const;
  virtual int EstimateMTU(uint16_t* mtu);
  virtual int GetOption(Option opt, int* value);
  virtual int SetOption(Option opt, int value);

 private:
  void CreateSink();
  bool SetAsync(int events);
  int DoConnect(const SocketAddress& addr);
  bool HandleClosed(int close_error);
  void PostClosed();
  void UpdateLastError();
  static int TranslateOption(Option opt, int* slevel, int* sopt);

  void OnSocketNotify(SOCKET socket, int event, int error);
  void OnDnsNotify(HANDLE task, int error);

  SOCKET socket_;
  int error_;
  ConnState state_;
  SocketAddress addr_;         // address that we connected to (see DoConnect)
  uint32_t connect_time_;
  bool closing_;
  int close_error_;

  class EventSink;
  friend class EventSink;
  EventSink * sink_;

  struct DnsLookup;
  DnsLookup * dns_;
};

///////////////////////////////////////////////////////////////////////////////
// Win32SocketServer
///////////////////////////////////////////////////////////////////////////////

class Win32SocketServer : public SocketServer {
 public:
  explicit Win32SocketServer(MessageQueue* message_queue);
  virtual ~Win32SocketServer();

  void set_modeless_dialog(HWND hdlg) {
    hdlg_ = hdlg;
  }

  // SocketServer Interface
  virtual Socket* CreateSocket(int type);
  virtual Socket* CreateSocket(int family, int type);

  virtual AsyncSocket* CreateAsyncSocket(int type);
  virtual AsyncSocket* CreateAsyncSocket(int family, int type);

  virtual void SetMessageQueue(MessageQueue* queue);
  virtual bool Wait(int cms, bool process_io);
  virtual void WakeUp();

  void Pump();

  HWND handle() { return wnd_.handle(); }

 private:
  class MessageWindow : public Win32Window {
   public:
    explicit MessageWindow(Win32SocketServer* ss) : ss_(ss) {}
   private:
    virtual bool OnMessage(UINT msg, WPARAM wp, LPARAM lp, LRESULT& result);
    Win32SocketServer* ss_;
  };

  static const TCHAR kWindowName[];
  MessageQueue *message_queue_;
  MessageWindow wnd_;
  CriticalSection cs_;
  bool posted_;
  HWND hdlg_;
};

///////////////////////////////////////////////////////////////////////////////
// Win32Thread. Automatically pumps Windows messages.
///////////////////////////////////////////////////////////////////////////////

class Win32Thread : public Thread {
 public:
  Win32Thread() : ss_(this), id_(0) {
    set_socketserver(&ss_);
  }
  virtual ~Win32Thread() {
    Stop();
    set_socketserver(NULL);
  }
  virtual void Run() {
    id_ = GetCurrentThreadId();
    Thread::Run();
    id_ = 0;
  }
  virtual void Quit() {
    PostThreadMessage(id_, WM_QUIT, 0, 0);
  }
 private:
  Win32SocketServer ss_;
  DWORD id_;
};

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // WEBRTC_WIN

#endif  // WEBRTC_BASE_WIN32SOCKETSERVER_H_
