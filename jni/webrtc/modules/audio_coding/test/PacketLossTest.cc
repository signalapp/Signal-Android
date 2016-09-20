/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/test/PacketLossTest.h"

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

ReceiverWithPacketLoss::ReceiverWithPacketLoss()
    : loss_rate_(0),
      burst_length_(1),
      packet_counter_(0),
      lost_packet_counter_(0),
      burst_lost_counter_(burst_length_) {
}

void ReceiverWithPacketLoss::Setup(AudioCodingModule *acm,
                                   RTPStream *rtpStream,
                                   std::string out_file_name,
                                   int channels,
                                   int loss_rate,
                                   int burst_length) {
  loss_rate_ = loss_rate;
  burst_length_ = burst_length;
  burst_lost_counter_ = burst_length_;  // To prevent first packet gets lost.
  std::stringstream ss;
  ss << out_file_name << "_" << loss_rate_ << "_" << burst_length_ << "_";
  Receiver::Setup(acm, rtpStream, ss.str(), channels);
}

bool ReceiverWithPacketLoss::IncomingPacket() {
  if (!_rtpStream->EndOfFile()) {
    if (packet_counter_ == 0) {
      _realPayloadSizeBytes = _rtpStream->Read(&_rtpInfo, _incomingPayload,
                                               _payloadSizeBytes, &_nextTime);
      if (_realPayloadSizeBytes == 0) {
        if (_rtpStream->EndOfFile()) {
          packet_counter_ = 0;
          return true;
        } else {
          return false;
        }
      }
    }

    if (!PacketLost()) {
      _acm->IncomingPacket(_incomingPayload, _realPayloadSizeBytes, _rtpInfo);
    }
    packet_counter_++;
    _realPayloadSizeBytes = _rtpStream->Read(&_rtpInfo, _incomingPayload,
                                             _payloadSizeBytes, &_nextTime);
    if (_realPayloadSizeBytes == 0 && _rtpStream->EndOfFile()) {
      packet_counter_ = 0;
      lost_packet_counter_ = 0;
    }
  }
  return true;
}

bool ReceiverWithPacketLoss::PacketLost() {
  if (burst_lost_counter_ < burst_length_) {
    lost_packet_counter_++;
    burst_lost_counter_++;
    return true;
  }

  if (lost_packet_counter_ * 100 < loss_rate_ * packet_counter_) {
    lost_packet_counter_++;
    burst_lost_counter_ = 1;
    return true;
  }
  return false;
}

SenderWithFEC::SenderWithFEC()
    : expected_loss_rate_(0) {
}

void SenderWithFEC::Setup(AudioCodingModule *acm, RTPStream *rtpStream,
                          std::string in_file_name, int sample_rate,
                          int channels, int expected_loss_rate) {
  Sender::Setup(acm, rtpStream, in_file_name, sample_rate, channels);
  EXPECT_TRUE(SetFEC(true));
  EXPECT_TRUE(SetPacketLossRate(expected_loss_rate));
}

bool SenderWithFEC::SetFEC(bool enable_fec) {
  if (_acm->SetCodecFEC(enable_fec) == 0) {
    return true;
  }
  return false;
}

bool SenderWithFEC::SetPacketLossRate(int expected_loss_rate) {
  if (_acm->SetPacketLossRate(expected_loss_rate) == 0) {
    expected_loss_rate_ = expected_loss_rate;
    return true;
  }
  return false;
}

PacketLossTest::PacketLossTest(int channels, int expected_loss_rate,
                               int actual_loss_rate, int burst_length)
    : channels_(channels),
      in_file_name_(channels_ == 1 ? "audio_coding/testfile32kHz" :
                    "audio_coding/teststereo32kHz"),
      sample_rate_hz_(32000),
      sender_(new SenderWithFEC),
      receiver_(new ReceiverWithPacketLoss),
      expected_loss_rate_(expected_loss_rate),
      actual_loss_rate_(actual_loss_rate),
      burst_length_(burst_length) {
}

void PacketLossTest::Perform() {
#ifndef WEBRTC_CODEC_OPUS
  return;
#else
  std::unique_ptr<AudioCodingModule> acm(AudioCodingModule::Create(0));

  int codec_id = acm->Codec("opus", 48000, channels_);

  RTPFile rtpFile;
  std::string fileName = webrtc::test::TempFilename(webrtc::test::OutputPath(),
                                                    "packet_loss_test");

  // Encode to file
  rtpFile.Open(fileName.c_str(), "wb+");
  rtpFile.WriteHeader();

  sender_->testMode = 0;
  sender_->codeId = codec_id;

  sender_->Setup(acm.get(), &rtpFile, in_file_name_, sample_rate_hz_, channels_,
                 expected_loss_rate_);
  if (acm->SendCodec()) {
    sender_->Run();
  }
  sender_->Teardown();
  rtpFile.Close();

  // Decode to file
  rtpFile.Open(fileName.c_str(), "rb");
  rtpFile.ReadHeader();

  receiver_->testMode = 0;
  receiver_->codeId = codec_id;

  receiver_->Setup(acm.get(), &rtpFile, "packetLoss_out", channels_,
                   actual_loss_rate_, burst_length_);
  receiver_->Run();
  receiver_->Teardown();
  rtpFile.Close();
#endif
}

}  // namespace webrtc
