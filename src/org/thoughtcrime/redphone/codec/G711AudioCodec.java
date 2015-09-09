/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.redphone.codec;


import android.util.Log;


/**
 * An implementation of G.711 audio companding.  This is a stateless codec that
 * transforms each input short of 16 bit audio by representing the 16 bit value
 * according to a modified log scale.
 *
 * @author Stuart O. Anderson
 */

public class G711AudioCodec extends AudioCodec {

  private static final boolean ZEROTRAP = false; /*
                           * turn on the trap as per
                           * the MIL-STD
                           */
  private static final int BIAS = 0x84; /*
                     * define the add-in bias for 16 bit
                     * samples
                     */
  private static final int CLIP = 32635;

  private static final int exp_lut[] = { 0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3,
      3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5,
      5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
      5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7 };
  private static final int decoder_exp_lut[] = { 0, 132, 396, 924, 1980,
      4092, 8316, 16764 };

  @Override
  public int decode(byte[] encodedData, short[] rawData, int encLen ) {
    int nBytes = encodedPacketSize();
    for (int i = 0; i < nBytes; i++) {
      rawData[i] = ulaw2linear(encodedData[i]);
    }
    return nBytes;
  }

  public static void test() {
    for( short i=-32768; i < 32767; i+=10 ) {
      byte b = linear2ulaw(i);
      short u = ulaw2linear(b);

      Log.d("ULAW TEST:", i + ", " + b + ", " + u);
    }
  }

  @Override
  public int encode(short[] rawData, byte[] encodedData, int rawLen ) {
    int nBytes = encodedPacketSize();
    for (int i = 0; i < nBytes; i++) {
      encodedData[i] = linear2ulaw(rawData[i]);
    }
    return nBytes;
  }

  private static byte linear2ulaw(short sample) {
    int sign, exponent, mantissa;
    byte ulawbyte;

    /* Get the sample into sign-magnitude. */
    sign = (sample > 0) ? 1 : 0; /* set aside the sign */
    if (sign == 0)
      sample = (short) -sample; /* get magnitude */
    if (sample > CLIP)
      sample = CLIP; /* clip the magnitude */

    /* Convert from 16 bit linear to ulaw. */
    sample = (short) (sample + BIAS);
    exponent = exp_lut[(sample >> 7) & 0xFF];
    mantissa = (sample >> (exponent + 3)) & 0x0F;
    ulawbyte = (byte) ~( (sign<<7) | (exponent << 4) | mantissa);
    if (ZEROTRAP)
      if (ulawbyte == 0)
        ulawbyte = 0x02; /* optional CCITT trap */

    return (ulawbyte);
  }

  private static short ulaw2linear(byte ulawbyte) {

    int sign, exponent, mantissa;
    short sample;

    ulawbyte = (byte) ~ulawbyte;
    sign = (ulawbyte & 0x80);
    exponent = (ulawbyte >> 4) & 0x07;
    mantissa = ulawbyte & 0x0F;
    sample = (short) (decoder_exp_lut[exponent] + (mantissa << (exponent + 3)));
    if (sign == 0)
      sample = (short) -sample;

    return (sample);
  }

  public int encodedPacketSize() {
    return AudioCodec.SAMPLES_PER_FRAME;
  }
}

// /**
// ** Signal conversion routines for use with Sun4/60 audio chip
// **/
//
// #include stdio.h
//
// unsigned char linear2ulaw(/* int */);
// int ulaw2linear(/* unsigned char */);
//
// /*
// ** This routine converts from linear to ulaw
// **
// ** Craig Reese: IDA/Supercomputing Research Center
// ** Joe Campbell: Department of Defense
// ** 29 September 1989
// **
// ** References:
// ** 1) CCITT Recommendation G.711 (very difficult to follow)
// ** 2) "A New Digital Technique for Implementation of Any
// ** Continuous PCM Companding Law," Villeret, Michel,
// ** et al. 1973 IEEE Int. Conf. on Communications, Vol 1,
// ** 1973, pg. 11.12-11.17
// ** 3) MIL-STD-188-113,"Interoperability and Performance Standards
// ** for Analog-to_Digital Conversion Techniques,"
// ** 17 February 1987
// **
// ** Input: Signed 16 bit linear sample
// ** Output: 8 bit ulaw sample
// */
//
// #define ZEROTRAP /* turn on the trap as per the MIL-STD */
// #define BIAS 0x84 /* define the add-in bias for 16 bit samples */
// #define CLIP 32635
//
// unsigned char
// linear2ulaw(sample)
// int sample; {
// static int exp_lut[256] = {0,0,1,1,2,2,2,2,3,3,3,3,3,3,3,3,
// 4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,
// 5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
// 5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
// 6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
// 6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
// 6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
// 6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
// 7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
// 7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
// 7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
// 7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
// 7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
// 7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
// 7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
// 7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7};
// int sign, exponent, mantissa;
// unsigned char ulawbyte;
//
// /* Get the sample into sign-magnitude. */
// sign = (sample >> 8) & 0x80; /* set aside the sign */
// if (sign != 0) sample = -sample; /* get magnitude */
// if (sample > CLIP) sample = CLIP; /* clip the magnitude */
//
// /* Convert from 16 bit linear to ulaw. */
// sample = sample + BIAS;
// exponent = exp_lut[(sample >> 7) & 0xFF];
// mantissa = (sample >> (exponent + 3)) & 0x0F;
// ulawbyte = ~(sign | (exponent << 4) | mantissa);
// #ifdef ZEROTRAP
// if (ulawbyte == 0) ulawbyte = 0x02; /* optional CCITT trap */
// #endif
//
// return(ulawbyte);
// }
//
// /*
// ** This routine converts from ulaw to 16 bit linear.
// **
// ** Craig Reese: IDA/Supercomputing Research Center
// ** 29 September 1989
// **
// ** References:
// ** 1) CCITT Recommendation G.711 (very difficult to follow)
// ** 2) MIL-STD-188-113,"Interoperability and Performance Standards
// ** for Analog-to_Digital Conversion Techniques,"
// ** 17 February 1987
// **
// ** Input: 8 bit ulaw sample
// ** Output: signed 16 bit linear sample
// */
//
// int
// ulaw2linear(ulawbyte)
// unsigned char ulawbyte;
// {
// static int exp_lut[8] = {0,132,396,924,1980,4092,8316,16764};
// int sign, exponent, mantissa, sample;
//
// ulawbyte = ~ulawbyte;
// sign = (ulawbyte & 0x80);
// exponent = (ulawbyte >> 4) & 0x07;
// mantissa = ulawbyte & 0x0F;
// sample = exp_lut[exponent] + (mantissa << (exponent + 3));
// if (sign != 0) sample = -sample;
//
// return(sample);
// }
