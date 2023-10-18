#ifndef SHARE_JFR_SUPPORT_CONTEXT_HPP
#define SHARE_JFR_SUPPORT_CONTEXT_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class JfrContext : AllStatic {
 private:
  static uint8_t _size;
 public:
  static void set_used_context_size(uint8_t size);
  static inline uint8_t get_used_context_size() { return _size; }
  static uint64_t get_and_set_context(uint8_t idx, uint64_t value);
  static uint8_t get_all_context(uint64_t* data, uint8_t length);
  static uint8_t set_all_context(uint64_t* data, uint8_t length);
  static uint8_t get_context(uint64_t** data);
  static void* get_thread_context_buffer();
};

#endif // SHARE_JFR_SUPPORT_CONTEXT_HPP
