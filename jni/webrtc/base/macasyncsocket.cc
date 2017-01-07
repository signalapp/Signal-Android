/*
 *  Copyright 2010 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
//
// MacAsyncSocket is a kind of AsyncSocket. It does not support the SOCK_DGRAM
// type (yet). It works asynchronously, which means that users of this socket
// should connect to the various events declared in asyncsocket.h to receive
// notifications about this socket.  It uses CFSockets for signals, but prefers
// the basic bsd socket operations rather than their CFSocket wrappers when
// possible.

#include <CoreFoundation/CoreFoundation.h>
#include <fcntl.h>

#include "webrtc/base/macasyncsocket.h"

#include "webrtc/base/logging.h"
#include "webrtc/base/macsocketserver.h"

namespace rtc {

static const int kCallbackFlags = kCFSocketReadCallBack |
                                  kCFSocketConnectCallBack |
                                  kCFSocketWriteCallBack;

MacAsyncSocket::MacAsyncSocket(MacBaseSocketServer* ss, int family)
    : ss_(ss),
      socket_(NULL),
      native_socket_(INVALID_SOCKET),
      source_(NULL),
      current_callbacks_(0),
      disabled_(false),
      error_(0),
      state_(CS_CLOSED),
      resolver_(NULL) {
  Initialize(family);
}

MacAsyncSocket::~MacAsyncSocket() {
  Close();
}

// Returns the address to which the socket is bound.  If the socket is not
// bound, then the any-address is returned.
SocketAddress MacAsyncSocket::GetLocalAddress() const {
  SocketAddress address;

  // The CFSocket doesn't pick up on implicit binds from the connect call.
  // Calling bind in before connect explicitly causes errors, so just query
  // the underlying bsd socket.
  sockaddr_storage addr;
  socklen_t addrlen = sizeof(addr);
  int result = ::getsockname(native_socket_,
                             reinterpret_cast<sockaddr*>(&addr), &addrlen);
  if (result >= 0) {
    SocketAddressFromSockAddrStorage(addr, &address);
  }
  return address;
}

// Returns the address to which the socket is connected.  If the socket is not
// connected, then the any-address is returned.
SocketAddress MacAsyncSocket::GetRemoteAddress() const {
  SocketAddress address;

  // Use native_socket for consistency with GetLocalAddress.
  sockaddr_storage addr;
  socklen_t addrlen = sizeof(addr);
  int result = ::getpeername(native_socket_,
                             reinterpret_cast<sockaddr*>(&addr), &addrlen);
  if (result >= 0) {
    SocketAddressFromSockAddrStorage(addr, &address);
  }
  return address;
}

// Bind the socket to a local address.
int MacAsyncSocket::Bind(const SocketAddress& address) {
  sockaddr_storage saddr = {0};
  size_t len = address.ToSockAddrStorage(&saddr);
  int err = ::bind(native_socket_, reinterpret_cast<sockaddr*>(&saddr), len);
  if (err == SOCKET_ERROR) error_ = errno;
  return err;
}

void MacAsyncSocket::OnResolveResult(SignalThread* thread) {
  if (thread != resolver_) {
    return;
  }
  int error = resolver_->GetError();
  if (error == 0) {
    error = DoConnect(resolver_->address());
  } else {
    Close();
  }
  if (error) {
    error_ = error;
    SignalCloseEvent(this, error_);
  }
}

// Connect to a remote address.
int MacAsyncSocket::Connect(const SocketAddress& addr) {
  // TODO(djw): Consolidate all the connect->resolve->doconnect implementations.
  if (state_ != CS_CLOSED) {
    SetError(EALREADY);
    return SOCKET_ERROR;
  }
  if (addr.IsUnresolvedIP()) {
    LOG(LS_VERBOSE) << "Resolving addr in MacAsyncSocket::Connect";
    resolver_ = new AsyncResolver();
    resolver_->SignalWorkDone.connect(this,
                                      &MacAsyncSocket::OnResolveResult);
    resolver_->Start(addr);
    state_ = CS_CONNECTING;
    return 0;
  }
  return DoConnect(addr);
}

int MacAsyncSocket::DoConnect(const SocketAddress& addr) {
  if (!valid()) {
    Initialize(addr.family());
    if (!valid())
      return SOCKET_ERROR;
  }

  sockaddr_storage saddr;
  size_t len = addr.ToSockAddrStorage(&saddr);
  int result = ::connect(native_socket_, reinterpret_cast<sockaddr*>(&saddr),
                         len);

  if (result != SOCKET_ERROR) {
    state_ = CS_CONNECTED;
  } else {
    error_ = errno;
    if (error_ == EINPROGRESS) {
      state_ = CS_CONNECTING;
      result = 0;
    }
  }
  return result;
}

// Send to the remote end we're connected to.
int MacAsyncSocket::Send(const void* buffer, size_t length) {
  if (!valid()) {
    return SOCKET_ERROR;
  }

  int sent = ::send(native_socket_, buffer, length, 0);

  if (sent == SOCKET_ERROR) {
    error_ = errno;

    if (IsBlocking()) {
      // Reenable the writable callback (once), since we are flow controlled.
      CFSocketEnableCallBacks(socket_, kCallbackFlags);
      current_callbacks_ = kCallbackFlags;
    }
  }
  return sent;
}

// Send to the given address. We may or may not be connected to anyone.
int MacAsyncSocket::SendTo(const void* buffer, size_t length,
                           const SocketAddress& address) {
  if (!valid()) {
    return SOCKET_ERROR;
  }

  sockaddr_storage saddr;
  size_t len = address.ToSockAddrStorage(&saddr);
  int sent = ::sendto(native_socket_, buffer, length, 0,
                      reinterpret_cast<sockaddr*>(&saddr), len);

  if (sent == SOCKET_ERROR) {
    error_ = errno;
  }

  return sent;
}

// Read data received from the remote end we're connected to.
int MacAsyncSocket::Recv(void* buffer, size_t length, int64_t* timestamp) {
  if (timestamp) {
    *timestamp = -1;
  }
  int received = ::recv(native_socket_, reinterpret_cast<char*>(buffer),
                        length, 0);
  if (received == SOCKET_ERROR) error_ = errno;

  // Recv should only be called when there is data to read
  ASSERT((received != 0) || (length == 0));
  return received;
}

// Read data received from any remote party
int MacAsyncSocket::RecvFrom(void* buffer,
                             size_t length,
                             SocketAddress* out_addr,
                             int64_t* timestamp) {
  if (timestamp) {
    *timestamp = -1;
  }
  sockaddr_storage saddr;
  socklen_t addr_len = sizeof(saddr);
  int received = ::recvfrom(native_socket_, reinterpret_cast<char*>(buffer),
                            length, 0, reinterpret_cast<sockaddr*>(&saddr),
                            &addr_len);
  if (received >= 0 && out_addr != NULL) {
    SocketAddressFromSockAddrStorage(saddr, out_addr);
  } else if (received == SOCKET_ERROR) {
    error_ = errno;
  }
  return received;
}

int MacAsyncSocket::Listen(int backlog) {
  if (!valid()) {
    return SOCKET_ERROR;
  }

  int res = ::listen(native_socket_, backlog);
  if (res != SOCKET_ERROR)
    state_ = CS_CONNECTING;
  else
    error_ = errno;

  return res;
}

MacAsyncSocket* MacAsyncSocket::Accept(SocketAddress* out_addr) {
  sockaddr_storage saddr;
  socklen_t addr_len = sizeof(saddr);

  int socket_fd = ::accept(native_socket_, reinterpret_cast<sockaddr*>(&saddr),
                           &addr_len);
  if (socket_fd == INVALID_SOCKET) {
    error_ = errno;
    return NULL;
  }

  MacAsyncSocket* s = new MacAsyncSocket(ss_, saddr.ss_family, socket_fd);
  if (s && s->valid()) {
    s->state_ = CS_CONNECTED;
    if (out_addr)
      SocketAddressFromSockAddrStorage(saddr, out_addr);
  } else {
    delete s;
    s = NULL;
  }
  return s;
}

int MacAsyncSocket::Close() {
  if (source_ != NULL) {
    CFRunLoopSourceInvalidate(source_);
    CFRelease(source_);
    if (ss_) ss_->UnregisterSocket(this);
    source_ = NULL;
  }

  if (socket_ != NULL) {
    CFSocketInvalidate(socket_);
    CFRelease(socket_);
    socket_ = NULL;
  }

  if (resolver_) {
    resolver_->Destroy(false);
    resolver_ = NULL;
  }

  native_socket_ = INVALID_SOCKET;  // invalidates the socket
  error_ = 0;
  state_ = CS_CLOSED;
  return 0;
}

int MacAsyncSocket::EstimateMTU(uint16_t* mtu) {
  ASSERT(false && "NYI");
  return -1;
}

int MacAsyncSocket::GetError() const {
  return error_;
}

void MacAsyncSocket::SetError(int error) {
  error_ = error;
}

Socket::ConnState MacAsyncSocket::GetState() const {
  return state_;
}

int MacAsyncSocket::GetOption(Option opt, int* value) {
  ASSERT(false && "NYI");
  return -1;
}

int MacAsyncSocket::SetOption(Option opt, int value) {
  ASSERT(false && "NYI");
  return -1;
}

void MacAsyncSocket::EnableCallbacks() {
  if (valid()) {
    disabled_ = false;
    CFSocketEnableCallBacks(socket_, current_callbacks_);
  }
}

void MacAsyncSocket::DisableCallbacks() {
  if (valid()) {
    disabled_ = true;
    CFSocketDisableCallBacks(socket_, kCallbackFlags);
  }
}

MacAsyncSocket::MacAsyncSocket(MacBaseSocketServer* ss, int family,
                               int native_socket)
    : ss_(ss),
      socket_(NULL),
      native_socket_(native_socket),
      source_(NULL),
      current_callbacks_(0),
      disabled_(false),
      error_(0),
      state_(CS_CLOSED),
      resolver_(NULL) {
  Initialize(family);
}

// Create a new socket, wrapping the native socket if provided or creating one
// otherwise. In case of any failure, consume the native socket.  We assume the
// wrapped socket is in the closed state.  If this is not the case you must
// update the state_ field for this socket yourself.
void MacAsyncSocket::Initialize(int family) {
  CFSocketContext ctx = { 0 };
  ctx.info = this;

  // First create the CFSocket
  CFSocketRef cf_socket = NULL;
  bool res = false;
  if (native_socket_ == INVALID_SOCKET) {
    cf_socket = CFSocketCreate(kCFAllocatorDefault,
                               family, SOCK_STREAM, IPPROTO_TCP,
                               kCallbackFlags, MacAsyncSocketCallBack, &ctx);
  } else {
    cf_socket = CFSocketCreateWithNative(kCFAllocatorDefault,
                                         native_socket_, kCallbackFlags,
                                         MacAsyncSocketCallBack, &ctx);
  }

  if (cf_socket) {
    res = true;
    socket_ = cf_socket;
    native_socket_ = CFSocketGetNative(cf_socket);
    current_callbacks_ = kCallbackFlags;
  }

  if (res) {
    // Make the underlying socket asynchronous
    res = (-1 != ::fcntl(native_socket_, F_SETFL,
                         ::fcntl(native_socket_, F_GETFL, 0) | O_NONBLOCK));
  }

  if (res) {
    // Add this socket to the run loop, at priority 1 so that it will be
    // queued behind any pending signals.
    source_ = CFSocketCreateRunLoopSource(kCFAllocatorDefault, socket_, 1);
    res = (source_ != NULL);
    if (!res) errno = EINVAL;
  }

  if (res) {
    if (ss_) ss_->RegisterSocket(this);
    CFRunLoopAddSource(CFRunLoopGetCurrent(), source_, kCFRunLoopCommonModes);
  }

  if (!res) {
    int error = errno;
    Close();  //  Clears error_.
    error_ = error;
  }
}

// Call CFRelease on the result when done using it
CFDataRef MacAsyncSocket::CopyCFAddress(const SocketAddress& address) {
  sockaddr_storage saddr;
  size_t len = address.ToSockAddrStorage(&saddr);

  const UInt8* bytes = reinterpret_cast<UInt8*>(&saddr);

  CFDataRef cf_address = CFDataCreate(kCFAllocatorDefault,
                                      bytes, len);

  ASSERT(cf_address != NULL);
  return cf_address;
}

void MacAsyncSocket::MacAsyncSocketCallBack(CFSocketRef s,
                                            CFSocketCallBackType callbackType,
                                            CFDataRef address,
                                            const void* data,
                                            void* info) {
  MacAsyncSocket* this_socket =
      reinterpret_cast<MacAsyncSocket*>(info);
  ASSERT(this_socket != NULL && this_socket->socket_ == s);

  // Don't signal any socket messages if the socketserver is not listening on
  // them.  When we are reenabled they will be requeued and will fire again.
  if (this_socket->disabled_)
    return;

  switch (callbackType) {
    case kCFSocketReadCallBack:
      // This callback is invoked in one of 3 situations:
      // 1. A new connection is waiting to be accepted.
      // 2. The remote end closed the connection (a recv will return 0).
      // 3. Data is available to read.
      // 4. The connection closed unhappily (recv will return -1).
      if (this_socket->state_ == CS_CONNECTING) {
        // Case 1.
        this_socket->SignalReadEvent(this_socket);
      } else {
        char ch, amt;
        amt = ::recv(this_socket->native_socket_, &ch, 1, MSG_PEEK);
        if (amt == 0) {
          // Case 2.
          this_socket->state_ = CS_CLOSED;

          // Disable additional callbacks or we will signal close twice.
          CFSocketDisableCallBacks(this_socket->socket_, kCFSocketReadCallBack);
          this_socket->current_callbacks_ &= ~kCFSocketReadCallBack;
          this_socket->SignalCloseEvent(this_socket, 0);
        } else if (amt > 0) {
          // Case 3.
          this_socket->SignalReadEvent(this_socket);
        } else {
          // Case 4.
          int error = errno;
          if (error == EAGAIN) {
            // Observed in practice.  Let's hope it's a spurious or out of date
            // signal, since we just eat it.
          } else {
            this_socket->error_ = error;
            this_socket->SignalCloseEvent(this_socket, error);
          }
        }
      }
      break;

    case kCFSocketConnectCallBack:
      if (data != NULL) {
        // An error occured in the background while connecting
        this_socket->error_ = errno;
        this_socket->state_ = CS_CLOSED;
        this_socket->SignalCloseEvent(this_socket, this_socket->error_);
      } else {
        this_socket->state_ = CS_CONNECTED;
        this_socket->SignalConnectEvent(this_socket);
      }
      break;

    case kCFSocketWriteCallBack:
      // Update our callback tracking.  Write doesn't reenable, so it's off now.
      this_socket->current_callbacks_ &= ~kCFSocketWriteCallBack;
      this_socket->SignalWriteEvent(this_socket);
      break;

    default:
      ASSERT(false && "Invalid callback type for socket");
  }
}

}  // namespace rtc
