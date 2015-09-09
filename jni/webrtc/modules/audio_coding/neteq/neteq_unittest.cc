/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * This file includes unit tests for NetEQ.
 */

#include "webrtc/modules/audio_coding/neteq/interface/neteq.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>  // memset

#include <algorithm>
#include <set>
#include <string>
#include <vector>

#include "gflags/gflags.h"
#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/test/NETEQTEST_RTPpacket.h"
#include "webrtc/modules/audio_coding/neteq/tools/audio_loop.h"
#include "webrtc/modules/audio_coding/codecs/pcm16b/include/pcm16b.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/test/testsupport/gtest_disable.h"
#include "webrtc/typedefs.h"

DEFINE_bool(gen_ref, false, "Generate reference files.");

namespace webrtc {

static bool IsAllZero(const int16_t* buf, int buf_length) {
  bool all_zero = true;
  for (int n = 0; n < buf_length && all_zero; ++n)
    all_zero = buf[n] == 0;
  return all_zero;
}

static bool IsAllNonZero(const int16_t* buf, int buf_length) {
  bool all_non_zero = true;
  for (int n = 0; n < buf_length && all_non_zero; ++n)
    all_non_zero = buf[n] != 0;
  return all_non_zero;
}

class RefFiles {
 public:
  RefFiles(const std::string& input_file, const std::string& output_file);
  ~RefFiles();
  template<class T> void ProcessReference(const T& test_results);
  template<typename T, size_t n> void ProcessReference(
      const T (&test_results)[n],
      size_t length);
  template<typename T, size_t n> void WriteToFile(
      const T (&test_results)[n],
      size_t length);
  template<typename T, size_t n> void ReadFromFileAndCompare(
      const T (&test_results)[n],
      size_t length);
  void WriteToFile(const NetEqNetworkStatistics& stats);
  void ReadFromFileAndCompare(const NetEqNetworkStatistics& stats);
  void WriteToFile(const RtcpStatistics& stats);
  void ReadFromFileAndCompare(const RtcpStatistics& stats);

  FILE* input_fp_;
  FILE* output_fp_;
};

RefFiles::RefFiles(const std::string &input_file,
                   const std::string &output_file)
    : input_fp_(NULL),
      output_fp_(NULL) {
  if (!input_file.empty()) {
    input_fp_ = fopen(input_file.c_str(), "rb");
    EXPECT_TRUE(input_fp_ != NULL);
  }
  if (!output_file.empty()) {
    output_fp_ = fopen(output_file.c_str(), "wb");
    EXPECT_TRUE(output_fp_ != NULL);
  }
}

RefFiles::~RefFiles() {
  if (input_fp_) {
    EXPECT_EQ(EOF, fgetc(input_fp_));  // Make sure that we reached the end.
    fclose(input_fp_);
  }
  if (output_fp_) fclose(output_fp_);
}

template<class T>
void RefFiles::ProcessReference(const T& test_results) {
  WriteToFile(test_results);
  ReadFromFileAndCompare(test_results);
}

template<typename T, size_t n>
void RefFiles::ProcessReference(const T (&test_results)[n], size_t length) {
  WriteToFile(test_results, length);
  ReadFromFileAndCompare(test_results, length);
}

template<typename T, size_t n>
void RefFiles::WriteToFile(const T (&test_results)[n], size_t length) {
  if (output_fp_) {
    ASSERT_EQ(length, fwrite(&test_results, sizeof(T), length, output_fp_));
  }
}

template<typename T, size_t n>
void RefFiles::ReadFromFileAndCompare(const T (&test_results)[n],
                                      size_t length) {
  if (input_fp_) {
    // Read from ref file.
    T* ref = new T[length];
    ASSERT_EQ(length, fread(ref, sizeof(T), length, input_fp_));
    // Compare
    ASSERT_EQ(0, memcmp(&test_results, ref, sizeof(T) * length));
    delete [] ref;
  }
}

void RefFiles::WriteToFile(const NetEqNetworkStatistics& stats) {
  if (output_fp_) {
    ASSERT_EQ(1u, fwrite(&stats, sizeof(NetEqNetworkStatistics), 1,
                         output_fp_));
  }
}

void RefFiles::ReadFromFileAndCompare(
    const NetEqNetworkStatistics& stats) {
  if (input_fp_) {
    // Read from ref file.
    size_t stat_size = sizeof(NetEqNetworkStatistics);
    NetEqNetworkStatistics ref_stats;
    ASSERT_EQ(1u, fread(&ref_stats, stat_size, 1, input_fp_));
    // Compare
    ASSERT_EQ(0, memcmp(&stats, &ref_stats, stat_size));
  }
}

void RefFiles::WriteToFile(const RtcpStatistics& stats) {
  if (output_fp_) {
    ASSERT_EQ(1u, fwrite(&(stats.fraction_lost), sizeof(stats.fraction_lost), 1,
                         output_fp_));
    ASSERT_EQ(1u, fwrite(&(stats.cumulative_lost),
                         sizeof(stats.cumulative_lost), 1, output_fp_));
    ASSERT_EQ(1u, fwrite(&(stats.extended_max_sequence_number),
                         sizeof(stats.extended_max_sequence_number), 1,
                         output_fp_));
    ASSERT_EQ(1u, fwrite(&(stats.jitter), sizeof(stats.jitter), 1,
                         output_fp_));
  }
}

void RefFiles::ReadFromFileAndCompare(
    const RtcpStatistics& stats) {
  if (input_fp_) {
    // Read from ref file.
    RtcpStatistics ref_stats;
    ASSERT_EQ(1u, fread(&(ref_stats.fraction_lost),
                        sizeof(ref_stats.fraction_lost), 1, input_fp_));
    ASSERT_EQ(1u, fread(&(ref_stats.cumulative_lost),
                        sizeof(ref_stats.cumulative_lost), 1, input_fp_));
    ASSERT_EQ(1u, fread(&(ref_stats.extended_max_sequence_number),
                        sizeof(ref_stats.extended_max_sequence_number), 1,
                        input_fp_));
    ASSERT_EQ(1u, fread(&(ref_stats.jitter), sizeof(ref_stats.jitter), 1,
                        input_fp_));
    // Compare
    ASSERT_EQ(ref_stats.fraction_lost, stats.fraction_lost);
    ASSERT_EQ(ref_stats.cumulative_lost, stats.cumulative_lost);
    ASSERT_EQ(ref_stats.extended_max_sequence_number,
              stats.extended_max_sequence_number);
    ASSERT_EQ(ref_stats.jitter, stats.jitter);
  }
}

class NetEqDecodingTest : public ::testing::Test {
 protected:
  // NetEQ must be polled for data once every 10 ms. Thus, neither of the
  // constants below can be changed.
  static const int kTimeStepMs = 10;
  static const int kBlockSize8kHz = kTimeStepMs * 8;
  static const int kBlockSize16kHz = kTimeStepMs * 16;
  static const int kBlockSize32kHz = kTimeStepMs * 32;
  static const int kMaxBlockSize = kBlockSize32kHz;
  static const int kInitSampleRateHz = 8000;

  NetEqDecodingTest();
  virtual void SetUp();
  virtual void TearDown();
  void SelectDecoders(NetEqDecoder* used_codec);
  void LoadDecoders();
  void OpenInputFile(const std::string &rtp_file);
  void Process(NETEQTEST_RTPpacket* rtp_ptr, int* out_len);
  void DecodeAndCompare(const std::string& rtp_file,
                        const std::string& ref_file,
                        const std::string& stat_ref_file,
                        const std::string& rtcp_ref_file);
  static void PopulateRtpInfo(int frame_index,
                              int timestamp,
                              WebRtcRTPHeader* rtp_info);
  static void PopulateCng(int frame_index,
                          int timestamp,
                          WebRtcRTPHeader* rtp_info,
                          uint8_t* payload,
                          int* payload_len);

  void WrapTest(uint16_t start_seq_no, uint32_t start_timestamp,
                const std::set<uint16_t>& drop_seq_numbers,
                bool expect_seq_no_wrap, bool expect_timestamp_wrap);

  void LongCngWithClockDrift(double drift_factor,
                             double network_freeze_ms,
                             bool pull_audio_during_freeze,
                             int delay_tolerance_ms,
                             int max_time_to_speech_ms);

  void DuplicateCng();

  uint32_t PlayoutTimestamp();

  NetEq* neteq_;
  NetEq::Config config_;
  FILE* rtp_fp_;
  unsigned int sim_clock_;
  int16_t out_data_[kMaxBlockSize];
  int output_sample_rate_;
  int algorithmic_delay_ms_;
};

// Allocating the static const so that it can be passed by reference.
const int NetEqDecodingTest::kTimeStepMs;
const int NetEqDecodingTest::kBlockSize8kHz;
const int NetEqDecodingTest::kBlockSize16kHz;
const int NetEqDecodingTest::kBlockSize32kHz;
const int NetEqDecodingTest::kMaxBlockSize;
const int NetEqDecodingTest::kInitSampleRateHz;

NetEqDecodingTest::NetEqDecodingTest()
    : neteq_(NULL),
      config_(),
      rtp_fp_(NULL),
      sim_clock_(0),
      output_sample_rate_(kInitSampleRateHz),
      algorithmic_delay_ms_(0) {
  config_.sample_rate_hz = kInitSampleRateHz;
  memset(out_data_, 0, sizeof(out_data_));
}

void NetEqDecodingTest::SetUp() {
  neteq_ = NetEq::Create(config_);
  NetEqNetworkStatistics stat;
  ASSERT_EQ(0, neteq_->NetworkStatistics(&stat));
  algorithmic_delay_ms_ = stat.current_buffer_size_ms;
  ASSERT_TRUE(neteq_);
  LoadDecoders();
}

void NetEqDecodingTest::TearDown() {
  delete neteq_;
  if (rtp_fp_)
    fclose(rtp_fp_);
}

void NetEqDecodingTest::LoadDecoders() {
  // Load PCMu.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderPCMu, 0));
  // Load PCMa.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderPCMa, 8));
#ifndef WEBRTC_ANDROID
  // Load iLBC.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderILBC, 102));
#endif  // WEBRTC_ANDROID
  // Load iSAC.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderISAC, 103));
#ifndef WEBRTC_ANDROID
  // Load iSAC SWB.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderISACswb, 104));
  // Load iSAC FB.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderISACfb, 105));
#endif  // WEBRTC_ANDROID
  // Load PCM16B nb.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderPCM16B, 93));
  // Load PCM16B wb.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderPCM16Bwb, 94));
  // Load PCM16B swb32.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderPCM16Bswb32kHz, 95));
  // Load CNG 8 kHz.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderCNGnb, 13));
  // Load CNG 16 kHz.
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderCNGwb, 98));
}

void NetEqDecodingTest::OpenInputFile(const std::string &rtp_file) {
  rtp_fp_ = fopen(rtp_file.c_str(), "rb");
  ASSERT_TRUE(rtp_fp_ != NULL);
  ASSERT_EQ(0, NETEQTEST_RTPpacket::skipFileHeader(rtp_fp_));
}

void NetEqDecodingTest::Process(NETEQTEST_RTPpacket* rtp, int* out_len) {
  // Check if time to receive.
  while ((sim_clock_ >= rtp->time()) &&
         (rtp->dataLen() >= 0)) {
    if (rtp->dataLen() > 0) {
      WebRtcRTPHeader rtpInfo;
      rtp->parseHeader(&rtpInfo);
      ASSERT_EQ(0, neteq_->InsertPacket(
          rtpInfo,
          rtp->payload(),
          rtp->payloadLen(),
          rtp->time() * (output_sample_rate_ / 1000)));
    }
    // Get next packet.
    ASSERT_NE(-1, rtp->readFromFile(rtp_fp_));
  }

  // Get audio from NetEq.
  NetEqOutputType type;
  int num_channels;
  ASSERT_EQ(0, neteq_->GetAudio(kMaxBlockSize, out_data_, out_len,
                                &num_channels, &type));
  ASSERT_TRUE((*out_len == kBlockSize8kHz) ||
              (*out_len == kBlockSize16kHz) ||
              (*out_len == kBlockSize32kHz));
  output_sample_rate_ = *out_len / 10 * 1000;

  // Increase time.
  sim_clock_ += kTimeStepMs;
}

void NetEqDecodingTest::DecodeAndCompare(const std::string& rtp_file,
                                         const std::string& ref_file,
                                         const std::string& stat_ref_file,
                                         const std::string& rtcp_ref_file) {
  OpenInputFile(rtp_file);

  std::string ref_out_file = "";
  if (ref_file.empty()) {
    ref_out_file = webrtc::test::OutputPath() + "neteq_universal_ref.pcm";
  }
  RefFiles ref_files(ref_file, ref_out_file);

  std::string stat_out_file = "";
  if (stat_ref_file.empty()) {
    stat_out_file = webrtc::test::OutputPath() + "neteq_network_stats.dat";
  }
  RefFiles network_stat_files(stat_ref_file, stat_out_file);

  std::string rtcp_out_file = "";
  if (rtcp_ref_file.empty()) {
    rtcp_out_file = webrtc::test::OutputPath() + "neteq_rtcp_stats.dat";
  }
  RefFiles rtcp_stat_files(rtcp_ref_file, rtcp_out_file);

  NETEQTEST_RTPpacket rtp;
  ASSERT_GT(rtp.readFromFile(rtp_fp_), 0);
  int i = 0;
  while (rtp.dataLen() >= 0) {
    std::ostringstream ss;
    ss << "Lap number " << i++ << " in DecodeAndCompare while loop";
    SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
    int out_len = 0;
    ASSERT_NO_FATAL_FAILURE(Process(&rtp, &out_len));
    ASSERT_NO_FATAL_FAILURE(ref_files.ProcessReference(out_data_, out_len));

    // Query the network statistics API once per second
    if (sim_clock_ % 1000 == 0) {
      // Process NetworkStatistics.
      NetEqNetworkStatistics network_stats;
      ASSERT_EQ(0, neteq_->NetworkStatistics(&network_stats));
      ASSERT_NO_FATAL_FAILURE(
          network_stat_files.ProcessReference(network_stats));

      // Process RTCPstat.
      RtcpStatistics rtcp_stats;
      neteq_->GetRtcpStatistics(&rtcp_stats);
      ASSERT_NO_FATAL_FAILURE(rtcp_stat_files.ProcessReference(rtcp_stats));
    }
  }
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
                                    int* payload_len) {
  rtp_info->header.sequenceNumber = frame_index;
  rtp_info->header.timestamp = timestamp;
  rtp_info->header.ssrc = 0x1234;  // Just an arbitrary SSRC.
  rtp_info->header.payloadType = 98;  // WB CNG.
  rtp_info->header.markerBit = 0;
  payload[0] = 64;  // Noise level -64 dBov, quite arbitrarily chosen.
  *payload_len = 1;  // Only noise level, no spectral parameters.
}

TEST_F(NetEqDecodingTest, DISABLED_ON_ANDROID(TestBitExactness)) {
  const std::string input_rtp_file = webrtc::test::ProjectRootPath() +
      "resources/audio_coding/neteq_universal_new.rtp";
  // Note that neteq4_universal_ref.pcm and neteq4_universal_ref_win_32.pcm
  // are identical. The latter could have been removed, but if clients still
  // have a copy of the file, the test will fail.
  const std::string input_ref_file =
      webrtc::test::ResourcePath("audio_coding/neteq4_universal_ref", "pcm");
#if defined(_MSC_VER) && (_MSC_VER >= 1700)
  // For Visual Studio 2012 and later, we will have to use the generic reference
  // file, rather than the windows-specific one.
  const std::string network_stat_ref_file = webrtc::test::ProjectRootPath() +
      "resources/audio_coding/neteq4_network_stats.dat";
#else
  const std::string network_stat_ref_file =
      webrtc::test::ResourcePath("audio_coding/neteq4_network_stats", "dat");
#endif
  const std::string rtcp_stat_ref_file =
      webrtc::test::ResourcePath("audio_coding/neteq4_rtcp_stats", "dat");

  if (FLAGS_gen_ref) {
    DecodeAndCompare(input_rtp_file, "", "", "");
  } else {
    DecodeAndCompare(input_rtp_file,
                     input_ref_file,
                     network_stat_ref_file,
                     rtcp_stat_ref_file);
  }
}

// TODO(hlundin): Re-enable test once the statistics interface is up and again.
TEST_F(NetEqDecodingTest, TestFrameWaitingTimeStatistics) {
  // Use fax mode to avoid time-scaling. This is to simplify the testing of
  // packet waiting times in the packet buffer.
  neteq_->SetPlayoutMode(kPlayoutFax);
  ASSERT_EQ(kPlayoutFax, neteq_->PlayoutMode());
  // Insert 30 dummy packets at once. Each packet contains 10 ms 16 kHz audio.
  size_t num_frames = 30;
  const int kSamples = 10 * 16;
  const int kPayloadBytes = kSamples * 2;
  for (size_t i = 0; i < num_frames; ++i) {
    uint16_t payload[kSamples] = {0};
    WebRtcRTPHeader rtp_info;
    rtp_info.header.sequenceNumber = i;
    rtp_info.header.timestamp = i * kSamples;
    rtp_info.header.ssrc = 0x1234;  // Just an arbitrary SSRC.
    rtp_info.header.payloadType = 94;  // PCM16b WB codec.
    rtp_info.header.markerBit = 0;
    ASSERT_EQ(0, neteq_->InsertPacket(
        rtp_info,
        reinterpret_cast<uint8_t*>(payload),
        kPayloadBytes, 0));
  }
  // Pull out all data.
  for (size_t i = 0; i < num_frames; ++i) {
    int out_len;
    int num_channels;
    NetEqOutputType type;
    ASSERT_EQ(0, neteq_->GetAudio(kMaxBlockSize, out_data_, &out_len,
                                  &num_channels, &type));
    ASSERT_EQ(kBlockSize16kHz, out_len);
  }

  std::vector<int> waiting_times;
  neteq_->WaitingTimes(&waiting_times);
  EXPECT_EQ(num_frames, waiting_times.size());
  // Since all frames are dumped into NetEQ at once, but pulled out with 10 ms
  // spacing (per definition), we expect the delay to increase with 10 ms for
  // each packet.
  for (size_t i = 0; i < waiting_times.size(); ++i) {
    EXPECT_EQ(static_cast<int>(i + 1) * 10, waiting_times[i]);
  }

  // Check statistics again and make sure it's been reset.
  neteq_->WaitingTimes(&waiting_times);
  int len = waiting_times.size();
  EXPECT_EQ(0, len);

  // Process > 100 frames, and make sure that that we get statistics
  // only for 100 frames. Note the new SSRC, causing NetEQ to reset.
  num_frames = 110;
  for (size_t i = 0; i < num_frames; ++i) {
    uint16_t payload[kSamples] = {0};
    WebRtcRTPHeader rtp_info;
    rtp_info.header.sequenceNumber = i;
    rtp_info.header.timestamp = i * kSamples;
    rtp_info.header.ssrc = 0x1235;  // Just an arbitrary SSRC.
    rtp_info.header.payloadType = 94;  // PCM16b WB codec.
    rtp_info.header.markerBit = 0;
    ASSERT_EQ(0, neteq_->InsertPacket(
        rtp_info,
        reinterpret_cast<uint8_t*>(payload),
        kPayloadBytes, 0));
    int out_len;
    int num_channels;
    NetEqOutputType type;
    ASSERT_EQ(0, neteq_->GetAudio(kMaxBlockSize, out_data_, &out_len,
                                  &num_channels, &type));
    ASSERT_EQ(kBlockSize16kHz, out_len);
  }

  neteq_->WaitingTimes(&waiting_times);
  EXPECT_EQ(100u, waiting_times.size());
}

TEST_F(NetEqDecodingTest, TestAverageInterArrivalTimeNegative) {
  const int kNumFrames = 3000;  // Needed for convergence.
  int frame_index = 0;
  const int kSamples = 10 * 16;
  const int kPayloadBytes = kSamples * 2;
  while (frame_index < kNumFrames) {
    // Insert one packet each time, except every 10th time where we insert two
    // packets at once. This will create a negative clock-drift of approx. 10%.
    int num_packets = (frame_index % 10 == 0 ? 2 : 1);
    for (int n = 0; n < num_packets; ++n) {
      uint8_t payload[kPayloadBytes] = {0};
      WebRtcRTPHeader rtp_info;
      PopulateRtpInfo(frame_index, frame_index * kSamples, &rtp_info);
      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes, 0));
      ++frame_index;
    }

    // Pull out data once.
    int out_len;
    int num_channels;
    NetEqOutputType type;
    ASSERT_EQ(0, neteq_->GetAudio(kMaxBlockSize, out_data_, &out_len,
                                  &num_channels, &type));
    ASSERT_EQ(kBlockSize16kHz, out_len);
  }

  NetEqNetworkStatistics network_stats;
  ASSERT_EQ(0, neteq_->NetworkStatistics(&network_stats));
  EXPECT_EQ(-103196, network_stats.clockdrift_ppm);
}

TEST_F(NetEqDecodingTest, TestAverageInterArrivalTimePositive) {
  const int kNumFrames = 5000;  // Needed for convergence.
  int frame_index = 0;
  const int kSamples = 10 * 16;
  const int kPayloadBytes = kSamples * 2;
  for (int i = 0; i < kNumFrames; ++i) {
    // Insert one packet each time, except every 10th time where we don't insert
    // any packet. This will create a positive clock-drift of approx. 11%.
    int num_packets = (i % 10 == 9 ? 0 : 1);
    for (int n = 0; n < num_packets; ++n) {
      uint8_t payload[kPayloadBytes] = {0};
      WebRtcRTPHeader rtp_info;
      PopulateRtpInfo(frame_index, frame_index * kSamples, &rtp_info);
      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes, 0));
      ++frame_index;
    }

    // Pull out data once.
    int out_len;
    int num_channels;
    NetEqOutputType type;
    ASSERT_EQ(0, neteq_->GetAudio(kMaxBlockSize, out_data_, &out_len,
                                  &num_channels, &type));
    ASSERT_EQ(kBlockSize16kHz, out_len);
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
  const int kSamples = kFrameSizeMs * 16;
  const int kPayloadBytes = kSamples * 2;
  double next_input_time_ms = 0.0;
  double t_ms;
  int out_len;
  int num_channels;
  NetEqOutputType type;

  // Insert speech for 5 seconds.
  const int kSpeechDurationMs = 5000;
  for (t_ms = 0; t_ms < kSpeechDurationMs; t_ms += 10) {
    // Each turn in this for loop is 10 ms.
    while (next_input_time_ms <= t_ms) {
      // Insert one 30 ms speech frame.
      uint8_t payload[kPayloadBytes] = {0};
      WebRtcRTPHeader rtp_info;
      PopulateRtpInfo(seq_no, timestamp, &rtp_info);
      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes, 0));
      ++seq_no;
      timestamp += kSamples;
      next_input_time_ms += static_cast<double>(kFrameSizeMs) * drift_factor;
    }
    // Pull out data once.
    ASSERT_EQ(0, neteq_->GetAudio(kMaxBlockSize, out_data_, &out_len,
                                  &num_channels, &type));
    ASSERT_EQ(kBlockSize16kHz, out_len);
  }

  EXPECT_EQ(kOutputNormal, type);
  int32_t delay_before = timestamp - PlayoutTimestamp();

  // Insert CNG for 1 minute (= 60000 ms).
  const int kCngPeriodMs = 100;
  const int kCngPeriodSamples = kCngPeriodMs * 16;  // Period in 16 kHz samples.
  const int kCngDurationMs = 60000;
  for (; t_ms < kSpeechDurationMs + kCngDurationMs; t_ms += 10) {
    // Each turn in this for loop is 10 ms.
    while (next_input_time_ms <= t_ms) {
      // Insert one CNG frame each 100 ms.
      uint8_t payload[kPayloadBytes];
      int payload_len;
      WebRtcRTPHeader rtp_info;
      PopulateCng(seq_no, timestamp, &rtp_info, payload, &payload_len);
      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, payload_len, 0));
      ++seq_no;
      timestamp += kCngPeriodSamples;
      next_input_time_ms += static_cast<double>(kCngPeriodMs) * drift_factor;
    }
    // Pull out data once.
    ASSERT_EQ(0, neteq_->GetAudio(kMaxBlockSize, out_data_, &out_len,
                                  &num_channels, &type));
    ASSERT_EQ(kBlockSize16kHz, out_len);
  }

  EXPECT_EQ(kOutputCNG, type);

  if (network_freeze_ms > 0) {
    // First keep pulling audio for |network_freeze_ms| without inserting
    // any data, then insert CNG data corresponding to |network_freeze_ms|
    // without pulling any output audio.
    const double loop_end_time = t_ms + network_freeze_ms;
    for (; t_ms < loop_end_time; t_ms += 10) {
      // Pull out data once.
      ASSERT_EQ(0,
                neteq_->GetAudio(
                    kMaxBlockSize, out_data_, &out_len, &num_channels, &type));
      ASSERT_EQ(kBlockSize16kHz, out_len);
      EXPECT_EQ(kOutputCNG, type);
    }
    bool pull_once = pull_audio_during_freeze;
    // If |pull_once| is true, GetAudio will be called once half-way through
    // the network recovery period.
    double pull_time_ms = (t_ms + next_input_time_ms) / 2;
    while (next_input_time_ms <= t_ms) {
      if (pull_once && next_input_time_ms >= pull_time_ms) {
        pull_once = false;
        // Pull out data once.
        ASSERT_EQ(
            0,
            neteq_->GetAudio(
                kMaxBlockSize, out_data_, &out_len, &num_channels, &type));
        ASSERT_EQ(kBlockSize16kHz, out_len);
        EXPECT_EQ(kOutputCNG, type);
        t_ms += 10;
      }
      // Insert one CNG frame each 100 ms.
      uint8_t payload[kPayloadBytes];
      int payload_len;
      WebRtcRTPHeader rtp_info;
      PopulateCng(seq_no, timestamp, &rtp_info, payload, &payload_len);
      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, payload_len, 0));
      ++seq_no;
      timestamp += kCngPeriodSamples;
      next_input_time_ms += kCngPeriodMs * drift_factor;
    }
  }

  // Insert speech again until output type is speech.
  double speech_restart_time_ms = t_ms;
  while (type != kOutputNormal) {
    // Each turn in this for loop is 10 ms.
    while (next_input_time_ms <= t_ms) {
      // Insert one 30 ms speech frame.
      uint8_t payload[kPayloadBytes] = {0};
      WebRtcRTPHeader rtp_info;
      PopulateRtpInfo(seq_no, timestamp, &rtp_info);
      ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes, 0));
      ++seq_no;
      timestamp += kSamples;
      next_input_time_ms += kFrameSizeMs * drift_factor;
    }
    // Pull out data once.
    ASSERT_EQ(0, neteq_->GetAudio(kMaxBlockSize, out_data_, &out_len,
                                  &num_channels, &type));
    ASSERT_EQ(kBlockSize16kHz, out_len);
    // Increase clock.
    t_ms += 10;
  }

  // Check that the speech starts again within reasonable time.
  double time_until_speech_returns_ms = t_ms - speech_restart_time_ms;
  EXPECT_LT(time_until_speech_returns_ms, max_time_to_speech_ms);
  int32_t delay_after = timestamp - PlayoutTimestamp();
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
  const int kPayloadBytes = 100;
  uint8_t payload[kPayloadBytes] = {0};
  WebRtcRTPHeader rtp_info;
  PopulateRtpInfo(0, 0, &rtp_info);
  rtp_info.header.payloadType = 1;  // Not registered as a decoder.
  EXPECT_EQ(NetEq::kFail,
            neteq_->InsertPacket(rtp_info, payload, kPayloadBytes, 0));
  EXPECT_EQ(NetEq::kUnknownRtpPayloadType, neteq_->LastError());
}

TEST_F(NetEqDecodingTest, DISABLED_ON_ANDROID(DecoderError)) {
  const int kPayloadBytes = 100;
  uint8_t payload[kPayloadBytes] = {0};
  WebRtcRTPHeader rtp_info;
  PopulateRtpInfo(0, 0, &rtp_info);
  rtp_info.header.payloadType = 103;  // iSAC, but the payload is invalid.
  EXPECT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes, 0));
  NetEqOutputType type;
  // Set all of |out_data_| to 1, and verify that it was set to 0 by the call
  // to GetAudio.
  for (int i = 0; i < kMaxBlockSize; ++i) {
    out_data_[i] = 1;
  }
  int num_channels;
  int samples_per_channel;
  EXPECT_EQ(NetEq::kFail,
            neteq_->GetAudio(kMaxBlockSize, out_data_,
                             &samples_per_channel, &num_channels, &type));
  // Verify that there is a decoder error to check.
  EXPECT_EQ(NetEq::kDecoderErrorCode, neteq_->LastError());
  // Code 6730 is an iSAC error code.
  EXPECT_EQ(6730, neteq_->LastDecoderError());
  // Verify that the first 160 samples are set to 0, and that the remaining
  // samples are left unmodified.
  static const int kExpectedOutputLength = 160;  // 10 ms at 16 kHz sample rate.
  for (int i = 0; i < kExpectedOutputLength; ++i) {
    std::ostringstream ss;
    ss << "i = " << i;
    SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
    EXPECT_EQ(0, out_data_[i]);
  }
  for (int i = kExpectedOutputLength; i < kMaxBlockSize; ++i) {
    std::ostringstream ss;
    ss << "i = " << i;
    SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
    EXPECT_EQ(1, out_data_[i]);
  }
}

TEST_F(NetEqDecodingTest, GetAudioBeforeInsertPacket) {
  NetEqOutputType type;
  // Set all of |out_data_| to 1, and verify that it was set to 0 by the call
  // to GetAudio.
  for (int i = 0; i < kMaxBlockSize; ++i) {
    out_data_[i] = 1;
  }
  int num_channels;
  int samples_per_channel;
  EXPECT_EQ(0, neteq_->GetAudio(kMaxBlockSize, out_data_,
                                &samples_per_channel,
                                &num_channels, &type));
  // Verify that the first block of samples is set to 0.
  static const int kExpectedOutputLength =
      kInitSampleRateHz / 100;  // 10 ms at initial sample rate.
  for (int i = 0; i < kExpectedOutputLength; ++i) {
    std::ostringstream ss;
    ss << "i = " << i;
    SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
    EXPECT_EQ(0, out_data_[i]);
  }
}

class NetEqBgnTest : public NetEqDecodingTest {
 protected:
  virtual void TestCondition(double sum_squared_noise,
                             bool should_be_faded) = 0;

  void CheckBgn(int sampling_rate_hz) {
    int expected_samples_per_channel = 0;
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

    NetEqOutputType type;
    int16_t output[kBlockSize32kHz];  // Maximum size is chosen.
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

    int number_channels = 0;
    int samples_per_channel = 0;

    uint32_t receive_timestamp = 0;
    for (int n = 0; n < 10; ++n) {  // Insert few packets and get audio.
      int enc_len_bytes =
          WebRtcPcm16b_EncodeW16(input.GetNextBlock(),
                                 expected_samples_per_channel,
                                 reinterpret_cast<int16_t*>(payload));
      ASSERT_EQ(enc_len_bytes, expected_samples_per_channel * 2);

      number_channels = 0;
      samples_per_channel = 0;
      ASSERT_EQ(0,
                neteq_->InsertPacket(
                    rtp_info, payload, enc_len_bytes, receive_timestamp));
      ASSERT_EQ(0,
                neteq_->GetAudio(kBlockSize32kHz,
                                 output,
                                 &samples_per_channel,
                                 &number_channels,
                                 &type));
      ASSERT_EQ(1, number_channels);
      ASSERT_EQ(expected_samples_per_channel, samples_per_channel);
      ASSERT_EQ(kOutputNormal, type);

      // Next packet.
      rtp_info.header.timestamp += expected_samples_per_channel;
      rtp_info.header.sequenceNumber++;
      receive_timestamp += expected_samples_per_channel;
    }

    number_channels = 0;
    samples_per_channel = 0;

    // Get audio without inserting packets, expecting PLC and PLC-to-CNG. Pull
    // one frame without checking speech-type. This is the first frame pulled
    // without inserting any packet, and might not be labeled as PLC.
    ASSERT_EQ(0,
              neteq_->GetAudio(kBlockSize32kHz,
                               output,
                               &samples_per_channel,
                               &number_channels,
                               &type));
    ASSERT_EQ(1, number_channels);
    ASSERT_EQ(expected_samples_per_channel, samples_per_channel);

    // To be able to test the fading of background noise we need at lease to
    // pull 611 frames.
    const int kFadingThreshold = 611;

    // Test several CNG-to-PLC packet for the expected behavior. The number 20
    // is arbitrary, but sufficiently large to test enough number of frames.
    const int kNumPlcToCngTestFrames = 20;
    bool plc_to_cng = false;
    for (int n = 0; n < kFadingThreshold + kNumPlcToCngTestFrames; ++n) {
      number_channels = 0;
      samples_per_channel = 0;
      memset(output, 1, sizeof(output));  // Set to non-zero.
      ASSERT_EQ(0,
                neteq_->GetAudio(kBlockSize32kHz,
                                 output,
                                 &samples_per_channel,
                                 &number_channels,
                                 &type));
      ASSERT_EQ(1, number_channels);
      ASSERT_EQ(expected_samples_per_channel, samples_per_channel);
      if (type == kOutputPLCtoCNG) {
        plc_to_cng = true;
        double sum_squared = 0;
        for (int k = 0; k < number_channels * samples_per_channel; ++k)
          sum_squared += output[k] * output[k];
        TestCondition(sum_squared, n > kFadingThreshold);
      } else {
        EXPECT_EQ(kOutputPLC, type);
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

TEST_F(NetEqDecodingTest, SyncPacketInsert) {
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
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderPCM16Bwb,
                                           kPcm16WbPayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderCNGnb, kCngNbPayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderCNGwb, kCngWbPayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderCNGswb32kHz,
                                           kCngSwb32PayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderCNGswb48kHz,
                                           kCngSwb48PayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderAVT, kAvtPayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderRED, kRedPayloadType));
  ASSERT_EQ(0, neteq_->RegisterPayloadType(kDecoderISAC, kIsacPayloadType));

  PopulateRtpInfo(0, 0, &rtp_info);
  rtp_info.header.payloadType = kPcm16WbPayloadType;

  // The first packet injected cannot be sync-packet.
  EXPECT_EQ(-1, neteq_->InsertSyncPacket(rtp_info, receive_timestamp));

  // Payload length of 10 ms PCM16 16 kHz.
  const int kPayloadBytes = kBlockSize16kHz * sizeof(int16_t);
  uint8_t payload[kPayloadBytes] = {0};
  ASSERT_EQ(0, neteq_->InsertPacket(
      rtp_info, payload, kPayloadBytes, receive_timestamp));

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
  const int kPayloadBytes = kBlockSize16kHz * sizeof(int16_t);
  uint8_t payload[kPayloadBytes];
  int16_t decoded[kBlockSize16kHz];
  int algorithmic_frame_delay = algorithmic_delay_ms_ / 10 + 1;
  for (int n = 0; n < kPayloadBytes; ++n) {
    payload[n] = (rand() & 0xF0) + 1;  // Non-zero random sequence.
  }
  // Insert some packets which decode to noise. We are not interested in
  // actual decoded values.
  NetEqOutputType output_type;
  int num_channels;
  int samples_per_channel;
  uint32_t receive_timestamp = 0;
  for (int n = 0; n < 100; ++n) {
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes,
                                      receive_timestamp));
    ASSERT_EQ(0, neteq_->GetAudio(kBlockSize16kHz, decoded,
                                  &samples_per_channel, &num_channels,
                                  &output_type));
    ASSERT_EQ(kBlockSize16kHz, samples_per_channel);
    ASSERT_EQ(1, num_channels);

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
    ASSERT_EQ(0, neteq_->GetAudio(kBlockSize16kHz, decoded,
                                  &samples_per_channel, &num_channels,
                                  &output_type));
    ASSERT_EQ(kBlockSize16kHz, samples_per_channel);
    ASSERT_EQ(1, num_channels);
    if (n > algorithmic_frame_delay) {
      EXPECT_TRUE(IsAllZero(decoded, samples_per_channel * num_channels));
    }
    rtp_info.header.sequenceNumber++;
    rtp_info.header.timestamp += kBlockSize16kHz;
    receive_timestamp += kBlockSize16kHz;
  }

  // We insert regular packets, if sync packet are not correctly buffered then
  // network statistics would show some packet loss.
  for (int n = 0; n <= algorithmic_frame_delay + 10; ++n) {
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes,
                                      receive_timestamp));
    ASSERT_EQ(0, neteq_->GetAudio(kBlockSize16kHz, decoded,
                                  &samples_per_channel, &num_channels,
                                  &output_type));
    if (n >= algorithmic_frame_delay + 1) {
      // Expect that this frame contain samples from regular RTP.
      EXPECT_TRUE(IsAllNonZero(decoded, samples_per_channel * num_channels));
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
  const int kPayloadBytes = kBlockSize16kHz * sizeof(int16_t);
  uint8_t payload[kPayloadBytes];
  int16_t decoded[kBlockSize16kHz];
  for (int n = 0; n < kPayloadBytes; ++n) {
    payload[n] = (rand() & 0xF0) + 1;  // Non-zero random sequence.
  }
  // Insert some packets which decode to noise. We are not interested in
  // actual decoded values.
  NetEqOutputType output_type;
  int num_channels;
  int samples_per_channel;
  uint32_t receive_timestamp = 0;
  int algorithmic_frame_delay = algorithmic_delay_ms_ / 10 + 1;
  for (int n = 0; n < algorithmic_frame_delay; ++n) {
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes,
                                      receive_timestamp));
    ASSERT_EQ(0, neteq_->GetAudio(kBlockSize16kHz, decoded,
                                  &samples_per_channel, &num_channels,
                                  &output_type));
    ASSERT_EQ(kBlockSize16kHz, samples_per_channel);
    ASSERT_EQ(1, num_channels);
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
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes,
                                      receive_timestamp));
    rtp_info.header.sequenceNumber++;
    rtp_info.header.timestamp += kBlockSize16kHz;
    receive_timestamp += kBlockSize16kHz;
  }

  // Decode.
  for (int n = 0; n < kNumSyncPackets; ++n) {
    ASSERT_EQ(0, neteq_->GetAudio(kBlockSize16kHz, decoded,
                                  &samples_per_channel, &num_channels,
                                  &output_type));
    ASSERT_EQ(kBlockSize16kHz, samples_per_channel);
    ASSERT_EQ(1, num_channels);
    EXPECT_TRUE(IsAllNonZero(decoded, samples_per_channel * num_channels));
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
  const int kPayloadBytes = kSamples * sizeof(int16_t);
  double next_input_time_ms = 0.0;
  int16_t decoded[kBlockSize16kHz];
  int num_channels;
  int samples_per_channel;
  NetEqOutputType output_type;
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
                  neteq_->InsertPacket(rtp_info, payload, kPayloadBytes,
                                       receive_timestamp));
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
    ASSERT_EQ(0, neteq_->GetAudio(kBlockSize16kHz, decoded,
                                  &samples_per_channel, &num_channels,
                                  &output_type));
    ASSERT_EQ(kBlockSize16kHz, samples_per_channel);
    ASSERT_EQ(1, num_channels);

    // Expect delay (in samples) to be less than 2 packets.
    EXPECT_LE(timestamp - PlayoutTimestamp(),
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
  const int kPayloadBytes = kSamples * 2;

  const int algorithmic_delay_samples = std::max(
      algorithmic_delay_ms_ * kSampleRateKhz, 5 * kSampleRateKhz / 8);
  // Insert three speech packet. Three are needed to get the frame length
  // correct.
  int out_len;
  int num_channels;
  NetEqOutputType type;
  uint8_t payload[kPayloadBytes] = {0};
  WebRtcRTPHeader rtp_info;
  for (int i = 0; i < 3; ++i) {
    PopulateRtpInfo(seq_no, timestamp, &rtp_info);
    ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes, 0));
    ++seq_no;
    timestamp += kSamples;

    // Pull audio once.
    ASSERT_EQ(0,
              neteq_->GetAudio(
                  kMaxBlockSize, out_data_, &out_len, &num_channels, &type));
    ASSERT_EQ(kBlockSize16kHz, out_len);
  }
  // Verify speech output.
  EXPECT_EQ(kOutputNormal, type);

  // Insert same CNG packet twice.
  const int kCngPeriodMs = 100;
  const int kCngPeriodSamples = kCngPeriodMs * kSampleRateKhz;
  int payload_len;
  PopulateCng(seq_no, timestamp, &rtp_info, payload, &payload_len);
  // This is the first time this CNG packet is inserted.
  ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, payload_len, 0));

  // Pull audio once and make sure CNG is played.
  ASSERT_EQ(0,
            neteq_->GetAudio(
                kMaxBlockSize, out_data_, &out_len, &num_channels, &type));
  ASSERT_EQ(kBlockSize16kHz, out_len);
  EXPECT_EQ(kOutputCNG, type);
  EXPECT_EQ(timestamp - algorithmic_delay_samples, PlayoutTimestamp());

  // Insert the same CNG packet again. Note that at this point it is old, since
  // we have already decoded the first copy of it.
  ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, payload_len, 0));

  // Pull audio until we have played |kCngPeriodMs| of CNG. Start at 10 ms since
  // we have already pulled out CNG once.
  for (int cng_time_ms = 10; cng_time_ms < kCngPeriodMs; cng_time_ms += 10) {
    ASSERT_EQ(0,
              neteq_->GetAudio(
                  kMaxBlockSize, out_data_, &out_len, &num_channels, &type));
    ASSERT_EQ(kBlockSize16kHz, out_len);
    EXPECT_EQ(kOutputCNG, type);
    EXPECT_EQ(timestamp - algorithmic_delay_samples,
              PlayoutTimestamp());
  }

  // Insert speech again.
  ++seq_no;
  timestamp += kCngPeriodSamples;
  PopulateRtpInfo(seq_no, timestamp, &rtp_info);
  ASSERT_EQ(0, neteq_->InsertPacket(rtp_info, payload, kPayloadBytes, 0));

  // Pull audio once and verify that the output is speech again.
  ASSERT_EQ(0,
            neteq_->GetAudio(
                kMaxBlockSize, out_data_, &out_len, &num_channels, &type));
  ASSERT_EQ(kBlockSize16kHz, out_len);
  EXPECT_EQ(kOutputNormal, type);
  EXPECT_EQ(timestamp + kSamples - algorithmic_delay_samples,
            PlayoutTimestamp());
}

uint32_t NetEqDecodingTest::PlayoutTimestamp() {
  uint32_t playout_timestamp = 0;
  EXPECT_TRUE(neteq_->GetPlayoutTimestamp(&playout_timestamp));
  return playout_timestamp;
}

TEST_F(NetEqDecodingTest, DiscardDuplicateCng) { DuplicateCng(); }
}  // namespace webrtc
