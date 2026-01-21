/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;

final class Row {
    private Object[] values;
    private String[] texts;
    private Set<RecordedEvent> contextualEvents = Collections.emptySet();

    public Row(int size) {
        values = new Object[size];
        texts = new String[size];
    }

    public Object getValue(int index) {
        return values[index];
    }

    public void putValue(int index, Object o) {
        values[index] = o;
    }

    public String getText(int index) {
        return texts[index];
    }

    public void putText(int index, String text) {
        texts[index] = text;
    }

    public void setContextualEvents(Set<RecordedEvent> events) {
        this.contextualEvents = events != null ? events : Collections.emptySet();
    }

    public Set<RecordedEvent> getContextualEvents() {
        return contextualEvents;
    }

    public void expandToSize(int newSize) {
        if (values.length < newSize) {
            values = Arrays.copyOf(values, newSize);
            texts = Arrays.copyOf(texts, newSize);
        }
    }

    /**
     * Retrieves the value of a contextual field from the stored contextual events.
     *
     * @param qualifiedFieldName the field name in "TypeName.fieldName" format
     * @return the field value, or null if not found (displays as "N/A")
     * @throws IllegalArgumentException if the field name is not properly qualified
     */
    public Object getContextualValue(String qualifiedFieldName) {
        // Parse "TypeName.fieldName"
        int dotIndex = qualifiedFieldName.indexOf('.');
        if (dotIndex == -1 || dotIndex == 0 || dotIndex == qualifiedFieldName.length() - 1) {
            throw new IllegalArgumentException(
                "Contextual field name must be in format 'TypeName.fieldName': " + qualifiedFieldName);
        }

        String typeName = qualifiedFieldName.substring(0, dotIndex);
        String attrName = qualifiedFieldName.substring(dotIndex + 1);

        // Find matching contextual event and extract value
        for (RecordedEvent ctxEvent : contextualEvents) {
            EventType eventType = ctxEvent.getEventType();
            String eventTypeName = QueryUtil.getSimpleName(eventType);
            if (eventTypeName.equals(typeName)) {
                if (ctxEvent.hasField(attrName)) {
                    return ctxEvent.getValue(attrName);
                }
            }
        }
        return null; // Will display as "N/A"
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
