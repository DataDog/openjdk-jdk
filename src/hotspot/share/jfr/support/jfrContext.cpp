#include "jfrContext.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrContextStack.hpp"
#include "runtime/threads.hpp"
#include "utilities/debug.hpp"

void JfrContext::push(u8 context_id) {
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  JfrContextStack* ctx = tl->get_context_stack();
  ctx->push(context_id);
}

u1 JfrContext::pop(u8 context_id) {
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  JfrContextStack* ctx = tl->get_context_stack();
  return ctx->pop(context_id);
}

u1* JfrContext::peek() {
  JavaThread* const jt = JavaThread::current();
  assert(jt != nullptr, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_native(jt));
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  JfrContextStack* ctx = tl->get_context_stack();
  return ctx->peek();
}

