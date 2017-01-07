/*
 *  Copyright 2007 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "webrtc/base/socket_unittest.h"

#include "webrtc/base/arraysize.h"
#include "webrtc/base/buffer.h"
#include "webrtc/base/asyncudpsocket.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/nethelpers.h"
#include "webrtc/base/socketserver.h"
#include "webrtc/base/testclient.h"
#include "webrtc/base/testutils.h"
#include "webrtc/base/thread.h"

namespace rtc {

#define MAYBE_SKIP_IPV6                             \
  if (!HasIPv6Enabled()) {                          \
    LOG(LS_INFO) << "No IPv6... skipping";          \
    return;                                         \
  }

// Data size to be used in TcpInternal tests.
static const size_t kTcpInternalDataSize = 1024 * 1024;  // bytes

void SocketTest::TestConnectIPv4() {
  ConnectInternal(kIPv4Loopback);
}

void SocketTest::TestConnectIPv6() {
  MAYBE_SKIP_IPV6;
  ConnectInternal(kIPv6Loopback);
}

void SocketTest::TestConnectWithDnsLookupIPv4() {
  ConnectWithDnsLookupInternal(kIPv4Loopback, "localhost");
}

void SocketTest::TestConnectWithDnsLookupIPv6() {
  // TODO: Enable this when DNS resolution supports IPv6.
  LOG(LS_INFO) << "Skipping IPv6 DNS test";
  // ConnectWithDnsLookupInternal(kIPv6Loopback, "localhost6");
}

void SocketTest::TestConnectFailIPv4() {
  ConnectFailInternal(kIPv4Loopback);
}

void SocketTest::TestConnectFailIPv6() {
  MAYBE_SKIP_IPV6;
  ConnectFailInternal(kIPv6Loopback);
}

void SocketTest::TestConnectWithDnsLookupFailIPv4() {
  ConnectWithDnsLookupFailInternal(kIPv4Loopback);
}

void SocketTest::TestConnectWithDnsLookupFailIPv6() {
  MAYBE_SKIP_IPV6;
  ConnectWithDnsLookupFailInternal(kIPv6Loopback);
}

void SocketTest::TestConnectWithClosedSocketIPv4() {
  ConnectWithClosedSocketInternal(kIPv4Loopback);
}

void SocketTest::TestConnectWithClosedSocketIPv6() {
  MAYBE_SKIP_IPV6;
  ConnectWithClosedSocketInternal(kIPv6Loopback);
}

void SocketTest::TestConnectWhileNotClosedIPv4() {
  ConnectWhileNotClosedInternal(kIPv4Loopback);
}

void SocketTest::TestConnectWhileNotClosedIPv6() {
  MAYBE_SKIP_IPV6;
  ConnectWhileNotClosedInternal(kIPv6Loopback);
}

void SocketTest::TestServerCloseDuringConnectIPv4() {
  ServerCloseDuringConnectInternal(kIPv4Loopback);
}

void SocketTest::TestServerCloseDuringConnectIPv6() {
  MAYBE_SKIP_IPV6;
  ServerCloseDuringConnectInternal(kIPv6Loopback);
}

void SocketTest::TestClientCloseDuringConnectIPv4() {
  ClientCloseDuringConnectInternal(kIPv4Loopback);
}

void SocketTest::TestClientCloseDuringConnectIPv6() {
  MAYBE_SKIP_IPV6;
  ClientCloseDuringConnectInternal(kIPv6Loopback);
}

void SocketTest::TestServerCloseIPv4() {
  ServerCloseInternal(kIPv4Loopback);
}

void SocketTest::TestServerCloseIPv6() {
  MAYBE_SKIP_IPV6;
  ServerCloseInternal(kIPv6Loopback);
}

void SocketTest::TestCloseInClosedCallbackIPv4() {
  CloseInClosedCallbackInternal(kIPv4Loopback);
}

void SocketTest::TestCloseInClosedCallbackIPv6() {
  MAYBE_SKIP_IPV6;
  CloseInClosedCallbackInternal(kIPv6Loopback);
}

void SocketTest::TestSocketServerWaitIPv4() {
  SocketServerWaitInternal(kIPv4Loopback);
}

void SocketTest::TestSocketServerWaitIPv6() {
  MAYBE_SKIP_IPV6;
  SocketServerWaitInternal(kIPv6Loopback);
}

void SocketTest::TestTcpIPv4() {
  TcpInternal(kIPv4Loopback, kTcpInternalDataSize, -1);
}

void SocketTest::TestTcpIPv6() {
  MAYBE_SKIP_IPV6;
  TcpInternal(kIPv6Loopback, kTcpInternalDataSize, -1);
}

void SocketTest::TestSingleFlowControlCallbackIPv4() {
  SingleFlowControlCallbackInternal(kIPv4Loopback);
}

void SocketTest::TestSingleFlowControlCallbackIPv6() {
  MAYBE_SKIP_IPV6;
  SingleFlowControlCallbackInternal(kIPv6Loopback);
}

void SocketTest::TestUdpIPv4() {
  UdpInternal(kIPv4Loopback);
}

void SocketTest::TestUdpIPv6() {
  MAYBE_SKIP_IPV6;
  UdpInternal(kIPv6Loopback);
}

void SocketTest::TestUdpReadyToSendIPv4() {
#if !defined(WEBRTC_MAC)
  // TODO(ronghuawu): Enable this test on mac/ios.
  UdpReadyToSend(kIPv4Loopback);
#endif
}

void SocketTest::TestUdpReadyToSendIPv6() {
#if defined(WEBRTC_WIN)
  // TODO(ronghuawu): Enable this test (currently flakey) on mac and linux.
  MAYBE_SKIP_IPV6;
  UdpReadyToSend(kIPv6Loopback);
#endif
}

void SocketTest::TestGetSetOptionsIPv4() {
  GetSetOptionsInternal(kIPv4Loopback);
}

void SocketTest::TestGetSetOptionsIPv6() {
  MAYBE_SKIP_IPV6;
  GetSetOptionsInternal(kIPv6Loopback);
}

void SocketTest::TestSocketRecvTimestamp() {
  SocketRecvTimestamp(kIPv4Loopback);
}

// For unbound sockets, GetLocalAddress / GetRemoteAddress return AF_UNSPEC
// values on Windows, but an empty address of the same family on Linux/MacOS X.
bool IsUnspecOrEmptyIP(const IPAddress& address) {
#if !defined(WEBRTC_WIN)
  return IPIsAny(address);
#else
  return address.family() == AF_UNSPEC;
#endif
}

void SocketTest::ConnectInternal(const IPAddress& loopback) {
  testing::StreamSink sink;
  SocketAddress accept_addr;

  // Create client.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(client.get());
  EXPECT_EQ(AsyncSocket::CS_CLOSED, client->GetState());
  EXPECT_PRED1(IsUnspecOrEmptyIP, client->GetLocalAddress().ipaddr());

  // Create server and listen.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));
  EXPECT_EQ(AsyncSocket::CS_CONNECTING, server->GetState());

  // Ensure no pending server connections, since we haven't done anything yet.
  EXPECT_FALSE(sink.Check(server.get(), testing::SSE_READ));
  EXPECT_TRUE(NULL == server->Accept(&accept_addr));
  EXPECT_TRUE(accept_addr.IsNil());

  // Attempt connect to listening socket.
  EXPECT_EQ(0, client->Connect(server->GetLocalAddress()));
  EXPECT_FALSE(client->GetLocalAddress().IsNil());
  EXPECT_NE(server->GetLocalAddress(), client->GetLocalAddress());

  // Client is connecting, outcome not yet determined.
  EXPECT_EQ(AsyncSocket::CS_CONNECTING, client->GetState());
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_OPEN));
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_CLOSE));

  // Server has pending connection, accept it.
  EXPECT_TRUE_WAIT((sink.Check(server.get(), testing::SSE_READ)), kTimeout);
  std::unique_ptr<AsyncSocket> accepted(server->Accept(&accept_addr));
  ASSERT_TRUE(accepted);
  EXPECT_FALSE(accept_addr.IsNil());
  EXPECT_EQ(accepted->GetRemoteAddress(), accept_addr);

  // Connected from server perspective, check the addresses are correct.
  EXPECT_EQ(AsyncSocket::CS_CONNECTED, accepted->GetState());
  EXPECT_EQ(server->GetLocalAddress(), accepted->GetLocalAddress());
  EXPECT_EQ(client->GetLocalAddress(), accepted->GetRemoteAddress());

  // Connected from client perspective, check the addresses are correct.
  EXPECT_EQ_WAIT(AsyncSocket::CS_CONNECTED, client->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_OPEN));
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_CLOSE));
  EXPECT_EQ(client->GetRemoteAddress(), server->GetLocalAddress());
  EXPECT_EQ(client->GetRemoteAddress(), accepted->GetLocalAddress());
}

void SocketTest::ConnectWithDnsLookupInternal(const IPAddress& loopback,
                                              const std::string& host) {
  testing::StreamSink sink;
  SocketAddress accept_addr;

  // Create client.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(client.get());

  // Create server and listen.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));

  // Attempt connect to listening socket.
  SocketAddress dns_addr(server->GetLocalAddress());
  dns_addr.SetIP(host);
  EXPECT_EQ(0, client->Connect(dns_addr));
  // TODO: Bind when doing DNS lookup.
  //EXPECT_NE(kEmptyAddr, client->GetLocalAddress());  // Implicit Bind

  // Client is connecting, outcome not yet determined.
  EXPECT_EQ(AsyncSocket::CS_CONNECTING, client->GetState());
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_OPEN));
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_CLOSE));

  // Server has pending connection, accept it.
  EXPECT_TRUE_WAIT((sink.Check(server.get(), testing::SSE_READ)), kTimeout);
  std::unique_ptr<AsyncSocket> accepted(server->Accept(&accept_addr));
  ASSERT_TRUE(accepted);
  EXPECT_FALSE(accept_addr.IsNil());
  EXPECT_EQ(accepted->GetRemoteAddress(), accept_addr);

  // Connected from server perspective, check the addresses are correct.
  EXPECT_EQ(AsyncSocket::CS_CONNECTED, accepted->GetState());
  EXPECT_EQ(server->GetLocalAddress(), accepted->GetLocalAddress());
  EXPECT_EQ(client->GetLocalAddress(), accepted->GetRemoteAddress());

  // Connected from client perspective, check the addresses are correct.
  EXPECT_EQ_WAIT(AsyncSocket::CS_CONNECTED, client->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_OPEN));
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_CLOSE));
  EXPECT_EQ(client->GetRemoteAddress(), server->GetLocalAddress());
  EXPECT_EQ(client->GetRemoteAddress(), accepted->GetLocalAddress());
}

void SocketTest::ConnectFailInternal(const IPAddress& loopback) {
  testing::StreamSink sink;
  SocketAddress accept_addr;

  // Create client.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(client.get());

  // Create server, but don't listen yet.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));

  // Attempt connect to a non-existent socket.
  // We don't connect to the server socket created above, since on
  // MacOS it takes about 75 seconds to get back an error!
  SocketAddress bogus_addr(loopback, 65535);
  EXPECT_EQ(0, client->Connect(bogus_addr));

  // Wait for connection to fail (ECONNREFUSED).
  EXPECT_EQ_WAIT(AsyncSocket::CS_CLOSED, client->GetState(), kTimeout);
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_OPEN));
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_ERROR));
  EXPECT_TRUE(client->GetRemoteAddress().IsNil());

  // Should be no pending server connections.
  EXPECT_FALSE(sink.Check(server.get(), testing::SSE_READ));
  EXPECT_TRUE(NULL == server->Accept(&accept_addr));
  EXPECT_EQ(IPAddress(), accept_addr.ipaddr());
}

void SocketTest::ConnectWithDnsLookupFailInternal(const IPAddress& loopback) {
  testing::StreamSink sink;
  SocketAddress accept_addr;

  // Create client.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(client.get());

  // Create server, but don't listen yet.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));

  // Attempt connect to a non-existent host.
  // We don't connect to the server socket created above, since on
  // MacOS it takes about 75 seconds to get back an error!
  SocketAddress bogus_dns_addr("not-a-real-hostname", 65535);
  EXPECT_EQ(0, client->Connect(bogus_dns_addr));

  // Wait for connection to fail (EHOSTNOTFOUND).
  bool dns_lookup_finished = false;
  WAIT_(client->GetState() == AsyncSocket::CS_CLOSED, kTimeout,
        dns_lookup_finished);
  if (!dns_lookup_finished) {
    LOG(LS_WARNING) << "Skipping test; DNS resolution took longer than 5 "
                    << "seconds.";
    return;
  }

  EXPECT_EQ_WAIT(AsyncSocket::CS_CLOSED, client->GetState(), kTimeout);
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_OPEN));
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_ERROR));
  EXPECT_TRUE(client->GetRemoteAddress().IsNil());
  // Should be no pending server connections.
  EXPECT_FALSE(sink.Check(server.get(), testing::SSE_READ));
  EXPECT_TRUE(NULL == server->Accept(&accept_addr));
  EXPECT_TRUE(accept_addr.IsNil());
}

void SocketTest::ConnectWithClosedSocketInternal(const IPAddress& loopback) {
  // Create server and listen.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));

  // Create a client and put in to CS_CLOSED state.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  EXPECT_EQ(0, client->Close());
  EXPECT_EQ(AsyncSocket::CS_CLOSED, client->GetState());

  // Connect() should reinitialize the socket, and put it in to CS_CONNECTING.
  EXPECT_EQ(0, client->Connect(SocketAddress(server->GetLocalAddress())));
  EXPECT_EQ(AsyncSocket::CS_CONNECTING, client->GetState());
}

void SocketTest::ConnectWhileNotClosedInternal(const IPAddress& loopback) {
  // Create server and listen.
  testing::StreamSink sink;
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));
  // Create client, connect.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  EXPECT_EQ(0, client->Connect(SocketAddress(server->GetLocalAddress())));
  EXPECT_EQ(AsyncSocket::CS_CONNECTING, client->GetState());
  // Try to connect again. Should fail, but not interfere with original attempt.
  EXPECT_EQ(SOCKET_ERROR,
            client->Connect(SocketAddress(server->GetLocalAddress())));

  // Accept the original connection.
  SocketAddress accept_addr;
  EXPECT_TRUE_WAIT((sink.Check(server.get(), testing::SSE_READ)), kTimeout);
  std::unique_ptr<AsyncSocket> accepted(server->Accept(&accept_addr));
  ASSERT_TRUE(accepted);
  EXPECT_FALSE(accept_addr.IsNil());

  // Check the states and addresses.
  EXPECT_EQ(AsyncSocket::CS_CONNECTED, accepted->GetState());
  EXPECT_EQ(server->GetLocalAddress(), accepted->GetLocalAddress());
  EXPECT_EQ(client->GetLocalAddress(), accepted->GetRemoteAddress());
  EXPECT_EQ_WAIT(AsyncSocket::CS_CONNECTED, client->GetState(), kTimeout);
  EXPECT_EQ(client->GetRemoteAddress(), server->GetLocalAddress());
  EXPECT_EQ(client->GetRemoteAddress(), accepted->GetLocalAddress());

  // Try to connect again, to an unresolved hostname.
  // Shouldn't break anything.
  EXPECT_EQ(SOCKET_ERROR,
            client->Connect(SocketAddress("localhost",
                                          server->GetLocalAddress().port())));
  EXPECT_EQ(AsyncSocket::CS_CONNECTED, accepted->GetState());
  EXPECT_EQ(AsyncSocket::CS_CONNECTED, client->GetState());
  EXPECT_EQ(client->GetRemoteAddress(), server->GetLocalAddress());
  EXPECT_EQ(client->GetRemoteAddress(), accepted->GetLocalAddress());
}

void SocketTest::ServerCloseDuringConnectInternal(const IPAddress& loopback) {
  testing::StreamSink sink;

  // Create client.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(client.get());

  // Create server and listen.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));

  // Attempt connect to listening socket.
  EXPECT_EQ(0, client->Connect(server->GetLocalAddress()));

  // Close down the server while the socket is in the accept queue.
  EXPECT_TRUE_WAIT(sink.Check(server.get(), testing::SSE_READ), kTimeout);
  server->Close();

  // This should fail the connection for the client. Clean up.
  EXPECT_EQ_WAIT(AsyncSocket::CS_CLOSED, client->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_ERROR));
  client->Close();
}

void SocketTest::ClientCloseDuringConnectInternal(const IPAddress& loopback) {
  testing::StreamSink sink;
  SocketAddress accept_addr;

  // Create client.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(client.get());

  // Create server and listen.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));

  // Attempt connect to listening socket.
  EXPECT_EQ(0, client->Connect(server->GetLocalAddress()));

  // Close down the client while the socket is in the accept queue.
  EXPECT_TRUE_WAIT(sink.Check(server.get(), testing::SSE_READ), kTimeout);
  client->Close();

  // The connection should still be able to be accepted.
  std::unique_ptr<AsyncSocket> accepted(server->Accept(&accept_addr));
  ASSERT_TRUE(accepted);
  sink.Monitor(accepted.get());
  EXPECT_EQ(AsyncSocket::CS_CONNECTED, accepted->GetState());

  // The accepted socket should then close (possibly with err, timing-related)
  EXPECT_EQ_WAIT(AsyncSocket::CS_CLOSED, accepted->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(accepted.get(), testing::SSE_CLOSE) ||
              sink.Check(accepted.get(), testing::SSE_ERROR));

  // The client should not get a close event.
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_CLOSE));
}

void SocketTest::ServerCloseInternal(const IPAddress& loopback) {
  testing::StreamSink sink;
  SocketAddress accept_addr;

  // Create client.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(client.get());

  // Create server and listen.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));

  // Attempt connection.
  EXPECT_EQ(0, client->Connect(server->GetLocalAddress()));

  // Accept connection.
  EXPECT_TRUE_WAIT((sink.Check(server.get(), testing::SSE_READ)), kTimeout);
  std::unique_ptr<AsyncSocket> accepted(server->Accept(&accept_addr));
  ASSERT_TRUE(accepted);
  sink.Monitor(accepted.get());

  // Both sides are now connected.
  EXPECT_EQ_WAIT(AsyncSocket::CS_CONNECTED, client->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_OPEN));
  EXPECT_EQ(client->GetRemoteAddress(), accepted->GetLocalAddress());
  EXPECT_EQ(accepted->GetRemoteAddress(), client->GetLocalAddress());

  // Send data to the client, and then close the connection.
  EXPECT_EQ(1, accepted->Send("a", 1));
  accepted->Close();
  EXPECT_EQ(AsyncSocket::CS_CLOSED, accepted->GetState());

  // Expect that the client is notified, and has not yet closed.
  EXPECT_TRUE_WAIT(sink.Check(client.get(), testing::SSE_READ), kTimeout);
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_CLOSE));
  EXPECT_EQ(AsyncSocket::CS_CONNECTED, client->GetState());

  // Ensure the data can be read.
  char buffer[10];
  EXPECT_EQ(1, client->Recv(buffer, sizeof(buffer), nullptr));
  EXPECT_EQ('a', buffer[0]);

  // Now we should close, but the remote address will remain.
  EXPECT_EQ_WAIT(AsyncSocket::CS_CLOSED, client->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_CLOSE));
  EXPECT_FALSE(client->GetRemoteAddress().IsAnyIP());

  // The closer should not get a close signal.
  EXPECT_FALSE(sink.Check(accepted.get(), testing::SSE_CLOSE));
  EXPECT_TRUE(accepted->GetRemoteAddress().IsNil());

  // And the closee should only get a single signal.
  Thread::Current()->ProcessMessages(0);
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_CLOSE));

  // Close down the client and ensure all is good.
  client->Close();
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_CLOSE));
  EXPECT_TRUE(client->GetRemoteAddress().IsNil());
}

class SocketCloser : public sigslot::has_slots<> {
 public:
  void OnClose(AsyncSocket* socket, int error) {
    socket->Close();  // Deleting here would blow up the vector of handlers
                      // for the socket's signal.
  }
};

void SocketTest::CloseInClosedCallbackInternal(const IPAddress& loopback) {
  testing::StreamSink sink;
  SocketCloser closer;
  SocketAddress accept_addr;

  // Create client.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(client.get());
  client->SignalCloseEvent.connect(&closer, &SocketCloser::OnClose);

  // Create server and listen.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));

  // Attempt connection.
  EXPECT_EQ(0, client->Connect(server->GetLocalAddress()));

  // Accept connection.
  EXPECT_TRUE_WAIT((sink.Check(server.get(), testing::SSE_READ)), kTimeout);
  std::unique_ptr<AsyncSocket> accepted(server->Accept(&accept_addr));
  ASSERT_TRUE(accepted);
  sink.Monitor(accepted.get());

  // Both sides are now connected.
  EXPECT_EQ_WAIT(AsyncSocket::CS_CONNECTED, client->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_OPEN));
  EXPECT_EQ(client->GetRemoteAddress(), accepted->GetLocalAddress());
  EXPECT_EQ(accepted->GetRemoteAddress(), client->GetLocalAddress());

  // Send data to the client, and then close the connection.
  accepted->Close();
  EXPECT_EQ(AsyncSocket::CS_CLOSED, accepted->GetState());

  // Expect that the client is notified, and has not yet closed.
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_CLOSE));
  EXPECT_EQ(AsyncSocket::CS_CONNECTED, client->GetState());

  // Now we should be closed and invalidated
  EXPECT_EQ_WAIT(AsyncSocket::CS_CLOSED, client->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_CLOSE));
  EXPECT_TRUE(Socket::CS_CLOSED == client->GetState());
}

class Sleeper : public MessageHandler {
 public:
  Sleeper() {}
  void OnMessage(Message* msg) {
    Thread::Current()->SleepMs(500);
  }
};

void SocketTest::SocketServerWaitInternal(const IPAddress& loopback) {
  testing::StreamSink sink;
  SocketAddress accept_addr;

  // Create & connect server and client sockets.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(client.get());
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));

  EXPECT_EQ(0, client->Connect(server->GetLocalAddress()));
  EXPECT_TRUE_WAIT((sink.Check(server.get(), testing::SSE_READ)), kTimeout);

  std::unique_ptr<AsyncSocket> accepted(server->Accept(&accept_addr));
  ASSERT_TRUE(accepted);
  sink.Monitor(accepted.get());
  EXPECT_EQ(AsyncSocket::CS_CONNECTED, accepted->GetState());
  EXPECT_EQ(server->GetLocalAddress(), accepted->GetLocalAddress());
  EXPECT_EQ(client->GetLocalAddress(), accepted->GetRemoteAddress());

  EXPECT_EQ_WAIT(AsyncSocket::CS_CONNECTED, client->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_OPEN));
  EXPECT_FALSE(sink.Check(client.get(), testing::SSE_CLOSE));
  EXPECT_EQ(client->GetRemoteAddress(), server->GetLocalAddress());
  EXPECT_EQ(client->GetRemoteAddress(), accepted->GetLocalAddress());

  // Do an i/o operation, triggering an eventual callback.
  EXPECT_FALSE(sink.Check(accepted.get(), testing::SSE_READ));
  char buf[1024] = {0};

  EXPECT_EQ(1024, client->Send(buf, 1024));
  EXPECT_FALSE(sink.Check(accepted.get(), testing::SSE_READ));

  // Shouldn't signal when blocked in a thread Send, where process_io is false.
  std::unique_ptr<Thread> thread(new Thread());
  thread->Start();
  Sleeper sleeper;
  TypedMessageData<AsyncSocket*> data(client.get());
  thread->Send(RTC_FROM_HERE, &sleeper, 0, &data);
  EXPECT_FALSE(sink.Check(accepted.get(), testing::SSE_READ));

  // But should signal when process_io is true.
  EXPECT_TRUE_WAIT((sink.Check(accepted.get(), testing::SSE_READ)), kTimeout);
  EXPECT_LT(0, accepted->Recv(buf, 1024, nullptr));
}

void SocketTest::TcpInternal(const IPAddress& loopback, size_t data_size,
    ssize_t max_send_size) {
  testing::StreamSink sink;
  SocketAddress accept_addr;

  // Create receiving client.
  std::unique_ptr<AsyncSocket> receiver(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(receiver.get());

  // Create server and listen.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));

  // Attempt connection.
  EXPECT_EQ(0, receiver->Connect(server->GetLocalAddress()));

  // Accept connection which will be used for sending.
  EXPECT_TRUE_WAIT((sink.Check(server.get(), testing::SSE_READ)), kTimeout);
  std::unique_ptr<AsyncSocket> sender(server->Accept(&accept_addr));
  ASSERT_TRUE(sender);
  sink.Monitor(sender.get());

  // Both sides are now connected.
  EXPECT_EQ_WAIT(AsyncSocket::CS_CONNECTED, receiver->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(receiver.get(), testing::SSE_OPEN));
  EXPECT_EQ(receiver->GetRemoteAddress(), sender->GetLocalAddress());
  EXPECT_EQ(sender->GetRemoteAddress(), receiver->GetLocalAddress());

  // Create test data.
  rtc::Buffer send_buffer(0, data_size);
  rtc::Buffer recv_buffer(0, data_size);
  for (size_t i = 0; i < data_size; ++i) {
    char ch = static_cast<char>(i % 256);
    send_buffer.AppendData(&ch, sizeof(ch));
  }

  // Send and receive a bunch of data.
  size_t sent_size = 0;
  bool writable = true;
  bool send_called = false;
  bool readable = false;
  bool recv_called = false;
  while (recv_buffer.size() < send_buffer.size()) {
    // Send as much as we can while we're cleared to send.
    while (writable && sent_size < send_buffer.size()) {
      int unsent_size = static_cast<int>(send_buffer.size() - sent_size);
      int sent = sender->Send(send_buffer.data() + sent_size, unsent_size);
      if (!send_called) {
        // The first Send() after connecting or getting writability should
        // succeed and send some data.
        EXPECT_GT(sent, 0);
        send_called = true;
      }
      if (sent >= 0) {
        EXPECT_LE(sent, unsent_size);
        sent_size += sent;
        if (max_send_size >= 0) {
          EXPECT_LE(static_cast<ssize_t>(sent), max_send_size);
          if (sent < unsent_size) {
            // If max_send_size is limiting the amount to send per call such
            // that the sent amount is less than the unsent amount, we simulate
            // that the socket is no longer writable.
            writable = false;
          }
        }
      } else {
        ASSERT_TRUE(sender->IsBlocking());
        writable = false;
      }
    }

    // Read all the sent data.
    while (recv_buffer.size() < sent_size) {
      if (!readable) {
        // Wait until data is available.
        EXPECT_TRUE_WAIT(sink.Check(receiver.get(), testing::SSE_READ),
            kTimeout);
        readable = true;
        recv_called = false;
      }

      // Receive as much as we can get in a single recv call.
      char recved_data[data_size];
      int recved_size = receiver->Recv(recved_data, data_size, nullptr);

      if (!recv_called) {
        // The first Recv() after getting readability should succeed and receive
        // some data.
        // TODO: The following line is disabled due to flakey pulse
        //     builds.  Re-enable if/when possible.
        // EXPECT_GT(recved_size, 0);
        recv_called = true;
      }
      if (recved_size >= 0) {
        EXPECT_LE(static_cast<size_t>(recved_size),
            sent_size - recv_buffer.size());
        recv_buffer.AppendData(recved_data, recved_size);
      } else {
        ASSERT_TRUE(receiver->IsBlocking());
        readable = false;
      }
    }

    // Once all that we've sent has been received, expect to be able to send
    // again.
    if (!writable) {
      EXPECT_TRUE_WAIT(sink.Check(sender.get(), testing::SSE_WRITE),
                       kTimeout);
      writable = true;
      send_called = false;
    }
  }

  // The received data matches the sent data.
  EXPECT_EQ(data_size, sent_size);
  EXPECT_EQ(data_size, recv_buffer.size());
  EXPECT_EQ(recv_buffer, send_buffer);

  // Close down.
  sender->Close();
  EXPECT_EQ_WAIT(AsyncSocket::CS_CLOSED, receiver->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(receiver.get(), testing::SSE_CLOSE));
  receiver->Close();
}

void SocketTest::SingleFlowControlCallbackInternal(const IPAddress& loopback) {
  testing::StreamSink sink;
  SocketAddress accept_addr;

  // Create client.
  std::unique_ptr<AsyncSocket> client(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(client.get());

  // Create server and listen.
  std::unique_ptr<AsyncSocket> server(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_STREAM));
  sink.Monitor(server.get());
  EXPECT_EQ(0, server->Bind(SocketAddress(loopback, 0)));
  EXPECT_EQ(0, server->Listen(5));

  // Attempt connection.
  EXPECT_EQ(0, client->Connect(server->GetLocalAddress()));

  // Accept connection.
  EXPECT_TRUE_WAIT((sink.Check(server.get(), testing::SSE_READ)), kTimeout);
  std::unique_ptr<AsyncSocket> accepted(server->Accept(&accept_addr));
  ASSERT_TRUE(accepted);
  sink.Monitor(accepted.get());

  // Both sides are now connected.
  EXPECT_EQ_WAIT(AsyncSocket::CS_CONNECTED, client->GetState(), kTimeout);
  EXPECT_TRUE(sink.Check(client.get(), testing::SSE_OPEN));
  EXPECT_EQ(client->GetRemoteAddress(), accepted->GetLocalAddress());
  EXPECT_EQ(accepted->GetRemoteAddress(), client->GetLocalAddress());

  // Expect a writable callback from the connect.
  EXPECT_TRUE_WAIT(sink.Check(accepted.get(), testing::SSE_WRITE), kTimeout);

  // Fill the socket buffer.
  char buf[1024 * 16] = {0};
  int sends = 0;
  while (++sends && accepted->Send(&buf, arraysize(buf)) != -1) {}
  EXPECT_TRUE(accepted->IsBlocking());

  // Wait until data is available.
  EXPECT_TRUE_WAIT(sink.Check(client.get(), testing::SSE_READ), kTimeout);

  // Pull data.
  for (int i = 0; i < sends; ++i) {
    client->Recv(buf, arraysize(buf), nullptr);
  }

  // Expect at least one additional writable callback.
  EXPECT_TRUE_WAIT(sink.Check(accepted.get(), testing::SSE_WRITE), kTimeout);

  // Adding data in response to the writeable callback shouldn't cause infinite
  // callbacks.
  int extras = 0;
  for (int i = 0; i < 100; ++i) {
    accepted->Send(&buf, arraysize(buf));
    rtc::Thread::Current()->ProcessMessages(1);
    if (sink.Check(accepted.get(), testing::SSE_WRITE)) {
      extras++;
    }
  }
  EXPECT_LT(extras, 2);

  // Close down.
  accepted->Close();
  client->Close();
}

void SocketTest::UdpInternal(const IPAddress& loopback) {
  SocketAddress empty = EmptySocketAddressWithFamily(loopback.family());
  // Test basic bind and connect behavior.
  AsyncSocket* socket =
      ss_->CreateAsyncSocket(loopback.family(), SOCK_DGRAM);
  EXPECT_EQ(AsyncSocket::CS_CLOSED, socket->GetState());
  EXPECT_EQ(0, socket->Bind(SocketAddress(loopback, 0)));
  SocketAddress addr1 = socket->GetLocalAddress();
  EXPECT_EQ(0, socket->Connect(addr1));
  EXPECT_EQ(AsyncSocket::CS_CONNECTED, socket->GetState());
  socket->Close();
  EXPECT_EQ(AsyncSocket::CS_CLOSED, socket->GetState());
  delete socket;

  // Test send/receive behavior.
  std::unique_ptr<TestClient> client1(
      new TestClient(AsyncUDPSocket::Create(ss_, addr1)));
  std::unique_ptr<TestClient> client2(
      new TestClient(AsyncUDPSocket::Create(ss_, empty)));

  SocketAddress addr2;
  EXPECT_EQ(3, client2->SendTo("foo", 3, addr1));
  EXPECT_TRUE(client1->CheckNextPacket("foo", 3, &addr2));

  SocketAddress addr3;
  EXPECT_EQ(6, client1->SendTo("bizbaz", 6, addr2));
  EXPECT_TRUE(client2->CheckNextPacket("bizbaz", 6, &addr3));
  EXPECT_EQ(addr3, addr1);
  // TODO: figure out what the intent is here
  for (int i = 0; i < 10; ++i) {
    client2.reset(new TestClient(AsyncUDPSocket::Create(ss_, empty)));

    SocketAddress addr4;
    EXPECT_EQ(3, client2->SendTo("foo", 3, addr1));
    EXPECT_TRUE(client1->CheckNextPacket("foo", 3, &addr4));
    EXPECT_EQ(addr4.ipaddr(), addr2.ipaddr());

    SocketAddress addr5;
    EXPECT_EQ(6, client1->SendTo("bizbaz", 6, addr4));
    EXPECT_TRUE(client2->CheckNextPacket("bizbaz", 6, &addr5));
    EXPECT_EQ(addr5, addr1);

    addr2 = addr4;
  }
}

void SocketTest::UdpReadyToSend(const IPAddress& loopback) {
  SocketAddress empty = EmptySocketAddressWithFamily(loopback.family());
  // RFC 5737 - The blocks 192.0.2.0/24 (TEST-NET-1) ... are provided for use in
  // documentation.
  // RFC 3849 - 2001:DB8::/32 as a documentation-only prefix.
  std::string dest = (loopback.family() == AF_INET6) ?
      "2001:db8::1" : "192.0.2.0";
  SocketAddress test_addr(dest, 2345);

  // Test send
  std::unique_ptr<TestClient> client(
      new TestClient(AsyncUDPSocket::Create(ss_, empty)));
  int test_packet_size = 1200;
  std::unique_ptr<char[]> test_packet(new char[test_packet_size]);
  // Init the test packet just to avoid memcheck warning.
  memset(test_packet.get(), 0, test_packet_size);
  // Set the send buffer size to the same size as the test packet to have a
  // better chance to get EWOULDBLOCK.
  int send_buffer_size = test_packet_size;
#if defined(WEBRTC_LINUX) && !defined(WEBRTC_ANDROID)
  send_buffer_size /= 2;
#endif
  client->SetOption(rtc::Socket::OPT_SNDBUF, send_buffer_size);

  int error = 0;
  uint32_t start_ms = Time();
  int sent_packet_num = 0;
  int expected_error = EWOULDBLOCK;
  while (start_ms + kTimeout > Time()) {
    int ret = client->SendTo(test_packet.get(), test_packet_size, test_addr);
    ++sent_packet_num;
    if (ret != test_packet_size) {
      error = client->GetError();
      if (error == expected_error) {
        LOG(LS_INFO) << "Got expected error code after sending "
                     << sent_packet_num << " packets.";
        break;
      }
    }
  }
  EXPECT_EQ(expected_error, error);
  EXPECT_FALSE(client->ready_to_send());
  EXPECT_TRUE_WAIT(client->ready_to_send(), kTimeout);
  LOG(LS_INFO) << "Got SignalReadyToSend";
}

void SocketTest::GetSetOptionsInternal(const IPAddress& loopback) {
  std::unique_ptr<AsyncSocket> socket(
      ss_->CreateAsyncSocket(loopback.family(), SOCK_DGRAM));
  socket->Bind(SocketAddress(loopback, 0));

  // Check SNDBUF/RCVBUF.
  const int desired_size = 12345;
#if defined(WEBRTC_LINUX)
  // Yes, really.  It's in the kernel source.
  const int expected_size = desired_size * 2;
#else   // !WEBRTC_LINUX
  const int expected_size = desired_size;
#endif  // !WEBRTC_LINUX
  int recv_size = 0;
  int send_size = 0;
  // get the initial sizes
  ASSERT_NE(-1, socket->GetOption(Socket::OPT_RCVBUF, &recv_size));
  ASSERT_NE(-1, socket->GetOption(Socket::OPT_SNDBUF, &send_size));
  // set our desired sizes
  ASSERT_NE(-1, socket->SetOption(Socket::OPT_RCVBUF, desired_size));
  ASSERT_NE(-1, socket->SetOption(Socket::OPT_SNDBUF, desired_size));
  // get the sizes again
  ASSERT_NE(-1, socket->GetOption(Socket::OPT_RCVBUF, &recv_size));
  ASSERT_NE(-1, socket->GetOption(Socket::OPT_SNDBUF, &send_size));
  // make sure they are right
  ASSERT_EQ(expected_size, recv_size);
  ASSERT_EQ(expected_size, send_size);

  // Check that we can't set NODELAY on a UDP socket.
  int current_nd, desired_nd = 1;
  ASSERT_EQ(-1, socket->GetOption(Socket::OPT_NODELAY, &current_nd));
  ASSERT_EQ(-1, socket->SetOption(Socket::OPT_NODELAY, desired_nd));

  // Skip the esimate MTU test for IPv6 for now.
  if (loopback.family() != AF_INET6) {
    // Try estimating MTU.
    std::unique_ptr<AsyncSocket> mtu_socket(
        ss_->CreateAsyncSocket(loopback.family(), SOCK_DGRAM));
    mtu_socket->Bind(SocketAddress(loopback, 0));
    uint16_t mtu;
    // should fail until we connect
    ASSERT_EQ(-1, mtu_socket->EstimateMTU(&mtu));
    mtu_socket->Connect(SocketAddress(loopback, 0));
#if defined(WEBRTC_WIN)
    // now it should succeed
    ASSERT_NE(-1, mtu_socket->EstimateMTU(&mtu));
    ASSERT_GE(mtu, 1492);  // should be at least the 1492 "plateau" on localhost
#elif defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
    // except on WEBRTC_MAC && !WEBRTC_IOS, where it's not yet implemented
    ASSERT_EQ(-1, mtu_socket->EstimateMTU(&mtu));
#else
    // and the behavior seems unpredictable on Linux,
    // failing on the build machine
    // but succeeding on my Ubiquity instance.
#endif
  }
}

void SocketTest::SocketRecvTimestamp(const IPAddress& loopback) {
  std::unique_ptr<Socket> socket(
      ss_->CreateSocket(loopback.family(), SOCK_DGRAM));
  EXPECT_EQ(0, socket->Bind(SocketAddress(loopback, 0)));
  SocketAddress address = socket->GetLocalAddress();

  socket->SendTo("foo", 3, address);
  int64_t timestamp;
  char buffer[3];
  socket->RecvFrom(buffer, 3, nullptr, &timestamp);
  EXPECT_GT(timestamp, -1);
  int64_t prev_timestamp = timestamp;

  const int64_t kTimeBetweenPacketsMs = 10;
  Thread::SleepMs(kTimeBetweenPacketsMs);

  socket->SendTo("bar", 3, address);
  socket->RecvFrom(buffer, 3, nullptr, &timestamp);
  EXPECT_NEAR(timestamp, prev_timestamp + kTimeBetweenPacketsMs * 1000, 2000);
}

}  // namespace rtc
