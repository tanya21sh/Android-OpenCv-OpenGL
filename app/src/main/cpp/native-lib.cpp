#include <jni.h>

#include <memory>
#include <mutex>
#include <vector>

#include "EdgeProcessor.h"

namespace {
constexpr char kTag[] = "NativeBridge";
std::mutex gMutex;
std::unique_ptr<EdgeProcessor> gProcessor;

EdgeProcessor& processorInstance() {
    if (!gProcessor) {
        gProcessor = std::make_unique<EdgeProcessor>();
    }
    return *gProcessor;
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_edgerenderer_nativebridge_NativeBridge_nativeInit(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(gMutex);
    processorInstance();
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_edgerenderer_nativebridge_NativeBridge_nativeProcessFrame(
        JNIEnv* env,
        jobject /*thiz*/,
        jbyteArray nv21Frame,
        jint width,
        jint height,
        jint mode) {
    if (nv21Frame == nullptr || width <= 0 || height <= 0) {
        return env->NewByteArray(0);
    }

    std::vector<uint8_t> output;

    {
        std::lock_guard<std::mutex> lock(gMutex);
        auto& processor = processorInstance();
        jbyte* frameBytes = env->GetByteArrayElements(nv21Frame, nullptr);
        output = processor.process(
                reinterpret_cast<uint8_t*>(frameBytes),
                width,
                height,
                static_cast<EdgeProcessor::Mode>(mode));
        env->ReleaseByteArrayElements(nv21Frame, frameBytes, JNI_ABORT);
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(output.size()));
    if (output.empty()) {
        return result;
    }
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(output.size()),
                            reinterpret_cast<const jbyte*>(output.data()));
    return result;
}
