#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "native-lib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT void JNICALL
Java_com_example_otgcam_MainActivity_startCamera(JNIEnv* env, jobject /* this */) {
    // Тут потрібно додати код для доступу до USB камери
    LOGI("Start Camera");
}
