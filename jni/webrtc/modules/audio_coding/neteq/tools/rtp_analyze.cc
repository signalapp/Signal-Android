/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <assert.h>
#include <stdio.h>
#include <vector>

#include "google/gflags.h"
#include "webrtc/modules/audio_coding/neteq/tools/packet.h"
#include "webrtc/modules/audio_coding/neteq/tools/rtp_file_source.h"
#include "webrtc/system_wrappers/interface/scoped_ptr.h"

// Flag validator.
static bool ValidatePayloadType(const char* flagname, int32_t value) {
  if (value >= 0 && value <= 127)  // Value is ok.
    return true;
  printf("Invalid value for --%s: %d\n", flagname, static_cast<int>(value));
  return false;
}
static bool ValidateExtensionId(const char* flagname, int32_t value) {
  if (value > 0 && value <= 255)  // Value is ok.
    return true;
  printf("Invalid value for --%s: %d\n", flagname, static_cast<int>(value));
  return false;
}

// Define command line flags.
DEFINE_int32(red, 117, "RTP payload type for RED");
static const bool red_dummy =
    google::RegisterFlagValidator(&FLAGS_red, &ValidatePayloadType);
DEFINE_int32(audio_level, 1, "Extension ID for audio level (RFC 6464)");
static const bool audio_level_dummy =
    google::RegisterFlagValidator(&FLAGS_audio_level, &ValidateExtensionId);

int main(int argc, char* argv[]) {
  std::string program_name = argv[0];
  std::string usage =
      "Tool for parsing an RTP dump file to text output.\n"
      "Run " +
      program_name +
      " --helpshort for usage.\n"
      "Example usage:\n" +
      program_name + " input.rtp output.txt\n\n" +
      "Output is sent to stdout if no output file is given." +
      "Note that this tool can read files with our without payloads.";
  google::SetUsageMessage(usage);
  google::ParseCommandLineFlags(&argc, &argv, true);

  if (argc != 2 && argc != 3) {
    // Print usage information.
    printf("%s", google::ProgramUsage());
    return 0;
  }

  printf("Input file: %s\n", argv[1]);
  webrtc::scoped_ptr<webrtc::test::RtpFileSource> file_source(
      webrtc::test::RtpFileSource::Create(argv[1]));
  assert(file_source.get());
  // Set RTP extension ID.
  bool print_audio_level = false;
  if (!google::GetCommandLineFlagInfoOrDie("audio_level").is_default) {
    print_audio_level = true;
    file_source->RegisterRtpHeaderExtension(webrtc::kRtpExtensionAudioLevel,
                                            FLAGS_audio_level);
  }

  FILE* out_file;
  if (argc == 3) {
    out_file = fopen(argv[2], "wt");
    if (!out_file) {
      printf("Cannot open output file %s\n", argv[2]);
      return -1;
    }
    printf("Output file: %s\n\n", argv[2]);
  } else {
    out_file = stdout;
  }

  // Print file header.
  fprintf(out_file, "SeqNo  TimeStamp   SendTime  Size    PT  M       SSRC");
  if (print_audio_level) {
    fprintf(out_file, " AuLvl (V)");
  }
  fprintf(out_file, "\n");

  webrtc::scoped_ptr<webrtc::test::Packet> packet;
  while (!file_source->EndOfFile()) {
    packet.reset(file_source->NextPacket());
    if (!packet.get()) {
      // This is probably an RTCP packet. Move on to the next one.
      continue;
    }
    assert(packet.get());
    // Write packet data to file.
    fprintf(out_file,
            "%5u %10u %10u %5i %5i %2i %#08X",
            packet->header().sequenceNumber,
            packet->header().timestamp,
            static_cast<unsigned int>(packet->time_ms()),
            static_cast<int>(packet->packet_length_bytes()),
            packet->header().payloadType,
            packet->header().markerBit,
            packet->header().ssrc);
    if (print_audio_level && packet->header().extension.hasAudioLevel) {
      // |audioLevel| consists of one bit for "V" and then 7 bits level.
      fprintf(out_file,
              " %5u (%1i)",
              packet->header().extension.audioLevel & 0x7F,
              (packet->header().extension.audioLevel & 0x80) == 0 ? 0 : 1);
    }
    fprintf(out_file, "\n");

    if (packet->header().payloadType == FLAGS_red) {
      std::list<webrtc::RTPHeader*> red_headers;
      packet->ExtractRedHeaders(&red_headers);
      while (!red_headers.empty()) {
        webrtc::RTPHeader* red = red_headers.front();
        assert(red);
        fprintf(out_file,
                "* %5u %10u %10u %5i\n",
                red->sequenceNumber,
                red->timestamp,
                static_cast<unsigned int>(packet->time_ms()),
                red->payloadType);
        red_headers.pop_front();
        delete red;
      }
    }
  }

  fclose(out_file);

  return 0;
}
