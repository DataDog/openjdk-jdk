/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#include "jfr/support/jfrUsdtSupport.hpp"

#include <math.h>
#include <string.h>

#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allocation.hpp"
#include "oops/klass.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"

#if defined(LINUX) && defined(DTRACE_ENABLED)
// The fire helpers bring in the dtrace -h header, which includes
// <sys/sdt.h> with the semaphore prolog. This TU must stay free of
// hotspot provider probe macros: the prolog is applied at sys/sdt.h
// include time and would detach their semaphores.
#include "jfrfiles/jfrUsdtFire.hpp"
#endif

// Per-thread scratch ring for NUL-terminated copies of probe string
// arguments (Symbol bytes are not NUL-terminated). copy_string only runs
// with the probe's is-enabled semaphore set, so the ring costs nothing until
// a tracer subscribes.
static thread_local char _usdt_scratch[4][256];
static thread_local unsigned _usdt_scratch_next = 0;

JfrUsdtSupport::Table::Table(size_t capacity_power_of_two)
  : capacity(capacity_power_of_two),
    mask(capacity_power_of_two - 1),
    entries(NEW_C_HEAP_ARRAY(Entry, capacity_power_of_two, mtTracing)) {
  assert((capacity_power_of_two & (capacity_power_of_two - 1)) == 0, "must be a power of two");
  for (size_t i = 0; i < capacity; i++) {
    entries[i].tid = 0;
    entries[i].os_thread_id = NO_OS_THREAD_ID;
  }
}

JfrUsdtSupport::Table* volatile JfrUsdtSupport::_table = nullptr;
volatile int64_t JfrUsdtSupport::_insert_lock = 0;

// Insert into the current table. The caller holds the insert lock and the
// table is guaranteed to have room and to not contain tid already.
void JfrUsdtSupport::table_insert(JfrUsdtSupport::Table* table, traceid tid, int64_t os_thread_id) {
  const size_t start = (size_t)tid & table->mask;
  for (size_t i = 0; i <= table->mask; i++) {
    Entry& e = table->entries[(start + i) & table->mask];
    if (e.tid == 0) {
      // Publish: the OS thread id is written before the key. A concurrent
      // lock-free lookup that acquires the key therefore sees the value.
      e.os_thread_id = os_thread_id;
      AtomicAccess::release_store(&e.tid, tid);
      return;
    }
    assert(e.tid != tid, "duplicate trace id registration");
  }
  ShouldNotReachHere();
}

JfrUsdtSupport::Table* JfrUsdtSupport::table_alloc_and_fill(size_t capacity, JfrUsdtSupport::Table* source) {
  Table* const table = NEW_C_HEAP_OBJ(Table, mtTracing);
  ::new (table) Table(capacity);
  if (source != nullptr) {
    for (size_t i = 0; i <= source->mask; i++) {
      const Entry& e = source->entries[i];
      const traceid tid = AtomicAccess::load_acquire(&e.tid);
      if (tid != 0) {
        table_insert(table, tid, e.os_thread_id);
      }
    }
  }
  return table;
}

int64_t JfrUsdtSupport::os_thread_id(traceid tid) {
  if (tid == 0) {
    return NO_OS_THREAD_ID;
  }
  Table* const table = AtomicAccess::load_acquire(&_table);
  if (table == nullptr) {
    return NO_OS_THREAD_ID;
  }
  const size_t start = (size_t)tid & table->mask;
  // Entries are never removed and linear probing never leaves a hole
  // between an entry's hash position and its actual position, so the
  // first empty slot ends the probe.
  for (size_t i = 0; i <= table->mask; i++) {
    const Entry& e = table->entries[(start + i) & table->mask];
    const traceid cur = AtomicAccess::load_acquire(&e.tid);
    if (cur == tid) {
      return e.os_thread_id;
    }
    if (cur == 0) {
      return NO_OS_THREAD_ID;
    }
  }
  return NO_OS_THREAD_ID;
}

void JfrUsdtSupport::record_slow(traceid tid, int64_t os_thread_id) {
  assert(tid != 0, "invariant");
  // One spin per thread registration; the registrant is never holding
  // other JFR locks, so plain spinning is safe here.
  while (AtomicAccess::cmpxchg(&_insert_lock, (int64_t)0, (int64_t)1) != 0) {
  }
  Table* table = AtomicAccess::load_acquire(&_table);
  if (table == nullptr) {
    table = table_alloc_and_fill(INITIAL_CAPACITY, nullptr);
    AtomicAccess::release_store(&_table, table);
  } else {
    size_t used = 0;
    for (size_t i = 0; i <= table->mask; i++) {
      const traceid cur = AtomicAccess::load_acquire(&table->entries[i].tid);
      if (cur == tid) {
        // Racy duplicate registration; first writer wins.
        AtomicAccess::release_store(&_insert_lock, (int64_t)0);
        return;
      }
      if (cur != 0) {
        used++;
      }
    }
    if (used > table->capacity / 2) {
      // Grow; the old table is leaked deliberately: publishing a new
      // table lock-free is what keeps lookups wait-free.
      table = table_alloc_and_fill(table->capacity * 2, table);
      AtomicAccess::release_store(&_table, table);
    }
  }
  table_insert(table, tid, os_thread_id);
  AtomicAccess::release_store(&_insert_lock, (int64_t)0);
}

const char* JfrUsdtSupport::copy_string(const u1* bytes, size_t length) {
  assert(bytes != nullptr, "invariant");
  const size_t cap = sizeof(_usdt_scratch[0]) - 1;
  char* const dst = _usdt_scratch[_usdt_scratch_next];
  _usdt_scratch_next = (_usdt_scratch_next + 1) % (sizeof(_usdt_scratch) / sizeof(_usdt_scratch[0]));
  const size_t n = length < cap ? length : cap;
  memcpy(dst, bytes, n);
  dst[n] = '\0';
  return dst;
}

void JfrUsdtSupport::on_thread_id_assigned(const Thread* t, traceid tid) {
  assert(t != nullptr, "invariant");
  assert(tid != 0, "invariant");
  const OSThread* const osthread = t->osthread();
  if (osthread == nullptr) {
    return; // no OS identity to record
  }
  record_slow(tid, osthread->thread_id());
}

#if defined(LINUX) && defined(DTRACE_ENABLED)

// Temporary sampling for the decoupled allocation sample. The
// exponential-CDF byte-distance draw below is not the end state: JFR
// already owns a sampler for this event, its rate-limiting throttle
// (JfrEventThrottler, 150/s with default settings), and the probe should
// be fed from that, for exactly its rate-limiting property: a fixed
// ceiling on samples per second that holds under any allocation load.
// The throttle is today created in the recorder-start path and
// configured by recording settings, so with no recording it accepts
// nothing. Until it can be started and driven independently of a
// recording, this draw keeps the probe firing without the recorder. It
// bounds sample density over bytes rather than rate, so an allocation
// storm can fire more than the throttle would have allowed.
static const int64_t ALLOCATION_SAMPLE_MEAN_BYTES = 512 * 1024;

struct AllocSampleState {
  int64_t last_sample_bytes; // cumulative allocation count at the last fire
  int64_t next_gap_bytes;    // bytes that must accumulate before the next fire
};

static thread_local AllocSampleState _alloc_sample_state = {0, 0};
static thread_local uint64_t _alloc_sample_prng = 0;

static uint64_t alloc_sample_next_random() {
  uint64_t x = _alloc_sample_prng;
  if (x == 0) {
    x = (uint64_t)os::random() | 1; // xorshift state must stay nonzero
  }
  x ^= x << 13;
  x ^= x >> 7;
  x ^= x << 17;
  _alloc_sample_prng = x;
  return x;
}

// Inverse exponential CDF: a byte distance with the configured mean.
static int64_t alloc_sample_next_gap() {
  const double u = ((double)(alloc_sample_next_random() >> 11) + 0.5) / (double)(1ULL << 53);
  return (int64_t)(-log(u) * ALLOCATION_SAMPLE_MEAN_BYTES);
}

bool JfrUsdtSupport::object_allocation_sample_subscribed() {
  return JFR_OBJECT_ALLOCATION_SAMPLE_ENABLED();
}

bool JfrUsdtSupport::send_object_allocation_sample(const Klass* klass, int64_t allocated_bytes) {
  assert(klass != nullptr, "invariant");
  assert(allocated_bytes > 0, "invariant");
  if (!JFR_OBJECT_ALLOCATION_SAMPLE_ENABLED()) {
    return false;
  }
  AllocSampleState& state = _alloc_sample_state;
  if (state.last_sample_bytes == 0) {
    // First attempt on the thread: anchor the counter without firing,
    // so the first sample does not claim bytes allocated before the
    // tracer subscribed.
    state.last_sample_bytes = allocated_bytes;
    state.next_gap_bytes = alloc_sample_next_gap();
    return false;
  }
  const int64_t weight = allocated_bytes - state.last_sample_bytes;
  if (weight < state.next_gap_bytes) {
    return false;
  }
  state.last_sample_bytes = allocated_bytes;
  state.next_gap_bytes = alloc_sample_next_gap();
  usdt_fire_object_allocation_sample(klass, weight);
  return true;
}

#else

bool JfrUsdtSupport::object_allocation_sample_subscribed() {
  return false;
}

bool JfrUsdtSupport::send_object_allocation_sample(const Klass* klass, int64_t allocated_bytes) {
  return false;
}

#endif
