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

import jdk.jfr.consumer.RecordedEvent;

/**
 * Functional interface for extracting contextual field values.
 * <p>
 * Used by Histogram to resolve values from contextual events
 * when processing GROUP BY queries with contextual field references.
 */
@FunctionalInterface
interface ContextValueExtractor {

    /**
     * Extracts the value of a contextual field for the given display event.
     *
     * @param field the field containing contextual type and field name info
     * @param displayEvent the event being displayed/aggregated
     * @return the contextual field value, or null if not available
     */
    Object extract(Field field, RecordedEvent displayEvent);

    /**
     * A no-op extractor that always returns null.
     * Used when context extraction is not needed.
     */
    ContextValueExtractor NONE = (field, event) -> null;
}
