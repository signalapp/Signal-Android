/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

//TODO(hlundin): Reformat file to meet style guide.

/* header includes */
#include <float.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef WIN32
#include <winsock2.h>
#include <io.h>
#endif
#ifdef WEBRTC_LINUX
#include <netinet/in.h>
#endif

#include <assert.h>

#include "testing/gtest/include/gtest/gtest.h"
#include "webrtc/typedefs.h"

/*********************/
/* Misc. definitions */
/*********************/

#define FIRSTLINELEN 40
#define CHECK_NOT_NULL(a) if((a)==NULL){ \
    fprintf(stderr,"\n %s \n line: %d \nerror at %s\n",__FILE__,__LINE__,#a ); \
    return(-1);}

struct arr_time {
  float time;
  uint32_t ix;
};

int filelen(FILE *fid)
{
  fpos_t cur_pos;
  int len;

  if (!fid || fgetpos(fid, &cur_pos)) {
    return(-1);
  }

  fseek(fid, 0, SEEK_END);
  len = ftell(fid);

  fsetpos(fid, &cur_pos);

  return (len);
}

int compare_arr_time(const void *x, const void *y);

int main(int argc, char* argv[])
{
  unsigned int  dat_len, rtp_len, Npack, k;
  arr_time    *time_vec;
  char      firstline[FIRSTLINELEN];
  unsigned char* rtp_vec = NULL;
        unsigned char** packet_ptr = NULL;
        unsigned char* temp_packet = NULL;
  const unsigned int kRtpDumpHeaderSize = 4 + 4 + 4 + 2 + 2;
  uint16_t      len;
  uint32_t      *offset;

/* check number of parameters */
  if (argc != 4) {
    /* print help text and exit */
    printf("Apply jitter on RTP stream.\n");
    printf("Reads an RTP stream and packet timing from two files.\n");
    printf("The RTP stream is modified to have the same jitter as described in "
           "the timing files.\n");
    printf("The format of the RTP stream file should be the same as for \n");
    printf("rtpplay, and can be obtained e.g., from Ethereal by using\n");
    printf("Statistics -> RTP -> Show All Streams -> [select a stream] -> "
           "Save As\n\n");
    printf("Usage:\n\n");
    printf("%s RTP_infile dat_file RTP_outfile\n", argv[0]);
    printf("where:\n");

    printf("RTP_infile       : RTP stream input file\n\n");

    printf("dat_file         : file with packet arrival times in ms\n\n");

    printf("RTP_outfile      : RTP stream output file\n\n");

    return(0);
  }

  FILE* in_file=fopen(argv[1],"rb");
  CHECK_NOT_NULL(in_file);
  printf("Input file: %s\n",argv[1]);
  FILE* dat_file=fopen(argv[2],"rb");
  CHECK_NOT_NULL(dat_file);
  printf("Dat-file: %s\n",argv[2]);
  FILE* out_file=fopen(argv[3],"wb");
  CHECK_NOT_NULL(out_file);
  printf("Output file: %s\n\n",argv[3]);

  // add 1000 bytes to avoid (rare) strange error.
  time_vec = (arr_time *) malloc(sizeof(arr_time)
                                 *(filelen(dat_file)/sizeof(float)) + 1000);
  if (time_vec==NULL) {
    fprintf(stderr, "Error: could not allocate memory for reading dat file\n");
    goto closing;
  }

  dat_len=0;
  while(fread(&(time_vec[dat_len].time),sizeof(float),1,dat_file)>0) {
    time_vec[dat_len].ix=dat_len;
    dat_len++;
  }

  if (dat_len == 0) {
    fprintf(stderr, "Error: dat_file is empty, no arrival time is given.\n");
    goto closing;
  }

  qsort(time_vec,dat_len,sizeof(arr_time),compare_arr_time);


  rtp_vec = (unsigned char *) malloc(sizeof(unsigned char)*filelen(in_file));
  if (rtp_vec==NULL) {
    fprintf(stderr,"Error: could not allocate memory for reading rtp file\n");
    goto closing;
  }

  // read file header and write directly to output file
  EXPECT_TRUE(fgets(firstline, FIRSTLINELEN, in_file) != NULL);
  EXPECT_GT(fputs(firstline, out_file), 0);
  EXPECT_EQ(kRtpDumpHeaderSize, fread(firstline, 1, kRtpDumpHeaderSize,
                                      in_file));
  EXPECT_EQ(kRtpDumpHeaderSize, fwrite(firstline, 1, kRtpDumpHeaderSize,
                                       out_file));

  // read all RTP packets into vector
  rtp_len=0;
  Npack=0;

  // read length of first packet.
  len=(uint16_t) fread(&rtp_vec[rtp_len], sizeof(unsigned char), 2, in_file);
  while(len==2) {
    len = ntohs(*((uint16_t *)(rtp_vec + rtp_len)));
    rtp_len += 2;
    if(fread(&rtp_vec[rtp_len], sizeof(unsigned char),
             len-2, in_file)!=(unsigned) (len-2)) {
      fprintf(stderr,"Error: currupt packet length\n");
      goto closing;
    }
    rtp_len += len-2;
    Npack++;

    // read length of next packet.
    len=(uint16_t) fread(&rtp_vec[rtp_len], sizeof(unsigned char), 2, in_file);
  }

  if (Npack == 0) {
    fprintf(stderr, "Error: No RTP packet found.\n");
    goto closing;
  }

  packet_ptr = (unsigned char **) malloc(Npack*sizeof(unsigned char*));

  packet_ptr[0]=rtp_vec;
  k=1;
  while(k<Npack) {
    len = ntohs(*((uint16_t *) packet_ptr[k-1]));
    packet_ptr[k]=packet_ptr[k-1]+len;
    k++;
  }

  for(k=0; k<dat_len && k<Npack; k++) {
    if(time_vec[k].time < FLT_MAX && time_vec[k].ix < Npack){
      temp_packet = packet_ptr[time_vec[k].ix];
      offset = (uint32_t *) (temp_packet+4);
      if ( time_vec[k].time >= 0 ) {
        *offset = htonl((uint32_t) time_vec[k].time);
      }
      else {
        *offset = htonl((uint32_t) 0);
        fprintf(stderr, "Warning: negative receive time in dat file transformed"
                " to 0.\n");
      }

      // write packet to file
      if (fwrite(temp_packet, sizeof(unsigned char),
                 ntohs(*((uint16_t*) temp_packet)),
                 out_file) !=
          ntohs(*((uint16_t*) temp_packet))) {
        return -1;
      }
    }
  }


closing:
  free(time_vec);
  free(rtp_vec);
        if (packet_ptr != NULL) {
    free(packet_ptr);
        }
        fclose(in_file);
  fclose(dat_file);
  fclose(out_file);

  return(0);
}



int compare_arr_time(const void *xp, const void *yp) {

  if(((arr_time *)xp)->time == ((arr_time *)yp)->time)
    return(0);
  else if(((arr_time *)xp)->time > ((arr_time *)yp)->time)
    return(1);

  return(-1);
}
