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
#include <string>
#include "webrtc/base/autodetectproxy.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/httpserver.h"
#include "webrtc/base/proxyserver.h"
#include "webrtc/base/socketadapters.h"
#include "webrtc/base/testclient.h"
#include "webrtc/base/testechoserver.h"
#include "webrtc/base/virtualsocketserver.h"

using rtc::Socket;
using rtc::Thread;
using rtc::SocketAddress;

static const SocketAddress kSocksProxyIntAddr("1.2.3.4", 1080);
static const SocketAddress kSocksProxyExtAddr("1.2.3.5", 0);
static const SocketAddress kHttpsProxyIntAddr("1.2.3.4", 443);
static const SocketAddress kHttpsProxyExtAddr("1.2.3.5", 0);
static const SocketAddress kBogusProxyIntAddr("1.2.3.4", 999);

// Used to run a proxy detect on the current thread. Otherwise we would need
// to make both threads share the same VirtualSocketServer.
class AutoDetectProxyRunner : public rtc::AutoDetectProxy {
 public:
  explicit AutoDetectProxyRunner(const std::string& agent)
      : AutoDetectProxy(agent) {}
  void Run() {
    DoWork();
    Thread::Current()->Restart();  // needed to reset the messagequeue
  }
};

// Sets up a virtual socket server and HTTPS/SOCKS5 proxy servers.
class ProxyTest : public testing::Test {
 public:
  ProxyTest() : ss_(new rtc::VirtualSocketServer(NULL)) {
    Thread::Current()->set_socketserver(ss_.get());
    socks_.reset(new rtc::SocksProxyServer(
        ss_.get(), kSocksProxyIntAddr, ss_.get(), kSocksProxyExtAddr));
    https_.reset(new rtc::HttpListenServer());
    https_->Listen(kHttpsProxyIntAddr);
  }
  ~ProxyTest() {
    Thread::Current()->set_socketserver(NULL);
  }

  rtc::SocketServer* ss() { return ss_.get(); }

  rtc::ProxyType DetectProxyType(const SocketAddress& address) {
    rtc::ProxyType type;
    AutoDetectProxyRunner* detect = new AutoDetectProxyRunner("unittest/1.0");
    detect->set_proxy(address);
    detect->Run();  // blocks until done
    type = detect->proxy().type;
    detect->Destroy(false);
    return type;
  }

 private:
  std::unique_ptr<rtc::SocketServer> ss_;
  std::unique_ptr<rtc::SocksProxyServer> socks_;
  // TODO: Make this a real HTTPS proxy server.
  std::unique_ptr<rtc::HttpListenServer> https_;
};

// Tests whether we can use a SOCKS5 proxy to connect to a server.
TEST_F(ProxyTest, TestSocks5Connect) {
  rtc::AsyncSocket* socket =
      ss()->CreateAsyncSocket(kSocksProxyIntAddr.family(), SOCK_STREAM);
  rtc::AsyncSocksProxySocket* proxy_socket =
      new rtc::AsyncSocksProxySocket(socket, kSocksProxyIntAddr,
                                           "", rtc::CryptString());
  // TODO: IPv6-ize these tests when proxy supports IPv6.

  rtc::TestEchoServer server(Thread::Current(),
                                   SocketAddress(INADDR_ANY, 0));

  rtc::AsyncTCPSocket* packet_socket = rtc::AsyncTCPSocket::Create(
      proxy_socket, SocketAddress(INADDR_ANY, 0), server.address());
  EXPECT_TRUE(packet_socket != NULL);
  rtc::TestClient client(packet_socket);

  EXPECT_EQ(Socket::CS_CONNECTING, proxy_socket->GetState());
  EXPECT_TRUE(client.CheckConnected());
  EXPECT_EQ(Socket::CS_CONNECTED, proxy_socket->GetState());
  EXPECT_EQ(server.address(), client.remote_address());
  client.Send("foo", 3);
  EXPECT_TRUE(client.CheckNextPacket("foo", 3, NULL));
  EXPECT_TRUE(client.CheckNoPacket());
}

/*
// Tests whether we can use a HTTPS proxy to connect to a server.
TEST_F(ProxyTest, TestHttpsConnect) {
  AsyncSocket* socket = ss()->CreateAsyncSocket(SOCK_STREAM);
  AsyncHttpsProxySocket* proxy_socket = new AsyncHttpsProxySocket(
      socket, "unittest/1.0", kHttpsProxyIntAddress, "", CryptString());
  TestClient client(new AsyncTCPSocket(proxy_socket));
  TestEchoServer server(Thread::Current(), SocketAddress());

  EXPECT_TRUE(client.Connect(server.address()));
  EXPECT_TRUE(client.CheckConnected());
  EXPECT_EQ(server.address(), client.remote_address());
  client.Send("foo", 3);
  EXPECT_TRUE(client.CheckNextPacket("foo", 3, NULL));
  EXPECT_TRUE(client.CheckNoPacket());
}
*/

// Tests whether we can autodetect a SOCKS5 proxy.
TEST_F(ProxyTest, TestAutoDetectSocks5) {
  EXPECT_EQ(rtc::PROXY_SOCKS5, DetectProxyType(kSocksProxyIntAddr));
}

/*
// Tests whether we can autodetect a HTTPS proxy.
TEST_F(ProxyTest, TestAutoDetectHttps) {
  EXPECT_EQ(rtc::PROXY_HTTPS, DetectProxyType(kHttpsProxyIntAddr));
}
*/

// Tests whether we fail properly for no proxy.
TEST_F(ProxyTest, TestAutoDetectBogus) {
  EXPECT_EQ(rtc::PROXY_UNKNOWN, DetectProxyType(kBogusProxyIntAddr));
}
