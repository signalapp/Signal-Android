#include <jni.h>
#include <webp/demux.h>

jobject createBitmap(JNIEnv *env, int width, int height, const uint8_t *pixels) {
    static auto      jbitmapConfigClass         = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("android/graphics/Bitmap$Config")));
    static jfieldID  jbitmapConfigARGB8888Field = env->GetStaticFieldID(jbitmapConfigClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    static auto      jbitmapClass               = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("android/graphics/Bitmap")));
    static jmethodID jbitmapCreateBitmapMethod  = env->GetStaticMethodID(jbitmapClass, "createBitmap", "([IIIIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jintArray intArray = env->NewIntArray(width * height);
    env->SetIntArrayRegion(intArray, 0, width * height, reinterpret_cast<const jint *>(pixels));

    jobject argb8888Config = env->GetStaticObjectField(jbitmapConfigClass, jbitmapConfigARGB8888Field);
    jobject jbitmap        = env->CallStaticObjectMethod(jbitmapClass, jbitmapCreateBitmapMethod, intArray, 0, width, width, height, argb8888Config);
    env->DeleteLocalRef(argb8888Config);

    return jbitmap;
}

jobject nativeDecodeBitmap(JNIEnv *env, jobject, jbyteArray data) {
    jbyte *javaBytes    = env->GetByteArrayElements(data, nullptr);
    auto  *buffer       = reinterpret_cast<uint8_t *>(javaBytes);
    jsize  bufferLength = env->GetArrayLength(data);

    WebPBitstreamFeatures features;
    WebPGetFeatures(buffer, bufferLength, &features);

    int width;
    int height;

    uint8_t *pixels = WebPDecodeBGRA(buffer, bufferLength, &width, &height);
    jobject jbitmap = createBitmap(env, width, height, pixels);

    WebPFree(pixels);
    env->ReleaseByteArrayElements(data, javaBytes, 0);

    return jbitmap;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass c = env->FindClass("org/signal/glide/webp/WebpDecoder");
    if (c == nullptr) {
        return JNI_ERR;
    }

    static const JNINativeMethod methods[] = {
            {"nativeDecodeBitmap", "([B)Landroid/graphics/Bitmap;", reinterpret_cast<void *>(nativeDecodeBitmap)}
    };

    int rc = env->RegisterNatives(c, methods, sizeof(methods) / sizeof(JNINativeMethod));

    if (rc != JNI_OK) {
        return rc;
    }

    return JNI_VERSION_1_6;
}
