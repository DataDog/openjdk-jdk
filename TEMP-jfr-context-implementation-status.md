# JFR View Context Feature - Implementation Status

## Overview

Implementation of `--show-context` flag for the `jfr view` command to display contextual event fields alongside regular event fields.

## Feature Description

**Goal**: Display events with contextual attributes from @Contextual annotated fields as additional columns in table view.

**Key Requirements**:
- New `--show-context` CLI flag
- Context matching rule: contextStart ≤ displayStart AND contextEnd ≥ displayStart
- Show ALL matching contextual events (not just most recent)
- Contextual fields appear as columns named `EventTypeName.fieldName`
- Universal feature for all views and event types
- Disable for aggregated queries (GROUP BY)

## Technical Approach

Based on PrettyWriter.java pattern (lines 70-169):
- PriorityQueue-based timeline for chronological event ordering
- Timestamp trick: multiply nanos by 2 for start, add 1 for end
- Event window size: 1,000,000 events
- LinkedHashSet (SequencedSet) for tracking active contexts per thread
- Dynamic column addition after event processing

## Implementation Status: COMPLETE (Not Tested)

All code changes have been implemented. Testing blocked by build environment issues (XCode metal tool not found).

## Files Modified

### 1. Configuration.java
**Path**: `src/jdk.jfr/share/classes/jdk/jfr/internal/query/Configuration.java`

**Change**: Added field after line 109
```java
/**
 * If contextual event fields should be displayed.
 * <p>
 * Contextual events are those with @Contextual annotated fields.
 * When enabled, displays fields from contextual events that were
 * active when the displayed event occurred.
 */
public boolean showContext;
```

### 2. View.java
**Path**: `src/jdk.jfr/share/classes/jdk/jfr/internal/tool/View.java`

**Changes**:
- Line 70: Added help text for `--show-context`
- Line 109: Added to option syntax list
- Line 129-131: Added flag parsing in execute()

### 3. QueryRun.java
**Path**: `src/jdk.jfr/share/classes/jdk/jfr/internal/query/QueryRun.java`

**Major Changes**:
- Added Timestamp and TypeMetadata record classes (lines 51-78)
- Added correlation fields: timeline, typeMetadataCache, activeContextsByThread (lines 91-93)
- Modified constructor to accept Configuration parameter (line 95)
- Added addSimpleEventListeners() method (lines 177-196)
- Modified addEventListeners() to split context-aware vs simple paths (lines 140-175)
- Added getTypeMetadata() for caching (lines 225-234)
- Added processTimestamp() for context tracking (lines 236-266)
- Added addContextualColumnsToTable() for field discovery (lines 268-295)
- Modified complete() to process timeline and add columns (lines 123-138)

**New Imports**:
- jdk.jfr.Contextual
- java.time.Instant
- java.util.LinkedHashMap
- java.util.LinkedHashSet
- java.util.PriorityQueue
- java.util.SequencedSet

### 4. Row.java
**Path**: `src/jdk.jfr/share/classes/jdk/jfr/internal/query/Row.java`

**Changes**:
- Added contextualEvents field (line 37)
- Added setContextualEvents() method (lines 60-62)
- Added getContextualEvents() method (lines 64-66)
- Added expandToSize() method (lines 68-73)
- Added getContextualValue() method (lines 75-92)
- Added getSimpleName() helper method (lines 94-97)

**New Imports**:
- jdk.jfr.EventType

### 5. Table.java
**Path**: `src/jdk.jfr/share/classes/jdk/jfr/internal/query/Table.java`

**Changes**:
- Added overloaded add() method accepting contextEvents (lines 74-81)
- Added addContextualColumn() for dynamic column addition (lines 83-98)

**New Imports**:
- jdk.jfr.consumer.RecordedEvent

### 6. QueryExecutor.java
**Path**: `src/jdk.jfr/share/classes/jdk/jfr/internal/query/QueryExecutor.java`

**Changes**:
- Added Configuration field (line 39)
- Added constructor overloads accepting Configuration (lines 41-59)
- Modified primary constructor to store configuration (lines 61-70)
- Updated QueryRun instantiation in addQueryRuns() to pass configuration (line 96)

## Key Implementation Details

### Context Matching Algorithm
1. Events added to PriorityQueue timeline with timestamps
2. Contextual events add both start and end timestamps
3. Display events add only end timestamp
4. Timeline processed chronologically
5. Active contexts tracked per thread in LinkedHashSet
6. Display event snapshots current active contexts for its thread

### Dynamic Column Addition
1. After all events processed, scan all rows for contextual events
2. Collect unique field names (TypeName.fieldName pattern)
3. Add these as new columns to Table
4. Row.getContextualValue() extracts values on-demand
5. Null values display as "N/A"

### Memory Management
- Event window: 1,000,000 events
- Process oldest when window exceeds limit
- Cleanup empty context sets per thread
- Bounded by concurrent contextual events (~100 per thread)

## Testing Requirements

### Build Steps
1. Fix XCode environment: `sudo xcode-select -r`
2. Configure: `bash configure --with-boot-jdk=<path-to-jdk-25-or-later>`
3. Build: `make images`

### Test Cases Needed
1. **Basic functionality**: Recording with contextual events, verify columns appear
2. **Context matching**: Verify only overlapping contexts shown
3. **Multiple contexts**: Verify all matching contexts from different types shown
4. **Edge cases**:
   - Null thread events (no context)
   - Instant events (same start/end time)
   - Aggregated queries (context disabled)
   - No matching contexts (graceful degradation)
5. **Performance**: Large recordings (1GB+), verify bounded memory

### Example Test Command
```bash
jfr view --show-context ThreadStart recording.jfr
```

Expected output:
```
                     ThreadStart

Thread Name  OS Thread ID  Java Thread ID  TraceEvent.id    TraceEvent.name
--------------------------------------------------------------------------------
Thread-1     12345         1               00-0af765...     POST /checkout
Thread-2     12346         2               N/A              N/A
```

## Next Steps

1. Resolve build environment (XCode metal tool)
2. Build the JDK
3. Create test recording with @Contextual events
4. Verify flag parsing and output format
5. Run edge case tests
6. Performance validation on large recordings

## Reference Implementation

PrettyWriter.java (lines 70-279) provides the proven pattern this implementation is based on.

## Contact

This implementation was completed on 2026-01-20 based on approved plan at:
`.claude/plans/eager-conjuring-pearl.md`
