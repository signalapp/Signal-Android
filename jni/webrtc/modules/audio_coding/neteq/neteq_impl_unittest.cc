/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "webrtc/modules/audio_coding/neteq/include/neteq.h"
#include "webrtc/modules/audio_coding/neteq/neteq_impl.h"

#include "testing/gmock/include/gmock/gmock.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/safe_conversions.h"
#include "webrtc/modules/audio_coding/codecs/builtin_audio_decoder_factory.h"
#include "webrtc/modules/audio_coding/codecs/mock/mock_audio_decoder_factory.h"
#include "webrtc/modules/audio_coding/neteq/accelerate.h"
#include "webrtc/modules/audio_coding/neteq/expand.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_audio_decoder.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_buffer_level_filter.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_decoder_database.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_delay_manager.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_delay_peak_detector.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_dtmf_buffer.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_dtmf_tone_generator.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_packet_buffer.h"
#include "webrtc/modules/audio_coding/neteq/mock/mock_payload_splitter.h"
#include "webrtc/modules/audio_coding/neteq/preemptive_expand.h"
#include "webrtc/modules/audio_coding/neteq/sync_buffer.h"
#include "webrtc/modules/audio_coding/neteq/timestamp_scaler.h"
#include "webrtc/modules/include/module_common_types.h"

using ::testing::AtLeast;
using ::testing::Return;
using ::testing::ReturnNull;
using ::testing::_;
using ::testing::SetArgPointee;
using ::testing::SetArrayArgument;
using ::testing::InSequence;
using ::testing::Invoke;
using ::testing::WithArg;
using ::testing::Pointee;
using ::testing::IsNull;

namespace webrtc {

// This function is called when inserting a packet list into the mock packet
// buffer. The purpose is to delete all inserted packets properly, to avoid
// memory leaks in the test.
int DeletePacketsAndReturnOk(PacketList* packet_list) {
  PacketBuffer::DeleteAllPackets(packet_list);
  return PacketBuffer::kOK;
}

class NetEqImplTest : public ::testing::Test {
 protected:
  NetEqImplTest() { config_.sample_rate_hz = 8000; }

  void CreateInstance() {
    NetEqImpl::Dependencies deps(config_, CreateBuiltinAudioDecoderFactory());

    // Get a local pointer to NetEq's TickTimer object.
    tick_timer_ = deps.tick_timer.get();

    if (use_mock_buffer_level_filter_) {
      std::unique_ptr<MockBufferLevelFilter> mock(new MockBufferLevelFilter);
      mock_buffer_level_filter_ = mock.get();
      deps.buffer_level_filter = std::move(mock);
    }
    buffer_level_filter_ = deps.buffer_level_filter.get();

    if (use_mock_decoder_database_) {
      std::unique_ptr<MockDecoderDatabase> mock(new MockDecoderDatabase);
      mock_decoder_database_ = mock.get();
      EXPECT_CALL(*mock_decoder_database_, GetActiveCngDecoder())
          .WillOnce(ReturnNull());
      deps.decoder_database = std::move(mock);
    }
    decoder_database_ = deps.decoder_database.get();

    if (use_mock_delay_peak_detector_) {
      std::unique_ptr<MockDelayPeakDetector> mock(
          new MockDelayPeakDetector(tick_timer_));
      mock_delay_peak_detector_ = mock.get();
      EXPECT_CALL(*mock_delay_peak_detector_, Reset()).Times(1);
      deps.delay_peak_detector = std::move(mock);
    }
    delay_peak_detector_ = deps.delay_peak_detector.get();

    if (use_mock_delay_manager_) {
      std::unique_ptr<MockDelayManager> mock(new MockDelayManager(
          config_.max_packets_in_buffer, delay_peak_detector_, tick_timer_));
      mock_delay_manager_ = mock.get();
      EXPECT_CALL(*mock_delay_manager_, set_streaming_mode(false)).Times(1);
      deps.delay_manager = std::move(mock);
    }
    delay_manager_ = deps.delay_manager.get();

    if (use_mock_dtmf_buffer_) {
      std::unique_ptr<MockDtmfBuffer> mock(
          new MockDtmfBuffer(config_.sample_rate_hz));
      mock_dtmf_buffer_ = mock.get();
      deps.dtmf_buffer = std::move(mock);
    }
    dtmf_buffer_ = deps.dtmf_buffer.get();

    if (use_mock_dtmf_tone_generator_) {
      std::unique_ptr<MockDtmfToneGenerator> mock(new MockDtmfToneGenerator);
      mock_dtmf_tone_generator_ = mock.get();
      deps.dtmf_tone_generator = std::move(mock);
    }
    dtmf_tone_generator_ = deps.dtmf_tone_generator.get();

    if (use_mock_packet_buffer_) {
      std::unique_ptr<MockPacketBuffer> mock(
          new MockPacketBuffer(config_.max_packets_in_buffer, tick_timer_));
      mock_packet_buffer_ = mock.get();
      deps.packet_buffer = std::move(mock);
    }
    packet_buffer_ = deps.packet_buffer.get();

    if (use_mock_payload_splitter_) {
      std::unique_ptr<MockPayloadSplitter> mock(new MockPayloadSplitter);
      mock_payload_splitter_ = mock.get();
      deps.payload_splitter = std::move(mock);
    }
    payload_splitter_ = deps.payload_splitter.get();

    deps.timestamp_scaler = std::unique_ptr<TimestampScaler>(
        new TimestampScaler(*deps.decoder_database.get()));

    neteq_.reset(new NetEqImpl(config_, std::move(deps)));
    ASSERT_TRUE(neteq_ != NULL);
  }

  void UseNoMocks() {
    ASSERT_TRUE(neteq_ == NULL) << "Must call UseNoMocks before CreateInstance";
    use_mock_buffer_level_filter_ = false;
    use_mock_decoder_database_ = false;
    use_mock_delay_peak_detector_ = false;
    use_mock_delay_manager_ = false;
    use_mock_dtmf_buffer_ = false;
    use_mock_dtmf_tone_generator_ = false;
    use_mock_packet_buffer_ = false;
    use_mock_payload_splitter_ = false;
  }

  virtual ~NetEqImplTest() {
    if (use_mock_buffer_level_filter_) {
      EXPECT_CALL(*mock_buffer_level_filter_, Die()).Times(1);
    }
    if (use_mock_decoder_database_) {
      EXPECT_CALL(*mock_decoder_database_, Die()).Times(1);
    }
    if (use_mock_delay_manager_) {
      EXPECT_CALL(*mock_delay_manager_, Die()).Times(1);
    }
    if (use_mock_delay_peak_detector_) {
      EXPECT_CALL(*mock_delay_peak_detector_, Die()).Times(1);
    }
    if (use_mock_dtmf_buffer_) {
      EXPECT_CALL(*mock_dtmf_buffer_, Die()).Times(1);
    }
    if (use_mock_dtmf_tone_generator_) {
      EXPECT_CALL(*mock_dtmf_tone_generator_, Die()).Times(1);
    }
    if (use_mock_packet_buffer_) {
      EXPECT_CALL(*mock_packet_buffer_, Die()).Times(1);
    }
  }

  std::unique_ptr<NetEqImpl> neteq_;
  NetEq::Config config_;
  TickTimer* tick_timer_ = nullptr;
  MockBufferLevelFilter* mock_buffer_level_filter_ = nullptr;
  BufferLevelFilter* buffer_level_filter_ = nullptr;
  bool use_mock_buffer_level_filter_ = true;
  MockDecoderDatabase* mock_decoder_database_ = nullptr;
  DecoderDatabase* decoder_database_ = nullptr;
  bool use_mock_decoder_database_ = true;
  MockDelayPeakDetector* mock_delay_peak_detector_ = nullptr;
  DelayPeakDetector* delay_peak_detector_ = nullptr;
  bool use_mock_delay_peak_detector_ = true;
  MockDelayManager* mock_delay_manager_ = nullptr;
  DelayManager* delay_manager_ = nullptr;
  bool use_mock_delay_manager_ = true;
  MockDtmfBuffer* mock_dtmf_buffer_ = nullptr;
  DtmfBuffer* dtmf_buffer_ = nullptr;
  bool use_mock_dtmf_buffer_ = true;
  MockDtmfToneGenerator* mock_dtmf_tone_generator_ = nullptr;
  DtmfToneGenerator* dtmf_tone_generator_ = nullptr;
  bool use_mock_dtmf_tone_generator_ = true;
  MockPacketBuffer* mock_packet_buffer_ = nullptr;
  PacketBuffer* packet_buffer_ = nullptr;
  bool use_mock_packet_buffer_ = true;
  MockPayloadSplitter* mock_payload_splitter_ = nullptr;
  PayloadSplitter* payload_splitter_ = nullptr;
  bool use_mock_payload_splitter_ = true;
};


// This tests the interface class NetEq.
// TODO(hlundin): Move to separate file?
TEST(NetEq, CreateAndDestroy) {
  NetEq::Config config;
  NetEq* neteq = NetEq::Create(config, CreateBuiltinAudioDecoderFactory());
  delete neteq;
}

TEST_F(NetEqImplTest, RegisterPayloadType) {
  CreateInstance();
  uint8_t rtp_payload_type = 0;
  NetEqDecoder codec_type = NetEqDecoder::kDecoderPCMu;
  const std::string kCodecName = "Robert\'); DROP TABLE Students;";
  EXPECT_CALL(*mock_decoder_database_,
              RegisterPayload(rtp_payload_type, codec_type, kCodecName));
  neteq_->RegisterPayloadType(codec_type, kCodecName, rtp_payload_type);
}

TEST_F(NetEqImplTest, RemovePayloadType) {
  CreateInstance();
  uint8_t rtp_payload_type = 0;
  EXPECT_CALL(*mock_decoder_database_, Remove(rtp_payload_type))
      .WillOnce(Return(DecoderDatabase::kDecoderNotFound));
  // Check that kFail is returned when database returns kDecoderNotFound.
  EXPECT_EQ(NetEq::kFail, neteq_->RemovePayloadType(rtp_payload_type));
}

TEST_F(NetEqImplTest, InsertPacket) {
  CreateInstance();
  const size_t kPayloadLength = 100;
  const uint8_t kPayloadType = 0;
  const uint16_t kFirstSequenceNumber = 0x1234;
  const uint32_t kFirstTimestamp = 0x12345678;
  const uint32_t kSsrc = 0x87654321;
  const uint32_t kFirstReceiveTime = 17;
  uint8_t payload[kPayloadLength] = {0};
  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = kFirstSequenceNumber;
  rtp_header.header.timestamp = kFirstTimestamp;
  rtp_header.header.ssrc = kSsrc;

  rtc::scoped_refptr<MockAudioDecoderFactory> mock_decoder_factory(
      new rtc::RefCountedObject<MockAudioDecoderFactory>);
  EXPECT_CALL(*mock_decoder_factory, MakeAudioDecoderMock(_, _))
      .WillOnce(Invoke([kPayloadLength, kFirstSequenceNumber, kFirstTimestamp,
                        kFirstReceiveTime](const SdpAudioFormat& format,
                                           std::unique_ptr<AudioDecoder>* dec) {
        EXPECT_EQ("pcmu", format.name);

        std::unique_ptr<MockAudioDecoder> mock_decoder(new MockAudioDecoder);
        EXPECT_CALL(*mock_decoder, Channels()).WillRepeatedly(Return(1));
        EXPECT_CALL(*mock_decoder, SampleRateHz()).WillRepeatedly(Return(8000));
        // BWE update function called with first packet.
        EXPECT_CALL(*mock_decoder,
                    IncomingPacket(_, kPayloadLength, kFirstSequenceNumber,
                                   kFirstTimestamp, kFirstReceiveTime));
        // BWE update function called with second packet.
        EXPECT_CALL(
            *mock_decoder,
            IncomingPacket(_, kPayloadLength, kFirstSequenceNumber + 1,
                           kFirstTimestamp + 160, kFirstReceiveTime + 155));
        EXPECT_CALL(*mock_decoder, Die()).Times(1);  // Called when deleted.

        *dec = std::move(mock_decoder);
      }));
  DecoderDatabase::DecoderInfo info(NetEqDecoder::kDecoderPCMu, "");

  // Expectations for decoder database.
  EXPECT_CALL(*mock_decoder_database_, IsRed(kPayloadType))
      .WillRepeatedly(Return(false));  // This is not RED.
  EXPECT_CALL(*mock_decoder_database_, CheckPayloadTypes(_))
      .Times(2)
      .WillRepeatedly(Return(DecoderDatabase::kOK));  // Payload type is valid.
  EXPECT_CALL(*mock_decoder_database_, IsDtmf(kPayloadType))
      .WillRepeatedly(Return(false));  // This is not DTMF.
  EXPECT_CALL(*mock_decoder_database_, GetDecoder(kPayloadType))
      .Times(3)
      .WillRepeatedly(
          Invoke([&info, mock_decoder_factory](uint8_t payload_type) {
            return info.GetDecoder(mock_decoder_factory);
          }));
  EXPECT_CALL(*mock_decoder_database_, IsComfortNoise(kPayloadType))
      .WillRepeatedly(Return(false));  // This is not CNG.
  EXPECT_CALL(*mock_decoder_database_, GetDecoderInfo(kPayloadType))
      .WillRepeatedly(Return(&info));

  // Expectations for packet buffer.
  EXPECT_CALL(*mock_packet_buffer_, NumPacketsInBuffer())
      .WillOnce(Return(0))   // First packet.
      .WillOnce(Return(1))   // Second packet.
      .WillOnce(Return(2));  // Second packet, checking after it was inserted.
  EXPECT_CALL(*mock_packet_buffer_, Empty())
      .WillOnce(Return(false));  // Called once after first packet is inserted.
  EXPECT_CALL(*mock_packet_buffer_, Flush())
      .Times(1);
  EXPECT_CALL(*mock_packet_buffer_, InsertPacketList(_, _, _, _))
      .Times(2)
      .WillRepeatedly(DoAll(SetArgPointee<2>(kPayloadType),
                            WithArg<0>(Invoke(DeletePacketsAndReturnOk))));
  // SetArgPointee<2>(kPayloadType) means that the third argument (zero-based
  // index) is a pointer, and the variable pointed to is set to kPayloadType.
  // Also invoke the function DeletePacketsAndReturnOk to properly delete all
  // packets in the list (to avoid memory leaks in the test).
  EXPECT_CALL(*mock_packet_buffer_, NextRtpHeader())
      .Times(1)
      .WillOnce(Return(&rtp_header.header));

  // Expectations for DTMF buffer.
  EXPECT_CALL(*mock_dtmf_buffer_, Flush())
      .Times(1);

  // Expectations for delay manager.
  {
    // All expectations within this block must be called in this specific order.
    InSequence sequence;  // Dummy variable.
    // Expectations when the first packet is inserted.
    EXPECT_CALL(*mock_delay_manager_,
                LastDecoderType(NetEqDecoder::kDecoderPCMu))
        .Times(1);
    EXPECT_CALL(*mock_delay_manager_, last_pack_cng_or_dtmf())
        .Times(2)
        .WillRepeatedly(Return(-1));
    EXPECT_CALL(*mock_delay_manager_, set_last_pack_cng_or_dtmf(0))
        .Times(1);
    EXPECT_CALL(*mock_delay_manager_, ResetPacketIatCount()).Times(1);
    // Expectations when the second packet is inserted. Slightly different.
    EXPECT_CALL(*mock_delay_manager_,
                LastDecoderType(NetEqDecoder::kDecoderPCMu))
        .Times(1);
    EXPECT_CALL(*mock_delay_manager_, last_pack_cng_or_dtmf())
        .WillOnce(Return(0));
    EXPECT_CALL(*mock_delay_manager_, SetPacketAudioLength(30))
        .WillOnce(Return(0));
  }

  // Expectations for payload splitter.
  EXPECT_CALL(*mock_payload_splitter_, SplitFec(_, _))
      .Times(2)
      .WillRepeatedly(Return(PayloadSplitter::kOK));
  EXPECT_CALL(*mock_payload_splitter_, SplitAudio(_, _))
      .Times(2)
      .WillRepeatedly(Return(PayloadSplitter::kOK));

  // Insert first packet.
  neteq_->InsertPacket(rtp_header, payload, kFirstReceiveTime);

  // Insert second packet.
  rtp_header.header.timestamp += 160;
  rtp_header.header.sequenceNumber += 1;
  neteq_->InsertPacket(rtp_header, payload, kFirstReceiveTime + 155);
}

TEST_F(NetEqImplTest, InsertPacketsUntilBufferIsFull) {
  UseNoMocks();
  CreateInstance();

  const int kPayloadLengthSamples = 80;
  const size_t kPayloadLengthBytes = 2 * kPayloadLengthSamples;  // PCM 16-bit.
  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const uint32_t kReceiveTime = 17;  // Value doesn't matter for this test.
  uint8_t payload[kPayloadLengthBytes] = {0};
  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = 0x1234;
  rtp_header.header.timestamp = 0x12345678;
  rtp_header.header.ssrc = 0x87654321;

  EXPECT_EQ(NetEq::kOK, neteq_->RegisterPayloadType(
                            NetEqDecoder::kDecoderPCM16B, "", kPayloadType));

  // Insert packets. The buffer should not flush.
  for (size_t i = 1; i <= config_.max_packets_in_buffer; ++i) {
    EXPECT_EQ(NetEq::kOK,
              neteq_->InsertPacket(rtp_header, payload, kReceiveTime));
    rtp_header.header.timestamp += kPayloadLengthSamples;
    rtp_header.header.sequenceNumber += 1;
    EXPECT_EQ(i, packet_buffer_->NumPacketsInBuffer());
  }

  // Insert one more packet and make sure the buffer got flushed. That is, it
  // should only hold one single packet.
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));
  EXPECT_EQ(1u, packet_buffer_->NumPacketsInBuffer());
  const RTPHeader* test_header = packet_buffer_->NextRtpHeader();
  EXPECT_EQ(rtp_header.header.timestamp, test_header->timestamp);
  EXPECT_EQ(rtp_header.header.sequenceNumber, test_header->sequenceNumber);
}

// This test verifies that timestamps propagate from the incoming packets
// through to the sync buffer and to the playout timestamp.
TEST_F(NetEqImplTest, VerifyTimestampPropagation) {
  UseNoMocks();
  CreateInstance();

  const uint8_t kPayloadType = 17;   // Just an arbitrary number.
  const uint32_t kReceiveTime = 17;  // Value doesn't matter for this test.
  const int kSampleRateHz = 8000;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = kPayloadLengthSamples;
  uint8_t payload[kPayloadLengthBytes] = {0};
  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = 0x1234;
  rtp_header.header.timestamp = 0x12345678;
  rtp_header.header.ssrc = 0x87654321;

  // This is a dummy decoder that produces as many output samples as the input
  // has bytes. The output is an increasing series, starting at 1 for the first
  // sample, and then increasing by 1 for each sample.
  class CountingSamplesDecoder : public AudioDecoder {
   public:
    CountingSamplesDecoder() : next_value_(1) {}

    // Produce as many samples as input bytes (|encoded_len|).
    int DecodeInternal(const uint8_t* encoded,
                       size_t encoded_len,
                       int /* sample_rate_hz */,
                       int16_t* decoded,
                       SpeechType* speech_type) override {
      for (size_t i = 0; i < encoded_len; ++i) {
        decoded[i] = next_value_++;
      }
      *speech_type = kSpeech;
      return encoded_len;
    }

    void Reset() override { next_value_ = 1; }

    int SampleRateHz() const override { return kSampleRateHz; }

    size_t Channels() const override { return 1; }

    uint16_t next_value() const { return next_value_; }

   private:
    int16_t next_value_;
  } decoder_;

  EXPECT_EQ(NetEq::kOK, neteq_->RegisterExternalDecoder(
                            &decoder_, NetEqDecoder::kDecoderPCM16B,
                            "dummy name", kPayloadType));

  // Insert one packet.
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));

  // Pull audio once.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  ASSERT_FALSE(muted);
  ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

  // Start with a simple check that the fake decoder is behaving as expected.
  EXPECT_EQ(kPayloadLengthSamples,
            static_cast<size_t>(decoder_.next_value() - 1));

  // The value of the last of the output samples is the same as the number of
  // samples played from the decoded packet. Thus, this number + the RTP
  // timestamp should match the playout timestamp.
  // Wrap the expected value in an rtc::Optional to compare them as such.
  EXPECT_EQ(
      rtc::Optional<uint32_t>(rtp_header.header.timestamp +
                              output.data_[output.samples_per_channel_ - 1]),
      neteq_->GetPlayoutTimestamp());

  // Check the timestamp for the last value in the sync buffer. This should
  // be one full frame length ahead of the RTP timestamp.
  const SyncBuffer* sync_buffer = neteq_->sync_buffer_for_test();
  ASSERT_TRUE(sync_buffer != NULL);
  EXPECT_EQ(rtp_header.header.timestamp + kPayloadLengthSamples,
            sync_buffer->end_timestamp());

  // Check that the number of samples still to play from the sync buffer add
  // up with what was already played out.
  EXPECT_EQ(
      kPayloadLengthSamples - output.data_[output.samples_per_channel_ - 1],
      sync_buffer->FutureLength());
}

TEST_F(NetEqImplTest, ReorderedPacket) {
  UseNoMocks();
  CreateInstance();

  const uint8_t kPayloadType = 17;   // Just an arbitrary number.
  const uint32_t kReceiveTime = 17;  // Value doesn't matter for this test.
  const int kSampleRateHz = 8000;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = kPayloadLengthSamples;
  uint8_t payload[kPayloadLengthBytes] = {0};
  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = 0x1234;
  rtp_header.header.timestamp = 0x12345678;
  rtp_header.header.ssrc = 0x87654321;

  // Create a mock decoder object.
  MockAudioDecoder mock_decoder;
  EXPECT_CALL(mock_decoder, Reset()).WillRepeatedly(Return());
  EXPECT_CALL(mock_decoder, SampleRateHz())
      .WillRepeatedly(Return(kSampleRateHz));
  EXPECT_CALL(mock_decoder, Channels()).WillRepeatedly(Return(1));
  EXPECT_CALL(mock_decoder, IncomingPacket(_, kPayloadLengthBytes, _, _, _))
      .WillRepeatedly(Return(0));
  EXPECT_CALL(mock_decoder, PacketDuration(_, kPayloadLengthBytes))
      .WillRepeatedly(Return(kPayloadLengthSamples));
  int16_t dummy_output[kPayloadLengthSamples] = {0};
  // The below expectation will make the mock decoder write
  // |kPayloadLengthSamples| zeros to the output array, and mark it as speech.
  EXPECT_CALL(mock_decoder, DecodeInternal(Pointee(0), kPayloadLengthBytes,
                                           kSampleRateHz, _, _))
      .WillOnce(DoAll(SetArrayArgument<3>(dummy_output,
                                          dummy_output + kPayloadLengthSamples),
                      SetArgPointee<4>(AudioDecoder::kSpeech),
                      Return(kPayloadLengthSamples)));
  EXPECT_EQ(NetEq::kOK, neteq_->RegisterExternalDecoder(
                            &mock_decoder, NetEqDecoder::kDecoderPCM16B,
                            "dummy name", kPayloadType));

  // Insert one packet.
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));

  // Pull audio once.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

  // Insert two more packets. The first one is out of order, and is already too
  // old, the second one is the expected next packet.
  rtp_header.header.sequenceNumber -= 1;
  rtp_header.header.timestamp -= kPayloadLengthSamples;
  payload[0] = 1;
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));
  rtp_header.header.sequenceNumber += 2;
  rtp_header.header.timestamp += 2 * kPayloadLengthSamples;
  payload[0] = 2;
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));

  // Expect only the second packet to be decoded (the one with "2" as the first
  // payload byte).
  EXPECT_CALL(mock_decoder, DecodeInternal(Pointee(2), kPayloadLengthBytes,
                                           kSampleRateHz, _, _))
      .WillOnce(DoAll(SetArrayArgument<3>(dummy_output,
                                          dummy_output + kPayloadLengthSamples),
                      SetArgPointee<4>(AudioDecoder::kSpeech),
                      Return(kPayloadLengthSamples)));

  // Pull audio once.
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

  // Now check the packet buffer, and make sure it is empty, since the
  // out-of-order packet should have been discarded.
  EXPECT_TRUE(packet_buffer_->Empty());

  EXPECT_CALL(mock_decoder, Die());
}

// This test verifies that NetEq can handle the situation where the first
// incoming packet is rejected.
TEST_F(NetEqImplTest, FirstPacketUnknown) {
  UseNoMocks();
  CreateInstance();

  const uint8_t kPayloadType = 17;   // Just an arbitrary number.
  const uint32_t kReceiveTime = 17;  // Value doesn't matter for this test.
  const int kSampleRateHz = 8000;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = kPayloadLengthSamples;
  uint8_t payload[kPayloadLengthBytes] = {0};
  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = 0x1234;
  rtp_header.header.timestamp = 0x12345678;
  rtp_header.header.ssrc = 0x87654321;

  // Insert one packet. Note that we have not registered any payload type, so
  // this packet will be rejected.
  EXPECT_EQ(NetEq::kFail,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));
  EXPECT_EQ(NetEq::kUnknownRtpPayloadType, neteq_->LastError());

  // Pull audio once.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  ASSERT_LE(output.samples_per_channel_, kMaxOutputSize);
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kPLC, output.speech_type_);

  // Register the payload type.
  EXPECT_EQ(NetEq::kOK, neteq_->RegisterPayloadType(
                            NetEqDecoder::kDecoderPCM16B, "", kPayloadType));

  // Insert 10 packets.
  for (size_t i = 0; i < 10; ++i) {
    rtp_header.header.sequenceNumber++;
    rtp_header.header.timestamp += kPayloadLengthSamples;
    EXPECT_EQ(NetEq::kOK,
              neteq_->InsertPacket(rtp_header, payload, kReceiveTime));
    EXPECT_EQ(i + 1, packet_buffer_->NumPacketsInBuffer());
  }

  // Pull audio repeatedly and make sure we get normal output, that is not PLC.
  for (size_t i = 0; i < 3; ++i) {
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
    ASSERT_LE(output.samples_per_channel_, kMaxOutputSize);
    EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
    EXPECT_EQ(1u, output.num_channels_);
    EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_)
        << "NetEq did not decode the packets as expected.";
  }
}

// This test verifies that NetEq can handle comfort noise and enters/quits codec
// internal CNG mode properly.
TEST_F(NetEqImplTest, CodecInternalCng) {
  UseNoMocks();
  CreateInstance();

  const uint8_t kPayloadType = 17;   // Just an arbitrary number.
  const uint32_t kReceiveTime = 17;  // Value doesn't matter for this test.
  const int kSampleRateKhz = 48;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(20 * kSampleRateKhz);  // 20 ms.
  const size_t kPayloadLengthBytes = 10;
  uint8_t payload[kPayloadLengthBytes] = {0};
  int16_t dummy_output[kPayloadLengthSamples] = {0};

  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = 0x1234;
  rtp_header.header.timestamp = 0x12345678;
  rtp_header.header.ssrc = 0x87654321;

  // Create a mock decoder object.
  MockAudioDecoder mock_decoder;
  EXPECT_CALL(mock_decoder, Reset()).WillRepeatedly(Return());
  EXPECT_CALL(mock_decoder, SampleRateHz())
      .WillRepeatedly(Return(kSampleRateKhz * 1000));
  EXPECT_CALL(mock_decoder, Channels()).WillRepeatedly(Return(1));
  EXPECT_CALL(mock_decoder, IncomingPacket(_, kPayloadLengthBytes, _, _, _))
      .WillRepeatedly(Return(0));
  EXPECT_CALL(mock_decoder, PacketDuration(_, kPayloadLengthBytes))
      .WillRepeatedly(Return(kPayloadLengthSamples));
  // Packed duration when asking the decoder for more CNG data (without a new
  // packet).
  EXPECT_CALL(mock_decoder, PacketDuration(nullptr, 0))
      .WillRepeatedly(Return(kPayloadLengthSamples));

  // Pointee(x) verifies that first byte of the payload equals x, this makes it
  // possible to verify that the correct payload is fed to Decode().
  EXPECT_CALL(mock_decoder, DecodeInternal(Pointee(0), kPayloadLengthBytes,
                                           kSampleRateKhz * 1000, _, _))
      .WillOnce(DoAll(SetArrayArgument<3>(dummy_output,
                                          dummy_output + kPayloadLengthSamples),
                      SetArgPointee<4>(AudioDecoder::kSpeech),
                      Return(kPayloadLengthSamples)));

  EXPECT_CALL(mock_decoder, DecodeInternal(Pointee(1), kPayloadLengthBytes,
                                           kSampleRateKhz * 1000, _, _))
      .WillOnce(DoAll(SetArrayArgument<3>(dummy_output,
                                          dummy_output + kPayloadLengthSamples),
                      SetArgPointee<4>(AudioDecoder::kComfortNoise),
                      Return(kPayloadLengthSamples)));

  EXPECT_CALL(mock_decoder,
              DecodeInternal(IsNull(), 0, kSampleRateKhz * 1000, _, _))
      .WillOnce(DoAll(SetArrayArgument<3>(dummy_output,
                                          dummy_output + kPayloadLengthSamples),
                      SetArgPointee<4>(AudioDecoder::kComfortNoise),
                      Return(kPayloadLengthSamples)));

  EXPECT_CALL(mock_decoder, DecodeInternal(Pointee(2), kPayloadLengthBytes,
                                           kSampleRateKhz * 1000, _, _))
      .WillOnce(DoAll(SetArrayArgument<3>(dummy_output,
                                          dummy_output + kPayloadLengthSamples),
                      SetArgPointee<4>(AudioDecoder::kSpeech),
                      Return(kPayloadLengthSamples)));

  EXPECT_EQ(NetEq::kOK, neteq_->RegisterExternalDecoder(
                            &mock_decoder, NetEqDecoder::kDecoderOpus,
                            "dummy name", kPayloadType));

  // Insert one packet (decoder will return speech).
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));

  // Insert second packet (decoder will return CNG).
  payload[0] = 1;
  rtp_header.header.sequenceNumber++;
  rtp_header.header.timestamp += kPayloadLengthSamples;
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));

  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateKhz);
  AudioFrame output;
  AudioFrame::SpeechType expected_type[8] = {
      AudioFrame::kNormalSpeech, AudioFrame::kNormalSpeech,
      AudioFrame::kCNG, AudioFrame::kCNG,
      AudioFrame::kCNG, AudioFrame::kCNG,
      AudioFrame::kNormalSpeech, AudioFrame::kNormalSpeech
  };
  int expected_timestamp_increment[8] = {
      -1,  // will not be used.
      10 * kSampleRateKhz,
      -1, -1,  // timestamp will be empty during CNG mode; indicated by -1 here.
      -1, -1,
      50 * kSampleRateKhz, 10 * kSampleRateKhz
  };

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  rtc::Optional<uint32_t> last_timestamp = neteq_->GetPlayoutTimestamp();
  ASSERT_TRUE(last_timestamp);

  // Lambda for verifying the timestamps.
  auto verify_timestamp = [&last_timestamp, &expected_timestamp_increment](
      rtc::Optional<uint32_t> ts, size_t i) {
    if (expected_timestamp_increment[i] == -1) {
      // Expect to get an empty timestamp value during CNG and PLC.
      EXPECT_FALSE(ts) << "i = " << i;
    } else {
      ASSERT_TRUE(ts) << "i = " << i;
      EXPECT_EQ(*ts, *last_timestamp + expected_timestamp_increment[i])
          << "i = " << i;
      last_timestamp = ts;
    }
  };

  for (size_t i = 1; i < 6; ++i) {
    ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
    EXPECT_EQ(1u, output.num_channels_);
    EXPECT_EQ(expected_type[i - 1], output.speech_type_);
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
    SCOPED_TRACE("");
    verify_timestamp(neteq_->GetPlayoutTimestamp(), i);
  }

  // Insert third packet, which leaves a gap from last packet.
  payload[0] = 2;
  rtp_header.header.sequenceNumber += 2;
  rtp_header.header.timestamp += 2 * kPayloadLengthSamples;
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));

  for (size_t i = 6; i < 8; ++i) {
    ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
    EXPECT_EQ(1u, output.num_channels_);
    EXPECT_EQ(expected_type[i - 1], output.speech_type_);
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
    SCOPED_TRACE("");
    verify_timestamp(neteq_->GetPlayoutTimestamp(), i);
  }

  // Now check the packet buffer, and make sure it is empty.
  EXPECT_TRUE(packet_buffer_->Empty());

  EXPECT_CALL(mock_decoder, Die());
}

TEST_F(NetEqImplTest, UnsupportedDecoder) {
  UseNoMocks();
  CreateInstance();
  static const size_t kNetEqMaxFrameSize = 5760;  // 120 ms @ 48 kHz.
  static const size_t kChannels = 2;

  const uint8_t kPayloadType = 17;   // Just an arbitrary number.
  const uint32_t kReceiveTime = 17;  // Value doesn't matter for this test.
  const int kSampleRateHz = 8000;

  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = 1;
  uint8_t payload[kPayloadLengthBytes] = {0};
  int16_t dummy_output[kPayloadLengthSamples * kChannels] = {0};
  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = 0x1234;
  rtp_header.header.timestamp = 0x12345678;
  rtp_header.header.ssrc = 0x87654321;

  class MockAudioDecoder : public AudioDecoder {
   public:
    // TODO(nisse): Valid overrides commented out, because the gmock
    // methods don't use any override declarations, and we want to avoid
    // warnings from -Winconsistent-missing-override. See
    // http://crbug.com/428099.
    void Reset() /* override */ {}
    MOCK_CONST_METHOD2(PacketDuration, int(const uint8_t*, size_t));
    MOCK_METHOD5(DecodeInternal, int(const uint8_t*, size_t, int, int16_t*,
                                     SpeechType*));
    int SampleRateHz() const /* override */ { return kSampleRateHz; }
    size_t Channels() const /* override */ { return kChannels; }
  } decoder_;

  const uint8_t kFirstPayloadValue = 1;
  const uint8_t kSecondPayloadValue = 2;

  EXPECT_CALL(decoder_, PacketDuration(Pointee(kFirstPayloadValue),
                                       kPayloadLengthBytes))
    .Times(AtLeast(1))
    .WillRepeatedly(Return(kNetEqMaxFrameSize + 1));

  EXPECT_CALL(decoder_,
              DecodeInternal(Pointee(kFirstPayloadValue), _, _, _, _))
      .Times(0);

  EXPECT_CALL(decoder_, DecodeInternal(Pointee(kSecondPayloadValue),
                                       kPayloadLengthBytes,
                                       kSampleRateHz, _, _))
      .Times(1)
      .WillOnce(DoAll(SetArrayArgument<3>(dummy_output,
                                          dummy_output +
                                          kPayloadLengthSamples * kChannels),
                      SetArgPointee<4>(AudioDecoder::kSpeech),
                      Return(static_cast<int>(
                          kPayloadLengthSamples * kChannels))));

  EXPECT_CALL(decoder_, PacketDuration(Pointee(kSecondPayloadValue),
                                       kPayloadLengthBytes))
    .Times(AtLeast(1))
    .WillRepeatedly(Return(kNetEqMaxFrameSize));

  EXPECT_EQ(NetEq::kOK, neteq_->RegisterExternalDecoder(
                            &decoder_, NetEqDecoder::kDecoderPCM16B,
                            "dummy name", kPayloadType));

  // Insert one packet.
  payload[0] = kFirstPayloadValue;  // This will make Decode() fail.
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));

  // Insert another packet.
  payload[0] = kSecondPayloadValue;  // This will make Decode() successful.
  rtp_header.header.sequenceNumber++;
  // The second timestamp needs to be at least 30 ms after the first to make
  // the second packet get decoded.
  rtp_header.header.timestamp += 3 * kPayloadLengthSamples;
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));

  AudioFrame output;
  bool muted;
  // First call to GetAudio will try to decode the "faulty" packet.
  // Expect kFail return value...
  EXPECT_EQ(NetEq::kFail, neteq_->GetAudio(&output, &muted));
  // ... and kOtherDecoderError error code.
  EXPECT_EQ(NetEq::kOtherDecoderError, neteq_->LastError());
  // Output size and number of channels should be correct.
  const size_t kExpectedOutputSize = 10 * (kSampleRateHz / 1000) * kChannels;
  EXPECT_EQ(kExpectedOutputSize, output.samples_per_channel_ * kChannels);
  EXPECT_EQ(kChannels, output.num_channels_);

  // Second call to GetAudio will decode the packet that is ok. No errors are
  // expected.
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(kExpectedOutputSize, output.samples_per_channel_ * kChannels);
  EXPECT_EQ(kChannels, output.num_channels_);
}

// This test inserts packets until the buffer is flushed. After that, it asks
// NetEq for the network statistics. The purpose of the test is to make sure
// that even though the buffer size increment is negative (which it becomes when
// the packet causing a flush is inserted), the packet length stored in the
// decision logic remains valid.
TEST_F(NetEqImplTest, FloodBufferAndGetNetworkStats) {
  UseNoMocks();
  CreateInstance();

  const size_t kPayloadLengthSamples = 80;
  const size_t kPayloadLengthBytes = 2 * kPayloadLengthSamples;  // PCM 16-bit.
  const uint8_t kPayloadType = 17;   // Just an arbitrary number.
  const uint32_t kReceiveTime = 17;  // Value doesn't matter for this test.
  uint8_t payload[kPayloadLengthBytes] = {0};
  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = 0x1234;
  rtp_header.header.timestamp = 0x12345678;
  rtp_header.header.ssrc = 0x87654321;

  EXPECT_EQ(NetEq::kOK, neteq_->RegisterPayloadType(
                            NetEqDecoder::kDecoderPCM16B, "", kPayloadType));

  // Insert packets until the buffer flushes.
  for (size_t i = 0; i <= config_.max_packets_in_buffer; ++i) {
    EXPECT_EQ(i, packet_buffer_->NumPacketsInBuffer());
    EXPECT_EQ(NetEq::kOK,
              neteq_->InsertPacket(rtp_header, payload, kReceiveTime));
    rtp_header.header.timestamp +=
        rtc::checked_cast<uint32_t>(kPayloadLengthSamples);
    ++rtp_header.header.sequenceNumber;
  }
  EXPECT_EQ(1u, packet_buffer_->NumPacketsInBuffer());

  // Ask for network statistics. This should not crash.
  NetEqNetworkStatistics stats;
  EXPECT_EQ(NetEq::kOK, neteq_->NetworkStatistics(&stats));
}

TEST_F(NetEqImplTest, DecodedPayloadTooShort) {
  UseNoMocks();
  CreateInstance();

  const uint8_t kPayloadType = 17;   // Just an arbitrary number.
  const uint32_t kReceiveTime = 17;  // Value doesn't matter for this test.
  const int kSampleRateHz = 8000;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = 2 * kPayloadLengthSamples;
  uint8_t payload[kPayloadLengthBytes] = {0};
  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = 0x1234;
  rtp_header.header.timestamp = 0x12345678;
  rtp_header.header.ssrc = 0x87654321;

  // Create a mock decoder object.
  MockAudioDecoder mock_decoder;
  EXPECT_CALL(mock_decoder, Reset()).WillRepeatedly(Return());
  EXPECT_CALL(mock_decoder, SampleRateHz())
      .WillRepeatedly(Return(kSampleRateHz));
  EXPECT_CALL(mock_decoder, Channels()).WillRepeatedly(Return(1));
  EXPECT_CALL(mock_decoder, IncomingPacket(_, kPayloadLengthBytes, _, _, _))
      .WillRepeatedly(Return(0));
  EXPECT_CALL(mock_decoder, PacketDuration(_, _))
      .WillRepeatedly(Return(kPayloadLengthSamples));
  int16_t dummy_output[kPayloadLengthSamples] = {0};
  // The below expectation will make the mock decoder write
  // |kPayloadLengthSamples| - 5 zeros to the output array, and mark it as
  // speech. That is, the decoded length is 5 samples shorter than the expected.
  EXPECT_CALL(mock_decoder,
              DecodeInternal(_, kPayloadLengthBytes, kSampleRateHz, _, _))
      .WillOnce(
          DoAll(SetArrayArgument<3>(dummy_output,
                                    dummy_output + kPayloadLengthSamples - 5),
                SetArgPointee<4>(AudioDecoder::kSpeech),
                Return(kPayloadLengthSamples - 5)));
  EXPECT_EQ(NetEq::kOK, neteq_->RegisterExternalDecoder(
                            &mock_decoder, NetEqDecoder::kDecoderPCM16B,
                            "dummy name", kPayloadType));

  // Insert one packet.
  EXPECT_EQ(NetEq::kOK,
            neteq_->InsertPacket(rtp_header, payload, kReceiveTime));

  EXPECT_EQ(5u, neteq_->sync_buffer_for_test()->FutureLength());

  // Pull audio once.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

  EXPECT_CALL(mock_decoder, Die());
}

// This test checks the behavior of NetEq when audio decoder fails.
TEST_F(NetEqImplTest, DecodingError) {
  UseNoMocks();
  CreateInstance();

  const uint8_t kPayloadType = 17;   // Just an arbitrary number.
  const uint32_t kReceiveTime = 17;  // Value doesn't matter for this test.
  const int kSampleRateHz = 8000;
  const int kDecoderErrorCode = -97;  // Any negative number.

  // We let decoder return 5 ms each time, and therefore, 2 packets make 10 ms.
  const size_t kFrameLengthSamples =
      static_cast<size_t>(5 * kSampleRateHz / 1000);

  const size_t kPayloadLengthBytes = 1;  // This can be arbitrary.

  uint8_t payload[kPayloadLengthBytes] = {0};

  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = 0x1234;
  rtp_header.header.timestamp = 0x12345678;
  rtp_header.header.ssrc = 0x87654321;

  // Create a mock decoder object.
  MockAudioDecoder mock_decoder;
  EXPECT_CALL(mock_decoder, Reset()).WillRepeatedly(Return());
  EXPECT_CALL(mock_decoder, SampleRateHz())
      .WillRepeatedly(Return(kSampleRateHz));
  EXPECT_CALL(mock_decoder, Channels()).WillRepeatedly(Return(1));
  EXPECT_CALL(mock_decoder, IncomingPacket(_, kPayloadLengthBytes, _, _, _))
      .WillRepeatedly(Return(0));
  EXPECT_CALL(mock_decoder, PacketDuration(_, _))
      .WillRepeatedly(Return(kFrameLengthSamples));
  EXPECT_CALL(mock_decoder, ErrorCode())
      .WillOnce(Return(kDecoderErrorCode));
  EXPECT_CALL(mock_decoder, HasDecodePlc())
      .WillOnce(Return(false));
  int16_t dummy_output[kFrameLengthSamples] = {0};

  {
    InSequence sequence;  // Dummy variable.
    // Mock decoder works normally the first time.
    EXPECT_CALL(mock_decoder,
                DecodeInternal(_, kPayloadLengthBytes, kSampleRateHz, _, _))
        .Times(3)
        .WillRepeatedly(
            DoAll(SetArrayArgument<3>(dummy_output,
                                      dummy_output + kFrameLengthSamples),
                  SetArgPointee<4>(AudioDecoder::kSpeech),
                  Return(kFrameLengthSamples)))
        .RetiresOnSaturation();

    // Then mock decoder fails. A common reason for failure can be buffer being
    // too short
    EXPECT_CALL(mock_decoder,
                DecodeInternal(_, kPayloadLengthBytes, kSampleRateHz, _, _))
        .WillOnce(Return(-1))
        .RetiresOnSaturation();

    // Mock decoder finally returns to normal.
    EXPECT_CALL(mock_decoder,
                DecodeInternal(_, kPayloadLengthBytes, kSampleRateHz, _, _))
        .Times(2)
        .WillRepeatedly(
            DoAll(SetArrayArgument<3>(dummy_output,
                                      dummy_output + kFrameLengthSamples),
                  SetArgPointee<4>(AudioDecoder::kSpeech),
                  Return(kFrameLengthSamples)));
  }

  EXPECT_EQ(NetEq::kOK, neteq_->RegisterExternalDecoder(
                            &mock_decoder, NetEqDecoder::kDecoderPCM16B,
                            "dummy name", kPayloadType));

  // Insert packets.
  for (int i = 0; i < 6; ++i) {
    rtp_header.header.sequenceNumber += 1;
    rtp_header.header.timestamp += kFrameLengthSamples;
    EXPECT_EQ(NetEq::kOK,
              neteq_->InsertPacket(rtp_header, payload, kReceiveTime));
  }

  // Pull audio.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

  // Pull audio again. Decoder fails.
  EXPECT_EQ(NetEq::kFail, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(NetEq::kDecoderErrorCode, neteq_->LastError());
  EXPECT_EQ(kDecoderErrorCode, neteq_->LastDecoderError());
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  // We are not expecting anything for output.speech_type_, since an error was
  // returned.

  // Pull audio again, should continue an expansion.
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kPLC, output.speech_type_);

  // Pull audio again, should behave normal.
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

  EXPECT_CALL(mock_decoder, Die());
}

// This test checks the behavior of NetEq when audio decoder fails during CNG.
TEST_F(NetEqImplTest, DecodingErrorDuringInternalCng) {
  UseNoMocks();
  CreateInstance();

  const uint8_t kPayloadType = 17;   // Just an arbitrary number.
  const uint32_t kReceiveTime = 17;  // Value doesn't matter for this test.
  const int kSampleRateHz = 8000;
  const int kDecoderErrorCode = -97;  // Any negative number.

  // We let decoder return 5 ms each time, and therefore, 2 packets make 10 ms.
  const size_t kFrameLengthSamples =
      static_cast<size_t>(5 * kSampleRateHz / 1000);

  const size_t kPayloadLengthBytes = 1;  // This can be arbitrary.

  uint8_t payload[kPayloadLengthBytes] = {0};

  WebRtcRTPHeader rtp_header;
  rtp_header.header.payloadType = kPayloadType;
  rtp_header.header.sequenceNumber = 0x1234;
  rtp_header.header.timestamp = 0x12345678;
  rtp_header.header.ssrc = 0x87654321;

  // Create a mock decoder object.
  MockAudioDecoder mock_decoder;
  EXPECT_CALL(mock_decoder, Reset()).WillRepeatedly(Return());
  EXPECT_CALL(mock_decoder, SampleRateHz())
      .WillRepeatedly(Return(kSampleRateHz));
  EXPECT_CALL(mock_decoder, Channels()).WillRepeatedly(Return(1));
  EXPECT_CALL(mock_decoder, IncomingPacket(_, kPayloadLengthBytes, _, _, _))
      .WillRepeatedly(Return(0));
  EXPECT_CALL(mock_decoder, PacketDuration(_, _))
      .WillRepeatedly(Return(kFrameLengthSamples));
  EXPECT_CALL(mock_decoder, ErrorCode())
      .WillOnce(Return(kDecoderErrorCode));
  int16_t dummy_output[kFrameLengthSamples] = {0};

  {
    InSequence sequence;  // Dummy variable.
    // Mock decoder works normally the first 2 times.
    EXPECT_CALL(mock_decoder,
                DecodeInternal(_, kPayloadLengthBytes, kSampleRateHz, _, _))
        .Times(2)
        .WillRepeatedly(
            DoAll(SetArrayArgument<3>(dummy_output,
                                      dummy_output + kFrameLengthSamples),
                  SetArgPointee<4>(AudioDecoder::kComfortNoise),
                  Return(kFrameLengthSamples)))
        .RetiresOnSaturation();

    // Then mock decoder fails. A common reason for failure can be buffer being
    // too short
    EXPECT_CALL(mock_decoder, DecodeInternal(nullptr, 0, kSampleRateHz, _, _))
        .WillOnce(Return(-1))
        .RetiresOnSaturation();

    // Mock decoder finally returns to normal.
    EXPECT_CALL(mock_decoder, DecodeInternal(nullptr, 0, kSampleRateHz, _, _))
        .Times(2)
        .WillRepeatedly(
            DoAll(SetArrayArgument<3>(dummy_output,
                                      dummy_output + kFrameLengthSamples),
                  SetArgPointee<4>(AudioDecoder::kComfortNoise),
                  Return(kFrameLengthSamples)));
  }

  EXPECT_EQ(NetEq::kOK, neteq_->RegisterExternalDecoder(
                            &mock_decoder, NetEqDecoder::kDecoderPCM16B,
                            "dummy name", kPayloadType));

  // Insert 2 packets. This will make netEq into codec internal CNG mode.
  for (int i = 0; i < 2; ++i) {
    rtp_header.header.sequenceNumber += 1;
    rtp_header.header.timestamp += kFrameLengthSamples;
    EXPECT_EQ(NetEq::kOK,
              neteq_->InsertPacket(rtp_header, payload, kReceiveTime));
  }

  // Pull audio.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kCNG, output.speech_type_);

  // Pull audio again. Decoder fails.
  EXPECT_EQ(NetEq::kFail, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(NetEq::kDecoderErrorCode, neteq_->LastError());
  EXPECT_EQ(kDecoderErrorCode, neteq_->LastDecoderError());
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  // We are not expecting anything for output.speech_type_, since an error was
  // returned.

  // Pull audio again, should resume codec CNG.
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kCNG, output.speech_type_);

  EXPECT_CALL(mock_decoder, Die());
}

// Tests that the return value from last_output_sample_rate_hz() is equal to the
// configured inital sample rate.
TEST_F(NetEqImplTest, InitialLastOutputSampleRate) {
  UseNoMocks();
  config_.sample_rate_hz = 48000;
  CreateInstance();
  EXPECT_EQ(48000, neteq_->last_output_sample_rate_hz());
}

TEST_F(NetEqImplTest, TickTimerIncrement) {
  UseNoMocks();
  CreateInstance();
  ASSERT_TRUE(tick_timer_);
  EXPECT_EQ(0u, tick_timer_->ticks());
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(1u, tick_timer_->ticks());
}

class Decoder120ms : public AudioDecoder {
 public:
  Decoder120ms(int sample_rate_hz, SpeechType speech_type)
      : sample_rate_hz_(sample_rate_hz),
        next_value_(1),
        speech_type_(speech_type) {}

  int DecodeInternal(const uint8_t* encoded,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override {
    EXPECT_EQ(sample_rate_hz_, sample_rate_hz);
    size_t decoded_len =
        rtc::CheckedDivExact(sample_rate_hz, 1000) * 120 * Channels();
    for (size_t i = 0; i < decoded_len; ++i) {
      decoded[i] = next_value_++;
    }
    *speech_type = speech_type_;
    return decoded_len;
  }

  void Reset() override { next_value_ = 1; }
  int SampleRateHz() const override { return sample_rate_hz_; }
  size_t Channels() const override { return 2; }

 private:
  int sample_rate_hz_;
  int16_t next_value_;
  SpeechType speech_type_;
};

class NetEqImplTest120ms : public NetEqImplTest {
 protected:
  NetEqImplTest120ms() : NetEqImplTest() {}
  virtual ~NetEqImplTest120ms() {}

  void CreateInstanceNoMocks() {
    UseNoMocks();
    CreateInstance();
  }

  void CreateInstanceWithDelayManagerMock() {
    UseNoMocks();
    use_mock_delay_manager_ = true;
    CreateInstance();
  }

  uint32_t timestamp_diff_between_packets() const {
    return rtc::CheckedDivExact(kSamplingFreq_, 1000u) * 120;
  }

  uint32_t first_timestamp() const { return 10u; }

  void GetFirstPacket() {
    bool muted;
    for (int i = 0; i < 12; i++) {
      EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
      EXPECT_FALSE(muted);
    }
  }

  void InsertPacket(uint32_t timestamp) {
    WebRtcRTPHeader rtp_header;
    rtp_header.header.payloadType = kPayloadType;
    rtp_header.header.sequenceNumber = sequence_number_;
    rtp_header.header.timestamp = timestamp;
    rtp_header.header.ssrc = 15;
    const size_t kPayloadLengthBytes = 1;  // This can be arbitrary.
    uint8_t payload[kPayloadLengthBytes] = {0};
    EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload, 10));
    sequence_number_++;
  }

  void Register120msCodec(AudioDecoder::SpeechType speech_type) {
    decoder_.reset(new Decoder120ms(kSamplingFreq_, speech_type));
    ASSERT_EQ(2u, decoder_->Channels());
    EXPECT_EQ(NetEq::kOK, neteq_->RegisterExternalDecoder(
                              decoder_.get(), NetEqDecoder::kDecoderOpus_2ch,
                              "120ms codec", kPayloadType));
  }

  std::unique_ptr<Decoder120ms> decoder_;
  AudioFrame output_;
  const uint32_t kPayloadType = 17;
  const uint32_t kSamplingFreq_ = 48000;
  uint16_t sequence_number_ = 1;
};

TEST_F(NetEqImplTest120ms, AudioRepetition) {
  config_.playout_mode = kPlayoutFax;
  CreateInstanceNoMocks();
  Register120msCodec(AudioDecoder::kSpeech);

  InsertPacket(first_timestamp());
  GetFirstPacket();

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(kAudioRepetition, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, AlternativePlc) {
  config_.playout_mode = kPlayoutOff;
  CreateInstanceNoMocks();
  Register120msCodec(AudioDecoder::kSpeech);

  InsertPacket(first_timestamp());
  GetFirstPacket();

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(kAlternativePlc, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, CodecInternalCng) {
  CreateInstanceNoMocks();
  Register120msCodec(AudioDecoder::kComfortNoise);

  InsertPacket(first_timestamp());
  GetFirstPacket();

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(kCodecInternalCng, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, Normal) {
  CreateInstanceNoMocks();
  Register120msCodec(AudioDecoder::kSpeech);

  InsertPacket(first_timestamp());
  GetFirstPacket();

  EXPECT_EQ(kNormal, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, Merge) {
  CreateInstanceWithDelayManagerMock();

  Register120msCodec(AudioDecoder::kSpeech);
  InsertPacket(first_timestamp());

  GetFirstPacket();
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));

  InsertPacket(first_timestamp() + 2 * timestamp_diff_between_packets());

  // Delay manager reports a target level which should cause a Merge.
  EXPECT_CALL(*mock_delay_manager_, TargetLevel()).WillOnce(Return(-10));

  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(kMerge, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, Expand) {
  CreateInstanceNoMocks();
  Register120msCodec(AudioDecoder::kSpeech);

  InsertPacket(first_timestamp());
  GetFirstPacket();

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(kExpand, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, FastAccelerate) {
  CreateInstanceWithDelayManagerMock();
  Register120msCodec(AudioDecoder::kSpeech);

  InsertPacket(first_timestamp());
  GetFirstPacket();
  InsertPacket(first_timestamp() + timestamp_diff_between_packets());

  // Delay manager report buffer limit which should cause a FastAccelerate.
  EXPECT_CALL(*mock_delay_manager_, BufferLimits(_, _))
      .Times(1)
      .WillOnce(DoAll(SetArgPointee<0>(0), SetArgPointee<1>(0)));

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(kFastAccelerate, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, PreemptiveExpand) {
  CreateInstanceWithDelayManagerMock();
  Register120msCodec(AudioDecoder::kSpeech);

  InsertPacket(first_timestamp());
  GetFirstPacket();

  InsertPacket(first_timestamp() + timestamp_diff_between_packets());

  // Delay manager report buffer limit which should cause a PreemptiveExpand.
  EXPECT_CALL(*mock_delay_manager_, BufferLimits(_, _))
      .Times(1)
      .WillOnce(DoAll(SetArgPointee<0>(100), SetArgPointee<1>(100)));

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(kPreemptiveExpand, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, Accelerate) {
  CreateInstanceWithDelayManagerMock();
  Register120msCodec(AudioDecoder::kSpeech);

  InsertPacket(first_timestamp());
  GetFirstPacket();

  InsertPacket(first_timestamp() + timestamp_diff_between_packets());

  // Delay manager report buffer limit which should cause a Accelerate.
  EXPECT_CALL(*mock_delay_manager_, BufferLimits(_, _))
      .Times(1)
      .WillOnce(DoAll(SetArgPointee<0>(1), SetArgPointee<1>(2)));

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(kAccelerate, neteq_->last_operation_for_test());
}

}// namespace webrtc
