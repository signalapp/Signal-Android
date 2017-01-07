/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_ASYNCPACKETSOCKET_H_
#define WEBRTC_BASE_ASYNCPACKETSOCKET_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/dscp.h"
#include "webrtc/base/sigslot.h"
#include "webrtc/base/socket.h"
#include "webrtc/base/timeutils.h"

namespace rtc {

// This structure holds the info needed to update the packet send time header
// extension, including the information needed to update the authentication tag
// after changing the value.
struct PacketTimeUpdateParams {
  PacketTimeUpdateParams();
  ~PacketTimeUpdateParams();

  int rtp_sendtime_extension_id;    // extension header id present in packet.
  std::vector<char> srtp_auth_key;  // Authentication key.
  int srtp_auth_tag_len;            // Authentication tag length.
  int64_t srtp_packet_index;        // Required for Rtp Packet authentication.
};

// This structure holds meta information for the packet which is about to send
// over network.
struct PacketOptions {
  PacketOptions() : dscp(DSCP_NO_CHANGE), packet_id(-1) {}
  explicit PacketOptions(DiffServCodePoint dscp) : dscp(dscp), packet_id(-1) {}

  DiffServCodePoint dscp;
  int packet_id;  // 16 bits, -1 represents "not set".
  PacketTimeUpdateParams packet_time_params;
};

// This structure will have the information about when packet is actually
// received by socket.
struct PacketTime {
  PacketTime() : timestamp(-1), not_before(-1) {}
  PacketTime(int64_t timestamp, int64_t not_before)
      : timestamp(timestamp), not_before(not_before) {}

  int64_t timestamp;   // Receive time after socket delivers the data.

  // Earliest possible time the data could have arrived, indicating the
  // potential error in the |timestamp| value, in case the system, is busy. For
  // example, the time of the last select() call.
  // If unknown, this value will be set to zero.
  int64_t not_before;
};

inline PacketTime CreatePacketTime(int64_t not_before) {
  return PacketTime(TimeMicros(), not_before);
}

// Provides the ability to receive packets asynchronously. Sends are not
// buffered since it is acceptable to drop packets under high load.
class AsyncPacketSocket : public sigslot::has_slots<> {
 public:
  enum State {
    STATE_CLOSED,
    STATE_BINDING,
    STATE_BOUND,
    STATE_CONNECTING,
    STATE_CONNECTED
  };

  AsyncPacketSocket();
  ~AsyncPacketSocket() override;

  // Returns current local address. Address may be set to NULL if the
  // socket is not bound yet (GetState() returns STATE_BINDING).
  virtual SocketAddress GetLocalAddress() const = 0;

  // Returns remote address. Returns zeroes if this is not a client TCP socket.
  virtual SocketAddress GetRemoteAddress() const = 0;

  // Send a packet.
  virtual int Send(const void *pv, size_t cb, const PacketOptions& options) = 0;
  virtual int SendTo(const void *pv, size_t cb, const SocketAddress& addr,
                     const PacketOptions& options) = 0;

  // Close the socket.
  virtual int Close() = 0;

  // Returns current state of the socket.
  virtual State GetState() const = 0;

  // Get/set options.
  virtual int GetOption(Socket::Option opt, int* value) = 0;
  virtual int SetOption(Socket::Option opt, int value) = 0;

  // Get/Set current error.
  // TODO: Remove SetError().
  virtual int GetError() const = 0;
  virtual void SetError(int error) = 0;

  // Emitted each time a packet is read. Used only for UDP and
  // connected TCP sockets.
  sigslot::signal5<AsyncPacketSocket*, const char*, size_t,
                   const SocketAddress&,
                   const PacketTime&> SignalReadPacket;

  // Emitted each time a packet is sent.
  sigslot::signal2<AsyncPacketSocket*, const SentPacket&> SignalSentPacket;

  // Emitted when the socket is currently able to send.
  sigslot::signal1<AsyncPacketSocket*> SignalReadyToSend;

  // Emitted after address for the socket is allocated, i.e. binding
  // is finished. State of the socket is changed from BINDING to BOUND
  // (for UDP and server TCP sockets) or CONNECTING (for client TCP
  // sockets).
  sigslot::signal2<AsyncPacketSocket*, const SocketAddress&> SignalAddressReady;

  // Emitted for client TCP sockets when state is changed from
  // CONNECTING to CONNECTED.
  sigslot::signal1<AsyncPacketSocket*> SignalConnect;

  // Emitted for client TCP sockets when state is changed from
  // CONNECTED to CLOSED.
  sigslot::signal2<AsyncPacketSocket*, int> SignalClose;

  // Used only for listening TCP sockets.
  sigslot::signal2<AsyncPacketSocket*, AsyncPacketSocket*> SignalNewConnection;

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(AsyncPacketSocket);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_ASYNCPACKETSOCKET_H_
