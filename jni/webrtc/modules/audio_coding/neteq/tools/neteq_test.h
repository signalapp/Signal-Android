/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_TEST_H_
#define WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_TEST_H_

#include <map>
#include <memory>
#include <string>
#include <utility>

#include "webrtc/modules/audio_coding/neteq/include/neteq.h"
#include "webrtc/modules/audio_coding/neteq/tools/audio_sink.h"
#include "webrtc/modules/audio_coding/neteq/tools/neteq_input.h"

namespace webrtc {
namespace test {

class NetEqTestErrorCallback {
 public:
  virtual ~NetEqTestErrorCallback() = default;
  virtual void OnInsertPacketError(int error_code,
                                   const NetEqInput::PacketData& packet) {}
  virtual void OnGetAudioError(int error_code) {}
};

class DefaultNetEqTestErrorCallback : public NetEqTestErrorCallback {
  void OnInsertPacketError(int error_code,
                           const NetEqInput::PacketData& packet) override;
  void OnGetAudioError(int error_code) override;
};

// Class that provides an input--output test for NetEq. The input (both packets
// and output events) is provided by a NetEqInput object, while the output is
// directed to an AudioSink object.
class NetEqTest {
 public:
  using DecoderMap = std::map<int, std::pair<NetEqDecoder, std::string> >;

  struct ExternalDecoderInfo {
    AudioDecoder* decoder;
    NetEqDecoder codec;
    std::string codec_name;
  };

  using ExtDecoderMap = std::map<int, ExternalDecoderInfo>;

  // Sets up the test with given configuration, codec mappings, input, ouput,
  // and callback objects for error reporting.
  NetEqTest(const NetEq::Config& config,
            const DecoderMap& codecs,
            const ExtDecoderMap& ext_codecs,
            std::unique_ptr<NetEqInput> input,
            std::unique_ptr<AudioSink> output,
            NetEqTestErrorCallback* error_callback);

  ~NetEqTest() = default;

  // Runs the test. Returns the duration of the produced audio in ms.
  int64_t Run();

  // Returns the statistics from NetEq.
  NetEqNetworkStatistics SimulationStats();

 private:
  void RegisterDecoders(const DecoderMap& codecs);
  void RegisterExternalDecoders(const ExtDecoderMap& codecs);

  std::unique_ptr<NetEq> neteq_;
  std::unique_ptr<NetEqInput> input_;
  std::unique_ptr<AudioSink> output_;
  NetEqTestErrorCallback* error_callback_ = nullptr;
  int sample_rate_hz_;
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_NETEQ_TOOLS_NETEQ_TEST_H_
