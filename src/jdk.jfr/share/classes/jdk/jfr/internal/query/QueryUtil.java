/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.query;

import jdk.jfr.EventType;

/**
 * Utility methods for the query package.
 */
final class QueryUtil {

    private QueryUtil() {
        // Prevent instantiation
    }

    /**
     * Extracts the simple name from a fully qualified name.
     * <p>
     * For example:
     * <ul>
     *   <li>"jdk.Trace" returns "Trace"</li>
     *   <li>"Trace" returns "Trace"</li>
     *   <li>"a.b.c.MyEvent" returns "MyEvent"</li>
     * </ul>
     *
     * @param fullName the fully qualified name
     * @return the simple name (text after the last dot, or the full name if no dots)
     */
    static String getSimpleName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        return lastDot == -1 ? fullName : fullName.substring(lastDot + 1);
    }

    /**
     * Extracts the simple name from an event type.
     *
     * @param eventType the event type
     * @return the simple name of the event type
     */
    static String getSimpleName(EventType eventType) {
        return getSimpleName(eventType.getName());
    }
}
