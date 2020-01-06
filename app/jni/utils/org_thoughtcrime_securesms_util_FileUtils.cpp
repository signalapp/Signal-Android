#include "org_thoughtcrime_securesms_util_FileUtils.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <linux/memfd.h>
#include <syscall.h>

jint JNICALL Java_org_thoughtcrime_securesms_util_FileUtils_getFileDescriptorOwner
  (JNIEnv *env, jclass clazz, jobject fileDescriptor)
{
  jclass fdClass = env->GetObjectClass(fileDescriptor);

  if (fdClass == NULL) {
    return -1;
  }

  jfieldID fdFieldId = env->GetFieldID(fdClass, "descriptor", "I");

  if (fdFieldId == NULL) {
    return -1;
  }

  int fd = env->GetIntField(fileDescriptor, fdFieldId);

  struct stat stat_struct;

  if (fstat(fd, &stat_struct) != 0) {
    return -1;
  }

  return stat_struct.st_uid;
}

JNIEXPORT jint JNICALL Java_org_thoughtcrime_securesms_util_FileUtils_createMemoryFileDescriptor
  (JNIEnv *env, jclass clazz, jstring jname)
{
  const char *name = env->GetStringUTFChars(jname, NULL);

  int fd = syscall(SYS_memfd_create, name, MFD_CLOEXEC);

  env->ReleaseStringUTFChars(jname, name);

  return fd;
}
