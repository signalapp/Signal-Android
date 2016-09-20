/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TESTCLIENT_H_
#define WEBRTC_BASE_TESTCLIENT_H_

#include <vector>
#include "webrtc/base/asyncudpsocket.h"
#include "webrtc/base/constructormagic.h"
#include "webrtc/base/criticalsection.h"

namespace rtc {

// A simple client that can send TCP or UDP data and check that it receives
// what it expects to receive. Useful for testing server functionality.
class TestClient : public sigslot::has_slots<> {
 public:
  // Records the contents of a packet that was received.
  struct Packet {
    Packet(const SocketAddress& a,
           const char* b,
           size_t s,
           const PacketTime& packet_time);
    Packet(const Packet& p);
    virtual ~Packet();

    SocketAddress addr;
    char*  buf;
    size_t size;
    PacketTime packet_time;
  };

  // Default timeout for NextPacket reads.
  static const int kTimeoutMs = 5000;

  // Creates a client that will send and receive with the given socket and
  // will post itself messages with the given thread.
  explicit TestClient(AsyncPacketSocket* socket);
  ~TestClient() override;

  SocketAddress address() const { return socket_->GetLocalAddress(); }
  SocketAddress remote_address() const { return socket_->GetRemoteAddress(); }

  // Checks that the socket moves to the specified connect state.
  bool CheckConnState(AsyncPacketSocket::State state);

  // Checks that the socket is connected to the remote side.
  bool CheckConnected() {
    return CheckConnState(AsyncPacketSocket::STATE_CONNECTED);
  }

  // Sends using the clients socket.
  int Send(const char* buf, size_t size);

  // Sends using the clients socket to the given destination.
  int SendTo(const char* buf, size_t size, const SocketAddress& dest);

  // Returns the next packet received by the client or 0 if none is received
  // within the specified timeout. The caller must delete the packet
  // when done with it.
  Packet* NextPacket(int timeout_ms);

  // Checks that the next packet has the given contents. Returns the remote
  // address that the packet was sent from.
  bool CheckNextPacket(const char* buf, size_t len, SocketAddress* addr);

  // Checks that no packets have arrived or will arrive in the next second.
  bool CheckNoPacket();

  int GetError();
  int SetOption(Socket::Option opt, int value);

  bool ready_to_send() const;

 private:
  // Timeout for reads when no packet is expected.
  static const int kNoPacketTimeoutMs = 1000;
  // Workaround for the fact that AsyncPacketSocket::GetConnState doesn't exist.
  Socket::ConnState GetState();
  // Slot for packets read on the socket.
  void OnPacket(AsyncPacketSocket* socket, const char* buf, size_t len,
                const SocketAddress& remote_addr,
                const PacketTime& packet_time);
  void OnReadyToSend(AsyncPacketSocket* socket);
  bool CheckTimestamp(int64_t packet_timestamp);

  CriticalSection crit_;
  AsyncPacketSocket* socket_;
  std::vector<Packet*>* packets_;
  bool ready_to_send_;
  int64_t prev_packet_timestamp_;
  RTC_DISALLOW_COPY_AND_ASSIGN(TestClient);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_TESTCLIENT_H_
