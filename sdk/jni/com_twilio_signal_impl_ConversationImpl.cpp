#include "com_twilio_signal_impl_ConversationImpl.h"
#include "talk/app/webrtc/java/jni/jni_helpers.h"
#include "TSCoreSDKTypes.h"
#include "TSCoreError.h"
#include "TSCLogger.h"
#include "TSCEndpoint.h"
#include "TSCSessionObserver.h"
#include "TSCVideoCaptureController.h"
#include "TSCSession.h"
#include "TSCParticipant.h"
#include "TSCAudioInputController.h"

#include <string>
#include <map>
#include <vector>


using namespace twiliosdk;

#define TAG  "TwilioSDK(native)"


JNIEXPORT jlong JNICALL Java_com_twilio_signal_impl_ConversationImpl_wrapOutgoingSession
  (JNIEnv *env, jobject obj, jlong nativeEndpoint, jlong nativeSessionObserver, jobjectArray participantList)
{

	TSCEndpoint* endpoint = reinterpret_cast<TSCEndpoint*>(nativeEndpoint);
	TSCOptions options;
	options.insert(std::pair<std::string,std::string>("audio","yes"));
	options.insert(std::pair<std::string,std::string>("video","yes"));

	TSCSessionObserverObjectRef sessionObserver =
			TSCSessionObserverObjectRef(reinterpret_cast<TSCSessionObserverObject*>(nativeSessionObserver));
	if (sessionObserver.get() == NULL) {
		TS_CORE_LOG_DEBUG("sessionObserver was null. Exiting");
		return 0;
	}

	TSCOutgoingSessionObjectRef outgoingSession = endpoint->createSession(options, sessionObserver);

	if (outgoingSession.get() == NULL) {
		TS_CORE_LOG_DEBUG("outgoingSession was null. Exiting");
		return 0;
	}

	int size = env->GetArrayLength(participantList);
	if (size == 0) {
		TS_CORE_LOG_DEBUG("no participants were provided");
		return 0;
	}

	std::vector<TSCParticipant> participants;
	for (int i=0; i < size; i++) {
		jstring value = (jstring)env->GetObjectArrayElement(participantList, i);
		const char *nativeString = env->GetStringUTFChars(value, 0);
		std::string participantStr(nativeString);
		env->ReleaseStringUTFChars(value, nativeString);
		TSCParticipant participant(participantStr);
		participants.push_back(participant);
	}

	outgoingSession->setParticipants(participants);
	return (jlong)outgoingSession.release();
}


JNIEXPORT void JNICALL Java_com_twilio_signal_impl_ConversationImpl_start
  (JNIEnv *env, jobject obj, jlong nativeSession)
{
	TS_CORE_LOG_DEBUG("start");
	TSCSessionObject* session = reinterpret_cast<TSCSessionObject*>(nativeSession);
	session->start();
}


JNIEXPORT void JNICALL Java_com_twilio_signal_impl_ConversationImpl_stop
  (JNIEnv *env, jobject obj, jlong nativeSession)
{
	TS_CORE_LOG_DEBUG("stop");
	TSCSessionObject* session = reinterpret_cast<TSCSessionObject*>(nativeSession);
	session->stop();
}

JNIEXPORT void JNICALL Java_com_twilio_signal_impl_ConversationImpl_setExternalCapturer
  (JNIEnv *env, jobject obj, jlong nativeSession, jlong nativeCapturer)
{
	TS_CORE_LOG_DEBUG("setExternalCapturer");
	TSCSessionObject* session = reinterpret_cast<TSCSessionObject*>(nativeSession);
	TSCVideoCaptureControllerPtr videoCaptureController = session->getVideoCaptureController();
	if(videoCaptureController != nullptr) {
		videoCaptureController->setExternalVideoCapturer(reinterpret_cast<cricket::VideoCapturer*>(nativeCapturer));
	} else {
		TS_CORE_LOG_DEBUG("videoCapturerController was null");
	}
}

JNIEXPORT void JNICALL Java_com_twilio_signal_impl_ConversationImpl_setSessionObserver
  (JNIEnv *, jobject, jlong nativeSession, jlong nativeSessionObserver)
{
	TS_CORE_LOG_DEBUG("setSessionObserver");
	TSCSessionObject* session = reinterpret_cast<TSCSessionObject*>(nativeSession);
	TSCSessionObserverObjectRef sessionObserver =
				TSCSessionObserverObjectRef(reinterpret_cast<TSCSessionObserverObject*>(nativeSessionObserver));
	session->setSessionObserver(sessionObserver);
}

JNIEXPORT jboolean JNICALL Java_com_twilio_signal_impl_ConversationImpl_enableVideo
  (JNIEnv *, jobject, jlong nativeSession, jboolean enabled, jboolean paused)
{
	TS_CORE_LOG_DEBUG("enableVideo");
	TSCSessionObject* session = reinterpret_cast<TSCSessionObject*>(nativeSession);
	return (session->enableVideo((bool)enabled, (bool)paused) ? JNI_TRUE : JNI_FALSE);
}

JNIEXPORT void JNICALL Java_com_twilio_signal_impl_ConversationImpl_freeNativeHandle
  (JNIEnv *env, jobject obj, jlong nativeSession)
{
	// NOTE: The core destroys the Session once it has stopped.
	// We do not need to call Release() in this case.
}

JNIEXPORT jboolean JNICALL Java_com_twilio_signal_impl_ConversationImpl_mute
  (JNIEnv *, jobject, jlong nativeSession, jboolean on)
{
	TS_CORE_LOG_DEBUG("mute");
	TSCSessionObject* session = reinterpret_cast<TSCSessionObject*>(nativeSession);
	TSCAudioInputControllerPtr audioInputCtrl = session->getAudioInputController();
	if (audioInputCtrl != nullptr) {
		return audioInputCtrl->setMuted(on) ? JNI_TRUE : JNI_FALSE;
	}
	return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_twilio_signal_impl_ConversationImpl_isMuted
  (JNIEnv *, jobject, jlong nativeSession)
{
	TS_CORE_LOG_DEBUG("isMuted");
	TSCSessionObject* session = reinterpret_cast<TSCSessionObject*>(nativeSession);
	TSCAudioInputControllerPtr audioInputCtrl = session->getAudioInputController();
	if (audioInputCtrl != nullptr) {
		return audioInputCtrl->isMuted() ? JNI_TRUE : JNI_FALSE;
	}
	return JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_twilio_signal_impl_ConversationImpl_inviteParticipants
  (JNIEnv *env, jobject obj, jlong nativeSession, jobjectArray participantList)
{
	TSCSessionObject* session = reinterpret_cast<TSCSessionObject*>(nativeSession);
	int size = env->GetArrayLength(participantList);
	if (size == 0) {
		TS_CORE_LOG_DEBUG("no participants were provided");
		return;
	}

	std::vector<TSCParticipant> participants;
	for (int i=0; i < size; i++) {
		jstring value = (jstring)env->GetObjectArrayElement(participantList, i);
		std::string participantStr = webrtc_jni::JavaToStdString(env, value);
		TSCParticipant participant(participantStr);
		participants.push_back(participant);
	}
	session->inviteParticipants(participants);
}

