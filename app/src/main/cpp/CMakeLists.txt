# CMake for the voidquickjs native library.
# Built by Gradle (externalNativeBuild) into the APK as libvoidquickjs.so,
# and by the host test via plain gcc (see vq_host_test.c header).
# QuickJS 2024-01-13 sources are vendored under quickjs/ (BSD/MIT).
cmake_minimum_required(VERSION 3.22.1)
project(voidquickjs C)

add_library(voidquickjs SHARED
    voidquickjs.c
    quickjs/quickjs.c
    quickjs/libregexp.c
    quickjs/libunicode.c
    quickjs/cutils.c
    quickjs/libbf.c)

target_include_directories(voidquickjs PRIVATE quickjs)
target_compile_definitions(voidquickjs PRIVATE CONFIG_VERSION="2024-01-13")
target_link_libraries(voidquickjs m)
if(NOT ANDROID)
    target_link_libraries(voidquickjs dl)
endif()

set_target_properties(voidquickjs PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED ON)
