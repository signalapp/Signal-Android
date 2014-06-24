#include "sha512.h"
#include "sph_sha2.h"

int crypto_hash_sha512_ref(unsigned char *output ,const unsigned char *input,
                           unsigned long long len)
{
  sph_sha512_context ctx;
  sph_sha512_init(&ctx);  
  sph_sha512(&ctx, input, len);
  sph_sha512_close(&ctx, output);
  return 0;
}

