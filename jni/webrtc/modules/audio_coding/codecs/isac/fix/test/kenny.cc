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
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <ctype.h>

#include "webrtc/modules/audio_coding/codecs/isac/fix/include/isacfix.h"
#include "webrtc/test/testsupport/perf_test.h"

// TODO(kma): Clean up the code and change benchmarking the whole codec to
// separate encoder and decoder.

/* Defines */
#define SEED_FILE "randseed.txt"  /* Used when running decoder on garbage data */
#define MAX_FRAMESAMPLES    960   /* max number of samples per frame (= 60 ms frame) */
#define FRAMESAMPLES_10ms 160   /* number of samples per 10ms frame */
#define FS           16000 /* sampling frequency (Hz) */

/* Function for reading audio data from PCM file */
int readframe(int16_t *data, FILE *inp, int length) {

  short k, rlen, status = 0;

  rlen = fread(data, sizeof(int16_t), length, inp);
  if (rlen < length) {
    for (k = rlen; k < length; k++)
      data[k] = 0;
    status = 1;
  }

  return status;
}

/* Struct for bottleneck model */
typedef struct {
  uint32_t send_time;            /* samples */
  uint32_t arrival_time;         /* samples */
  uint32_t sample_count;         /* samples */
  uint16_t rtp_number;
} BottleNeckModel;

void get_arrival_time(int current_framesamples,   /* samples */
                      size_t packet_size,         /* bytes */
                      int bottleneck,             /* excluding headers; bits/s */
                      BottleNeckModel *BN_data)
{
  const int HeaderSize = 35;
  int HeaderRate;

  HeaderRate = HeaderSize * 8 * FS / current_framesamples;     /* bits/s */

  /* everything in samples */
  BN_data->sample_count = BN_data->sample_count + current_framesamples;

  BN_data->arrival_time += static_cast<uint32_t>(
      ((packet_size + HeaderSize) * 8 * FS) / (bottleneck + HeaderRate));
  BN_data->send_time += current_framesamples;

  if (BN_data->arrival_time < BN_data->sample_count)
    BN_data->arrival_time = BN_data->sample_count;

  BN_data->rtp_number++;
}

void get_arrival_time2(int current_framesamples,
                       int current_delay,
                       BottleNeckModel *BN_data)
{
  if (current_delay == -1)
    //dropped packet
  {
    BN_data->arrival_time += current_framesamples;
  }
  else if (current_delay != -2)
  {
    //
    BN_data->arrival_time += (current_framesamples + ((FS/1000) * current_delay));
  }
  //else
  //current packet has same timestamp as previous packet

  BN_data->rtp_number++;
}

int main(int argc, char* argv[])
{

  char inname[100], outname[100],  outbitsname[100], bottleneck_file[100];
  FILE *inp, *outp, *f_bn, *outbits;
  int endfile;

  size_t i;
  int errtype, h = 0, k, packetLossPercent = 0;
  int16_t CodingMode;
  int16_t bottleneck;
  int framesize = 30;           /* ms */
  int cur_framesmpls, err = 0, lostPackets = 0;

  /* Runtime statistics */
  double starttime, runtime, length_file;

  int stream_len_int = 0;
  size_t stream_len = 0;
  int16_t framecnt;
  int declen = 0;
  int16_t shortdata[FRAMESAMPLES_10ms];
  int16_t decoded[MAX_FRAMESAMPLES];
  uint16_t streamdata[500];
  int16_t speechType[1];
  size_t prevFrameSize = 1;
  int16_t rateBPS = 0;
  int16_t fixedFL = 0;
  int16_t payloadSize = 0;
  int32_t payloadRate = 0;
  int setControlBWE = 0;
  int readLoss;
  FILE  *plFile = NULL;

  char version_number[20];
  char tmpBit[5] = ".bit";

  int totalbits =0;
  int totalsmpls =0;
  int16_t testNum, testCE;

  FILE *fp_gns = NULL;
  int gns = 0;
  int cur_delay = 0;
  char gns_file[100];

  int nbTest = 0;
  int16_t lostFrame;
  float scale = (float)0.7;
  /* only one structure used for ISAC encoder */
  ISACFIX_MainStruct *ISAC_main_inst = NULL;

  /* For fault test 10, garbage data */
  FILE *seedfile;
  unsigned int random_seed = (unsigned int) time(NULL);//1196764538

  BottleNeckModel       BN_data;
  f_bn  = NULL;

  readLoss = 0;
  packetLossPercent = 0;

  /* Handling wrong input arguments in the command line */
  if ((argc<3) || (argc>21))  {
    printf("\n\nWrong number of arguments or flag values.\n\n");

    printf("\n");
    WebRtcIsacfix_version(version_number);
    printf("iSAC version %s \n\n", version_number);

    printf("Usage:\n\n");
    printf("./kenny.exe [-F num][-I] bottleneck_value infile outfile \n\n");
    printf("with:\n");
    printf("[-I]             :if -I option is specified, the coder will use\n");
    printf("                  an instantaneous Bottleneck value. If not, it\n");
    printf("                  will be an adaptive Bottleneck value.\n\n");
    printf("bottleneck_value :the value of the bottleneck provided either\n");
    printf("                  as a fixed value (e.g. 25000) or\n");
    printf("                  read from a file (e.g. bottleneck.txt)\n\n");
    printf("[-INITRATE num]  :Set a new value for initial rate. Note! Only used"
           " in adaptive mode.\n\n");
    printf("[-FL num]        :Set (initial) frame length in msec. Valid length"
           " are 30 and 60 msec.\n\n");
    printf("[-FIXED_FL]      :Frame length to be fixed to initial value.\n\n");
    printf("[-MAX num]       :Set the limit for the payload size of iSAC"
           " in bytes. \n");
    printf("                  Minimum 100, maximum 400.\n\n");
    printf("[-MAXRATE num]   :Set the maxrate for iSAC in bits per second. \n");
    printf("                  Minimum 32000, maximum 53400.\n\n");
    printf("[-F num]         :if -F option is specified, the test function\n");
    printf("                  will run the iSAC API fault scenario specified"
           " by the\n");
    printf("                  supplied number.\n");
    printf("                  F 1 - Call encoder prior to init encoder call\n");
    printf("                  F 2 - Call decoder prior to init decoder call\n");
    printf("                  F 3 - Call decoder prior to encoder call\n");
    printf("                  F 4 - Call decoder with a too short coded"
           " sequence\n");
    printf("                  F 5 - Call decoder with a too long coded"
           " sequence\n");
    printf("                  F 6 - Call decoder with random bit stream\n");
    printf("                  F 7 - Call init encoder/decoder at random"
           " during a call\n");
    printf("                  F 8 - Call encoder/decoder without having"
           " allocated memory for \n");
    printf("                        encoder/decoder instance\n");
    printf("                  F 9 - Call decodeB without calling decodeA\n");
    printf("                  F 10 - Call decodeB with garbage data\n");
    printf("[-PL num]       : if -PL option is specified 0<num<100 will "
           "specify the\n");
    printf("                  percentage of packet loss\n\n");
    printf("[-G file]       : if -G option is specified the file given is"
           " a .gns file\n");
    printf("                  that represents a network profile\n\n");
    printf("[-NB num]       : if -NB option, use the narrowband interfaces\n");
    printf("                  num=1 => encode with narrowband encoder"
           " (infile is narrowband)\n");
    printf("                  num=2 => decode with narrowband decoder"
           " (outfile is narrowband)\n\n");
    printf("[-CE num]       : Test of APIs used by Conference Engine.\n");
    printf("                  CE 1 - createInternal, freeInternal,"
           " getNewBitstream \n");
    printf("                  CE 2 - transcode, getBWE \n");
    printf("                  CE 3 - getSendBWE, setSendBWE.  \n\n");
    printf("[-RTP_INIT num] : if -RTP_INIT option is specified num will be"
           " the initial\n");
    printf("                  value of the rtp sequence number.\n\n");
    printf("infile          : Normal speech input file\n\n");
    printf("outfile         : Speech output file\n\n");
    printf("Example usage   : \n\n");
    printf("./kenny.exe -I bottleneck.txt speechIn.pcm speechOut.pcm\n\n");
    exit(0);

  }

  /* Print version number */
  WebRtcIsacfix_version(version_number);
  printf("iSAC version %s \n\n", version_number);

  /* Loop over all command line arguments */
  CodingMode = 0;
  testNum = 0;
  testCE = 0;
  for (i = 1; i + 2 < static_cast<size_t>(argc); i++) {
    /* Instantaneous mode */
    if (!strcmp ("-I", argv[i])) {
      printf("\nInstantaneous BottleNeck\n");
      CodingMode = 1;
      i++;
    }

    /* Set (initial) bottleneck value */
    if (!strcmp ("-INITRATE", argv[i])) {
      rateBPS = atoi(argv[i + 1]);
      setControlBWE = 1;
      if ((rateBPS < 10000) || (rateBPS > 32000)) {
        printf("\n%d is not a initial rate. "
               "Valid values are in the range 10000 to 32000.\n", rateBPS);
        exit(0);
      }
      printf("\nNew initial rate: %d\n", rateBPS);
      i++;
    }

    /* Set (initial) framelength */
    if (!strcmp ("-FL", argv[i])) {
      framesize = atoi(argv[i + 1]);
      if ((framesize != 30) && (framesize != 60)) {
        printf("\n%d is not a valid frame length. "
               "Valid length are 30 and 60 msec.\n", framesize);
        exit(0);
      }
      printf("\nFrame Length: %d\n", framesize);
      i++;
    }

    /* Fixed frame length */
    if (!strcmp ("-FIXED_FL", argv[i])) {
      fixedFL = 1;
      setControlBWE = 1;
    }

    /* Set maximum allowed payload size in bytes */
    if (!strcmp ("-MAX", argv[i])) {
      payloadSize = atoi(argv[i + 1]);
      printf("Maximum Payload Size: %d\n", payloadSize);
      i++;
    }

    /* Set maximum rate in bytes */
    if (!strcmp ("-MAXRATE", argv[i])) {
      payloadRate = atoi(argv[i + 1]);
      printf("Maximum Rate in kbps: %d\n", payloadRate);
      i++;
    }

    /* Test of fault scenarious */
    if (!strcmp ("-F", argv[i])) {
      testNum = atoi(argv[i + 1]);
      printf("\nFault test: %d\n", testNum);
      if (testNum < 1 || testNum > 10) {
        printf("\n%d is not a valid Fault Scenario number."
               " Valid Fault Scenarios are numbered 1-10.\n", testNum);
        exit(0);
      }
      i++;
    }

    /* Packet loss test */
    if (!strcmp ("-PL", argv[i])) {
      if( isdigit( *argv[i+1] ) ) {
        packetLossPercent = atoi( argv[i+1] );
        if( (packetLossPercent < 0) | (packetLossPercent > 100) ) {
          printf( "\nInvalid packet loss perentage \n" );
          exit( 0 );
        }
        if( packetLossPercent > 0 ) {
          printf( "\nSimulating %d %% of independent packet loss\n",
                  packetLossPercent );
        } else {
          printf( "\nNo Packet Loss Is Simulated \n" );
        }
        readLoss = 0;
      } else {
        readLoss = 1;
        plFile = fopen( argv[i+1], "rb" );
        if( plFile == NULL ) {
          printf( "\n couldn't open the frameloss file: %s\n", argv[i+1] );
          exit( 0 );
        }
        printf( "\nSimulating packet loss through the given "
                "channel file: %s\n", argv[i+1] );
      }
      i++;
    }

    /* Random packetlosses */
    if (!strcmp ("-rnd", argv[i])) {
      srand(time(NULL) );
      printf( "\n Random pattern in lossed packets \n" );
    }

    /* Use gns file */
    if (!strcmp ("-G", argv[i])) {
      sscanf(argv[i + 1], "%s", gns_file);
      fp_gns = fopen(gns_file, "rb");
      if (fp_gns  == NULL) {
        printf("Cannot read file %s.\n", gns_file);
        exit(0);
      }
      gns = 1;
      i++;
    }

    /* Run Narrowband interfaces (either encoder or decoder) */
    if (!strcmp ("-NB", argv[i])) {
      nbTest = atoi(argv[i + 1]);
      i++;
    }

    /* Run Conference Engine APIs */
    if (!strcmp ("-CE", argv[i])) {
      testCE = atoi(argv[i + 1]);
      if (testCE==1 || testCE==2) {
        i++;
        scale = (float)atof( argv[i+1] );
      } else if (testCE < 1 || testCE > 3) {
        printf("\n%d is not a valid CE-test number, valid Fault "
               "Scenarios are numbered 1-3\n", testCE);
        exit(0);
      }
      i++;
    }

    /* Set initial RTP number */
    if (!strcmp ("-RTP_INIT", argv[i])) {
      i++;
    }
  }

  /* Get Bottleneck value                                                   */
  /* Gns files and bottleneck should not and can not be used simultaneously */
  bottleneck = atoi(argv[CodingMode+1]);
  if (bottleneck == 0 && gns == 0) {
    sscanf(argv[CodingMode+1], "%s", bottleneck_file);
    f_bn = fopen(bottleneck_file, "rb");
    if (f_bn  == NULL) {
      printf("No value provided for BottleNeck and cannot read file %s\n",
             bottleneck_file);
      exit(0);
    } else {
      int aux_var;
      printf("reading bottleneck rates from file %s\n\n",bottleneck_file);
      if (fscanf(f_bn, "%d", &aux_var) == EOF) {
        /* Set pointer to beginning of file */
        fseek(f_bn, 0L, SEEK_SET);
        if (fscanf(f_bn, "%d", &aux_var) == EOF) {
          exit(0);
        }
      }
      bottleneck = (int16_t)aux_var;
      /* Bottleneck is a cosine function
       * Matlab code for writing the bottleneck file:
       * BottleNeck_10ms = 20e3 + 10e3 * cos((0:5999)/5999*2*pi);
       * fid = fopen('bottleneck.txt', 'wb');
       * fprintf(fid, '%d\n', BottleNeck_10ms); fclose(fid);
       */
    }
  } else {
    f_bn = NULL;
    printf("\nfixed bottleneck rate of %d bits/s\n\n", bottleneck);
  }

  if (CodingMode == 0) {
    printf("\nAdaptive BottleNeck\n");
  }

  /* Get Input and Output files */
  sscanf(argv[argc-2], "%s", inname);
  sscanf(argv[argc-1], "%s", outname);

  /* Add '.bit' to output bitstream file */
  while ((int)outname[h] != 0) {
    outbitsname[h] = outname[h];
    h++;
  }
  for (k=0; k<5; k++) {
    outbitsname[h] = tmpBit[k];
    h++;
  }
  if ((inp = fopen(inname,"rb")) == NULL) {
    printf("  iSAC: Cannot read file %s\n", inname);
    exit(1);
  }
  if ((outp = fopen(outname,"wb")) == NULL) {
    printf("  iSAC: Cannot write file %s\n", outname);
    exit(1);
  }

  if ((outbits = fopen(outbitsname,"wb")) == NULL) {
    printf("  iSAC: Cannot write file %s\n", outbitsname);
    exit(1);
  }
  printf("\nInput:%s\nOutput:%s\n\n", inname, outname);

  /* Error test number 10, garbage data */
  if (testNum == 10) {
    /* Test to run decoder with garbage data */
    srand(random_seed);

    if ( (seedfile = fopen(SEED_FILE, "a+t") ) == NULL ) {
      printf("Error: Could not open file %s\n", SEED_FILE);
    }
    else {
      fprintf(seedfile, "%u\n", random_seed);
      fclose(seedfile);
    }
  }

  /* Runtime statistics */
  starttime = clock()/(double)CLOCKS_PER_SEC;

  /* Initialize the ISAC and BN structs */
  if (testNum != 8)
  {
    if(1){
      err =WebRtcIsacfix_Create(&ISAC_main_inst);
    }else{
      /* Test the Assign functions */
      int sss;
      void *ppp;
      err =WebRtcIsacfix_AssignSize(&sss);
      ppp=malloc(sss);
      err =WebRtcIsacfix_Assign(&ISAC_main_inst,ppp);
    }
    /* Error check */
    if (err < 0) {
      printf("\n\n Error in create.\n\n");
    }
    if (testCE == 1) {
      err = WebRtcIsacfix_CreateInternal(ISAC_main_inst);
      /* Error check */
      if (err < 0) {
        printf("\n\n Error in createInternal.\n\n");
      }
    }
  }

  /* Init of bandwidth data */
  BN_data.send_time     = 0;
  BN_data.arrival_time  = 0;
  BN_data.sample_count  = 0;
  BN_data.rtp_number    = 0;

  /* Initialize encoder and decoder */
  framecnt= 0;
  endfile = 0;
  if (testNum != 1) {
    WebRtcIsacfix_EncoderInit(ISAC_main_inst, CodingMode);
  }
  if (testNum != 2) {
    WebRtcIsacfix_DecoderInit(ISAC_main_inst);
  }

  if (CodingMode == 1) {
    err = WebRtcIsacfix_Control(ISAC_main_inst, bottleneck, framesize);
    if (err < 0) {
      /* exit if returned with error */
      errtype=WebRtcIsacfix_GetErrorCode(ISAC_main_inst);
      printf("\n\n Error in control: %d.\n\n", errtype);
    }
  } else if(setControlBWE == 1) {
    err = WebRtcIsacfix_ControlBwe(ISAC_main_inst, rateBPS, framesize, fixedFL);
  }

  if (payloadSize != 0) {
    err = WebRtcIsacfix_SetMaxPayloadSize(ISAC_main_inst, payloadSize);
    if (err < 0) {
      /* exit if returned with error */
      errtype=WebRtcIsacfix_GetErrorCode(ISAC_main_inst);
      printf("\n\n Error in SetMaxPayloadSize: %d.\n\n", errtype);
      exit(EXIT_FAILURE);
    }
  }
  if (payloadRate != 0) {
    err = WebRtcIsacfix_SetMaxRate(ISAC_main_inst, payloadRate);
    if (err < 0) {
      /* exit if returned with error */
      errtype=WebRtcIsacfix_GetErrorCode(ISAC_main_inst);
      printf("\n\n Error in SetMaxRateInBytes: %d.\n\n", errtype);
      exit(EXIT_FAILURE);
    }
  }

  *speechType = 1;


  while (endfile == 0) {

    if(testNum == 7 && (rand()%2 == 0)) {
      err = WebRtcIsacfix_EncoderInit(ISAC_main_inst, CodingMode);
      /* Error check */
      if (err < 0) {
        errtype=WebRtcIsacfix_GetErrorCode(ISAC_main_inst);
        printf("\n\n Error in encoderinit: %d.\n\n", errtype);
      }

      WebRtcIsacfix_DecoderInit(ISAC_main_inst);
    }


    cur_framesmpls = 0;
    while (1) {
      /* Read 10 ms speech block */
      if (nbTest != 1) {
        endfile = readframe(shortdata, inp, FRAMESAMPLES_10ms);
      } else {
        endfile = readframe(shortdata, inp, (FRAMESAMPLES_10ms/2));
      }

      if (testNum == 7) {
        srand(time(NULL));
      }

      /* iSAC encoding */
      if (!(testNum == 3 && framecnt == 0)) {
        if (nbTest != 1) {
          short bwe;

          /* Encode */
          stream_len_int = WebRtcIsacfix_Encode(ISAC_main_inst,
                                                shortdata,
                                                (uint8_t*)streamdata);

          /* If packet is ready, and CE testing, call the different API
             functions from the internal API. */
          if (stream_len_int>0) {
            if (testCE == 1) {
              err = WebRtcIsacfix_ReadBwIndex(
                  reinterpret_cast<const uint8_t*>(streamdata),
                  static_cast<size_t>(stream_len_int),
                  &bwe);
              stream_len_int = WebRtcIsacfix_GetNewBitStream(
                  ISAC_main_inst,
                  bwe,
                  scale,
                  reinterpret_cast<uint8_t*>(streamdata));
            } else if (testCE == 2) {
              /* transcode function not supported */
            } else if (testCE == 3) {
              /* Only for Function testing. The functions should normally
                 not be used in this way                                      */

              err = WebRtcIsacfix_GetDownLinkBwIndex(ISAC_main_inst, &bwe);
              /* Error Check */
              if (err < 0) {
                errtype=WebRtcIsacfix_GetErrorCode(ISAC_main_inst);
                printf("\nError in getSendBWE: %d.\n", errtype);
              }

              err = WebRtcIsacfix_UpdateUplinkBw(ISAC_main_inst, bwe);
              /* Error Check */
              if (err < 0) {
                errtype=WebRtcIsacfix_GetErrorCode(ISAC_main_inst);
                printf("\nError in setBWE: %d.\n", errtype);
              }

            }
          }
        } else {
#ifdef WEBRTC_ISAC_FIX_NB_CALLS_ENABLED
          stream_len_int = WebRtcIsacfix_EncodeNb(ISAC_main_inst,
                                                  shortdata,
                                                  streamdata);
#else
          stream_len_int = -1;
#endif
        }
      }
      else
      {
        break;
      }

      if (stream_len_int < 0 || err < 0) {
        /* exit if returned with error */
        errtype=WebRtcIsacfix_GetErrorCode(ISAC_main_inst);
        printf("\nError in encoder: %d.\n", errtype);
      } else {
        stream_len = static_cast<size_t>(stream_len_int);
        if (fwrite(streamdata, sizeof(char), stream_len, outbits) !=
            stream_len) {
          return -1;
        }
      }

      cur_framesmpls += FRAMESAMPLES_10ms;

      /* read next bottleneck rate */
      if (f_bn != NULL) {
        int aux_var;
        if (fscanf(f_bn, "%d", &aux_var) == EOF) {
          /* Set pointer to beginning of file */
          fseek(f_bn, 0L, SEEK_SET);
          if (fscanf(f_bn, "%d", &aux_var) == EOF) {
            exit(0);
          }
        }
        bottleneck = (int16_t)aux_var;
        if (CodingMode == 1) {
          WebRtcIsacfix_Control(ISAC_main_inst, bottleneck, framesize);
        }
      }

      /* exit encoder loop if the encoder returned a bitstream */
      if (stream_len != 0) break;
    }

    /* make coded sequence to short be inreasing */
    /* the length the decoder expects */
    if (testNum == 4) {
      stream_len += 10;
    }

    /* make coded sequence to long be decreasing */
    /* the length the decoder expects */
    if (testNum == 5) {
      stream_len -= 10;
    }

    if (testNum == 6) {
      srand(time(NULL));
      for (i = 0; i < stream_len; i++ ) {
        streamdata[i] = rand();
      }
    }

    /* set pointer to beginning of file */
    if (fp_gns != NULL) {
      if (fscanf(fp_gns, "%d", &cur_delay) == EOF) {
        fseek(fp_gns, 0L, SEEK_SET);
        if (fscanf(fp_gns, "%d", &cur_delay) == EOF) {
          exit(0);
        }
      }
    }

    /* simulate packet handling through NetEq and the modem */
    if (!(testNum == 3 && framecnt == 0)) {
      if (gns == 0) {
        get_arrival_time(cur_framesmpls, stream_len, bottleneck,
                         &BN_data);
      } else {
        get_arrival_time2(cur_framesmpls, cur_delay, &BN_data);
      }
    }

    /* packet not dropped */
    if (cur_delay != -1) {

      /* Error test number 10, garbage data */
      if (testNum == 10) {
        for ( i = 0; i < stream_len; i++) {
          streamdata[i] = (short) (streamdata[i] + (short) rand());
        }
      }

      if (testNum != 9) {
        err = WebRtcIsacfix_UpdateBwEstimate(
            ISAC_main_inst,
            reinterpret_cast<const uint8_t*>(streamdata),
            stream_len,
            BN_data.rtp_number,
            BN_data.send_time,
            BN_data.arrival_time);

        if (err < 0) {
          /* exit if returned with error */
          errtype=WebRtcIsacfix_GetErrorCode(ISAC_main_inst);
          printf("\nError in decoder: %d.\n", errtype);
        }
      }

      if( readLoss == 1 ) {
        if( fread( &lostFrame, sizeof(int16_t), 1, plFile ) != 1 ) {
          rewind( plFile );
        }
        lostFrame = !lostFrame;
      } else {
        lostFrame = (rand()%100 < packetLossPercent);
      }



      /* iSAC decoding */
      if( lostFrame && framecnt >  0) {
        if (nbTest !=2) {
          declen = static_cast<int>(
              WebRtcIsacfix_DecodePlc(ISAC_main_inst, decoded, prevFrameSize));
        } else {
#ifdef WEBRTC_ISAC_FIX_NB_CALLS_ENABLED
          declen = static_cast<int>(WebRtcIsacfix_DecodePlcNb(
              ISAC_main_inst, decoded, prevFrameSize));
#else
          declen = -1;
#endif
        }
        lostPackets++;
      } else {
        if (nbTest !=2 ) {
          size_t FL;
          /* Call getFramelen, only used here for function test */
          err = WebRtcIsacfix_ReadFrameLen(
              reinterpret_cast<const uint8_t*>(streamdata), stream_len, &FL);
          declen = WebRtcIsacfix_Decode(
              ISAC_main_inst,
              reinterpret_cast<const uint8_t*>(streamdata),
              stream_len,
              decoded,
              speechType);
          /* Error check */
          if (err < 0 || declen < 0 || FL != static_cast<size_t>(declen)) {
            errtype=WebRtcIsacfix_GetErrorCode(ISAC_main_inst);
            printf("\nError in decode_B/or getFrameLen: %d.\n", errtype);
          }
          prevFrameSize = static_cast<size_t>(declen/480);

        } else {
#ifdef WEBRTC_ISAC_FIX_NB_CALLS_ENABLED
          declen = WebRtcIsacfix_DecodeNb( ISAC_main_inst, streamdata,
                                           stream_len, decoded, speechType );
#else
          declen = -1;
#endif
          prevFrameSize = static_cast<size_t>(declen / 240);
        }
      }

      if (declen <= 0) {
        /* exit if returned with error */
        errtype=WebRtcIsacfix_GetErrorCode(ISAC_main_inst);
        printf("\nError in decoder: %d.\n", errtype);
      }

      /* Write decoded speech frame to file */
      if (fwrite(decoded, sizeof(int16_t),
                 declen, outp) != (size_t)declen) {
        return -1;
      }
      //   fprintf( ratefile, "%f \n", stream_len / ( ((double)declen)/
      // ((double)FS) ) * 8 );
    } else {
      lostPackets++;
    }
    framecnt++;

    totalsmpls += declen;
    totalbits += static_cast<int>(8 * stream_len);

    /* Error test number 10, garbage data */
    if (testNum == 10) {
      if ( (seedfile = fopen(SEED_FILE, "a+t") ) == NULL ) {
        printf( "Error: Could not open file %s\n", SEED_FILE);
      }
      else {
        fprintf(seedfile, "ok\n\n");
        fclose(seedfile);
      }
    }
  }
  printf("\nLost Frames %d ~ %4.1f%%\n", lostPackets,
         (double)lostPackets/(double)framecnt*100.0 );
  printf("\n\ntotal bits                          = %d bits", totalbits);
  printf("\nmeasured average bitrate              = %0.3f kbits/s",
         (double)totalbits *(FS/1000) / totalsmpls);
  printf("\n");

  /* Runtime statistics */


  runtime = (double)(((double)clock()/(double)CLOCKS_PER_SEC)-starttime);
  length_file = ((double)framecnt*(double)declen/FS);
  printf("\n\nLength of speech file: %.1f s\n", length_file);
  printf("Time to run iSAC:      %.2f s (%.2f %% of realtime)\n\n",
         runtime, (100*runtime/length_file));
  printf("\n\n_______________________________________________\n");

  // Record the results with Perf test tools.
  webrtc::test::PrintResult("isac", "", "time_per_10ms_frame",
                            (runtime * 10000) / length_file, "us", false);

  fclose(inp);
  fclose(outp);
  fclose(outbits);

  if ( testCE == 1) {
    WebRtcIsacfix_FreeInternal(ISAC_main_inst);
  }
  WebRtcIsacfix_Free(ISAC_main_inst);
  return 0;
}
