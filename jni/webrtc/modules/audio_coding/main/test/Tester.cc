/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>
#include <string>
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/main/interface/audio_coding_module.h"
#include "webrtc/modules/audio_coding/main/test/APITest.h"
#include "webrtc/modules/audio_coding/main/test/EncodeDecodeTest.h"
#include "webrtc/modules/audio_coding/main/test/iSACTest.h"
#include "webrtc/modules/audio_coding/main/test/opus_test.h"
#include "webrtc/modules/audio_coding/main/test/PacketLossTest.h"
#include "webrtc/modules/audio_coding/main/test/TestAllCodecs.h"
#include "webrtc/modules/audio_coding/main/test/TestRedFec.h"
#include "webrtc/modules/audio_coding/main/test/TestStereo.h"
#include "webrtc/modules/audio_coding/main/test/TestVADDTX.h"
#include "webrtc/modules/audio_coding/main/test/TwoWayCommunication.h"
#include "webrtc/system_wrappers/interface/trace.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/test/testsupport/gtest_disable.h"

using webrtc::Trace;

// This parameter is used to describe how to run the tests. It is normally
// set to 0, and all tests are run in quite mode.
#define ACM_TEST_MODE 0

TEST(AudioCodingModuleTest, TestAllCodecs) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
          "acm_allcodecs_trace.txt").c_str());
  webrtc::TestAllCodecs(ACM_TEST_MODE).Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, DISABLED_ON_ANDROID(TestEncodeDecode)) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_encodedecode_trace.txt").c_str());
  webrtc::EncodeDecodeTest(ACM_TEST_MODE).Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, DISABLED_ON_ANDROID(TestRedFec)) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_fec_trace.txt").c_str());
  webrtc::TestRedFec().Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, DISABLED_ON_ANDROID(TestIsac)) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_isac_trace.txt").c_str());
  webrtc::ISACTest(ACM_TEST_MODE).Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, DISABLED_ON_ANDROID(TwoWayCommunication)) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_twowaycom_trace.txt").c_str());
  webrtc::TwoWayCommunication(ACM_TEST_MODE).Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, DISABLED_ON_ANDROID(TestStereo)) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_stereo_trace.txt").c_str());
  webrtc::TestStereo(ACM_TEST_MODE).Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, DISABLED_ON_ANDROID(TestVADDTX)) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_vaddtx_trace.txt").c_str());
  webrtc::TestVADDTX().Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, TestOpus) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_opus_trace.txt").c_str());
  webrtc::OpusTest().Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, TestPacketLoss) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_packetloss_trace.txt").c_str());
  webrtc::PacketLossTest(1, 10, 10, 1).Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, TestPacketLossBurst) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_packetloss_burst_trace.txt").c_str());
  webrtc::PacketLossTest(1, 10, 10, 2).Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, TestPacketLossStereo) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_packetloss_trace.txt").c_str());
  webrtc::PacketLossTest(2, 10, 10, 1).Perform();
  Trace::ReturnTrace();
}

TEST(AudioCodingModuleTest, TestPacketLossStereoBurst) {
  Trace::CreateTrace();
  Trace::SetTraceFile((webrtc::test::OutputPath() +
      "acm_packetloss_burst_trace.txt").c_str());
  webrtc::PacketLossTest(2, 10, 10, 2).Perform();
  Trace::ReturnTrace();
}

// The full API test is too long to run automatically on bots, but can be used
// for offline testing. User interaction is needed.
#ifdef ACM_TEST_FULL_API
  TEST(AudioCodingModuleTest, TestAPI) {
    Trace::CreateTrace();
    Trace::SetTraceFile((webrtc::test::OutputPath() +
        "acm_apitest_trace.txt").c_str());
    webrtc::APITest().Perform();
    Trace::ReturnTrace();
  }
#endif
