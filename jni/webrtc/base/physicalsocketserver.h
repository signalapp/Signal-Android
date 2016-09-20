/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_PHYSICALSOCKETSERVER_H__
#define WEBRTC_BASE_PHYSICALSOCKETSERVER_H__

#include <memory>
#include <vector>

#include "webrtc/base/asyncfile.h"
#include "webrtc/base/nethelpers.h"
#include "webrtc/base/socketserver.h"
#include "webrtc/base/criticalsection.h"

#if defined(WEBRTC_POSIX)
typedef int SOCKET;
#endif // WEBRTC_POSIX

namespace rtc {

// Event constants for the Dispatcher class.
enum DispatcherEvent {
  DE_READ    = 0x0001,
  DE_WRITE   = 0x0002,
  DE_CONNECT = 0x0004,
  DE_CLOSE   = 0x0008,
  DE_ACCEPT  = 0x0010,
};

class Signaler;
#if defined(WEBRTC_POSIX)
class PosixSignalDispatcher;
#endif

class Dispatcher {
 public:
  virtual ~Dispatcher() {}
  virtual uint32_t GetRequestedEvents() = 0;
  virtual void OnPreEvent(uint32_t ff) = 0;
  virtual void OnEvent(uint32_t ff, int err) = 0;
#if defined(WEBRTC_WIN)
  virtual WSAEVENT GetWSAEvent() = 0;
  virtual SOCKET GetSocket() = 0;
  virtual bool CheckSignalClose() = 0;
#elif defined(WEBRTC_POSIX)
  virtual int GetDescriptor() = 0;
  virtual bool IsDescriptorClosed() = 0;
#endif
};

// A socket server that provides the real sockets of the underlying OS.
class PhysicalSocketServer : public SocketServer {
 public:
  PhysicalSocketServer();
  ~PhysicalSocketServer() override;

  // SocketFactory:
  Socket* CreateSocket(int type) override;
  Socket* CreateSocket(int family, int type) override;

  AsyncSocket* CreateAsyncSocket(int type) override;
  AsyncSocket* CreateAsyncSocket(int family, int type) override;

  // Internal Factory for Accept (virtual so it can be overwritten in tests).
  virtual AsyncSocket* WrapSocket(SOCKET s);

  // SocketServer:
  bool Wait(int cms, bool process_io) override;
  void WakeUp() override;

  void Add(Dispatcher* dispatcher);
  void Remove(Dispatcher* dispatcher);

#if defined(WEBRTC_POSIX)
  AsyncFile* CreateFile(int fd);

  // Sets the function to be executed in response to the specified POSIX signal.
  // The function is executed from inside Wait() using the "self-pipe trick"--
  // regardless of which thread receives the signal--and hence can safely
  // manipulate user-level data structures.
  // "handler" may be SIG_IGN, SIG_DFL, or a user-specified function, just like
  // with signal(2).
  // Only one PhysicalSocketServer should have user-level signal handlers.
  // Dispatching signals on multiple PhysicalSocketServers is not reliable.
  // The signal mask is not modified. It is the caller's responsibily to
  // maintain it as desired.
  virtual bool SetPosixSignalHandler(int signum, void (*handler)(int));

 protected:
  Dispatcher* signal_dispatcher();
#endif

 private:
  typedef std::vector<Dispatcher*> DispatcherList;
  typedef std::vector<size_t*> IteratorList;

#if defined(WEBRTC_POSIX)
  static bool InstallSignal(int signum, void (*handler)(int));

  std::unique_ptr<PosixSignalDispatcher> signal_dispatcher_;
#endif
  DispatcherList dispatchers_;
  IteratorList iterators_;
  Signaler* signal_wakeup_;
  CriticalSection crit_;
  bool fWait_;
#if defined(WEBRTC_WIN)
  WSAEVENT socket_ev_;
#endif
};

class PhysicalSocket : public AsyncSocket, public sigslot::has_slots<> {
 public:
  PhysicalSocket(PhysicalSocketServer* ss, SOCKET s = INVALID_SOCKET);
  ~PhysicalSocket() override;

  // Creates the underlying OS socket (same as the "socket" function).
  virtual bool Create(int family, int type);

  SocketAddress GetLocalAddress() const override;
  SocketAddress GetRemoteAddress() const override;

  int Bind(const SocketAddress& bind_addr) override;
  int Connect(const SocketAddress& addr) override;

  int GetError() const override;
  void SetError(int error) override;

  ConnState GetState() const override;

  int GetOption(Option opt, int* value) override;
  int SetOption(Option opt, int value) override;

  int Send(const void* pv, size_t cb) override;
  int SendTo(const void* buffer,
             size_t length,
             const SocketAddress& addr) override;

  int Recv(void* buffer, size_t length, int64_t* timestamp) override;
  int RecvFrom(void* buffer,
               size_t length,
               SocketAddress* out_addr,
               int64_t* timestamp) override;

  int Listen(int backlog) override;
  AsyncSocket* Accept(SocketAddress* out_addr) override;

  int Close() override;

  int EstimateMTU(uint16_t* mtu) override;

  SocketServer* socketserver() { return ss_; }

 protected:
  int DoConnect(const SocketAddress& connect_addr);

  // Make virtual so ::accept can be overwritten in tests.
  virtual SOCKET DoAccept(SOCKET socket, sockaddr* addr, socklen_t* addrlen);

  // Make virtual so ::send can be overwritten in tests.
  virtual int DoSend(SOCKET socket, const char* buf, int len, int flags);

  // Make virtual so ::sendto can be overwritten in tests.
  virtual int DoSendTo(SOCKET socket, const char* buf, int len, int flags,
                       const struct sockaddr* dest_addr, socklen_t addrlen);

  void OnResolveResult(AsyncResolverInterface* resolver);

  void UpdateLastError();
  void MaybeRemapSendError();

  static int TranslateOption(Option opt, int* slevel, int* sopt);

  PhysicalSocketServer* ss_;
  SOCKET s_;
  uint8_t enabled_events_;
  bool udp_;
  CriticalSection crit_;
  int error_ GUARDED_BY(crit_);
  ConnState state_;
  AsyncResolver* resolver_;

#if !defined(NDEBUG)
  std::string dbg_addr_;
#endif
};

class SocketDispatcher : public Dispatcher, public PhysicalSocket {
 public:
  explicit SocketDispatcher(PhysicalSocketServer *ss);
  SocketDispatcher(SOCKET s, PhysicalSocketServer *ss);
  ~SocketDispatcher() override;

  bool Initialize();

  virtual bool Create(int type);
  bool Create(int family, int type) override;

#if defined(WEBRTC_WIN)
  WSAEVENT GetWSAEvent() override;
  SOCKET GetSocket() override;
  bool CheckSignalClose() override;
#elif defined(WEBRTC_POSIX)
  int GetDescriptor() override;
  bool IsDescriptorClosed() override;
#endif

  uint32_t GetRequestedEvents() override;
  void OnPreEvent(uint32_t ff) override;
  void OnEvent(uint32_t ff, int err) override;

  int Close() override;

#if defined(WEBRTC_WIN)
 private:
  static int next_id_;
  int id_;
  bool signal_close_;
  int signal_err_;
#endif // WEBRTC_WIN
};

} // namespace rtc

#endif // WEBRTC_BASE_PHYSICALSOCKETSERVER_H__
