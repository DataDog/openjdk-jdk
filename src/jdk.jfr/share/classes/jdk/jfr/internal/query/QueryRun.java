/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.SequencedSet;
import java.util.Set;

import jdk.jfr.Contextual;
import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.MetadataEvent;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.internal.query.QueryResolver.QueryException;
import jdk.jfr.internal.query.QueryResolver.QuerySyntaxException;

final class QueryRun {
    // Inner records for context tracking (based on PrettyWriter pattern)
    private static record Timestamp(RecordedEvent event, long seconds, int nanosCompare, boolean contextual)
        implements Comparable<Timestamp> {

        public static Timestamp createStart(RecordedEvent event, boolean contextual) {
            Instant time = event.getStartTime();
            return new Timestamp(event, time.getEpochSecond(), 2 * time.getNano(), contextual);
        }

        public static Timestamp createEnd(RecordedEvent event, boolean contextual) {
            Instant time = event.getEndTime();
            return new Timestamp(event, time.getEpochSecond(), 2 * time.getNano() + 1, contextual);
        }

        public boolean start() {
            return (nanosCompare & 1L) == 0;
        }

        @Override
        public int compareTo(Timestamp that) {
            int cmp = Long.compare(seconds, that.seconds);
            if (cmp != 0) return cmp;
            return nanosCompare - that.nanosCompare;
        }
    }

    private static record TypeMetadata(Long id, List<ValueDescriptor> contextualFields,
                                       boolean hasContextualFields, String simpleName) {
    }

    private static final int EVENT_WINDOW_SIZE = 1_000_000;

    private final Histogram histogram = new Histogram();
    private final Table table = new Table();
    private final List<String> syntaxErrors = new ArrayList<>();
    private final List<String> metadataErrors = new ArrayList<>();
    private final Query query;
    private final EventStream stream;
    private final Configuration configuration;

    // Fields for context tracking
    private PriorityQueue<Timestamp> timeline;
    private Map<Long, TypeMetadata> typeMetadataCache;
    private Map<Long, SequencedSet<RecordedEvent>> activeContextsByThread;

    public QueryRun(EventStream stream, Query query, Configuration configuration) {
        this.stream = stream;
        this.query = query;
        this.configuration = configuration;
        if (configuration.showContext) {
            this.timeline = new PriorityQueue<>(EVENT_WINDOW_SIZE + 4);
            this.typeMetadataCache = new HashMap<>();
            this.activeContextsByThread = new HashMap<>();
        }
    }

    void onMetadata(MetadataEvent e) {
        if (table.getFields().isEmpty()) {
            // Only use first metadata event for now
            try {
                QueryResolver resolver = new QueryResolver(query, e.getEventTypes());
                List<Field> fields = resolver.resolve();
                table.addFields(fields);
                histogram.addFields(fields);
                addEventListeners();
            } catch (QuerySyntaxException qe) {
                syntaxErrors.add(qe.getMessage());
            } catch (QueryException qe) {
                metadataErrors.add(qe.getMessage());
            }
        }
    }

    public void complete() {
        // Process remaining events in timeline
        if (configuration.showContext && timeline != null) {
            while (!timeline.isEmpty()) {
                processTimestamp(timeline.remove(), table.getFields());
            }

            // Discover all contextual fields from processed events
            addContextualColumnsToTable();
        }

        // Existing aggregation logic
        if (!query.groupBy.isEmpty()) {
            table.addRows(histogram.toRows());
        }
    }

    private void addEventListeners() {
        if (!configuration.showContext || !query.groupBy.isEmpty()) {
            // Simple path: no context tracking or aggregated query
            addSimpleEventListeners();
            return;
        }

        // Context-aware path
        for (var entry : groupByTypeDescriptor().entrySet()) {
            FilteredType type = entry.getKey();
            List<Field> sourceFields = entry.getValue();

            stream.onEvent(type.getName(), e -> {
                // Apply filters
                for (var filter : type.getFilters()) {
                    Object object = filter.field().valueGetter.apply(e);
                    String text = FieldFormatter.format(filter.field(), object);
                    if (!text.equals(filter.value())) return;
                }

                TypeMetadata metadata = getTypeMetadata(e.getEventType());

                // Add to timeline for processing
                if (metadata.hasContextualFields()) {
                    timeline.add(Timestamp.createStart(e, true));
                    timeline.add(Timestamp.createEnd(e, true));
                }
                timeline.add(Timestamp.createEnd(e, false));

                // Process window when full
                while (timeline.size() > EVENT_WINDOW_SIZE) {
                    processTimestamp(timeline.remove(), sourceFields);
                }
            });
        }
    }

    private void addSimpleEventListeners() {
        // Existing implementation for non-context mode
        for (var entry : groupByTypeDescriptor().entrySet()) {
            FilteredType type = entry.getKey();
            List<Field> sourceFields = entry.getValue();
            stream.onEvent(type.getName(), e -> {
                for (var filter : type.getFilters()) {
                    Object object = filter.field().valueGetter.apply(e);
                    String text = FieldFormatter.format(filter.field(), object);
                    if (!text.equals(filter.value())) {
                        return;
                    }
                }
                if (query.groupBy.isEmpty()) {
                    table.add(e, sourceFields, Collections.emptySet());
                } else {
                    histogram.add(e, type, sourceFields);
                }
            });
        }
    }

    private LinkedHashMap<FilteredType, List<Field>> groupByTypeDescriptor() {
        var multiMap = new LinkedHashMap<FilteredType, List<Field>>();
        for (Field field : table.getFields()) {
            for (Field sourceFields : field.sourceFields) {
                multiMap.computeIfAbsent(sourceFields.type, k -> new ArrayList<>()).add(field);
            }
        }
        return multiMap;
    }

    public List<String> getSyntaxErrors() {
        return syntaxErrors;
    }

    public List<String> getMetadataErrors() {
        return metadataErrors;
    }

    public Query getQuery() {
        return query;
    }

    public Table getTable() {
        return table;
    }

    private TypeMetadata getTypeMetadata(EventType eventType) {
        return typeMetadataCache.computeIfAbsent(eventType.getId(), id -> {
            List<ValueDescriptor> contextualFields = eventType.getFields().stream()
                .filter(vd -> vd.getAnnotation(Contextual.class) != null)
                .toList();
            String name = eventType.getName();
            String simpleName = name.substring(name.lastIndexOf(".") + 1);
            return new TypeMetadata(id, contextualFields, !contextualFields.isEmpty(), simpleName);
        });
    }

    private void processTimestamp(Timestamp ts, List<Field> sourceFields) {
        RecordedEvent event = ts.event();
        RecordedThread thread = event.getThread();

        if (ts.contextual()) {
            if (thread == null) return;
            Long threadId = thread.getId();

            if (ts.start()) {
                activeContextsByThread.computeIfAbsent(threadId, k -> new LinkedHashSet<>()).add(event);
            } else {
                SequencedSet<RecordedEvent> contexts = activeContextsByThread.get(threadId);
                if (contexts != null) {
                    contexts.remove(event);
                    if (contexts.isEmpty()) {
                        activeContextsByThread.remove(threadId);
                    }
                }
            }
        } else {
            // Display event - get matching contexts
            Set<RecordedEvent> matchingContexts = Collections.emptySet();
            if (thread != null) {
                SequencedSet<RecordedEvent> activeContexts = activeContextsByThread.get(thread.getId());
                if (activeContexts != null) {
                    matchingContexts = new LinkedHashSet<>(activeContexts);
                }
            }
            table.add(event, sourceFields, matchingContexts);
        }
    }

    private void addContextualColumnsToTable() {
        // Collect all unique contextual fields across all rows
        Set<String> contextualFieldNames = new LinkedHashSet<>();

        for (Row row : table.getRows()) {
            for (RecordedEvent ctxEvent : row.getContextualEvents()) {
                EventType eventType = ctxEvent.getEventType();
                String typeName = getSimpleName(eventType);

                for (ValueDescriptor vd : eventType.getFields()) {
                    if (vd.getAnnotation(Contextual.class) != null) {
                        String fieldName = typeName + "." + vd.getName();
                        contextualFieldNames.add(fieldName);
                    }
                }
            }
        }

        // Add these as new columns to the table
        for (String fieldName : contextualFieldNames) {
            table.addContextualColumn(fieldName);
        }
    }

    private String getSimpleName(EventType type) {
        String name = type.getName();
        return name.substring(name.lastIndexOf(".") + 1);
    }
}
