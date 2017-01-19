/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/main/acm2/acm_receiver.h"

#include <algorithm>  // std::min

#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/main/interface/audio_coding_module.h"
#include "webrtc/modules/audio_coding/main/acm2/audio_coding_module_impl.h"
#include "webrtc/modules/audio_coding/main/acm2/acm_codec_database.h"
#include "webrtc/modules/audio_coding/neteq/tools/rtp_generator.h"
#include "webrtc/system_wrappers/interface/clock.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"
#include "webrtc/test/test_suite.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/test/testsupport/gtest_disable.h"

namespace webrtc {

namespace acm2 {
namespace {

bool CodecsEqual(const CodecInst& codec_a, const CodecInst& codec_b) {
    if (strcmp(codec_a.plname, codec_b.plname) != 0 ||
        codec_a.plfreq != codec_b.plfreq ||
        codec_a.pltype != codec_b.pltype ||
        codec_b.channels != codec_a.channels)
      return false;
    return true;
}

}  // namespace

class AcmReceiverTest : public AudioPacketizationCallback,
                        public ::testing::Test {
 protected:
  AcmReceiverTest()
      : timestamp_(0),
        packet_sent_(false),
        last_packet_send_timestamp_(timestamp_),
        last_frame_type_(kFrameEmpty) {
    AudioCodingModule::Config config;
    acm_.reset(new AudioCodingModuleImpl(config));
    receiver_.reset(new AcmReceiver(config));
  }

  ~AcmReceiverTest() {}

  void SetUp() {
    ASSERT_TRUE(receiver_.get() != NULL);
    ASSERT_TRUE(acm_.get() != NULL);
    for (int n = 0; n < ACMCodecDB::kNumCodecs; n++) {
      ASSERT_EQ(0, ACMCodecDB::Codec(n, &codecs_[n]));
    }

    acm_->InitializeReceiver();
    acm_->InitializeSender();
    acm_->RegisterTransportCallback(this);

    rtp_header_.header.sequenceNumber = 0;
    rtp_header_.header.timestamp = 0;
    rtp_header_.header.markerBit = false;
    rtp_header_.header.ssrc = 0x12345678;  // Arbitrary.
    rtp_header_.header.numCSRCs = 0;
    rtp_header_.header.payloadType = 0;
    rtp_header_.frameType = kAudioFrameSpeech;
    rtp_header_.type.Audio.isCNG = false;
  }

  void TearDown() {
  }

  void InsertOnePacketOfSilence(int codec_id) {
    CodecInst codec;
    ACMCodecDB::Codec(codec_id, &codec);
    if (timestamp_ == 0) {  // This is the first time inserting audio.
      ASSERT_EQ(0, acm_->RegisterSendCodec(codec));
    } else {
      CodecInst current_codec;
      ASSERT_EQ(0, acm_->SendCodec(&current_codec));
      if (!CodecsEqual(codec, current_codec))
        ASSERT_EQ(0, acm_->RegisterSendCodec(codec));
    }
    AudioFrame frame;
    // Frame setup according to the codec.
    frame.sample_rate_hz_ = codec.plfreq;
    frame.samples_per_channel_ = codec.plfreq / 100;  // 10 ms.
    frame.num_channels_ = codec.channels;
    memset(frame.data_, 0, frame.samples_per_channel_ * frame.num_channels_ *
           sizeof(int16_t));
    int num_bytes = 0;
    packet_sent_ = false;
    last_packet_send_timestamp_ = timestamp_;
    while (num_bytes == 0) {
      frame.timestamp_ = timestamp_;
      timestamp_ += frame.samples_per_channel_;
      ASSERT_EQ(0, acm_->Add10MsData(frame));
      num_bytes = acm_->Process();
      ASSERT_GE(num_bytes, 0);
    }
    ASSERT_TRUE(packet_sent_);  // Sanity check.
  }

  // Last element of id should be negative.
  void AddSetOfCodecs(const int* id) {
    int n = 0;
    while (id[n] >= 0) {
      ASSERT_EQ(0, receiver_->AddCodec(id[n], codecs_[id[n]].pltype,
                                       codecs_[id[n]].channels, NULL));
      ++n;
    }
  }

  virtual int SendData(
      FrameType frame_type,
      uint8_t payload_type,
      uint32_t timestamp,
      const uint8_t* payload_data,
      uint16_t payload_len_bytes,
      const RTPFragmentationHeader* fragmentation) {
    if (frame_type == kFrameEmpty)
      return 0;

    rtp_header_.header.payloadType = payload_type;
    rtp_header_.frameType = frame_type;
    if (frame_type == kAudioFrameSpeech)
      rtp_header_.type.Audio.isCNG = false;
    else
      rtp_header_.type.Audio.isCNG = true;
    rtp_header_.header.timestamp = timestamp;

    int ret_val = receiver_->InsertPacket(rtp_header_, payload_data,
                                          payload_len_bytes);
    if (ret_val < 0) {
      assert(false);
      return -1;
    }
    rtp_header_.header.sequenceNumber++;
    packet_sent_ = true;
    last_frame_type_ = frame_type;
    return 0;
  }

  scoped_ptr<AcmReceiver> receiver_;
  CodecInst codecs_[ACMCodecDB::kMaxNumCodecs];
  scoped_ptr<AudioCodingModule> acm_;
  WebRtcRTPHeader rtp_header_;
  uint32_t timestamp_;
  bool packet_sent_;  // Set when SendData is called reset when inserting audio.
  uint32_t last_packet_send_timestamp_;
  FrameType last_frame_type_;
};

TEST_F(AcmReceiverTest, DISABLED_ON_ANDROID(AddCodecGetCodec)) {
  // Add codec.
  for (int n = 0; n < ACMCodecDB::kNumCodecs; ++n) {
    if (n & 0x1)  // Just add codecs with odd index.
      EXPECT_EQ(0, receiver_->AddCodec(n, codecs_[n].pltype,
                                       codecs_[n].channels, NULL));
  }
  // Get codec and compare.
  for (int n = 0; n < ACMCodecDB::kNumCodecs; ++n) {
    CodecInst my_codec;
    if (n & 0x1) {
      // Codecs with odd index should match the reference.
      EXPECT_EQ(0, receiver_->DecoderByPayloadType(codecs_[n].pltype,
                                                   &my_codec));
      EXPECT_TRUE(CodecsEqual(codecs_[n], my_codec));
    } else {
      // Codecs with even index are not registered.
      EXPECT_EQ(-1, receiver_->DecoderByPayloadType(codecs_[n].pltype,
                                                    &my_codec));
    }
  }
}

TEST_F(AcmReceiverTest, DISABLED_ON_ANDROID(AddCodecChangePayloadType)) {
  CodecInst ref_codec;
  const int codec_id = ACMCodecDB::kPCMA;
  EXPECT_EQ(0, ACMCodecDB::Codec(codec_id, &ref_codec));
  const int payload_type = ref_codec.pltype;
  EXPECT_EQ(0, receiver_->AddCodec(codec_id, ref_codec.pltype,
                                   ref_codec.channels, NULL));
  CodecInst test_codec;
  EXPECT_EQ(0, receiver_->DecoderByPayloadType(payload_type, &test_codec));
  EXPECT_EQ(true, CodecsEqual(ref_codec, test_codec));

  // Re-register the same codec with different payload.
  ref_codec.pltype = payload_type + 1;
  EXPECT_EQ(0, receiver_->AddCodec(codec_id, ref_codec.pltype,
                                   ref_codec.channels, NULL));

  // Payload type |payload_type| should not exist.
  EXPECT_EQ(-1, receiver_->DecoderByPayloadType(payload_type, &test_codec));

  // Payload type |payload_type + 1| should exist.
  EXPECT_EQ(0, receiver_->DecoderByPayloadType(payload_type + 1, &test_codec));
  EXPECT_TRUE(CodecsEqual(test_codec, ref_codec));
}

TEST_F(AcmReceiverTest, DISABLED_ON_ANDROID(AddCodecRemoveCodec)) {
  CodecInst codec;
  const int codec_id = ACMCodecDB::kPCMA;
  EXPECT_EQ(0, ACMCodecDB::Codec(codec_id, &codec));
  const int payload_type = codec.pltype;
  EXPECT_EQ(0, receiver_->AddCodec(codec_id, codec.pltype,
                                   codec.channels, NULL));

  // Remove non-existing codec should not fail. ACM1 legacy.
  EXPECT_EQ(0, receiver_->RemoveCodec(payload_type + 1));

  // Remove an existing codec.
  EXPECT_EQ(0, receiver_->RemoveCodec(payload_type));

  // Ask for the removed codec, must fail.
  EXPECT_EQ(-1, receiver_->DecoderByPayloadType(payload_type, &codec));
}

TEST_F(AcmReceiverTest, DISABLED_ON_ANDROID(SampleRate)) {
  const int kCodecId[] = {
      ACMCodecDB::kISAC, ACMCodecDB::kISACSWB, ACMCodecDB::kISACFB,
      -1  // Terminator.
  };
  AddSetOfCodecs(kCodecId);

  AudioFrame frame;
  const int kOutSampleRateHz = 8000;  // Different than codec sample rate.
  int n = 0;
  while (kCodecId[n] >= 0) {
    const int num_10ms_frames = codecs_[kCodecId[n]].pacsize /
        (codecs_[kCodecId[n]].plfreq / 100);
    InsertOnePacketOfSilence(kCodecId[n]);
    for (int k = 0; k < num_10ms_frames; ++k) {
      EXPECT_EQ(0, receiver_->GetAudio(kOutSampleRateHz, &frame));
    }
    EXPECT_EQ(std::min(32000, codecs_[kCodecId[n]].plfreq),
              receiver_->current_sample_rate_hz());
    ++n;
  }
}

// Verify that the playout mode is set correctly.
TEST_F(AcmReceiverTest, DISABLED_ON_ANDROID(PlayoutMode)) {
  receiver_->SetPlayoutMode(voice);
  EXPECT_EQ(voice, receiver_->PlayoutMode());

  receiver_->SetPlayoutMode(streaming);
  EXPECT_EQ(streaming, receiver_->PlayoutMode());

  receiver_->SetPlayoutMode(fax);
  EXPECT_EQ(fax, receiver_->PlayoutMode());

  receiver_->SetPlayoutMode(off);
  EXPECT_EQ(off, receiver_->PlayoutMode());
}

TEST_F(AcmReceiverTest, DISABLED_ON_ANDROID(PostdecodingVad)) {
  receiver_->EnableVad();
  EXPECT_TRUE(receiver_->vad_enabled());

  const int id = ACMCodecDB::kPCM16Bwb;
  ASSERT_EQ(0, receiver_->AddCodec(id, codecs_[id].pltype, codecs_[id].channels,
                                   NULL));
  const int kNumPackets = 5;
  const int num_10ms_frames = codecs_[id].pacsize / (codecs_[id].plfreq / 100);
  AudioFrame frame;
  for (int n = 0; n < kNumPackets; ++n) {
    InsertOnePacketOfSilence(id);
    for (int k = 0; k < num_10ms_frames; ++k)
      ASSERT_EQ(0, receiver_->GetAudio(codecs_[id].plfreq, &frame));
  }
  EXPECT_EQ(AudioFrame::kVadPassive, frame.vad_activity_);

  receiver_->DisableVad();
  EXPECT_FALSE(receiver_->vad_enabled());

  for (int n = 0; n < kNumPackets; ++n) {
    InsertOnePacketOfSilence(id);
    for (int k = 0; k < num_10ms_frames; ++k)
      ASSERT_EQ(0, receiver_->GetAudio(codecs_[id].plfreq, &frame));
  }
  EXPECT_EQ(AudioFrame::kVadUnknown, frame.vad_activity_);
}

TEST_F(AcmReceiverTest, DISABLED_ON_ANDROID(LastAudioCodec)) {
  const int kCodecId[] = {
      ACMCodecDB::kISAC, ACMCodecDB::kPCMA, ACMCodecDB::kISACSWB,
      ACMCodecDB::kPCM16Bswb32kHz, ACMCodecDB::kG722_1C_48,
      -1  // Terminator.
  };
  AddSetOfCodecs(kCodecId);

  const int kCngId[] = {  // Not including full-band.
      ACMCodecDB::kCNNB, ACMCodecDB::kCNWB, ACMCodecDB::kCNSWB,
      -1  // Terminator.
  };
  AddSetOfCodecs(kCngId);

  // Register CNG at sender side.
  int n = 0;
  while (kCngId[n] > 0) {
    ASSERT_EQ(0, acm_->RegisterSendCodec(codecs_[kCngId[n]]));
    ++n;
  }

  CodecInst codec;
  // No audio payload is received.
  EXPECT_EQ(-1, receiver_->LastAudioCodec(&codec));

  // Start with sending DTX.
  ASSERT_EQ(0, acm_->SetVAD(true, true, VADVeryAggr));
  packet_sent_ = false;
  InsertOnePacketOfSilence(kCodecId[0]);  // Enough to test with one codec.
  ASSERT_TRUE(packet_sent_);
  EXPECT_EQ(kAudioFrameCN, last_frame_type_);

  // Has received, only, DTX. Last Audio codec is undefined.
  EXPECT_EQ(-1, receiver_->LastAudioCodec(&codec));
  EXPECT_EQ(-1, receiver_->last_audio_codec_id());
  EXPECT_EQ(-1, receiver_->last_audio_payload_type());

  n = 0;
  while (kCodecId[n] >= 0) {  // Loop over codecs.
    // Set DTX off to send audio payload.
    acm_->SetVAD(false, false, VADAggr);
    packet_sent_ = false;
    InsertOnePacketOfSilence(kCodecId[n]);

    // Sanity check if Actually an audio payload received, and it should be
    // of type "speech."
    ASSERT_TRUE(packet_sent_);
    ASSERT_EQ(kAudioFrameSpeech, last_frame_type_);
    EXPECT_EQ(kCodecId[n], receiver_->last_audio_codec_id());

    // Set VAD on to send DTX. Then check if the "Last Audio codec" returns
    // the expected codec.
    acm_->SetVAD(true, true, VADAggr);

    // Do as many encoding until a DTX is sent.
    while (last_frame_type_ != kAudioFrameCN) {
      packet_sent_ = false;
      InsertOnePacketOfSilence(kCodecId[n]);
      ASSERT_TRUE(packet_sent_);
    }
    EXPECT_EQ(kCodecId[n], receiver_->last_audio_codec_id());
    EXPECT_EQ(codecs_[kCodecId[n]].pltype,
              receiver_->last_audio_payload_type());
    EXPECT_EQ(0, receiver_->LastAudioCodec(&codec));
    EXPECT_TRUE(CodecsEqual(codecs_[kCodecId[n]], codec));
    ++n;
  }
}

}  // namespace acm2

}  // namespace webrtc
