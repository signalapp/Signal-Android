/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_opus.h"

#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_codec_database.h"

namespace webrtc {

namespace acm2 {

namespace {
  const CodecInst kOpusCodecInst = {105, "opus", 48000, 960, 1, 32000};
  // These constants correspond to those used in ACMOpus::SetPacketLossRate().
  const int kPacketLossRate20 = 20;
  const int kPacketLossRate10 = 10;
  const int kPacketLossRate5 = 5;
  const int kPacketLossRate1 = 1;
  const int kLossRate20Margin = 2;
  const int kLossRate10Margin = 1;
  const int kLossRate5Margin = 1;
}  // namespace

class AcmOpusTest : public ACMOpus {
 public:
  explicit AcmOpusTest(int16_t codec_id)
      : ACMOpus(codec_id) {}
  ~AcmOpusTest() {}
  int packet_loss_rate() { return packet_loss_rate_; }

  void TestSetPacketLossRate(int from, int to, int expected_return);
};

#ifdef WEBRTC_CODEC_OPUS
void AcmOpusTest::TestSetPacketLossRate(int from, int to, int expected_return) {
  for (int loss = from; loss <= to; (to >= from) ? ++loss : --loss) {
    EXPECT_EQ(0, SetPacketLossRate(loss));
    EXPECT_EQ(expected_return, packet_loss_rate());
  }
}

TEST(AcmOpusTest, PacketLossRateOptimized) {
  AcmOpusTest opus(ACMCodecDB::kOpus);
  WebRtcACMCodecParams params;
  memcpy(&(params.codec_inst), &kOpusCodecInst, sizeof(CodecInst));
  EXPECT_EQ(0, opus.InitEncoder(&params, true));
  EXPECT_EQ(0, opus.SetFEC(true));

  // Note that the order of the following calls is critical.
  opus.TestSetPacketLossRate(0, 0, 0);
  opus.TestSetPacketLossRate(kPacketLossRate1,
                             kPacketLossRate5 + kLossRate5Margin - 1,
                             kPacketLossRate1);
  opus.TestSetPacketLossRate(kPacketLossRate5 + kLossRate5Margin,
                             kPacketLossRate10 + kLossRate10Margin - 1,
                             kPacketLossRate5);
  opus.TestSetPacketLossRate(kPacketLossRate10 + kLossRate10Margin,
                             kPacketLossRate20 + kLossRate20Margin - 1,
                             kPacketLossRate10);
  opus.TestSetPacketLossRate(kPacketLossRate20 + kLossRate20Margin,
                             100,
                             kPacketLossRate20);
  opus.TestSetPacketLossRate(kPacketLossRate20 + kLossRate20Margin,
                             kPacketLossRate20 - kLossRate20Margin,
                             kPacketLossRate20);
  opus.TestSetPacketLossRate(kPacketLossRate20 - kLossRate20Margin - 1,
                             kPacketLossRate10 - kLossRate10Margin,
                             kPacketLossRate10);
  opus.TestSetPacketLossRate(kPacketLossRate10 - kLossRate10Margin - 1,
                             kPacketLossRate5 - kLossRate5Margin,
                             kPacketLossRate5);
  opus.TestSetPacketLossRate(kPacketLossRate5 - kLossRate5Margin - 1,
                             kPacketLossRate1,
                             kPacketLossRate1);
  opus.TestSetPacketLossRate(0, 0, 0);
}
#else
void AcmOpusTest:TestSetPacketLossRate(int /* from */, int /* to */,
                                       int /* expected_return */) {
  return;
}
#endif  // WEBRTC_CODEC_OPUS

}  // namespace acm2

}  // namespace webrtc
