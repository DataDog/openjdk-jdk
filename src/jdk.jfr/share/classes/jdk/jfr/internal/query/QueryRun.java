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

/**
 * Executes a query against an event stream, handling both simple queries and
 * context-aware queries with {@code @Contextual} field support.
 * <p>
 * <b>Thread Safety:</b> This class is NOT thread-safe. It is designed to be used
 * from a single thread that processes the EventStream sequentially. EventStream
 * provides ordering guarantees that ensure events are delivered in timestamp order
 * on a single thread, which allows safe mutation of {@code activeContextsByThread}
 * and other internal state without synchronization.
 */
final class QueryRun {
    // Inner records for context tracking (based on PrettyWriter pattern)
    private static record Timestamp(RecordedEvent event, long seconds, int nanosCompare, boolean contextual)
        implements Comparable<Timestamp> {

        /**
         * Creates a timestamp for an event start time.
         * Uses nanosCompare = 2*nanos to ensure start events sort before
         * end events at the same instant (end events use 2*nanos + 1).
         */
        public static Timestamp createStart(RecordedEvent event, boolean contextual) {
            Instant time = event.getStartTime();
            return new Timestamp(event, time.getEpochSecond(), 2 * time.getNano(), contextual);
        }

        /**
         * Creates a timestamp for an event end time.
         * Uses nanosCompare = 2*nanos + 1 to ensure end events sort after
         * start events at the same instant.
         */
        public static Timestamp createEnd(RecordedEvent event, boolean contextual) {
            Instant time = event.getEndTime();
            return new Timestamp(event, time.getEpochSecond(), 2 * time.getNano() + 1, contextual);
        }

        /**
         * Returns true if this timestamp represents an event start time.
         * Start timestamps have even nanosCompare values (LSB = 0).
         */
        public boolean start() {
            return (nanosCompare & 1) == 0;
        }

        @Override
        public int compareTo(Timestamp that) {
            int cmp = Long.compare(seconds, that.seconds);
            if (cmp != 0) return cmp;
            return Integer.compare(nanosCompare, that.nanosCompare);
        }
    }

    private static record TypeMetadata(Long id, List<ValueDescriptor> contextualFields,
                                       boolean hasContextualFields, String simpleName) {
    }

    /**
     * Maximum number of events to buffer in the timeline before processing.
     * This bounds memory usage while maintaining context tracking accuracy.
     * When the timeline exceeds this size, the oldest events are processed
     * and removed from the buffer.
     * <p>
     * The value of 1 million events is chosen to balance memory usage
     * (typically ~100MB for average event sizes) with context accuracy for
     * recordings containing long-duration contextual events. This allows
     * context to span across substantial portions of a recording while
     * preventing unbounded memory growth.
     */
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
        boolean hasContextualFields = hasContextualFieldReferences();

        // Process remaining events in timeline for context-aware display (non-aggregated)
        if (configuration.showContext && timeline != null && query.groupBy.isEmpty()) {
            while (!timeline.isEmpty()) {
                processTimestamp(timeline.remove(), table.getFields());
            }

            // Discover all contextual fields from processed events
            addContextualColumnsToTable();
        }

        // Process remaining events in timeline for context-aware aggregation
        if (hasContextualFields && !query.groupBy.isEmpty() && timeline != null) {
            // Get the first filtered type for aggregation processing
            var entries = groupByTypeDescriptor().entrySet().iterator();
            if (entries.hasNext()) {
                var entry = entries.next();
                FilteredType type = entry.getKey();
                List<Field> sourceFields = entry.getValue();
                while (!timeline.isEmpty()) {
                    processAggregationTimestamp(timeline.remove(), type, sourceFields);
                }
            }
        }

        // Existing aggregation logic
        if (!query.groupBy.isEmpty()) {
            table.addRows(histogram.toRows());
        }
    }

    private void addEventListeners() {
        // Check if query has contextual field references (for GROUP BY with context)
        boolean hasContextualFields = hasContextualFieldReferences();

        // Use simple path when:
        // - No context display requested AND no contextual field references
        // - OR context display requested but we have GROUP BY without contextual fields
        if (!configuration.showContext && !hasContextualFields) {
            addSimpleEventListeners();
            return;
        }

        // For GROUP BY queries with contextual field references, use context-aware aggregation
        if (!query.groupBy.isEmpty() && hasContextualFields) {
            addContextAwareAggregationListeners();
            return;
        }

        // For non-GROUP BY queries with context display, use timeline-based tracking
        if (!query.groupBy.isEmpty()) {
            // GROUP BY without contextual fields - use simple path
            addSimpleEventListeners();
            return;
        }

        // Context-aware path for display (non-aggregated): need to listen to both query events and contextual events

        // First, register listeners for the queried event types
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

                // Add display event to timeline
                timeline.add(Timestamp.createEnd(e, false));

                // Process window when full
                while (timeline.size() > EVENT_WINDOW_SIZE) {
                    processTimestamp(timeline.remove(), sourceFields);
                }
            });
        }

        // Second, register a global listener for all contextual events
        stream.onEvent(e -> {
            TypeMetadata metadata = getTypeMetadata(e.getEventType());

            // Only process if it has contextual fields
            if (metadata.hasContextualFields()) {
                // Filter by context types if specified
                if (shouldSkipContextualEvent(metadata)) {
                    return; // Skip this contextual event
                }

                timeline.add(Timestamp.createStart(e, true));
                timeline.add(Timestamp.createEnd(e, true));
            }
        });
    }

    /**
     * Adds event listeners for GROUP BY queries that reference contextual fields.
     * Uses timeline-based context tracking and provides a callback to Histogram
     * for extracting contextual field values.
     */
    private void addContextAwareAggregationListeners() {
        // Initialize context tracking structures
        if (timeline == null) {
            timeline = new PriorityQueue<>(EVENT_WINDOW_SIZE + 4);
            typeMetadataCache = new HashMap<>();
            activeContextsByThread = new HashMap<>();
        }

        // Register listeners for the queried event types
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

                // Add display event to timeline for processing
                timeline.add(Timestamp.createEnd(e, false));

                // Process window when full
                while (timeline.size() > EVENT_WINDOW_SIZE) {
                    processAggregationTimestamp(timeline.remove(), type, sourceFields);
                }
            });
        }

        // Register a global listener for all contextual events
        stream.onEvent(e -> {
            TypeMetadata metadata = getTypeMetadata(e.getEventType());

            if (metadata.hasContextualFields()) {
                if (shouldSkipContextualEvent(metadata)) {
                    return;
                }

                timeline.add(Timestamp.createStart(e, true));
                timeline.add(Timestamp.createEnd(e, true));
            }
        });
    }

    /**
     * Processes a timestamp for aggregation queries with contextual fields.
     * <p>
     * For contextual events, maintains the activeContextsByThread map.
     * For display events, delegates to histogram with context extraction.
     * Events without thread information are skipped for context tracking
     * but still processed for aggregation.
     */
    private void processAggregationTimestamp(Timestamp ts, FilteredType type, List<Field> sourceFields) {
        RecordedEvent event = ts.event();
        RecordedThread thread = event.getThread();

        if (ts.contextual()) {
            // Contextual events require thread information for tracking
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
            // Display event - aggregate with context value extraction
            // Note: thread may be null, which is handled gracefully in extractContextValue
            histogram.add(event, type, sourceFields, this::extractContextValue);
        }
    }

    /**
     * Extracts a contextual field value for the given field and display event.
     * This is the callback provided to Histogram for contextual field resolution.
     */
    private Object extractContextValue(Field field, RecordedEvent displayEvent) {
        if (!field.contextual) {
            return null;
        }

        RecordedThread thread = displayEvent.getThread();
        if (thread == null) {
            return null;
        }

        SequencedSet<RecordedEvent> contexts = activeContextsByThread.get(thread.getId());
        if (contexts == null) {
            return null;
        }

        // Find matching context event
        for (RecordedEvent ctx : contexts) {
            String simpleName = QueryUtil.getSimpleName(ctx.getEventType());
            if (simpleName.equals(field.contextTypeName) ||
                ctx.getEventType().getName().equals(field.contextTypeName)) {
                if (ctx.hasField(field.contextFieldName)) {
                    return ctx.getValue(field.contextFieldName);
                }
            }
        }

        return null;
    }

    /**
     * Checks if any field in the query references a contextual event type.
     */
    private boolean hasContextualFieldReferences() {
        for (Field field : table.getFields()) {
            if (field.contextual) {
                return true;
            }
            for (Field sourceField : field.sourceFields) {
                if (sourceField.contextual) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the given contextual event should be skipped based on
     * configured context type filters.
     *
     * @param metadata the event type metadata
     * @return true if this event should be skipped
     */
    private boolean shouldSkipContextualEvent(TypeMetadata metadata) {
        return configuration.contextTypes != null
            && !configuration.contextTypes.isEmpty()
            && !configuration.contextTypes.contains(metadata.simpleName());
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
            String simpleName = QueryUtil.getSimpleName(eventType);
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
                String typeName = QueryUtil.getSimpleName(eventType);

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
}
