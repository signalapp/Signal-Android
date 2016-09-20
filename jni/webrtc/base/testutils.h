/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TESTUTILS_H__
#define WEBRTC_BASE_TESTUTILS_H__

// Utilities for testing rtc infrastructure in unittests

#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID)
#include <X11/Xlib.h>
#include <X11/extensions/Xrandr.h>

// X defines a few macros that stomp on types that gunit.h uses.
#undef None
#undef Bool
#endif

#include <algorithm>
#include <map>
#include <memory>
#include <vector>
#include "webrtc/base/arraysize.h"
#include "webrtc/base/asyncsocket.h"
#include "webrtc/base/common.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/nethelpers.h"
#include "webrtc/base/pathutils.h"
#include "webrtc/base/stream.h"
#include "webrtc/base/stringencode.h"
#include "webrtc/base/stringutils.h"
#include "webrtc/base/thread.h"

namespace testing {

using namespace rtc;

///////////////////////////////////////////////////////////////////////////////
// StreamSink - Monitor asynchronously signalled events from StreamInterface
// or AsyncSocket (which should probably be a StreamInterface.
///////////////////////////////////////////////////////////////////////////////

// Note: Any event that is an error is treaded as SSE_ERROR instead of that
// event.

enum StreamSinkEvent {
  SSE_OPEN  = SE_OPEN,
  SSE_READ  = SE_READ,
  SSE_WRITE = SE_WRITE,
  SSE_CLOSE = SE_CLOSE,
  SSE_ERROR = 16
};

class StreamSink : public sigslot::has_slots<> {
 public:
  void Monitor(StreamInterface* stream) {
   stream->SignalEvent.connect(this, &StreamSink::OnEvent);
   events_.erase(stream);
  }
  void Unmonitor(StreamInterface* stream) {
   stream->SignalEvent.disconnect(this);
   // In case you forgot to unmonitor a previous object with this address
   events_.erase(stream);
  }
  bool Check(StreamInterface* stream, StreamSinkEvent event, bool reset = true) {
    return DoCheck(stream, event, reset);
  }
  int Events(StreamInterface* stream, bool reset = true) {
    return DoEvents(stream, reset);
  }

  void Monitor(AsyncSocket* socket) {
   socket->SignalConnectEvent.connect(this, &StreamSink::OnConnectEvent);
   socket->SignalReadEvent.connect(this, &StreamSink::OnReadEvent);
   socket->SignalWriteEvent.connect(this, &StreamSink::OnWriteEvent);
   socket->SignalCloseEvent.connect(this, &StreamSink::OnCloseEvent);
   // In case you forgot to unmonitor a previous object with this address
   events_.erase(socket);
  }
  void Unmonitor(AsyncSocket* socket) {
   socket->SignalConnectEvent.disconnect(this);
   socket->SignalReadEvent.disconnect(this);
   socket->SignalWriteEvent.disconnect(this);
   socket->SignalCloseEvent.disconnect(this);
   events_.erase(socket);
  }
  bool Check(AsyncSocket* socket, StreamSinkEvent event, bool reset = true) {
    return DoCheck(socket, event, reset);
  }
  int Events(AsyncSocket* socket, bool reset = true) {
    return DoEvents(socket, reset);
  }

 private:
  typedef std::map<void*,int> EventMap;

  void OnEvent(StreamInterface* stream, int events, int error) {
    if (error) {
      events = SSE_ERROR;
    }
    AddEvents(stream, events);
  }
  void OnConnectEvent(AsyncSocket* socket) {
    AddEvents(socket, SSE_OPEN);
  }
  void OnReadEvent(AsyncSocket* socket) {
    AddEvents(socket, SSE_READ);
  }
  void OnWriteEvent(AsyncSocket* socket) {
    AddEvents(socket, SSE_WRITE);
  }
  void OnCloseEvent(AsyncSocket* socket, int error) {
    AddEvents(socket, (0 == error) ? SSE_CLOSE : SSE_ERROR);
  }

  void AddEvents(void* obj, int events) {
    EventMap::iterator it = events_.find(obj);
    if (events_.end() == it) {
      events_.insert(EventMap::value_type(obj, events));
    } else {
      it->second |= events;
    }
  }
  bool DoCheck(void* obj, StreamSinkEvent event, bool reset) {
    EventMap::iterator it = events_.find(obj);
    if ((events_.end() == it) || (0 == (it->second & event))) {
      return false;
    }
    if (reset) {
      it->second &= ~event;
    }
    return true;
  }
  int DoEvents(void* obj, bool reset) {
    EventMap::iterator it = events_.find(obj);
    if (events_.end() == it)
      return 0;
    int events = it->second;
    if (reset) {
      it->second = 0;
    }
    return events;
  }

  EventMap events_;
};

///////////////////////////////////////////////////////////////////////////////
// StreamSource - Implements stream interface and simulates asynchronous
// events on the stream, without a network.  Also buffers written data.
///////////////////////////////////////////////////////////////////////////////

class StreamSource : public StreamInterface {
public:
  StreamSource() {
    Clear();
  }

  void Clear() {
    readable_data_.clear();
    written_data_.clear();
    state_ = SS_CLOSED;
    read_block_ = 0;
    write_block_ = SIZE_UNKNOWN;
  }
  void QueueString(const char* data) {
    QueueData(data, strlen(data));
  }
  void QueueStringF(const char* format, ...) {
    va_list args;
    va_start(args, format);
    char buffer[1024];
    size_t len = vsprintfn(buffer, sizeof(buffer), format, args);
    ASSERT(len < sizeof(buffer) - 1);
    va_end(args);
    QueueData(buffer, len);
  }
  void QueueData(const char* data, size_t len) {
    readable_data_.insert(readable_data_.end(), data, data + len);
    if ((SS_OPEN == state_) && (readable_data_.size() == len)) {
      SignalEvent(this, SE_READ, 0);
    }
  }
  std::string ReadData() {
    std::string data;
    // avoid accessing written_data_[0] if it is undefined
    if (written_data_.size() > 0) {
      data.insert(0, &written_data_[0], written_data_.size());
    }
    written_data_.clear();
    return data;
  }
  void SetState(StreamState state) {
    int events = 0;
    if ((SS_OPENING == state_) && (SS_OPEN == state)) {
      events |= SE_OPEN;
      if (!readable_data_.empty()) {
        events |= SE_READ;
      }
    } else if ((SS_CLOSED != state_) && (SS_CLOSED == state)) {
      events |= SE_CLOSE;
    }
    state_ = state;
    if (events) {
      SignalEvent(this, events, 0);
    }
  }
  // Will cause Read to block when there are pos bytes in the read queue.
  void SetReadBlock(size_t pos) { read_block_ = pos; }
  // Will cause Write to block when there are pos bytes in the write queue.
  void SetWriteBlock(size_t pos) { write_block_ = pos; }

  virtual StreamState GetState() const { return state_; }
  virtual StreamResult Read(void* buffer, size_t buffer_len,
                            size_t* read, int* error) {
    if (SS_CLOSED == state_) {
      if (error) *error = -1;
      return SR_ERROR;
    }
    if ((SS_OPENING == state_) || (readable_data_.size() <= read_block_)) {
      return SR_BLOCK;
    }
    size_t count = std::min(buffer_len, readable_data_.size() - read_block_);
    memcpy(buffer, &readable_data_[0], count);
    size_t new_size = readable_data_.size() - count;
    // Avoid undefined access beyond the last element of the vector.
    // This only happens when new_size is 0.
    if (count < readable_data_.size()) {
      memmove(&readable_data_[0], &readable_data_[count], new_size);
    }
    readable_data_.resize(new_size);
    if (read) *read = count;
    return SR_SUCCESS;
  }
  virtual StreamResult Write(const void* data, size_t data_len,
                             size_t* written, int* error) {
    if (SS_CLOSED == state_) {
      if (error) *error = -1;
      return SR_ERROR;
    }
    if (SS_OPENING == state_) {
      return SR_BLOCK;
    }
    if (SIZE_UNKNOWN != write_block_) {
      if (written_data_.size() >= write_block_) {
        return SR_BLOCK;
      }
      if (data_len > (write_block_ - written_data_.size())) {
        data_len = write_block_ - written_data_.size();
      }
    }
    if (written) *written = data_len;
    const char* cdata = static_cast<const char*>(data);
    written_data_.insert(written_data_.end(), cdata, cdata + data_len);
    return SR_SUCCESS;
  }
  virtual void Close() { state_ = SS_CLOSED; }

private:
  typedef std::vector<char> Buffer;
  Buffer readable_data_, written_data_;
  StreamState state_;
  size_t read_block_, write_block_;
};

///////////////////////////////////////////////////////////////////////////////
// SocketTestClient
// Creates a simulated client for testing.  Works on real and virtual networks.
///////////////////////////////////////////////////////////////////////////////

class SocketTestClient : public sigslot::has_slots<> {
public:
  SocketTestClient() {
    Init(NULL, AF_INET);
  }
  SocketTestClient(AsyncSocket* socket) {
    Init(socket, socket->GetLocalAddress().family());
  }
  SocketTestClient(const SocketAddress& address) {
    Init(NULL, address.family());
    socket_->Connect(address);
  }

  AsyncSocket* socket() { return socket_.get(); }

  void QueueString(const char* data) {
    QueueData(data, strlen(data));
  }
  void QueueStringF(const char* format, ...) {
    va_list args;
    va_start(args, format);
    char buffer[1024];
    size_t len = vsprintfn(buffer, sizeof(buffer), format, args);
    ASSERT(len < sizeof(buffer) - 1);
    va_end(args);
    QueueData(buffer, len);
  }
  void QueueData(const char* data, size_t len) {
    send_buffer_.insert(send_buffer_.end(), data, data + len);
    if (Socket::CS_CONNECTED == socket_->GetState()) {
      Flush();
    }
  }
  std::string ReadData() {
    std::string data(&recv_buffer_[0], recv_buffer_.size());
    recv_buffer_.clear();
    return data;
  }

  bool IsConnected() const {
    return (Socket::CS_CONNECTED == socket_->GetState());
  }
  bool IsClosed() const {
    return (Socket::CS_CLOSED == socket_->GetState());
  }

private:
  typedef std::vector<char> Buffer;

  void Init(AsyncSocket* socket, int family) {
    if (!socket) {
      socket = Thread::Current()->socketserver()
          ->CreateAsyncSocket(family, SOCK_STREAM);
    }
    socket_.reset(socket);
    socket_->SignalConnectEvent.connect(this,
      &SocketTestClient::OnConnectEvent);
    socket_->SignalReadEvent.connect(this, &SocketTestClient::OnReadEvent);
    socket_->SignalWriteEvent.connect(this, &SocketTestClient::OnWriteEvent);
    socket_->SignalCloseEvent.connect(this, &SocketTestClient::OnCloseEvent);
  }

  void Flush() {
    size_t sent = 0;
    while (sent < send_buffer_.size()) {
      int result = socket_->Send(&send_buffer_[sent],
                                 send_buffer_.size() - sent);
      if (result > 0) {
        sent += result;
      } else {
        break;
      }
    }
    size_t new_size = send_buffer_.size() - sent;
    memmove(&send_buffer_[0], &send_buffer_[sent], new_size);
    send_buffer_.resize(new_size);
  }

  void OnConnectEvent(AsyncSocket* socket) {
    if (!send_buffer_.empty()) {
      Flush();
    }
  }
  void OnReadEvent(AsyncSocket* socket) {
    char data[64 * 1024];
    int result = socket_->Recv(data, arraysize(data), nullptr);
    if (result > 0) {
      recv_buffer_.insert(recv_buffer_.end(), data, data + result);
    }
  }
  void OnWriteEvent(AsyncSocket* socket) {
    if (!send_buffer_.empty()) {
      Flush();
    }
  }
  void OnCloseEvent(AsyncSocket* socket, int error) {
  }

  std::unique_ptr<AsyncSocket> socket_;
  Buffer send_buffer_, recv_buffer_;
};

///////////////////////////////////////////////////////////////////////////////
// SocketTestServer
// Creates a simulated server for testing.  Works on real and virtual networks.
///////////////////////////////////////////////////////////////////////////////

class SocketTestServer : public sigslot::has_slots<> {
 public:
  SocketTestServer(const SocketAddress& address)
      : socket_(Thread::Current()->socketserver()
                ->CreateAsyncSocket(address.family(), SOCK_STREAM))
  {
    socket_->SignalReadEvent.connect(this, &SocketTestServer::OnReadEvent);
    socket_->Bind(address);
    socket_->Listen(5);
  }
  virtual ~SocketTestServer() {
    clear();
  }

  size_t size() const { return clients_.size(); }
  SocketTestClient* client(size_t index) const { return clients_[index]; }
  SocketTestClient* operator[](size_t index) const { return client(index); }

  void clear() {
    for (size_t i=0; i<clients_.size(); ++i) {
      delete clients_[i];
    }
    clients_.clear();
  }

 private:
  void OnReadEvent(AsyncSocket* socket) {
    AsyncSocket* accepted =
      static_cast<AsyncSocket*>(socket_->Accept(NULL));
    if (!accepted)
      return;
    clients_.push_back(new SocketTestClient(accepted));
  }

  std::unique_ptr<AsyncSocket> socket_;
  std::vector<SocketTestClient*> clients_;
};

///////////////////////////////////////////////////////////////////////////////
// Unittest predicates which are similar to STREQ, but for raw memory
///////////////////////////////////////////////////////////////////////////////

inline AssertionResult CmpHelperMemEq(const char* expected_expression,
                                      const char* expected_length_expression,
                                      const char* actual_expression,
                                      const char* actual_length_expression,
                                      const void* expected,
                                      size_t expected_length,
                                      const void* actual,
                                      size_t actual_length)
{
  if ((expected_length == actual_length)
      && (0 == memcmp(expected, actual, expected_length))) {
    return AssertionSuccess();
  }

  Message msg;
  msg << "Value of: " << actual_expression
      << " [" << actual_length_expression << "]";
  if (true) {  //!actual_value.Equals(actual_expression)) {
    size_t buffer_size = actual_length * 2 + 1;
    char* buffer = STACK_ARRAY(char, buffer_size);
    hex_encode(buffer, buffer_size,
               reinterpret_cast<const char*>(actual), actual_length);
    msg << "\n  Actual: " << buffer << " [" << actual_length << "]";
  }

  msg << "\nExpected: " << expected_expression
      << " [" << expected_length_expression << "]";
  if (true) {  //!expected_value.Equals(expected_expression)) {
    size_t buffer_size = expected_length * 2 + 1;
    char* buffer = STACK_ARRAY(char, buffer_size);
    hex_encode(buffer, buffer_size,
               reinterpret_cast<const char*>(expected), expected_length);
    msg << "\nWhich is: " << buffer << " [" << expected_length << "]";
  }

  return AssertionFailure(msg);
}

#define EXPECT_MEMEQ(expected, expected_length, actual, actual_length) \
  EXPECT_PRED_FORMAT4(::testing::CmpHelperMemEq, expected, expected_length, \
                      actual, actual_length)

#define ASSERT_MEMEQ(expected, expected_length, actual, actual_length) \
  ASSERT_PRED_FORMAT4(::testing::CmpHelperMemEq, expected, expected_length, \
                      actual, actual_length)

///////////////////////////////////////////////////////////////////////////////
// Helpers for initializing constant memory with integers in a particular byte
// order
///////////////////////////////////////////////////////////////////////////////

#define BYTE_CAST(x) static_cast<uint8_t>((x)&0xFF)

// Declare a N-bit integer as a little-endian sequence of bytes
#define LE16(x) BYTE_CAST(((uint16_t)x) >> 0), BYTE_CAST(((uint16_t)x) >> 8)

#define LE32(x) \
  BYTE_CAST(((uint32_t)x) >> 0), BYTE_CAST(((uint32_t)x) >> 8), \
      BYTE_CAST(((uint32_t)x) >> 16), BYTE_CAST(((uint32_t)x) >> 24)

#define LE64(x) \
  BYTE_CAST(((uint64_t)x) >> 0), BYTE_CAST(((uint64_t)x) >> 8),       \
      BYTE_CAST(((uint64_t)x) >> 16), BYTE_CAST(((uint64_t)x) >> 24), \
      BYTE_CAST(((uint64_t)x) >> 32), BYTE_CAST(((uint64_t)x) >> 40), \
      BYTE_CAST(((uint64_t)x) >> 48), BYTE_CAST(((uint64_t)x) >> 56)

// Declare a N-bit integer as a big-endian (Internet) sequence of bytes
#define BE16(x) BYTE_CAST(((uint16_t)x) >> 8), BYTE_CAST(((uint16_t)x) >> 0)

#define BE32(x) \
  BYTE_CAST(((uint32_t)x) >> 24), BYTE_CAST(((uint32_t)x) >> 16), \
      BYTE_CAST(((uint32_t)x) >> 8), BYTE_CAST(((uint32_t)x) >> 0)

#define BE64(x) \
  BYTE_CAST(((uint64_t)x) >> 56), BYTE_CAST(((uint64_t)x) >> 48),     \
      BYTE_CAST(((uint64_t)x) >> 40), BYTE_CAST(((uint64_t)x) >> 32), \
      BYTE_CAST(((uint64_t)x) >> 24), BYTE_CAST(((uint64_t)x) >> 16), \
      BYTE_CAST(((uint64_t)x) >> 8), BYTE_CAST(((uint64_t)x) >> 0)

// Declare a N-bit integer as a this-endian (local machine) sequence of bytes
#ifndef BIG_ENDIAN
#define BIG_ENDIAN 1
#endif  // BIG_ENDIAN

#if BIG_ENDIAN
#define TE16 BE16
#define TE32 BE32
#define TE64 BE64
#else  // !BIG_ENDIAN
#define TE16 LE16
#define TE32 LE32
#define TE64 LE64
#endif  // !BIG_ENDIAN

///////////////////////////////////////////////////////////////////////////////

// Helpers for determining if X/screencasting is available (on linux).

#define MAYBE_SKIP_SCREENCAST_TEST() \
  if (!testing::IsScreencastingAvailable()) { \
    LOG(LS_WARNING) << "Skipping test, since it doesn't have the requisite " \
                    << "X environment for screen capture."; \
    return; \
  } \

#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID)
struct XDisplay {
  XDisplay() : display_(XOpenDisplay(NULL)) { }
  ~XDisplay() { if (display_) XCloseDisplay(display_); }
  bool IsValid() const { return display_ != NULL; }
  operator Display*() { return display_; }
 private:
  Display* display_;
};
#endif

// Returns true if screencasting is available. When false, anything that uses
// screencasting features may fail.
inline bool IsScreencastingAvailable() {
#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID)
  XDisplay display;
  if (!display.IsValid()) {
    LOG(LS_WARNING) << "No X Display available.";
    return false;
  }
  int ignored_int, major_version, minor_version;
  if (!XRRQueryExtension(display, &ignored_int, &ignored_int) ||
      !XRRQueryVersion(display, &major_version, &minor_version) ||
      major_version < 1 ||
      (major_version < 2 && minor_version < 3)) {
    LOG(LS_WARNING) << "XRandr version: " << major_version << "."
                    << minor_version;
    LOG(LS_WARNING) << "XRandr is not supported or is too old (pre 1.3).";
    return false;
  }
#endif
  return true;
}
}  // namespace testing

#endif  // WEBRTC_BASE_TESTUTILS_H__
