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

extern "C" {
#include "webrtc/modules/audio_processing/aec/aec_core.h"
}
#include "webrtc/modules/audio_processing/aec/echo_cancellation_internal.h"
#include "webrtc/modules/audio_processing/aec/include/echo_cancellation.h"
#include "webrtc/typedefs.h"

namespace {

class SystemDelayTest : public ::testing::Test {
 protected:
  SystemDelayTest();
  virtual void SetUp();
  virtual void TearDown();

  // Initialization of AEC handle with respect to |sample_rate_hz|. Since the
  // device sample rate is unimportant we set that value to 48000 Hz.
  void Init(int sample_rate_hz);

  // Makes one render call and one capture call in that specific order.
  void RenderAndCapture(int device_buffer_ms);

  // Fills up the far-end buffer with respect to the default device buffer size.
  int BufferFillUp();

  // Runs and verifies the behavior in a stable startup procedure.
  void RunStableStartup();

  // Maps buffer size in ms into samples, taking the unprocessed frame into
  // account.
  int MapBufferSizeToSamples(int size_in_ms);

  void* handle_;
  aecpc_t* self_;
  int samples_per_frame_;
  // Dummy input/output speech data.
  int16_t far_[160];
  int16_t near_[160];
  int16_t out_[160];
};

SystemDelayTest::SystemDelayTest()
    : handle_(NULL), self_(NULL), samples_per_frame_(0) {
  // Dummy input data are set with more or less arbitrary non-zero values.
  memset(far_, 1, sizeof(far_));
  memset(near_, 2, sizeof(near_));
  memset(out_, 0, sizeof(out_));
}

void SystemDelayTest::SetUp() {
  ASSERT_EQ(0, WebRtcAec_Create(&handle_));
  self_ = reinterpret_cast<aecpc_t*>(handle_);
}

void SystemDelayTest::TearDown() {
  // Free AEC
  ASSERT_EQ(0, WebRtcAec_Free(handle_));
  handle_ = NULL;
}

// In SWB mode nothing is added to the buffer handling with respect to
// functionality compared to WB. We therefore only verify behavior in NB and WB.
static const int kSampleRateHz[] = {8000, 16000};
static const size_t kNumSampleRates =
    sizeof(kSampleRateHz) / sizeof(*kSampleRateHz);

// Default audio device buffer size used.
static const int kDeviceBufMs = 100;

// Requirement for a stable device convergence time in ms. Should converge in
// less than |kStableConvergenceMs|.
static const int kStableConvergenceMs = 100;

// Maximum convergence time in ms. This means that we should leave the startup
// phase after |kMaxConvergenceMs| independent of device buffer stability
// conditions.
static const int kMaxConvergenceMs = 500;

void SystemDelayTest::Init(int sample_rate_hz) {
  // Initialize AEC
  EXPECT_EQ(0, WebRtcAec_Init(handle_, sample_rate_hz, 48000));

  // One frame equals 10 ms of data.
  samples_per_frame_ = sample_rate_hz / 100;
}

void SystemDelayTest::RenderAndCapture(int device_buffer_ms) {
  EXPECT_EQ(0, WebRtcAec_BufferFarend(handle_, far_, samples_per_frame_));
  EXPECT_EQ(0,
            WebRtcAec_Process(handle_,
                              near_,
                              NULL,
                              out_,
                              NULL,
                              samples_per_frame_,
                              device_buffer_ms,
                              0));
}

int SystemDelayTest::BufferFillUp() {
  // To make sure we have a full buffer when we verify stability we first fill
  // up the far-end buffer with the same amount as we will report in through
  // Process().
  int buffer_size = 0;
  for (int i = 0; i < kDeviceBufMs / 10; i++) {
    EXPECT_EQ(0, WebRtcAec_BufferFarend(handle_, far_, samples_per_frame_));
    buffer_size += samples_per_frame_;
    EXPECT_EQ(buffer_size, WebRtcAec_system_delay(self_->aec));
  }
  return buffer_size;
}

void SystemDelayTest::RunStableStartup() {
  // To make sure we have a full buffer when we verify stability we first fill
  // up the far-end buffer with the same amount as we will report in through
  // Process().
  int buffer_size = BufferFillUp();
  // A stable device should be accepted and put in a regular process mode within
  // |kStableConvergenceMs|.
  int process_time_ms = 0;
  for (; process_time_ms < kStableConvergenceMs; process_time_ms += 10) {
    RenderAndCapture(kDeviceBufMs);
    buffer_size += samples_per_frame_;
    if (self_->startup_phase == 0) {
      // We have left the startup phase.
      break;
    }
  }
  // Verify convergence time.
  EXPECT_GT(kStableConvergenceMs, process_time_ms);
  // Verify that the buffer has been flushed.
  EXPECT_GE(buffer_size, WebRtcAec_system_delay(self_->aec));
}

int SystemDelayTest::MapBufferSizeToSamples(int size_in_ms) {
  // The extra 10 ms corresponds to the unprocessed frame.
  return (size_in_ms + 10) * samples_per_frame_ / 10;
}

// The tests should meet basic requirements and not be adjusted to what is
// actually implemented. If we don't get good code coverage this way we either
// lack in tests or have unnecessary code.
// General requirements:
// 1) If we add far-end data the system delay should be increased with the same
//    amount we add.
// 2) If the far-end buffer is full we should flush the oldest data to make room
//    for the new. In this case the system delay is unaffected.
// 3) There should exist a startup phase in which the buffer size is to be
//    determined. In this phase no cancellation should be performed.
// 4) Under stable conditions (small variations in device buffer sizes) the AEC
//    should determine an appropriate local buffer size within
//    |kStableConvergenceMs| ms.
// 5) Under unstable conditions the AEC should make a decision within
//    |kMaxConvergenceMs| ms.
// 6) If the local buffer runs out of data we should stuff the buffer with older
//    frames.
// 7) The system delay should within |kMaxConvergenceMs| ms heal from
//    disturbances like drift, data glitches, toggling events and outliers.
// 8) The system delay should never become negative.

TEST_F(SystemDelayTest, CorrectIncreaseWhenBufferFarend) {
  // When we add data to the AEC buffer the internal system delay should be
  // incremented with the same amount as the size of data.
  for (size_t i = 0; i < kNumSampleRates; i++) {
    Init(kSampleRateHz[i]);

    // Loop through a couple of calls to make sure the system delay increments
    // correctly.
    for (int j = 1; j <= 5; j++) {
      EXPECT_EQ(0, WebRtcAec_BufferFarend(handle_, far_, samples_per_frame_));
      EXPECT_EQ(j * samples_per_frame_, WebRtcAec_system_delay(self_->aec));
    }
  }
}

// TODO(bjornv): Add a test to verify behavior if the far-end buffer is full
// when adding new data.

TEST_F(SystemDelayTest, CorrectDelayAfterStableStartup) {
  // We run the system in a stable startup. After that we verify that the system
  // delay meets the requirements.
  for (size_t i = 0; i < kNumSampleRates; i++) {
    Init(kSampleRateHz[i]);
    RunStableStartup();

    // Verify system delay with respect to requirements, i.e., the
    // |system_delay| is in the interval [75%, 100%] of what's reported on the
    // average.
    int average_reported_delay = kDeviceBufMs * samples_per_frame_ / 10;
    EXPECT_GE(average_reported_delay, WebRtcAec_system_delay(self_->aec));
    EXPECT_LE(average_reported_delay * 3 / 4,
              WebRtcAec_system_delay(self_->aec));
  }
}

TEST_F(SystemDelayTest, CorrectDelayAfterUnstableStartup) {
  // In an unstable system we would start processing after |kMaxConvergenceMs|.
  // On the last frame the AEC buffer is adjusted to 60% of the last reported
  // device buffer size.
  // We construct an unstable system by altering the device buffer size between
  // two values |kDeviceBufMs| +- 25 ms.
  for (size_t i = 0; i < kNumSampleRates; i++) {
    Init(kSampleRateHz[i]);

    // To make sure we have a full buffer when we verify stability we first fill
    // up the far-end buffer with the same amount as we will report in on the
    // average through Process().
    int buffer_size = BufferFillUp();

    int buffer_offset_ms = 25;
    int reported_delay_ms = 0;
    int process_time_ms = 0;
    for (; process_time_ms <= kMaxConvergenceMs; process_time_ms += 10) {
      reported_delay_ms = kDeviceBufMs + buffer_offset_ms;
      RenderAndCapture(reported_delay_ms);
      buffer_size += samples_per_frame_;
      buffer_offset_ms = -buffer_offset_ms;
      if (self_->startup_phase == 0) {
        // We have left the startup phase.
        break;
      }
    }
    // Verify convergence time.
    EXPECT_GE(kMaxConvergenceMs, process_time_ms);
    // Verify that the buffer has been flushed.
    EXPECT_GE(buffer_size, WebRtcAec_system_delay(self_->aec));

    // Verify system delay with respect to requirements, i.e., the
    // |system_delay| is in the interval [60%, 100%] of what's last reported.
    EXPECT_GE(reported_delay_ms * samples_per_frame_ / 10,
              WebRtcAec_system_delay(self_->aec));
    EXPECT_LE(reported_delay_ms * samples_per_frame_ / 10 * 3 / 5,
              WebRtcAec_system_delay(self_->aec));
  }
}

TEST_F(SystemDelayTest, CorrectDelayAfterStableBufferBuildUp) {
  // In this test we start by establishing the device buffer size during stable
  // conditions, but with an empty internal far-end buffer. Once that is done we
  // verify that the system delay is increased correctly until we have reach an
  // internal buffer size of 75% of what's been reported.
  for (size_t i = 0; i < kNumSampleRates; i++) {
    Init(kSampleRateHz[i]);

    // We assume that running |kStableConvergenceMs| calls will put the
    // algorithm in a state where the device buffer size has been determined. We
    // can make that assumption since we have a separate stability test.
    int process_time_ms = 0;
    for (; process_time_ms < kStableConvergenceMs; process_time_ms += 10) {
      EXPECT_EQ(0,
                WebRtcAec_Process(handle_,
                                  near_,
                                  NULL,
                                  out_,
                                  NULL,
                                  samples_per_frame_,
                                  kDeviceBufMs,
                                  0));
    }
    // Verify that a buffer size has been established.
    EXPECT_EQ(0, self_->checkBuffSize);

    // We now have established the required buffer size. Let us verify that we
    // fill up before leaving the startup phase for normal processing.
    int buffer_size = 0;
    int target_buffer_size = kDeviceBufMs * samples_per_frame_ / 10 * 3 / 4;
    process_time_ms = 0;
    for (; process_time_ms <= kMaxConvergenceMs; process_time_ms += 10) {
      RenderAndCapture(kDeviceBufMs);
      buffer_size += samples_per_frame_;
      if (self_->startup_phase == 0) {
        // We have left the startup phase.
        break;
      }
    }
    // Verify convergence time.
    EXPECT_GT(kMaxConvergenceMs, process_time_ms);
    // Verify that the buffer has reached the desired size.
    EXPECT_LE(target_buffer_size, WebRtcAec_system_delay(self_->aec));

    // Verify normal behavior (system delay is kept constant) after startup by
    // running a couple of calls to BufferFarend() and Process().
    for (int j = 0; j < 6; j++) {
      int system_delay_before_calls = WebRtcAec_system_delay(self_->aec);
      RenderAndCapture(kDeviceBufMs);
      EXPECT_EQ(system_delay_before_calls, WebRtcAec_system_delay(self_->aec));
    }
  }
}

TEST_F(SystemDelayTest, CorrectDelayWhenBufferUnderrun) {
  // Here we test a buffer under run scenario. If we keep on calling
  // WebRtcAec_Process() we will finally run out of data, but should
  // automatically stuff the buffer. We verify this behavior by checking if the
  // system delay goes negative.
  for (size_t i = 0; i < kNumSampleRates; i++) {
    Init(kSampleRateHz[i]);
    RunStableStartup();

    // The AEC has now left the Startup phase. We now have at most
    // |kStableConvergenceMs| in the buffer. Keep on calling Process() until
    // we run out of data and verify that the system delay is non-negative.
    for (int j = 0; j <= kStableConvergenceMs; j += 10) {
      EXPECT_EQ(0,
                WebRtcAec_Process(handle_,
                                  near_,
                                  NULL,
                                  out_,
                                  NULL,
                                  samples_per_frame_,
                                  kDeviceBufMs,
                                  0));
      EXPECT_LE(0, WebRtcAec_system_delay(self_->aec));
    }
  }
}

TEST_F(SystemDelayTest, CorrectDelayDuringDrift) {
  // This drift test should verify that the system delay is never exceeding the
  // device buffer. The drift is simulated by decreasing the reported device
  // buffer size by 1 ms every 100 ms. If the device buffer size goes below 30
  // ms we jump (add) 10 ms to give a repeated pattern.
  for (size_t i = 0; i < kNumSampleRates; i++) {
    Init(kSampleRateHz[i]);
    RunStableStartup();

    // We have now left the startup phase and proceed with normal processing.
    int jump = 0;
    for (int j = 0; j < 1000; j++) {
      // Drift = -1 ms per 100 ms of data.
      int device_buf_ms = kDeviceBufMs - (j / 10) + jump;
      int device_buf = MapBufferSizeToSamples(device_buf_ms);

      if (device_buf_ms < 30) {
        // Add 10 ms data, taking affect next frame.
        jump += 10;
      }
      RenderAndCapture(device_buf_ms);

      // Verify that the system delay does not exceed the device buffer.
      EXPECT_GE(device_buf, WebRtcAec_system_delay(self_->aec));

      // Verify that the system delay is non-negative.
      EXPECT_LE(0, WebRtcAec_system_delay(self_->aec));
    }
  }
}

TEST_F(SystemDelayTest, ShouldRecoverAfterGlitch) {
  // This glitch test should verify that the system delay recovers if there is
  // a glitch in data. The data glitch is constructed as 200 ms of buffering
  // after which the stable procedure continues. The glitch is never reported by
  // the device.
  // The system is said to be in a non-causal state if the difference between
  // the device buffer and system delay is less than a block (64 samples).
  for (size_t i = 0; i < kNumSampleRates; i++) {
    Init(kSampleRateHz[i]);
    RunStableStartup();
    int device_buf = MapBufferSizeToSamples(kDeviceBufMs);
    // Glitch state.
    for (int j = 0; j < 20; j++) {
      EXPECT_EQ(0, WebRtcAec_BufferFarend(handle_, far_, samples_per_frame_));
      // No need to verify system delay, since that is done in a separate test.
    }
    // Verify that we are in a non-causal state, i.e.,
    // |system_delay| > |device_buf|.
    EXPECT_LT(device_buf, WebRtcAec_system_delay(self_->aec));

    // Recover state. Should recover at least 4 ms of data per 10 ms, hence a
    // glitch of 200 ms will take at most 200 * 10 / 4 = 500 ms to recover from.
    bool non_causal = true;  // We are currently in a non-causal state.
    for (int j = 0; j < 50; j++) {
      int system_delay_before = WebRtcAec_system_delay(self_->aec);
      RenderAndCapture(kDeviceBufMs);
      int system_delay_after = WebRtcAec_system_delay(self_->aec);

      // We have recovered if |device_buf| - |system_delay_after| >= 64 (one
      // block). During recovery |system_delay_after| < |system_delay_before|,
      // otherwise they are equal.
      if (non_causal) {
        EXPECT_LT(system_delay_after, system_delay_before);
        if (device_buf - system_delay_after >= 64) {
          non_causal = false;
        }
      } else {
        EXPECT_EQ(system_delay_before, system_delay_after);
      }
      // Verify that the system delay is non-negative.
      EXPECT_LE(0, WebRtcAec_system_delay(self_->aec));
    }
    // Check that we have recovered.
    EXPECT_FALSE(non_causal);
  }
}

TEST_F(SystemDelayTest, UnaffectedWhenSpuriousDeviceBufferValues) {
  // This spurious device buffer data test aims at verifying that the system
  // delay is unaffected by large outliers.
  // The system is said to be in a non-causal state if the difference between
  // the device buffer and system delay is less than a block (64 samples).
  for (size_t i = 0; i < kNumSampleRates; i++) {
    Init(kSampleRateHz[i]);
    RunStableStartup();
    int device_buf = MapBufferSizeToSamples(kDeviceBufMs);

    // Normal state. We are currently not in a non-causal state.
    bool non_causal = false;

    // Run 1 s and replace device buffer size with 500 ms every 100 ms.
    for (int j = 0; j < 100; j++) {
      int system_delay_before_calls = WebRtcAec_system_delay(self_->aec);
      int device_buf_ms = kDeviceBufMs;
      if (j % 10 == 0) {
        device_buf_ms = 500;
      }
      RenderAndCapture(device_buf_ms);

      // Check for non-causality.
      if (device_buf - WebRtcAec_system_delay(self_->aec) < 64) {
        non_causal = true;
      }
      EXPECT_FALSE(non_causal);
      EXPECT_EQ(system_delay_before_calls, WebRtcAec_system_delay(self_->aec));

      // Verify that the system delay is non-negative.
      EXPECT_LE(0, WebRtcAec_system_delay(self_->aec));
    }
  }
}

TEST_F(SystemDelayTest, CorrectImpactWhenTogglingDeviceBufferValues) {
  // This test aims at verifying that the system delay is "unaffected" by
  // toggling values reported by the device.
  // The test is constructed such that every other device buffer value is zero
  // and then 2 * |kDeviceBufMs|, hence the size is constant on the average. The
  // zero values will force us into a non-causal state and thereby lowering the
  // system delay until we basically runs out of data. Once that happens the
  // buffer will be stuffed.
  // TODO(bjornv): This test will have a better impact if we verified that the
  // delay estimate goes up when the system delay goes done to meet the average
  // device buffer size.
  for (size_t i = 0; i < kNumSampleRates; i++) {
    Init(kSampleRateHz[i]);
    RunStableStartup();
    int device_buf = MapBufferSizeToSamples(kDeviceBufMs);

    // Normal state. We are currently not in a non-causal state.
    bool non_causal = false;

    // Loop through 100 frames (both render and capture), which equals 1 s of
    // data. Every odd frame we set the device buffer size to 2 * |kDeviceBufMs|
    // and even frames we set the device buffer size to zero.
    for (int j = 0; j < 100; j++) {
      int system_delay_before_calls = WebRtcAec_system_delay(self_->aec);
      int device_buf_ms = 2 * (j % 2) * kDeviceBufMs;
      RenderAndCapture(device_buf_ms);

      // Check for non-causality, compared with the average device buffer size.
      non_causal |= (device_buf - WebRtcAec_system_delay(self_->aec) < 64);
      EXPECT_GE(system_delay_before_calls, WebRtcAec_system_delay(self_->aec));

      // Verify that the system delay is non-negative.
      EXPECT_LE(0, WebRtcAec_system_delay(self_->aec));
    }
    // Verify we are not in a non-causal state.
    EXPECT_FALSE(non_causal);
  }
}

}  // namespace
