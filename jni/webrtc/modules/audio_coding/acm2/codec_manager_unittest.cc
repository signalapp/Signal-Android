/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/codecs/mock/mock_audio_encoder.h"
#include "webrtc/modules/audio_coding/acm2/codec_manager.h"
#include "webrtc/modules/audio_coding/acm2/rent_a_codec.h"

namespace webrtc {
namespace acm2 {

using ::testing::Return;

namespace {

// Create a MockAudioEncoder with some reasonable default behavior.
std::unique_ptr<MockAudioEncoder> CreateMockEncoder() {
  auto enc = std::unique_ptr<MockAudioEncoder>(new MockAudioEncoder);
  EXPECT_CALL(*enc, SampleRateHz()).WillRepeatedly(Return(8000));
  EXPECT_CALL(*enc, NumChannels()).WillRepeatedly(Return(1));
  EXPECT_CALL(*enc, Max10MsFramesInAPacket()).WillRepeatedly(Return(1));
  EXPECT_CALL(*enc, Die());
  return enc;
}

}  // namespace

TEST(CodecManagerTest, ExternalEncoderFec) {
  auto enc0 = CreateMockEncoder();
  auto enc1 = CreateMockEncoder();
  auto enc2 = CreateMockEncoder();
  {
    ::testing::InSequence s;
    EXPECT_CALL(*enc0, SetFec(false)).WillOnce(Return(true));
    EXPECT_CALL(*enc1, SetFec(true)).WillOnce(Return(true));
    EXPECT_CALL(*enc2, SetFec(true)).WillOnce(Return(false));
  }

  CodecManager cm;
  RentACodec rac;

  // use_codec_fec starts out false.
  EXPECT_FALSE(cm.GetStackParams()->use_codec_fec);
  cm.GetStackParams()->speech_encoder = std::move(enc0);
  EXPECT_TRUE(rac.RentEncoderStack(cm.GetStackParams()));
  EXPECT_FALSE(cm.GetStackParams()->use_codec_fec);

  // Set it to true.
  EXPECT_EQ(true, cm.SetCodecFEC(true));
  EXPECT_TRUE(cm.GetStackParams()->use_codec_fec);
  cm.GetStackParams()->speech_encoder = std::move(enc1);
  EXPECT_TRUE(rac.RentEncoderStack(cm.GetStackParams()));
  EXPECT_TRUE(cm.GetStackParams()->use_codec_fec);

  // Switch to a codec that doesn't support it.
  cm.GetStackParams()->speech_encoder = std::move(enc2);
  EXPECT_TRUE(rac.RentEncoderStack(cm.GetStackParams()));
  EXPECT_FALSE(cm.GetStackParams()->use_codec_fec);
}

}  // namespace acm2
}  // namespace webrtc
