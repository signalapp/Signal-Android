/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Unit tests for DelayPeakDetector class.

#include "webrtc/modules/audio_coding/neteq/delay_peak_detector.h"

#include "testing/gtest/include/gtest/gtest.h"

namespace webrtc {

TEST(DelayPeakDetector, CreateAndDestroy) {
  TickTimer tick_timer;
  DelayPeakDetector* detector = new DelayPeakDetector(&tick_timer);
  EXPECT_FALSE(detector->peak_found());
  delete detector;
}

TEST(DelayPeakDetector, EmptyHistory) {
  TickTimer tick_timer;
  DelayPeakDetector detector(&tick_timer);
  EXPECT_EQ(-1, detector.MaxPeakHeight());
  EXPECT_EQ(0u, detector.MaxPeakPeriod());
}

// Inject a series of packet arrivals into the detector. Three of the packets
// have suffered delays. After the third delay peak, peak-mode is expected to
// start. This should then continue until it is disengaged due to lack of peaks.
TEST(DelayPeakDetector, TriggerPeakMode) {
  TickTimer tick_timer;
  DelayPeakDetector detector(&tick_timer);
  const int kPacketSizeMs = 30;
  detector.SetPacketAudioLength(kPacketSizeMs);

  // Load up normal arrival times; 0 ms, 30 ms, 60 ms, 90 ms, ...
  const int kNumPackets = 1000;
  int arrival_times_ms[kNumPackets];
  for (int i = 0; i < kNumPackets; ++i) {
    arrival_times_ms[i] = i * kPacketSizeMs;
  }

  // Delay three packets.
  const int kPeakDelayMs = 100;
  // First delay peak.
  arrival_times_ms[100] += kPeakDelayMs;
  // Second delay peak.
  arrival_times_ms[200] += kPeakDelayMs;
  // Third delay peak. Trigger peak-mode after this packet.
  arrival_times_ms[400] += kPeakDelayMs;
  // The second peak period is the longest, 200 packets.
  const uint64_t kWorstPeakPeriod = 200 * kPacketSizeMs;
  int peak_mode_start_ms = arrival_times_ms[400];
  // Expect to disengage after no peaks are observed for two period times.
  int peak_mode_end_ms = peak_mode_start_ms + 2 * kWorstPeakPeriod;

  // Load into detector.
  int time = 0;
  int next = 1;  // Start with the second packet to get a proper IAT.
  while (next < kNumPackets) {
    while (next < kNumPackets && arrival_times_ms[next] <= time) {
      int iat_packets = (arrival_times_ms[next] - arrival_times_ms[next - 1]) /
          kPacketSizeMs;
      const int kTargetBufferLevel = 1;  // Define peaks to be iat > 2.
      if (time < peak_mode_start_ms || time > peak_mode_end_ms) {
        EXPECT_FALSE(detector.Update(iat_packets, kTargetBufferLevel));
      } else {
        EXPECT_TRUE(detector.Update(iat_packets, kTargetBufferLevel));
        EXPECT_EQ(kWorstPeakPeriod, detector.MaxPeakPeriod());
        EXPECT_EQ(kPeakDelayMs / kPacketSizeMs + 1, detector.MaxPeakHeight());
      }
      ++next;
    }
    tick_timer.Increment();
    time += 10;  // Increase time 10 ms.
  }
}

// Same test as TriggerPeakMode, but with base target buffer level increased to
// 2, in order to raise the bar for delay peaks to inter-arrival times > 4.
// The delay pattern has peaks with delay = 3, thus should not trigger.
TEST(DelayPeakDetector, DoNotTriggerPeakMode) {
  TickTimer tick_timer;
  DelayPeakDetector detector(&tick_timer);
  const int kPacketSizeMs = 30;
  detector.SetPacketAudioLength(kPacketSizeMs);

  // Load up normal arrival times; 0 ms, 30 ms, 60 ms, 90 ms, ...
  const int kNumPackets = 1000;
  int arrival_times_ms[kNumPackets];
  for (int i = 0; i < kNumPackets; ++i) {
    arrival_times_ms[i] = i * kPacketSizeMs;
  }

  // Delay three packets.
  const int kPeakDelayMs = 100;
  // First delay peak.
  arrival_times_ms[100] += kPeakDelayMs;
  // Second delay peak.
  arrival_times_ms[200] += kPeakDelayMs;
  // Third delay peak.
  arrival_times_ms[400] += kPeakDelayMs;

  // Load into detector.
  int time = 0;
  int next = 1;  // Start with the second packet to get a proper IAT.
  while (next < kNumPackets) {
    while (next < kNumPackets && arrival_times_ms[next] <= time) {
      int iat_packets = (arrival_times_ms[next] - arrival_times_ms[next - 1]) /
          kPacketSizeMs;
      const int kTargetBufferLevel = 2;  // Define peaks to be iat > 4.
      EXPECT_FALSE(detector.Update(iat_packets, kTargetBufferLevel));
      ++next;
    }
    tick_timer.Increment();
    time += 10;  // Increase time 10 ms.
  }
}

// In situations with reordered packets, the DelayPeakDetector may be updated
// back-to-back (i.e., without the tick_timer moving) but still with non-zero
// inter-arrival time. This test is to make sure that this does not cause
// problems.
TEST(DelayPeakDetector, ZeroDistancePeaks) {
  TickTimer tick_timer;
  DelayPeakDetector detector(&tick_timer);
  const int kPacketSizeMs = 30;
  detector.SetPacketAudioLength(kPacketSizeMs);

  const int kTargetBufferLevel = 2;  // Define peaks to be iat > 4.
  const int kInterArrivalTime = 3 * kTargetBufferLevel;  // Will trigger a peak.
  EXPECT_FALSE(detector.Update(kInterArrivalTime, kTargetBufferLevel));
  EXPECT_FALSE(detector.Update(kInterArrivalTime, kTargetBufferLevel));
  EXPECT_FALSE(detector.Update(kInterArrivalTime, kTargetBufferLevel));
}

}  // namespace webrtc
