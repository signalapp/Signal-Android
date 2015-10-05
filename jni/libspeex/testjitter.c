#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <speex/speex_jitter.h>
#include <stdio.h>

union jbpdata {
  unsigned int idx;
  unsigned char data[4];
};

void synthIn(JitterBufferPacket *in, int idx, int span) {
  union jbpdata d;
  d.idx = idx;
  
  in->data = d.data;
  in->len = sizeof(d);
  in->timestamp = idx * 10;
  in->span = span * 10;
  in->sequence = idx;
  in->user_data = 0;
}

void jitterFill(JitterBuffer *jb) {
   char buffer[65536];
   JitterBufferPacket in, out;
   int i;

   out.data = buffer;
   
   jitter_buffer_reset(jb);

   for(i=0;i<100;++i) {
     synthIn(&in, i, 1);
     jitter_buffer_put(jb, &in);
     
     out.len = 65536;
     if (jitter_buffer_get(jb, &out, 10, NULL) != JITTER_BUFFER_OK) {
       printf("Fill test failed iteration %d\n", i);
     }
     if (out.timestamp != i * 10) {
       printf("Fill test expected %d got %d\n", i*10, out.timestamp);
     }
     jitter_buffer_tick(jb);
   }
}

int main()
{
   char buffer[65536];
   JitterBufferPacket in, out;
   int i;
   
   JitterBuffer *jb = jitter_buffer_init(10);
   
   out.data = buffer;
   
   /* Frozen sender case */
   jitterFill(jb);
   for(i=0;i<100;++i) {
     out.len = 65536;
     jitter_buffer_get(jb, &out, 10, NULL);
     jitter_buffer_tick(jb);
   }
   synthIn(&in, 100, 1);
   jitter_buffer_put(jb, &in);
   out.len = 65536;
   if (jitter_buffer_get(jb, &out, 10, NULL) != JITTER_BUFFER_OK) {
     printf("Failed frozen sender resynchronize\n");
   } else {
     printf("Frozen sender: Jitter %d\n", out.timestamp - 100*10);
   }
   return 0;
}
