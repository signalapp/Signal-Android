/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
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

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/test/NETEQTEST_RTPpacket.h"

#define FIRSTLINELEN 40

int main(int argc, char* argv[]) {
  if (argc < 4 || argc > 6) {
    printf(
        "Usage: RTPtimeshift in.rtp out.rtp newStartTS [newStartSN "
        "[newStartArrTime]]\n");
    exit(1);
  }

  FILE* inFile = fopen(argv[1], "rb");
  if (!inFile) {
    printf("Cannot open input file %s\n", argv[1]);
    return (-1);
  }
  printf("Input RTP file: %s\n", argv[1]);

  FILE* outFile = fopen(argv[2], "wb");
  if (!outFile) {
    printf("Cannot open output file %s\n", argv[2]);
    return (-1);
  }
  printf("Output RTP file: %s\n\n", argv[2]);

  // Read file header and write directly to output file.
  const unsigned int kRtpDumpHeaderSize = 4 + 4 + 4 + 2 + 2;
  char firstline[FIRSTLINELEN];
  EXPECT_TRUE(fgets(firstline, FIRSTLINELEN, inFile) != NULL);
  EXPECT_GT(fputs(firstline, outFile), 0);
  EXPECT_EQ(kRtpDumpHeaderSize,
            fread(firstline, 1, kRtpDumpHeaderSize, inFile));
  EXPECT_EQ(kRtpDumpHeaderSize,
            fwrite(firstline, 1, kRtpDumpHeaderSize, outFile));
  NETEQTEST_RTPpacket packet;
  int packLen = packet.readFromFile(inFile);
  if (packLen < 0) {
    exit(1);
  }

  // Get new start TS and start SeqNo from arguments.
  uint32_t TSdiff = atoi(argv[3]) - packet.timeStamp();
  uint16_t SNdiff = 0;
  uint32_t ATdiff = 0;
  if (argc > 4) {
    int startSN = atoi(argv[4]);
    if (startSN >= 0)
      SNdiff = startSN - packet.sequenceNumber();
    if (argc > 5) {
      int startTS = atoi(argv[5]);
      if (startTS >= 0)
        ATdiff = startTS - packet.time();
    }
  }

  while (packLen >= 0) {
    packet.setTimeStamp(packet.timeStamp() + TSdiff);
    packet.setSequenceNumber(packet.sequenceNumber() + SNdiff);
    packet.setTime(packet.time() + ATdiff);

    packet.writeToFile(outFile);

    packLen = packet.readFromFile(inFile);
  }

  fclose(inFile);
  fclose(outFile);

  return 0;
}
