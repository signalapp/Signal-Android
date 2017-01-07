/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/acm2/acm_receive_test_oldapi.h"

#include <assert.h>
#include <stdio.h>

#include <memory>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/include/audio_coding_module.h"
#include "webrtc/modules/audio_coding/neteq/tools/audio_sink.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet_source.h"

namespace webrtc {
namespace test {

namespace {
// Returns true if the codec should be registered, otherwise false. Changes
// the number of channels for the Opus codec to always be 1.
bool ModifyAndUseThisCodec(CodecInst* codec_param) {
  if (STR_CASE_CMP(codec_param->plname, "CN") == 0 &&
      codec_param->plfreq == 48000)
    return false;  // Skip 48 kHz comfort noise.

  if (STR_CASE_CMP(codec_param->plname, "telephone-event") == 0)
    return false;  // Skip DTFM.

  return true;
}

// Remaps payload types from ACM's default to those used in the resource file
// neteq_universal_new.rtp. Returns true if the codec should be registered,
// otherwise false. The payload types are set as follows (all are mono codecs):
// PCMu = 0;
// PCMa = 8;
// Comfort noise 8 kHz = 13
// Comfort noise 16 kHz = 98
// Comfort noise 32 kHz = 99
// iLBC = 102
// iSAC wideband = 103
// iSAC super-wideband = 104
// AVT/DTMF = 106
// RED = 117
// PCM16b 8 kHz = 93
// PCM16b 16 kHz = 94
// PCM16b 32 kHz = 95
// G.722 = 94
bool RemapPltypeAndUseThisCodec(const char* plname,
                                int plfreq,
                                size_t channels,
                                int* pltype) {
  if (channels != 1)
    return false;  // Don't use non-mono codecs.

  // Re-map pltypes to those used in the NetEq test files.
  if (STR_CASE_CMP(plname, "PCMU") == 0 && plfreq == 8000) {
    *pltype = 0;
  } else if (STR_CASE_CMP(plname, "PCMA") == 0 && plfreq == 8000) {
    *pltype = 8;
  } else if (STR_CASE_CMP(plname, "CN") == 0 && plfreq == 8000) {
    *pltype = 13;
  } else if (STR_CASE_CMP(plname, "CN") == 0 && plfreq == 16000) {
    *pltype = 98;
  } else if (STR_CASE_CMP(plname, "CN") == 0 && plfreq == 32000) {
    *pltype = 99;
  } else if (STR_CASE_CMP(plname, "ILBC") == 0) {
    *pltype = 102;
  } else if (STR_CASE_CMP(plname, "ISAC") == 0 && plfreq == 16000) {
    *pltype = 103;
  } else if (STR_CASE_CMP(plname, "ISAC") == 0 && plfreq == 32000) {
    *pltype = 104;
  } else if (STR_CASE_CMP(plname, "telephone-event") == 0) {
    *pltype = 106;
  } else if (STR_CASE_CMP(plname, "red") == 0) {
    *pltype = 117;
  } else if (STR_CASE_CMP(plname, "L16") == 0 && plfreq == 8000) {
    *pltype = 93;
  } else if (STR_CASE_CMP(plname, "L16") == 0 && plfreq == 16000) {
    *pltype = 94;
  } else if (STR_CASE_CMP(plname, "L16") == 0 && plfreq == 32000) {
    *pltype = 95;
  } else if (STR_CASE_CMP(plname, "G722") == 0) {
    *pltype = 9;
  } else {
    // Don't use any other codecs.
    return false;
  }
  return true;
}
}  // namespace

AcmReceiveTestOldApi::AcmReceiveTestOldApi(
    PacketSource* packet_source,
    AudioSink* audio_sink,
    int output_freq_hz,
    NumOutputChannels exptected_output_channels)
    : clock_(0),
      acm_(webrtc::AudioCodingModule::Create(0, &clock_)),
      packet_source_(packet_source),
      audio_sink_(audio_sink),
      output_freq_hz_(output_freq_hz),
      exptected_output_channels_(exptected_output_channels) {
}

void AcmReceiveTestOldApi::RegisterDefaultCodecs() {
  CodecInst my_codec_param;
  for (int n = 0; n < acm_->NumberOfCodecs(); n++) {
    ASSERT_EQ(0, acm_->Codec(n, &my_codec_param)) << "Failed to get codec.";
    if (ModifyAndUseThisCodec(&my_codec_param)) {
      ASSERT_EQ(0, acm_->RegisterReceiveCodec(my_codec_param))
          << "Couldn't register receive codec.\n";
    }
  }
}

void AcmReceiveTestOldApi::RegisterNetEqTestCodecs() {
  CodecInst my_codec_param;
  for (int n = 0; n < acm_->NumberOfCodecs(); n++) {
    ASSERT_EQ(0, acm_->Codec(n, &my_codec_param)) << "Failed to get codec.";
    if (!ModifyAndUseThisCodec(&my_codec_param)) {
      // Skip this codec.
      continue;
    }

    if (RemapPltypeAndUseThisCodec(my_codec_param.plname,
                                   my_codec_param.plfreq,
                                   my_codec_param.channels,
                                   &my_codec_param.pltype)) {
      ASSERT_EQ(0, acm_->RegisterReceiveCodec(my_codec_param))
          << "Couldn't register receive codec.\n";
    }
  }
}

int AcmReceiveTestOldApi::RegisterExternalReceiveCodec(
    int rtp_payload_type,
    AudioDecoder* external_decoder,
    int sample_rate_hz,
    int num_channels,
    const std::string& name) {
  return acm_->RegisterExternalReceiveCodec(rtp_payload_type, external_decoder,
                                            sample_rate_hz, num_channels, name);
}

void AcmReceiveTestOldApi::Run() {
  for (std::unique_ptr<Packet> packet(packet_source_->NextPacket()); packet;
       packet = packet_source_->NextPacket()) {
    // Pull audio until time to insert packet.
    while (clock_.TimeInMilliseconds() < packet->time_ms()) {
      AudioFrame output_frame;
      bool muted;
      EXPECT_EQ(0,
                acm_->PlayoutData10Ms(output_freq_hz_, &output_frame, &muted));
      ASSERT_EQ(output_freq_hz_, output_frame.sample_rate_hz_);
      ASSERT_FALSE(muted);
      const size_t samples_per_block =
          static_cast<size_t>(output_freq_hz_ * 10 / 1000);
      EXPECT_EQ(samples_per_block, output_frame.samples_per_channel_);
      if (exptected_output_channels_ != kArbitraryChannels) {
        if (output_frame.speech_type_ == webrtc::AudioFrame::kPLC) {
          // Don't check number of channels for PLC output, since each test run
          // usually starts with a short period of mono PLC before decoding the
          // first packet.
        } else {
          EXPECT_EQ(exptected_output_channels_, output_frame.num_channels_);
        }
      }
      ASSERT_TRUE(audio_sink_->WriteAudioFrame(output_frame));
      clock_.AdvanceTimeMilliseconds(10);
      AfterGetAudio();
    }

    // Insert packet after converting from RTPHeader to WebRtcRTPHeader.
    WebRtcRTPHeader header;
    header.header = packet->header();
    header.frameType = kAudioFrameSpeech;
    memset(&header.type.Audio, 0, sizeof(RTPAudioHeader));
    EXPECT_EQ(0,
              acm_->IncomingPacket(
                  packet->payload(),
                  static_cast<int32_t>(packet->payload_length_bytes()),
                  header))
        << "Failure when inserting packet:" << std::endl
        << "  PT = " << static_cast<int>(header.header.payloadType) << std::endl
        << "  TS = " << header.header.timestamp << std::endl
        << "  SN = " << header.header.sequenceNumber;
  }
}

AcmReceiveTestToggleOutputFreqOldApi::AcmReceiveTestToggleOutputFreqOldApi(
    PacketSource* packet_source,
    AudioSink* audio_sink,
    int output_freq_hz_1,
    int output_freq_hz_2,
    int toggle_period_ms,
    NumOutputChannels exptected_output_channels)
    : AcmReceiveTestOldApi(packet_source,
                           audio_sink,
                           output_freq_hz_1,
                           exptected_output_channels),
      output_freq_hz_1_(output_freq_hz_1),
      output_freq_hz_2_(output_freq_hz_2),
      toggle_period_ms_(toggle_period_ms),
      last_toggle_time_ms_(clock_.TimeInMilliseconds()) {
}

void AcmReceiveTestToggleOutputFreqOldApi::AfterGetAudio() {
  if (clock_.TimeInMilliseconds() >= last_toggle_time_ms_ + toggle_period_ms_) {
    output_freq_hz_ = (output_freq_hz_ == output_freq_hz_1_)
                          ? output_freq_hz_2_
                          : output_freq_hz_1_;
    last_toggle_time_ms_ = clock_.TimeInMilliseconds();
  }
}

}  // namespace test
}  // namespace webrtc
