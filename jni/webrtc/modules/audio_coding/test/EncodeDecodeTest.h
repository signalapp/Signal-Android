/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_CODING_TEST_ENCODEDECODETEST_H_
#define WEBRTC_MODULES_AUDIO_CODING_TEST_ENCODEDECODETEST_H_

#include <stdio.h>
#include <string.h>

#include "webrtc/modules/audio_coding/include/audio_coding_module.h"
#include "webrtc/modules/audio_coding/test/ACMTest.h"
#include "webrtc/modules/audio_coding/test/PCMFile.h"
#include "webrtc/modules/audio_coding/test/RTPFile.h"
#include "webrtc/typedefs.h"

namespace webrtc {

#define MAX_INCOMING_PAYLOAD 8096

// TestPacketization callback which writes the encoded payloads to file
class TestPacketization : public AudioPacketizationCallback {
 public:
  TestPacketization(RTPStream *rtpStream, uint16_t frequency);
  ~TestPacketization();
  int32_t SendData(const FrameType frameType,
                   const uint8_t payloadType,
                   const uint32_t timeStamp,
                   const uint8_t* payloadData,
                   const size_t payloadSize,
                   const RTPFragmentationHeader* fragmentation) override;

 private:
  static void MakeRTPheader(uint8_t* rtpHeader, uint8_t payloadType,
                            int16_t seqNo, uint32_t timeStamp, uint32_t ssrc);
  RTPStream* _rtpStream;
  int32_t _frequency;
  int16_t _seqNo;
};

class Sender {
 public:
  Sender();
  void Setup(AudioCodingModule *acm, RTPStream *rtpStream,
             std::string in_file_name, int sample_rate, size_t channels);
  void Teardown();
  void Run();
  bool Add10MsData();

  //for auto_test and logging
  uint8_t testMode;
  uint8_t codeId;

 protected:
  AudioCodingModule* _acm;

 private:
  PCMFile _pcmFile;
  AudioFrame _audioFrame;
  TestPacketization* _packetization;
};

class Receiver {
 public:
  Receiver();
  virtual ~Receiver() {};
  void Setup(AudioCodingModule *acm, RTPStream *rtpStream,
             std::string out_file_name, size_t channels);
  void Teardown();
  void Run();
  virtual bool IncomingPacket();
  bool PlayoutData();

  //for auto_test and logging
  uint8_t codeId;
  uint8_t testMode;

 private:
  PCMFile _pcmFile;
  int16_t* _playoutBuffer;
  uint16_t _playoutLengthSmpls;
  int32_t _frequency;
  bool _firstTime;

 protected:
  AudioCodingModule* _acm;
  uint8_t _incomingPayload[MAX_INCOMING_PAYLOAD];
  RTPStream* _rtpStream;
  WebRtcRTPHeader _rtpInfo;
  size_t _realPayloadSizeBytes;
  size_t _payloadSizeBytes;
  uint32_t _nextTime;
};

class EncodeDecodeTest : public ACMTest {
 public:
  EncodeDecodeTest();
  explicit EncodeDecodeTest(int testMode);
  void Perform() override;

  uint16_t _playoutFreq;
  uint8_t _testMode;

 private:
  std::string EncodeToFile(int fileType,
                           int codeId,
                           int* codePars,
                           int testMode);

 protected:
  Sender _sender;
  Receiver _receiver;
};

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_CODING_TEST_ENCODEDECODETEST_H_
