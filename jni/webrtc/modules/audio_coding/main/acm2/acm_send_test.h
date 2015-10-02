/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_SEND_TEST_H_
#define WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_SEND_TEST_H_

#include <vector>

#include "webrtc/base/constructormagic.h"
#include "webrtc/modules/audio_coding/main/interface/audio_coding_module.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet_source.h"
#include "webrtc/system_wrappers/interface/clock.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"

namespace webrtc {

namespace test {
class InputAudioFile;
class Packet;

class AcmSendTest : public AudioPacketizationCallback, public PacketSource {
 public:
  AcmSendTest(InputAudioFile* audio_source,
              int source_rate_hz,
              int test_duration_ms);
  virtual ~AcmSendTest() {}

  // Registers the send codec. Returns true on success, false otherwise.
  bool RegisterCodec(const char* payload_name,
                     int sampling_freq_hz,
                     int channels,
                     int payload_type,
                     int frame_size_samples);

  // Returns the next encoded packet. Returns NULL if the test duration was
  // exceeded. Ownership of the packet is handed over to the caller.
  // Inherited from PacketSource.
  Packet* NextPacket();

  // Inherited from AudioPacketizationCallback.
  virtual int32_t SendData(
      FrameType frame_type,
      uint8_t payload_type,
      uint32_t timestamp,
      const uint8_t* payload_data,
      uint16_t payload_len_bytes,
      const RTPFragmentationHeader* fragmentation) OVERRIDE;

 private:
  static const int kBlockSizeMs = 10;

  // Creates a Packet object from the last packet produced by ACM (and received
  // through the SendData method as a callback). Ownership of the new Packet
  // object is transferred to the caller.
  Packet* CreatePacket();

  SimulatedClock clock_;
  scoped_ptr<AudioCodingModule> acm_;
  InputAudioFile* audio_source_;
  int source_rate_hz_;
  const int input_block_size_samples_;
  AudioFrame input_frame_;
  CodecInst codec_;
  bool codec_registered_;
  int test_duration_ms_;
  // The following member variables are set whenever SendData() is called.
  FrameType frame_type_;
  int payload_type_;
  uint32_t timestamp_;
  uint16_t sequence_number_;
  std::vector<uint8_t> last_payload_vec_;

  DISALLOW_COPY_AND_ASSIGN(AcmSendTest);
};

}  // namespace test
}  // namespace webrtc
#endif  // WEBRTC_MODULES_AUDIO_CODING_MAIN_ACM2_ACM_SEND_TEST_H_
