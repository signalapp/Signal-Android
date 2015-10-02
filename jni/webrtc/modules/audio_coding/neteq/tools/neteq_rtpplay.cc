/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// TODO(hlundin): The functionality in this file should be moved into one or
// several classes.

#include <assert.h>
#include <stdio.h>

#include <algorithm>
#include <iostream>
#include <string>

#include "google/gflags.h"
#include "webrtc/modules/audio_coding/codecs/pcm16b/include/pcm16b.h"
#include "webrtc/modules/audio_coding/neteq/interface/neteq.h"
#include "webrtc/modules/audio_coding/neteq/tools/input_audio_file.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet.h"
#include "webrtc/modules/audio_coding/neteq/tools/rtp_file_source.h"
#include "webrtc/modules/interface/module_common_types.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"
#include "webrtc/system_wrappers/interface/trace.h"
#include "webrtc/test/testsupport/fileutils.h"
#include "webrtc/typedefs.h"

using webrtc::NetEq;
using webrtc::WebRtcRTPHeader;

// Flag validators.
static bool ValidatePayloadType(const char* flagname, int32_t value) {
  if (value >= 0 && value <= 127)  // Value is ok.
    return true;
  printf("Invalid value for --%s: %d\n", flagname, static_cast<int>(value));
  return false;
}

// Define command line flags.
DEFINE_int32(pcmu, 0, "RTP payload type for PCM-u");
static const bool pcmu_dummy =
    google::RegisterFlagValidator(&FLAGS_pcmu, &ValidatePayloadType);
DEFINE_int32(pcma, 8, "RTP payload type for PCM-a");
static const bool pcma_dummy =
    google::RegisterFlagValidator(&FLAGS_pcma, &ValidatePayloadType);
DEFINE_int32(ilbc, 102, "RTP payload type for iLBC");
static const bool ilbc_dummy =
    google::RegisterFlagValidator(&FLAGS_ilbc, &ValidatePayloadType);
DEFINE_int32(isac, 103, "RTP payload type for iSAC");
static const bool isac_dummy =
    google::RegisterFlagValidator(&FLAGS_isac, &ValidatePayloadType);
DEFINE_int32(isac_swb, 104, "RTP payload type for iSAC-swb (32 kHz)");
static const bool isac_swb_dummy =
    google::RegisterFlagValidator(&FLAGS_isac_swb, &ValidatePayloadType);
DEFINE_int32(pcm16b, 93, "RTP payload type for PCM16b-nb (8 kHz)");
static const bool pcm16b_dummy =
    google::RegisterFlagValidator(&FLAGS_pcm16b, &ValidatePayloadType);
DEFINE_int32(pcm16b_wb, 94, "RTP payload type for PCM16b-wb (16 kHz)");
static const bool pcm16b_wb_dummy =
    google::RegisterFlagValidator(&FLAGS_pcm16b_wb, &ValidatePayloadType);
DEFINE_int32(pcm16b_swb32, 95, "RTP payload type for PCM16b-swb32 (32 kHz)");
static const bool pcm16b_swb32_dummy =
    google::RegisterFlagValidator(&FLAGS_pcm16b_swb32, &ValidatePayloadType);
DEFINE_int32(pcm16b_swb48, 96, "RTP payload type for PCM16b-swb48 (48 kHz)");
static const bool pcm16b_swb48_dummy =
    google::RegisterFlagValidator(&FLAGS_pcm16b_swb48, &ValidatePayloadType);
DEFINE_int32(g722, 9, "RTP payload type for G.722");
static const bool g722_dummy =
    google::RegisterFlagValidator(&FLAGS_g722, &ValidatePayloadType);
DEFINE_int32(avt, 106, "RTP payload type for AVT/DTMF");
static const bool avt_dummy =
    google::RegisterFlagValidator(&FLAGS_avt, &ValidatePayloadType);
DEFINE_int32(red, 117, "RTP payload type for redundant audio (RED)");
static const bool red_dummy =
    google::RegisterFlagValidator(&FLAGS_red, &ValidatePayloadType);
DEFINE_int32(cn_nb, 13, "RTP payload type for comfort noise (8 kHz)");
static const bool cn_nb_dummy =
    google::RegisterFlagValidator(&FLAGS_cn_nb, &ValidatePayloadType);
DEFINE_int32(cn_wb, 98, "RTP payload type for comfort noise (16 kHz)");
static const bool cn_wb_dummy =
    google::RegisterFlagValidator(&FLAGS_cn_wb, &ValidatePayloadType);
DEFINE_int32(cn_swb32, 99, "RTP payload type for comfort noise (32 kHz)");
static const bool cn_swb32_dummy =
    google::RegisterFlagValidator(&FLAGS_cn_swb32, &ValidatePayloadType);
DEFINE_int32(cn_swb48, 100, "RTP payload type for comfort noise (48 kHz)");
static const bool cn_swb48_dummy =
    google::RegisterFlagValidator(&FLAGS_cn_swb48, &ValidatePayloadType);
DEFINE_bool(codec_map, false, "Prints the mapping between RTP payload type and "
    "codec");
DEFINE_string(replacement_audio_file, "",
              "A PCM file that will be used to populate ""dummy"" RTP packets");

// Declaring helper functions (defined further down in this file).
std::string CodecName(webrtc::NetEqDecoder codec);
void RegisterPayloadTypes(NetEq* neteq);
void PrintCodecMapping();
size_t ReplacePayload(webrtc::test::InputAudioFile* replacement_audio_file,
                      webrtc::scoped_ptr<int16_t[]>* replacement_audio,
                      webrtc::scoped_ptr<uint8_t[]>* payload,
                      size_t* payload_mem_size_bytes,
                      size_t* frame_size_samples,
                      WebRtcRTPHeader* rtp_header,
                      const webrtc::test::Packet* next_packet);
int CodecSampleRate(uint8_t payload_type);
int CodecTimestampRate(uint8_t payload_type);
bool IsComfortNosie(uint8_t payload_type);

int main(int argc, char* argv[]) {
  static const int kMaxChannels = 5;
  static const int kMaxSamplesPerMs = 48000 / 1000;
  static const int kOutputBlockSizeMs = 10;

  std::string program_name = argv[0];
  std::string usage = "Tool for decoding an RTP dump file using NetEq.\n"
      "Run " + program_name + " --helpshort for usage.\n"
      "Example usage:\n" + program_name +
      " input.rtp output.pcm\n";
  google::SetUsageMessage(usage);
  google::ParseCommandLineFlags(&argc, &argv, true);

  if (FLAGS_codec_map) {
    PrintCodecMapping();
  }

  if (argc != 3) {
    if (FLAGS_codec_map) {
      // We have already printed the codec map. Just end the program.
      return 0;
    }
    // Print usage information.
    std::cout << google::ProgramUsage();
    return 0;
  }

  printf("Input file: %s\n", argv[1]);
  webrtc::scoped_ptr<webrtc::test::RtpFileSource> file_source(
      webrtc::test::RtpFileSource::Create(argv[1]));
  assert(file_source.get());

  FILE* out_file = fopen(argv[2], "wb");
  if (!out_file) {
    std::cerr << "Cannot open output file " << argv[2] << std::endl;
    exit(1);
  }
  std::cout << "Output file: " << argv[2] << std::endl;

  // Check if a replacement audio file was provided, and if so, open it.
  bool replace_payload = false;
  webrtc::scoped_ptr<webrtc::test::InputAudioFile> replacement_audio_file;
  if (!FLAGS_replacement_audio_file.empty()) {
    replacement_audio_file.reset(
        new webrtc::test::InputAudioFile(FLAGS_replacement_audio_file));
    replace_payload = true;
  }

  // Enable tracing.
  webrtc::Trace::CreateTrace();
  webrtc::Trace::SetTraceFile((webrtc::test::OutputPath() +
      "neteq_trace.txt").c_str());
  webrtc::Trace::set_level_filter(webrtc::kTraceAll);

  // Initialize NetEq instance.
  int sample_rate_hz = 16000;
  NetEq::Config config;
  config.sample_rate_hz = sample_rate_hz;
  NetEq* neteq = NetEq::Create(config);
  RegisterPayloadTypes(neteq);

  // Read first packet.
  if (file_source->EndOfFile()) {
    printf("Warning: RTP file is empty");
    webrtc::Trace::ReturnTrace();
    return 0;
  }
  webrtc::scoped_ptr<webrtc::test::Packet> packet(file_source->NextPacket());
  bool packet_available = true;

  // Set up variables for audio replacement if needed.
  webrtc::scoped_ptr<webrtc::test::Packet> next_packet;
  bool next_packet_available = false;
  size_t input_frame_size_timestamps = 0;
  webrtc::scoped_ptr<int16_t[]> replacement_audio;
  webrtc::scoped_ptr<uint8_t[]> payload;
  size_t payload_mem_size_bytes = 0;
  if (replace_payload) {
    // Initially assume that the frame size is 30 ms at the initial sample rate.
    // This value will be replaced with the correct one as soon as two
    // consecutive packets are found.
    input_frame_size_timestamps = 30 * sample_rate_hz / 1000;
    replacement_audio.reset(new int16_t[input_frame_size_timestamps]);
    payload_mem_size_bytes = 2 * input_frame_size_timestamps;
    payload.reset(new uint8_t[payload_mem_size_bytes]);
    assert(!file_source->EndOfFile());
    next_packet.reset(file_source->NextPacket());
    next_packet_available = true;
  }

  // This is the main simulation loop.
  // Set the simulation clock to start immediately with the first packet.
  int time_now_ms = packet->time_ms();
  int next_input_time_ms = time_now_ms;
  int next_output_time_ms = time_now_ms;
  if (time_now_ms % kOutputBlockSizeMs != 0) {
    // Make sure that next_output_time_ms is rounded up to the next multiple
    // of kOutputBlockSizeMs. (Legacy bit-exactness.)
    next_output_time_ms +=
        kOutputBlockSizeMs - time_now_ms % kOutputBlockSizeMs;
  }
  while (packet_available) {
    // Check if it is time to insert packet.
    while (time_now_ms >= next_input_time_ms && packet_available) {
      assert(packet->virtual_payload_length_bytes() > 0);
      // Parse RTP header.
      WebRtcRTPHeader rtp_header;
      packet->ConvertHeader(&rtp_header);
      const uint8_t* payload_ptr = packet->payload();
      size_t payload_len = packet->payload_length_bytes();
      if (replace_payload) {
        payload_len = ReplacePayload(replacement_audio_file.get(),
                                     &replacement_audio,
                                     &payload,
                                     &payload_mem_size_bytes,
                                     &input_frame_size_timestamps,
                                     &rtp_header,
                                     next_packet.get());
        payload_ptr = payload.get();
      }
      int error =
          neteq->InsertPacket(rtp_header,
                              payload_ptr,
                              static_cast<int>(payload_len),
                              packet->time_ms() * sample_rate_hz / 1000);
      if (error != NetEq::kOK) {
        std::cerr << "InsertPacket returned error code " << neteq->LastError()
                  << std::endl;
      }

      // Get next packet from file.
      if (!file_source->EndOfFile()) {
        packet.reset(file_source->NextPacket());
      } else {
        packet_available = false;
      }
      if (replace_payload) {
        // At this point |packet| contains the packet *after* |next_packet|.
        // Swap Packet objects between |packet| and |next_packet|.
        packet.swap(next_packet);
        // Swap the status indicators unless they're already the same.
        if (packet_available != next_packet_available) {
          packet_available = !packet_available;
          next_packet_available = !next_packet_available;
        }
      }
      next_input_time_ms = packet->time_ms();
    }

    // Check if it is time to get output audio.
    if (time_now_ms >= next_output_time_ms) {
      static const int kOutDataLen = kOutputBlockSizeMs * kMaxSamplesPerMs *
          kMaxChannels;
      int16_t out_data[kOutDataLen];
      int num_channels;
      int samples_per_channel;
      int error = neteq->GetAudio(kOutDataLen, out_data, &samples_per_channel,
                                   &num_channels, NULL);
      if (error != NetEq::kOK) {
        std::cerr << "GetAudio returned error code " <<
            neteq->LastError() << std::endl;
      } else {
        // Calculate sample rate from output size.
        sample_rate_hz = 1000 * samples_per_channel / kOutputBlockSizeMs;
      }

      // Write to file.
      // TODO(hlundin): Make writing to file optional.
      size_t write_len = samples_per_channel * num_channels;
      if (fwrite(out_data, sizeof(out_data[0]), write_len, out_file) !=
          write_len) {
        std::cerr << "Error while writing to file" << std::endl;
        webrtc::Trace::ReturnTrace();
        exit(1);
      }
      next_output_time_ms += kOutputBlockSizeMs;
    }
    // Advance time to next event.
    time_now_ms = std::min(next_input_time_ms, next_output_time_ms);
  }

  std::cout << "Simulation done" << std::endl;

  fclose(out_file);
  delete neteq;
  webrtc::Trace::ReturnTrace();
  return 0;
}


// Help functions.

// Maps a codec type to a printable name string.
std::string CodecName(webrtc::NetEqDecoder codec) {
  switch (codec) {
    case webrtc::kDecoderPCMu:
      return "PCM-u";
    case webrtc::kDecoderPCMa:
      return "PCM-a";
    case webrtc::kDecoderILBC:
      return "iLBC";
    case webrtc::kDecoderISAC:
      return "iSAC";
    case webrtc::kDecoderISACswb:
      return "iSAC-swb (32 kHz)";
    case webrtc::kDecoderPCM16B:
      return "PCM16b-nb (8 kHz)";
    case webrtc::kDecoderPCM16Bwb:
      return "PCM16b-wb (16 kHz)";
    case webrtc::kDecoderPCM16Bswb32kHz:
      return "PCM16b-swb32 (32 kHz)";
    case webrtc::kDecoderPCM16Bswb48kHz:
      return "PCM16b-swb48 (48 kHz)";
    case webrtc::kDecoderG722:
      return "G.722";
    case webrtc::kDecoderRED:
      return "redundant audio (RED)";
    case webrtc::kDecoderAVT:
      return "AVT/DTMF";
    case webrtc::kDecoderCNGnb:
      return "comfort noise (8 kHz)";
    case webrtc::kDecoderCNGwb:
      return "comfort noise (16 kHz)";
    case webrtc::kDecoderCNGswb32kHz:
      return "comfort noise (32 kHz)";
    case webrtc::kDecoderCNGswb48kHz:
      return "comfort noise (48 kHz)";
    default:
      assert(false);
      return "undefined";
  }
}

// Registers all decoders in |neteq|.
void RegisterPayloadTypes(NetEq* neteq) {
  assert(neteq);
  int error;
  error = neteq->RegisterPayloadType(webrtc::kDecoderPCMu, FLAGS_pcmu);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_pcmu <<
        " as " << CodecName(webrtc::kDecoderPCMu).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderPCMa, FLAGS_pcma);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_pcma <<
        " as " << CodecName(webrtc::kDecoderPCMa).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderILBC, FLAGS_ilbc);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_ilbc <<
        " as " << CodecName(webrtc::kDecoderILBC).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderISAC, FLAGS_isac);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_isac <<
        " as " << CodecName(webrtc::kDecoderISAC).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderISACswb, FLAGS_isac_swb);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_isac_swb <<
        " as " << CodecName(webrtc::kDecoderISACswb).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderPCM16B, FLAGS_pcm16b);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_pcm16b <<
        " as " << CodecName(webrtc::kDecoderPCM16B).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderPCM16Bwb,
                                      FLAGS_pcm16b_wb);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_pcm16b_wb <<
        " as " << CodecName(webrtc::kDecoderPCM16Bwb).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderPCM16Bswb32kHz,
                                      FLAGS_pcm16b_swb32);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_pcm16b_swb32 <<
        " as " << CodecName(webrtc::kDecoderPCM16Bswb32kHz).c_str() <<
        std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderPCM16Bswb48kHz,
                                      FLAGS_pcm16b_swb48);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_pcm16b_swb48 <<
        " as " << CodecName(webrtc::kDecoderPCM16Bswb48kHz).c_str() <<
        std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderG722, FLAGS_g722);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_g722 <<
        " as " << CodecName(webrtc::kDecoderG722).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderAVT, FLAGS_avt);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_avt <<
        " as " << CodecName(webrtc::kDecoderAVT).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderRED, FLAGS_red);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_red <<
        " as " << CodecName(webrtc::kDecoderRED).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderCNGnb, FLAGS_cn_nb);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_cn_nb <<
        " as " << CodecName(webrtc::kDecoderCNGnb).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderCNGwb, FLAGS_cn_wb);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_cn_wb <<
        " as " << CodecName(webrtc::kDecoderCNGwb).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderCNGswb32kHz,
                                      FLAGS_cn_swb32);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_cn_swb32 <<
        " as " << CodecName(webrtc::kDecoderCNGswb32kHz).c_str() << std::endl;
    exit(1);
  }
  error = neteq->RegisterPayloadType(webrtc::kDecoderCNGswb48kHz,
                                     FLAGS_cn_swb48);
  if (error) {
    std::cerr << "Cannot register payload type " << FLAGS_cn_swb48 <<
        " as " << CodecName(webrtc::kDecoderCNGswb48kHz).c_str() << std::endl;
    exit(1);
  }
}

void PrintCodecMapping() {
  std::cout << CodecName(webrtc::kDecoderPCMu).c_str() << ": " << FLAGS_pcmu <<
      std::endl;
  std::cout << CodecName(webrtc::kDecoderPCMa).c_str() << ": " << FLAGS_pcma <<
      std::endl;
  std::cout << CodecName(webrtc::kDecoderILBC).c_str() << ": " << FLAGS_ilbc <<
      std::endl;
  std::cout << CodecName(webrtc::kDecoderISAC).c_str() << ": " << FLAGS_isac <<
      std::endl;
  std::cout << CodecName(webrtc::kDecoderISACswb).c_str() << ": " <<
      FLAGS_isac_swb << std::endl;
  std::cout << CodecName(webrtc::kDecoderPCM16B).c_str() << ": " <<
      FLAGS_pcm16b << std::endl;
  std::cout << CodecName(webrtc::kDecoderPCM16Bwb).c_str() << ": " <<
      FLAGS_pcm16b_wb << std::endl;
  std::cout << CodecName(webrtc::kDecoderPCM16Bswb32kHz).c_str() << ": " <<
      FLAGS_pcm16b_swb32 << std::endl;
  std::cout << CodecName(webrtc::kDecoderPCM16Bswb48kHz).c_str() << ": " <<
      FLAGS_pcm16b_swb48 << std::endl;
  std::cout << CodecName(webrtc::kDecoderG722).c_str() << ": " << FLAGS_g722 <<
      std::endl;
  std::cout << CodecName(webrtc::kDecoderAVT).c_str() << ": " << FLAGS_avt <<
      std::endl;
  std::cout << CodecName(webrtc::kDecoderRED).c_str() << ": " << FLAGS_red <<
      std::endl;
  std::cout << CodecName(webrtc::kDecoderCNGnb).c_str() << ": " <<
      FLAGS_cn_nb << std::endl;
  std::cout << CodecName(webrtc::kDecoderCNGwb).c_str() << ": " <<
      FLAGS_cn_wb << std::endl;
  std::cout << CodecName(webrtc::kDecoderCNGswb32kHz).c_str() << ": " <<
      FLAGS_cn_swb32 << std::endl;
  std::cout << CodecName(webrtc::kDecoderCNGswb48kHz).c_str() << ": " <<
      FLAGS_cn_swb48 << std::endl;
}

size_t ReplacePayload(webrtc::test::InputAudioFile* replacement_audio_file,
                      webrtc::scoped_ptr<int16_t[]>* replacement_audio,
                      webrtc::scoped_ptr<uint8_t[]>* payload,
                      size_t* payload_mem_size_bytes,
                      size_t* frame_size_samples,
                      WebRtcRTPHeader* rtp_header,
                      const webrtc::test::Packet* next_packet) {
  size_t payload_len = 0;
  // Check for CNG.
  if (IsComfortNosie(rtp_header->header.payloadType)) {
    // If CNG, simply insert a zero-energy one-byte payload.
    if (*payload_mem_size_bytes < 1) {
      (*payload).reset(new uint8_t[1]);
      *payload_mem_size_bytes = 1;
    }
    (*payload)[0] = 127;  // Max attenuation of CNG.
    payload_len = 1;
  } else {
    assert(next_packet->virtual_payload_length_bytes() > 0);
    // Check if payload length has changed.
    if (next_packet->header().sequenceNumber ==
        rtp_header->header.sequenceNumber + 1) {
      if (*frame_size_samples !=
          next_packet->header().timestamp - rtp_header->header.timestamp) {
        *frame_size_samples =
            next_packet->header().timestamp - rtp_header->header.timestamp;
        (*replacement_audio).reset(
            new int16_t[*frame_size_samples]);
        *payload_mem_size_bytes = 2 * *frame_size_samples;
        (*payload).reset(new uint8_t[*payload_mem_size_bytes]);
      }
    }
    // Get new speech.
    assert((*replacement_audio).get());
    if (CodecTimestampRate(rtp_header->header.payloadType) !=
        CodecSampleRate(rtp_header->header.payloadType) ||
        rtp_header->header.payloadType == FLAGS_red ||
        rtp_header->header.payloadType == FLAGS_avt) {
      // Some codecs have different sample and timestamp rates. And neither
      // RED nor DTMF is supported for replacement.
      std::cerr << "Codec not supported for audio replacement." <<
          std::endl;
      webrtc::Trace::ReturnTrace();
      exit(1);
    }
    assert(*frame_size_samples > 0);
    if (!replacement_audio_file->Read(*frame_size_samples,
                                      (*replacement_audio).get())) {
      std::cerr << "Could not read replacement audio file." << std::endl;
      webrtc::Trace::ReturnTrace();
      exit(1);
    }
    // Encode it as PCM16.
    assert((*payload).get());
    payload_len = WebRtcPcm16b_Encode((*replacement_audio).get(),
                                      static_cast<int16_t>(*frame_size_samples),
                                      (*payload).get());
    assert(payload_len == 2 * *frame_size_samples);
    // Change payload type to PCM16.
    switch (CodecSampleRate(rtp_header->header.payloadType)) {
      case 8000:
        rtp_header->header.payloadType = FLAGS_pcm16b;
        break;
      case 16000:
        rtp_header->header.payloadType = FLAGS_pcm16b_wb;
        break;
      case 32000:
        rtp_header->header.payloadType = FLAGS_pcm16b_swb32;
        break;
      case 48000:
        rtp_header->header.payloadType = FLAGS_pcm16b_swb48;
        break;
      default:
        std::cerr << "Payload type " <<
            static_cast<int>(rtp_header->header.payloadType) <<
            " not supported or unknown." << std::endl;
        webrtc::Trace::ReturnTrace();
        exit(1);
    }
  }
  return payload_len;
}

int CodecSampleRate(uint8_t payload_type) {
  if (payload_type == FLAGS_pcmu ||
      payload_type == FLAGS_pcma ||
      payload_type == FLAGS_ilbc ||
      payload_type == FLAGS_pcm16b ||
      payload_type == FLAGS_cn_nb) {
    return 8000;
  } else if (payload_type == FLAGS_isac ||
      payload_type == FLAGS_pcm16b_wb ||
      payload_type == FLAGS_g722 ||
      payload_type == FLAGS_cn_wb) {
    return 16000;
  } else if (payload_type == FLAGS_isac_swb ||
      payload_type == FLAGS_pcm16b_swb32 ||
      payload_type == FLAGS_cn_swb32) {
    return 32000;
  } else if (payload_type == FLAGS_pcm16b_swb48 ||
      payload_type == FLAGS_cn_swb48) {
    return 48000;
  } else if (payload_type == FLAGS_avt ||
      payload_type == FLAGS_red) {
      return 0;
  } else {
    return -1;
  }
}

int CodecTimestampRate(uint8_t payload_type) {
  if (payload_type == FLAGS_g722) {
    return 8000;
  } else {
    return CodecSampleRate(payload_type);
  }
}

bool IsComfortNosie(uint8_t payload_type) {
  if (payload_type == FLAGS_cn_nb ||
      payload_type == FLAGS_cn_wb ||
      payload_type == FLAGS_cn_swb32 ||
      payload_type == FLAGS_cn_swb48) {
    return true;
  } else {
    return false;
  }
}
