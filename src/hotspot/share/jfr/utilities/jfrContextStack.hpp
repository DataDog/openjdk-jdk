#ifndef SHARE_JFR_SUPPORT_JFRCONTEXTSTACK_HPP
#define SHARE_JFR_SUPPORT_JFRCONTEXTSTACK_HPP

#include "jni.h"

#include "jfrLinkedList.inline.hpp"

class ContextHolder : public JfrCHeapObj {
 public:
  ContextHolder* _next;
  const u8 id;
  u1 flag;

  ContextHolder(u8 context_id) : id(context_id), flag(0) {}
};

class JfrContextStack : public JfrCHeapObj {
 private:
  JfrLinkedList<ContextHolder> _stack;
 public:
  void push(u8 context_id);
  u1 pop(u8 context_id);
  u1* peek();
};
#endif // SHARE_JFR_SUPPORT_JFRCONTEXTSTACK_HPP
