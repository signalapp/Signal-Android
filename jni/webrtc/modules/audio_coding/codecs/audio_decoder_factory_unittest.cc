/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/codecs/builtin_audio_decoder_factory.h"

namespace webrtc {

TEST(AudioDecoderFactoryTest, CreateUnknownDecoder) {
  rtc::scoped_refptr<AudioDecoderFactory> adf =
      CreateBuiltinAudioDecoderFactory();
  ASSERT_TRUE(adf);
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("rey", 8000, 1)));
}

TEST(AudioDecoderFactoryTest, CreatePcmu) {
  rtc::scoped_refptr<AudioDecoderFactory> adf =
      CreateBuiltinAudioDecoderFactory();
  ASSERT_TRUE(adf);
  // PCMu supports 8 kHz, and any number of channels.
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("pcmu", 8000, 0)));
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("pcmu", 8000, 1)));
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("pcmu", 8000, 2)));
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("pcmu", 8000, 3)));
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("pcmu", 16000, 1)));
}

TEST(AudioDecoderFactoryTest, CreatePcma) {
  rtc::scoped_refptr<AudioDecoderFactory> adf =
      CreateBuiltinAudioDecoderFactory();
  ASSERT_TRUE(adf);
  // PCMa supports 8 kHz, and any number of channels.
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("pcma", 8000, 0)));
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("pcma", 8000, 1)));
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("pcma", 8000, 2)));
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("pcma", 8000, 3)));
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("pcma", 16000, 1)));
}

TEST(AudioDecoderFactoryTest, CreateIlbc) {
  rtc::scoped_refptr<AudioDecoderFactory> adf =
      CreateBuiltinAudioDecoderFactory();
  ASSERT_TRUE(adf);
  // iLBC supports 8 kHz, 1 channel.
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("ilbc", 8000, 0)));
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("ilbc", 8000, 1)));
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("ilbc", 8000, 2)));
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("ilbc", 16000, 1)));
}

TEST(AudioDecoderFactoryTest, CreateIsac) {
  rtc::scoped_refptr<AudioDecoderFactory> adf =
      CreateBuiltinAudioDecoderFactory();
  ASSERT_TRUE(adf);
  // iSAC supports 16 kHz, 1 channel. The float implementation additionally
  // supports 32 kHz, 1 channel.
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("isac", 16000, 0)));
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("isac", 16000, 1)));
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("isac", 16000, 2)));
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("isac", 8000, 1)));
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("isac", 48000, 1)));
#ifdef WEBRTC_ARCH_ARM
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("isac", 32000, 1)));
#else
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("isac", 32000, 1)));
#endif
}

TEST(AudioDecoderFactoryTest, CreateL16) {
  rtc::scoped_refptr<AudioDecoderFactory> adf =
      CreateBuiltinAudioDecoderFactory();
  ASSERT_TRUE(adf);
  // L16 supports any clock rate, any number of channels.
  const int clockrates[] = {8000, 16000, 32000, 48000};
  const int num_channels[] = {1, 2, 3, 4711};
  for (int clockrate : clockrates) {
    EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("l16", clockrate, 0)));
    for (int channels : num_channels) {
      EXPECT_TRUE(
          adf->MakeAudioDecoder(SdpAudioFormat("l16", clockrate, channels)));
    }
  }
}

TEST(AudioDecoderFactoryTest, CreateG722) {
  rtc::scoped_refptr<AudioDecoderFactory> adf =
      CreateBuiltinAudioDecoderFactory();
  ASSERT_TRUE(adf);
  // g722 supports 8 kHz, 1-2 channels.
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("g722", 8000, 0)));
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("g722", 8000, 1)));
  EXPECT_TRUE(adf->MakeAudioDecoder(SdpAudioFormat("g722", 8000, 2)));
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("g722", 8000, 3)));
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("g722", 16000, 1)));
  EXPECT_FALSE(adf->MakeAudioDecoder(SdpAudioFormat("g722", 32000, 1)));

  // g722 actually uses a 16 kHz sample rate instead of the nominal 8 kHz.
  std::unique_ptr<AudioDecoder> dec =
      adf->MakeAudioDecoder(SdpAudioFormat("g722", 8000, 1));
  EXPECT_EQ(16000, dec->SampleRateHz());
}

TEST(AudioDecoderFactoryTest, CreateOpus) {
  rtc::scoped_refptr<AudioDecoderFactory> adf =
      CreateBuiltinAudioDecoderFactory();
  ASSERT_TRUE(adf);
  // Opus supports 48 kHz, 2 channels, and wants a "stereo" parameter whose
  // value is either "0" or "1".
  for (int hz : {8000, 16000, 32000, 48000}) {
    for (int channels : {0, 1, 2, 3}) {
      for (std::string stereo : {"XX", "0", "1", "2"}) {
        std::map<std::string, std::string> params;
        if (stereo != "XX") {
          params["stereo"] = stereo;
        }
        bool good =
            (hz == 48000 && channels == 2 && (stereo == "0" || stereo == "1"));
        EXPECT_EQ(good, static_cast<bool>(adf->MakeAudioDecoder(SdpAudioFormat(
                            "opus", hz, channels, std::move(params)))));
      }
    }
  }
}

}  // namespace webrtc
