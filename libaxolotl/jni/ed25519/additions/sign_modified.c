#include <string.h>
#include "crypto_sign.h"
#include "crypto_hash_sha512.h"
#include "ge.h"
#include "sc.h"
#include "zeroize.h"

/* NEW: Compare to pristine crypto_sign() 
   Uses explicit private key for nonce derivation and as scalar,
   instead of deriving both from a master key.
*/
int crypto_sign_modified(
  unsigned char *sm,
  const unsigned char *m,unsigned long long mlen,
  const unsigned char *sk, const unsigned char* pk,
  const unsigned char* random
)
{
  unsigned char nonce[64];
  unsigned char hram[64];
  ge_p3 R;
  int count=0;

  memmove(sm + 64,m,mlen);
  memmove(sm + 32,sk,32); /* NEW: Use privkey directly for nonce derivation */

  /* NEW : add prefix to separate hash uses - see .h */
  sm[0] = 0xFE;
  for (count = 1; count < 32; count++)
    sm[count] = 0xFF;

  /* NEW: add suffix of random data */
  memmove(sm + mlen + 64, random, 64);

  crypto_hash_sha512(nonce,sm,mlen + 128);
  memmove(sm + 32,pk,32);

  sc_reduce(nonce);
  ge_scalarmult_base(&R,nonce);
  ge_p3_tobytes(sm,&R);

  crypto_hash_sha512(hram,sm,mlen + 64);
  sc_reduce(hram);
  sc_muladd(sm + 32,hram,sk,nonce); /* NEW: Use privkey directly */

  return 0;
}
