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

#include "webrtc/modules/audio_processing/utility/delay_estimator.h"
#include "webrtc/modules/audio_processing/utility/delay_estimator_internal.h"
#include "webrtc/modules/audio_processing/utility/delay_estimator_wrapper.h"
#include "webrtc/typedefs.h"

namespace {

enum { kSpectrumSize = 65 };
// Delay history sizes.
enum { kMaxDelay = 100 };
enum { kLookahead = 10 };
enum { kHistorySize = kMaxDelay + kLookahead };
// Length of binary spectrum sequence.
enum { kSequenceLength = 400 };

const int kDifferentHistorySize = 3;
const int kDifferentLookahead = 1;

const int kEnable[] = { 0, 1 };
const size_t kSizeEnable = sizeof(kEnable) / sizeof(*kEnable);

class DelayEstimatorTest : public ::testing::Test {
 protected:
  DelayEstimatorTest();
  virtual void SetUp();
  virtual void TearDown();

  void Init();
  void InitBinary();
  void VerifyDelay(BinaryDelayEstimator* binary_handle, int offset, int delay);
  void RunBinarySpectra(BinaryDelayEstimator* binary1,
                        BinaryDelayEstimator* binary2,
                        int near_offset, int lookahead_offset, int far_offset);
  void RunBinarySpectraTest(int near_offset, int lookahead_offset,
                            int ref_robust_validation, int robust_validation);

  void* handle_;
  DelayEstimator* self_;
  void* farend_handle_;
  DelayEstimatorFarend* farend_self_;
  BinaryDelayEstimator* binary_;
  BinaryDelayEstimatorFarend* binary_farend_;
  int spectrum_size_;
  // Dummy input spectra.
  float far_f_[kSpectrumSize];
  float near_f_[kSpectrumSize];
  uint16_t far_u16_[kSpectrumSize];
  uint16_t near_u16_[kSpectrumSize];
  uint32_t binary_spectrum_[kSequenceLength + kHistorySize];
};

DelayEstimatorTest::DelayEstimatorTest()
    : handle_(NULL),
      self_(NULL),
      farend_handle_(NULL),
      farend_self_(NULL),
      binary_(NULL),
      binary_farend_(NULL),
      spectrum_size_(kSpectrumSize) {
  // Dummy input data are set with more or less arbitrary non-zero values.
  memset(far_f_, 1, sizeof(far_f_));
  memset(near_f_, 2, sizeof(near_f_));
  memset(far_u16_, 1, sizeof(far_u16_));
  memset(near_u16_, 2, sizeof(near_u16_));
  // Construct a sequence of binary spectra used to verify delay estimate. The
  // |kSequenceLength| has to be long enough for the delay estimation to leave
  // the initialized state.
  binary_spectrum_[0] = 1;
  for (int i = 1; i < (kSequenceLength + kHistorySize); i++) {
    binary_spectrum_[i] = 3 * binary_spectrum_[i - 1];
  }
}

void DelayEstimatorTest::SetUp() {
  farend_handle_ = WebRtc_CreateDelayEstimatorFarend(kSpectrumSize,
                                                     kHistorySize);
  ASSERT_TRUE(farend_handle_ != NULL);
  farend_self_ = reinterpret_cast<DelayEstimatorFarend*>(farend_handle_);
  handle_ = WebRtc_CreateDelayEstimator(farend_handle_, kLookahead);
  ASSERT_TRUE(handle_ != NULL);
  self_ = reinterpret_cast<DelayEstimator*>(handle_);
  binary_farend_ = WebRtc_CreateBinaryDelayEstimatorFarend(kHistorySize);
  ASSERT_TRUE(binary_farend_ != NULL);
  binary_ = WebRtc_CreateBinaryDelayEstimator(binary_farend_, kLookahead);
  ASSERT_TRUE(binary_ != NULL);
}

void DelayEstimatorTest::TearDown() {
  WebRtc_FreeDelayEstimator(handle_);
  handle_ = NULL;
  self_ = NULL;
  WebRtc_FreeDelayEstimatorFarend(farend_handle_);
  farend_handle_ = NULL;
  farend_self_ = NULL;
  WebRtc_FreeBinaryDelayEstimator(binary_);
  binary_ = NULL;
  WebRtc_FreeBinaryDelayEstimatorFarend(binary_farend_);
  binary_farend_ = NULL;
}

void DelayEstimatorTest::Init() {
  // Initialize Delay Estimator
  EXPECT_EQ(0, WebRtc_InitDelayEstimatorFarend(farend_handle_));
  EXPECT_EQ(0, WebRtc_InitDelayEstimator(handle_));
  // Verify initialization.
  EXPECT_EQ(0, farend_self_->far_spectrum_initialized);
  EXPECT_EQ(0, self_->near_spectrum_initialized);
  EXPECT_EQ(-2, WebRtc_last_delay(handle_));  // Delay in initial state.
  EXPECT_FLOAT_EQ(0, WebRtc_last_delay_quality(handle_));  // Zero quality.
}

void DelayEstimatorTest::InitBinary() {
  // Initialize Binary Delay Estimator (far-end part).
  WebRtc_InitBinaryDelayEstimatorFarend(binary_farend_);
  // Initialize Binary Delay Estimator
  WebRtc_InitBinaryDelayEstimator(binary_);
  // Verify initialization. This does not guarantee a complete check, since
  // |last_delay| may be equal to -2 before initialization if done on the fly.
  EXPECT_EQ(-2, binary_->last_delay);
}

void DelayEstimatorTest::VerifyDelay(BinaryDelayEstimator* binary_handle,
                                     int offset, int delay) {
  // Verify that we WebRtc_binary_last_delay() returns correct delay.
  EXPECT_EQ(delay, WebRtc_binary_last_delay(binary_handle));

  if (delay != -2) {
    // Verify correct delay estimate. In the non-causal case the true delay
    // is equivalent with the |offset|.
    EXPECT_EQ(offset, delay);
  }
}

void DelayEstimatorTest::RunBinarySpectra(BinaryDelayEstimator* binary1,
                                          BinaryDelayEstimator* binary2,
                                          int near_offset,
                                          int lookahead_offset,
                                          int far_offset) {
  int different_validations = binary1->robust_validation_enabled ^
      binary2->robust_validation_enabled;
  WebRtc_InitBinaryDelayEstimatorFarend(binary_farend_);
  WebRtc_InitBinaryDelayEstimator(binary1);
  WebRtc_InitBinaryDelayEstimator(binary2);
  // Verify initialization. This does not guarantee a complete check, since
  // |last_delay| may be equal to -2 before initialization if done on the fly.
  EXPECT_EQ(-2, binary1->last_delay);
  EXPECT_EQ(-2, binary2->last_delay);
  for (int i = kLookahead; i < (kSequenceLength + kLookahead); i++) {
    WebRtc_AddBinaryFarSpectrum(binary_farend_,
                                binary_spectrum_[i + far_offset]);
    int delay_1 = WebRtc_ProcessBinarySpectrum(binary1, binary_spectrum_[i]);
    int delay_2 =
        WebRtc_ProcessBinarySpectrum(binary2,
                                     binary_spectrum_[i - near_offset]);

    VerifyDelay(binary1, far_offset + kLookahead, delay_1);
    VerifyDelay(binary2,
                far_offset + kLookahead + lookahead_offset + near_offset,
                delay_2);
    // Expect the two delay estimates to be offset by |lookahead_offset| +
    // |near_offset| when we have left the initial state.
    if ((delay_1 != -2) && (delay_2 != -2)) {
      EXPECT_EQ(delay_1, delay_2 - lookahead_offset - near_offset);
    }
    // For the case of identical signals |delay_1| and |delay_2| should match
    // all the time, unless one of them has robust validation turned on.  In
    // that case the robust validation leaves the initial state faster.
    if ((near_offset == 0) && (lookahead_offset == 0)) {
      if  (!different_validations) {
        EXPECT_EQ(delay_1, delay_2);
      } else {
        if (binary1->robust_validation_enabled) {
          EXPECT_GE(delay_1, delay_2);
        } else {
          EXPECT_GE(delay_2, delay_1);
        }
      }
    }
  }
  // Verify that we have left the initialized state.
  EXPECT_NE(-2, WebRtc_binary_last_delay(binary1));
  EXPECT_LT(0, WebRtc_binary_last_delay_quality(binary1));
  EXPECT_NE(-2, WebRtc_binary_last_delay(binary2));
  EXPECT_LT(0, WebRtc_binary_last_delay_quality(binary2));
}

void DelayEstimatorTest::RunBinarySpectraTest(int near_offset,
                                              int lookahead_offset,
                                              int ref_robust_validation,
                                              int robust_validation) {
  BinaryDelayEstimator* binary2 =
      WebRtc_CreateBinaryDelayEstimator(binary_farend_,
                                        kLookahead + lookahead_offset);
  // Verify the delay for both causal and non-causal systems. For causal systems
  // the delay is equivalent with a positive |offset| of the far-end sequence.
  // For non-causal systems the delay is equivalent with a negative |offset| of
  // the far-end sequence.
  binary_->robust_validation_enabled = ref_robust_validation;
  binary2->robust_validation_enabled = robust_validation;
  for (int offset = -kLookahead;
      offset < kMaxDelay - lookahead_offset - near_offset;
      offset++) {
    RunBinarySpectra(binary_, binary2, near_offset, lookahead_offset, offset);
  }
  WebRtc_FreeBinaryDelayEstimator(binary2);
  binary2 = NULL;
  binary_->robust_validation_enabled = 0;  // Reset reference.
}

TEST_F(DelayEstimatorTest, CorrectErrorReturnsOfWrapper) {
  // In this test we verify correct error returns on invalid API calls.

  // WebRtc_CreateDelayEstimatorFarend() and WebRtc_CreateDelayEstimator()
  // should return a NULL pointer on invalid input values.
  // Make sure we have a non-NULL value at start, so we can detect NULL after
  // create failure.
  void* handle = farend_handle_;
  handle = WebRtc_CreateDelayEstimatorFarend(33, kHistorySize);
  EXPECT_TRUE(handle == NULL);
  handle = WebRtc_CreateDelayEstimatorFarend(kSpectrumSize, 1);
  EXPECT_TRUE(handle == NULL);

  handle = handle_;
  handle = WebRtc_CreateDelayEstimator(NULL, kLookahead);
  EXPECT_TRUE(handle == NULL);
  handle = WebRtc_CreateDelayEstimator(farend_handle_, -1);
  EXPECT_TRUE(handle == NULL);

  // WebRtc_InitDelayEstimatorFarend() and WebRtc_InitDelayEstimator() should
  // return -1 if we have a NULL pointer as |handle|.
  EXPECT_EQ(-1, WebRtc_InitDelayEstimatorFarend(NULL));
  EXPECT_EQ(-1, WebRtc_InitDelayEstimator(NULL));

  // WebRtc_AddFarSpectrumFloat() should return -1 if we have:
  // 1) NULL pointer as |handle|.
  // 2) NULL pointer as far-end spectrum.
  // 3) Incorrect spectrum size.
  EXPECT_EQ(-1, WebRtc_AddFarSpectrumFloat(NULL, far_f_, spectrum_size_));
  // Use |farend_handle_| which is properly created at SetUp().
  EXPECT_EQ(-1, WebRtc_AddFarSpectrumFloat(farend_handle_, NULL,
                                           spectrum_size_));
  EXPECT_EQ(-1, WebRtc_AddFarSpectrumFloat(farend_handle_, far_f_,
                                           spectrum_size_ + 1));

  // WebRtc_AddFarSpectrumFix() should return -1 if we have:
  // 1) NULL pointer as |handle|.
  // 2) NULL pointer as far-end spectrum.
  // 3) Incorrect spectrum size.
  // 4) Too high precision in far-end spectrum (Q-domain > 15).
  EXPECT_EQ(-1, WebRtc_AddFarSpectrumFix(NULL, far_u16_, spectrum_size_, 0));
  EXPECT_EQ(-1, WebRtc_AddFarSpectrumFix(farend_handle_, NULL, spectrum_size_,
                                         0));
  EXPECT_EQ(-1, WebRtc_AddFarSpectrumFix(farend_handle_, far_u16_,
                                         spectrum_size_ + 1, 0));
  EXPECT_EQ(-1, WebRtc_AddFarSpectrumFix(farend_handle_, far_u16_,
                                         spectrum_size_, 16));

  // WebRtc_set_history_size() should return -1 if:
  // 1) |handle| is a NULL.
  // 2) |history_size| <= 1.
  EXPECT_EQ(-1, WebRtc_set_history_size(NULL, 1));
  EXPECT_EQ(-1, WebRtc_set_history_size(handle_, 1));
  // WebRtc_history_size() should return -1 if:
  // 1) NULL pointer input.
  EXPECT_EQ(-1, WebRtc_history_size(NULL));
  // 2) there is a mismatch between history size.
  void* tmp_handle = WebRtc_CreateDelayEstimator(farend_handle_, kHistorySize);
  EXPECT_EQ(0, WebRtc_InitDelayEstimator(tmp_handle));
  EXPECT_EQ(kDifferentHistorySize,
            WebRtc_set_history_size(tmp_handle, kDifferentHistorySize));
  EXPECT_EQ(kDifferentHistorySize, WebRtc_history_size(tmp_handle));
  EXPECT_EQ(kHistorySize, WebRtc_set_history_size(handle_, kHistorySize));
  EXPECT_EQ(-1, WebRtc_history_size(tmp_handle));

  // WebRtc_set_lookahead() should return -1 if we try a value outside the
  /// buffer.
  EXPECT_EQ(-1, WebRtc_set_lookahead(handle_, kLookahead + 1));
  EXPECT_EQ(-1, WebRtc_set_lookahead(handle_, -1));

  // WebRtc_set_allowed_offset() should return -1 if we have:
  // 1) NULL pointer as |handle|.
  // 2) |allowed_offset| < 0.
  EXPECT_EQ(-1, WebRtc_set_allowed_offset(NULL, 0));
  EXPECT_EQ(-1, WebRtc_set_allowed_offset(handle_, -1));

  EXPECT_EQ(-1, WebRtc_get_allowed_offset(NULL));

  // WebRtc_enable_robust_validation() should return -1 if we have:
  // 1) NULL pointer as |handle|.
  // 2) Incorrect |enable| value (not 0 or 1).
  EXPECT_EQ(-1, WebRtc_enable_robust_validation(NULL, kEnable[0]));
  EXPECT_EQ(-1, WebRtc_enable_robust_validation(handle_, -1));
  EXPECT_EQ(-1, WebRtc_enable_robust_validation(handle_, 2));

  // WebRtc_is_robust_validation_enabled() should return -1 if we have NULL
  // pointer as |handle|.
  EXPECT_EQ(-1, WebRtc_is_robust_validation_enabled(NULL));

  // WebRtc_DelayEstimatorProcessFloat() should return -1 if we have:
  // 1) NULL pointer as |handle|.
  // 2) NULL pointer as near-end spectrum.
  // 3) Incorrect spectrum size.
  // 4) Non matching history sizes if multiple delay estimators using the same
  //    far-end reference.
  EXPECT_EQ(-1, WebRtc_DelayEstimatorProcessFloat(NULL, near_f_,
                                                  spectrum_size_));
  // Use |handle_| which is properly created at SetUp().
  EXPECT_EQ(-1, WebRtc_DelayEstimatorProcessFloat(handle_, NULL,
                                                  spectrum_size_));
  EXPECT_EQ(-1, WebRtc_DelayEstimatorProcessFloat(handle_, near_f_,
                                                  spectrum_size_ + 1));
  // |tmp_handle| is already in a non-matching state.
  EXPECT_EQ(-1, WebRtc_DelayEstimatorProcessFloat(tmp_handle,
                                                  near_f_,
                                                  spectrum_size_));

  // WebRtc_DelayEstimatorProcessFix() should return -1 if we have:
  // 1) NULL pointer as |handle|.
  // 2) NULL pointer as near-end spectrum.
  // 3) Incorrect spectrum size.
  // 4) Too high precision in near-end spectrum (Q-domain > 15).
  // 5) Non matching history sizes if multiple delay estimators using the same
  //    far-end reference.
  EXPECT_EQ(-1, WebRtc_DelayEstimatorProcessFix(NULL, near_u16_, spectrum_size_,
                                                0));
  EXPECT_EQ(-1, WebRtc_DelayEstimatorProcessFix(handle_, NULL, spectrum_size_,
                                                0));
  EXPECT_EQ(-1, WebRtc_DelayEstimatorProcessFix(handle_, near_u16_,
                                                spectrum_size_ + 1, 0));
  EXPECT_EQ(-1, WebRtc_DelayEstimatorProcessFix(handle_, near_u16_,
                                                spectrum_size_, 16));
  // |tmp_handle| is already in a non-matching state.
  EXPECT_EQ(-1, WebRtc_DelayEstimatorProcessFix(tmp_handle,
                                                near_u16_,
                                                spectrum_size_,
                                                0));
  WebRtc_FreeDelayEstimator(tmp_handle);

  // WebRtc_last_delay() should return -1 if we have a NULL pointer as |handle|.
  EXPECT_EQ(-1, WebRtc_last_delay(NULL));

  // Free any local memory if needed.
  WebRtc_FreeDelayEstimator(handle);
}

TEST_F(DelayEstimatorTest, VerifyAllowedOffset) {
  // Is set to zero by default.
  EXPECT_EQ(0, WebRtc_get_allowed_offset(handle_));
  for (int i = 1; i >= 0; i--) {
    EXPECT_EQ(0, WebRtc_set_allowed_offset(handle_, i));
    EXPECT_EQ(i, WebRtc_get_allowed_offset(handle_));
    Init();
    // Unaffected over a reset.
    EXPECT_EQ(i, WebRtc_get_allowed_offset(handle_));
  }
}

TEST_F(DelayEstimatorTest, VerifyEnableRobustValidation) {
  // Disabled by default.
  EXPECT_EQ(0, WebRtc_is_robust_validation_enabled(handle_));
  for (size_t i = 0; i < kSizeEnable; ++i) {
    EXPECT_EQ(0, WebRtc_enable_robust_validation(handle_, kEnable[i]));
    EXPECT_EQ(kEnable[i], WebRtc_is_robust_validation_enabled(handle_));
    Init();
    // Unaffected over a reset.
    EXPECT_EQ(kEnable[i], WebRtc_is_robust_validation_enabled(handle_));
  }
}

TEST_F(DelayEstimatorTest, InitializedSpectrumAfterProcess) {
  // In this test we verify that the mean spectra are initialized after first
  // time we call WebRtc_AddFarSpectrum() and Process() respectively. The test
  // also verifies the state is not left for zero spectra.
  const float kZerosFloat[kSpectrumSize] = { 0.0 };
  const uint16_t kZerosU16[kSpectrumSize] = { 0 };

  // For floating point operations, process one frame and verify initialization
  // flag.
  Init();
  EXPECT_EQ(0, WebRtc_AddFarSpectrumFloat(farend_handle_, kZerosFloat,
                                          spectrum_size_));
  EXPECT_EQ(0, farend_self_->far_spectrum_initialized);
  EXPECT_EQ(0, WebRtc_AddFarSpectrumFloat(farend_handle_, far_f_,
                                           spectrum_size_));
  EXPECT_EQ(1, farend_self_->far_spectrum_initialized);
  EXPECT_EQ(-2, WebRtc_DelayEstimatorProcessFloat(handle_, kZerosFloat,
                                                  spectrum_size_));
  EXPECT_EQ(0, self_->near_spectrum_initialized);
  EXPECT_EQ(-2, WebRtc_DelayEstimatorProcessFloat(handle_, near_f_,
                                                  spectrum_size_));
  EXPECT_EQ(1, self_->near_spectrum_initialized);

  // For fixed point operations, process one frame and verify initialization
  // flag.
  Init();
  EXPECT_EQ(0, WebRtc_AddFarSpectrumFix(farend_handle_, kZerosU16,
                                        spectrum_size_, 0));
  EXPECT_EQ(0, farend_self_->far_spectrum_initialized);
  EXPECT_EQ(0, WebRtc_AddFarSpectrumFix(farend_handle_, far_u16_,
                                         spectrum_size_, 0));
  EXPECT_EQ(1, farend_self_->far_spectrum_initialized);
  EXPECT_EQ(-2, WebRtc_DelayEstimatorProcessFix(handle_, kZerosU16,
                                                spectrum_size_, 0));
  EXPECT_EQ(0, self_->near_spectrum_initialized);
  EXPECT_EQ(-2, WebRtc_DelayEstimatorProcessFix(handle_, near_u16_,
                                                spectrum_size_, 0));
  EXPECT_EQ(1, self_->near_spectrum_initialized);
}

TEST_F(DelayEstimatorTest, CorrectLastDelay) {
  // In this test we verify that we get the correct last delay upon valid call.
  // We simply process the same data until we leave the initialized state
  // (|last_delay| = -2). Then we compare the Process() output with the
  // last_delay() call.

  // TODO(bjornv): Update quality values for robust validation.
  int last_delay = 0;
  // Floating point operations.
  Init();
  for (int i = 0; i < 200; i++) {
    EXPECT_EQ(0, WebRtc_AddFarSpectrumFloat(farend_handle_, far_f_,
                                            spectrum_size_));
    last_delay = WebRtc_DelayEstimatorProcessFloat(handle_, near_f_,
                                                   spectrum_size_);
    if (last_delay != -2) {
      EXPECT_EQ(last_delay, WebRtc_last_delay(handle_));
      if (!WebRtc_is_robust_validation_enabled(handle_)) {
        EXPECT_FLOAT_EQ(7203.f / kMaxBitCountsQ9,
                        WebRtc_last_delay_quality(handle_));
      }
      break;
    }
  }
  // Verify that we have left the initialized state.
  EXPECT_NE(-2, WebRtc_last_delay(handle_));
  EXPECT_LT(0, WebRtc_last_delay_quality(handle_));

  // Fixed point operations.
  Init();
  for (int i = 0; i < 200; i++) {
    EXPECT_EQ(0, WebRtc_AddFarSpectrumFix(farend_handle_, far_u16_,
                                          spectrum_size_, 0));
    last_delay = WebRtc_DelayEstimatorProcessFix(handle_, near_u16_,
                                                 spectrum_size_, 0);
    if (last_delay != -2) {
      EXPECT_EQ(last_delay, WebRtc_last_delay(handle_));
      if (!WebRtc_is_robust_validation_enabled(handle_)) {
        EXPECT_FLOAT_EQ(7203.f / kMaxBitCountsQ9,
                        WebRtc_last_delay_quality(handle_));
      }
      break;
    }
  }
  // Verify that we have left the initialized state.
  EXPECT_NE(-2, WebRtc_last_delay(handle_));
  EXPECT_LT(0, WebRtc_last_delay_quality(handle_));
}

TEST_F(DelayEstimatorTest, CorrectErrorReturnsOfBinaryEstimatorFarend) {
  // In this test we verify correct output on invalid API calls to the Binary
  // Delay Estimator (far-end part).

  BinaryDelayEstimatorFarend* binary = binary_farend_;
  // WebRtc_CreateBinaryDelayEstimatorFarend() should return -1 if the input
  // history size is less than 2. This is to make sure the buffer shifting
  // applies properly.
  // Make sure we have a non-NULL value at start, so we can detect NULL after
  // create failure.
  binary = WebRtc_CreateBinaryDelayEstimatorFarend(1);
  EXPECT_TRUE(binary == NULL);
}

TEST_F(DelayEstimatorTest, CorrectErrorReturnsOfBinaryEstimator) {
  // In this test we verify correct output on invalid API calls to the Binary
  // Delay Estimator.

  BinaryDelayEstimator* binary_handle = binary_;
  // WebRtc_CreateBinaryDelayEstimator() should return -1 if we have a NULL
  // pointer as |binary_farend| or invalid input values. Upon failure, the
  // |binary_handle| should be NULL.
  // Make sure we have a non-NULL value at start, so we can detect NULL after
  // create failure.
  binary_handle = WebRtc_CreateBinaryDelayEstimator(NULL, kLookahead);
  EXPECT_TRUE(binary_handle == NULL);
  binary_handle = WebRtc_CreateBinaryDelayEstimator(binary_farend_, -1);
  EXPECT_TRUE(binary_handle == NULL);
}

TEST_F(DelayEstimatorTest, MeanEstimatorFix) {
  // In this test we verify that we update the mean value in correct direction
  // only. With "direction" we mean increase or decrease.

  int32_t mean_value = 4000;
  int32_t mean_value_before = mean_value;
  int32_t new_mean_value = mean_value * 2;

  // Increasing |mean_value|.
  WebRtc_MeanEstimatorFix(new_mean_value, 10, &mean_value);
  EXPECT_LT(mean_value_before, mean_value);
  EXPECT_GT(new_mean_value, mean_value);

  // Decreasing |mean_value|.
  new_mean_value = mean_value / 2;
  mean_value_before = mean_value;
  WebRtc_MeanEstimatorFix(new_mean_value, 10, &mean_value);
  EXPECT_GT(mean_value_before, mean_value);
  EXPECT_LT(new_mean_value, mean_value);
}

TEST_F(DelayEstimatorTest, ExactDelayEstimateMultipleNearSameSpectrum) {
  // In this test we verify that we get the correct delay estimates if we shift
  // the signal accordingly. We create two Binary Delay Estimators and feed them
  // with the same signals, so they should output the same results.
  // We verify both causal and non-causal delays.
  // For these noise free signals, the robust validation should not have an
  // impact, hence we turn robust validation on/off for both reference and
  // delayed near end.

  for (size_t i = 0; i < kSizeEnable; ++i) {
    for (size_t j = 0; j < kSizeEnable; ++j) {
      RunBinarySpectraTest(0, 0, kEnable[i], kEnable[j]);
    }
  }
}

TEST_F(DelayEstimatorTest, ExactDelayEstimateMultipleNearDifferentSpectrum) {
  // In this test we use the same setup as above, but we now feed the two Binary
  // Delay Estimators with different signals, so they should output different
  // results.
  // For these noise free signals, the robust validation should not have an
  // impact, hence we turn robust validation on/off for both reference and
  // delayed near end.

  const int kNearOffset = 1;
  for (size_t i = 0; i < kSizeEnable; ++i) {
    for (size_t j = 0; j < kSizeEnable; ++j) {
      RunBinarySpectraTest(kNearOffset, 0, kEnable[i], kEnable[j]);
    }
  }
}

TEST_F(DelayEstimatorTest, ExactDelayEstimateMultipleNearDifferentLookahead) {
  // In this test we use the same setup as above, feeding the two Binary
  // Delay Estimators with the same signals. The difference is that we create
  // them with different lookahead.
  // For these noise free signals, the robust validation should not have an
  // impact, hence we turn robust validation on/off for both reference and
  // delayed near end.

  const int kLookaheadOffset = 1;
  for (size_t i = 0; i < kSizeEnable; ++i) {
    for (size_t j = 0; j < kSizeEnable; ++j) {
      RunBinarySpectraTest(0, kLookaheadOffset, kEnable[i], kEnable[j]);
    }
  }
}

TEST_F(DelayEstimatorTest, AllowedOffsetNoImpactWhenRobustValidationDisabled) {
  // The same setup as in ExactDelayEstimateMultipleNearSameSpectrum with the
  // difference that |allowed_offset| is set for the reference binary delay
  // estimator.

  binary_->allowed_offset = 10;
  RunBinarySpectraTest(0, 0, 0, 0);
  binary_->allowed_offset = 0;  // Reset reference.
}

TEST_F(DelayEstimatorTest, VerifyLookaheadAtCreate) {
  void* farend_handle = WebRtc_CreateDelayEstimatorFarend(kSpectrumSize,
                                                          kMaxDelay);
  ASSERT_TRUE(farend_handle != NULL);
  void* handle = WebRtc_CreateDelayEstimator(farend_handle, kLookahead);
  ASSERT_TRUE(handle != NULL);
  EXPECT_EQ(kLookahead, WebRtc_lookahead(handle));
  WebRtc_FreeDelayEstimator(handle);
  WebRtc_FreeDelayEstimatorFarend(farend_handle);
}

TEST_F(DelayEstimatorTest, VerifyLookaheadIsSetAndKeptAfterInit) {
  EXPECT_EQ(kLookahead, WebRtc_lookahead(handle_));
  EXPECT_EQ(kDifferentLookahead,
            WebRtc_set_lookahead(handle_, kDifferentLookahead));
  EXPECT_EQ(kDifferentLookahead, WebRtc_lookahead(handle_));
  EXPECT_EQ(0, WebRtc_InitDelayEstimatorFarend(farend_handle_));
  EXPECT_EQ(kDifferentLookahead, WebRtc_lookahead(handle_));
  EXPECT_EQ(0, WebRtc_InitDelayEstimator(handle_));
  EXPECT_EQ(kDifferentLookahead, WebRtc_lookahead(handle_));
}

TEST_F(DelayEstimatorTest, VerifyHistorySizeAtCreate) {
  EXPECT_EQ(kHistorySize, WebRtc_history_size(handle_));
}

TEST_F(DelayEstimatorTest, VerifyHistorySizeIsSetAndKeptAfterInit) {
  EXPECT_EQ(kHistorySize, WebRtc_history_size(handle_));
  EXPECT_EQ(kDifferentHistorySize,
            WebRtc_set_history_size(handle_, kDifferentHistorySize));
  EXPECT_EQ(kDifferentHistorySize, WebRtc_history_size(handle_));
  EXPECT_EQ(0, WebRtc_InitDelayEstimator(handle_));
  EXPECT_EQ(kDifferentHistorySize, WebRtc_history_size(handle_));
  EXPECT_EQ(0, WebRtc_InitDelayEstimatorFarend(farend_handle_));
  EXPECT_EQ(kDifferentHistorySize, WebRtc_history_size(handle_));
}

// TODO(bjornv): Add tests for SoftReset...(...).

}  // namespace
