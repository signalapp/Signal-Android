/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>
#include <string.h>

#include <math.h>

#include "common_types.h"
#include "SpatialAudio.h"
#include "trace.h"
#include "utility.h"
#include "webrtc/test/testsupport/fileutils.h"

namespace webrtc {

#define NUM_PANN_COEFFS 10

SpatialAudio::SpatialAudio(int testMode)
    : _acmLeft(AudioCodingModule::Create(1)),
      _acmRight(AudioCodingModule::Create(2)),
      _acmReceiver(AudioCodingModule::Create(3)),
      _testMode(testMode) {
}

SpatialAudio::~SpatialAudio() {
  delete _channel;
  _inFile.Close();
  _outFile.Close();
}

int16_t SpatialAudio::Setup() {
  _channel = new Channel;

  // Register callback for the sender side.
  CHECK_ERROR(_acmLeft->RegisterTransportCallback(_channel));
  CHECK_ERROR(_acmRight->RegisterTransportCallback(_channel));
  // Register the receiver ACM in channel
  _channel->RegisterReceiverACM(_acmReceiver.get());

  uint16_t sampFreqHz = 32000;

  const std::string file_name = webrtc::test::ResourcePath(
      "audio_coding/testfile32kHz", "pcm");
  _inFile.Open(file_name, sampFreqHz, "rb", false);

  std::string output_file = webrtc::test::OutputPath()
      + "out_spatial_autotest.pcm";
  if (_testMode == 1) {
    output_file = webrtc::test::OutputPath() + "testspatial_out.pcm";
    printf("\n");
    printf("Enter the output file [%s]: ", output_file.c_str());
    PCMFile::ChooseFile(&output_file, MAX_FILE_NAME_LENGTH_BYTE, &sampFreqHz);
  } else {
    output_file = webrtc::test::OutputPath() + "testspatial_out.pcm";
  }
  _outFile.Open(output_file, sampFreqHz, "wb", false);
  _outFile.SaveStereo(true);

  // Register all available codes as receiving codecs.
  CodecInst codecInst;
  int status;
  uint8_t num_encoders = _acmReceiver->NumberOfCodecs();
  // Register all available codes as receiving codecs once more.
  for (uint8_t n = 0; n < num_encoders; n++) {
    status = _acmReceiver->Codec(n, &codecInst);
    if (status < 0) {
      printf("Error in Codec(), no matching codec found");
    }
    status = _acmReceiver->RegisterReceiveCodec(codecInst);
    if (status < 0) {
      printf("Error in RegisterReceiveCodec() for payload type %d",
             codecInst.pltype);
    }
  }

  return 0;
}

void SpatialAudio::Perform() {
  if (_testMode == 0) {
    printf("Running SpatialAudio Test");
    WEBRTC_TRACE(webrtc::kTraceStateInfo, webrtc::kTraceAudioCoding, -1,
                 "---------- SpatialAudio ----------");
  }

  Setup();

  CodecInst codecInst;
  _acmLeft->Codec((uint8_t) 1, &codecInst);
  CHECK_ERROR(_acmLeft->RegisterSendCodec(codecInst));
  EncodeDecode();

  int16_t pannCntr = 0;

  double leftPanning[NUM_PANN_COEFFS] = { 1.00, 0.95, 0.90, 0.85, 0.80, 0.75,
      0.70, 0.60, 0.55, 0.50 };
  double rightPanning[NUM_PANN_COEFFS] = { 0.50, 0.55, 0.60, 0.70, 0.75, 0.80,
      0.85, 0.90, 0.95, 1.00 };

  while ((pannCntr + 1) < NUM_PANN_COEFFS) {
    _acmLeft->Codec((uint8_t) 0, &codecInst);
    codecInst.pacsize = 480;
    CHECK_ERROR(_acmLeft->RegisterSendCodec(codecInst));
    CHECK_ERROR(_acmRight->RegisterSendCodec(codecInst));

    EncodeDecode(leftPanning[pannCntr], rightPanning[pannCntr]);
    pannCntr++;

    // Change codec
    _acmLeft->Codec((uint8_t) 3, &codecInst);
    codecInst.pacsize = 320;
    CHECK_ERROR(_acmLeft->RegisterSendCodec(codecInst));
    CHECK_ERROR(_acmRight->RegisterSendCodec(codecInst));

    EncodeDecode(leftPanning[pannCntr], rightPanning[pannCntr]);
    pannCntr++;
    if (_testMode == 0) {
      printf(".");
    }
  }

  _acmLeft->Codec((uint8_t) 4, &codecInst);
  CHECK_ERROR(_acmLeft->RegisterSendCodec(codecInst));
  EncodeDecode();

  _acmLeft->Codec((uint8_t) 0, &codecInst);
  codecInst.pacsize = 480;
  CHECK_ERROR(_acmLeft->RegisterSendCodec(codecInst));
  CHECK_ERROR(_acmRight->RegisterSendCodec(codecInst));
  pannCntr = NUM_PANN_COEFFS - 1;
  while (pannCntr >= 0) {
    EncodeDecode(leftPanning[pannCntr], rightPanning[pannCntr]);
    pannCntr--;
    if (_testMode == 0) {
      printf(".");
    }
  }
  if (_testMode == 0) {
    printf("Done!\n");
  }
}

void SpatialAudio::EncodeDecode(const double leftPanning,
                                const double rightPanning) {
  AudioFrame audioFrame;
  int32_t outFileSampFreq = _outFile.SamplingFrequency();

  const double rightToLeftRatio = rightPanning / leftPanning;

  _channel->SetIsStereo(true);

  while (!_inFile.EndOfFile()) {
    _inFile.Read10MsData(audioFrame);
    for (int n = 0; n < audioFrame.samples_per_channel_; n++) {
      audioFrame.data_[n] = (int16_t) floor(
          audioFrame.data_[n] * leftPanning + 0.5);
    }
    CHECK_ERROR(_acmLeft->Add10MsData(audioFrame));

    for (int n = 0; n < audioFrame.samples_per_channel_; n++) {
      audioFrame.data_[n] = (int16_t) floor(
          audioFrame.data_[n] * rightToLeftRatio + 0.5);
    }
    CHECK_ERROR(_acmRight->Add10MsData(audioFrame));

    CHECK_ERROR(_acmLeft->Process());
    CHECK_ERROR(_acmRight->Process());

    CHECK_ERROR(_acmReceiver->PlayoutData10Ms(outFileSampFreq, &audioFrame));
    _outFile.Write10MsData(audioFrame);
  }
  _inFile.Rewind();
}

void SpatialAudio::EncodeDecode() {
  AudioFrame audioFrame;
  int32_t outFileSampFreq = _outFile.SamplingFrequency();

  _channel->SetIsStereo(false);

  while (!_inFile.EndOfFile()) {
    _inFile.Read10MsData(audioFrame);
    CHECK_ERROR(_acmLeft->Add10MsData(audioFrame));

    CHECK_ERROR(_acmLeft->Process());

    CHECK_ERROR(_acmReceiver->PlayoutData10Ms(outFileSampFreq, &audioFrame));
    _outFile.Write10MsData(audioFrame);
  }
  _inFile.Rewind();
}

}  // namespace webrtc
