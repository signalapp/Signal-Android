
void zeroize(unsigned char* b, unsigned long len)
{
  unsigned long count = 0;
  unsigned long retval = 0;
  volatile unsigned char *p = b;

  for (count = 0; count < len; count++)
    p[count] = 0;
}
