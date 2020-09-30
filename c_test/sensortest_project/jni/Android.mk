LOCAL_PATH := $(call my-dir)

#include $(CLEAR_VARS)
#LOCAL_MODULE := sensordata
#LOCAL_SRC_FILES := SensorData.cpp
#include $(BUILD_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := datacontainer
#LOCAL_SRC_FILES := DataContainer.cpp
#LOCAL_SHARED_LIBRARIES := sensordata
#include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE    := sensortest  # name your module here.
LOCAL_SRC_FILES := main.cpp DataContainer.cpp SensorData.cpp
LOCAL_LDLIBS    := -landroid
#LOCAL_SHARED_LIBRARIES := datacontainer
#include $(BUILD_SHARED_LIBRARY)

include $(BUILD_EXECUTABLE)
