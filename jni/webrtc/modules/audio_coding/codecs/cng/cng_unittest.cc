/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include <string>

#include "gtest/gtest.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc_cng.h"

namespace webrtc {

enum {
  kSidShortIntervalUpdate = 1,
  kSidNormalIntervalUpdate = 100,
  kSidLongIntervalUpdate = 10000
};

enum {
  kCNGNumParamsLow = 0,
  kCNGNumParamsNormal = 8,
  kCNGNumParamsHigh = WEBRTC_CNG_MAX_LPC_ORDER,
  kCNGNumParamsTooHigh = WEBRTC_CNG_MAX_LPC_ORDER + 1
};

enum {
  kNoSid,
  kForceSid
};

class CngTest : public ::testing::Test {
 protected:
  CngTest();
  virtual void SetUp();

  CNG_enc_inst* cng_enc_inst_;
  CNG_dec_inst* cng_dec_inst_;
  int16_t speech_data_[640];  // Max size of CNG internal buffers.
};

CngTest::CngTest()
    : cng_enc_inst_(NULL),
      cng_dec_inst_(NULL) {
}

void CngTest::SetUp() {
  FILE* input_file;
  const std::string file_name =
        webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
  input_file = fopen(file_name.c_str(), "rb");
  ASSERT_TRUE(input_file != NULL);
  ASSERT_EQ(640, static_cast<int32_t>(fread(speech_data_, sizeof(int16_t),
                                             640, input_file)));
  fclose(input_file);
  input_file = NULL;
}

// Test failing Create.
TEST_F(CngTest, CngCreateFail) {
  // Test to see that an invalid pointer is caught.
  EXPECT_EQ(-1, WebRtcCng_CreateEnc(NULL));
  EXPECT_EQ(-1, WebRtcCng_CreateDec(NULL));
}

// Test normal Create.
TEST_F(CngTest, CngCreate) {
  EXPECT_EQ(0, WebRtcCng_CreateEnc(&cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_CreateDec(&cng_dec_inst_));
  EXPECT_TRUE(cng_enc_inst_ != NULL);
  EXPECT_TRUE(cng_dec_inst_ != NULL);
  // Free encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_FreeEnc(cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_FreeDec(cng_dec_inst_));
}

// Create CNG encoder, init with faulty values, free CNG encoder.
TEST_F(CngTest, CngInitFail) {
  // Create encoder memory.
  EXPECT_EQ(0, WebRtcCng_CreateEnc(&cng_enc_inst_));

  // Call with too few parameters.
  EXPECT_EQ(-1, WebRtcCng_InitEnc(cng_enc_inst_, 8000, kSidNormalIntervalUpdate,
                                  kCNGNumParamsLow));
  EXPECT_EQ(6130, WebRtcCng_GetErrorCodeEnc(cng_enc_inst_));

  // Call with too many parameters.
  EXPECT_EQ(-1, WebRtcCng_InitEnc(cng_enc_inst_, 8000, kSidNormalIntervalUpdate,
                                  kCNGNumParamsTooHigh));
  EXPECT_EQ(6130, WebRtcCng_GetErrorCodeEnc(cng_enc_inst_));

  // Free encoder memory.
  EXPECT_EQ(0, WebRtcCng_FreeEnc(cng_enc_inst_));
}

TEST_F(CngTest, CngEncode) {
  uint8_t sid_data[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t number_bytes;

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcCng_CreateEnc(&cng_enc_inst_));

  // 8 kHz, Normal number of parameters
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 8000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsNormal));
  EXPECT_EQ(0, WebRtcCng_Encode(cng_enc_inst_, speech_data_, 80, sid_data,
                                &number_bytes, kNoSid));
  EXPECT_EQ(kCNGNumParamsNormal + 1, WebRtcCng_Encode(
      cng_enc_inst_, speech_data_, 80, sid_data, &number_bytes, kForceSid));

  // 16 kHz, Normal number of parameters
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 16000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsNormal));
  EXPECT_EQ(0, WebRtcCng_Encode(cng_enc_inst_, speech_data_, 160, sid_data,
                                &number_bytes, kNoSid));
  EXPECT_EQ(kCNGNumParamsNormal + 1, WebRtcCng_Encode(
      cng_enc_inst_, speech_data_, 160, sid_data, &number_bytes, kForceSid));

  // 32 kHz, Max number of parameters
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 32000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsHigh));
  EXPECT_EQ(0, WebRtcCng_Encode(cng_enc_inst_, speech_data_, 320, sid_data,
                                &number_bytes, kNoSid));
  EXPECT_EQ(kCNGNumParamsHigh + 1, WebRtcCng_Encode(
      cng_enc_inst_, speech_data_, 320, sid_data, &number_bytes, kForceSid));

  // 48 kHz, Normal number of parameters
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 48000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsNormal));
  EXPECT_EQ(0, WebRtcCng_Encode(cng_enc_inst_, speech_data_, 480, sid_data,
                                &number_bytes, kNoSid));
  EXPECT_EQ(kCNGNumParamsNormal + 1, WebRtcCng_Encode(
      cng_enc_inst_, speech_data_, 480, sid_data, &number_bytes, kForceSid));

  // 64 kHz, Normal number of parameters
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 64000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsNormal));
  EXPECT_EQ(0, WebRtcCng_Encode(cng_enc_inst_, speech_data_, 640, sid_data,
                                &number_bytes, kNoSid));
  EXPECT_EQ(kCNGNumParamsNormal + 1, WebRtcCng_Encode(
      cng_enc_inst_, speech_data_, 640, sid_data, &number_bytes, kForceSid));

  // Free encoder memory.
  EXPECT_EQ(0, WebRtcCng_FreeEnc(cng_enc_inst_));
}

// Encode Cng with too long input vector.
TEST_F(CngTest, CngEncodeTooLong) {
  uint8_t sid_data[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t number_bytes;

  // Create and init encoder memory.
  EXPECT_EQ(0, WebRtcCng_CreateEnc(&cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 8000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsNormal));

  // Run encoder with too much data.
  EXPECT_EQ(-1, WebRtcCng_Encode(cng_enc_inst_, speech_data_, 641, sid_data,
                                 &number_bytes, kNoSid));
  EXPECT_EQ(6140, WebRtcCng_GetErrorCodeEnc(cng_enc_inst_));

  // Free encoder memory.
  EXPECT_EQ(0, WebRtcCng_FreeEnc(cng_enc_inst_));
}

// Call encode without calling init.
TEST_F(CngTest, CngEncodeNoInit) {
  uint8_t sid_data[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t number_bytes;

  // Create encoder memory.
  EXPECT_EQ(0, WebRtcCng_CreateEnc(&cng_enc_inst_));

  // Run encoder without calling init.
  EXPECT_EQ(-1, WebRtcCng_Encode(cng_enc_inst_, speech_data_, 640, sid_data,
                                 &number_bytes, kNoSid));
  EXPECT_EQ(6120, WebRtcCng_GetErrorCodeEnc(cng_enc_inst_));

  // Free encoder memory.
  EXPECT_EQ(0, WebRtcCng_FreeEnc(cng_enc_inst_));
}

// Update SID parameters, for both 9 and 16 parameters.
TEST_F(CngTest, CngUpdateSid) {
  uint8_t sid_data[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t number_bytes;

  // Create and initialize encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_CreateEnc(&cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_CreateDec(&cng_dec_inst_));
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 16000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsNormal));
  EXPECT_EQ(0, WebRtcCng_InitDec(cng_dec_inst_));

  // Run normal Encode and UpdateSid.
  EXPECT_EQ(kCNGNumParamsNormal + 1, WebRtcCng_Encode(
      cng_enc_inst_, speech_data_, 160, sid_data, &number_bytes, kForceSid));
  EXPECT_EQ(0, WebRtcCng_UpdateSid(cng_dec_inst_, sid_data,
                                   kCNGNumParamsNormal + 1));

  // Reinit with new length.
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 16000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsHigh));
  EXPECT_EQ(0, WebRtcCng_InitDec(cng_dec_inst_));

  // Expect 0 because of unstable parameters after switching length.
  EXPECT_EQ(0, WebRtcCng_Encode(cng_enc_inst_, speech_data_, 160, sid_data,
                                &number_bytes, kForceSid));
  EXPECT_EQ(kCNGNumParamsHigh + 1, WebRtcCng_Encode(
      cng_enc_inst_, speech_data_ + 160, 160, sid_data, &number_bytes,
      kForceSid));
  EXPECT_EQ(0, WebRtcCng_UpdateSid(cng_dec_inst_, sid_data,
                                   kCNGNumParamsNormal + 1));

  // Free encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_FreeEnc(cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_FreeDec(cng_dec_inst_));
}

// Update SID parameters, with wrong parameters or without calling decode.
TEST_F(CngTest, CngUpdateSidErroneous) {
  uint8_t sid_data[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t number_bytes;

  // Create encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_CreateEnc(&cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_CreateDec(&cng_dec_inst_));

  // Encode.
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 16000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsNormal));
  EXPECT_EQ(kCNGNumParamsNormal + 1, WebRtcCng_Encode(
      cng_enc_inst_, speech_data_, 160, sid_data, &number_bytes, kForceSid));

  // Update Sid before initializing decoder.
  EXPECT_EQ(-1, WebRtcCng_UpdateSid(cng_dec_inst_, sid_data,
                                    kCNGNumParamsNormal + 1));
  EXPECT_EQ(6220, WebRtcCng_GetErrorCodeDec(cng_dec_inst_));

  // Initialize decoder.
  EXPECT_EQ(0, WebRtcCng_InitDec(cng_dec_inst_));

  // First run with valid parameters, then with too many CNG parameters.
  // The function will operate correctly by only reading the maximum number of
  // parameters, skipping the extra.
  EXPECT_EQ(0, WebRtcCng_UpdateSid(cng_dec_inst_, sid_data,
                                   kCNGNumParamsNormal + 1));
  EXPECT_EQ(0, WebRtcCng_UpdateSid(cng_dec_inst_, sid_data,
                                   kCNGNumParamsTooHigh + 1));

  // Free encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_FreeEnc(cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_FreeDec(cng_dec_inst_));
}

// Test to generate cng data, by forcing SID. Both normal and faulty condition.
TEST_F(CngTest, CngGenerate) {
  uint8_t sid_data[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t out_data[640];
  int16_t number_bytes;

  // Create and initialize encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_CreateEnc(&cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_CreateDec(&cng_dec_inst_));
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 16000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsNormal));
  EXPECT_EQ(0, WebRtcCng_InitDec(cng_dec_inst_));

  // Normal Encode.
  EXPECT_EQ(kCNGNumParamsNormal + 1, WebRtcCng_Encode(
      cng_enc_inst_, speech_data_, 160, sid_data, &number_bytes, kForceSid));

  // Normal UpdateSid.
  EXPECT_EQ(0, WebRtcCng_UpdateSid(cng_dec_inst_, sid_data,
                                   kCNGNumParamsNormal + 1));

  // Two normal Generate, one with new_period.
  EXPECT_EQ(0, WebRtcCng_Generate(cng_dec_inst_, out_data, 640, 1));
  EXPECT_EQ(0, WebRtcCng_Generate(cng_dec_inst_, out_data, 640, 0));

  // Call Genereate with too much data.
  EXPECT_EQ(-1, WebRtcCng_Generate(cng_dec_inst_, out_data, 641, 0));
  EXPECT_EQ(6140, WebRtcCng_GetErrorCodeDec(cng_dec_inst_));

  // Free encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_FreeEnc(cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_FreeDec(cng_dec_inst_));
}

// Test automatic SID.
TEST_F(CngTest, CngAutoSid) {
  uint8_t sid_data[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t number_bytes;

  // Create and initialize encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_CreateEnc(&cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_CreateDec(&cng_dec_inst_));
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 16000, kSidNormalIntervalUpdate,
                                 kCNGNumParamsNormal));
  EXPECT_EQ(0, WebRtcCng_InitDec(cng_dec_inst_));

  // Normal Encode, 100 msec, where no SID data should be generated.
  for (int i = 0; i < 10; i++) {
    EXPECT_EQ(0, WebRtcCng_Encode(cng_enc_inst_, speech_data_, 160, sid_data,
                                  &number_bytes, kNoSid));
  }

  // We have reached 100 msec, and SID data should be generated.
  EXPECT_EQ(kCNGNumParamsNormal + 1, WebRtcCng_Encode(
      cng_enc_inst_, speech_data_, 160, sid_data, &number_bytes, kNoSid));

  // Free encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_FreeEnc(cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_FreeDec(cng_dec_inst_));
}

// Test automatic SID, with very short interval.
TEST_F(CngTest, CngAutoSidShort) {
  uint8_t sid_data[WEBRTC_CNG_MAX_LPC_ORDER + 1];
  int16_t number_bytes;

  // Create and initialize encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_CreateEnc(&cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_CreateDec(&cng_dec_inst_));
  EXPECT_EQ(0, WebRtcCng_InitEnc(cng_enc_inst_, 16000, kSidShortIntervalUpdate,
                                 kCNGNumParamsNormal));
  EXPECT_EQ(0, WebRtcCng_InitDec(cng_dec_inst_));

  // First call will never generate SID, unless forced to.
  EXPECT_EQ(0, WebRtcCng_Encode(cng_enc_inst_, speech_data_, 160, sid_data,
                                &number_bytes, kNoSid));

  // Normal Encode, 100 msec, SID data should be generated all the time.
  for (int i = 0; i < 10; i++) {
    EXPECT_EQ(kCNGNumParamsNormal + 1, WebRtcCng_Encode(
        cng_enc_inst_, speech_data_, 160, sid_data, &number_bytes, kNoSid));
  }

  // Free encoder and decoder memory.
  EXPECT_EQ(0, WebRtcCng_FreeEnc(cng_enc_inst_));
  EXPECT_EQ(0, WebRtcCng_FreeDec(cng_dec_inst_));
}

}  // namespace webrtc
