/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/include/neteq.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>  // memset

#include <algorithm>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "gflags/gflags.h"
#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/base/sha1digest.h"
#include "webrtc/base/stringencode.h"
#include "webrtc/modules/audio_coding/codecs/builtin_audio_decoder_factory.h"
#include "webrtc/modules/audio_coding/neteq/tools/audio_loop.h"
#include "webrtc/modules/audio_coding/neteq/tools/rtp_file_source.h"
#include "webrtc/modules/audio_coding/codecs/pcm16b/pcm16b.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/typedefs.h"

#ifdef WEBRTC_NETEQ_UNITTEST_BITEXACT
#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "external/webrtc/webrtc/modules/audio_coding/neteq/neteq_unittest.pb.h"
#else
#include "webrtc/audio_coding/neteq/neteq_unittest.pb.h"
#endif
#endif

DEFINE_bool(gen_ref, false, "Generate reference files.");

namespace {

const std::string& PlatformChecksum(const std::string& checksum_general,
                                    const std::string& checksum_android,
                                    const std::string& checksum_win_32,
                                    const std::string& checksum_win_64) {
#ifdef WEBRTC_ANDROID
    return checksum_android;
#elif WEBRTC_WIN
  #ifdef WEBRTC_ARCH_64_BITS
    return checksum_win_64;
  #else
    return checksum_win_32;
  #endif  // WEBRTC_ARCH_64_BITS
#else
  return checksum_general;
#endif  // WEBRTC_WIN
}

bool IsAllZero(const int16_t* buf, size_t buf_length) {
  bool all_zero = true;
  for (size_t n = 0; n < buf_length && all_zero; ++n)
    all_zero = buf[n] == 0;
  return all_zero;
}

bool IsAllNonZero(const int16_t* buf, size_t buf_length) {
  bool all_non_zero = true;
  for (size_t n = 0; n < buf_length && all_non_zero; ++n)
    all_non_zero = buf[n] != 0;
  return all_non_zero;
}

#ifdef WEBRTC_NETEQ_UNITTEST_BITEXACT
void Convert(const webrtc::NetEqNetworkStatistics& stats_raw,
             webrtc::neteq_unittest::NetEqNetworkStatistics* stats) {
  stats->set_current_buffer_size_ms(stats_raw.current_buffer_size_ms);
  stats->set_preferred_buffer_size_ms(stats_raw.preferred_buffer_size_ms);
  stats->set_jitter_peaks_found(stats_raw.jitter_peaks_found);
  stats->set_packet_loss_rate(stats_raw.packet_loss_rate);
  stats->set_packet_discard_rate(stats_raw.packet_discard_rate);
  stats->set_expand_rate(stats_raw.expand_rate);
  stats->set_speech_expand_rate(stats_raw.speech_expand_rate);
  stats->set_preemptive_rate(stats_raw.preemptive_rate);
  stats->set_accelerate_rate(stats_raw.accelerate_rate);
  stats->set_secondary_decoded_rate(stats_raw.secondary_decoded_rate);
  stats->set_clockdrift_ppm(stats_raw.clockdrift_ppm);
  stats->set_added_zero_samples(stats_raw.added_zero_samples);
  stats->set_mean_waiting_time_ms(stats_raw.mean_waiting_time_ms);
  stats->set_median_waiting_time_ms(stats_raw.median_waiting_time_ms);
  stats->set_min_waiting_time_ms(stats_raw.min_waiting_time_ms);
  stats->set_max_waiting_time_ms(stats_raw.max_waiting_time_ms);
}

void Convert(const webrtc::RtcpStatistics& stats_raw,
             webrtc::neteq_unittest::RtcpStatistics* stats) {
  stats->set_fraction_lost(stats_raw.fraction_lost);
  stats->set_cumulative_lost(stats_raw.cumulative_lost);
  stats->set_extended_max_sequence_number(
      stats_raw.extended_max_sequence_number);
  stats->set_jitter(stats_raw.jitter);
}

void AddMessage(FILE* file, rtc::MessageDigest* digest,
                const std::string& message) {
  int32_t size = message.length();
  if (file)
    ASSERT_EQ(1u, fwrite(&size, sizeof(size), 1, file));
  digest->Update(&size, sizeof(size));

  if (file)
    ASSERT_EQ(static_cast<size_t>(size),
              fwrite(message.data(), sizeof(char), size, file));
  digest->Update(message.data(), sizeof(char) * size);
}

#endif  // WEBRTC_NETEQ_UNITTEST_BITEXACT

void LoadDecoders(webrtc::NetEq* neteq) {
  // Load PCMu.
  ASSERT_EQ(0, neteq->RegisterPayloadType(webrtc::NetEqDecoder::kDecoderPCMu,
                                          "pcmu", 0));
  // Load PCMa.
  ASSERT_EQ(0, neteq->RegisterPayloadType(webrtc::NetEqDecoder::kDecoderPCMa,
                                          "pcma", 8));
#ifdef WEBRTC_CODEC_ILBC
  // Load iLBC.
  ASSERT_EQ(0, neteq->RegisterPayloadType(webrtc::NetEqDecoder::kDecoderILBC,
                                          "ilbc", 102));
#endif
#if defined(WEBRTC_CODEC_ISAC) || defined(WEBRTC_CODEC_ISACFX)
  // Load iSAC.
  ASSERT_EQ(0, neteq->RegisterPayloadType(webrtc::NetEqDecoder::kDecoderISAC,
                                          "isac", 103));
#endif
#ifdef WEBRTC_CODEC_ISAC
  // Load iSAC SWB.
  ASSERT_EQ(0, neteq->RegisterPayloadType(webrtc::NetEqDecoder::kDecoderISACswb,
                                          "isac-swb", 104));
#endif
#ifdef WEBRTC_CODEC_OPUS
  ASSERT_EQ(0, neteq->RegisterPayloadType(webrtc::NetEqDecoder::kDecoderOpus,
                                          "opus", 111));
#endif
  // Load PCM16B nb.
  ASSERT_EQ(0, neteq->RegisterPayloadType(webrtc::NetEqDecoder::kDecoderPCM16B,
                                          "pcm16-nb", 93));
  // Load PCM16B wb.
  ASSERT_EQ(0, neteq->RegisterPayloadType(
                   webrtc::NetEqDecoder::kDecoderPCM16Bwb, "pcm16-wb", 94));
  // Load PCM16B swb32.
  ASSERT_EQ(
      0, neteq->RegisterPayloadType(
             webrtc::NetEqDecoder::kDecoderPCM16Bswb32kHz, "pcm16-swb32", 95));
  // Load CNG 8 kHz.
  ASSERT_EQ(0, neteq->RegisterPayloadType(webrtc::NetEqDecoder::kDecoderCNGnb,
                                          "cng-nb", 13));
  // Load CNG 16 kHz.
  ASSERT_EQ(0, neteq->RegisterPayloadType(webrtc::NetEqDecoder::kDecoderCNGwb,
                                          "cng-wb", 98));
}
}  // namespace

namespace webrtc {

class ResultSink {
 public:
  explicit ResultSink(const std::string& output_file);
  ~ResultSink();

  template<typename T, size_t n> void AddResult(
      const T (&test_results)[n],
      size_t length);

  void AddResult(const NetEqNetworkStatistics& stats);
  void AddResult(const RtcpStatistics& stats);

  void VerifyChecksum(const std::string& ref_check_sum);

 private:
  FILE* output_fp_;
  std::unique_ptr<rtc::MessageDigest> digest_;
};

ResultSink::ResultSink(const std::string &output_file)
    : output_fp_(nullptr),
      digest_(new rtc::Sha1Digest()) {
  if (!output_file.empty()) {
    output_fp_ = fopen(output_file.c_str(), "wb");
    EXPECT_TRUE(output_fp_ != NULL);
  }
}

ResultSink::~ResultSink() {
  if (output_fp_)
    fclose(output_fp_);
}

template<typename T, size_t n>
void ResultSink::AddResult(const T (&test_results)[n], size_t length) {
  if (output_fp_) {
    ASSERT_EQ(length, fwrite(&test_results, sizeof(T), length, output_fp_));
  }
  digest_->Update(&test_results, sizeof(T) * length);
}

void ResultSink::AddResult(const NetEqNetworkStatistics& stats_raw) {
#ifdef WEBRTC_NETEQ_UNITTEST_BITEXACT
  neteq_unittest::NetEqNetworkStatistics stats;
  Convert(stats_raw, &stats);

  std::string stats_string;
  ASSERT_TRUE(stats.SerializeToString(&stats_string));
  AddMessage(output_fp_, digest_.get(), stats_string);
#else
  FAIL() << "Writing to reference file requires Proto Buffer.";
#endif  // WEBRTC_NETEQ_UNITTEST_BITEXACT
}

void ResultSink::AddResult(const RtcpStatistics& stats_raw) {
#ifdef WEBRTC_NETEQ_UNITTEST_BITEXACT
  neteq_unittest::RtcpStatistics stats;
  Convert(stats_raw, &stats);

  std::string stats_string;
  ASSERT_TRUE(stats.SerializeToString(&stats_string));
  AddMessage(output_fp_, digest_.get(), stats_string);
#else
  FAIL() << "Writing to reference file requires Proto Buffer.";
#endif  // WEBRTC_NETEQ_UNITTEST_BITEXACT
}

void ResultSink::VerifyChecksum(const std::string& checksum) {
  std::vector<char> buffer;
  buffer.resize(digest_->Size());
  digest_->Finish(&buffer[0], buffer.size());
  const std::string result = rtc::hex_encode(&buffer[0], digest_->Size());
  EXPECT_EQ(checksum, result);
}

class NetEqDecodingTest : public ::testing::Test {
 protected:
  // NetEQ must be polled for data once every 10 ms. Thus, neither of the
  // constants below can be changed.
  static const int kTimeStepMs = 10;
  static const size_t kBlockSize8kHz = kTimeStepMs * 8;
  static const size_t kBlockSize16kHz = kTimeStepMs * 16;
  static const size_t kBlockSize32kHz = kTimeStepMs * 32;
  static const size_t kBlockSize48kHz = kTimeStepMs * 48;
  static const int kInitSampleRateHz = 8000;

  NetEqDecodingTest();
  virtual void SetUp();
  virtual void TearDown();
  void SelectDecoders(NetEqDecoder* used_codec);
  void OpenInputFile(const std::string &rtp_file);
  void Process();

  void DecodeAndCompare(const std::string& rtp_file,
                        const std::string& output_checksum,
                        const std::string& network_stats_checksum,
                        const std::string& rtcp_stats_checksum,
                        bool gen_ref);

  static void PopulateRtpInfo(int frame_index,
                              int timestamp,
                              WebRtcRTPHeader* rtp_info);
  static void PopulateCng(int frame_index,
                          int timestamp,
                          WebRtcRTPHeader* rtp_info,
                          uint8_t* payload,
                          size_t* payload_len);

  void WrapTest(uint16_t start_seq_no, uint32_t start_timestamp,
                const std::set<uint16_t>& drop_seq_numbers,
                bool expect_seq_no_wrap, bool expect_timestamp_wrap);

  void LongCngWithClockDrift(double drift_factor,
                             double network_freeze_ms,
                             bool pull_audio_during_freeze,
                             int delay_tolerance_ms,
                             int max_time_to_speech_ms);

  void DuplicateCng();

  rtc::Optional<uint32_t> PlayoutTimestamp();

  NetEq* neteq_;
  NetEq::Config config_;
  std::unique_ptr<test::RtpFileSource> rtp_source_;
  std::unique_ptr<test::Packet> packet_;
  unsigned int sim_clock_;
  AudioFrame out_frame_;
  int output_sample_rate_;
  int algorithmic_delay_ms_;
};

// Allocating the static const so that it can be passed by reference.
const int NetEqDecodingTest::kTimeStepMs;
const size_t NetEqDecodingTest::kBlockSize8kHz;
const size_t NetEqDecodingTest::kBlockSize16kHz;
const size_t NetEqDecodingTest::kBlockSize32kHz;
const int NetEqDecodingTest::kInitSampleRateHz;

NetEqDecodingTest::NetEqDecodingTest()
    : neteq_(NULL),
      config_(),
      sim_clock_(0),
      output_sample_rate_(kInitSampleRateHz),
      algorithmic_delay_ms_(0) {
  config_.sample_rate_hz = kInitSampleRateHz;
}

void NetEqDecodingTest::SetUp() {
  neteq_ = NetEq::Create(config_, CreateBuiltinAudioDecoderFactory());
  NetEqNetworkStatistics stat;
  ASSERT_EQ(0, neteq_->NetworkStatistics(&stat));
  algorithmic_delay_ms_ = stat.current_buffer_size_ms;
  ASSERT_TRUE(neteq_);
  LoadDecoders(neteq_);
}

void NetEqDecodingTest::TearDown() {
  delete neteq_;
}

void NetEqDecodingTest::OpenInputFile(const std::string &rtp_file) {
  rtp_source_.reset(test::RtpFileSource::Create(rtp_file));
}

void NetEqDecodingTest::Process() {
  // Check if time to receive.
  while (packet_ && sim_clock_ >= packet_->time_ms()) {
    if (packet_->payload_length_bytes() > 0) {
      WebRtcRTPHeader rtp_header;
      packet_->ConvertHeader(&rtp_header);
#ifndef WEBRTC_CODEC_ISAC
      // Ignore payload type 104 (iSAC-swb) if ISAC is not supported.
      if (rtp_header.header.payloadType != 104)
#endif
      ASSERT_EQ(0, neteq_->InsertPacket(
                       rtp_header,
                       rtc::ArrayView<const uint8_t>(
                           packet_->payload(), packet_->payload_length_bytes()),
                       static_cast<uint32_t>(packet_->time_ms() *
                                             (output_sample_rate_ / 1000))));
    }
    // Get next packet.
    packet_ = rtp_source_->NextPacket();
  }

  // Get audio from NetEq.
  bool muted;
  ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
  ASSERT_FALSE(muted);
  ASSERT_TRUE((out_frame_.samples_per_channel_ == kBlockSize8kHz) ||
              (out_frame_.samples_per_channel_ == kBlockSize16kHz) ||
              (out_frame_.samples_per_channel_ == kBlockSize32kHz) ||
              (out_frame_.samples_per_channel_ == kBlockSize48kHz));
  output_sample_rate_ = out_frame_.sample_rate_hz_;
  EXPECT_EQ(output_sample_rate_, neteq_->last_output_sample_rate_hz());

  // Increase time.
  sim_clock_ += kTimeStepMs;
}

void NetEqDecodingTest::DecodeAndCompare(
    const std::string& rtp_file,
    const std::string& output_checksum,
    const std::string& network_stats_checksum,
    const std::string& rtcp_stats_checksum,
    bool gen_ref) {
  OpenInputFile(rtp_file);

  std::string ref_out_file =
      gen_ref ? webrtc::test::OutputPath() + "neteq_universal_ref.pcm" : "";
  ResultSink output(ref_out_file);

  std::string stat_out_file =
      gen_ref ? webrtc::test::OutputPath() + "neteq_network_stats.dat" : "";
  ResultSink network_stats(stat_out_file);

  std::string rtcp_out_file =
      gen_ref ? webrtc::test::OutputPath() + "neteq_rtcp_stats.dat" : "";
  ResultSink rtcp_stats(rtcp_out_file);

  packet_ = rtp_source_->NextPacket();
  int i = 0;
  while (packet_) {
    std::ostringstream ss;
    ss << "Lap number " << i++ << " in DecodeAndCompare while loop";
    SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
    ASSERT_NO_FATAL_FAILURE(Process());
    ASSERT_NO_FATAL_FAILURE(output.AddResult(
        out_frame_.data_, out_frame_.samples_per_channel_));

    // Query the network statistics API once per second
    if (sim_clock_ % 1000 == 0) {
      // Process NetworkStatistics.
      NetEqNetworkStatistics current_network_stats;
      ASSERT_EQ(0, neteq_->NetworkStatistics(&current_network_stats));
      ASSERT_NO_FATAL_FAILURE(network_stats.AddResult(current_network_stats));

      // Compare with CurrentDelay, which should be identical.
      EXPECT_EQ(current_network_stats.current_buffer_size_ms,
                neteq_->CurrentDelayMs());

      // Process RTCPstat.
      RtcpStatistics current_rtcp_stats;
      neteq_->GetRtcpStatistics(&current_rtcp_stats);
      ASSERT_NO_FATAL_FAILURE(rtcp_stats.AddResult(current_rtcp_stats));
    }
  }

  SCOPED_TRACE("Check output audio.");
  output.VerifyChecksum(output_checksum);
  SCOPED_TRACE("Check network stats.");
  network_stats.VerifyChecksum(network_stats_checksum);
  SCOPED_TRACE("Check rtcp stats.");
  rtcp_stats.VerifyChecksum(rtcp_stats_checksum);
}

void NetEqDecodingTest::PopulateRtpInfo(int frame_index,
                                        int timestamp,
                                        WebRtcRTPHeader* rtp_info) {
  rtp_info->header.sequenceNumber = frame_index;
  rtp_info->header.timestamp = timestamp;
  rtp_info->header.ssrc = 0x1234;  // Just an arbitrary SSRC.
  rtp_info->header.payloadType = 94;  // PCM16b WB codec.
  rtp_info->header.markerBit = 0;
}

void NetEqDecodingTest::PopulateCng(int frame_index,
                                    int timestamp,
                                    WebRtcRTPHeader* rtp_info,
                                    uint8_t* payload,
                                    size_t* payload_len) {
  rtp_info->header.sequenceNumber = frame_index;
  rtp_info->header.timestamp = timestamp;
  rtp_info->header.ssrc = 0x1234;  // Just an arbitrary SSRC.
  rtp_info->header.payloadType = 98;  // WB CNG.
  rtp_info->header.markerBit = 0;
  payload[0] = 64;  // Noise level -64 dBov, quite arbitrarily chosen.
  *payload_len = 1;  // Only noise level, no spectral parameters.
}

#if !defined(WEBRTC_IOS) && defined(WEBRTC_NETEQ_UNITTEST_BITEXACT) && \
    (defined(WEBRTC_CODEC_ISAC) || defined(WEBRTC_CODEC_ISACFX)) &&    \
    defined(WEBRTC_CODEC_ILBC) && defined(WEBRTC_CODEC_G722) &&        \
    !defined(WEBRTC_ARCH_ARM64)
#define MAYBE_TestBitExactness TestBitExactness
#else
#define MAYBE_TestBitExactness DISABLED_TestBitExactness
#endif
TEST_F(NetEqDecodingTest, MAYBE_TestBitExactness) {
  const std::string input_rtp_file =
      webrtc::test::ResourcePath("audio_coding/neteq_universal_new", "rtp");

  const std::string output_checksum = PlatformChecksum(
      "472ebe1126f41fdb6b5c63c87f625a52e7604e49",
      "d2a6b6ff54b340cf9f961c7f07768d86b3761073",
      "472ebe1126f41fdb6b5c63c87f625a52e7604e49",
      "f9749813dbc3fb59dae761de518fec65b8407c5b");

  const std::string network_stats_checksum = PlatformChecksum(
      "2cf380a05ee07080bd72471e8ec7777a39644ec9",
      "01be67dc4c3b8e74743a45cbd8684c0535dec9ad",
      "2cf380a05ee07080bd72471e8ec7777a39644ec9",
      "2cf380a05ee07080bd72471e8ec7777a39644ec9");

  const std::string rtcp_stats_checksum = PlatformChecksum(
      "b8880bf9fed2487efbddcb8d94b9937a29ae521d",
      "f3f7b3d3e71d7e635240b5373b57df6a7e4ce9d4",
      "b8880bf9fed2487efbddcb8d94b9937a29ae521d",
      "b8880bf9fed2487efbddcb8d94b9937a29ae521d");

  DecodeAndCompare(input_rtp_file,
                   output_checksum,
                   network_stats_checksum,
                   rtcp_stats_checksum,
                   FLAGS_gen_ref);
}

#if !defined(WEBRTC_IOS) && !defined(WEBRTC_ANDROID) &&             \
    defined(WEBRTC_NETEQ_UNITTEST_BITEXACT) &&                      \
    defined(WEBRTC_CODEC_OPUS)
#define MAYBE_TestOpusBitExactness TestOpusBitExactness
#else
#define MAYBE_TestOpusBitExactness DISABLED_TestOpusBitExactness
#endif
TEST_F(NetEqDecodingTest, MAYBE_TestOpusBitExactness) {
  const std::string input_rtp_file =
      webrtc::test::ResourcePath("audio_coding/neteq_opus", "rtp");

  const std::string output_checksum = PlatformChecksum(
      "19ad24b4a1eb7a9620e6da09f98c49aa5792ade4",
      "19ad24b4a1eb7a9620e6da09f98c49aa5792ade4",
      "19ad24b4a1eb7a9620e6da09f98c49aa5792ade4",
      "19ad24b4a1eb7a9620e6da09f98c49aa5792ade4");

  const std::string network_stats_checksum = PlatformChecksum(
      "6eab76efbde753d4dde38983445ca16b4ce59b39",
      "6eab76efbde753d4dde38983445ca16b4ce59b39",
      "6eab76efbde753d4dde38983445ca16b4ce59b39",
      "6eab76efbde753d4dde38983445ca16b4ce59b39");

  const std::string rtcp_stats_checksum = PlatformChecksum(
      "e37c797e3de6a64dda88c9ade7a013d022a2e1e0",
      "e37c797e3de6a64dda88c9ade7a013d022a2e1e0",
      "e37c797e3de6a64dda88c9ade7a013d022a2e1e0",
      "e37c797e3de6a64dda88c9ade7a013d022a2e1e0");

  DecodeAndCompare(input_rtp_file,
                   output_checksum,
                   network_stats_checksum,
                   rtcp_stats_checksum,
                   FLAGS_gen_ref);
}

// Use fax mode to avoid time-scaling. This is to simplify the testing of
// packet waiting times in the packet buffer.
class NetEqDecodingTestFaxMode : public NetEqDecodingTest {
 protected:
  NetEqDecodingTestFaxMode() : NetEqDecodingTest() {
    config_.playout_mode = kPlayoutFax;
  }
};

TEST_F(NetEqDecodingTestFaxMode, TestFrameWaitingTimeStatistics) {
  // Insert 30 dummy packets at once. Each packet contains 10 ms 16 kHz audio.
  size_t num_frames = 30;
  const size_t kSamples = 10 * 16;
  const size_t kPayloadBytes = kSamples * 2;
  for (size_t i = 0; i < num_frames; ++i) {
    const uint8_t payload[kPayloadBytes] = {0};
    WebRtcRTPHeader rtp_info;
    rtp_info.header.sequenceNumber = i;
    rtp_info.header.timestamp = i * kSamples;
    rtp_info.header.ssrc = 0x1234;  // Just an arbitrary SSRC.
    rtp_info.header.payloadType = 94;  // PCM16b WB codec.
    rtp_info.header.markerBit = 0;
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
  }
  // Pull out all data.
  for (size_t i = 0; i < num_frames; ++i) {
    bool muted;
    ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
    ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
  }

  NetEqNetworkStatistics stats;
  EXPECT_EQ(0, neteq_->NetworkStatistics(&stats));
  // Since all frames are dumped into NetEQ at once, but pulled out with 10 ms
  // spacing (per definition), we expect the delay to increase with 10 ms for
  // each packet. Thus, we are calculating the statistics for a series from 10
  // to 300, in steps of 10 ms.
  EXPECT_EQ(155, stats.mean_waiting_time_ms);
  EXPECT_EQ(155, stats.median_waiting_time_ms);
  EXPECT_EQ(10, stats.min_waiting_time_ms);
  EXPECT_EQ(300, stats.max_waiting_time_ms);

  // Check statistics again and make sure it's been reset.
  EXPECT_EQ(0, neteq_->NetworkStatistics(&stats));
  EXPECT_EQ(-1, stats.mean_waiting_time_ms);
  EXPECT_EQ(-1, stats.median_waiting_time_ms);
  EXPECT_EQ(-1, stats.min_waiting_time_ms);
  EXPECT_EQ(-1, stats.max_waiting_time_ms);
}

TEST_F(NetEqDecodingTest, TestAverageInterArrivalTimeNegative) {
  const int kNumFrames = 3000;  // Needed for convergence.
  int frame_index = 0;
  const size_t kSamples = 10 * 16;
  const size_t kPayloadBytes = kSamples * 2;
  while (frame_index < kNumFrames) {
    // Insert one packet each time, except every 10th time where we insert two
    // packets at once. This will create a negative clock-drift of approx. 10%.
    int num_packets = (frame_index % 10 == 0 ? 2 : 1);
    for (int n = 0; n < num_packets; ++n) {
      uint8_t payload[kPayloadBytes] = {0};
      WebRtcRTPHeader rtp_info;
      PopulateRtpInfo(frame_index, frame_index * kSamples, &rtp_info);
      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
      ++frame_index;
    }

    // Pull out data once.
    bool muted;
    ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
    ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
  }

  NetEqNetworkStatistics network_stats;
  ASSERT_EQ(0, neteq_->NetworkStatistics(&network_stats));
  EXPECT_EQ(-103196, network_stats.clockdrift_ppm);
}

TEST_F(NetEqDecodingTest, TestAverageInterArrivalTimePositive) {
  const int kNumFrames = 5000;  // Needed for convergence.
  int frame_index = 0;
  const size_t kSamples = 10 * 16;
  const size_t kPayloadBytes = kSamples * 2;
  for (int i = 0; i < kNumFrames; ++i) {
    // Insert one packet each time, except every 10th time where we don't insert
    // any packet. This will create a positive clock-drift of approx. 11%.
    int num_packets = (i % 10 == 9 ? 0 : 1);
    for (int n = 0; n < num_packets; ++n) {
      uint8_t payload[kPayloadBytes] = {0};
      WebRtcRTPHeader rtp_info;
      PopulateRtpInfo(frame_index, frame_index * kSamples, &rtp_info);
      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
      ++frame_index;
    }

    // Pull out data once.
    bool muted;
    ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
    ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
  }

  NetEqNetworkStatistics network_stats;
  ASSERT_EQ(0, neteq_->NetworkStatistics(&network_stats));
  EXPECT_EQ(110946, network_stats.clockdrift_ppm);
}

void NetEqDecodingTest::LongCngWithClockDrift(double drift_factor,
                                              double network_freeze_ms,
                                              bool pull_audio_during_freeze,
                                              int delay_tolerance_ms,
                                              int max_time_to_speech_ms) {
  uint16_t seq_no = 0;
  uint32_t timestamp = 0;
  const int kFrameSizeMs = 30;
  const size_t kSamples = kFrameSizeMs * 16;
  const size_t kPayloadBytes = kSamples * 2;
  double next_input_time_ms = 0.0;
  double t_ms;
  bool muted;

  // Insert speech for 5 seconds.
  const int kSpeechDurationMs = 5000;
  for (t_ms = 0; t_ms < kSpeechDurationMs; t_ms += 10) {
    // Each turn in this for loop is 10 ms.
    while (next_input_time_ms <= t_ms) {
      // Insert one 30 ms speech frame.
      uint8_t payload[kPayloadBytes] = {0};
      WebRtcRTPHeader rtp_info;
      PopulateRtpInfo(seq_no, timestamp, &rtp_info);
      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
      ++seq_no;
      timestamp += kSamples;
      next_input_time_ms += static_cast<double>(kFrameSizeMs) * drift_factor;
    }
    // Pull out data once.
    ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
    ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
  }

  EXPECT_EQ(AudioFrame::kNormalSpeech, out_frame_.speech_type_);
  rtc::Optional<uint32_t> playout_timestamp = PlayoutTimestamp();
  ASSERT_TRUE(playout_timestamp);
  int32_t delay_before = timestamp - *playout_timestamp;

  // Insert CNG for 1 minute (= 60000 ms).
  const int kCngPeriodMs = 100;
  const int kCngPeriodSamples = kCngPeriodMs * 16;  // Period in 16 kHz samples.
  const int kCngDurationMs = 60000;
  for (; t_ms < kSpeechDurationMs + kCngDurationMs; t_ms += 10) {
    // Each turn in this for loop is 10 ms.
    while (next_input_time_ms <= t_ms) {
      // Insert one CNG frame each 100 ms.
      uint8_t payload[kPayloadBytes];
      size_t payload_len;
      WebRtcRTPHeader rtp_info;
      PopulateCng(seq_no, timestamp, &rtp_info, payload, &payload_len);
      ASSERT_EQ(0, neteq_->InsertPacket(
                       rtp_info,
                       rtc::ArrayView<const uint8_t>(payload, payload_len), 0));
      ++seq_no;
      timestamp += kCngPeriodSamples;
      next_input_time_ms += static_cast<double>(kCngPeriodMs) * drift_factor;
    }
    // Pull out data once.
    ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
    ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
  }

  EXPECT_EQ(AudioFrame::kCNG, out_frame_.speech_type_);

  if (network_freeze_ms > 0) {
    // First keep pulling audio for |network_freeze_ms| without inserting
    // any data, then insert CNG data corresponding to |network_freeze_ms|
    // without pulling any output audio.
    const double loop_end_time = t_ms + network_freeze_ms;
    for (; t_ms < loop_end_time; t_ms += 10) {
      // Pull out data once.
      ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
      ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
      EXPECT_EQ(AudioFrame::kCNG, out_frame_.speech_type_);
    }
    bool pull_once = pull_audio_during_freeze;
    // If |pull_once| is true, GetAudio will be called once half-way through
    // the network recovery period.
    double pull_time_ms = (t_ms + next_input_time_ms) / 2;
    while (next_input_time_ms <= t_ms) {
      if (pull_once && next_input_time_ms >= pull_time_ms) {
        pull_once = false;
        // Pull out data once.
        ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
        ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
        EXPECT_EQ(AudioFrame::kCNG, out_frame_.speech_type_);
        t_ms += 10;
      }
      // Insert one CNG frame each 100 ms.
      uint8_t payload[kPayloadBytes];
      size_t payload_len;
      WebRtcRTPHeader rtp_info;
      PopulateCng(seq_no, timestamp, &rtp_info, payload, &payload_len);
      ASSERT_EQ(0, neteq_->InsertPacket(
                       rtp_info,
                       rtc::ArrayView<const uint8_t>(payload, payload_len), 0));
      ++seq_no;
      timestamp += kCngPeriodSamples;
      next_input_time_ms += kCngPeriodMs * drift_factor;
    }
  }

  // Insert speech again until output type is speech.
  double speech_restart_time_ms = t_ms;
  while (out_frame_.speech_type_ != AudioFrame::kNormalSpeech) {
    // Each turn in this for loop is 10 ms.
    while (next_input_time_ms <= t_ms) {
      // Insert one 30 ms speech frame.
      uint8_t payload[kPayloadBytes] = {0};
      WebRtcRTPHeader rtp_info;
      PopulateRtpInfo(seq_no, timestamp, &rtp_info);
      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
      ++seq_no;
      timestamp += kSamples;
      next_input_time_ms += kFrameSizeMs * drift_factor;
    }
    // Pull out data once.
    ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
    ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
    // Increase clock.
    t_ms += 10;
  }

  // Check that the speech starts again within reasonable time.
  double time_until_speech_returns_ms = t_ms - speech_restart_time_ms;
  EXPECT_LT(time_until_speech_returns_ms, max_time_to_speech_ms);
  playout_timestamp = PlayoutTimestamp();
  ASSERT_TRUE(playout_timestamp);
  int32_t delay_after = timestamp - *playout_timestamp;
  // Compare delay before and after, and make sure it differs less than 20 ms.
  EXPECT_LE(delay_after, delay_before + delay_tolerance_ms * 16);
  EXPECT_GE(delay_after, delay_before - delay_tolerance_ms * 16);
}

TEST_F(NetEqDecodingTest, LongCngWithNegativeClockDrift) {
  // Apply a clock drift of -25 ms / s (sender faster than receiver).
  const double kDriftFactor = 1000.0 / (1000.0 + 25.0);
  const double kNetworkFreezeTimeMs = 0.0;
  const bool kGetAudioDuringFreezeRecovery = false;
  const int kDelayToleranceMs = 20;
  const int kMaxTimeToSpeechMs = 100;
  LongCngWithClockDrift(kDriftFactor,
                        kNetworkFreezeTimeMs,
                        kGetAudioDuringFreezeRecovery,
                        kDelayToleranceMs,
                        kMaxTimeToSpeechMs);
}

TEST_F(NetEqDecodingTest, LongCngWithPositiveClockDrift) {
  // Apply a clock drift of +25 ms / s (sender slower than receiver).
  const double kDriftFactor = 1000.0 / (1000.0 - 25.0);
  const double kNetworkFreezeTimeMs = 0.0;
  const bool kGetAudioDuringFreezeRecovery = false;
  const int kDelayToleranceMs = 20;
  const int kMaxTimeToSpeechMs = 100;
  LongCngWithClockDrift(kDriftFactor,
                        kNetworkFreezeTimeMs,
                        kGetAudioDuringFreezeRecovery,
                        kDelayToleranceMs,
                        kMaxTimeToSpeechMs);
}

TEST_F(NetEqDecodingTest, LongCngWithNegativeClockDriftNetworkFreeze) {
  // Apply a clock drift of -25 ms / s (sender faster than receiver).
  const double kDriftFactor = 1000.0 / (1000.0 + 25.0);
  const double kNetworkFreezeTimeMs = 5000.0;
  const bool kGetAudioDuringFreezeRecovery = false;
  const int kDelayToleranceMs = 50;
  const int kMaxTimeToSpeechMs = 200;
  LongCngWithClockDrift(kDriftFactor,
                        kNetworkFreezeTimeMs,
                        kGetAudioDuringFreezeRecovery,
                        kDelayToleranceMs,
                        kMaxTimeToSpeechMs);
}

TEST_F(NetEqDecodingTest, LongCngWithPositiveClockDriftNetworkFreeze) {
  // Apply a clock drift of +25 ms / s (sender slower than receiver).
  const double kDriftFactor = 1000.0 / (1000.0 - 25.0);
  const double kNetworkFreezeTimeMs = 5000.0;
  const bool kGetAudioDuringFreezeRecovery = false;
  const int kDelayToleranceMs = 20;
  const int kMaxTimeToSpeechMs = 100;
  LongCngWithClockDrift(kDriftFactor,
                        kNetworkFreezeTimeMs,
                        kGetAudioDuringFreezeRecovery,
                        kDelayToleranceMs,
                        kMaxTimeToSpeechMs);
}

TEST_F(NetEqDecodingTest, LongCngWithPositiveClockDriftNetworkFreezeExtraPull) {
  // Apply a clock drift of +25 ms / s (sender slower than receiver).
  const double kDriftFactor = 1000.0 / (1000.0 - 25.0);
  const double kNetworkFreezeTimeMs = 5000.0;
  const bool kGetAudioDuringFreezeRecovery = true;
  const int kDelayToleranceMs = 20;
  const int kMaxTimeToSpeechMs = 100;
  LongCngWithClockDrift(kDriftFactor,
                        kNetworkFreezeTimeMs,
                        kGetAudioDuringFreezeRecovery,
                        kDelayToleranceMs,
                        kMaxTimeToSpeechMs);
}

TEST_F(NetEqDecodingTest, LongCngWithoutClockDrift) {
  const double kDriftFactor = 1.0;  // No drift.
  const double kNetworkFreezeTimeMs = 0.0;
  const bool kGetAudioDuringFreezeRecovery = false;
  const int kDelayToleranceMs = 10;
  const int kMaxTimeToSpeechMs = 50;
  LongCngWithClockDrift(kDriftFactor,
                        kNetworkFreezeTimeMs,
                        kGetAudioDuringFreezeRecovery,
                        kDelayToleranceMs,
                        kMaxTimeToSpeechMs);
}

TEST_F(NetEqDecodingTest, UnknownPayloadType) {
  const size_t kPayloadBytes = 100;
  uint8_t payload[kPayloadBytes] = {0};
  WebRtcRTPHeader rtp_info;
  PopulateRtpInfo(0, 0, &rtp_info);
  rtp_info.header.payloadType = 1;  // Not registered as a decoder.
  EXPECT_EQ(NetEq::kFail, neteq_->InsertPacket(rtp_info, payload, 0));
  EXPECT_EQ(NetEq::kUnknownRtpPayloadType, neteq_->LastError());
}

#if defined(WEBRTC_CODEC_ISAC) || defined(WEBRTC_CODEC_ISACFX)
#define MAYBE_DecoderError DecoderError
#else
#define MAYBE_DecoderError DISABLED_DecoderError
#endif

TEST_F(NetEqDecodingTest, MAYBE_DecoderError) {
  const size_t kPayloadBytes = 100;
  uint8_t payload[kPayloadBytes] = {0};
  WebRtcRTPHeader rtp_info;
  PopulateRtpInfo(0, 0, &rtp_info);
  rtp_info.header.payloadType = 103;  // iSAC, but the payload is invalid.
  EXPECT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
  // Set all of |out_data_| to 1, and verify that it was set to 0 by the call
  // to GetAudio.
  for (size_t i = 0; i < AudioFrame::kMaxDataSizeSamples; ++i) {
    out_frame_.data_[i] = 1;
  }
  bool muted;
  EXPECT_EQ(NetEq::kFail, neteq_->GetAudio(&out_frame_, &muted));
  ASSERT_FALSE(muted);
  // Verify that there is a decoder error to check.
  EXPECT_EQ(NetEq::kDecoderErrorCode, neteq_->LastError());

  enum NetEqDecoderError {
    ISAC_LENGTH_MISMATCH = 6730,
    ISAC_RANGE_ERROR_DECODE_FRAME_LENGTH = 6640
  };
#if defined(WEBRTC_CODEC_ISAC)
  EXPECT_EQ(ISAC_LENGTH_MISMATCH, neteq_->LastDecoderError());
#elif defined(WEBRTC_CODEC_ISACFX)
  EXPECT_EQ(ISAC_RANGE_ERROR_DECODE_FRAME_LENGTH, neteq_->LastDecoderError());
#endif
  // Verify that the first 160 samples are set to 0, and that the remaining
  // samples are left unmodified.
  static const int kExpectedOutputLength = 160;  // 10 ms at 16 kHz sample rate.
  for (int i = 0; i < kExpectedOutputLength; ++i) {
    std::ostringstream ss;
    ss << "i = " << i;
    SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
    EXPECT_EQ(0, out_frame_.data_[i]);
  }
  for (size_t i = kExpectedOutputLength; i < AudioFrame::kMaxDataSizeSamples;
       ++i) {
    std::ostringstream ss;
    ss << "i = " << i;
    SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
    EXPECT_EQ(1, out_frame_.data_[i]);
  }
}

TEST_F(NetEqDecodingTest, GetAudioBeforeInsertPacket) {
  // Set all of |out_data_| to 1, and verify that it was set to 0 by the call
  // to GetAudio.
  for (size_t i = 0; i < AudioFrame::kMaxDataSizeSamples; ++i) {
    out_frame_.data_[i] = 1;
  }
  bool muted;
  EXPECT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
  ASSERT_FALSE(muted);
  // Verify that the first block of samples is set to 0.
  static const int kExpectedOutputLength =
      kInitSampleRateHz / 100;  // 10 ms at initial sample rate.
  for (int i = 0; i < kExpectedOutputLength; ++i) {
    std::ostringstream ss;
    ss << "i = " << i;
    SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
    EXPECT_EQ(0, out_frame_.data_[i]);
  }
  // Verify that the sample rate did not change from the initial configuration.
  EXPECT_EQ(config_.sample_rate_hz, neteq_->last_output_sample_rate_hz());
}

class NetEqBgnTest : public NetEqDecodingTest {
 protected:
  virtual void TestCondition(double sum_squared_noise,
                             bool should_be_faded) = 0;

  void CheckBgn(int sampling_rate_hz) {
    size_t expected_samples_per_channel = 0;
    uint8_t payload_type = 0xFF;  // Invalid.
    if (sampling_rate_hz == 8000) {
      expected_samples_per_channel = kBlockSize8kHz;
      payload_type = 93;  // PCM 16, 8 kHz.
    } else if (sampling_rate_hz == 16000) {
      expected_samples_per_channel = kBlockSize16kHz;
      payload_type = 94;  // PCM 16, 16 kHZ.
    } else if (sampling_rate_hz == 32000) {
      expected_samples_per_channel = kBlockSize32kHz;
      payload_type = 95;  // PCM 16, 32 kHz.
    } else {
      ASSERT_TRUE(false);  // Unsupported test case.
    }

    AudioFrame output;
    test::AudioLoop input;
    // We are using the same 32 kHz input file for all tests, regardless of
    // |sampling_rate_hz|. The output may sound weird, but the test is still
    // valid.
    ASSERT_TRUE(input.Init(
        webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm"),
        10 * sampling_rate_hz,  // Max 10 seconds loop length.
        expected_samples_per_channel));

    // Payload of 10 ms of PCM16 32 kHz.
    uint8_t payload[kBlockSize32kHz * sizeof(int16_t)];
    WebRtcRTPHeader rtp_info;
    PopulateRtpInfo(0, 0, &rtp_info);
    rtp_info.header.payloadType = payload_type;

    uint32_t receive_timestamp = 0;
    bool muted;
    for (int n = 0; n < 10; ++n) {  // Insert few packets and get audio.
      auto block = input.GetNextBlock();
      ASSERT_EQ(expected_samples_per_channel, block.size());
      size_t enc_len_bytes =
          WebRtcPcm16b_Encode(block.data(), block.size(), payload);
      ASSERT_EQ(enc_len_bytes, expected_samples_per_channel * 2);

      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, rtc::ArrayView<const uint8_t>(
                                                      payload, enc_len_bytes),
                                        receive_timestamp));
      output.Reset();
      ASSERT_EQ(0, neteq_->GetAudio(&output, &muted));
      ASSERT_EQ(1u, output.num_channels_);
      ASSERT_EQ(expected_samples_per_channel, output.samples_per_channel_);
      ASSERT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

      // Next packet.
      rtp_info.header.timestamp += expected_samples_per_channel;
      rtp_info.header.sequenceNumber++;
      receive_timestamp += expected_samples_per_channel;
    }

    output.Reset();

    // Get audio without inserting packets, expecting PLC and PLC-to-CNG. Pull
    // one frame without checking speech-type. This is the first frame pulled
    // without inserting any packet, and might not be labeled as PLC.
    ASSERT_EQ(0, neteq_->GetAudio(&output, &muted));
    ASSERT_EQ(1u, output.num_channels_);
    ASSERT_EQ(expected_samples_per_channel, output.samples_per_channel_);

    // To be able to test the fading of background noise we need at lease to
    // pull 611 frames.
    const int kFadingThreshold = 611;

    // Test several CNG-to-PLC packet for the expected behavior. The number 20
    // is arbitrary, but sufficiently large to test enough number of frames.
    const int kNumPlcToCngTestFrames = 20;
    bool plc_to_cng = false;
    for (int n = 0; n < kFadingThreshold + kNumPlcToCngTestFrames; ++n) {
      output.Reset();
      memset(output.data_, 1, sizeof(output.data_));  // Set to non-zero.
      ASSERT_EQ(0, neteq_->GetAudio(&output, &muted));
      ASSERT_FALSE(muted);
      ASSERT_EQ(1u, output.num_channels_);
      ASSERT_EQ(expected_samples_per_channel, output.samples_per_channel_);
      if (output.speech_type_ == AudioFrame::kPLCCNG) {
        plc_to_cng = true;
        double sum_squared = 0;
        for (size_t k = 0;
             k < output.num_channels_ * output.samples_per_channel_; ++k)
          sum_squared += output.data_[k] * output.data_[k];
        TestCondition(sum_squared, n > kFadingThreshold);
      } else {
        EXPECT_EQ(AudioFrame::kPLC, output.speech_type_);
      }
    }
    EXPECT_TRUE(plc_to_cng);  // Just to be sure that PLC-to-CNG has occurred.
  }
};

class NetEqBgnTestOn : public NetEqBgnTest {
 protected:
  NetEqBgnTestOn() : NetEqBgnTest() {
    config_.background_noise_mode = NetEq::kBgnOn;
  }

  void TestCondition(double sum_squared_noise, bool /*should_be_faded*/) {
    EXPECT_NE(0, sum_squared_noise);
  }
};

class NetEqBgnTestOff : public NetEqBgnTest {
 protected:
  NetEqBgnTestOff() : NetEqBgnTest() {
    config_.background_noise_mode = NetEq::kBgnOff;
  }

  void TestCondition(double sum_squared_noise, bool /*should_be_faded*/) {
    EXPECT_EQ(0, sum_squared_noise);
  }
};

class NetEqBgnTestFade : public NetEqBgnTest {
 protected:
  NetEqBgnTestFade() : NetEqBgnTest() {
    config_.background_noise_mode = NetEq::kBgnFade;
  }

  void TestCondition(double sum_squared_noise, bool should_be_faded) {
    if (should_be_faded)
      EXPECT_EQ(0, sum_squared_noise);
  }
};

TEST_F(NetEqBgnTestOn, RunTest) {
  CheckBgn(8000);
  CheckBgn(16000);
  CheckBgn(32000);
}

TEST_F(NetEqBgnTestOff, RunTest) {
  CheckBgn(8000);
  CheckBgn(16000);
  CheckBgn(32000);
}

TEST_F(NetEqBgnTestFade, RunTest) {
  CheckBgn(8000);
  CheckBgn(16000);
  CheckBgn(32000);
}

#if defined(WEBRTC_CODEC_ISAC) || defined(WEBRTC_CODEC_ISACFX)
#define MAYBE_SyncPacketInsert SyncPacketInsert
#else
#define MAYBE_SyncPacketInsert DISABLED_SyncPacketInsert
#endif
TEST_F(NetEqDecodingTest, MAYBE_SyncPacketInsert) {
  WebRtcRTPHeader rtp_info;
  uint32_t receive_timestamp = 0;
  // For the readability use the following payloads instead of the defaults of
  // this test.
  uint8_t kPcm16WbPayloadType = 1;
  uint8_t kCngNbPayloadType = 2;
  uint8_t kCngWbPayloadType = 3;
  uint8_t kCngSwb32PayloadType = 4;
  uint8_t kCngSwb48PayloadType = 5;
  uint8_t kAvtPayloadType = 6;
  uint8_t kRedPayloadType = 7;
  uint8_t kIsacPayloadType = 9;  // Payload type 8 is already registered.

  // Register decoders.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(NetEqDecoder::kDecoderPCM16Bwb,
                                           "pcm16-wb", kPcm16WbPayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(NetEqDecoder::kDecoderCNGnb,
                                           "cng-nb", kCngNbPayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(NetEqDecoder::kDecoderCNGwb,
                                           "cng-wb", kCngWbPayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(NetEqDecoder::kDecoderCNGswb32kHz,
                                           "cng-swb32", kCngSwb32PayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(NetEqDecoder::kDecoderCNGswb48kHz,
                                           "cng-swb48", kCngSwb48PayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(NetEqDecoder::kDecoderAVT, "avt",
                                           kAvtPayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(NetEqDecoder::kDecoderRED, "red",
                                           kRedPayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(NetEqDecoder::kDecoderISAC, "isac",
                                           kIsacPayloadType));

  PopulateRtpInfo(0, 0, &rtp_info);
  rtp_info.header.payloadType = kPcm16WbPayloadType;

  // The first packet injected cannot be sync-packet.
  EXPECT_EQ(-1, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));

  // Payload length of 10 ms PCM16 16 kHz.
  const size_t kPayloadBytes = kBlockSize16kHz * sizeof(int16_t);
  uint8_t payload[kPayloadBytes] = {0};
  ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, receive_timestamp));

  // Next packet. Last packet contained 10 ms audio.
  rtp_info.header.sequenceNumber++;
  rtp_info.header.timestamp += kBlockSize16kHz;
  receive_timestamp += kBlockSize16kHz;

  // Unacceptable payload types CNG, AVT (DTMF), RED.
  rtp_info.header.payloadType = kCngNbPayloadType;
  EXPECT_EQ(-1, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));

  rtp_info.header.payloadType = kCngWbPayloadType;
  EXPECT_EQ(-1, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));

  rtp_info.header.payloadType = kCngSwb32PayloadType;
  EXPECT_EQ(-1, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));

  rtp_info.header.payloadType = kCngSwb48PayloadType;
  EXPECT_EQ(-1, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));

  rtp_info.header.payloadType = kAvtPayloadType;
  EXPECT_EQ(-1, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));

  rtp_info.header.payloadType = kRedPayloadType;
  EXPECT_EQ(-1, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));

  // Change of codec cannot be initiated with a sync packet.
  rtp_info.header.payloadType = kIsacPayloadType;
  EXPECT_EQ(-1, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));

  // Change of SSRC is not allowed with a sync packet.
  rtp_info.header.payloadType = kPcm16WbPayloadType;
  ++rtp_info.header.ssrc;
  EXPECT_EQ(-1, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));

  --rtp_info.header.ssrc;
  EXPECT_EQ(0, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));
}

// First insert several noise like packets, then sync-packets. Decoding all
// packets should not produce error, statistics should not show any packet loss
// and sync-packets should decode to zero.
// TODO(turajs) we will have a better test if we have a referece NetEq, and
// when Sync packets are inserted in "test" NetEq we insert all-zero payload
// in reference NetEq and compare the output of those two.
TEST_F(NetEqDecodingTest, SyncPacketDecode) {
  WebRtcRTPHeader rtp_info;
  PopulateRtpInfo(0, 0, &rtp_info);
  const size_t kPayloadBytes = kBlockSize16kHz * sizeof(int16_t);
  uint8_t payload[kPayloadBytes];
  AudioFrame output;
  int algorithmic_frame_delay = algorithmic_delay_ms_ / 10 + 1;
  for (size_t n = 0; n < kPayloadBytes; ++n) {
    payload[n] = (rand() & 0xF0) + 1;  // Non-zero random sequence.
  }
  // Insert some packets which decode to noise. We are not interested in
  // actual decoded values.
  uint32_t receive_timestamp = 0;
  bool muted;
  for (int n = 0; n < 100; ++n) {
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, receive_timestamp));
    ASSERT_EQ(0, neteq_->GetAudio(&output, &muted));
    ASSERT_EQ(kBlockSize16kHz, output.samples_per_channel_);
    ASSERT_EQ(1u, output.num_channels_);

    rtp_info.header.sequenceNumber++;
    rtp_info.header.timestamp += kBlockSize16kHz;
    receive_timestamp += kBlockSize16kHz;
  }
  const int kNumSyncPackets = 10;

  // Make sure sufficient number of sync packets are inserted that we can
  // conduct a test.
  ASSERT_GT(kNumSyncPackets, algorithmic_frame_delay);
  // Insert sync-packets, the decoded sequence should be all-zero.
  for (int n = 0; n < kNumSyncPackets; ++n) {
    ASSERT_EQ(0, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));
    ASSERT_EQ(0, neteq_->GetAudio(&output, &muted));
    ASSERT_FALSE(muted);
    ASSERT_EQ(kBlockSize16kHz, output.samples_per_channel_);
    ASSERT_EQ(1u, output.num_channels_);
    if (n > algorithmic_frame_delay) {
      EXPECT_TRUE(IsAllZero(
          output.data_, output.samples_per_channel_ * output.num_channels_));
    }
    rtp_info.header.sequenceNumber++;
    rtp_info.header.timestamp += kBlockSize16kHz;
    receive_timestamp += kBlockSize16kHz;
  }

  // We insert regular packets, if sync packet are not correctly buffered then
  // network statistics would show some packet loss.
  for (int n = 0; n <= algorithmic_frame_delay + 10; ++n) {
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, receive_timestamp));
    ASSERT_EQ(0, neteq_->GetAudio(&output, &muted));
    ASSERT_FALSE(muted);
    if (n >= algorithmic_frame_delay + 1) {
      // Expect that this frame contain samples from regular RTP.
      EXPECT_TRUE(IsAllNonZero(
          output.data_, output.samples_per_channel_ * output.num_channels_));
    }
    rtp_info.header.sequenceNumber++;
    rtp_info.header.timestamp += kBlockSize16kHz;
    receive_timestamp += kBlockSize16kHz;
  }
  NetEqNetworkStatistics network_stats;
  ASSERT_EQ(0, neteq_->NetworkStatistics(&network_stats));
  // Expecting a "clean" network.
  EXPECT_EQ(0, network_stats.packet_loss_rate);
  EXPECT_EQ(0, network_stats.expand_rate);
  EXPECT_EQ(0, network_stats.accelerate_rate);
  EXPECT_LE(network_stats.preemptive_rate, 150);
}

// Test if the size of the packet buffer reported correctly when containing
// sync packets. Also, test if network packets override sync packets. That is to
// prefer decoding a network packet to a sync packet, if both have same sequence
// number and timestamp.
TEST_F(NetEqDecodingTest, SyncPacketBufferSizeAndOverridenByNetworkPackets) {
  WebRtcRTPHeader rtp_info;
  PopulateRtpInfo(0, 0, &rtp_info);
  const size_t kPayloadBytes = kBlockSize16kHz * sizeof(int16_t);
  uint8_t payload[kPayloadBytes];
  AudioFrame output;
  for (size_t n = 0; n < kPayloadBytes; ++n) {
    payload[n] = (rand() & 0xF0) + 1;  // Non-zero random sequence.
  }
  // Insert some packets which decode to noise. We are not interested in
  // actual decoded values.
  uint32_t receive_timestamp = 0;
  int algorithmic_frame_delay = algorithmic_delay_ms_ / 10 + 1;
  bool muted;
  for (int n = 0; n < algorithmic_frame_delay; ++n) {
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, receive_timestamp));
    ASSERT_EQ(0, neteq_->GetAudio(&output, &muted));
    ASSERT_EQ(kBlockSize16kHz, output.samples_per_channel_);
    ASSERT_EQ(1u, output.num_channels_);
    rtp_info.header.sequenceNumber++;
    rtp_info.header.timestamp += kBlockSize16kHz;
    receive_timestamp += kBlockSize16kHz;
  }
  const int kNumSyncPackets = 10;

  WebRtcRTPHeader first_sync_packet_rtp_info;
  memcpy(&first_sync_packet_rtp_info, &rtp_info, sizeof(rtp_info));

  // Insert sync-packets, but no decoding.
  for (int n = 0; n < kNumSyncPackets; ++n) {
    ASSERT_EQ(0, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));
    rtp_info.header.sequenceNumber++;
    rtp_info.header.timestamp += kBlockSize16kHz;
    receive_timestamp += kBlockSize16kHz;
  }
  NetEqNetworkStatistics network_stats;
  ASSERT_EQ(0, neteq_->NetworkStatistics(&network_stats));
  EXPECT_EQ(kNumSyncPackets * 10 + algorithmic_delay_ms_,
            network_stats.current_buffer_size_ms);

  // Rewind |rtp_info| to that of the first sync packet.
  memcpy(&rtp_info, &first_sync_packet_rtp_info, sizeof(rtp_info));

  // Insert.
  for (int n = 0; n < kNumSyncPackets; ++n) {
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, receive_timestamp));
    rtp_info.header.sequenceNumber++;
    rtp_info.header.timestamp += kBlockSize16kHz;
    receive_timestamp += kBlockSize16kHz;
  }

  // Decode.
  for (int n = 0; n < kNumSyncPackets; ++n) {
    ASSERT_EQ(0, neteq_->GetAudio(&output, &muted));
    ASSERT_FALSE(muted);
    ASSERT_EQ(kBlockSize16kHz, output.samples_per_channel_);
    ASSERT_EQ(1u, output.num_channels_);
    EXPECT_TRUE(IsAllNonZero(
        output.data_, output.samples_per_channel_ * output.num_channels_));
  }
}

void NetEqDecodingTest::WrapTest(uint16_t start_seq_no,
                                 uint32_t start_timestamp,
                                 const std::set<uint16_t>& drop_seq_numbers,
                                 bool expect_seq_no_wrap,
                                 bool expect_timestamp_wrap) {
  uint16_t seq_no = start_seq_no;
  uint32_t timestamp = start_timestamp;
  const int kBlocksPerFrame = 3;  // Number of 10 ms blocks per frame.
  const int kFrameSizeMs = kBlocksPerFrame * kTimeStepMs;
  const int kSamples = kBlockSize16kHz * kBlocksPerFrame;
  const size_t kPayloadBytes = kSamples * sizeof(int16_t);
  double next_input_time_ms = 0.0;
  uint32_t receive_timestamp = 0;

  // Insert speech for 2 seconds.
  const int kSpeechDurationMs = 2000;
  int packets_inserted = 0;
  uint16_t last_seq_no;
  uint32_t last_timestamp;
  bool timestamp_wrapped = false;
  bool seq_no_wrapped = false;
  for (double t_ms = 0; t_ms < kSpeechDurationMs; t_ms += 10) {
    // Each turn in this for loop is 10 ms.
    while (next_input_time_ms <= t_ms) {
      // Insert one 30 ms speech frame.
      uint8_t payload[kPayloadBytes] = {0};
      WebRtcRTPHeader rtp_info;
      PopulateRtpInfo(seq_no, timestamp, &rtp_info);
      if (drop_seq_numbers.find(seq_no) == drop_seq_numbers.end()) {
        // This sequence number was not in the set to drop. Insert it.
        ASSERT_EQ(0,
                  neteq_->InsertPacket(rtp_info, payload, receive_timestamp));
        ++packets_inserted;
      }
      NetEqNetworkStatistics network_stats;
      ASSERT_EQ(0, neteq_->NetworkStatistics(&network_stats));

      // Due to internal NetEq logic, preferred buffer-size is about 4 times the
      // packet size for first few packets. Therefore we refrain from checking
      // the criteria.
      if (packets_inserted > 4) {
        // Expect preferred and actual buffer size to be no more than 2 frames.
        EXPECT_LE(network_stats.preferred_buffer_size_ms, kFrameSizeMs * 2);
        EXPECT_LE(network_stats.current_buffer_size_ms, kFrameSizeMs * 2 +
                  algorithmic_delay_ms_);
      }
      last_seq_no = seq_no;
      last_timestamp = timestamp;

      ++seq_no;
      timestamp += kSamples;
      receive_timestamp += kSamples;
      next_input_time_ms += static_cast<double>(kFrameSizeMs);

      seq_no_wrapped |= seq_no < last_seq_no;
      timestamp_wrapped |= timestamp < last_timestamp;
    }
    // Pull out data once.
    AudioFrame output;
    bool muted;
    ASSERT_EQ(0, neteq_->GetAudio(&output, &muted));
    ASSERT_EQ(kBlockSize16kHz, output.samples_per_channel_);
    ASSERT_EQ(1u, output.num_channels_);

    // Expect delay (in samples) to be less than 2 packets.
    rtc::Optional<uint32_t> playout_timestamp = PlayoutTimestamp();
    ASSERT_TRUE(playout_timestamp);
    EXPECT_LE(timestamp - *playout_timestamp,
              static_cast<uint32_t>(kSamples * 2));
  }
  // Make sure we have actually tested wrap-around.
  ASSERT_EQ(expect_seq_no_wrap, seq_no_wrapped);
  ASSERT_EQ(expect_timestamp_wrap, timestamp_wrapped);
}

TEST_F(NetEqDecodingTest, SequenceNumberWrap) {
  // Start with a sequence number that will soon wrap.
  std::set<uint16_t> drop_seq_numbers;  // Don't drop any packets.
  WrapTest(0xFFFF - 10, 0, drop_seq_numbers, true, false);
}

TEST_F(NetEqDecodingTest, SequenceNumberWrapAndDrop) {
  // Start with a sequence number that will soon wrap.
  std::set<uint16_t> drop_seq_numbers;
  drop_seq_numbers.insert(0xFFFF);
  drop_seq_numbers.insert(0x0);
  WrapTest(0xFFFF - 10, 0, drop_seq_numbers, true, false);
}

TEST_F(NetEqDecodingTest, TimestampWrap) {
  // Start with a timestamp that will soon wrap.
  std::set<uint16_t> drop_seq_numbers;
  WrapTest(0, 0xFFFFFFFF - 3000, drop_seq_numbers, false, true);
}

TEST_F(NetEqDecodingTest, TimestampAndSequenceNumberWrap) {
  // Start with a timestamp and a sequence number that will wrap at the same
  // time.
  std::set<uint16_t> drop_seq_numbers;
  WrapTest(0xFFFF - 10, 0xFFFFFFFF - 5000, drop_seq_numbers, true, true);
}

void NetEqDecodingTest::DuplicateCng() {
  uint16_t seq_no = 0;
  uint32_t timestamp = 0;
  const int kFrameSizeMs = 10;
  const int kSampleRateKhz = 16;
  const int kSamples = kFrameSizeMs * kSampleRateKhz;
  const size_t kPayloadBytes = kSamples * 2;

  const int algorithmic_delay_samples = std::max(
      algorithmic_delay_ms_ * kSampleRateKhz, 5 * kSampleRateKhz / 8);
  // Insert three speech packets. Three are needed to get the frame length
  // correct.
  uint8_t payload[kPayloadBytes] = {0};
  WebRtcRTPHeader rtp_info;
  bool muted;
  for (int i = 0; i < 3; ++i) {
    PopulateRtpInfo(seq_no, timestamp, &rtp_info);
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
    ++seq_no;
    timestamp += kSamples;

    // Pull audio once.
    ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
    ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
  }
  // Verify speech output.
  EXPECT_EQ(AudioFrame::kNormalSpeech, out_frame_.speech_type_);

  // Insert same CNG packet twice.
  const int kCngPeriodMs = 100;
  const int kCngPeriodSamples = kCngPeriodMs * kSampleRateKhz;
  size_t payload_len;
  PopulateCng(seq_no, timestamp, &rtp_info, payload, &payload_len);
  // This is the first time this CNG packet is inserted.
  ASSERT_EQ(
      0, neteq_->InsertPacket(
             rtp_info, rtc::ArrayView<const uint8_t>(payload, payload_len), 0));

  // Pull audio once and make sure CNG is played.
  ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
  ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
  EXPECT_EQ(AudioFrame::kCNG, out_frame_.speech_type_);
  EXPECT_FALSE(PlayoutTimestamp());  // Returns empty value during CNG.
  EXPECT_EQ(timestamp - algorithmic_delay_samples,
            out_frame_.timestamp_ + out_frame_.samples_per_channel_);

  // Insert the same CNG packet again. Note that at this point it is old, since
  // we have already decoded the first copy of it.
  ASSERT_EQ(
      0, neteq_->InsertPacket(
             rtp_info, rtc::ArrayView<const uint8_t>(payload, payload_len), 0));

  // Pull audio until we have played |kCngPeriodMs| of CNG. Start at 10 ms since
  // we have already pulled out CNG once.
  for (int cng_time_ms = 10; cng_time_ms < kCngPeriodMs; cng_time_ms += 10) {
    ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
    ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
    EXPECT_EQ(AudioFrame::kCNG, out_frame_.speech_type_);
    EXPECT_FALSE(PlayoutTimestamp());  // Returns empty value during CNG.
    EXPECT_EQ(timestamp - algorithmic_delay_samples,
              out_frame_.timestamp_ + out_frame_.samples_per_channel_);
  }

  // Insert speech again.
  ++seq_no;
  timestamp += kCngPeriodSamples;
  PopulateRtpInfo(seq_no, timestamp, &rtp_info);
  ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));

  // Pull audio once and verify that the output is speech again.
  ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
  ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, out_frame_.speech_type_);
  rtc::Optional<uint32_t> playout_timestamp = PlayoutTimestamp();
  ASSERT_TRUE(playout_timestamp);
  EXPECT_EQ(timestamp + kSamples - algorithmic_delay_samples,
            *playout_timestamp);
}

rtc::Optional<uint32_t> NetEqDecodingTest::PlayoutTimestamp() {
  return neteq_->GetPlayoutTimestamp();
}

TEST_F(NetEqDecodingTest, DiscardDuplicateCng) { DuplicateCng(); }

TEST_F(NetEqDecodingTest, CngFirst) {
  uint16_t seq_no = 0;
  uint32_t timestamp = 0;
  const int kFrameSizeMs = 10;
  const int kSampleRateKhz = 16;
  const int kSamples = kFrameSizeMs * kSampleRateKhz;
  const int kPayloadBytes = kSamples * 2;
  const int kCngPeriodMs = 100;
  const int kCngPeriodSamples = kCngPeriodMs * kSampleRateKhz;
  size_t payload_len;

  uint8_t payload[kPayloadBytes] = {0};
  WebRtcRTPHeader rtp_info;

  PopulateCng(seq_no, timestamp, &rtp_info, payload, &payload_len);
  ASSERT_EQ(
      NetEq::kOK,
      neteq_->InsertPacket(
          rtp_info, rtc::ArrayView<const uint8_t>(payload, payload_len), 0));
  ++seq_no;
  timestamp += kCngPeriodSamples;

  // Pull audio once and make sure CNG is played.
  bool muted;
  ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
  ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
  EXPECT_EQ(AudioFrame::kCNG, out_frame_.speech_type_);

  // Insert some speech packets.
  for (int i = 0; i < 3; ++i) {
    PopulateRtpInfo(seq_no, timestamp, &rtp_info);
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
    ++seq_no;
    timestamp += kSamples;

    // Pull audio once.
    ASSERT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
    ASSERT_EQ(kBlockSize16kHz, out_frame_.samples_per_channel_);
  }
  // Verify speech output.
  EXPECT_EQ(AudioFrame::kNormalSpeech, out_frame_.speech_type_);
}

class NetEqDecodingTestWithMutedState : public NetEqDecodingTest {
 public:
  NetEqDecodingTestWithMutedState() : NetEqDecodingTest() {
    config_.enable_muted_state = true;
  }

 protected:
  static constexpr size_t kSamples = 10 * 16;
  static constexpr size_t kPayloadBytes = kSamples * 2;

  void InsertPacket(uint32_t rtp_timestamp) {
    uint8_t payload[kPayloadBytes] = {0};
    WebRtcRTPHeader rtp_info;
    PopulateRtpInfo(0, rtp_timestamp, &rtp_info);
    EXPECT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
  }

  bool GetAudioReturnMuted() {
    bool muted;
    EXPECT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
    return muted;
  }

  void GetAudioUntilMuted() {
    while (!GetAudioReturnMuted()) {
      ASSERT_LT(counter_++, 1000) << "Test timed out";
    }
  }

  void GetAudioUntilNormal() {
    bool muted = false;
    while (out_frame_.speech_type_ != AudioFrame::kNormalSpeech) {
      EXPECT_EQ(0, neteq_->GetAudio(&out_frame_, &muted));
      ASSERT_LT(counter_++, 1000) << "Test timed out";
    }
    EXPECT_FALSE(muted);
  }

  int counter_ = 0;
};

// Verifies that NetEq goes in and out of muted state as expected.
TEST_F(NetEqDecodingTestWithMutedState, MutedState) {
  // Insert one speech packet.
  InsertPacket(0);
  // Pull out audio once and expect it not to be muted.
  EXPECT_FALSE(GetAudioReturnMuted());
  // Pull data until faded out.
  GetAudioUntilMuted();

  // Verify that output audio is not written during muted mode. Other parameters
  // should be correct, though.
  AudioFrame new_frame;
  for (auto& d : new_frame.data_) {
    d = 17;
  }
  bool muted;
  EXPECT_EQ(0, neteq_->GetAudio(&new_frame, &muted));
  EXPECT_TRUE(muted);
  for (auto d : new_frame.data_) {
    EXPECT_EQ(17, d);
  }
  EXPECT_EQ(out_frame_.timestamp_ + out_frame_.samples_per_channel_,
            new_frame.timestamp_);
  EXPECT_EQ(out_frame_.samples_per_channel_, new_frame.samples_per_channel_);
  EXPECT_EQ(out_frame_.sample_rate_hz_, new_frame.sample_rate_hz_);
  EXPECT_EQ(out_frame_.num_channels_, new_frame.num_channels_);
  EXPECT_EQ(out_frame_.speech_type_, new_frame.speech_type_);
  EXPECT_EQ(out_frame_.vad_activity_, new_frame.vad_activity_);

  // Insert new data. Timestamp is corrected for the time elapsed since the last
  // packet. Verify that normal operation resumes.
  InsertPacket(kSamples * counter_);
  GetAudioUntilNormal();

  NetEqNetworkStatistics stats;
  EXPECT_EQ(0, neteq_->NetworkStatistics(&stats));
  // NetEqNetworkStatistics::expand_rate tells the fraction of samples that were
  // concealment samples, in Q14 (16384 = 100%) .The vast majority should be
  // concealment samples in this test.
  EXPECT_GT(stats.expand_rate, 14000);
  // And, it should be greater than the speech_expand_rate.
  EXPECT_GT(stats.expand_rate, stats.speech_expand_rate);
}

// Verifies that NetEq goes out of muted state when given a delayed packet.
TEST_F(NetEqDecodingTestWithMutedState, MutedStateDelayedPacket) {
  // Insert one speech packet.
  InsertPacket(0);
  // Pull out audio once and expect it not to be muted.
  EXPECT_FALSE(GetAudioReturnMuted());
  // Pull data until faded out.
  GetAudioUntilMuted();
  // Insert new data. Timestamp is only corrected for the half of the time
  // elapsed since the last packet. That is, the new packet is delayed. Verify
  // that normal operation resumes.
  InsertPacket(kSamples * counter_ / 2);
  GetAudioUntilNormal();
}

// Verifies that NetEq goes out of muted state when given a future packet.
TEST_F(NetEqDecodingTestWithMutedState, MutedStateFuturePacket) {
  // Insert one speech packet.
  InsertPacket(0);
  // Pull out audio once and expect it not to be muted.
  EXPECT_FALSE(GetAudioReturnMuted());
  // Pull data until faded out.
  GetAudioUntilMuted();
  // Insert new data. Timestamp is over-corrected for the time elapsed since the
  // last packet. That is, the new packet is too early. Verify that normal
  // operation resumes.
  InsertPacket(kSamples * counter_ * 2);
  GetAudioUntilNormal();
}

// Verifies that NetEq goes out of muted state when given an old packet.
TEST_F(NetEqDecodingTestWithMutedState, MutedStateOldPacket) {
  // Insert one speech packet.
  InsertPacket(0);
  // Pull out audio once and expect it not to be muted.
  EXPECT_FALSE(GetAudioReturnMuted());
  // Pull data until faded out.
  GetAudioUntilMuted();

  EXPECT_NE(AudioFrame::kNormalSpeech, out_frame_.speech_type_);
  // Insert packet which is older than the first packet.
  InsertPacket(kSamples * (counter_ - 1000));
  EXPECT_FALSE(GetAudioReturnMuted());
  EXPECT_EQ(AudioFrame::kNormalSpeech, out_frame_.speech_type_);
}

class NetEqDecodingTestTwoInstances : public NetEqDecodingTest {
 public:
  NetEqDecodingTestTwoInstances() : NetEqDecodingTest() {}

  void SetUp() override {
    NetEqDecodingTest::SetUp();
    config2_ = config_;
  }

  void CreateSecondInstance() {
    neteq2_.reset(NetEq::Create(config2_, CreateBuiltinAudioDecoderFactory()));
    ASSERT_TRUE(neteq2_);
    LoadDecoders(neteq2_.get());
  }

 protected:
  std::unique_ptr<NetEq> neteq2_;
  NetEq::Config config2_;
};

namespace {
::testing::AssertionResult AudioFramesEqualExceptData(const AudioFrame& a,
                                                      const AudioFrame& b) {
  if (a.timestamp_ != b.timestamp_)
    return ::testing::AssertionFailure() << "timestamp_ diff (" << a.timestamp_
                                         << " != " << b.timestamp_ << ")";
  if (a.sample_rate_hz_ != b.sample_rate_hz_)
    return ::testing::AssertionFailure() << "sample_rate_hz_ diff ("
                                         << a.sample_rate_hz_
                                         << " != " << b.sample_rate_hz_ << ")";
  if (a.samples_per_channel_ != b.samples_per_channel_)
    return ::testing::AssertionFailure()
           << "samples_per_channel_ diff (" << a.samples_per_channel_
           << " != " << b.samples_per_channel_ << ")";
  if (a.num_channels_ != b.num_channels_)
    return ::testing::AssertionFailure() << "num_channels_ diff ("
                                         << a.num_channels_
                                         << " != " << b.num_channels_ << ")";
  if (a.speech_type_ != b.speech_type_)
    return ::testing::AssertionFailure() << "speech_type_ diff ("
                                         << a.speech_type_
                                         << " != " << b.speech_type_ << ")";
  if (a.vad_activity_ != b.vad_activity_)
    return ::testing::AssertionFailure() << "vad_activity_ diff ("
                                         << a.vad_activity_
                                         << " != " << b.vad_activity_ << ")";
  return ::testing::AssertionSuccess();
}

::testing::AssertionResult AudioFramesEqual(const AudioFrame& a,
                                            const AudioFrame& b) {
  ::testing::AssertionResult res = AudioFramesEqualExceptData(a, b);
  if (!res)
    return res;
  if (memcmp(
      a.data_, b.data_,
      a.samples_per_channel_ * a.num_channels_ * sizeof(a.data_[0])) != 0) {
    return ::testing::AssertionFailure() << "data_ diff";
  }
  return ::testing::AssertionSuccess();
}

}  // namespace

TEST_F(NetEqDecodingTestTwoInstances, CompareMutedStateOnOff) {
  ASSERT_FALSE(config_.enable_muted_state);
  config2_.enable_muted_state = true;
  CreateSecondInstance();

  // Insert one speech packet into both NetEqs.
  const size_t kSamples = 10 * 16;
  const size_t kPayloadBytes = kSamples * 2;
  uint8_t payload[kPayloadBytes] = {0};
  WebRtcRTPHeader rtp_info;
  PopulateRtpInfo(0, 0, &rtp_info);
  EXPECT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
  EXPECT_EQ(0, neteq2_->InsertPacket(rtp_info, payload, 0));

  AudioFrame out_frame1, out_frame2;
  bool muted;
  for (int i = 0; i < 1000; ++i) {
    std::ostringstream ss;
    ss << "i = " << i;
    SCOPED_TRACE(ss.str());  // Print out the loop iterator on failure.
    EXPECT_EQ(0, neteq_->GetAudio(&out_frame1, &muted));
    EXPECT_FALSE(muted);
    EXPECT_EQ(0, neteq2_->GetAudio(&out_frame2, &muted));
    if (muted) {
      EXPECT_TRUE(AudioFramesEqualExceptData(out_frame1, out_frame2));
    } else {
      EXPECT_TRUE(AudioFramesEqual(out_frame1, out_frame2));
    }
  }
  EXPECT_TRUE(muted);

  // Insert new data. Timestamp is corrected for the time elapsed since the last
  // packet.
  PopulateRtpInfo(0, kSamples * 1000, &rtp_info);
  EXPECT_EQ(0, neteq_->InsertPacket(rtp_info, payload, 0));
  EXPECT_EQ(0, neteq2_->InsertPacket(rtp_info, payload, 0));

  int counter = 0;
  while (out_frame1.speech_type_ != AudioFrame::kNormalSpeech) {
    ASSERT_LT(counter++, 1000) << "Test timed out";
    std::ostringstream ss;
    ss << "counter = " << counter;
    SCOPED_TRACE(ss.str());  // Print out the loop iterator on failure.
    EXPECT_EQ(0, neteq_->GetAudio(&out_frame1, &muted));
    EXPECT_FALSE(muted);
    EXPECT_EQ(0, neteq2_->GetAudio(&out_frame2, &muted));
    if (muted) {
      EXPECT_TRUE(AudioFramesEqualExceptData(out_frame1, out_frame2));
    } else {
      EXPECT_TRUE(AudioFramesEqual(out_frame1, out_frame2));
    }
  }
  EXPECT_FALSE(muted);
}

}  // namespace webrtc
