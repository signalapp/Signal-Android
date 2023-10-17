#include <android/log.h>
#include <jni.h>
#include <webp/demux.h>

#include <algorithm>
#include <string>

#define TAG "WebpResourceDecoder"

jobject createBitmap(JNIEnv *env, int width, int height, const uint8_t *pixels) {
    static auto      jbitmapConfigClass         = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("android/graphics/Bitmap$Config")));
    static jfieldID  jbitmapConfigARGB8888Field = env->GetStaticFieldID(jbitmapConfigClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    static auto      jbitmapClass               = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass("android/graphics/Bitmap")));
    static jmethodID jbitmapCreateBitmapMethod  = env->GetStaticMethodID(jbitmapClass, "createBitmap", "([IIIIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jintArray intArray = env->NewIntArray(width * height);
    env->SetIntArrayRegion(intArray, 0, width * height, reinterpret_cast<const jint *>(pixels));

    jobject argb8888Config = env->GetStaticObjectField(jbitmapConfigClass, jbitmapConfigARGB8888Field);
    jobject jbitmap        = env->CallStaticObjectMethod(jbitmapClass, jbitmapCreateBitmapMethod, intArray, 0, width, width, height, argb8888Config);

    return jbitmap;
}

jobject nativeDecodeBitmapScaled(JNIEnv *env, jobject, jbyteArray data, jint requestedWidth, jint requestedHeight) {
    jbyte *javaBytes    = env->GetByteArrayElements(data, nullptr);
    auto  *buffer       = reinterpret_cast<uint8_t *>(javaBytes);
    jsize  bufferLength = env->GetArrayLength(data);

    WebPBitstreamFeatures features;
    if (WebPGetFeatures(buffer, bufferLength, &features) != VP8_STATUS_OK) {
        __android_log_write(ANDROID_LOG_WARN, TAG, "GetFeatures");
        env->ReleaseByteArrayElements(data, javaBytes, JNI_ABORT);
        return nullptr;
    }

    WebPDecoderConfig config;
    if (!WebPInitDecoderConfig(&config)) {
        __android_log_write(ANDROID_LOG_WARN, TAG, "Init decoder config");
        env->ReleaseByteArrayElements(data, javaBytes, JNI_ABORT);
        return nullptr;
    }

    config.options.no_fancy_upsampling = 1;
    config.output.colorspace           = MODE_BGRA;

    if (requestedWidth > 0 && requestedHeight > 0 && features.width > 0 && features.height > 0 && (requestedWidth < features.width || requestedHeight < features.height)) {
        double widthScale    = static_cast<double>(requestedWidth)  / features.width;
        double heightScale   = static_cast<double>(requestedHeight) / features.height;
        double requiredScale = std::min(widthScale, heightScale);

        config.options.use_scaling   = 1;
        config.options.scaled_width  = static_cast<int>(requiredScale * features.width);
        config.options.scaled_height = static_cast<int>(requiredScale * features.height);
    }

    uint8_t *pixels = nullptr;
    int width       = 0;
    int height      = 0;

    VP8StatusCode result = WebPDecode(buffer, bufferLength, &config);
    if (result != VP8_STATUS_OK) {
        __android_log_write(ANDROID_LOG_WARN, TAG, ("Scaled WebPDecode failed (" + std::to_string(result) + ")").c_str());
    } else {
        pixels = config.output.u.RGBA.rgba;
        width  = config.output.width;
        height = config.output.height;
    }

    jobject jbitmap = nullptr;
    if (pixels != nullptr) {
        jbitmap = createBitmap(env, width, height, pixels);
    }

    WebPFree(pixels);
    env->ReleaseByteArrayElements(data, javaBytes, JNI_ABORT);

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
            {"nativeDecodeBitmapScaled", "([BII)Landroid/graphics/Bitmap;", reinterpret_cast<void *>(nativeDecodeBitmapScaled)}
    };

    int rc = env->RegisterNatives(c, methods, sizeof(methods) / sizeof(JNINativeMethod));

    if (rc != JNI_OK) {
        return rc;
    }

    return JNI_VERSION_1_6;
}
