/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <string>

#include "webrtc/base/asynctcpsocket.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/physicalsocketserver.h"
#include "webrtc/base/virtualsocketserver.h"

namespace rtc {

class AsyncTCPSocketTest
    : public testing::Test,
      public sigslot::has_slots<> {
 public:
  AsyncTCPSocketTest()
      : pss_(new rtc::PhysicalSocketServer),
        vss_(new rtc::VirtualSocketServer(pss_.get())),
        socket_(vss_->CreateAsyncSocket(SOCK_STREAM)),
        tcp_socket_(new AsyncTCPSocket(socket_, true)),
        ready_to_send_(false) {
    tcp_socket_->SignalReadyToSend.connect(this,
                                           &AsyncTCPSocketTest::OnReadyToSend);
  }

  void OnReadyToSend(rtc::AsyncPacketSocket* socket) {
    ready_to_send_ = true;
  }

 protected:
  std::unique_ptr<PhysicalSocketServer> pss_;
  std::unique_ptr<VirtualSocketServer> vss_;
  AsyncSocket* socket_;
  std::unique_ptr<AsyncTCPSocket> tcp_socket_;
  bool ready_to_send_;
};

TEST_F(AsyncTCPSocketTest, OnWriteEvent) {
  EXPECT_FALSE(ready_to_send_);
  socket_->SignalWriteEvent(socket_);
  EXPECT_TRUE(ready_to_send_);
}

}  // namespace rtc
