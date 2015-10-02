/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/system_wrappers/interface/rtp_to_ntp.h"

#include "webrtc/system_wrappers/interface/clock.h"

#include <assert.h>

namespace webrtc {

RtcpMeasurement::RtcpMeasurement()
    : ntp_secs(0), ntp_frac(0), rtp_timestamp(0) {}

RtcpMeasurement::RtcpMeasurement(uint32_t ntp_secs, uint32_t ntp_frac,
                                 uint32_t timestamp)
    : ntp_secs(ntp_secs), ntp_frac(ntp_frac), rtp_timestamp(timestamp) {}

// Calculates the RTP timestamp frequency from two pairs of NTP and RTP
// timestamps.
bool CalculateFrequency(
    int64_t rtcp_ntp_ms1,
    uint32_t rtp_timestamp1,
    int64_t rtcp_ntp_ms2,
    uint32_t rtp_timestamp2,
    double* frequency_khz) {
  if (rtcp_ntp_ms1 <= rtcp_ntp_ms2) {
    return false;
  }
  *frequency_khz = static_cast<double>(rtp_timestamp1 - rtp_timestamp2) /
      static_cast<double>(rtcp_ntp_ms1 - rtcp_ntp_ms2);
  return true;
}

// Detects if there has been a wraparound between |old_timestamp| and
// |new_timestamp|, and compensates by adding 2^32 if that is the case.
bool CompensateForWrapAround(uint32_t new_timestamp,
                             uint32_t old_timestamp,
                             int64_t* compensated_timestamp) {
  assert(compensated_timestamp);
  int64_t wraps = CheckForWrapArounds(new_timestamp, old_timestamp);
  if (wraps < 0) {
    // Reordering, don't use this packet.
    return false;
  }
  *compensated_timestamp = new_timestamp + (wraps << 32);
  return true;
}

bool UpdateRtcpList(uint32_t ntp_secs,
                    uint32_t ntp_frac,
                    uint32_t rtp_timestamp,
                    RtcpList* rtcp_list,
                    bool* new_rtcp_sr) {
  *new_rtcp_sr = false;
  if (ntp_secs == 0 && ntp_frac == 0) {
    return false;
  }

  RtcpMeasurement measurement;
  measurement.ntp_secs = ntp_secs;
  measurement.ntp_frac = ntp_frac;
  measurement.rtp_timestamp = rtp_timestamp;

  for (RtcpList::iterator it = rtcp_list->begin();
       it != rtcp_list->end(); ++it) {
    if (measurement.ntp_secs == (*it).ntp_secs &&
        measurement.ntp_frac == (*it).ntp_frac) {
      // This RTCP has already been added to the list.
      return true;
    }
  }

  // We need two RTCP SR reports to map between RTP and NTP. More than two will
  // not improve the mapping.
  if (rtcp_list->size() == 2) {
    rtcp_list->pop_back();
  }
  rtcp_list->push_front(measurement);
  *new_rtcp_sr = true;
  return true;
}

// Converts |rtp_timestamp| to the NTP time base using the NTP and RTP timestamp
// pairs in |rtcp|. The converted timestamp is returned in
// |rtp_timestamp_in_ms|. This function compensates for wrap arounds in RTP
// timestamps and returns false if it can't do the conversion due to reordering.
bool RtpToNtpMs(int64_t rtp_timestamp,
                const RtcpList& rtcp,
                int64_t* rtp_timestamp_in_ms) {
  assert(rtcp.size() == 2);
  int64_t rtcp_ntp_ms_new = Clock::NtpToMs(rtcp.front().ntp_secs,
                                           rtcp.front().ntp_frac);
  int64_t rtcp_ntp_ms_old = Clock::NtpToMs(rtcp.back().ntp_secs,
                                           rtcp.back().ntp_frac);
  int64_t rtcp_timestamp_new = rtcp.front().rtp_timestamp;
  int64_t rtcp_timestamp_old = rtcp.back().rtp_timestamp;
  if (!CompensateForWrapAround(rtcp_timestamp_new,
                               rtcp_timestamp_old,
                               &rtcp_timestamp_new)) {
    return false;
  }
  double freq_khz;
  if (!CalculateFrequency(rtcp_ntp_ms_new,
                          rtcp_timestamp_new,
                          rtcp_ntp_ms_old,
                          rtcp_timestamp_old,
                          &freq_khz)) {
    return false;
  }
  double offset = rtcp_timestamp_new - freq_khz * rtcp_ntp_ms_new;
  int64_t rtp_timestamp_unwrapped;
  if (!CompensateForWrapAround(rtp_timestamp, rtcp_timestamp_old,
                               &rtp_timestamp_unwrapped)) {
    return false;
  }
  double rtp_timestamp_ntp_ms = (static_cast<double>(rtp_timestamp_unwrapped) -
      offset) / freq_khz + 0.5f;
  if (rtp_timestamp_ntp_ms < 0) {
    return false;
  }
  *rtp_timestamp_in_ms = rtp_timestamp_ntp_ms;
  return true;
}

int CheckForWrapArounds(uint32_t new_timestamp, uint32_t old_timestamp) {
  if (new_timestamp < old_timestamp) {
    // This difference should be less than -2^31 if we have had a wrap around
    // (e.g. |new_timestamp| = 1, |rtcp_rtp_timestamp| = 2^32 - 1). Since it is
    // cast to a int32_t, it should be positive.
    if (static_cast<int32_t>(new_timestamp - old_timestamp) > 0) {
      // Forward wrap around.
      return 1;
    }
  } else if (static_cast<int32_t>(old_timestamp - new_timestamp) > 0) {
    // This difference should be less than -2^31 if we have had a backward wrap
    // around. Since it is cast to a int32_t, it should be positive.
    return -1;
  }
  return 0;
}

}  // namespace webrtc
