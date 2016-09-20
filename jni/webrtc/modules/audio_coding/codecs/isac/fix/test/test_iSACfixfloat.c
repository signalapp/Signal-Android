/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/*
 * test_iSACfixfloat.c
 *
 * Test compatibility and quality between floating- and fixed-point code
 * */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* include API */
#include "isac.h"
#include "isacfix.h"
#include "webrtc/base/format_macros.h"

/* max number of samples per frame (= 60 ms frame) */
#define MAX_FRAMESAMPLES 960
/* number of samples per 10ms frame */
#define FRAMESAMPLES_10ms 160
/* sampling frequency (Hz) */
#define FS 16000

/* Runtime statistics */
#include <time.h>
#define CLOCKS_PER_SEC 1000

// FILE *histfile, *ratefile;

/* function for reading audio data from PCM file */
int readframe(int16_t* data, FILE* inp, int length) {
  short k, rlen, status = 0;

  rlen = fread(data, sizeof(int16_t), length, inp);
  if (rlen < length) {
    for (k = rlen; k < length; k++)
      data[k] = 0;
    status = 1;
  }

  return status;
}

typedef struct {
  uint32_t send_time;    /* samples */
  uint32_t arrival_time; /* samples */
  uint32_t sample_count; /* samples */
  uint16_t rtp_number;
} BottleNeckModel;

void get_arrival_time(int current_framesamples, /* samples */
                      size_t packet_size,       /* bytes */
                      int bottleneck,           /* excluding headers; bits/s */
                      BottleNeckModel* BN_data) {
  const int HeaderSize = 35;
  int HeaderRate;

  HeaderRate = HeaderSize * 8 * FS / current_framesamples; /* bits/s */

  /* everything in samples */
  BN_data->sample_count = BN_data->sample_count + current_framesamples;

  BN_data->arrival_time += (uint32_t)
      (((packet_size + HeaderSize) * 8 * FS) / (bottleneck + HeaderRate));
  BN_data->send_time += current_framesamples;

  if (BN_data->arrival_time < BN_data->sample_count)
    BN_data->arrival_time = BN_data->sample_count;

  BN_data->rtp_number++;
}

int main(int argc, char* argv[]) {
  char inname[50], outname[50], bottleneck_file[50], bitfilename[60],
      bitending[10] = "_bits.pcm";
  FILE* inp, *outp, *f_bn, *bitsp;
  int framecnt, endfile;

  int i, j, errtype, plc = 0;
  int16_t CodingMode;
  int16_t bottleneck;

  int framesize = 30; /* ms */
  // int framesize = 60; /* To invoke cisco complexity case at frame 2252 */

  int cur_framesmpls, err;

  /* Runtime statistics */
  double starttime;
  double runtime;
  double length_file;

  size_t stream_len = 0;
  int declen;

  int16_t shortdata[FRAMESAMPLES_10ms];
  int16_t decoded[MAX_FRAMESAMPLES];
  uint16_t streamdata[600];
  int16_t speechType[1];

  // int16_t* iSACstruct;

  char version_number[20];
  int mode = -1, tmp, nbTest = 0; /*,sss;*/

#if !defined(NDEBUG)
  FILE* fy;
  double kbps;
  size_t totalbits = 0;
  int totalsmpls = 0;
#endif

  /* only one structure used for ISAC encoder */
  ISAC_MainStruct* ISAC_main_inst;
  ISACFIX_MainStruct* ISACFIX_main_inst;

  BottleNeckModel BN_data;
  f_bn = NULL;

#if !defined(NDEBUG)
  fy = fopen("bit_rate.dat", "w");
  fclose(fy);
  fy = fopen("bytes_frames.dat", "w");
  fclose(fy);
#endif

  // histfile = fopen("histo.dat", "ab");
  // ratefile = fopen("rates.dat", "ab");

  /* handling wrong input arguments in the command line */
  if ((argc < 6) || (argc > 10)) {
    printf("\n\nWrong number of arguments or flag values.\n\n");

    printf("\n");
    WebRtcIsacfix_version(version_number);
    printf("iSAC version %s \n\n", version_number);

    printf("Usage:\n\n");
    printf("./kenny.exe [-I] bottleneck_value infile outfile \n\n");
    printf("with:\n");

    printf("[-I]            : If -I option is specified, the coder will use\n");
    printf("                  an instantaneous Bottleneck value. If not, it\n");
    printf("                  will be an adaptive Bottleneck value.\n\n");
    printf("bottleneck_value: The value of the bottleneck provided either\n");
    printf("                  as a fixed value (e.g. 25000) or\n");
    printf("                  read from a file (e.g. bottleneck.txt)\n\n");
    printf("[-m] mode       : Mode (encoder - decoder):\n");
    printf("                    0 - float - float\n");
    printf("                    1 - float - fix\n");
    printf("                    2 - fix - float\n");
    printf("                    3 - fix - fix\n\n");
    printf("[-PLC]          : Test PLC packetlosses\n\n");
    printf("[-NB] num       : Test NB interfaces:\n");
    printf("                    1 - encNB\n");
    printf("                    2 - decNB\n\n");
    printf("infile          : Normal speech input file\n\n");
    printf("outfile         : Speech output file\n\n");
    printf("Example usage:\n\n");
    printf("./kenny.exe -I bottleneck.txt -m 1 speechIn.pcm speechOut.pcm\n\n");
    exit(0);
  }

  printf("--------------------START---------------------\n\n");
  WebRtcIsac_version(version_number);
  printf("iSAC FLOAT version %s \n", version_number);
  WebRtcIsacfix_version(version_number);
  printf("iSAC FIX version   %s \n\n", version_number);

  CodingMode = 0;
  tmp = 1;
  for (i = 1; i < argc; i++) {
    if (!strcmp("-I", argv[i])) {
      printf("\nInstantaneous BottleNeck\n");
      CodingMode = 1;
      i++;
      tmp = 0;
    }

    if (!strcmp("-m", argv[i])) {
      mode = atoi(argv[i + 1]);
      i++;
    }

    if (!strcmp("-PLC", argv[i])) {
      plc = 1;
    }

    if (!strcmp("-NB", argv[i])) {
      nbTest = atoi(argv[i + 1]);
      i++;
    }
  }

  if (mode < 0) {
    printf("\nError! Mode must be set: -m 0 \n");
    exit(0);
  }

  if (CodingMode == 0) {
    printf("\nAdaptive BottleNeck\n");
  }

  /* Get Bottleneck value */
  bottleneck = atoi(argv[2 - tmp]);
  if (bottleneck == 0) {
    sscanf(argv[2 - tmp], "%s", bottleneck_file);
    f_bn = fopen(bottleneck_file, "rb");
    if (f_bn == NULL) {
      printf("No value provided for BottleNeck and cannot read file %s.\n",
             bottleneck_file);
      exit(0);
    } else {
      printf("reading bottleneck rates from file %s\n\n", bottleneck_file);
      if (fscanf(f_bn, "%d", &bottleneck) == EOF) {
        /* Set pointer to beginning of file */
        fseek(f_bn, 0L, SEEK_SET);
        fscanf(f_bn, "%d", &bottleneck);
      }

      /* Bottleneck is a cosine function
       * Matlab code for writing the bottleneck file:
       * BottleNeck_10ms = 20e3 + 10e3 * cos((0:5999)/5999*2*pi);
       * fid = fopen('bottleneck.txt', 'wb');
       * fprintf(fid, '%d\n', BottleNeck_10ms); fclose(fid);
       */
    }
  } else {
    printf("\nfixed bottleneck rate of %d bits/s\n\n", bottleneck);
  }

  /* Get Input and Output files */
  sscanf(argv[argc - 2], "%s", inname);
  sscanf(argv[argc - 1], "%s", outname);

  if ((inp = fopen(inname, "rb")) == NULL) {
    printf("  iSAC: Cannot read file %s.\n", inname);
    exit(1);
  }
  if ((outp = fopen(outname, "wb")) == NULL) {
    printf("  iSAC: Cannot write file %s.\n", outname);
    exit(1);
  }
  printf("\nInput:%s\nOutput:%s\n", inname, outname);

  i = 0;
  while (outname[i] != '\0') {
    bitfilename[i] = outname[i];
    i++;
  }
  i -= 4;
  for (j = 0; j < 9; j++, i++)
    bitfilename[i] = bitending[j];
  bitfilename[i] = '\0';
  if ((bitsp = fopen(bitfilename, "wb")) == NULL) {
    printf("  iSAC: Cannot read file %s.\n", bitfilename);
    exit(1);
  }
  printf("Bitstream:%s\n\n", bitfilename);

  starttime = clock() / (double)CLOCKS_PER_SEC; /* Runtime statistics */

  /* Initialize the ISAC and BN structs */
  WebRtcIsac_create(&ISAC_main_inst);
  WebRtcIsacfix_Create(&ISACFIX_main_inst);

  BN_data.send_time = 0;
  BN_data.arrival_time = 0;
  BN_data.sample_count = 0;
  BN_data.rtp_number = 0;

  /* Initialize encoder and decoder */
  framecnt = 0;
  endfile = 0;

  if (mode == 0) { /* Encode using FLOAT, decode using FLOAT */

    printf("Coding mode: Encode using FLOAT, decode using FLOAT \n\n");

    /* Init iSAC FLOAT */
    WebRtcIsac_EncoderInit(ISAC_main_inst, CodingMode);
    WebRtcIsac_DecoderInit(ISAC_main_inst);
    if (CodingMode == 1) {
      err = WebRtcIsac_Control(ISAC_main_inst, bottleneck, framesize);
      if (err < 0) {
        /* exit if returned with error */
        errtype = WebRtcIsac_GetErrorCode(ISAC_main_inst);
        printf("\n\n Error in initialization: %d.\n\n", errtype);
        // exit(EXIT_FAILURE);
      }
    }

  } else if (mode == 1) { /* Encode using FLOAT, decode using FIX */

    printf("Coding mode: Encode using FLOAT, decode using FIX \n\n");

    /* Init iSAC FLOAT */
    WebRtcIsac_EncoderInit(ISAC_main_inst, CodingMode);
    WebRtcIsac_DecoderInit(ISAC_main_inst);
    if (CodingMode == 1) {
      err = WebRtcIsac_Control(ISAC_main_inst, bottleneck, framesize);
      if (err < 0) {
        /* exit if returned with error */
        errtype = WebRtcIsac_GetErrorCode(ISAC_main_inst);
        printf("\n\n Error in initialization: %d.\n\n", errtype);
        // exit(EXIT_FAILURE);
      }
    }

    /* Init iSAC FIX */
    WebRtcIsacfix_EncoderInit(ISACFIX_main_inst, CodingMode);
    WebRtcIsacfix_DecoderInit(ISACFIX_main_inst);
    if (CodingMode == 1) {
      err = WebRtcIsacfix_Control(ISACFIX_main_inst, bottleneck, framesize);
      if (err < 0) {
        /* exit if returned with error */
        errtype = WebRtcIsacfix_GetErrorCode(ISACFIX_main_inst);
        printf("\n\n Error in initialization: %d.\n\n", errtype);
        // exit(EXIT_FAILURE);
      }
    }
  } else if (mode == 2) { /* Encode using FIX, decode using FLOAT */

    printf("Coding mode: Encode using FIX, decode using FLOAT \n\n");

    /* Init iSAC FLOAT */
    WebRtcIsac_EncoderInit(ISAC_main_inst, CodingMode);
    WebRtcIsac_DecoderInit(ISAC_main_inst);
    if (CodingMode == 1) {
      err = WebRtcIsac_Control(ISAC_main_inst, bottleneck, framesize);
      if (err < 0) {
        /* exit if returned with error */
        errtype = WebRtcIsac_GetErrorCode(ISAC_main_inst);
        printf("\n\n Error in initialization: %d.\n\n", errtype);
        // exit(EXIT_FAILURE);
      }
    }

    /* Init iSAC FIX */
    WebRtcIsacfix_EncoderInit(ISACFIX_main_inst, CodingMode);
    WebRtcIsacfix_DecoderInit(ISACFIX_main_inst);
    if (CodingMode == 1) {
      err = WebRtcIsacfix_Control(ISACFIX_main_inst, bottleneck, framesize);
      if (err < 0) {
        /* exit if returned with error */
        errtype = WebRtcIsacfix_GetErrorCode(ISACFIX_main_inst);
        printf("\n\n Error in initialization: %d.\n\n", errtype);
        // exit(EXIT_FAILURE);
      }
    }
  } else if (mode == 3) {
    printf("Coding mode: Encode using FIX, decode using FIX \n\n");

    WebRtcIsacfix_EncoderInit(ISACFIX_main_inst, CodingMode);
    WebRtcIsacfix_DecoderInit(ISACFIX_main_inst);
    if (CodingMode == 1) {
      err = WebRtcIsacfix_Control(ISACFIX_main_inst, bottleneck, framesize);
      if (err < 0) {
        /* exit if returned with error */
        errtype = WebRtcIsacfix_GetErrorCode(ISACFIX_main_inst);
        printf("\n\n Error in initialization: %d.\n\n", errtype);
        // exit(EXIT_FAILURE);
      }
    }

  } else
    printf("Mode must be value between 0 and 3\n");
  *speechType = 1;

//#define BI_TEST 1
#ifdef BI_TEST
  err = WebRtcIsacfix_SetMaxPayloadSize(ISACFIX_main_inst, 300);
  if (err < 0) {
    /* exit if returned with error */
    errtype = WebRtcIsacfix_GetErrorCode(ISACFIX_main_inst);
    printf("\n\n Error in setMaxPayloadSize: %d.\n\n", errtype);
    fclose(inp);
    fclose(outp);
    fclose(bitsp);
    return (EXIT_FAILURE);
  }
#endif

  while (endfile == 0) {
    cur_framesmpls = 0;
    while (1) {
      int stream_len_int;

      /* Read 10 ms speech block */
      if (nbTest != 1)
        endfile = readframe(shortdata, inp, FRAMESAMPLES_10ms);
      else
        endfile = readframe(shortdata, inp, (FRAMESAMPLES_10ms / 2));

      /* iSAC encoding */

      if (mode == 0 || mode == 1) {
        stream_len_int =
            WebRtcIsac_Encode(ISAC_main_inst, shortdata, (uint8_t*)streamdata);
        if (stream_len_int < 0) {
          /* exit if returned with error */
          errtype = WebRtcIsac_GetErrorCode(ISAC_main_inst);
          printf("\n\nError in encoder: %d.\n\n", errtype);
          // exit(EXIT_FAILURE);
        }
      } else if (mode == 2 || mode == 3) {
        /* iSAC encoding */
        if (nbTest != 1) {
          stream_len_int = WebRtcIsacfix_Encode(ISACFIX_main_inst, shortdata,
                                                (uint8_t*)streamdata);
        } else {
          stream_len_int =
              WebRtcIsacfix_EncodeNb(ISACFIX_main_inst, shortdata, streamdata);
        }

        if (stream_len_int < 0) {
          /* exit if returned with error */
          errtype = WebRtcIsacfix_GetErrorCode(ISACFIX_main_inst);
          printf("\n\nError in encoder: %d.\n\n", errtype);
          // exit(EXIT_FAILURE);
        }
      }
      stream_len = (size_t)stream_len_int;

      cur_framesmpls += FRAMESAMPLES_10ms;

      /* read next bottleneck rate */
      if (f_bn != NULL) {
        if (fscanf(f_bn, "%d", &bottleneck) == EOF) {
          /* Set pointer to beginning of file */
          fseek(f_bn, 0L, SEEK_SET);
          fscanf(f_bn, "%d", &bottleneck);
        }
        if (CodingMode == 1) {
          if (mode == 0 || mode == 1)
            WebRtcIsac_Control(ISAC_main_inst, bottleneck, framesize);
          else if (mode == 2 || mode == 3)
            WebRtcIsacfix_Control(ISACFIX_main_inst, bottleneck, framesize);
        }
      }

      /* exit encoder loop if the encoder returned a bitstream */
      if (stream_len != 0)
        break;
    }

    fwrite(streamdata, 1, stream_len, bitsp); /* NOTE! Writes bytes to file */

    /* simulate packet handling through NetEq and the modem */
    get_arrival_time(cur_framesmpls, stream_len, bottleneck, &BN_data);
    //*****************************
    if (1) {
      if (mode == 0) {
        err = WebRtcIsac_UpdateBwEstimate(ISAC_main_inst, streamdata,
                                          stream_len, BN_data.rtp_number,
                                          BN_data.arrival_time);

        if (err < 0) {
          /* exit if returned with error */
          errtype = WebRtcIsac_GetErrorCode(ISAC_main_inst);
          printf("\n\nError in decoder: %d.\n\n", errtype);
          // exit(EXIT_FAILURE);
        }
        /* iSAC decoding */
        declen = WebRtcIsac_Decode(ISAC_main_inst, streamdata, stream_len,
                                   decoded, speechType);
        if (declen <= 0) {
          /* exit if returned with error */
          errtype = WebRtcIsac_GetErrorCode(ISAC_main_inst);
          printf("\n\nError in decoder: %d.\n\n", errtype);
          // exit(EXIT_FAILURE);
        }
      } else if (mode == 1) {
        err = WebRtcIsac_UpdateBwEstimate(ISAC_main_inst, streamdata,
                                          stream_len, BN_data.rtp_number,
                                          BN_data.arrival_time);
        err = WebRtcIsacfix_UpdateBwEstimate1(ISACFIX_main_inst, streamdata,
                                              stream_len, BN_data.rtp_number,
                                              BN_data.arrival_time);
        if (err < 0) {
          /* exit if returned with error */
          errtype = WebRtcIsacfix_GetErrorCode(ISACFIX_main_inst);
          printf("\n\nError in decoder: %d.\n\n", errtype);
          // exit(EXIT_FAILURE);
        }

        declen = WebRtcIsac_Decode(ISAC_main_inst, streamdata, stream_len,
                                   decoded, speechType);

        /* iSAC decoding */
        if (plc && (framecnt + 1) % 10 == 0) {
          if (nbTest != 2) {
            declen =
                (int)WebRtcIsacfix_DecodePlc(ISACFIX_main_inst, decoded, 1);
          } else {
            declen =
                (int)WebRtcIsacfix_DecodePlcNb(ISACFIX_main_inst, decoded, 1);
          }
        } else {
          if (nbTest != 2)
            declen = WebRtcIsacfix_Decode(ISACFIX_main_inst, streamdata,
                                          stream_len, decoded, speechType);
          else
            declen = WebRtcIsacfix_DecodeNb(ISACFIX_main_inst, streamdata,
                                            stream_len, decoded, speechType);
        }

        if (declen <= 0) {
          /* exit if returned with error */
          errtype = WebRtcIsacfix_GetErrorCode(ISACFIX_main_inst);
          printf("\n\nError in decoder: %d.\n\n", errtype);
          // exit(EXIT_FAILURE);
        }
      } else if (mode == 2) {
        err = WebRtcIsacfix_UpdateBwEstimate1(ISACFIX_main_inst, streamdata,
                                              stream_len, BN_data.rtp_number,
                                              BN_data.arrival_time);

        err = WebRtcIsac_UpdateBwEstimate(ISAC_main_inst, streamdata,
                                          stream_len, BN_data.rtp_number,
                                          BN_data.arrival_time);

        if (err < 0) {
          /* exit if returned with error */
          errtype = WebRtcIsac_GetErrorCode(ISAC_main_inst);
          printf("\n\nError in decoder: %d.\n\n", errtype);
          // exit(EXIT_FAILURE);
        }
        /* iSAC decoding */
        declen = WebRtcIsac_Decode(ISAC_main_inst, streamdata, stream_len,
                                   decoded, speechType);
        if (declen <= 0) {
          /* exit if returned with error */
          errtype = WebRtcIsac_GetErrorCode(ISAC_main_inst);
          printf("\n\nError in decoder: %d.\n\n", errtype);
          // exit(EXIT_FAILURE);
        }
      } else if (mode == 3) {
        err = WebRtcIsacfix_UpdateBwEstimate(
            ISACFIX_main_inst, streamdata, stream_len, BN_data.rtp_number,
            BN_data.send_time, BN_data.arrival_time);

        if (err < 0) {
          /* exit if returned with error */
          errtype = WebRtcIsacfix_GetErrorCode(ISACFIX_main_inst);
          printf("\n\nError in decoder: %d.\n\n", errtype);
          // exit(EXIT_FAILURE);
        }
        /* iSAC decoding */

        if (plc && (framecnt + 1) % 10 == 0) {
          if (nbTest != 2) {
            declen =
                (int)WebRtcIsacfix_DecodePlc(ISACFIX_main_inst, decoded, 1);
          } else {
            declen =
                (int)WebRtcIsacfix_DecodePlcNb(ISACFIX_main_inst, decoded, 1);
          }
        } else {
          if (nbTest != 2) {
            declen = WebRtcIsacfix_Decode(ISACFIX_main_inst, streamdata,
                                          stream_len, decoded, speechType);
          } else {
            declen = WebRtcIsacfix_DecodeNb(ISACFIX_main_inst, streamdata,
                                            stream_len, decoded, speechType);
          }
        }
        if (declen <= 0) {
          /* exit if returned with error */
          errtype = WebRtcIsacfix_GetErrorCode(ISACFIX_main_inst);
          printf("\n\nError in decoder: %d.\n\n", errtype);
          // exit(EXIT_FAILURE);
        }
      }

      /* Write decoded speech frame to file */
      fwrite(decoded, sizeof(int16_t), declen, outp);
    }

    fprintf(stderr, "  \rframe = %d", framecnt);
    framecnt++;

#if !defined(NDEBUG)

    totalsmpls += declen;
    totalbits += 8 * stream_len;
    kbps = (double)FS / (double)cur_framesmpls * 8.0 * stream_len / 1000.0;
    fy = fopen("bit_rate.dat", "a");
    fprintf(fy, "Frame %i = %0.14f\n", framecnt, kbps);
    fclose(fy);

#endif
  }

#if !defined(NDEBUG)
  printf("\n\ntotal bits               = %" PRIuS " bits", totalbits);
  printf("\nmeasured average bitrate = %0.3f kbits/s",
         (double)totalbits * (FS / 1000) / totalsmpls);
  printf("\n");
#endif

  /* Runtime statistics */
  runtime = (double)(clock() / (double)CLOCKS_PER_SEC - starttime);
  length_file = ((double)framecnt * (double)declen / FS);
  printf("\n\nLength of speech file: %.1f s\n", length_file);
  printf("Time to run iSAC:      %.2f s (%.2f %% of realtime)\n\n", runtime,
         (100 * runtime / length_file));
  printf("---------------------END----------------------\n");

  fclose(inp);
  fclose(outp);

  WebRtcIsac_Free(ISAC_main_inst);
  WebRtcIsacfix_Free(ISACFIX_main_inst);

  // fclose(histfile);
  // fclose(ratefile);

  return 0;
}
