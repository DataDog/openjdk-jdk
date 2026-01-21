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
 */
package org.openjdk.bench.jdk.jfr;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import jdk.jfr.Contextual;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;

import org.openjdk.jmh.annotations.*;

/**
 * Benchmark the overhead of the --show-context feature in JFR view.
 * Tests context tracking, timeline processing, and dynamic column addition.
 *
 * Uses the jfr CLI tool for accurate end-to-end benchmarking.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(1)
public class ViewContextOverhead {

    // Test event classes
    @Name("BenchSpan")
    static class SpanEvent extends Event {
        @Contextual
        String spanId;
        @Contextual
        String operation;
    }

    @Name("BenchTrace")
    static class TraceEvent extends Event {
        @Contextual
        String traceId;
        @Contextual
        String service;
    }

    @Name("BenchWork")
    @StackTrace(false)
    static class WorkEvent extends Event {
        String task;
        int duration;
    }

    // Parameterized workload sizes
    @Param({"100", "1000"})
    private int eventCount;

    @Param({"10", "100"})
    private int contextEventCount;

    private Path recordingPath;
    private Path deepNestingRecordingPath;
    private String jfrCommand;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        recordingPath = createTestRecording(eventCount, contextEventCount);
        deepNestingRecordingPath = createDeepNestingRecording(Math.max(10, eventCount / 10), 20);

        // Find jfr command in current JDK
        String javaHome = System.getProperty("java.home");
        jfrCommand = Path.of(javaHome, "bin", "jfr").toString();
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        Files.deleteIfExists(recordingPath);
        Files.deleteIfExists(deepNestingRecordingPath);
    }

    /**
     * Baseline: View without context tracking.
     */
    @Benchmark
    public int viewWithoutContext() throws Exception {
        return runJfrView(recordingPath, "BenchWork", false, null);
    }

    /**
     * Context enabled: View with --show-context flag showing all context types.
     */
    @Benchmark
    public int viewWithContextAll() throws Exception {
        return runJfrView(recordingPath, "BenchWork", true, null);
    }

    /**
     * Context filtered: View with --show-context=BenchSpan filtering.
     */
    @Benchmark
    public int viewWithContextFiltered() throws Exception {
        return runJfrView(recordingPath, "BenchWork", true, "BenchSpan");
    }

    /**
     * Deep nesting: View with many (20+) nested contexts active per event.
     * Tests scalability of context lookup.
     */
    @Benchmark
    public int viewWithManyActiveContexts() throws Exception {
        return runJfrView(deepNestingRecordingPath, "BenchWork", true, null);
    }

    /**
     * Runs jfr view command and returns exit code.
     */
    private int runJfrView(Path recording, String eventType, boolean showContext, String contextFilter) throws Exception {
        ProcessBuilder pb;
        if (showContext) {
            if (contextFilter != null) {
                pb = new ProcessBuilder(jfrCommand, "view", "--show-context=" + contextFilter,
                        eventType, recording.toAbsolutePath().toString());
            } else {
                pb = new ProcessBuilder(jfrCommand, "view", "--show-context",
                        eventType, recording.toAbsolutePath().toString());
            }
        } else {
            pb = new ProcessBuilder(jfrCommand, "view",
                    eventType, recording.toAbsolutePath().toString());
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Consume output to prevent blocking
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // Discard output
            }
        }

        return process.waitFor();
    }

    /**
     * Creates a test recording with specified number of work events and context events.
     * Context events are distributed to create realistic overlapping patterns.
     */
    private Path createTestRecording(int workEvents, int contextEvents) throws Exception {
        Path path = Files.createTempFile("bench-context", ".jfr");

        try (Recording r = new Recording()) {
            r.enable(SpanEvent.class);
            r.enable(TraceEvent.class);
            r.enable(WorkEvent.class);
            r.start();

            int workEventsPerContext = Math.max(1, workEvents / contextEvents);

            for (int ctx = 0; ctx < contextEvents; ctx++) {
                // Start trace context
                TraceEvent trace = new TraceEvent();
                trace.traceId = "trace-" + ctx;
                trace.service = "bench-service";
                trace.begin();

                // Start span context
                SpanEvent span = new SpanEvent();
                span.spanId = "span-" + ctx;
                span.operation = "operation-" + (ctx % 10);
                span.begin();

                // Generate work events within context
                for (int i = 0; i < workEventsPerContext; i++) {
                    WorkEvent work = new WorkEvent();
                    work.task = "Task-" + (ctx * workEventsPerContext + i);
                    work.duration = i * 10;
                    work.commit();
                }

                span.commit();
                trace.commit();
            }

            r.stop();
            r.dump(path);
        }

        return path;
    }

    /**
     * Creates a recording with deeply nested contexts.
     * Each work event has `nestingDepth` active contexts.
     */
    private Path createDeepNestingRecording(int workEvents, int nestingDepth) throws Exception {
        Path path = Files.createTempFile("bench-deep-context", ".jfr");

        try (Recording r = new Recording()) {
            r.enable(SpanEvent.class);
            r.enable(WorkEvent.class);
            r.start();

            // Create nested spans
            SpanEvent[] spans = new SpanEvent[nestingDepth];
            for (int i = 0; i < nestingDepth; i++) {
                spans[i] = new SpanEvent();
                spans[i].spanId = "span-level-" + i;
                spans[i].operation = "op-" + i;
                spans[i].begin();
            }

            // Generate work events within all nested contexts
            for (int i = 0; i < workEvents; i++) {
                WorkEvent work = new WorkEvent();
                work.task = "DeepTask-" + i;
                work.duration = i;
                work.commit();
            }

            // Close all spans in reverse order
            for (int i = nestingDepth - 1; i >= 0; i--) {
                spans[i].commit();
            }

            r.stop();
            r.dump(path);
        }

        return path;
    }
}
