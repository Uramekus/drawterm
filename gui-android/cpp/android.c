#include <jni.h>
#include <android/native_window.h>
#include <android/log.h>

#include "u.h"
#include "lib.h"
#include "dat.h"
#include "fns.h"

#include <draw.h>
#include <memdraw.h>
#include <keyboard.h>
#include <cursor.h>
#include "screen.h"

Memimage *gscreen = nil;
extern int screenWidth;
extern int screenHeight;
extern ANativeWindow *window;
extern jobject mainActivityObj;
extern JavaVM *jvm;

char*
clipread(void)
{
	char *ret;
	const char *s;
	JNIEnv *env;
	jint rs = (*jvm)->AttachCurrentThread(jvm, &env, NULL);
	if (rs != JNI_OK) {
		__android_log_print(ANDROID_LOG_WARN, "drawterm", "AttachCurrentThread returned error: %d", rs);
		return strdup("");
	}
	jclass clazz = (*env)->GetObjectClass(env, mainActivityObj);
	jmethodID methodID = (*env)->GetMethodID(env, clazz, "getClipBoard", "()Ljava/lang/String;");
        jstring str = (jstring)(*env)->CallObjectMethod(env, mainActivityObj, methodID);
	s = (*env)->GetStringUTFChars(env, str, NULL);
	ret = strdup(s);
	(*env)->ReleaseStringUTFChars(env, str, s);
	(*jvm)->DetachCurrentThread(jvm);
	return ret;
}

int
clipwrite(char *buf)
{
	JNIEnv *env;
	jint rs = (*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6);
	if(rs != JNI_OK) {
		__android_log_print(ANDROID_LOG_WARN, "drawterm", "GetEnv returned error: %d", rs);
		return 0;
	}
	jclass clazz = (*env)->GetObjectClass(env, mainActivityObj);
	jmethodID methodID = (*env)->GetMethodID(env, clazz, "setClipBoard", "(Ljava/lang/String;)V");
        jstring str = (*env)->NewStringUTF(env, buf);
	(*env)->CallVoidMethod(env, mainActivityObj, methodID, str);
	return 0;
}

void
show_notification(char *buf)
{
	JNIEnv *env;
	jint rs = (*jvm)->AttachCurrentThread(jvm, &env, NULL);
	if(rs != JNI_OK) {
		__android_log_print(ANDROID_LOG_WARN, "drawterm", "AttachCurrentThread returned error: %d", rs);
		return;
	}
	jclass clazz = (*env)->GetObjectClass(env, mainActivityObj);
	jmethodID methodID = (*env)->GetMethodID(env, clazz, "showNotification", "(Ljava/lang/String;)V");
        jstring str = (*env)->NewStringUTF(env, buf);
	(*env)->CallVoidMethod(env, mainActivityObj, methodID, str);
	(*jvm)->DetachCurrentThread(jvm);
	return;
}

int
num_cameras()
{
	JNIEnv *env;
	int n;
	jint rs = (*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6);
	if(rs != JNI_OK) {
		__android_log_print(ANDROID_LOG_WARN, "drawterm", "GetEnv returned error: %d", rs);
		return 0;
	}
	jclass clazz = (*env)->GetObjectClass(env, mainActivityObj);
	jmethodID methodID = (*env)->GetMethodID(env, clazz, "numCameras", "()I");
	n = (*env)->CallIntMethod(env, mainActivityObj, methodID);
	return n;
}

void
take_picture(int id)
{
	JNIEnv *env;
	jint rs = (*jvm)->AttachCurrentThread(jvm, &env, NULL);
	if(rs != JNI_OK) {
		__android_log_print(ANDROID_LOG_WARN, "drawterm", "AttachCurrentThread returned error: %d", rs);
		return;
	}
	jclass clazz = (*env)->GetObjectClass(env, mainActivityObj);
	jmethodID methodID = (*env)->GetMethodID(env, clazz, "takePicture", "(I)V");
	(*env)->CallVoidMethod(env, mainActivityObj, methodID, id);
	(*jvm)->DetachCurrentThread(jvm);
	return;
}

void
setcolor(ulong i, ulong r, ulong g, ulong b)
{
	return;
}

void
getcolor(ulong v, ulong *r, ulong *g, ulong *b)
{
	*r = (v>>16)&0xFF;
	*g = (v>>8)&0xFF;
	*b = v&0xFF;
}

void
flushmemscreen(Rectangle r)
{
	ANativeWindow_Buffer buffer;
	uint8_t *pixels;
	int x, y, o, b;
	ARect bounds;

	if (window == NULL || gscreen == NULL || gscreen->data == NULL)
		return;

	memset(&buffer, 0, sizeof(buffer));

	bounds.left = r.min.x;
	bounds.top = r.min.y;
	bounds.right = r.max.x;
	bounds.bottom = r.max.y;

	if (ANativeWindow_lock(window, &buffer, &bounds) != 0) {
		__android_log_print(ANDROID_LOG_WARN, "drawterm", "Unable to lock window buffer");
		return;
	}

	r.min.x = bounds.left;
	r.min.y = bounds.top;
	r.max.x = bounds.right;
	r.max.y = bounds.bottom;

	pixels = (uint8_t*)buffer.bits;
	for (y = r.min.y; y < r.max.y; y++)
		for (x = r.min.x; x < r.max.x; x++) {
			o = (y * screenWidth + x) * 4;
			b = (y * buffer.stride + x) * 4;
			pixels[b+3] = 0xFF;
			pixels[b+2] = gscreen->data->bdata[o+0];
			pixels[b+1] = gscreen->data->bdata[o+1];
			pixels[b+0] = gscreen->data->bdata[o+2];
		}

	if (ANativeWindow_unlockAndPost(window) != 0) {
		__android_log_print(ANDROID_LOG_WARN, "drawterm", "Unable to unlock and post window buffer");
	}
	return;
}

extern void (*_sysfatal)(char *fmt, va_list arg);
extern void (*os_panic_hook)(char *buf);

static void
android_sysfatal(char *fmt, va_list arg)
{
	char buf[1024];
	vseprint(buf, buf+sizeof(buf), fmt, arg);
	__android_log_print(ANDROID_LOG_FATAL, "drawterm", "sysfatal: %s", buf);
	show_notification(buf);
	exit(1);
}

void
screeninit(void)
{
	_sysfatal = android_sysfatal;
	os_panic_hook = show_notification;
	Rectangle r = Rect(0,0,screenWidth,screenHeight);
	memimageinit();
	screensize(r, XRGB32);
	if (gscreen == nil)
		panic("screensize failed");
	gscreen->clipr = r;
	qlock(&drawlock);
	terminit();
	flushmemscreen(r);
	qunlock(&drawlock);
	return;
}

void
screensize(Rectangle r, ulong chan)
{
	Memimage *mi;

	mi = allocmemimage(r, chan);
	if (mi == nil)
		return;

	if (gscreen != nil)
		freememimage(gscreen);

	gscreen = mi;
	gscreen->clipr = ZR;
}

Memdata*
attachscreen(Rectangle *r, ulong *chan, int *depth, int *width, int *softscreen)
{
	*r = gscreen->clipr;
	*depth = gscreen->depth;
	*chan = gscreen->chan;
	*width = gscreen->width;
	*softscreen = 1;
	gscreen->data->ref++;
	return gscreen->data;
}

extern jobject mainActivityObj;
extern JavaVM *jvm;

void
setcursor(void)
{
	JNIEnv *env;
	if (jvm == NULL || mainActivityObj == NULL) return;
	if ((*jvm)->GetEnv(jvm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
		if ((*jvm)->AttachCurrentThread(jvm, &env, NULL) != JNI_OK) {
			return;
		}
	}

	int pixels[256];
	int i, j, k = 0;
	for (i = 0; i < 16; i++) {
		uchar s1 = cursor.set[2*i];
		uchar s2 = cursor.set[2*i + 1];
		uchar c1 = cursor.clr[2*i];
		uchar c2 = cursor.clr[2*i + 1];

		for (j = 0; j < 8; j++) {
			int s = (s1 >> (7 - j)) & 1;
			int c = (c1 >> (7 - j)) & 1;
			int argb = 0;
			if (s) argb = 0xFF000000;
			else if (c) argb = 0xFFFFFFFF;
			pixels[k++] = argb;
		}
		for (j = 0; j < 8; j++) {
			int s = (s2 >> (7 - j)) & 1;
			int c = (c2 >> (7 - j)) & 1;
			int argb = 0;
			if (s) argb = 0xFF000000;
			else if (c) argb = 0xFFFFFFFF;
			pixels[k++] = argb;
		}
	}

	jintArray jPixels = (*env)->NewIntArray(env, 256);
	if (jPixels == NULL) return;
	(*env)->SetIntArrayRegion(env, jPixels, 0, 256, (jint*)pixels);

	jclass cls = (*env)->GetObjectClass(env, mainActivityObj);
	jmethodID mid = (*env)->GetMethodID(env, cls, "setCursor", "([III)V");
	if (mid) {
		int hotX = cursor.offset.x < 0 ? -cursor.offset.x : cursor.offset.x;
		int hotY = cursor.offset.y < 0 ? -cursor.offset.y : cursor.offset.y;
		(*env)->CallVoidMethod(env, mainActivityObj, mid, jPixels, hotX, hotY);
	}
	(*env)->DeleteLocalRef(env, jPixels);
	(*env)->DeleteLocalRef(env, cls);
}

void
mouseset(Point xy)
{
	return;
}

void
guimain(void)
{
	cpubody();
}

