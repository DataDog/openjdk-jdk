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

#ifndef SHARE_JFR_SUPPORT_JFRUSDTSUPPORT_HPP
#define SHARE_JFR_SUPPORT_JFRUSDTSUPPORT_HPP

#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allocation.hpp"

class Klass;
class Thread;

// Resolve a JFR thread trace id to the OS thread id for USDT probe
// arguments. The consumer side (e.g. eBPF tracers) can only correlate with
// kernel-visible threads, so Thread-typed event fields are projected to
// osThreadId at probe-fire time.
//
// JFR itself has no trace-id-to-thread registry (it resolves threads while
// iterating them at chunk flush), so this class maintains a small open
// addressed table. Entries are published once, when JFR assigns a thread
// its trace id, and are never mutated or removed: trace ids are unique for
// the process lifetime, so a stale entry only holds memory, never a wrong
// value. Lookups are lock-free; insertions take a spin lock, and resizing
// publishes a fresh table (the old one is leaked) via a release store.
class JfrUsdtSupport : public AllStatic {
 private:
  struct Entry {
    volatile traceid tid;
    int64_t os_thread_id;
  };

  struct Table {
    const size_t capacity;
    const size_t mask;
    Entry* const entries;

    explicit Table(size_t capacity_power_of_two);
    NONCOPYABLE(Table);
    // Tables are published lock-free and intentionally never destroyed.
  };

  static const int64_t NO_OS_THREAD_ID = -1;
  static const size_t INITIAL_CAPACITY = 1024;

  static Table* volatile _table;
  static volatile int64_t _insert_lock;

  static void table_insert(Table* table, traceid tid, int64_t os_thread_id);
  static Table* table_alloc_and_fill(size_t capacity, Table* source);
  static void record_slow(traceid tid, int64_t os_thread_id);

 public:
  static void on_thread_id_assigned(const Thread* t, traceid tid);

  // OS thread id for the given trace id, or NO_OS_THREAD_ID if unknown
  static int64_t os_thread_id(traceid tid);

  // NUL-terminated copy of bytes in a per-thread scratch buffer, capped at
  // USDT_STRING_CAP. Only called with the probe's is-enabled semaphore set.
  static const char* copy_string(const u1* bytes, size_t length);

  // True when a tracer subscribed to the object__allocation__sample
  // probe. Constant false in builds without dtrace support, so the
  // call site needs no extra guarding.
  static bool object_allocation_sample_subscribed();

  // Standalone sampling decision for the object__allocation__sample
  // probe, used when no recording accepts the attempt: with JFR off,
  // the event throttle never accepts, and the probe would never fire.
  // Samples by byte distance over the thread's cumulative allocation
  // counter, so each accepted sample carries the bytes accumulated
  // since the previous one and weights reconstruct the allocation
  // total. Temporary implementation; the intended sampler is JFR's own
  // rate-limiting throttle, decoupled from the recorder (see
  // jfrUsdtSupport.cpp). Returns true if the probe fired.
  static bool send_object_allocation_sample(const Klass* klass, int64_t allocated_bytes);
};

#endif // SHARE_JFR_SUPPORT_JFRUSDTSUPPORT_HPP
