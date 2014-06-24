#include <string.h>
#include "crypto_sign.h"
#include "crypto_hash_sha512.h"
#include "ge.h"
#include "sc.h"

/* NEW: Compare to pristine crypto_sign() 
   Uses explicit private key for nonce derivation and as scalar,
   instead of deriving both from a master key.
*/
int crypto_sign_modified(
  unsigned char *sm,unsigned long long *smlen,
  const unsigned char *m,unsigned long long mlen,
  const unsigned char *sk
)
{
  unsigned char pk[32];
  unsigned char az[64];
  unsigned char nonce[64];
  unsigned char hram[64];
  ge_p3 R;

  memmove(pk,sk + 32,32);

  *smlen = mlen + 64;
  memmove(sm + 64,m,mlen);
  memmove(sm + 32,sk,32); /* NEW: Use privkey directly for nonce derivation */
  crypto_hash_sha512(nonce,sm + 32,mlen + 32);
  memmove(sm + 32,pk,32);

  sc_reduce(nonce);
  ge_scalarmult_base(&R,nonce);
  ge_p3_tobytes(sm,&R);

  crypto_hash_sha512(hram,sm,mlen + 64);
  sc_reduce(hram);
  sc_muladd(sm + 32,hram,sk,nonce); /* NEW: Use privkey directly */

  return 0;
}
