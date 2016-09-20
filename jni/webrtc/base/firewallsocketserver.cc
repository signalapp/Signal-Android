/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/firewallsocketserver.h"

#include <assert.h>

#include <algorithm>

#include "webrtc/base/asyncsocket.h"
#include "webrtc/base/logging.h"

namespace rtc {

class FirewallSocket : public AsyncSocketAdapter {
 public:
  FirewallSocket(FirewallSocketServer* server, AsyncSocket* socket, int type)
    : AsyncSocketAdapter(socket), server_(server), type_(type) {
  }

  int Connect(const SocketAddress& addr) override {
    if (type_ == SOCK_STREAM) {
      if (!server_->Check(FP_TCP, GetLocalAddress(), addr)) {
        LOG(LS_VERBOSE) << "FirewallSocket outbound TCP connection from "
                        << GetLocalAddress().ToSensitiveString() << " to "
                        << addr.ToSensitiveString() << " denied";
        // TODO: Handle this asynchronously.
        SetError(EHOSTUNREACH);
        return SOCKET_ERROR;
      }
    }
    return AsyncSocketAdapter::Connect(addr);
  }
  int Send(const void* pv, size_t cb) override {
    return SendTo(pv, cb, GetRemoteAddress());
  }
  int SendTo(const void* pv, size_t cb, const SocketAddress& addr) override {
    if (type_ == SOCK_DGRAM) {
      if (!server_->Check(FP_UDP, GetLocalAddress(), addr)) {
        LOG(LS_VERBOSE) << "FirewallSocket outbound UDP packet from "
                        << GetLocalAddress().ToSensitiveString() << " to "
                        << addr.ToSensitiveString() << " dropped";
        return static_cast<int>(cb);
      }
    }
    return AsyncSocketAdapter::SendTo(pv, cb, addr);
  }
  int Recv(void* pv, size_t cb, int64_t* timestamp) override {
    SocketAddress addr;
    return RecvFrom(pv, cb, &addr, timestamp);
  }
  int RecvFrom(void* pv,
               size_t cb,
               SocketAddress* paddr,
               int64_t* timestamp) override {
    if (type_ == SOCK_DGRAM) {
      while (true) {
        int res = AsyncSocketAdapter::RecvFrom(pv, cb, paddr, timestamp);
        if (res <= 0)
          return res;
        if (server_->Check(FP_UDP, *paddr, GetLocalAddress()))
          return res;
        LOG(LS_VERBOSE) << "FirewallSocket inbound UDP packet from "
                        << paddr->ToSensitiveString() << " to "
                        << GetLocalAddress().ToSensitiveString() << " dropped";
      }
    }
    return AsyncSocketAdapter::RecvFrom(pv, cb, paddr, timestamp);
  }

  int Listen(int backlog) override {
    if (!server_->tcp_listen_enabled()) {
      LOG(LS_VERBOSE) << "FirewallSocket listen attempt denied";
      return -1;
    }

    return AsyncSocketAdapter::Listen(backlog);
  }
  AsyncSocket* Accept(SocketAddress* paddr) override {
    SocketAddress addr;
    while (AsyncSocket* sock = AsyncSocketAdapter::Accept(&addr)) {
      if (server_->Check(FP_TCP, addr, GetLocalAddress())) {
        if (paddr)
          *paddr = addr;
        return sock;
      }
      sock->Close();
      delete sock;
      LOG(LS_VERBOSE) << "FirewallSocket inbound TCP connection from "
                      << addr.ToSensitiveString() << " to "
                      << GetLocalAddress().ToSensitiveString() << " denied";
    }
    return 0;
  }

 private:
  FirewallSocketServer* server_;
  int type_;
};

FirewallSocketServer::FirewallSocketServer(SocketServer* server,
                                           FirewallManager* manager,
                                           bool should_delete_server)
    : server_(server), manager_(manager),
      should_delete_server_(should_delete_server),
      udp_sockets_enabled_(true), tcp_sockets_enabled_(true),
      tcp_listen_enabled_(true) {
  if (manager_)
    manager_->AddServer(this);
}

FirewallSocketServer::~FirewallSocketServer() {
  if (manager_)
    manager_->RemoveServer(this);

  if (server_ && should_delete_server_) {
    delete server_;
    server_ = NULL;
  }
}

void FirewallSocketServer::AddRule(bool allow, FirewallProtocol p,
                                   FirewallDirection d,
                                   const SocketAddress& addr) {
  SocketAddress any;
  if (d == FD_IN || d == FD_ANY) {
    AddRule(allow, p, any, addr);
  }
  if (d == FD_OUT || d == FD_ANY) {
    AddRule(allow, p, addr, any);
  }
}


void FirewallSocketServer::AddRule(bool allow, FirewallProtocol p,
                                   const SocketAddress& src,
                                   const SocketAddress& dst) {
  Rule r;
  r.allow = allow;
  r.p = p;
  r.src = src;
  r.dst = dst;
  CritScope scope(&crit_);
  rules_.push_back(r);
}

void FirewallSocketServer::ClearRules() {
  CritScope scope(&crit_);
  rules_.clear();
}

bool FirewallSocketServer::Check(FirewallProtocol p,
                                 const SocketAddress& src,
                                 const SocketAddress& dst) {
  CritScope scope(&crit_);
  for (size_t i = 0; i < rules_.size(); ++i) {
    const Rule& r = rules_[i];
    if ((r.p != p) && (r.p != FP_ANY))
      continue;
    if ((r.src.ipaddr() != src.ipaddr()) && !r.src.IsNil())
      continue;
    if ((r.src.port() != src.port()) && (r.src.port() != 0))
      continue;
    if ((r.dst.ipaddr() != dst.ipaddr()) && !r.dst.IsNil())
      continue;
    if ((r.dst.port() != dst.port()) && (r.dst.port() != 0))
      continue;
    return r.allow;
  }
  return true;
}

Socket* FirewallSocketServer::CreateSocket(int type) {
  return CreateSocket(AF_INET, type);
}

Socket* FirewallSocketServer::CreateSocket(int family, int type) {
  return WrapSocket(server_->CreateAsyncSocket(family, type), type);
}

AsyncSocket* FirewallSocketServer::CreateAsyncSocket(int type) {
  return CreateAsyncSocket(AF_INET, type);
}

AsyncSocket* FirewallSocketServer::CreateAsyncSocket(int family, int type) {
  return WrapSocket(server_->CreateAsyncSocket(family, type), type);
}

void FirewallSocketServer::SetMessageQueue(MessageQueue* queue) {
  server_->SetMessageQueue(queue);
}

bool FirewallSocketServer::Wait(int cms, bool process_io) {
  return server_->Wait(cms, process_io);
}

void FirewallSocketServer::WakeUp() {
  return server_->WakeUp();
}

AsyncSocket* FirewallSocketServer::WrapSocket(AsyncSocket* sock, int type) {
  if (!sock ||
      (type == SOCK_STREAM && !tcp_sockets_enabled_) ||
      (type == SOCK_DGRAM && !udp_sockets_enabled_)) {
    LOG(LS_VERBOSE) << "FirewallSocketServer socket creation denied";
    delete sock;
    return NULL;
  }
  return new FirewallSocket(this, sock, type);
}

FirewallManager::FirewallManager() {
}

FirewallManager::~FirewallManager() {
  assert(servers_.empty());
}

void FirewallManager::AddServer(FirewallSocketServer* server) {
  CritScope scope(&crit_);
  servers_.push_back(server);
}

void FirewallManager::RemoveServer(FirewallSocketServer* server) {
  CritScope scope(&crit_);
  servers_.erase(std::remove(servers_.begin(), servers_.end(), server),
                 servers_.end());
}

void FirewallManager::AddRule(bool allow, FirewallProtocol p,
                              FirewallDirection d, const SocketAddress& addr) {
  CritScope scope(&crit_);
  for (std::vector<FirewallSocketServer*>::const_iterator it =
      servers_.begin(); it != servers_.end(); ++it) {
    (*it)->AddRule(allow, p, d, addr);
  }
}

void FirewallManager::ClearRules() {
  CritScope scope(&crit_);
  for (std::vector<FirewallSocketServer*>::const_iterator it =
      servers_.begin(); it != servers_.end(); ++it) {
    (*it)->ClearRules();
  }
}

}  // namespace rtc
