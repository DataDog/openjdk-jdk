/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.jmx.annotations.model;

import java.lang.reflect.Field;
import java.lang.reflect.ReflectPermission;

/**
 * A trampoline for generating artificial getters/setters for non-public fields.
 *
 */
class Trampoline {
    private static final ReflectPermission ACCESS_CHECKS = new ReflectPermission("suppressAccessChecks");

    @SuppressWarnings("unchecked")
    static <T> T getField(Field fld, Object instance) {
        try {
            fld.setAccessible(true);
            return (T) fld.get(instance);
        } catch (Exception exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static <T> void setField(Field fld, Object instance, T value) {
        try {
            fld.setAccessible(true);
            fld.set(instance, value);
        } catch (Exception exception) {
        }
    }
}
