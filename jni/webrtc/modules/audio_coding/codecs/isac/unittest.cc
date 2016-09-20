/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <algorithm>
#include <numeric>
#include <sstream>
#include <vector>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/buffer.h"
#include "webrtc/modules/audio_coding/codecs/isac/fix/include/audio_encoder_isacfix.h"
#include "webrtc/modules/audio_coding/codecs/isac/main/include/audio_encoder_isac.h"
#include "webrtc/modules/audio_coding/neteq/tools/input_audio_file.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

namespace {

const int kIsacNumberOfSamples = 32 * 60;  // 60 ms at 32 kHz

std::vector<int16_t> LoadSpeechData() {
  webrtc::test::InputAudioFile input_file(
      webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm"));
  std::vector<int16_t> speech_data(kIsacNumberOfSamples);
  input_file.Read(kIsacNumberOfSamples, speech_data.data());
  return speech_data;
}

template <typename T>
IsacBandwidthInfo GetBwInfo(typename T::instance_type* inst) {
  IsacBandwidthInfo bi;
  T::GetBandwidthInfo(inst, &bi);
  EXPECT_TRUE(bi.in_use);
  return bi;
}

// Encodes one packet. Returns the packet duration in milliseconds.
template <typename T>
int EncodePacket(typename T::instance_type* inst,
                 const IsacBandwidthInfo* bi,
                 const int16_t* speech_data,
                 rtc::Buffer* output) {
  output->SetSize(1000);
  for (int duration_ms = 10;; duration_ms += 10) {
    if (bi)
      T::SetBandwidthInfo(inst, bi);
    int encoded_bytes = T::Encode(inst, speech_data, output->data());
    if (encoded_bytes > 0 || duration_ms >= 60) {
      EXPECT_GT(encoded_bytes, 0);
      EXPECT_LE(static_cast<size_t>(encoded_bytes), output->size());
      output->SetSize(encoded_bytes);
      return duration_ms;
    }
  }
}

template <typename T>
std::vector<int16_t> DecodePacket(typename T::instance_type* inst,
                                  const rtc::Buffer& encoded) {
  std::vector<int16_t> decoded(kIsacNumberOfSamples);
  int16_t speech_type;
  int nsamples = T::DecodeInternal(inst, encoded.data(), encoded.size(),
                                   &decoded.front(), &speech_type);
  EXPECT_GT(nsamples, 0);
  EXPECT_LE(static_cast<size_t>(nsamples), decoded.size());
  decoded.resize(nsamples);
  return decoded;
}

class BoundedCapacityChannel final {
 public:
  BoundedCapacityChannel(int sample_rate_hz, int rate_bits_per_second)
      : current_time_rtp_(0),
        channel_rate_bytes_per_sample_(rate_bits_per_second /
                                       (8.0 * sample_rate_hz)) {}

  // Simulate sending the given number of bytes at the given RTP time. Returns
  // the new current RTP time after the sending is done.
  int Send(int send_time_rtp, int nbytes) {
    current_time_rtp_ = std::max(current_time_rtp_, send_time_rtp) +
                        nbytes / channel_rate_bytes_per_sample_;
    return current_time_rtp_;
  }

 private:
  int current_time_rtp_;
  // The somewhat strange unit for channel rate, bytes per sample, is because
  // RTP time is measured in samples:
  const double channel_rate_bytes_per_sample_;
};

// Test that the iSAC encoder produces identical output whether or not we use a
// conjoined encoder+decoder pair or a separate encoder and decoder that
// communicate BW estimation info explicitly.
template <typename T, bool adaptive>
void TestGetSetBandwidthInfo(const int16_t* speech_data,
                             int rate_bits_per_second,
                             int sample_rate_hz,
                             int frame_size_ms) {
  const int bit_rate = 32000;

  // Conjoined encoder/decoder pair:
  typename T::instance_type* encdec;
  ASSERT_EQ(0, T::Create(&encdec));
  ASSERT_EQ(0, T::EncoderInit(encdec, adaptive ? 0 : 1));
  T::DecoderInit(encdec);
  ASSERT_EQ(0, T::SetEncSampRate(encdec, sample_rate_hz));
  if (adaptive)
    ASSERT_EQ(0, T::ControlBwe(encdec, bit_rate, frame_size_ms, false));
  else
    ASSERT_EQ(0, T::Control(encdec, bit_rate, frame_size_ms));

  // Disjoint encoder/decoder pair:
  typename T::instance_type* enc;
  ASSERT_EQ(0, T::Create(&enc));
  ASSERT_EQ(0, T::EncoderInit(enc, adaptive ? 0 : 1));
  ASSERT_EQ(0, T::SetEncSampRate(enc, sample_rate_hz));
  if (adaptive)
    ASSERT_EQ(0, T::ControlBwe(enc, bit_rate, frame_size_ms, false));
  else
    ASSERT_EQ(0, T::Control(enc, bit_rate, frame_size_ms));
  typename T::instance_type* dec;
  ASSERT_EQ(0, T::Create(&dec));
  T::DecoderInit(dec);
  T::SetInitialBweBottleneck(dec, bit_rate);
  T::SetEncSampRateInDecoder(dec, sample_rate_hz);

  // 0. Get initial BW info from decoder.
  auto bi = GetBwInfo<T>(dec);

  BoundedCapacityChannel channel1(sample_rate_hz, rate_bits_per_second),
      channel2(sample_rate_hz, rate_bits_per_second);

  int elapsed_time_ms = 0;
  for (int i = 0; elapsed_time_ms < 10000; ++i) {
    std::ostringstream ss;
    ss << " i = " << i;
    SCOPED_TRACE(ss.str());

    // 1. Encode 3 * 10 ms or 6 * 10 ms. The separate encoder is given the BW
    // info before each encode call.
    rtc::Buffer bitstream1, bitstream2;
    int duration1_ms =
        EncodePacket<T>(encdec, nullptr, speech_data, &bitstream1);
    int duration2_ms = EncodePacket<T>(enc, &bi, speech_data, &bitstream2);
    EXPECT_EQ(duration1_ms, duration2_ms);
    if (adaptive)
      EXPECT_TRUE(duration1_ms == 30 || duration1_ms == 60);
    else
      EXPECT_EQ(frame_size_ms, duration1_ms);
    ASSERT_EQ(bitstream1.size(), bitstream2.size());
    EXPECT_EQ(bitstream1, bitstream2);

    // 2. Deliver the encoded data to the decoders.
    const int send_time = elapsed_time_ms * (sample_rate_hz / 1000);
    EXPECT_EQ(0, T::UpdateBwEstimate(
                     encdec, bitstream1.data(), bitstream1.size(), i, send_time,
                     channel1.Send(send_time, bitstream1.size())));
    EXPECT_EQ(0, T::UpdateBwEstimate(
                     dec, bitstream2.data(), bitstream2.size(), i, send_time,
                     channel2.Send(send_time, bitstream2.size())));

    // 3. Decode, and get new BW info from the separate decoder.
    ASSERT_EQ(0, T::SetDecSampRate(encdec, sample_rate_hz));
    ASSERT_EQ(0, T::SetDecSampRate(dec, sample_rate_hz));
    auto decoded1 = DecodePacket<T>(encdec, bitstream1);
    auto decoded2 = DecodePacket<T>(dec, bitstream2);
    EXPECT_EQ(decoded1, decoded2);
    bi = GetBwInfo<T>(dec);

    elapsed_time_ms += duration1_ms;
  }

  EXPECT_EQ(0, T::Free(encdec));
  EXPECT_EQ(0, T::Free(enc));
  EXPECT_EQ(0, T::Free(dec));
}

enum class IsacType { Fix, Float };

std::ostream& operator<<(std::ostream& os, IsacType t) {
  os << (t == IsacType::Fix ? "fix" : "float");
  return os;
}

struct IsacTestParam {
  IsacType isac_type;
  bool adaptive;
  int channel_rate_bits_per_second;
  int sample_rate_hz;
  int frame_size_ms;

  friend std::ostream& operator<<(std::ostream& os, const IsacTestParam& itp) {
    os << '{' << itp.isac_type << ','
       << (itp.adaptive ? "adaptive" : "nonadaptive") << ','
       << itp.channel_rate_bits_per_second << ',' << itp.sample_rate_hz << ','
       << itp.frame_size_ms << '}';
    return os;
  }
};

class IsacCommonTest : public testing::TestWithParam<IsacTestParam> {};

}  // namespace

TEST_P(IsacCommonTest, GetSetBandwidthInfo) {
  auto p = GetParam();
  auto test_fun = [p] {
    if (p.isac_type == IsacType::Fix) {
      if (p.adaptive)
        return TestGetSetBandwidthInfo<IsacFix, true>;
      else
        return TestGetSetBandwidthInfo<IsacFix, false>;
    } else {
      if (p.adaptive)
        return TestGetSetBandwidthInfo<IsacFloat, true>;
      else
        return TestGetSetBandwidthInfo<IsacFloat, false>;
    }
  }();
  test_fun(LoadSpeechData().data(), p.channel_rate_bits_per_second,
           p.sample_rate_hz, p.frame_size_ms);
}

std::vector<IsacTestParam> TestCases() {
  static const IsacType types[] = {IsacType::Fix, IsacType::Float};
  static const bool adaptives[] = {true, false};
  static const int channel_rates[] = {12000, 15000, 19000, 22000};
  static const int sample_rates[] = {16000, 32000};
  static const int frame_sizes[] = {30, 60};
  std::vector<IsacTestParam> cases;
  for (IsacType type : types)
    for (bool adaptive : adaptives)
      for (int channel_rate : channel_rates)
        for (int sample_rate : sample_rates)
          if (!(type == IsacType::Fix && sample_rate == 32000))
            for (int frame_size : frame_sizes)
              if (!(sample_rate == 32000 && frame_size == 60))
                cases.push_back(
                    {type, adaptive, channel_rate, sample_rate, frame_size});
  return cases;
}

INSTANTIATE_TEST_CASE_P(, IsacCommonTest, testing::ValuesIn(TestCases()));

}  // namespace webrtc
