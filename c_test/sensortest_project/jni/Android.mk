LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE    := sensortest  # name your module here.
LOCAL_SRC_FILES := main.c
LOCAL_LDLIBS    := -landroid
#include $(BUILD_SHARED_LIBRARY)

include $(BUILD_EXECUTABLE)
