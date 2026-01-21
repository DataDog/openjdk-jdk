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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.Contextual;
import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;

/**
 * Index of contextual event types and their @Contextual annotated fields.
 * <p>
 * Since @Contextual is a field-level annotation (not type-level), this index
 * scans all event types to discover which types have contextual fields.
 * <p>
 * The index supports resolution of field references like "Trace.traceId"
 * where "Trace" is the simple name of an event type and "traceId" is
 * a field annotated with @Contextual.
 */
final class ContextualFieldIndex {

    /**
     * Information about a contextual event type.
     */
    record ContextualTypeInfo(
        EventType eventType,
        String simpleName,      // e.g., "Trace"
        String fullName,        // e.g., "jdk.Trace"
        List<String> contextualFields  // e.g., ["traceId", "spanId"]
    ) {}

    /**
     * Result of resolving a contextual field reference.
     */
    record ResolvedField(
        ContextualTypeInfo typeInfo,
        String fieldName
    ) {}

    private final Map<String, ContextualTypeInfo> bySimpleName = new HashMap<>();
    private final Map<String, ContextualTypeInfo> byFullName = new HashMap<>();

    /**
     * Creates a contextual field index from the given event types.
     *
     * @param eventTypes list of all event types from metadata
     */
    public ContextualFieldIndex(List<EventType> eventTypes) {
        for (EventType type : eventTypes) {
            List<String> contextualFields = new ArrayList<>();

            for (ValueDescriptor vd : type.getFields()) {
                if (vd.getAnnotation(Contextual.class) != null) {
                    contextualFields.add(vd.getName());
                }
            }

            if (!contextualFields.isEmpty()) {
                String fullName = type.getName();
                String simpleName = QueryUtil.getSimpleName(fullName);
                ContextualTypeInfo info = new ContextualTypeInfo(type, simpleName, fullName, List.copyOf(contextualFields));

                byFullName.put(fullName, info);
                // Only add to simpleName map if not already present (first wins)
                ContextualTypeInfo existing = bySimpleName.putIfAbsent(simpleName, info);
                if (existing != null && Logger.shouldLog(LogTag.JFR_SYSTEM, LogLevel.DEBUG)) {
                    Logger.log(LogTag.JFR_SYSTEM, LogLevel.DEBUG,
                        "Contextual event type simple name collision: '" + simpleName +
                        "' already mapped to '" + existing.fullName() +
                        "', ignoring '" + fullName + "'. Use fully qualified names to disambiguate.");
                }
            }
        }
    }

    /**
     * Resolves a field reference that may be a contextual field.
     * <p>
     * Supports references like:
     * <ul>
     *   <li>"Trace.traceId" - simple name + field</li>
     *   <li>"jdk.Trace.traceId" - full name + field</li>
     * </ul>
     *
     * @param reference the field reference (e.g., "Trace.traceId")
     * @return the resolved field info, or null if not a contextual field
     */
    public ResolvedField resolve(String reference) {
        int lastDot = reference.lastIndexOf('.');
        if (lastDot == -1) {
            return null;
        }

        String fieldName = reference.substring(lastDot + 1);
        String typePart = reference.substring(0, lastDot);

        // Try as simple name first (most common case)
        ContextualTypeInfo info = bySimpleName.get(typePart);
        if (info != null && info.contextualFields().contains(fieldName)) {
            return new ResolvedField(info, fieldName);
        }

        // Try as full name
        info = byFullName.get(typePart);
        if (info != null && info.contextualFields().contains(fieldName)) {
            return new ResolvedField(info, fieldName);
        }

        return null;
    }

    /**
     * Checks if a field reference refers to a contextual field.
     *
     * @param reference the field reference
     * @return true if it's a contextual field reference
     */
    public boolean isContextualField(String reference) {
        return resolve(reference) != null;
    }

}
