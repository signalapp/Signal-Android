/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <iomanip>

#include "webrtc/base/asyncsocket.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/socketfactory.h"
#include "webrtc/base/socketpool.h"
#include "webrtc/base/socketstream.h"
#include "webrtc/base/thread.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// StreamCache - Caches a set of open streams, defers creation to a separate
//  StreamPool.
///////////////////////////////////////////////////////////////////////////////

StreamCache::StreamCache(StreamPool* pool) : pool_(pool) {
}

StreamCache::~StreamCache() {
  for (ConnectedList::iterator it = active_.begin(); it != active_.end();
       ++it) {
    delete it->second;
  }
  for (ConnectedList::iterator it = cached_.begin(); it != cached_.end();
       ++it) {
    delete it->second;
  }
}

StreamInterface* StreamCache::RequestConnectedStream(
    const SocketAddress& remote, int* err) {
  LOG_F(LS_VERBOSE) << "(" << remote << ")";
  for (ConnectedList::iterator it = cached_.begin(); it != cached_.end();
       ++it) {
    if (remote == it->first) {
      it->second->SignalEvent.disconnect(this);
      // Move from cached_ to active_
      active_.push_front(*it);
      cached_.erase(it);
      if (err)
        *err = 0;
      LOG_F(LS_VERBOSE) << "Providing cached stream";
      return active_.front().second;
    }
  }
  if (StreamInterface* stream = pool_->RequestConnectedStream(remote, err)) {
    // We track active streams so that we can remember their address
    active_.push_front(ConnectedStream(remote, stream));
    LOG_F(LS_VERBOSE) << "Providing new stream";
    return active_.front().second;
  }
  return NULL;
}

void StreamCache::ReturnConnectedStream(StreamInterface* stream) {
  for (ConnectedList::iterator it = active_.begin(); it != active_.end();
       ++it) {
    if (stream == it->second) {
      LOG_F(LS_VERBOSE) << "(" << it->first << ")";
      if (stream->GetState() == SS_CLOSED) {
        // Return closed streams
        LOG_F(LS_VERBOSE) << "Returning closed stream";
        pool_->ReturnConnectedStream(it->second);
      } else {
        // Monitor open streams
        stream->SignalEvent.connect(this, &StreamCache::OnStreamEvent);
        LOG_F(LS_VERBOSE) << "Caching stream";
        cached_.push_front(*it);
      }
      active_.erase(it);
      return;
    }
  }
  ASSERT(false);
}

void StreamCache::OnStreamEvent(StreamInterface* stream, int events, int err) {
  if ((events & SE_CLOSE) == 0) {
    LOG_F(LS_WARNING) << "(" << events << ", " << err
                      << ") received non-close event";
    return;
  }
  for (ConnectedList::iterator it = cached_.begin(); it != cached_.end();
       ++it) {
    if (stream == it->second) {
      LOG_F(LS_VERBOSE) << "(" << it->first << ")";
      // We don't cache closed streams, so return it.
      it->second->SignalEvent.disconnect(this);
      LOG_F(LS_VERBOSE) << "Returning closed stream";
      pool_->ReturnConnectedStream(it->second);
      cached_.erase(it);
      return;
    }
  }
  ASSERT(false);
}

//////////////////////////////////////////////////////////////////////
// NewSocketPool
//////////////////////////////////////////////////////////////////////

NewSocketPool::NewSocketPool(SocketFactory* factory) : factory_(factory) {
}

NewSocketPool::~NewSocketPool() {
}

StreamInterface*
NewSocketPool::RequestConnectedStream(const SocketAddress& remote, int* err) {
  AsyncSocket* socket =
      factory_->CreateAsyncSocket(remote.family(), SOCK_STREAM);
  if (!socket) {
    if (err)
      *err = -1;
    return NULL;
  }
  if ((socket->Connect(remote) != 0) && !socket->IsBlocking()) {
    if (err)
      *err = socket->GetError();
    delete socket;
    return NULL;
  }
  if (err)
    *err = 0;
  return new SocketStream(socket);
}

void
NewSocketPool::ReturnConnectedStream(StreamInterface* stream) {
  Thread::Current()->Dispose(stream);
}

//////////////////////////////////////////////////////////////////////
// ReuseSocketPool
//////////////////////////////////////////////////////////////////////

ReuseSocketPool::ReuseSocketPool(SocketFactory* factory)
: factory_(factory), stream_(NULL), checked_out_(false) {
}

ReuseSocketPool::~ReuseSocketPool() {
  ASSERT(!checked_out_);
  delete stream_;
}

StreamInterface*
ReuseSocketPool::RequestConnectedStream(const SocketAddress& remote, int* err) {
  // Only one socket can be used from this "pool" at a time
  ASSERT(!checked_out_);
  if (!stream_) {
    LOG_F(LS_VERBOSE) << "Creating new socket";
    int family = remote.family();
    // TODO: Deal with this when we/I clean up DNS resolution.
    if (remote.IsUnresolvedIP()) {
      family = AF_INET;
    }
    AsyncSocket* socket =
        factory_->CreateAsyncSocket(family, SOCK_STREAM);
    if (!socket) {
      if (err)
        *err = -1;
      return NULL;
    }
    stream_ = new SocketStream(socket);
  }
  if ((stream_->GetState() == SS_OPEN) && (remote == remote_)) {
    LOG_F(LS_VERBOSE) << "Reusing connection to: " << remote_;
  } else {
    remote_ = remote;
    stream_->Close();
    if ((stream_->GetSocket()->Connect(remote_) != 0)
        && !stream_->GetSocket()->IsBlocking()) {
      if (err)
        *err = stream_->GetSocket()->GetError();
      return NULL;
    } else {
      LOG_F(LS_VERBOSE) << "Opening connection to: " << remote_;
    }
  }
  stream_->SignalEvent.disconnect(this);
  checked_out_ = true;
  if (err)
    *err = 0;
  return stream_;
}

void
ReuseSocketPool::ReturnConnectedStream(StreamInterface* stream) {
  ASSERT(stream == stream_);
  ASSERT(checked_out_);
  checked_out_ = false;
  // Until the socket is reused, monitor it to determine if it closes.
  stream_->SignalEvent.connect(this, &ReuseSocketPool::OnStreamEvent);
}

void
ReuseSocketPool::OnStreamEvent(StreamInterface* stream, int events, int err) {
  ASSERT(stream == stream_);
  ASSERT(!checked_out_);

  // If the stream was written to and then immediately returned to us then
  // we may get a writable notification for it, which we should ignore.
  if (events == SE_WRITE) {
    LOG_F(LS_VERBOSE) << "Pooled Socket unexpectedly writable: ignoring";
    return;
  }

  // If the peer sent data, we can't process it, so drop the connection.
  // If the socket has closed, clean it up.
  // In either case, we'll reconnect it the next time it is used.
  ASSERT(0 != (events & (SE_READ|SE_CLOSE)));
  if (0 != (events & SE_CLOSE)) {
    LOG_F(LS_VERBOSE) << "Connection closed with error: " << err;
  } else {
    LOG_F(LS_VERBOSE) << "Pooled Socket unexpectedly readable: closing";
  }
  stream_->Close();
}

///////////////////////////////////////////////////////////////////////////////
// LoggingPoolAdapter - Adapts a StreamPool to supply streams with attached
// LoggingAdapters.
///////////////////////////////////////////////////////////////////////////////

LoggingPoolAdapter::LoggingPoolAdapter(
    StreamPool* pool, LoggingSeverity level, const std::string& label,
    bool binary_mode)
  : pool_(pool), level_(level), label_(label), binary_mode_(binary_mode) {
}

LoggingPoolAdapter::~LoggingPoolAdapter() {
  for (StreamList::iterator it = recycle_bin_.begin();
       it != recycle_bin_.end(); ++it) {
    delete *it;
  }
}

StreamInterface* LoggingPoolAdapter::RequestConnectedStream(
    const SocketAddress& remote, int* err) {
  if (StreamInterface* stream = pool_->RequestConnectedStream(remote, err)) {
    ASSERT(SS_CLOSED != stream->GetState());
    std::stringstream ss;
    ss << label_ << "(0x" << std::setfill('0') << std::hex << std::setw(8)
       << stream << ")";
    LOG_V(level_) << ss.str()
                  << ((SS_OPEN == stream->GetState()) ? " Connected"
                                                      : " Connecting")
                  << " to " << remote;
    if (recycle_bin_.empty()) {
      return new LoggingAdapter(stream, level_, ss.str(), binary_mode_);
    }
    LoggingAdapter* logging = recycle_bin_.front();
    recycle_bin_.pop_front();
    logging->set_label(ss.str());
    logging->Attach(stream);
    return logging;
  }
  return NULL;
}

void LoggingPoolAdapter::ReturnConnectedStream(StreamInterface* stream) {
  LoggingAdapter* logging = static_cast<LoggingAdapter*>(stream);
  pool_->ReturnConnectedStream(logging->Detach());
  recycle_bin_.push_back(logging);
}

///////////////////////////////////////////////////////////////////////////////

} // namespace rtc
