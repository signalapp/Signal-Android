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

#include "gtest/gtest.h"
#include "webrtc/modules/audio_coding/neteq/test/NETEQTEST_RTPpacket.h"

#define FIRSTLINELEN 40

int main(int argc, char* argv[]) {
  if (argc < 3) {
    printf("Usage: RTPcat in1.rtp int2.rtp [...] out.rtp\n");
    exit(1);
  }

  FILE* in_file = fopen(argv[1], "rb");
  if (!in_file) {
    printf("Cannot open input file %s\n", argv[1]);
    return -1;
  }

  FILE* out_file = fopen(argv[argc - 1], "wb");  // Last parameter is out file.
  if (!out_file) {
    printf("Cannot open output file %s\n", argv[argc - 1]);
    return -1;
  }
  printf("Output RTP file: %s\n\n", argv[argc - 1]);

  // Read file header and write directly to output file.
  char firstline[FIRSTLINELEN];
  const unsigned int kRtpDumpHeaderSize = 4 + 4 + 4 + 2 + 2;
  EXPECT_TRUE(fgets(firstline, FIRSTLINELEN, in_file) != NULL);
  EXPECT_GT(fputs(firstline, out_file), 0);
  EXPECT_EQ(kRtpDumpHeaderSize, fread(firstline, 1, kRtpDumpHeaderSize,
                                      in_file));
  EXPECT_EQ(kRtpDumpHeaderSize, fwrite(firstline, 1, kRtpDumpHeaderSize,
                                       out_file));

  // Close input file and re-open it later (easier to write the loop below).
  fclose(in_file);

  for (int i = 1; i < argc - 1; i++) {
    in_file = fopen(argv[i], "rb");
    if (!in_file) {
      printf("Cannot open input file %s\n", argv[i]);
      return -1;
    }
    printf("Input RTP file: %s\n", argv[i]);

    NETEQTEST_RTPpacket::skipFileHeader(in_file);
    NETEQTEST_RTPpacket packet;
    int pack_len = packet.readFromFile(in_file);
    if (pack_len < 0) {
      exit(1);
    }
    while (pack_len >= 0) {
      packet.writeToFile(out_file);
      pack_len = packet.readFromFile(in_file);
    }
    fclose(in_file);
  }
  fclose(out_file);
  return 0;
}
