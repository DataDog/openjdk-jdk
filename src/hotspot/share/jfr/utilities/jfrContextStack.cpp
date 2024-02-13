#include "jfrContextStack.hpp"
#include "utilities/debug.hpp"

void JfrContextStack::push(u8 context_id) {
  ContextHolder* holder = new ContextHolder(context_id);
  _stack.add(holder);
}

u1 JfrContextStack::pop(u8 context_id) {
  ContextHolder* holder = _stack.remove();
  assert(holder->id == context_id, "invariant");
  u1 ret = holder->flag;
  delete holder;
  return ret;
}

u1* JfrContextStack::peek() {
  ContextHolder* h = _stack.head();
  return h ? &(h->flag) : nullptr;
}

