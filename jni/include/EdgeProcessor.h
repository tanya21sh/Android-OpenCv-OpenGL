#pragma once

#include <cstdint>
#include <vector>
#include <opencv2/core.hpp>
#include <mutex>

class EdgeProcessor {
public:
    enum class Mode {
        kRaw = 0,
        kCanny = 1
    };

    EdgeProcessor();
    ~EdgeProcessor() = default;

    std::vector<uint8_t> process(const uint8_t* nv21, int width, int height, Mode mode);

private:
    std::mutex mutex_;
    cv::Mat yuvFrame_;
    cv::Mat rgbaFrame_;
    cv::Mat grayFrame_;
    cv::Mat blurredFrame_;
    cv::Mat edgesFrame_;
};
