/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include <stdio.h>
#include <string.h>
#include "jvmti.h"
#include "jvmti_common.h"
#include "../get_stack_trace.h"


extern "C" {

static jvmtiEnv *jvmti = NULL;
static jmethodID* ids = NULL;
static int ids_size = 0;

JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == NULL) {
    printf("Wrong result of a valid call to GetEnv!\n");
    return JNI_ERR;
  }
  ids = (jmethodID*)malloc(sizeof(jmethodID) * 10);
  return JNI_OK;
}

JNIEXPORT void JNICALL
Java_GetStackTraceAndRetransformTest_initialize(JNIEnv *env, jclass cls, jclass tgt) {
    env->GetStaticMethodID(tgt, "callAction1", "()V");
    env->GetStaticMethodID(tgt, "callAction2", "()V");
    env->GetStaticMethodID(tgt, "callAction3", "()V");
}

JNIEXPORT void JNICALL
Java_GetStackTraceAndRetransformTest_capture(JNIEnv *env, jclass cls, jthread thread) {
//  const char* m_name = env->GetStringUTFChars(method_name, NULL);

  jint count;
  const int MAX_NUMBER_OF_FRAMES = 32;
  jvmtiFrameInfo frames[MAX_NUMBER_OF_FRAMES];

  jvmtiError err = jvmti->GetStackTrace(thread, 0, MAX_NUMBER_OF_FRAMES, frames, &count);
  check_jvmti_status(env, err, "GetStackTrace failed.");

  for (int i = 0; i < count; i++) {
    fprintf(stderr, "Frame mid: %d = %p (%p)\n", i, frames[i].method, *(void**)frames[i].method);
  }

  ids[ids_size++] = frames[1].method;

//
//  jmethodID mid = env->GetStaticMethodID(tgt, m_name, "()V");
//  if (mid == NULL) {
//    printf("Failed to get method id\n");
//    return;
//  }
//  if (ids_size == 10) {
//    printf("Too many methods\n");
//    return;
//  }
//  ids[ids_size++] = mid;
//  fprintf(stderr, "Captured %p -> %p\n", mid, *(void**)mid);
//
//  env->ReleaseStringUTFChars(method_name, m_name);
}

JNIEXPORT void JNICALL
Java_GetStackTraceAndRetransformTest_check(JNIEnv *jni, jclass cls) {
  for (int i = 0; i < ids_size; i++) {
    fprintf(stderr, "Checking %d: %p\n", i, ids[i]);
    jclass rslt = NULL;
    char* class_name = NULL;
    jvmti->GetMethodDeclaringClass(ids[i], &rslt);
    if (rslt != NULL) {
        jvmti->GetClassSignature(rslt, &class_name, NULL);
    }
    fprintf(stderr, "===> %p -> %p :: %s\n", ids[i], *(void**)ids[i], class_name == NULL ? "<NULL>" : class_name);
  }
}
}
