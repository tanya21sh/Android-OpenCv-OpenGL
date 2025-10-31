cmake_minimum_required(VERSION 3.22.1)

project(edge_processor)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_library(
        edgeproc
        SHARED
        native-lib.cpp
        ../../jni/src/EdgeProcessor.cpp
)

find_package(OpenCV REQUIRED core imgproc)

find_library(
        log-lib
        log)
find_library(
        android-lib
        android)
find_library(
        jnigraphics-lib
        jnigraphics)

target_include_directories(
        edgeproc
        PRIVATE
        ../../jni/include
        ${OpenCV_INCLUDE_DIRS}
)

target_link_libraries(
        edgeproc
        PRIVATE
        ${OpenCV_LIBS}
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
)
