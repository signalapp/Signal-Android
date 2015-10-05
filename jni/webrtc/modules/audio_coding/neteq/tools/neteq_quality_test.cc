/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <math.h>
#include <stdio.h>
#include "webrtc/modules/audio_coding/neteq/tools/neteq_quality_test.h"

namespace webrtc {
namespace test {

const uint8_t kPayloadType = 95;
const int kOutputSizeMs = 10;
const int kInitSeed = 0x12345678;
const int kPacketLossTimeUnitMs = 10;

// Define switch for packet loss rate.
static bool ValidatePacketLossRate(const char* /* flag_name */, int32_t value) {
  if (value >= 0 && value <= 100)
    return true;
  printf("Invalid packet loss percentile, should be between 0 and 100.");
  return false;
}

DEFINE_int32(packet_loss_rate, 10, "Percentile of packet loss.");

static const bool packet_loss_rate_dummy =
    RegisterFlagValidator(&FLAGS_packet_loss_rate, &ValidatePacketLossRate);

// Define switch for random loss mode.
static bool ValidateRandomLossMode(const char* /* flag_name */, int32_t value) {
  if (value >= 0 && value <= 2)
    return true;
  printf("Invalid random packet loss mode, should be between 0 and 2.");
  return false;
}

DEFINE_int32(random_loss_mode, 1,
    "Random loss mode: 0--no loss, 1--uniform loss, 2--Gilbert Elliot loss.");
static const bool random_loss_mode_dummy =
    RegisterFlagValidator(&FLAGS_random_loss_mode, &ValidateRandomLossMode);

// Define switch for burst length.
static bool ValidateBurstLength(const char* /* flag_name */, int32_t value) {
  if (value >= kPacketLossTimeUnitMs)
    return true;
  printf("Invalid burst length, should be greater than %d ms.",
         kPacketLossTimeUnitMs);
  return false;
}

DEFINE_int32(burst_length, 30,
    "Burst length in milliseconds, only valid for Gilbert Elliot loss.");

static const bool burst_length_dummy =
    RegisterFlagValidator(&FLAGS_burst_length, &ValidateBurstLength);

// Define switch for drift factor.
static bool ValidateDriftFactor(const char* /* flag_name */, double value) {
  if (value > -0.1)
    return true;
  printf("Invalid drift factor, should be greater than -0.1.");
  return false;
}

DEFINE_double(drift_factor, 0.0, "Time drift factor.");

static const bool drift_factor_dummy =
    RegisterFlagValidator(&FLAGS_drift_factor, &ValidateDriftFactor);

// ProbTrans00Solver() is to calculate the transition probability from no-loss
// state to itself in a modified Gilbert Elliot packet loss model. The result is
// to achieve the target packet loss rate |loss_rate|, when a packet is not
// lost only if all |units| drawings within the duration of the packet result in
// no-loss.
static double ProbTrans00Solver(int units, double loss_rate,
                                double prob_trans_10) {
  if (units == 1)
    return prob_trans_10 / (1.0f - loss_rate) - prob_trans_10;
// 0 == prob_trans_00 ^ (units - 1) + (1 - loss_rate) / prob_trans_10 *
//     prob_trans_00 - (1 - loss_rate) * (1 + 1 / prob_trans_10).
// There is a unique solution between 0.0 and 1.0, due to the monotonicity and
// an opposite sign at 0.0 and 1.0.
// For simplicity, we reformulate the equation as
//     f(x) = x ^ (units - 1) + a x + b.
// Its derivative is
//     f'(x) = (units - 1) x ^ (units - 2) + a.
// The derivative is strictly greater than 0 when x is between 0 and 1.
// We use Newton's method to solve the equation, iteration is
//     x(k+1) = x(k) - f(x) / f'(x);
  const double kPrecision = 0.001f;
  const int kIterations = 100;
  const double a = (1.0f - loss_rate) / prob_trans_10;
  const double b = (loss_rate - 1.0f) * (1.0f + 1.0f / prob_trans_10);
  double x = 0.0f;  // Starting point;
  double f = b;
  double f_p;
  int iter = 0;
  while ((f >= kPrecision || f <= -kPrecision) && iter < kIterations) {
    f_p = (units - 1.0f) * pow(x, units - 2) + a;
    x -= f / f_p;
    if (x > 1.0f) {
      x = 1.0f;
    } else if (x < 0.0f) {
      x = 0.0f;
    }
    f = pow(x, units - 1) + a * x + b;
    iter ++;
  }
  return x;
}

NetEqQualityTest::NetEqQualityTest(int block_duration_ms,
                                   int in_sampling_khz,
                                   int out_sampling_khz,
                                   enum NetEqDecoder decoder_type,
                                   int channels,
                                   std::string in_filename,
                                   std::string out_filename)
    : decoded_time_ms_(0),
      decodable_time_ms_(0),
      drift_factor_(FLAGS_drift_factor),
      packet_loss_rate_(FLAGS_packet_loss_rate),
      block_duration_ms_(block_duration_ms),
      in_sampling_khz_(in_sampling_khz),
      out_sampling_khz_(out_sampling_khz),
      decoder_type_(decoder_type),
      channels_(channels),
      in_filename_(in_filename),
      out_filename_(out_filename),
      log_filename_(out_filename + ".log"),
      in_size_samples_(in_sampling_khz_ * block_duration_ms_),
      out_size_samples_(out_sampling_khz_ * kOutputSizeMs),
      payload_size_bytes_(0),
      max_payload_bytes_(0),
      in_file_(new InputAudioFile(in_filename_)),
      out_file_(NULL),
      log_file_(NULL),
      rtp_generator_(new RtpGenerator(in_sampling_khz_, 0, 0,
                                      decodable_time_ms_)),
      total_payload_size_bytes_(0) {
  NetEq::Config config;
  config.sample_rate_hz = out_sampling_khz_ * 1000;
  neteq_.reset(NetEq::Create(config));
  max_payload_bytes_ = in_size_samples_ * channels_ * sizeof(int16_t);
  in_data_.reset(new int16_t[in_size_samples_ * channels_]);
  payload_.reset(new uint8_t[max_payload_bytes_]);
  out_data_.reset(new int16_t[out_size_samples_ * channels_]);
}

bool NoLoss::Lost() {
  return false;
}

UniformLoss::UniformLoss(double loss_rate)
    : loss_rate_(loss_rate) {
}

bool UniformLoss::Lost() {
  int drop_this = rand();
  return (drop_this < loss_rate_ * RAND_MAX);
}

GilbertElliotLoss::GilbertElliotLoss(double prob_trans_11, double prob_trans_01)
    : prob_trans_11_(prob_trans_11),
      prob_trans_01_(prob_trans_01),
      lost_last_(false),
      uniform_loss_model_(new UniformLoss(0)) {
}

bool GilbertElliotLoss::Lost() {
  // Simulate bursty channel (Gilbert model).
  // (1st order) Markov chain model with memory of the previous/last
  // packet state (lost or received).
  if (lost_last_) {
    // Previous packet was not received.
    uniform_loss_model_->set_loss_rate(prob_trans_11_);
    return lost_last_ = uniform_loss_model_->Lost();
  } else {
    uniform_loss_model_->set_loss_rate(prob_trans_01_);
    return lost_last_ = uniform_loss_model_->Lost();
  }
}

void NetEqQualityTest::SetUp() {
  out_file_ = fopen(out_filename_.c_str(), "wb");
  log_file_ = fopen(log_filename_.c_str(), "wt");
  ASSERT_TRUE(out_file_ != NULL);
  ASSERT_EQ(0, neteq_->RegisterPayloadType(decoder_type_, kPayloadType));
  rtp_generator_->set_drift_factor(drift_factor_);

  int units = block_duration_ms_ / kPacketLossTimeUnitMs;
  switch (FLAGS_random_loss_mode) {
    case 1: {
      // |unit_loss_rate| is the packet loss rate for each unit time interval
      // (kPacketLossTimeUnitMs). Since a packet loss event is generated if any
      // of |block_duration_ms_ / kPacketLossTimeUnitMs| unit time intervals of
      // a full packet duration is drawn with a loss, |unit_loss_rate| fulfills
      // (1 - unit_loss_rate) ^ (block_duration_ms_ / kPacketLossTimeUnitMs) ==
      // 1 - packet_loss_rate.
      double unit_loss_rate = (1.0f - pow(1.0f - 0.01f * packet_loss_rate_,
          1.0f / units));
      loss_model_.reset(new UniformLoss(unit_loss_rate));
      break;
    }
    case 2: {
      // |FLAGS_burst_length| should be integer times of kPacketLossTimeUnitMs.
      ASSERT_EQ(0, FLAGS_burst_length % kPacketLossTimeUnitMs);

      // We do not allow 100 percent packet loss in Gilbert Elliot model, which
      // makes no sense.
      ASSERT_GT(100, packet_loss_rate_);

      // To guarantee the overall packet loss rate, transition probabilities
      // need to satisfy:
      // pi_0 * (1 - prob_trans_01_) ^ units +
      //     pi_1 * prob_trans_10_ ^ (units - 1) == 1 - loss_rate
      // pi_0 = prob_trans_10 / (prob_trans_10 + prob_trans_01_)
      //     is the stationary state probability of no-loss
      // pi_1 = prob_trans_01_ / (prob_trans_10 + prob_trans_01_)
      //     is the stationary state probability of loss
      // After a derivation prob_trans_00 should satisfy:
      // prob_trans_00 ^ (units - 1) = (loss_rate - 1) / prob_trans_10 *
      //     prob_trans_00 + (1 - loss_rate) * (1 + 1 / prob_trans_10).
      double loss_rate = 0.01f * packet_loss_rate_;
      double prob_trans_10 = 1.0f * kPacketLossTimeUnitMs / FLAGS_burst_length;
      double prob_trans_00 = ProbTrans00Solver(units, loss_rate, prob_trans_10);
      loss_model_.reset(new GilbertElliotLoss(1.0f - prob_trans_10,
                                              1.0f - prob_trans_00));
      break;
    }
    default: {
      loss_model_.reset(new NoLoss);
      break;
    }
  }

  // Make sure that the packet loss profile is same for all derived tests.
  srand(kInitSeed);
}

void NetEqQualityTest::TearDown() {
  fclose(out_file_);
}

bool NetEqQualityTest::PacketLost() {
  int cycles = block_duration_ms_ / kPacketLossTimeUnitMs;

  // The loop is to make sure that codecs with different block lengths share the
  // same packet loss profile.
  bool lost = false;
  for (int idx = 0; idx < cycles; idx ++) {
    if (loss_model_->Lost()) {
      // The packet will be lost if any of the drawings indicates a loss, but
      // the loop has to go on to make sure that codecs with different block
      // lengths keep the same pace.
      lost = true;
    }
  }
  return lost;
}

int NetEqQualityTest::Transmit() {
  int packet_input_time_ms =
      rtp_generator_->GetRtpHeader(kPayloadType, in_size_samples_,
                                   &rtp_header_);
  if (payload_size_bytes_ > 0) {
    fprintf(log_file_, "Packet at %d ms", packet_input_time_ms);
    if (!PacketLost()) {
      int ret = neteq_->InsertPacket(rtp_header_, &payload_[0],
                                     payload_size_bytes_,
                                     packet_input_time_ms * in_sampling_khz_);
      if (ret != NetEq::kOK)
        return -1;
      fprintf(log_file_, " OK.\n");
    } else {
      fprintf(log_file_, " Lost.\n");
    }
  }
  return packet_input_time_ms;
}

int NetEqQualityTest::DecodeBlock() {
  int channels;
  int samples;
  int ret = neteq_->GetAudio(out_size_samples_ * channels_, &out_data_[0],
                             &samples, &channels, NULL);

  if (ret != NetEq::kOK) {
    return -1;
  } else {
    assert(channels == channels_);
    assert(samples == kOutputSizeMs * out_sampling_khz_);
    fwrite(&out_data_[0], sizeof(int16_t), samples * channels, out_file_);
    return samples;
  }
}

void NetEqQualityTest::Simulate(int end_time_ms) {
  int audio_size_samples;

  while (decoded_time_ms_ < end_time_ms) {
    // Assume 10 packets in packets buffer.
    while (decodable_time_ms_ - 10 * block_duration_ms_ < decoded_time_ms_) {
      ASSERT_TRUE(in_file_->Read(in_size_samples_ * channels_, &in_data_[0]));
      payload_size_bytes_ = EncodeBlock(&in_data_[0],
                                        in_size_samples_, &payload_[0],
                                        max_payload_bytes_);
      total_payload_size_bytes_ += payload_size_bytes_;
      decodable_time_ms_ = Transmit() + block_duration_ms_;
    }
    audio_size_samples = DecodeBlock();
    if (audio_size_samples > 0) {
      decoded_time_ms_ += audio_size_samples / out_sampling_khz_;
    }
  }
  fprintf(log_file_, "%f", 8.0f * total_payload_size_bytes_ / end_time_ms);
}

}  // namespace test
}  // namespace webrtc
