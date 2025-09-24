LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_PACKAGE_NAME := AB_OTATEST
LOCAL_CERTIFICATE := testkey     # must match platform.pk8/platform.x509.pem
LOCAL_PRIVILEGED_MODULE := true   # only with platform key
LOCAL_DEX_PREOPT := false
LOCAL_PRIVATE_PLATFORM_APIS := true

include $(BUILD_PACKAGE)
