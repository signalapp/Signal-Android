/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/modules/audio_coding/test/EncodeDecodeTest.h"

#include <memory>
#include <sstream>
#include <stdio.h>
#include <stdlib.h>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/common_types.h"
#include "webrtc/modules/audio_coding/include/audio_coding_module.h"
#include "webrtc/modules/audio_coding/acm2/acm_common_defs.h"
#include "webrtc/modules/audio_coding/test/utility.h"
#include "webrtc/system_wrappers/include/trace.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

TestPacketization::TestPacketization(RTPStream *rtpStream, uint16_t frequency)
    : _rtpStream(rtpStream),
      _frequency(frequency),
      _seqNo(0) {
}

TestPacketization::~TestPacketization() {
}

int32_t TestPacketization::SendData(
    const FrameType /* frameType */, const uint8_t payloadType,
    const uint32_t timeStamp, const uint8_t* payloadData,
    const size_t payloadSize,
    const RTPFragmentationHeader* /* fragmentation */) {
  _rtpStream->Write(payloadType, timeStamp, _seqNo++, payloadData, payloadSize,
                    _frequency);
  return 1;
}

Sender::Sender()
    : _acm(NULL),
      _pcmFile(),
      _audioFrame(),
      _packetization(NULL) {
}

void Sender::Setup(AudioCodingModule *acm, RTPStream *rtpStream,
                   std::string in_file_name, int sample_rate, size_t channels) {
  struct CodecInst sendCodec;
  int noOfCodecs = acm->NumberOfCodecs();
  int codecNo;

  // Open input file
  const std::string file_name = webrtc::test::ResourcePath(in_file_name, "pcm");
  _pcmFile.Open(file_name, sample_rate, "rb");
  if (channels == 2) {
    _pcmFile.ReadStereo(true);
  }
  // Set test length to 500 ms (50 blocks of 10 ms each).
  _pcmFile.SetNum10MsBlocksToRead(50);
  // Fast-forward 1 second (100 blocks) since the file starts with silence.
  _pcmFile.FastForward(100);

  // Set the codec for the current test.
  if ((testMode == 0) || (testMode == 1)) {
    // Set the codec id.
    codecNo = codeId;
  } else {
    // Choose codec on command line.
    printf("List of supported codec.\n");
    for (int n = 0; n < noOfCodecs; n++) {
      EXPECT_EQ(0, acm->Codec(n, &sendCodec));
      printf("%d %s\n", n, sendCodec.plname);
    }
    printf("Choose your codec:");
    ASSERT_GT(scanf("%d", &codecNo), 0);
  }

  EXPECT_EQ(0, acm->Codec(codecNo, &sendCodec));

  sendCodec.channels = channels;

  EXPECT_EQ(0, acm->RegisterSendCodec(sendCodec));
  _packetization = new TestPacketization(rtpStream, sendCodec.plfreq);
  EXPECT_EQ(0, acm->RegisterTransportCallback(_packetization));

  _acm = acm;
}

void Sender::Teardown() {
  _pcmFile.Close();
  delete _packetization;
}

bool Sender::Add10MsData() {
  if (!_pcmFile.EndOfFile()) {
    EXPECT_GT(_pcmFile.Read10MsData(_audioFrame), 0);
    int32_t ok = _acm->Add10MsData(_audioFrame);
    EXPECT_GE(ok, 0);
    return ok >= 0 ? true : false;
  }
  return false;
}

void Sender::Run() {
  while (true) {
    if (!Add10MsData()) {
      break;
    }
  }
}

Receiver::Receiver()
    : _playoutLengthSmpls(WEBRTC_10MS_PCM_AUDIO),
      _payloadSizeBytes(MAX_INCOMING_PAYLOAD) {
}

void Receiver::Setup(AudioCodingModule *acm, RTPStream *rtpStream,
                     std::string out_file_name, size_t channels) {
  struct CodecInst recvCodec = CodecInst();
  int noOfCodecs;
  EXPECT_EQ(0, acm->InitializeReceiver());

  noOfCodecs = acm->NumberOfCodecs();
  for (int i = 0; i < noOfCodecs; i++) {
    EXPECT_EQ(0, acm->Codec(i, &recvCodec));
    if (recvCodec.channels == channels)
      EXPECT_EQ(0, acm->RegisterReceiveCodec(recvCodec));
    // Forces mono/stereo for Opus.
    if (!strcmp(recvCodec.plname, "opus")) {
      recvCodec.channels = channels;
      EXPECT_EQ(0, acm->RegisterReceiveCodec(recvCodec));
    }
  }

  int playSampFreq;
  std::string file_name;
  std::stringstream file_stream;
  file_stream << webrtc::test::OutputPath() << out_file_name
      << static_cast<int>(codeId) << ".pcm";
  file_name = file_stream.str();
  _rtpStream = rtpStream;

  if (testMode == 1) {
    playSampFreq = recvCodec.plfreq;
    _pcmFile.Open(file_name, recvCodec.plfreq, "wb+");
  } else if (testMode == 0) {
    playSampFreq = 32000;
    _pcmFile.Open(file_name, 32000, "wb+");
  } else {
    printf("\nValid output frequencies:\n");
    printf("8000\n16000\n32000\n-1,");
    printf("which means output frequency equal to received signal frequency");
    printf("\n\nChoose output sampling frequency: ");
    ASSERT_GT(scanf("%d", &playSampFreq), 0);
    file_name = webrtc::test::OutputPath() + out_file_name + ".pcm";
    _pcmFile.Open(file_name, playSampFreq, "wb+");
  }

  _realPayloadSizeBytes = 0;
  _playoutBuffer = new int16_t[WEBRTC_10MS_PCM_AUDIO];
  _frequency = playSampFreq;
  _acm = acm;
  _firstTime = true;
}

void Receiver::Teardown() {
  delete[] _playoutBuffer;
  _pcmFile.Close();
  if (testMode > 1) {
    Trace::ReturnTrace();
  }
}

bool Receiver::IncomingPacket() {
  if (!_rtpStream->EndOfFile()) {
    if (_firstTime) {
      _firstTime = false;
      _realPayloadSizeBytes = _rtpStream->Read(&_rtpInfo, _incomingPayload,
                                               _payloadSizeBytes, &_nextTime);
      if (_realPayloadSizeBytes == 0) {
        if (_rtpStream->EndOfFile()) {
          _firstTime = true;
          return true;
        } else {
          return false;
        }
      }
    }

    EXPECT_EQ(0, _acm->IncomingPacket(_incomingPayload, _realPayloadSizeBytes,
                                      _rtpInfo));
    _realPayloadSizeBytes = _rtpStream->Read(&_rtpInfo, _incomingPayload,
                                             _payloadSizeBytes, &_nextTime);
    if (_realPayloadSizeBytes == 0 && _rtpStream->EndOfFile()) {
      _firstTime = true;
    }
  }
  return true;
}

bool Receiver::PlayoutData() {
  AudioFrame audioFrame;
  bool muted;
  int32_t ok = _acm->PlayoutData10Ms(_frequency, &audioFrame, &muted);
  if (muted) {
    ADD_FAILURE();
    return false;
  }
  EXPECT_EQ(0, ok);
  if (ok < 0){
    return false;
  }
  if (_playoutLengthSmpls == 0) {
    return false;
  }
  _pcmFile.Write10MsData(audioFrame.data_,
      audioFrame.samples_per_channel_ * audioFrame.num_channels_);
  return true;
}

void Receiver::Run() {
  uint8_t counter500Ms = 50;
  uint32_t clock = 0;

  while (counter500Ms > 0) {
    if (clock == 0 || clock >= _nextTime) {
      EXPECT_TRUE(IncomingPacket());
      if (clock == 0) {
        clock = _nextTime;
      }
    }
    if ((clock % 10) == 0) {
      if (!PlayoutData()) {
        clock++;
        continue;
      }
    }
    if (_rtpStream->EndOfFile()) {
      counter500Ms--;
    }
    clock++;
  }
}

EncodeDecodeTest::EncodeDecodeTest() {
  _testMode = 2;
  Trace::CreateTrace();
  Trace::SetTraceFile(
      (webrtc::test::OutputPath() + "acm_encdec_trace.txt").c_str());
}

EncodeDecodeTest::EncodeDecodeTest(int testMode) {
  //testMode == 0 for autotest
  //testMode == 1 for testing all codecs/parameters
  //testMode > 1 for specific user-input test (as it was used before)
  _testMode = testMode;
  if (_testMode != 0) {
    Trace::CreateTrace();
    Trace::SetTraceFile(
        (webrtc::test::OutputPath() + "acm_encdec_trace.txt").c_str());
  }
}

void EncodeDecodeTest::Perform() {
  int numCodecs = 1;
  int codePars[3];  // Frequency, packet size, rate.
  int numPars[52];  // Number of codec parameters sets (freq, pacsize, rate)
                    // to test, for a given codec.

  codePars[0] = 0;
  codePars[1] = 0;
  codePars[2] = 0;

  std::unique_ptr<AudioCodingModule> acm(AudioCodingModule::Create(0));
  struct CodecInst sendCodecTmp;
  numCodecs = acm->NumberOfCodecs();

  if (_testMode != 2) {
    for (int n = 0; n < numCodecs; n++) {
      EXPECT_EQ(0, acm->Codec(n, &sendCodecTmp));
      if (STR_CASE_CMP(sendCodecTmp.plname, "telephone-event") == 0) {
        numPars[n] = 0;
      } else if (STR_CASE_CMP(sendCodecTmp.plname, "cn") == 0) {
        numPars[n] = 0;
      } else if (STR_CASE_CMP(sendCodecTmp.plname, "red") == 0) {
        numPars[n] = 0;
      } else if (sendCodecTmp.channels == 2) {
        numPars[n] = 0;
      } else {
        numPars[n] = 1;
      }
    }
  } else {
    numCodecs = 1;
    numPars[0] = 1;
  }

  _receiver.testMode = _testMode;

  // Loop over all mono codecs:
  for (int codeId = 0; codeId < numCodecs; codeId++) {
    // Only encode using real mono encoders, not telephone-event and cng.
    for (int loopPars = 1; loopPars <= numPars[codeId]; loopPars++) {
      // Encode all data to file.
      std::string fileName = EncodeToFile(1, codeId, codePars, _testMode);

      RTPFile rtpFile;
      rtpFile.Open(fileName.c_str(), "rb");

      _receiver.codeId = codeId;

      rtpFile.ReadHeader();
      _receiver.Setup(acm.get(), &rtpFile, "encodeDecode_out", 1);
      _receiver.Run();
      _receiver.Teardown();
      rtpFile.Close();
    }
  }

  // End tracing.
  if (_testMode == 1) {
    Trace::ReturnTrace();
  }
}

std::string EncodeDecodeTest::EncodeToFile(int fileType,
                                           int codeId,
                                           int* codePars,
                                           int testMode) {
  std::unique_ptr<AudioCodingModule> acm(AudioCodingModule::Create(1));
  RTPFile rtpFile;
  std::string fileName = webrtc::test::TempFilename(webrtc::test::OutputPath(),
                                                    "encode_decode_rtp");
  rtpFile.Open(fileName.c_str(), "wb+");
  rtpFile.WriteHeader();

  // Store for auto_test and logging.
  _sender.testMode = testMode;
  _sender.codeId = codeId;

  _sender.Setup(acm.get(), &rtpFile, "audio_coding/testfile32kHz", 32000, 1);
  if (acm->SendCodec()) {
    _sender.Run();
  }
  _sender.Teardown();
  rtpFile.Close();

  return fileName;
}

}  // namespace webrtc
