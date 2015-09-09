/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * testG711.cpp : Defines the entry point for the console application.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* include API */
#include "g711_interface.h"

/* Runtime statistics */
#include <time.h>
#define CLOCKS_PER_SEC_G711 1000

/* function for reading audio data from PCM file */
int readframe(int16_t* data, FILE* inp, int length) {

  short k, rlen, status = 0;

  rlen = (short) fread(data, sizeof(int16_t), length, inp);
  if (rlen < length) {
    for (k = rlen; k < length; k++)
      data[k] = 0;
    status = 1;
  }

  return status;
}

int main(int argc, char* argv[]) {
  char inname[80], outname[40], bitname[40];
  FILE* inp;
  FILE* outp;
  FILE* bitp = NULL;
  int framecnt, endfile;

  int16_t framelength = 80;

  int err;

  /* Runtime statistics */
  double starttime;
  double runtime;
  double length_file;

  int16_t stream_len = 0;
  int16_t shortdata[480];
  int16_t decoded[480];
  int16_t streamdata[500];
  int16_t speechType[1];
  char law[2];
  char versionNumber[40];

  /* handling wrong input arguments in the command line */
  if ((argc != 5) && (argc != 6)) {
    printf("\n\nWrong number of arguments or flag values.\n\n");

    printf("\n");
    printf("\nG.711 test application\n\n");
    printf("Usage:\n\n");
    printf("./testG711.exe framelength law infile outfile \n\n");
    printf("framelength: Framelength in samples.\n");
    printf("law        : Coding law, A och u.\n");
    printf("infile     : Normal speech input file\n");
    printf("outfile    : Speech output file\n\n");
    printf("outbits    : Output bitstream file [optional]\n\n");
    exit(0);

  }

  /* Get version and print */
  WebRtcG711_Version(versionNumber, 40);

  printf("-----------------------------------\n");
  printf("G.711 version: %s\n\n", versionNumber);
  /* Get frame length */
  framelength = atoi(argv[1]);

  /* Get compression law */
  strcpy(law, argv[2]);

  /* Get Input and Output files */
  sscanf(argv[3], "%s", inname);
  sscanf(argv[4], "%s", outname);
  if (argc == 6) {
    sscanf(argv[5], "%s", bitname);
    if ((bitp = fopen(bitname, "wb")) == NULL) {
      printf("  G.711: Cannot read file %s.\n", bitname);
      exit(1);
    }
  }

  if ((inp = fopen(inname, "rb")) == NULL) {
    printf("  G.711: Cannot read file %s.\n", inname);
    exit(1);
  }
  if ((outp = fopen(outname, "wb")) == NULL) {
    printf("  G.711: Cannot write file %s.\n", outname);
    exit(1);
  }
  printf("\nInput:  %s\nOutput: %s\n", inname, outname);
  if (argc == 6) {
    printf("\nBitfile:  %s\n", bitname);
  }

  starttime = clock() / (double) CLOCKS_PER_SEC_G711; /* Runtime statistics */

  /* Initialize encoder and decoder */
  framecnt = 0;
  endfile = 0;
  while (endfile == 0) {
    framecnt++;
    /* Read speech block */
    endfile = readframe(shortdata, inp, framelength);

    /* G.711 encoding */
    if (!strcmp(law, "A")) {
      /* A-law encoding */
      stream_len = WebRtcG711_EncodeA(NULL, shortdata, framelength, streamdata);
      if (argc == 6) {
        /* Write bits to file */
        if (fwrite(streamdata, sizeof(unsigned char), stream_len, bitp) !=
            static_cast<size_t>(stream_len)) {
          return -1;
        }
      }
      err = WebRtcG711_DecodeA(NULL, streamdata, stream_len, decoded,
                               speechType);
    } else if (!strcmp(law, "u")) {
      /* u-law encoding */
      stream_len = WebRtcG711_EncodeU(NULL, shortdata, framelength, streamdata);
      if (argc == 6) {
        /* Write bits to file */
        if (fwrite(streamdata, sizeof(unsigned char), stream_len, bitp) !=
            static_cast<size_t>(stream_len)) {
          return -1;
        }
      }
      err = WebRtcG711_DecodeU(NULL, streamdata, stream_len, decoded,
                               speechType);
    } else {
      printf("Wrong law mode\n");
      exit(1);
    }
    if (stream_len < 0 || err < 0) {
      /* exit if returned with error */
      printf("Error in encoder/decoder\n");
    } else {
      /* Write coded speech to file */
      if (fwrite(decoded, sizeof(short), framelength, outp) !=
          static_cast<size_t>(framelength)) {
        return -1;
      }
    }
  }

  runtime = (double)(clock() / (double) CLOCKS_PER_SEC_G711 - starttime);
  length_file = ((double) framecnt * (double) framelength / 8000);
  printf("\n\nLength of speech file: %.1f s\n", length_file);
  printf("Time to run G.711:      %.2f s (%.2f %% of realtime)\n\n",
         runtime,
         (100 * runtime / length_file));
  printf("---------------------END----------------------\n");

  fclose(inp);
  fclose(outp);

  return 0;
}
