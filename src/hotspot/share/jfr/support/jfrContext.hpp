#ifndef SHARE_JFR_SUPPORT_JFRCONTEXT_HPP
#define SHARE_JFR_SUPPORT_JFRCONTEXT_HPP

#include "jni.h"

class JfrContext : public AllStatic {
 public:
  static void push(u8 context_id);
  static u1 pop(u8 context_id);
  static u1* peek();
};

#endif // SHARE_JFR_SUPPORT_JFRCONTEXT_HPP
