TWSDK_JNI_PATH := $(call my-dir)

include $(TWSDK_JNI_PATH)/conversations-core.mk

# WEBRTC JNI
include $(CLEAR_VARS)
LOCAL_MODULE := webrtc-jni
LOCAL_SRC_FILES := $(PREFIX)/webrtc/android/armeabiv7a/lib/libwebrtc-jni.a
LOCAL_EXPORT_C_INCLUDES := $(PREFIX)/webrtc/android/armeabiv7a/include
include $(PREBUILT_STATIC_LIBRARY)

ifneq ($(ENABLE_PROFILING),)
include $(TWSDK_JNI_PATH)/../thirdparty/android-ndk-profiler/jni/Android.mk
endif

HPTEMP = $(shell uname -s)
HOST_PLATFORM = $(shell echo $(HPTEMP) | tr A-Z a-z)

ifeq ($(HOST_PLATFORM),linux)
    TOOLCHAIN_PLAT = linux-x86_64
else
    ifeq ($(HOST_PLATFORM),darwin)
        TOOLCHAIN_PLAT = darwin-x86_64
    else
        $(error Host platform not supported)
    endif
endif

LOCAL_PATH := $(TWSDK_JNI_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE := twilio-native
LOCAL_SRC_FILES := \
	dummy.cpp \
	com_twilio_conversations_impl_TwilioConversationsImpl.cpp \
	com_twilio_conversations_impl_ConversationsClientImpl.cpp \
	com_twilio_conversations_impl_ConversationsClientImpl_EndpointObserverInternal.cpp \
	com_twilio_conversations_impl_ConversationImpl.cpp \
	com_twilio_conversations_impl_ConversationImpl_SessionObserverInternal.cpp

ifeq ($(shell test "$(APP_DEBUGGABLE)" = "true" -o "$(NDK_DEBUG)" = "1" && echo true || echo false),true)
debug_cflags := \
	-DENABLE_PJSIP_LOGGING \
	-DENABLE_JNI_DEBUG_LOGGING \
	-D_DEBUG
endif


LOCAL_CFLAGS += \
	-Wall \
	-DPOSIX \
	-fvisibility=hidden \
	-DTW_EXPORT='__attribute__((visibility("default")))' \
	$(debug_cflags)

LOCAL_CPPFLAGS := -std=gnu++11 -fno-rtti

LOCAL_LDLIBS := \
	-llog \
	-lz \
	-lm \
	-ldl \
	-lGLESv2 \
	-ljnigraphics \
	-lOpenSLES \
	-lEGL \
	-lGLESv1_CM \
	-landroid \


LOCAL_STATIC_LIBRARIES := \
	twilio-sdk-core \
	access-manager \
	PocoNetSSL \
	PocoCrypto \
	PocoNet \
	PocoUtil \
	PocoXML \
	PocoJSON \
	PocoFoundation \
	boringssl \
	webrtc-jni \


# Make JNI_OnLoad a local symbol in libwebrtc-jni.a since it is already defined by libtwilio-jni.a
# dummy.cpp is a fake depedency that causes this command to run prior to linking
LOCALIZE_SYMBOL := $(LOCAL_PATH)/dummy.cpp

$(LOCALIZE_SYMBOL):
	@echo "Localize the JNI_OnLoad symbol in libwebrtc.a to prevent conflicts with initialization in initCore"
	$(ANDROID_NDK_HOME)/toolchains/aarch64-linux-android-4.9/prebuilt/$(TOOLCHAIN_PLAT)/aarch64-linux-android/bin/objcopy --localize-symbol JNI_OnLoad $(PREFIX)/webrtc/android/armeabiv7a/lib/libwebrtc-jni.a
	touch $(LOCALIZE_SYMBOL)

.INTERMEDIATE: $(LOCALIZE_SYMBOL)

include $(BUILD_SHARED_LIBRARY)
