#include "EdgeProcessor.h"

#include <cstring>
#include <opencv2/imgproc.hpp>

namespace {
constexpr char kTag[] = "EdgeProcessor";

void ensureCapacity(cv::Mat& mat, int rows, int cols, int type) {
    if (mat.empty() || mat.rows != rows || mat.cols != cols || mat.type() != type) {
        mat.create(rows, cols, type);
    }
}
}

EdgeProcessor::EdgeProcessor() = default;

std::vector<uint8_t> EdgeProcessor::process(const uint8_t* nv21, int width, int height, Mode mode) {
    if (nv21 == nullptr || width <= 0 || height <= 0) {
        return {};
    }

    std::lock_guard<std::mutex> lock(mutex_);
    const Mode effectiveMode = mode == Mode::kCanny ? Mode::kCanny : Mode::kRaw;

    const int yuvRows = height + height / 2;
    ensureCapacity(yuvFrame_, yuvRows, width, CV_8UC1);
    std::memcpy(yuvFrame_.data, nv21, static_cast<size_t>(width) * height * 3 / 2);

    ensureCapacity(rgbaFrame_, height, width, CV_8UC4);
    cv::cvtColor(yuvFrame_, rgbaFrame_, cv::COLOR_YUV2RGBA_NV21);

    if (effectiveMode == Mode::kCanny) {
        ensureCapacity(grayFrame_, height, width, CV_8UC1);
        ensureCapacity(blurredFrame_, height, width, CV_8UC1);
        ensureCapacity(edgesFrame_, height, width, CV_8UC1);

        cv::cvtColor(rgbaFrame_, grayFrame_, cv::COLOR_RGBA2GRAY);
        cv::GaussianBlur(grayFrame_, blurredFrame_, cv::Size(5, 5), 1.4);
        cv::Canny(blurredFrame_, edgesFrame_, 40.0, 120.0);
        cv::cvtColor(edgesFrame_, rgbaFrame_, cv::COLOR_GRAY2RGBA);
    }

    const auto dataSize = static_cast<size_t>(rgbaFrame_.total() * rgbaFrame_.elemSize());
    std::vector<uint8_t> output(dataSize);
    std::memcpy(output.data(), rgbaFrame_.data, dataSize);
    return output;
}
