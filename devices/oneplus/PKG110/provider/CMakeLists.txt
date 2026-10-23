cmake_minimum_required(VERSION 3.22)
project(portal_camera_provider C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(PORTAL_AIDL_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/aidl" CACHE PATH
    "Generated AIDL root (head/ and gen/); run scripts/gen-aidl.sh first")
if(NOT EXISTS "${PORTAL_AIDL_ROOT}/head" OR NOT EXISTS "${PORTAL_AIDL_ROOT}/gen")
    message(FATAL_ERROR
        "AIDL bindings missing under ${PORTAL_AIDL_ROOT}. Run scripts/gen-aidl.sh (requires Android SDK aidl) first.")
endif()
file(GLOB_RECURSE AIDL_SRCS ${PORTAL_AIDL_ROOT}/gen/*.cpp)
list(LENGTH AIDL_SRCS AIDL_SRC_COUNT)
if(AIDL_SRC_COUNT EQUAL 0)
    message(FATAL_ERROR "No generated AIDL C++ sources under ${PORTAL_AIDL_ROOT}/gen")
endif()
add_executable(portal_camera_provider
    src/main.cpp
    src/provider.cpp
    src/frame_reader.cpp
    src/selection_client.cpp
    src/video_source.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../camera-hal/src/jpeg_encode.c
    ${AIDL_SRCS})
target_include_directories(portal_camera_provider PRIVATE
    ${PORTAL_AIDL_ROOT}/head
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../camera-hal/src)
target_compile_options(portal_camera_provider PRIVATE -fno-sanitize=shadow-call-stack)
target_link_libraries(portal_camera_provider log dl binder_ndk mediandk android nativewindow)
