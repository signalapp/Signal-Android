/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/neteq/tools/neteq_performance_test.h"

#include "webrtc/base/checks.h"
#include "webrtc/modules/audio_coding/codecs/builtin_audio_decoder_factory.h"
#include "webrtc/modules/audio_coding/codecs/pcm16b/pcm16b.h"
#include "webrtc/modules/audio_coding/neteq/include/neteq.h"
#include "webrtc/modules/audio_coding/neteq/tools/audio_loop.h"
#include "webrtc/modules/audio_coding/neteq/tools/rtp_generator.h"
#include "webrtc/modules/include/module_common_types.h"
#include "webrtc/system_wrappers/include/clock.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/typedefs.h"

using webrtc::NetEq;
using webrtc::test::AudioLoop;
using webrtc::test::RtpGenerator;
using webrtc::WebRtcRTPHeader;

namespace webrtc {
namespace test {

int64_t NetEqPerformanceTest::Run(int runtime_ms,
                                  int lossrate,
                                  double drift_factor) {
  const std::string kInputFileName =
      webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
  const int kSampRateHz = 32000;
  const webrtc::NetEqDecoder kDecoderType =
      webrtc::NetEqDecoder::kDecoderPCM16Bswb32kHz;
  const std::string kDecoderName = "pcm16-swb32";
  const int kPayloadType = 95;

  // Initialize NetEq instance.
  NetEq::Config config;
  config.sample_rate_hz = kSampRateHz;
  NetEq* neteq = NetEq::Create(config, CreateBuiltinAudioDecoderFactory());
  // Register decoder in |neteq|.
  if (neteq->RegisterPayloadType(kDecoderType, kDecoderName, kPayloadType) != 0)
    return -1;

  // Set up AudioLoop object.
  AudioLoop audio_loop;
  const size_t kMaxLoopLengthSamples = kSampRateHz * 10;  // 10 second loop.
  const size_t kInputBlockSizeSamples = 60 * kSampRateHz / 1000;  // 60 ms.
  if (!audio_loop.Init(kInputFileName, kMaxLoopLengthSamples,
                       kInputBlockSizeSamples))
    return -1;

  int32_t time_now_ms = 0;

  // Get first input packet.
  WebRtcRTPHeader rtp_header;
  RtpGenerator rtp_gen(kSampRateHz / 1000);
  // Start with positive drift first half of simulation.
  rtp_gen.set_drift_factor(drift_factor);
  bool drift_flipped = false;
  int32_t packet_input_time_ms =
      rtp_gen.GetRtpHeader(kPayloadType, kInputBlockSizeSamples, &rtp_header);
  auto input_samples = audio_loop.GetNextBlock();
  if (input_samples.empty())
    exit(1);
  uint8_t input_payload[kInputBlockSizeSamples * sizeof(int16_t)];
  size_t payload_len = WebRtcPcm16b_Encode(input_samples.data(),
                                           input_samples.size(), input_payload);
  RTC_CHECK_EQ(sizeof(input_payload), payload_len);

  // Main loop.
  webrtc::Clock* clock = webrtc::Clock::GetRealTimeClock();
  int64_t start_time_ms = clock->TimeInMilliseconds();
  AudioFrame out_frame;
  while (time_now_ms < runtime_ms) {
    while (packet_input_time_ms <= time_now_ms) {
      // Drop every N packets, where N = FLAGS_lossrate.
      bool lost = false;
      if (lossrate > 0) {
        lost = ((rtp_header.header.sequenceNumber - 1) % lossrate) == 0;
      }
      if (!lost) {
        // Insert packet.
        int error =
            neteq->InsertPacket(rtp_header, input_payload,
                                packet_input_time_ms * kSampRateHz / 1000);
        if (error != NetEq::kOK)
          return -1;
      }

      // Get next packet.
      packet_input_time_ms = rtp_gen.GetRtpHeader(kPayloadType,
                                                  kInputBlockSizeSamples,
                                                  &rtp_header);
      input_samples = audio_loop.GetNextBlock();
      if (input_samples.empty())
        return -1;
      payload_len = WebRtcPcm16b_Encode(input_samples.data(),
                                        input_samples.size(), input_payload);
      assert(payload_len == kInputBlockSizeSamples * sizeof(int16_t));
    }

    // Get output audio, but don't do anything with it.
    bool muted;
    int error = neteq->GetAudio(&out_frame, &muted);
    RTC_CHECK(!muted);
    if (error != NetEq::kOK)
      return -1;

    assert(out_frame.samples_per_channel_ ==
           static_cast<size_t>(kSampRateHz * 10 / 1000));

    static const int kOutputBlockSizeMs = 10;
    time_now_ms += kOutputBlockSizeMs;
    if (time_now_ms >= runtime_ms / 2 && !drift_flipped) {
      // Apply negative drift second half of simulation.
      rtp_gen.set_drift_factor(-drift_factor);
      drift_flipped = true;
    }
  }
  int64_t end_time_ms = clock->TimeInMilliseconds();
  delete neteq;
  return end_time_ms - start_time_ms;
}

}  // namespace test
}  // namespace webrtc
