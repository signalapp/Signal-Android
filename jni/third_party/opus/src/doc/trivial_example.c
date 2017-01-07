/* Copyright (c) 2013 Jean-Marc Valin */
/*
   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions
   are met:

   - Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   - Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
   OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

/* This is meant to be a simple example of encoding and decoding audio
   using Opus. It should make it easy to understand how the Opus API
   works. For more information, see the full API documentation at:
   https://www.opus-codec.org/docs/ */

#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <opus.h>
#include <stdio.h>

/*The frame size is hardcoded for this sample code but it doesn't have to be*/
#define FRAME_SIZE 960
#define SAMPLE_RATE 48000
#define CHANNELS 2
#define APPLICATION OPUS_APPLICATION_AUDIO
#define BITRATE 64000

#define MAX_FRAME_SIZE 6*960
#define MAX_PACKET_SIZE (3*1276)

int main(int argc, char **argv)
{
   char *inFile;
   FILE *fin;
   char *outFile;
   FILE *fout;
   opus_int16 in[FRAME_SIZE*CHANNELS];
   opus_int16 out[MAX_FRAME_SIZE*CHANNELS];
   unsigned char cbits[MAX_PACKET_SIZE];
   int nbBytes;
   /*Holds the state of the encoder and decoder */
   OpusEncoder *encoder;
   OpusDecoder *decoder;
   int err;

   if (argc != 3)
   {
      fprintf(stderr, "usage: trivial_example input.pcm output.pcm\n");
      fprintf(stderr, "input and output are 16-bit little-endian raw files\n");
      return EXIT_FAILURE;
   }

   /*Create a new encoder state */
   encoder = opus_encoder_create(SAMPLE_RATE, CHANNELS, APPLICATION, &err);
   if (err<0)
   {
      fprintf(stderr, "failed to create an encoder: %s\n", opus_strerror(err));
      return EXIT_FAILURE;
   }
   /* Set the desired bit-rate. You can also set other parameters if needed.
      The Opus library is designed to have good defaults, so only set
      parameters you know you need. Doing otherwise is likely to result
      in worse quality, but better. */
   err = opus_encoder_ctl(encoder, OPUS_SET_BITRATE(BITRATE));
   if (err<0)
   {
      fprintf(stderr, "failed to set bitrate: %s\n", opus_strerror(err));
      return EXIT_FAILURE;
   }
   inFile = argv[1];
   fin = fopen(inFile, "r");
   if (fin==NULL)
   {
      fprintf(stderr, "failed to open input file: %s\n", strerror(errno));
      return EXIT_FAILURE;
   }


   /* Create a new decoder state. */
   decoder = opus_decoder_create(SAMPLE_RATE, CHANNELS, &err);
   if (err<0)
   {
      fprintf(stderr, "failed to create decoder: %s\n", opus_strerror(err));
      return EXIT_FAILURE;
   }
   outFile = argv[2];
   fout = fopen(outFile, "w");
   if (fout==NULL)
   {
      fprintf(stderr, "failed to open output file: %s\n", strerror(errno));
      return EXIT_FAILURE;
   }

   while (1)
   {
      int i;
      unsigned char pcm_bytes[MAX_FRAME_SIZE*CHANNELS*2];
      int frame_size;

      /* Read a 16 bits/sample audio frame. */
      fread(pcm_bytes, sizeof(short)*CHANNELS, FRAME_SIZE, fin);
      if (feof(fin))
         break;
      /* Convert from little-endian ordering. */
      for (i=0;i<CHANNELS*FRAME_SIZE;i++)
         in[i]=pcm_bytes[2*i+1]<<8|pcm_bytes[2*i];

      /* Encode the frame. */
      nbBytes = opus_encode(encoder, in, FRAME_SIZE, cbits, MAX_PACKET_SIZE);
      if (nbBytes<0)
      {
         fprintf(stderr, "encode failed: %s\n", opus_strerror(nbBytes));
         return EXIT_FAILURE;
      }


      /* Decode the data. In this example, frame_size will be constant because
         the encoder is using a constant frame size. However, that may not
         be the case for all encoders, so the decoder must always check
         the frame size returned. */
      frame_size = opus_decode(decoder, cbits, nbBytes, out, MAX_FRAME_SIZE, 0);
      if (frame_size<0)
      {
         fprintf(stderr, "decoder failed: %s\n", opus_strerror(frame_size));
         return EXIT_FAILURE;
      }

      /* Convert to little-endian ordering. */
      for(i=0;i<CHANNELS*frame_size;i++)
      {
         pcm_bytes[2*i]=out[i]&0xFF;
         pcm_bytes[2*i+1]=(out[i]>>8)&0xFF;
      }
      /* Write the decoded audio to file. */
      fwrite(pcm_bytes, sizeof(short), frame_size*CHANNELS, fout);
   }
   /*Destroy the encoder state*/
   opus_encoder_destroy(encoder);
   opus_decoder_destroy(decoder);
   fclose(fin);
   fclose(fout);
   return EXIT_SUCCESS;
}
