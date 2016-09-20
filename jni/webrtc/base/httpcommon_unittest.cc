/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/gunit.h"
#include "webrtc/base/httpcommon-inl.h"
#include "webrtc/base/httpcommon.h"

namespace rtc {

#define TEST_PROTOCOL "http://"
#define TEST_HOST "www.google.com"
#define TEST_PATH "/folder/file.html"
#define TEST_QUERY "?query=x&attr=y"
#define TEST_URL TEST_PROTOCOL TEST_HOST TEST_PATH TEST_QUERY

TEST(Url, DecomposesUrls) {
  Url<char> url(TEST_URL);
  EXPECT_TRUE(url.valid());
  EXPECT_FALSE(url.secure());
  EXPECT_STREQ(TEST_HOST, url.host().c_str());
  EXPECT_EQ(80, url.port());
  EXPECT_STREQ(TEST_PATH, url.path().c_str());
  EXPECT_STREQ(TEST_QUERY, url.query().c_str());
  EXPECT_STREQ(TEST_HOST, url.address().c_str());
  EXPECT_STREQ(TEST_PATH TEST_QUERY, url.full_path().c_str());
  EXPECT_STREQ(TEST_URL, url.url().c_str());
}

TEST(Url, ComposesUrls) {
  // Set in constructor
  Url<char> url(TEST_PATH TEST_QUERY, TEST_HOST, 80);
  EXPECT_TRUE(url.valid());
  EXPECT_FALSE(url.secure());
  EXPECT_STREQ(TEST_HOST, url.host().c_str());
  EXPECT_EQ(80, url.port());
  EXPECT_STREQ(TEST_PATH, url.path().c_str());
  EXPECT_STREQ(TEST_QUERY, url.query().c_str());
  EXPECT_STREQ(TEST_HOST, url.address().c_str());
  EXPECT_STREQ(TEST_PATH TEST_QUERY, url.full_path().c_str());
  EXPECT_STREQ(TEST_URL, url.url().c_str());

  url.clear();
  EXPECT_FALSE(url.valid());
  EXPECT_FALSE(url.secure());
  EXPECT_STREQ("", url.host().c_str());
  EXPECT_EQ(80, url.port());
  EXPECT_STREQ("/", url.path().c_str());
  EXPECT_STREQ("", url.query().c_str());

  // Set component-wise
  url.set_host(TEST_HOST);
  url.set_port(80);
  url.set_path(TEST_PATH);
  url.set_query(TEST_QUERY);
  EXPECT_TRUE(url.valid());
  EXPECT_FALSE(url.secure());
  EXPECT_STREQ(TEST_HOST, url.host().c_str());
  EXPECT_EQ(80, url.port());
  EXPECT_STREQ(TEST_PATH, url.path().c_str());
  EXPECT_STREQ(TEST_QUERY, url.query().c_str());
  EXPECT_STREQ(TEST_HOST, url.address().c_str());
  EXPECT_STREQ(TEST_PATH TEST_QUERY, url.full_path().c_str());
  EXPECT_STREQ(TEST_URL, url.url().c_str());
}

TEST(Url, EnsuresNonEmptyPath) {
  Url<char> url(TEST_PROTOCOL TEST_HOST);
  EXPECT_TRUE(url.valid());
  EXPECT_STREQ("/", url.path().c_str());

  url.clear();
  EXPECT_STREQ("/", url.path().c_str());
  url.set_path("");
  EXPECT_STREQ("/", url.path().c_str());

  url.clear();
  EXPECT_STREQ("/", url.path().c_str());
  url.set_full_path("");
  EXPECT_STREQ("/", url.path().c_str());
}

TEST(Url, GetQueryAttributes) {
  Url<char> url(TEST_URL);
  std::string value;
  EXPECT_TRUE(url.get_attribute("query", &value));
  EXPECT_STREQ("x", value.c_str());
  value.clear();
  EXPECT_TRUE(url.get_attribute("attr", &value));
  EXPECT_STREQ("y", value.c_str());
  value.clear();
  EXPECT_FALSE(url.get_attribute("Query", &value));
  EXPECT_TRUE(value.empty());
}

TEST(Url, SkipsUserAndPassword) {
  Url<char> url("https://mail.google.com:pwd@badsite.com:12345/asdf");
  EXPECT_TRUE(url.valid());
  EXPECT_TRUE(url.secure());
  EXPECT_STREQ("badsite.com", url.host().c_str());
  EXPECT_EQ(12345, url.port());
  EXPECT_STREQ("/asdf", url.path().c_str());
  EXPECT_STREQ("badsite.com:12345", url.address().c_str());
}

TEST(Url, SkipsUser) {
  Url<char> url("https://mail.google.com@badsite.com:12345/asdf");
  EXPECT_TRUE(url.valid());
  EXPECT_TRUE(url.secure());
  EXPECT_STREQ("badsite.com", url.host().c_str());
  EXPECT_EQ(12345, url.port());
  EXPECT_STREQ("/asdf", url.path().c_str());
  EXPECT_STREQ("badsite.com:12345", url.address().c_str());
}

TEST(HttpResponseData, parseLeaderHttp1_0) {
  static const char kResponseString[] = "HTTP/1.0 200 OK";
  HttpResponseData response;
  EXPECT_EQ(HE_NONE, response.parseLeader(kResponseString,
                                          sizeof(kResponseString) - 1));
  EXPECT_EQ(HVER_1_0, response.version);
  EXPECT_EQ(200U, response.scode);
}

TEST(HttpResponseData, parseLeaderHttp1_1) {
  static const char kResponseString[] = "HTTP/1.1 200 OK";
  HttpResponseData response;
  EXPECT_EQ(HE_NONE, response.parseLeader(kResponseString,
                                          sizeof(kResponseString) - 1));
  EXPECT_EQ(HVER_1_1, response.version);
  EXPECT_EQ(200U, response.scode);
}

TEST(HttpResponseData, parseLeaderHttpUnknown) {
  static const char kResponseString[] = "HTTP 200 OK";
  HttpResponseData response;
  EXPECT_EQ(HE_NONE, response.parseLeader(kResponseString,
                                          sizeof(kResponseString) - 1));
  EXPECT_EQ(HVER_UNKNOWN, response.version);
  EXPECT_EQ(200U, response.scode);
}

TEST(HttpResponseData, parseLeaderHttpFailure) {
  static const char kResponseString[] = "HTTP/1.1 503 Service Unavailable";
  HttpResponseData response;
  EXPECT_EQ(HE_NONE, response.parseLeader(kResponseString,
                                          sizeof(kResponseString) - 1));
  EXPECT_EQ(HVER_1_1, response.version);
  EXPECT_EQ(503U, response.scode);
}

TEST(HttpResponseData, parseLeaderHttpInvalid) {
  static const char kResponseString[] = "Durrrrr, what's HTTP?";
  HttpResponseData response;
  EXPECT_EQ(HE_PROTOCOL, response.parseLeader(kResponseString,
                                              sizeof(kResponseString) - 1));
}

} // namespace rtc
