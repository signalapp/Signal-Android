/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>

#include "gflags/gflags.h"
#include "gtest/gtest.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/main/interface/audio_coding_module.h"
#include "webrtc/modules/audio_coding/main/test/Channel.h"
#include "webrtc/modules/audio_coding/main/test/PCMFile.h"
#include "webrtc/modules/interface/module_common_types.h"
#include "webrtc/system_wrappers/interface/clock.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"
#include "webrtc/test/testsupport/fileutils.h"

// Codec.
DEFINE_string(codec, "opus", "Codec Name");
DEFINE_int32(codec_sample_rate_hz, 48000, "Sampling rate in Hertz.");
DEFINE_int32(codec_channels, 1, "Number of channels of the codec.");

// PCM input/output.
DEFINE_string(input, "", "Input PCM file at 16 kHz.");
DEFINE_bool(input_stereo, false, "Input is stereo.");
DEFINE_int32(input_fs_hz, 32000, "Input sample rate Hz.");
DEFINE_string(output, "insert_rtp_with_timing_out.pcm", "OutputFile");
DEFINE_int32(output_fs_hz, 32000, "Output sample rate Hz");

// Timing files
DEFINE_string(seq_num, "seq_num", "Sequence number file.");
DEFINE_string(send_ts, "send_timestamp", "Send timestamp file.");
DEFINE_string(receive_ts, "last_rec_timestamp", "Receive timestamp file");

// Delay logging
DEFINE_string(delay, "", "Log for delay.");

// Other setups
DEFINE_int32(init_delay, 0, "Initial delay.");
DEFINE_bool(verbose, false, "Verbosity.");
DEFINE_double(loss_rate, 0, "Rate of packet loss < 1");

const int32_t kAudioPlayedOut = 0x00000001;
const int32_t kPacketPushedIn = 0x00000001 << 1;
const int kPlayoutPeriodMs = 10;

namespace webrtc {

class InsertPacketWithTiming {
 public:
  InsertPacketWithTiming()
      : sender_clock_(new SimulatedClock(0)),
        receiver_clock_(new SimulatedClock(0)),
        send_acm_(AudioCodingModule::Create(0, sender_clock_)),
        receive_acm_(AudioCodingModule::Create(0, receiver_clock_)),
        channel_(new Channel),
        seq_num_fid_(fopen(FLAGS_seq_num.c_str(), "rt")),
        send_ts_fid_(fopen(FLAGS_send_ts.c_str(), "rt")),
        receive_ts_fid_(fopen(FLAGS_receive_ts.c_str(), "rt")),
        pcm_out_fid_(fopen(FLAGS_output.c_str(), "wb")),
        samples_in_1ms_(48),
        num_10ms_in_codec_frame_(2),  // Typical 20 ms frames.
        time_to_insert_packet_ms_(3),  // An arbitrary offset on pushing packet.
        next_receive_ts_(0),
        time_to_playout_audio_ms_(kPlayoutPeriodMs),
        loss_threshold_(0),
        playout_timing_fid_(fopen("playout_timing.txt", "wt")) {}

  void SetUp() {
    ASSERT_TRUE(sender_clock_ != NULL);
    ASSERT_TRUE(receiver_clock_ != NULL);

    ASSERT_TRUE(send_acm_.get() != NULL);
    ASSERT_TRUE(receive_acm_.get() != NULL);
    ASSERT_TRUE(channel_ != NULL);

    ASSERT_TRUE(seq_num_fid_ != NULL);
    ASSERT_TRUE(send_ts_fid_ != NULL);
    ASSERT_TRUE(receive_ts_fid_ != NULL);

    ASSERT_TRUE(playout_timing_fid_ != NULL);

    next_receive_ts_ = ReceiveTimestamp();

    CodecInst codec;
    ASSERT_EQ(0, AudioCodingModule::Codec(FLAGS_codec.c_str(), &codec,
                             FLAGS_codec_sample_rate_hz,
                             FLAGS_codec_channels));
    ASSERT_EQ(0, receive_acm_->InitializeReceiver());
    ASSERT_EQ(0, send_acm_->RegisterSendCodec(codec));
    ASSERT_EQ(0, receive_acm_->RegisterReceiveCodec(codec));

    // Set codec-dependent parameters.
    samples_in_1ms_ = codec.plfreq / 1000;
    num_10ms_in_codec_frame_ = codec.pacsize / (codec.plfreq / 100);

    channel_->RegisterReceiverACM(receive_acm_.get());
    send_acm_->RegisterTransportCallback(channel_);

    if (FLAGS_input.size() == 0) {
      std::string file_name = test::ResourcePath("audio_coding/testfile32kHz",
                                                 "pcm");
      pcm_in_fid_.Open(file_name, 32000, "r", true);  // auto-rewind
      std::cout << "Input file " << file_name << " 32 kHz mono." << std::endl;
    } else {
      pcm_in_fid_.Open(FLAGS_input, static_cast<uint16_t>(FLAGS_input_fs_hz),
                    "r", true);  // auto-rewind
      std::cout << "Input file " << FLAGS_input << "at " << FLAGS_input_fs_hz
          << " Hz in " << ((FLAGS_input_stereo) ? "stereo." : "mono.")
          << std::endl;
      pcm_in_fid_.ReadStereo(FLAGS_input_stereo);
    }

    ASSERT_TRUE(pcm_out_fid_ != NULL);
    std::cout << "Output file " << FLAGS_output << " at " << FLAGS_output_fs_hz
        << " Hz." << std::endl;

    // Other setups
    if (FLAGS_init_delay > 0)
      EXPECT_EQ(0, receive_acm_->SetInitialPlayoutDelay(FLAGS_init_delay));

    if (FLAGS_loss_rate > 0)
      loss_threshold_ = RAND_MAX * FLAGS_loss_rate;
    else
      loss_threshold_ = 0;
  }

  void TickOneMillisecond(uint32_t* action) {
    // One millisecond passed.
    time_to_insert_packet_ms_--;
    time_to_playout_audio_ms_--;
    sender_clock_->AdvanceTimeMilliseconds(1);
    receiver_clock_->AdvanceTimeMilliseconds(1);

    // Reset action.
    *action = 0;

    // Is it time to pull audio?
    if (time_to_playout_audio_ms_ == 0) {
      time_to_playout_audio_ms_ = kPlayoutPeriodMs;
      receive_acm_->PlayoutData10Ms(static_cast<int>(FLAGS_output_fs_hz),
                                    &frame_);
      fwrite(frame_.data_, sizeof(frame_.data_[0]),
             frame_.samples_per_channel_ * frame_.num_channels_, pcm_out_fid_);
      *action |= kAudioPlayedOut;
    }

    // Is it time to push in next packet?
    if (time_to_insert_packet_ms_ <= .5) {
      *action |= kPacketPushedIn;

      // Update time-to-insert packet.
      uint32_t t = next_receive_ts_;
      next_receive_ts_ = ReceiveTimestamp();
      time_to_insert_packet_ms_ += static_cast<float>(next_receive_ts_ - t) /
          samples_in_1ms_;

      // Push in just enough audio.
      for (int n = 0; n < num_10ms_in_codec_frame_; n++) {
        pcm_in_fid_.Read10MsData(frame_);
        EXPECT_EQ(0, send_acm_->Add10MsData(frame_));
      }

      // Set the parameters for the packet to be pushed in receiver ACM right
      // now.
      uint32_t ts = SendTimestamp();
      int seq_num = SequenceNumber();
      bool lost = false;
      channel_->set_send_timestamp(ts);
      channel_->set_sequence_number(seq_num);
      if (loss_threshold_ > 0 && rand() < loss_threshold_) {
        channel_->set_num_packets_to_drop(1);
        lost = true;
      }

      // Process audio in send ACM, this should result in generation of a
      // packet.
      EXPECT_GT(send_acm_->Process(), 0);

      if (FLAGS_verbose) {
        if (!lost) {
          std::cout << "\nInserting packet number " << seq_num
              << " timestamp " << ts << std::endl;
        } else {
          std::cout << "\nLost packet number " << seq_num
              << " timestamp " << ts << std::endl;
        }
      }
    }
  }

  void TearDown() {
    delete channel_;

    fclose(seq_num_fid_);
    fclose(send_ts_fid_);
    fclose(receive_ts_fid_);
    fclose(pcm_out_fid_);
    pcm_in_fid_.Close();
  }

  ~InsertPacketWithTiming() {
    delete sender_clock_;
    delete receiver_clock_;
  }

  // Are there more info to simulate.
  bool HasPackets() {
    if (feof(seq_num_fid_) || feof(send_ts_fid_) || feof(receive_ts_fid_))
      return false;
    return true;
  }

  // Jitter buffer delay.
  void Delay(int* optimal_delay, int* current_delay) {
    ACMNetworkStatistics statistics;
    receive_acm_->NetworkStatistics(&statistics);
    *optimal_delay = statistics.preferredBufferSize;
    *current_delay = statistics.currentBufferSize;
  }

 private:
  uint32_t SendTimestamp() {
    uint32_t t;
    EXPECT_EQ(1, fscanf(send_ts_fid_, "%u\n", &t));
    return t;
  }

  uint32_t ReceiveTimestamp() {
    uint32_t t;
    EXPECT_EQ(1, fscanf(receive_ts_fid_, "%u\n", &t));
    return t;
  }

  int SequenceNumber() {
    int n;
    EXPECT_EQ(1, fscanf(seq_num_fid_, "%d\n", &n));
    return n;
  }

  // This class just creates these pointers, not deleting them. They are deleted
  // by the associated ACM.
  SimulatedClock* sender_clock_;
  SimulatedClock* receiver_clock_;

  scoped_ptr<AudioCodingModule> send_acm_;
  scoped_ptr<AudioCodingModule> receive_acm_;
  Channel* channel_;

  FILE* seq_num_fid_;  // Input (text), one sequence number per line.
  FILE* send_ts_fid_;  // Input (text), one send timestamp per line.
  FILE* receive_ts_fid_;  // Input (text), one receive timestamp per line.
  FILE* pcm_out_fid_;  // Output PCM16.

  PCMFile pcm_in_fid_;  // Input PCM16.

  int samples_in_1ms_;

  // TODO(turajs): this can be computed from the send timestamp, but there is
  // some complication to account for lost and reordered packets.
  int num_10ms_in_codec_frame_;

  float time_to_insert_packet_ms_;
  uint32_t next_receive_ts_;
  uint32_t time_to_playout_audio_ms_;

  AudioFrame frame_;

  double loss_threshold_;

  // Output (text), sequence number, playout timestamp, time (ms) of playout,
  // per line.
  FILE* playout_timing_fid_;
};

}  // webrtc

int main(int argc, char* argv[]) {
  google::ParseCommandLineFlags(&argc, &argv, true);
  webrtc::InsertPacketWithTiming test;
  test.SetUp();

  FILE* delay_log = NULL;
  if (FLAGS_delay.size() > 0) {
    delay_log = fopen(FLAGS_delay.c_str(), "wt");
    if (delay_log == NULL) {
      std::cout << "Cannot open the file to log delay values." << std::endl;
      exit(1);
    }
  }

  uint32_t action_taken;
  int optimal_delay_ms;
  int current_delay_ms;
  while (test.HasPackets()) {
    test.TickOneMillisecond(&action_taken);

    if (action_taken != 0) {
      test.Delay(&optimal_delay_ms, &current_delay_ms);
      if (delay_log != NULL) {
        fprintf(delay_log, "%3d %3d\n", optimal_delay_ms, current_delay_ms);
      }
    }
  }
  std::cout << std::endl;
  test.TearDown();
  if (delay_log != NULL)
    fclose(delay_log);
}
