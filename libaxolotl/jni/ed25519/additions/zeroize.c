#include "zeroize.h"

void zeroize(unsigned char* b, size_t len)
{
  size_t count = 0;
  unsigned long retval = 0;
  volatile unsigned char *p = b;

  for (count = 0; count < len; count++)
    p[count] = 0;
}

void zeroize_stack()
{
  unsigned char m[ZEROIZE_STACK_SIZE];
  zeroize(m, sizeof m);
}
