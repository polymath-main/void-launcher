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
# UI audit fix 1 (CI run 41750644796): upstream QuickJS builds
# libquickjs with CONFIG_BIGNUM=y by default (Makefile:115-116).
# Without it, libbf.c compiles out the decimal/bignum definitions
# (USE_BF_DEC) while bf_atof_internal and JS_ReadBigNum still call
# them, so the Android link fails with undefined bfdec_normalize_
# and_round / mp_pow_dec. Defining it matches upstream's default
# libquickjs build; it does not change the Void.* host API.
target_compile_definitions(voidquickjs PRIVATE CONFIG_VERSION="2024-01-13" CONFIG_BIGNUM)
target_link_libraries(voidquickjs m)
if(NOT ANDROID)
    target_link_libraries(voidquickjs dl)
endif()

set_target_properties(voidquickjs PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED ON)
