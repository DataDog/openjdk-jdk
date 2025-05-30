/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/parallel/jvmFlagConstraintsParallel.hpp"
#include "gc/shared/gc_globals.hpp"
#include "runtime/globals.hpp"
#include "utilities/globalDefinitions.hpp"

JVMFlag::Error InitialTenuringThresholdConstraintFuncParallel(uint value, bool verbose) {
  // InitialTenuringThreshold is only used for ParallelGC.
  if (UseParallelGC && (value > MaxTenuringThreshold)) {
      JVMFlag::printError(verbose,
                          "InitialTenuringThreshold (%u) must be "
                          "less than or equal to MaxTenuringThreshold (%u)\n",
                          value, MaxTenuringThreshold);
      return JVMFlag::VIOLATES_CONSTRAINT;
  }
  return JVMFlag::SUCCESS;
}

JVMFlag::Error MaxTenuringThresholdConstraintFuncParallel(uint value, bool verbose) {
  // As only ParallelGC uses InitialTenuringThreshold,
  // we don't need to compare InitialTenuringThreshold with MaxTenuringThreshold.
  if (UseParallelGC && (value < InitialTenuringThreshold)) {
    JVMFlag::printError(verbose,
                        "MaxTenuringThreshold (%u) must be "
                        "greater than or equal to InitialTenuringThreshold (%u)\n",
                        value, InitialTenuringThreshold);
    return JVMFlag::VIOLATES_CONSTRAINT;
  }

  return JVMFlag::SUCCESS;
}
