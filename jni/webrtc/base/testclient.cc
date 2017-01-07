/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/testclient.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/timeutils.h"

namespace rtc {

// DESIGN: Each packet received is put it into a list of packets.
//         Callers can retrieve received packets from any thread by calling
//         NextPacket.

TestClient::TestClient(AsyncPacketSocket* socket)
    : socket_(socket), ready_to_send_(false), prev_packet_timestamp_(-1) {
  packets_ = new std::vector<Packet*>();
  socket_->SignalReadPacket.connect(this, &TestClient::OnPacket);
  socket_->SignalReadyToSend.connect(this, &TestClient::OnReadyToSend);
}

TestClient::~TestClient() {
  delete socket_;
  for (unsigned i = 0; i < packets_->size(); i++)
    delete (*packets_)[i];
  delete packets_;
}

bool TestClient::CheckConnState(AsyncPacketSocket::State state) {
  // Wait for our timeout value until the socket reaches the desired state.
  int64_t end = TimeAfter(kTimeoutMs);
  while (socket_->GetState() != state && TimeUntil(end) > 0) {
    Thread::Current()->ProcessMessages(1);
  }
  return (socket_->GetState() == state);
}

int TestClient::Send(const char* buf, size_t size) {
  rtc::PacketOptions options;
  return socket_->Send(buf, size, options);
}

int TestClient::SendTo(const char* buf, size_t size,
                       const SocketAddress& dest) {
  rtc::PacketOptions options;
  return socket_->SendTo(buf, size, dest, options);
}

TestClient::Packet* TestClient::NextPacket(int timeout_ms) {
  // If no packets are currently available, we go into a get/dispatch loop for
  // at most timeout_ms.  If, during the loop, a packet arrives, then we can
  // stop early and return it.

  // Note that the case where no packet arrives is important.  We often want to
  // test that a packet does not arrive.

  // Note also that we only try to pump our current thread's message queue.
  // Pumping another thread's queue could lead to messages being dispatched from
  // the wrong thread to non-thread-safe objects.

  int64_t end = TimeAfter(timeout_ms);
  while (TimeUntil(end) > 0) {
    {
      CritScope cs(&crit_);
      if (packets_->size() != 0) {
        break;
      }
    }
    Thread::Current()->ProcessMessages(1);
  }

  // Return the first packet placed in the queue.
  Packet* packet = NULL;
  CritScope cs(&crit_);
  if (packets_->size() > 0) {
    packet = packets_->front();
    packets_->erase(packets_->begin());
  }

  return packet;
}

bool TestClient::CheckNextPacket(const char* buf, size_t size,
                                 SocketAddress* addr) {
  bool res = false;
  Packet* packet = NextPacket(kTimeoutMs);
  if (packet) {
    res = (packet->size == size && memcmp(packet->buf, buf, size) == 0 &&
           CheckTimestamp(packet->packet_time.timestamp));
    if (addr)
      *addr = packet->addr;
    delete packet;
  }
  return res;
}

bool TestClient::CheckTimestamp(int64_t packet_timestamp) {
  bool res = true;
  if (packet_timestamp == -1) {
    res = false;
  }
  if (prev_packet_timestamp_ != -1) {
    if (packet_timestamp < prev_packet_timestamp_) {
      res = false;
    }
  }
  prev_packet_timestamp_ = packet_timestamp;
  return res;
}

bool TestClient::CheckNoPacket() {
  bool res;
  Packet* packet = NextPacket(kNoPacketTimeoutMs);
  res = (packet == NULL);
  delete packet;
  return res;
}

int TestClient::GetError() {
  return socket_->GetError();
}

int TestClient::SetOption(Socket::Option opt, int value) {
  return socket_->SetOption(opt, value);
}

bool TestClient::ready_to_send() const {
  return ready_to_send_;
}

void TestClient::OnPacket(AsyncPacketSocket* socket, const char* buf,
                          size_t size, const SocketAddress& remote_addr,
                          const PacketTime& packet_time) {
  CritScope cs(&crit_);
  packets_->push_back(new Packet(remote_addr, buf, size, packet_time));
}

void TestClient::OnReadyToSend(AsyncPacketSocket* socket) {
  ready_to_send_ = true;
}

TestClient::Packet::Packet(const SocketAddress& a,
                           const char* b,
                           size_t s,
                           const PacketTime& packet_time)
    : addr(a), buf(0), size(s), packet_time(packet_time) {
  buf = new char[size];
  memcpy(buf, b, size);
}

TestClient::Packet::Packet(const Packet& p)
    : addr(p.addr), buf(0), size(p.size), packet_time(p.packet_time) {
  buf = new char[size];
  memcpy(buf, p.buf, size);
}

TestClient::Packet::~Packet() {
  delete[] buf;
}

}  // namespace rtc
