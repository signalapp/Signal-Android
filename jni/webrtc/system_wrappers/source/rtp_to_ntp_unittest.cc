/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/system_wrappers/interface/rtp_to_ntp.h"

namespace webrtc {

TEST(WrapAroundTests, NoWrap) {
  EXPECT_EQ(0, CheckForWrapArounds(0xFFFFFFFF, 0xFFFFFFFE));
  EXPECT_EQ(0, CheckForWrapArounds(1, 0));
  EXPECT_EQ(0, CheckForWrapArounds(0x00010000, 0x0000FFFF));
}

TEST(WrapAroundTests, ForwardWrap) {
  EXPECT_EQ(1, CheckForWrapArounds(0, 0xFFFFFFFF));
  EXPECT_EQ(1, CheckForWrapArounds(0, 0xFFFF0000));
  EXPECT_EQ(1, CheckForWrapArounds(0x0000FFFF, 0xFFFFFFFF));
  EXPECT_EQ(1, CheckForWrapArounds(0x0000FFFF, 0xFFFF0000));
}

TEST(WrapAroundTests, BackwardWrap) {
  EXPECT_EQ(-1, CheckForWrapArounds(0xFFFFFFFF, 0));
  EXPECT_EQ(-1, CheckForWrapArounds(0xFFFF0000, 0));
  EXPECT_EQ(-1, CheckForWrapArounds(0xFFFFFFFF, 0x0000FFFF));
  EXPECT_EQ(-1, CheckForWrapArounds(0xFFFF0000, 0x0000FFFF));
}

TEST(WrapAroundTests, OldRtcpWrapped) {
  RtcpList rtcp;
  uint32_t ntp_sec = 0;
  uint32_t ntp_frac = 0;
  uint32_t timestamp = 0;
  const uint32_t kOneMsInNtpFrac = 4294967;
  const uint32_t kTimestampTicksPerMs = 90;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp -= kTimestampTicksPerMs;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp -= kTimestampTicksPerMs;
  int64_t timestamp_in_ms = -1;
  // This expected to fail since it's highly unlikely that the older RTCP
  // has a much smaller RTP timestamp than the newer.
  EXPECT_FALSE(RtpToNtpMs(timestamp, rtcp, &timestamp_in_ms));
}

TEST(WrapAroundTests, NewRtcpWrapped) {
  RtcpList rtcp;
  uint32_t ntp_sec = 0;
  uint32_t ntp_frac = 0;
  uint32_t timestamp = 0xFFFFFFFF;
  const uint32_t kOneMsInNtpFrac = 4294967;
  const uint32_t kTimestampTicksPerMs = 90;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp += kTimestampTicksPerMs;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  int64_t timestamp_in_ms = -1;
  EXPECT_TRUE(RtpToNtpMs(rtcp.back().rtp_timestamp, rtcp, &timestamp_in_ms));
  // Since this RTP packet has the same timestamp as the RTCP packet constructed
  // at time 0 it should be mapped to 0 as well.
  EXPECT_EQ(0, timestamp_in_ms);
}

TEST(WrapAroundTests, RtpWrapped) {
  const uint32_t kOneMsInNtpFrac = 4294967;
  const uint32_t kTimestampTicksPerMs = 90;
  RtcpList rtcp;
  uint32_t ntp_sec = 0;
  uint32_t ntp_frac = 0;
  uint32_t timestamp = 0xFFFFFFFF - 2 * kTimestampTicksPerMs;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp += kTimestampTicksPerMs;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp += kTimestampTicksPerMs;
  int64_t timestamp_in_ms = -1;
  EXPECT_TRUE(RtpToNtpMs(timestamp, rtcp, &timestamp_in_ms));
  // Since this RTP packet has the same timestamp as the RTCP packet constructed
  // at time 0 it should be mapped to 0 as well.
  EXPECT_EQ(2, timestamp_in_ms);
}

TEST(WrapAroundTests, OldRtp_RtcpsWrapped) {
  const uint32_t kOneMsInNtpFrac = 4294967;
  const uint32_t kTimestampTicksPerMs = 90;
  RtcpList rtcp;
  uint32_t ntp_sec = 0;
  uint32_t ntp_frac = 0;
  uint32_t timestamp = 0;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp += kTimestampTicksPerMs;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp -= 2*kTimestampTicksPerMs;
  int64_t timestamp_in_ms = -1;
  EXPECT_FALSE(RtpToNtpMs(timestamp, rtcp, &timestamp_in_ms));
}

TEST(WrapAroundTests, OldRtp_NewRtcpWrapped) {
  const uint32_t kOneMsInNtpFrac = 4294967;
  const uint32_t kTimestampTicksPerMs = 90;
  RtcpList rtcp;
  uint32_t ntp_sec = 0;
  uint32_t ntp_frac = 0;
  uint32_t timestamp = 0xFFFFFFFF;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp += kTimestampTicksPerMs;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp -= kTimestampTicksPerMs;
  int64_t timestamp_in_ms = -1;
  EXPECT_TRUE(RtpToNtpMs(timestamp, rtcp, &timestamp_in_ms));
  // Constructed at the same time as the first RTCP and should therefore be
  // mapped to zero.
  EXPECT_EQ(0, timestamp_in_ms);
}

TEST(WrapAroundTests, OldRtp_OldRtcpWrapped) {
  const uint32_t kOneMsInNtpFrac = 4294967;
  const uint32_t kTimestampTicksPerMs = 90;
  RtcpList rtcp;
  uint32_t ntp_sec = 0;
  uint32_t ntp_frac = 0;
  uint32_t timestamp = 0;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp -= kTimestampTicksPerMs;
  rtcp.push_front(RtcpMeasurement(ntp_sec, ntp_frac, timestamp));
  ntp_frac += kOneMsInNtpFrac;
  timestamp += 2*kTimestampTicksPerMs;
  int64_t timestamp_in_ms = -1;
  EXPECT_FALSE(RtpToNtpMs(timestamp, rtcp, &timestamp_in_ms));
}
};  // namespace webrtc
