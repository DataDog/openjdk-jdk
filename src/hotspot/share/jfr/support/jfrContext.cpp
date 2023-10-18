#include "precompiled.hpp"
#include "jfrContext.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "runtime/thread.hpp"

#ifndef MIN
#define MIN(a,b) ((a) < (b) ? (a) : (b))
#endif

uint8_t JfrContext::_size = 0;

JfrThreadLocal* getThreadLocal() {
    Thread* thrd = Thread::current_or_null_safe();
    return thrd != nullptr ? thrd->jfr_thread_local() : nullptr;
}

void JfrContext::set_used_context_size(uint8_t size) {
    assert(size <= 8, "max context size is 8");
    _size = size;
}

uint64_t JfrContext::get_and_set_context(uint8_t idx, uint64_t value) {
    JfrThreadLocal* jfrTLocal = getThreadLocal();
    if (jfrTLocal != nullptr) {
        uint64_t old_value = 0;
        jfrTLocal->get_context(&old_value, 1, idx);
        jfrTLocal->set_context(&value, 1, idx);
        return old_value;
    }
    return 0;
}

uint8_t JfrContext::get_all_context(uint64_t* data, uint8_t length) {
    JfrThreadLocal* jfrTLocal = getThreadLocal();
    if (jfrTLocal != nullptr) {
        return jfrTLocal->get_context(data, MIN(_size, length), 0);
    }

    return 0;
}

uint8_t JfrContext::get_context(uint64_t** data) {
    void* buffer = get_thread_context_buffer();
    if (buffer) {
        *data = (uint64_t*) buffer;
        return _size;
    }
    return 0;
}

uint8_t JfrContext::set_all_context(uint64_t* data, uint8_t length) {
    JfrThreadLocal* jfrTLocal = getThreadLocal();
    if (jfrTLocal != nullptr) {
        jfrTLocal->set_context(data, MIN(_size, length), 0);
        return length;
    }

    return 0;
}

void* JfrContext::get_thread_context_buffer() {
    JfrThreadLocal* jfrTLocal = getThreadLocal();
    if (jfrTLocal != nullptr) {
        return (void*)jfrTLocal->get_context_buffer();
    }
    return nullptr;
}