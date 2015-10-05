#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <speex/speex.h>
#include <stdio.h>
#include <stdlib.h>
#include <speex/speex_callbacks.h>

#ifdef FIXED_DEBUG
extern long long spx_mips;
#endif

#define FRAME_SIZE 320
#include <math.h>
int main(int argc, char **argv)
{
   char *inFile, *outFile, *bitsFile;
   FILE *fin, *fout, *fbits=NULL;
   short in_short[FRAME_SIZE];
   short out_short[FRAME_SIZE];
   float sigpow,errpow,snr, seg_snr=0;
   int snr_frames = 0;
   char cbits[200];
   int nbBits;
   int i;
   void *st;
   void *dec;
   SpeexBits bits;
   spx_int32_t tmp;
   int bitCount=0;
   spx_int32_t skip_group_delay;
   SpeexCallback callback;

   sigpow = 0;
   errpow = 0;

   st = speex_encoder_init(speex_lib_get_mode(SPEEX_MODEID_WB));
   dec = speex_decoder_init(speex_lib_get_mode(SPEEX_MODEID_WB));

   callback.callback_id = SPEEX_INBAND_CHAR;
   callback.func = speex_std_char_handler;
   callback.data = stderr;
   speex_decoder_ctl(dec, SPEEX_SET_HANDLER, &callback);

   callback.callback_id = SPEEX_INBAND_MODE_REQUEST;
   callback.func = speex_std_mode_request_handler;
   callback.data = st;
   speex_decoder_ctl(dec, SPEEX_SET_HANDLER, &callback);

   tmp=1;
   speex_decoder_ctl(dec, SPEEX_SET_ENH, &tmp);
   tmp=0;
   speex_encoder_ctl(st, SPEEX_SET_VBR, &tmp);
   tmp=8;
   speex_encoder_ctl(st, SPEEX_SET_QUALITY, &tmp);
   tmp=3;
   speex_encoder_ctl(st, SPEEX_SET_COMPLEXITY, &tmp);
   /*tmp=3;
   speex_encoder_ctl(st, SPEEX_SET_HIGH_MODE, &tmp);
   tmp=6;
   speex_encoder_ctl(st, SPEEX_SET_LOW_MODE, &tmp);
*/

   speex_encoder_ctl(st, SPEEX_GET_LOOKAHEAD, &skip_group_delay);
   speex_decoder_ctl(dec, SPEEX_GET_LOOKAHEAD, &tmp);
   skip_group_delay += tmp;


   if (argc != 4 && argc != 3)
   {
      fprintf (stderr, "Usage: encode [in file] [out file] [bits file]\nargc = %d", argc);
      exit(1);
   }
   inFile = argv[1];
   fin = fopen(inFile, "rb");
   outFile = argv[2];
   fout = fopen(outFile, "wb+");
   if (argc==4)
   {
      bitsFile = argv[3];
      fbits = fopen(bitsFile, "wb");
   }
   speex_bits_init(&bits);
   while (!feof(fin))
   {
      fread(in_short, sizeof(short), FRAME_SIZE, fin);
      if (feof(fin))
         break;
      speex_bits_reset(&bits);

      speex_encode_int(st, in_short, &bits);
      nbBits = speex_bits_write(&bits, cbits, 200);
      bitCount+=bits.nbBits;

      if (argc==4)
         fwrite(cbits, 1, nbBits, fbits);
      speex_bits_rewind(&bits);

      speex_decode_int(dec, &bits, out_short);
      speex_bits_reset(&bits);

      fwrite(&out_short[skip_group_delay], sizeof(short), FRAME_SIZE-skip_group_delay, fout);
      skip_group_delay = 0;
   }
   fprintf (stderr, "Total encoded size: %d bits\n", bitCount);
   speex_encoder_destroy(st);
   speex_decoder_destroy(dec);
   speex_bits_destroy(&bits);

   rewind(fin);
   rewind(fout);

   while ( FRAME_SIZE == fread(in_short, sizeof(short), FRAME_SIZE, fin) 
           &&
           FRAME_SIZE ==  fread(out_short, sizeof(short), FRAME_SIZE,fout) )
   {
	float s=0, e=0;
        for (i=0;i<FRAME_SIZE;++i) {
            s += (float)in_short[i] * in_short[i];
            e += ((float)in_short[i]-out_short[i]) * ((float)in_short[i]-out_short[i]);
        }
	seg_snr += 10*log10((s+160)/(e+160));
	sigpow += s;
	errpow += e;
	snr_frames++;
   }
   fclose(fin);
   fclose(fout);

   snr = 10 * log10( sigpow / errpow );
   seg_snr /= snr_frames;
   fprintf(stderr,"SNR = %f\nsegmental SNR = %f\n",snr, seg_snr);

#ifdef FIXED_DEBUG
   printf ("Total: %f MIPS\n", (float)(1e-6*50*spx_mips/snr_frames));
#endif
   
   return 1;
}
