/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#if defined(WEBRTC_POSIX)
#include <netinet/in.h>  // for sockaddr_in
#endif

#include "webrtc/base/gunit.h"
#include "webrtc/base/socketaddress.h"
#include "webrtc/base/ipaddress.h"

namespace rtc {

const in6_addr kTestV6Addr =  { { {0x20, 0x01, 0x0d, 0xb8,
                                   0x10, 0x20, 0x30, 0x40,
                                   0x50, 0x60, 0x70, 0x80,
                                   0x90, 0xA0, 0xB0, 0xC0} } };
const in6_addr kMappedV4Addr = { { {0x00, 0x00, 0x00, 0x00,
                                    0x00, 0x00, 0x00, 0x00,
                                    0x00, 0x00, 0xFF, 0xFF,
                                    0x01, 0x02, 0x03, 0x04} } };
const std::string kTestV6AddrString = "2001:db8:1020:3040:5060:7080:90a0:b0c0";
const std::string kTestV6AddrAnonymizedString = "2001:db8:1020:x:x:x:x:x";
const std::string kTestV6AddrFullString =
    "[2001:db8:1020:3040:5060:7080:90a0:b0c0]:5678";
const std::string kTestV6AddrFullAnonymizedString =
    "[2001:db8:1020:x:x:x:x:x]:5678";

TEST(SocketAddressTest, TestDefaultCtor) {
  SocketAddress addr;
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(), addr.ipaddr());
  EXPECT_EQ(0, addr.port());
  EXPECT_EQ("", addr.hostname());
}

TEST(SocketAddressTest, TestIPPortCtor) {
  SocketAddress addr(IPAddress(0x01020304), 5678);
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0x01020304U), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("", addr.hostname());
  EXPECT_EQ("1.2.3.4:5678", addr.ToString());
}

TEST(SocketAddressTest, TestIPv4StringPortCtor) {
  SocketAddress addr("1.2.3.4", 5678);
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0x01020304U), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("1.2.3.4", addr.hostname());
  EXPECT_EQ("1.2.3.4:5678", addr.ToString());
}

TEST(SocketAddressTest, TestIPv6StringPortCtor) {
  SocketAddress addr2(kTestV6AddrString, 1234);
  IPAddress tocheck(kTestV6Addr);

  EXPECT_FALSE(addr2.IsUnresolvedIP());
  EXPECT_EQ(tocheck, addr2.ipaddr());
  EXPECT_EQ(1234, addr2.port());
  EXPECT_EQ(kTestV6AddrString, addr2.hostname());
  EXPECT_EQ("[" + kTestV6AddrString + "]:1234", addr2.ToString());
}

TEST(SocketAddressTest, TestSpecialStringPortCtor) {
  // inet_addr doesn't handle this address properly.
  SocketAddress addr("255.255.255.255", 5678);
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0xFFFFFFFFU), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("255.255.255.255", addr.hostname());
  EXPECT_EQ("255.255.255.255:5678", addr.ToString());
}

TEST(SocketAddressTest, TestHostnamePortCtor) {
  SocketAddress addr("a.b.com", 5678);
  EXPECT_TRUE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("a.b.com", addr.hostname());
  EXPECT_EQ("a.b.com:5678", addr.ToString());
}

TEST(SocketAddressTest, TestCopyCtor) {
  SocketAddress from("1.2.3.4", 5678);
  SocketAddress addr(from);
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0x01020304U), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("1.2.3.4", addr.hostname());
  EXPECT_EQ("1.2.3.4:5678", addr.ToString());
}

TEST(SocketAddressTest, TestAssign) {
  SocketAddress from("1.2.3.4", 5678);
  SocketAddress addr(IPAddress(0x88888888), 9999);
  addr = from;
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0x01020304U), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("1.2.3.4", addr.hostname());
  EXPECT_EQ("1.2.3.4:5678", addr.ToString());
}

TEST(SocketAddressTest, TestSetIPPort) {
  SocketAddress addr(IPAddress(0x88888888), 9999);
  addr.SetIP(IPAddress(0x01020304));
  addr.SetPort(5678);
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0x01020304U), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("", addr.hostname());
  EXPECT_EQ("1.2.3.4:5678", addr.ToString());
}

TEST(SocketAddressTest, TestSetIPFromString) {
  SocketAddress addr(IPAddress(0x88888888), 9999);
  addr.SetIP("1.2.3.4");
  addr.SetPort(5678);
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0x01020304U), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("1.2.3.4", addr.hostname());
  EXPECT_EQ("1.2.3.4:5678", addr.ToString());
}

TEST(SocketAddressTest, TestSetIPFromHostname) {
  SocketAddress addr(IPAddress(0x88888888), 9999);
  addr.SetIP("a.b.com");
  addr.SetPort(5678);
  EXPECT_TRUE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("a.b.com", addr.hostname());
  EXPECT_EQ("a.b.com:5678", addr.ToString());
  addr.SetResolvedIP(IPAddress(0x01020304));
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0x01020304U), addr.ipaddr());
  EXPECT_EQ("a.b.com", addr.hostname());
  EXPECT_EQ("a.b.com:5678", addr.ToString());
}

TEST(SocketAddressTest, TestFromIPv4String) {
  SocketAddress addr;
  EXPECT_TRUE(addr.FromString("1.2.3.4:5678"));
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0x01020304U), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("1.2.3.4", addr.hostname());
  EXPECT_EQ("1.2.3.4:5678", addr.ToString());
}

TEST(SocketAddressTest, TestFromIPv6String) {
  SocketAddress addr;
  EXPECT_TRUE(addr.FromString(kTestV6AddrFullString));
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ(kTestV6AddrString, addr.hostname());
  EXPECT_EQ(kTestV6AddrFullString, addr.ToString());
}

TEST(SocketAddressTest, TestFromHostname) {
  SocketAddress addr;
  EXPECT_TRUE(addr.FromString("a.b.com:5678"));
  EXPECT_TRUE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("a.b.com", addr.hostname());
  EXPECT_EQ("a.b.com:5678", addr.ToString());
}

TEST(SocketAddressTest, TestToFromSockAddr) {
  SocketAddress from("1.2.3.4", 5678), addr;
  sockaddr_in addr_in;
  from.ToSockAddr(&addr_in);
  EXPECT_TRUE(addr.FromSockAddr(addr_in));
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0x01020304U), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("", addr.hostname());
  EXPECT_EQ("1.2.3.4:5678", addr.ToString());
}

TEST(SocketAddressTest, TestToFromSockAddrStorage) {
  SocketAddress from("1.2.3.4", 5678), addr;
  sockaddr_storage addr_storage;
  from.ToSockAddrStorage(&addr_storage);
  EXPECT_TRUE(SocketAddressFromSockAddrStorage(addr_storage, &addr));
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(0x01020304U), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("", addr.hostname());
  EXPECT_EQ("1.2.3.4:5678", addr.ToString());

  addr.Clear();
  from.ToDualStackSockAddrStorage(&addr_storage);
  EXPECT_TRUE(SocketAddressFromSockAddrStorage(addr_storage, &addr));
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(kMappedV4Addr), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("", addr.hostname());
  EXPECT_EQ("[::ffff:1.2.3.4]:5678", addr.ToString());

  addr.Clear();
  memset(&addr_storage, 0, sizeof(sockaddr_storage));
  from = SocketAddress(kTestV6AddrString, 5678);
  from.SetScopeID(6);
  from.ToSockAddrStorage(&addr_storage);
  EXPECT_TRUE(SocketAddressFromSockAddrStorage(addr_storage, &addr));
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(kTestV6Addr), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("", addr.hostname());
  EXPECT_EQ(kTestV6AddrFullString, addr.ToString());
  EXPECT_EQ(6, addr.scope_id());

  addr.Clear();
  from.ToDualStackSockAddrStorage(&addr_storage);
  EXPECT_TRUE(SocketAddressFromSockAddrStorage(addr_storage, &addr));
  EXPECT_FALSE(addr.IsUnresolvedIP());
  EXPECT_EQ(IPAddress(kTestV6Addr), addr.ipaddr());
  EXPECT_EQ(5678, addr.port());
  EXPECT_EQ("", addr.hostname());
  EXPECT_EQ(kTestV6AddrFullString, addr.ToString());
  EXPECT_EQ(6, addr.scope_id());

  addr = from;
  addr_storage.ss_family = AF_UNSPEC;
  EXPECT_FALSE(SocketAddressFromSockAddrStorage(addr_storage, &addr));
  EXPECT_EQ(from, addr);

  EXPECT_FALSE(SocketAddressFromSockAddrStorage(addr_storage, NULL));
}

bool AreEqual(const SocketAddress& addr1,
              const SocketAddress& addr2) {
  return addr1 == addr2 && addr2 == addr1 &&
      !(addr1 != addr2) && !(addr2 != addr1);
}

bool AreUnequal(const SocketAddress& addr1,
                const SocketAddress& addr2) {
  return !(addr1 == addr2) && !(addr2 == addr1) &&
      addr1 != addr2 && addr2 != addr1;
}

TEST(SocketAddressTest, TestEqualityOperators) {
  SocketAddress addr1("1.2.3.4", 5678);
  SocketAddress addr2("1.2.3.4", 5678);
  EXPECT_PRED2(AreEqual, addr1, addr2);

  addr2 = SocketAddress("0.0.0.1", 5678);
  EXPECT_PRED2(AreUnequal, addr1, addr2);

  addr2 = SocketAddress("1.2.3.4", 1234);
  EXPECT_PRED2(AreUnequal, addr1, addr2);

  addr2 = SocketAddress(kTestV6AddrString, 5678);
  EXPECT_PRED2(AreUnequal, addr1, addr2);

  addr1 = SocketAddress(kTestV6AddrString, 5678);
  EXPECT_PRED2(AreEqual, addr1, addr2);

  addr2 = SocketAddress(kTestV6AddrString, 1234);
  EXPECT_PRED2(AreUnequal, addr1, addr2);

  addr2 = SocketAddress("fe80::1", 5678);
  EXPECT_PRED2(AreUnequal, addr1, addr2);

  SocketAddress addr3("a.b.c.d", 1);
  SocketAddress addr4("b.b.c.d", 1);
  EXPECT_PRED2(AreUnequal, addr3, addr4);
  EXPECT_PRED2(AreEqual, addr3, addr3);

  addr3.SetIP(addr1.ip());
  addr4.SetIP(addr1.ip());
  EXPECT_PRED2(AreEqual,addr3, addr4);
}

bool IsLessThan(const SocketAddress& addr1, const SocketAddress& addr2) {
  return addr1 < addr2 &&
      !(addr2 < addr1) &&
      !(addr1 == addr2);
}

TEST(SocketAddressTest, TestComparisonOperator) {
  SocketAddress addr1("1.2.3.4", 5678);
  SocketAddress addr2("1.2.3.4", 5678);

  EXPECT_FALSE(addr1 < addr2);
  EXPECT_FALSE(addr2 < addr1);

  addr2 = SocketAddress("1.2.3.4", 5679);
  EXPECT_PRED2(IsLessThan, addr1, addr2);

  addr2 = SocketAddress("2.2.3.4", 49152);
  EXPECT_PRED2(IsLessThan, addr1, addr2);

  addr2 = SocketAddress(kTestV6AddrString, 5678);
  EXPECT_PRED2(IsLessThan, addr1, addr2);

  addr1 = SocketAddress("fe80::1", 5678);
  EXPECT_PRED2(IsLessThan, addr2, addr1);

  addr2 = SocketAddress("fe80::1", 5679);
  EXPECT_PRED2(IsLessThan, addr1, addr2);

  addr2 = SocketAddress("fe80::1", 5678);
  EXPECT_FALSE(addr1 < addr2);
  EXPECT_FALSE(addr2 < addr1);

  SocketAddress addr3("a.b.c.d", 1);
  SocketAddress addr4("b.b.c.d", 1);
  EXPECT_PRED2(IsLessThan, addr3, addr4);
}

TEST(SocketAddressTest, TestToSensitiveString) {
  SocketAddress addr_v4("1.2.3.4", 5678);
  EXPECT_EQ("1.2.3.4", addr_v4.HostAsURIString());
  EXPECT_EQ("1.2.3.4:5678", addr_v4.ToString());

#if defined(NDEBUG)
  EXPECT_EQ("1.2.3.x", addr_v4.HostAsSensitiveURIString());
  EXPECT_EQ("1.2.3.x:5678", addr_v4.ToSensitiveString());
#else
  EXPECT_EQ("1.2.3.4", addr_v4.HostAsSensitiveURIString());
  EXPECT_EQ("1.2.3.4:5678", addr_v4.ToSensitiveString());
#endif  // defined(NDEBUG)

  SocketAddress addr_v6(kTestV6AddrString, 5678);
  EXPECT_EQ("[" + kTestV6AddrString + "]", addr_v6.HostAsURIString());
  EXPECT_EQ(kTestV6AddrFullString, addr_v6.ToString());
#if defined(NDEBUG)
  EXPECT_EQ("[" + kTestV6AddrAnonymizedString + "]",
            addr_v6.HostAsSensitiveURIString());
  EXPECT_EQ(kTestV6AddrFullAnonymizedString, addr_v6.ToSensitiveString());
#else
  EXPECT_EQ("[" + kTestV6AddrString + "]", addr_v6.HostAsSensitiveURIString());
  EXPECT_EQ(kTestV6AddrFullString, addr_v6.ToSensitiveString());
#endif  // defined(NDEBUG)
}

}  // namespace rtc
