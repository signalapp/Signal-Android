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

#include <algorithm>
#include <vector>

#include "webrtc/modules/audio_coding/neteq/test/NETEQTEST_DummyRTPpacket.h"
#include "webrtc/modules/audio_coding/neteq/test/NETEQTEST_RTPpacket.h"

#define FIRSTLINELEN 40
//#define WEBRTC_DUMMY_RTP

static bool pktCmp(NETEQTEST_RTPpacket *a, NETEQTEST_RTPpacket *b) {
  return (a->time() < b->time());
}

int main(int argc, char* argv[]) {
  FILE* in_file = fopen(argv[1], "rb");
  if (!in_file) {
    printf("Cannot open input file %s\n", argv[1]);
    return -1;
  }
  printf("Input RTP file: %s\n", argv[1]);

  FILE* stat_file = fopen(argv[2], "rt");
  if (!stat_file) {
    printf("Cannot open timing file %s\n", argv[2]);
    return -1;
  }
  printf("Timing file: %s\n", argv[2]);

  FILE* out_file = fopen(argv[3], "wb");
  if (!out_file) {
    printf("Cannot open output file %s\n", argv[3]);
    return -1;
  }
  printf("Output RTP file: %s\n\n", argv[3]);

  // Read all statistics and insert into map.
  // Read first line.
  char temp_str[100];
  if (fgets(temp_str, 100, stat_file) == NULL) {
    printf("Failed to read timing file %s\n", argv[2]);
    return -1;
  }
  // Define map.
  std::map<std::pair<uint16_t, uint32_t>, uint32_t> packet_stats;
  uint16_t seq_no;
  uint32_t ts;
  uint32_t send_time;

  while (fscanf(stat_file,
                "%hu %u %u %*i %*i\n", &seq_no, &ts, &send_time) == 3) {
    std::pair<uint16_t, uint32_t>
        temp_pair = std::pair<uint16_t, uint32_t>(seq_no, ts);

    packet_stats[temp_pair] = send_time;
  }

  fclose(stat_file);

  // Read file header and write directly to output file.
  char first_line[FIRSTLINELEN];
  if (fgets(first_line, FIRSTLINELEN, in_file) == NULL) {
    printf("Failed to read first line of input file %s\n", argv[1]);
    return -1;
  }
  fputs(first_line, out_file);
  // start_sec + start_usec + source + port + padding
  const unsigned int kRtpDumpHeaderSize = 4 + 4 + 4 + 2 + 2;
  if (fread(first_line, 1, kRtpDumpHeaderSize, in_file)
      != kRtpDumpHeaderSize) {
    printf("Failed to read RTP dump header from input file %s\n", argv[1]);
    return -1;
  }
  if (fwrite(first_line, 1, kRtpDumpHeaderSize, out_file)
      != kRtpDumpHeaderSize) {
    printf("Failed to write RTP dump header to output file %s\n", argv[3]);
    return -1;
  }

  std::vector<NETEQTEST_RTPpacket *> packet_vec;

  while (1) {
    // Insert in vector.
#ifdef WEBRTC_DUMMY_RTP
    NETEQTEST_RTPpacket *new_packet = new NETEQTEST_DummyRTPpacket();
#else
    NETEQTEST_RTPpacket *new_packet = new NETEQTEST_RTPpacket();
#endif
    if (new_packet->readFromFile(in_file) < 0) {
      // End of file.
      break;
    }

    // Look for new send time in statistics vector.
    std::pair<uint16_t, uint32_t> temp_pair =
        std::pair<uint16_t, uint32_t>(new_packet->sequenceNumber(),
                                      new_packet->timeStamp());

    uint32_t new_send_time = packet_stats[temp_pair];
    new_packet->setTime(new_send_time);  // Set new send time.
    packet_vec.push_back(new_packet);  // Insert in vector.
  }

  // Sort the vector according to send times.
  std::sort(packet_vec.begin(), packet_vec.end(), pktCmp);

  std::vector<NETEQTEST_RTPpacket *>::iterator it;
  for (it = packet_vec.begin(); it != packet_vec.end(); it++) {
    // Write to out file.
    if ((*it)->writeToFile(out_file) < 0) {
      printf("Error writing to file\n");
      return -1;
    }
    // Delete packet.
    delete *it;
  }

  fclose(in_file);
  fclose(out_file);

  return 0;
}
