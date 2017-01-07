/*
 *  Copyright 2009 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "webrtc/base/gunit.h"
#include "webrtc/base/socket_unittest.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/win32socketserver.h"

namespace rtc {

// Test that Win32SocketServer::Wait works as expected.
TEST(Win32SocketServerTest, TestWait) {
  Win32SocketServer server(NULL);
  uint32_t start = Time();
  server.Wait(1000, true);
  EXPECT_GE(TimeSince(start), 1000);
}

// Test that Win32Socket::Pump does not touch general Windows messages.
TEST(Win32SocketServerTest, TestPump) {
  Win32SocketServer server(NULL);
  SocketServerScope scope(&server);
  EXPECT_EQ(TRUE, PostMessage(NULL, WM_USER, 999, 0));
  server.Pump();
  MSG msg;
  EXPECT_EQ(TRUE, PeekMessage(&msg, NULL, WM_USER, 0, PM_REMOVE));
  EXPECT_EQ(WM_USER, msg.message);
  EXPECT_EQ(999, msg.wParam);
}

// Test that Win32Socket passes all the generic Socket tests.
class Win32SocketTest : public SocketTest {
 protected:
  Win32SocketTest() : server_(NULL), scope_(&server_) {}
  Win32SocketServer server_;
  SocketServerScope scope_;
};

TEST_F(Win32SocketTest, TestConnectIPv4) {
  SocketTest::TestConnectIPv4();
}

TEST_F(Win32SocketTest, TestConnectIPv6) {
  SocketTest::TestConnectIPv6();
}

TEST_F(Win32SocketTest, TestConnectWithDnsLookupIPv4) {
  SocketTest::TestConnectWithDnsLookupIPv4();
}

TEST_F(Win32SocketTest, TestConnectWithDnsLookupIPv6) {
  SocketTest::TestConnectWithDnsLookupIPv6();
}

TEST_F(Win32SocketTest, TestConnectFailIPv4) {
  SocketTest::TestConnectFailIPv4();
}

TEST_F(Win32SocketTest, TestConnectFailIPv6) {
  SocketTest::TestConnectFailIPv6();
}

TEST_F(Win32SocketTest, TestConnectWithDnsLookupFailIPv4) {
  SocketTest::TestConnectWithDnsLookupFailIPv4();
}

TEST_F(Win32SocketTest, TestConnectWithDnsLookupFailIPv6) {
  SocketTest::TestConnectWithDnsLookupFailIPv6();
}

TEST_F(Win32SocketTest, TestConnectWithClosedSocketIPv4) {
  SocketTest::TestConnectWithClosedSocketIPv4();
}

TEST_F(Win32SocketTest, TestConnectWithClosedSocketIPv6) {
  SocketTest::TestConnectWithClosedSocketIPv6();
}

TEST_F(Win32SocketTest, TestConnectWhileNotClosedIPv4) {
  SocketTest::TestConnectWhileNotClosedIPv4();
}

TEST_F(Win32SocketTest, TestConnectWhileNotClosedIPv6) {
  SocketTest::TestConnectWhileNotClosedIPv6();
}

TEST_F(Win32SocketTest, TestServerCloseDuringConnectIPv4) {
  SocketTest::TestServerCloseDuringConnectIPv4();
}

TEST_F(Win32SocketTest, TestServerCloseDuringConnectIPv6) {
  SocketTest::TestServerCloseDuringConnectIPv6();
}

TEST_F(Win32SocketTest, TestClientCloseDuringConnectIPv4) {
  SocketTest::TestClientCloseDuringConnectIPv4();
}

TEST_F(Win32SocketTest, TestClientCloseDuringConnectIPv6) {
  SocketTest::TestClientCloseDuringConnectIPv6();
}

TEST_F(Win32SocketTest, TestServerCloseIPv4) {
  SocketTest::TestServerCloseIPv4();
}

TEST_F(Win32SocketTest, TestServerCloseIPv6) {
  SocketTest::TestServerCloseIPv6();
}

TEST_F(Win32SocketTest, TestCloseInClosedCallbackIPv4) {
  SocketTest::TestCloseInClosedCallbackIPv4();
}

TEST_F(Win32SocketTest, TestCloseInClosedCallbackIPv6) {
  SocketTest::TestCloseInClosedCallbackIPv6();
}

TEST_F(Win32SocketTest, TestSocketServerWaitIPv4) {
  SocketTest::TestSocketServerWaitIPv4();
}

TEST_F(Win32SocketTest, TestSocketServerWaitIPv6) {
  SocketTest::TestSocketServerWaitIPv6();
}

TEST_F(Win32SocketTest, TestTcpIPv4) {
  SocketTest::TestTcpIPv4();
}

TEST_F(Win32SocketTest, TestTcpIPv6) {
  SocketTest::TestTcpIPv6();
}

TEST_F(Win32SocketTest, TestUdpIPv4) {
  SocketTest::TestUdpIPv4();
}

TEST_F(Win32SocketTest, TestUdpIPv6) {
  SocketTest::TestUdpIPv6();
}

TEST_F(Win32SocketTest, TestGetSetOptionsIPv4) {
  SocketTest::TestGetSetOptionsIPv4();
}

TEST_F(Win32SocketTest, TestGetSetOptionsIPv6) {
  SocketTest::TestGetSetOptionsIPv6();
}

}  // namespace rtc
