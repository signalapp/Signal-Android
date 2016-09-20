/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/autodetectproxy.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/httpcommon.h"
#include "webrtc/base/httpcommon-inl.h"

namespace rtc {

static const char kUserAgent[] = "";
static const char kPath[] = "/";
static const char kHost[] = "relay.google.com";
static const uint16_t kPort = 443;
static const bool kSecure = true;
// At most, AutoDetectProxy should take ~6 seconds. Each connect step is
// allotted 2 seconds, with the initial resolution + connect given an
// extra 2 seconds. The slowest case is:
// 1) Resolution + HTTPS takes full 4 seconds and fails (but resolution
// succeeds).
// 2) SOCKS5 takes the full 2 seconds.
// Socket creation time seems unbounded, and has been observed to take >1 second
// on a linux machine under load. As such, we allow for 10 seconds for timeout,
// though could still end up with some flakiness.
static const int kTimeoutMs = 10000;

class AutoDetectProxyTest : public testing::Test, public sigslot::has_slots<> {
 public:
  AutoDetectProxyTest() : auto_detect_proxy_(NULL), done_(false) {}

 protected:
  bool Create(const std::string& user_agent,
              const std::string& path,
              const std::string& host,
              uint16_t port,
              bool secure,
              bool startnow) {
    auto_detect_proxy_ = new AutoDetectProxy(user_agent);
    EXPECT_TRUE(auto_detect_proxy_ != NULL);
    if (!auto_detect_proxy_) {
      return false;
    }
    Url<char> host_url(path, host, port);
    host_url.set_secure(secure);
    auto_detect_proxy_->set_server_url(host_url.url());
    auto_detect_proxy_->SignalWorkDone.connect(
        this,
        &AutoDetectProxyTest::OnWorkDone);
    if (startnow) {
      auto_detect_proxy_->Start();
    }
    return true;
  }

  bool Run(int timeout_ms) {
    EXPECT_TRUE_WAIT(done_, timeout_ms);
    return done_;
  }

  void SetProxy(const SocketAddress& proxy) {
    auto_detect_proxy_->set_proxy(proxy);
  }

  void Start() {
    auto_detect_proxy_->Start();
  }

  void TestCopesWithProxy(const SocketAddress& proxy) {
    // Tests that at least autodetect doesn't crash for a given proxy address.
    ASSERT_TRUE(Create(kUserAgent,
                       kPath,
                       kHost,
                       kPort,
                       kSecure,
                       false));
    SetProxy(proxy);
    Start();
    ASSERT_TRUE(Run(kTimeoutMs));
  }

 private:
  void OnWorkDone(rtc::SignalThread *thread) {
    AutoDetectProxy *auto_detect_proxy =
        static_cast<rtc::AutoDetectProxy *>(thread);
    EXPECT_TRUE(auto_detect_proxy == auto_detect_proxy_);
    auto_detect_proxy_ = NULL;
    auto_detect_proxy->Release();
    done_ = true;
  }

  AutoDetectProxy *auto_detect_proxy_;
  bool done_;
};

TEST_F(AutoDetectProxyTest, TestDetectUnresolvedProxy) {
  TestCopesWithProxy(rtc::SocketAddress("localhost", 9999));
}

TEST_F(AutoDetectProxyTest, TestDetectUnresolvableProxy) {
  TestCopesWithProxy(rtc::SocketAddress("invalid", 9999));
}

TEST_F(AutoDetectProxyTest, TestDetectIPv6Proxy) {
  TestCopesWithProxy(rtc::SocketAddress("::1", 9999));
}

TEST_F(AutoDetectProxyTest, TestDetectIPv4Proxy) {
  TestCopesWithProxy(rtc::SocketAddress("127.0.0.1", 9999));
}

// Test that proxy detection completes successfully. (Does not actually verify
// the correct detection result since we don't know what proxy to expect on an
// arbitrary machine.)
TEST_F(AutoDetectProxyTest, TestProxyDetection) {
  ASSERT_TRUE(Create(kUserAgent,
                     kPath,
                     kHost,
                     kPort,
                     kSecure,
                     true));
  ASSERT_TRUE(Run(kTimeoutMs));
}

}  // namespace rtc
