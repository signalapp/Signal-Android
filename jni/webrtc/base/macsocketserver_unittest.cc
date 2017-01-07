/*
 *  Copyright 2009 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "webrtc/base/gunit.h"
#include "webrtc/base/socket_unittest.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/macsocketserver.h"

namespace rtc {

class WakeThread : public Thread {
 public:
  WakeThread(SocketServer* ss) : ss_(ss) {
  }
  virtual ~WakeThread() {
    Stop();
  }
  void Run() {
    ss_->WakeUp();
  }
 private:
  SocketServer* ss_;
};

#ifndef CARBON_DEPRECATED

// Test that MacCFSocketServer::Wait works as expected.
TEST(MacCFSocketServerTest, TestWait) {
  MacCFSocketServer server;
  uint32_t start = Time();
  server.Wait(1000, true);
  EXPECT_GE(TimeSince(start), 1000);
}

// Test that MacCFSocketServer::Wakeup works as expected.
TEST(MacCFSocketServerTest, TestWakeup) {
  MacCFSocketServer server;
  WakeThread thread(&server);
  uint32_t start = Time();
  thread.Start();
  server.Wait(10000, true);
  EXPECT_LT(TimeSince(start), 10000);
}

// Test that MacCarbonSocketServer::Wait works as expected.
TEST(MacCarbonSocketServerTest, TestWait) {
  MacCarbonSocketServer server;
  uint32_t start = Time();
  server.Wait(1000, true);
  EXPECT_GE(TimeSince(start), 1000);
}

// Test that MacCarbonSocketServer::Wakeup works as expected.
TEST(MacCarbonSocketServerTest, TestWakeup) {
  MacCarbonSocketServer server;
  WakeThread thread(&server);
  uint32_t start = Time();
  thread.Start();
  server.Wait(10000, true);
  EXPECT_LT(TimeSince(start), 10000);
}

// Test that MacCarbonAppSocketServer::Wait works as expected.
TEST(MacCarbonAppSocketServerTest, TestWait) {
  MacCarbonAppSocketServer server;
  uint32_t start = Time();
  server.Wait(1000, true);
  EXPECT_GE(TimeSince(start), 1000);
}

// Test that MacCarbonAppSocketServer::Wakeup works as expected.
TEST(MacCarbonAppSocketServerTest, TestWakeup) {
  MacCarbonAppSocketServer server;
  WakeThread thread(&server);
  uint32_t start = Time();
  thread.Start();
  server.Wait(10000, true);
  EXPECT_LT(TimeSince(start), 10000);
}

#endif

// Test that MacAsyncSocket passes all the generic Socket tests.
class MacAsyncSocketTest : public SocketTest {
 protected:
  MacAsyncSocketTest()
      : server_(CreateSocketServer()),
        scope_(server_.get()) {}
  // Override for other implementations of MacBaseSocketServer.
  virtual MacBaseSocketServer* CreateSocketServer() {
    return new MacCFSocketServer();
  };
  std::unique_ptr<MacBaseSocketServer> server_;
  SocketServerScope scope_;
};

TEST_F(MacAsyncSocketTest, TestConnectIPv4) {
  SocketTest::TestConnectIPv4();
}

TEST_F(MacAsyncSocketTest, TestConnectIPv6) {
  SocketTest::TestConnectIPv6();
}

TEST_F(MacAsyncSocketTest, TestConnectWithDnsLookupIPv4) {
  SocketTest::TestConnectWithDnsLookupIPv4();
}

TEST_F(MacAsyncSocketTest, TestConnectWithDnsLookupIPv6) {
  SocketTest::TestConnectWithDnsLookupIPv6();
}

// BUG=https://code.google.com/p/webrtc/issues/detail?id=2272
TEST_F(MacAsyncSocketTest, DISABLED_TestConnectFailIPv4) {
  SocketTest::TestConnectFailIPv4();
}

// Flaky. See webrtc:4738.
TEST_F(MacAsyncSocketTest, DISABLED_TestConnectFailIPv6) {
  SocketTest::TestConnectFailIPv6();
}

// Reenable once we have mac async dns
TEST_F(MacAsyncSocketTest, DISABLED_TestConnectWithDnsLookupFailIPv4) {
  SocketTest::TestConnectWithDnsLookupFailIPv4();
}

TEST_F(MacAsyncSocketTest, DISABLED_TestConnectWithDnsLookupFailIPv6) {
  SocketTest::TestConnectWithDnsLookupFailIPv6();
}

TEST_F(MacAsyncSocketTest, TestConnectWithClosedSocketIPv4) {
  SocketTest::TestConnectWithClosedSocketIPv4();
}

TEST_F(MacAsyncSocketTest, TestConnectWithClosedSocketIPv6) {
  SocketTest::TestConnectWithClosedSocketIPv6();
}

// Flaky at the moment (10% failure rate).  Seems the client doesn't get
// signalled in a timely manner...
TEST_F(MacAsyncSocketTest, DISABLED_TestServerCloseDuringConnectIPv4) {
  SocketTest::TestServerCloseDuringConnectIPv4();
}

TEST_F(MacAsyncSocketTest, DISABLED_TestServerCloseDuringConnectIPv6) {
  SocketTest::TestServerCloseDuringConnectIPv6();
}
// Flaky at the moment (0.5% failure rate).  Seems the client doesn't get
// signalled in a timely manner...
TEST_F(MacAsyncSocketTest, TestClientCloseDuringConnectIPv4) {
  SocketTest::TestClientCloseDuringConnectIPv4();
}

TEST_F(MacAsyncSocketTest, TestClientCloseDuringConnectIPv6) {
  SocketTest::TestClientCloseDuringConnectIPv6();
}

TEST_F(MacAsyncSocketTest, TestServerCloseIPv4) {
  SocketTest::TestServerCloseIPv4();
}

TEST_F(MacAsyncSocketTest, TestServerCloseIPv6) {
  SocketTest::TestServerCloseIPv6();
}

TEST_F(MacAsyncSocketTest, TestCloseInClosedCallbackIPv4) {
  SocketTest::TestCloseInClosedCallbackIPv4();
}

TEST_F(MacAsyncSocketTest, TestCloseInClosedCallbackIPv6) {
  SocketTest::TestCloseInClosedCallbackIPv6();
}

TEST_F(MacAsyncSocketTest, TestSocketServerWaitIPv4) {
  SocketTest::TestSocketServerWaitIPv4();
}

TEST_F(MacAsyncSocketTest, TestSocketServerWaitIPv6) {
  SocketTest::TestSocketServerWaitIPv6();
}

TEST_F(MacAsyncSocketTest, TestTcpIPv4) {
  SocketTest::TestTcpIPv4();
}

TEST_F(MacAsyncSocketTest, TestTcpIPv6) {
  SocketTest::TestTcpIPv6();
}

TEST_F(MacAsyncSocketTest, TestSingleFlowControlCallbackIPv4) {
  SocketTest::TestSingleFlowControlCallbackIPv4();
}

TEST_F(MacAsyncSocketTest, TestSingleFlowControlCallbackIPv6) {
  SocketTest::TestSingleFlowControlCallbackIPv6();
}

TEST_F(MacAsyncSocketTest, DISABLED_TestUdpIPv4) {
  SocketTest::TestUdpIPv4();
}

TEST_F(MacAsyncSocketTest, DISABLED_TestUdpIPv6) {
  SocketTest::TestUdpIPv6();
}

TEST_F(MacAsyncSocketTest, DISABLED_TestGetSetOptionsIPv4) {
  SocketTest::TestGetSetOptionsIPv4();
}

TEST_F(MacAsyncSocketTest, DISABLED_TestGetSetOptionsIPv6) {
  SocketTest::TestGetSetOptionsIPv6();
}

#ifndef CARBON_DEPRECATED
class MacCarbonAppAsyncSocketTest : public MacAsyncSocketTest {
  virtual MacBaseSocketServer* CreateSocketServer() {
    return new MacCarbonAppSocketServer();
  };
};

TEST_F(MacCarbonAppAsyncSocketTest, TestSocketServerWaitIPv4) {
  SocketTest::TestSocketServerWaitIPv4();
}

TEST_F(MacCarbonAppAsyncSocketTest, TestSocketServerWaitIPv6) {
  SocketTest::TestSocketServerWaitIPv6();
}
#endif
}  // namespace rtc
