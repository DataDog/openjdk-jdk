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

package jdk.jfr.tool;

import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.Contextual;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @summary Test jfr view --show-context flag
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.tool.TestViewContext
 */
public class TestViewContext {

    // Test event classes
    @Name("ContextEvent")
    static class ContextEvent extends Event {
        @Contextual
        String requestId;
        @Contextual
        String userId;
    }

    @Name("Span")
    static class SpanEvent extends Event {
        @Contextual
        String spanId;
        @Contextual
        String operation;
    }

    @Name("Trace")
    static class TraceEvent extends Event {
        @Contextual
        String traceId;
        @Contextual
        String service;
    }

    @Name("WorkEvent")
    @StackTrace(false)
    static class WorkEvent extends Event {
        String task;
        int duration;
    }

    public static void main(String... args) throws Throwable {
        // P0 Tests - Critical functionality
        testBasicContextDisplay();
        testNoContextFlag();
        testContextOverlap();
        testNestedContexts();
        testPerThreadContextTracking();
        testSingleTypeFilter();
        testMultipleTypeFilter();

        // GROUP BY tests - require debug build for jfr query command
        if (Platform.isDebugBuild()) {
            testGroupByContextualField();
            testGroupByMultipleContextualTypes();
            testGroupByContextualFieldWithAggregation();
        } else {
            System.out.println("Skipping GROUP BY tests (jfr query requires debug build)");
        }

        System.out.println("All tests passed!");
    }

    // P0 Test 1.1: Basic Context Display
    private static void testBasicContextDisplay() throws Throwable {
        System.out.println("Running testBasicContextDisplay...");

        Path recording = createSimpleContextRecording();
        OutputAnalyzer output = ExecuteHelper.jfr("view", "--show-context", "WorkEvent",
                recording.toAbsolutePath().toString());

        // Verify contextual columns appear
        output.shouldContain("ContextEvent.requestId");
        output.shouldContain("ContextEvent.userId");

        // Verify contextual values appear
        output.shouldContain("req-001");
        output.shouldContain("user-alice");
        output.shouldContain("req-002");
        output.shouldContain("user-bob");

        // Verify work event fields still appear
        output.shouldContain("Task-0");
        output.shouldContain("Task-1");

        Files.delete(recording);
        System.out.println("  PASSED");
    }

    // P0 Test 1.2: No Context Flag (Baseline)
    private static void testNoContextFlag() throws Throwable {
        System.out.println("Running testNoContextFlag...");

        Path recording = createSimpleContextRecording();
        OutputAnalyzer output = ExecuteHelper.jfr("view", "WorkEvent",
                recording.toAbsolutePath().toString());

        // Verify NO contextual columns appear
        output.shouldNotContain("ContextEvent.requestId");
        output.shouldNotContain("ContextEvent.userId");

        // Verify work event fields still appear
        output.shouldContain("Task-0");
        output.shouldContain("Task-1");

        Files.delete(recording);
        System.out.println("  PASSED");
    }

    // P0 Test 2.1: Context Overlap
    private static void testContextOverlap() throws Throwable {
        System.out.println("Running testContextOverlap...");

        Path recording = createContextOverlapRecording();
        OutputAnalyzer output = ExecuteHelper.jfr("view", "--show-context", "WorkEvent",
                recording.toAbsolutePath().toString());

        String outputText = output.getOutput();

        // Parse output to verify correct context matching
        // Event1 should have context, Event2 should not, Event3 should not
        String[] lines = outputText.split("\n");
        boolean foundInContext = false;
        boolean foundOutOfContext = false;

        for (String line : lines) {
            if (line.contains("InContext")) {
                if (line.contains("ctx-001")) {
                    foundInContext = true;
                }
            }
            if (line.contains("BeforeContext") || line.contains("AfterContext")) {
                // These should NOT have context values - check for N/A or empty
                if (!line.contains("ctx-001")) {
                    foundOutOfContext = true;
                }
            }
        }

        if (!foundInContext) {
            throw new RuntimeException("InContext event should have context value");
        }
        if (!foundOutOfContext) {
            throw new RuntimeException("Out-of-context events should not have context value");
        }

        Files.delete(recording);
        System.out.println("  PASSED");
    }

    // P0 Test 2.2: Nested Contexts
    private static void testNestedContexts() throws Throwable {
        System.out.println("Running testNestedContexts...");

        Path recording = createNestedContextRecording();
        OutputAnalyzer output = ExecuteHelper.jfr("view", "--show-context", "WorkEvent",
                recording.toAbsolutePath().toString());

        // Verify both Trace and Span contexts appear
        output.shouldContain("Trace.traceId");
        output.shouldContain("Trace.service");
        output.shouldContain("Span.spanId");
        output.shouldContain("Span.operation");

        // Verify values from both contexts
        output.shouldContain("trace-001");
        output.shouldContain("api-gateway");
        output.shouldContain("span-001");
        output.shouldContain("GET /users");

        Files.delete(recording);
        System.out.println("  PASSED");
    }

    // P0 Test 3.1: Per-Thread Context Tracking
    private static void testPerThreadContextTracking() throws Throwable {
        System.out.println("Running testPerThreadContextTracking...");

        Path recording = createMultiThreadContextRecording();
        OutputAnalyzer output = ExecuteHelper.jfr("view", "--show-context", "WorkEvent",
                recording.toAbsolutePath().toString());

        String outputText = output.getOutput();
        String[] lines = outputText.split("\n");

        // Verify TestViewContext-Thread-1 events have Context1 values
        // Verify TestViewContext-Thread-2 events have Context2 values
        boolean thread1HasCorrectContext = false;
        boolean thread2HasCorrectContext = false;

        for (String line : lines) {
            if (line.contains("TestViewContext-Thread-1") && line.contains("Thread1Task") && line.contains("thread1-ctx")) {
                thread1HasCorrectContext = true;
            }
            if (line.contains("TestViewContext-Thread-2") && line.contains("Thread2Task") && line.contains("thread2-ctx")) {
                thread2HasCorrectContext = true;
            }
            // Verify no context leakage
            if (line.contains("TestViewContext-Thread-1") && line.contains("thread2-ctx")) {
                throw new RuntimeException("Context leaked from TestViewContext-Thread-2 to TestViewContext-Thread-1");
            }
            if (line.contains("TestViewContext-Thread-2") && line.contains("thread1-ctx")) {
                throw new RuntimeException("Context leaked from TestViewContext-Thread-1 to TestViewContext-Thread-2");
            }
        }

        if (!thread1HasCorrectContext) {
            throw new RuntimeException("TestViewContext-Thread-1 should have thread1-ctx");
        }
        if (!thread2HasCorrectContext) {
            throw new RuntimeException("TestViewContext-Thread-2 should have thread2-ctx");
        }

        Files.delete(recording);
        System.out.println("  PASSED");
    }

    // P0 Test 4.1: Single Type Filter
    private static void testSingleTypeFilter() throws Throwable {
        System.out.println("Running testSingleTypeFilter...");

        Path recording = createMultipleContextTypesRecording();
        OutputAnalyzer output = ExecuteHelper.jfr("view", "--show-context=Span", "WorkEvent",
                recording.toAbsolutePath().toString());

        // Verify only Span columns appear
        output.shouldContain("Span.spanId");
        output.shouldContain("Span.operation");

        // Verify Trace columns do NOT appear
        output.shouldNotContain("Trace.traceId");
        output.shouldNotContain("Trace.service");

        Files.delete(recording);
        System.out.println("  PASSED");
    }

    // P0 Test 4.2: Multiple Type Filter
    private static void testMultipleTypeFilter() throws Throwable {
        System.out.println("Running testMultipleTypeFilter...");

        Path recording = createMultipleContextTypesRecording();
        OutputAnalyzer output = ExecuteHelper.jfr("view", "--show-context=Span,Trace", "WorkEvent",
                recording.toAbsolutePath().toString());

        // Verify both Span and Trace columns appear
        output.shouldContain("Span.spanId");
        output.shouldContain("Span.operation");
        output.shouldContain("Trace.traceId");
        output.shouldContain("Trace.service");

        Files.delete(recording);
        System.out.println("  PASSED");
    }

    // Helper: Create simple context recording
    private static Path createSimpleContextRecording() throws Exception {
        Path path = Utils.createTempFile("simple-context", ".jfr");

        try (Recording r = new Recording()) {
            r.enable(ContextEvent.class);
            r.enable(WorkEvent.class);
            r.start();

            // Context 1
            ContextEvent ctx1 = new ContextEvent();
            ctx1.requestId = "req-001";
            ctx1.userId = "user-alice";
            ctx1.begin();

            for (int i = 0; i < 3; i++) {
                WorkEvent work = new WorkEvent();
                work.task = "Task-" + i;
                work.duration = i * 100;
                work.commit();
                Thread.sleep(10);
            }

            ctx1.commit();

            // Context 2
            ContextEvent ctx2 = new ContextEvent();
            ctx2.requestId = "req-002";
            ctx2.userId = "user-bob";
            ctx2.begin();

            for (int i = 3; i < 5; i++) {
                WorkEvent work = new WorkEvent();
                work.task = "Task-" + i;
                work.duration = i * 100;
                work.commit();
                Thread.sleep(10);
            }

            ctx2.commit();

            r.stop();
            r.dump(path);
        }

        return path;
    }

    // Helper: Create context overlap recording
    private static Path createContextOverlapRecording() throws Exception {
        Path path = Utils.createTempFile("context-overlap", ".jfr");

        try (Recording r = new Recording()) {
            r.enable(ContextEvent.class);
            r.enable(WorkEvent.class);
            r.start();

            // Event before context
            WorkEvent before = new WorkEvent();
            before.task = "BeforeContext";
            before.duration = 0;
            before.commit();
            Thread.sleep(50);

            // Start context
            ContextEvent ctx = new ContextEvent();
            ctx.requestId = "ctx-001";
            ctx.userId = "user-test";
            ctx.begin();
            Thread.sleep(50);

            // Event during context
            WorkEvent during = new WorkEvent();
            during.task = "InContext";
            during.duration = 0;
            during.commit();
            Thread.sleep(50);

            ctx.commit();
            Thread.sleep(50);

            // Event after context
            WorkEvent after = new WorkEvent();
            after.task = "AfterContext";
            after.duration = 0;
            after.commit();

            r.stop();
            r.dump(path);
        }

        return path;
    }

    // Helper: Create nested context recording
    private static Path createNestedContextRecording() throws Exception {
        Path path = Utils.createTempFile("nested-context", ".jfr");

        try (Recording r = new Recording()) {
            r.enable(TraceEvent.class);
            r.enable(SpanEvent.class);
            r.enable(WorkEvent.class);
            r.start();

            // Outer Trace context
            TraceEvent trace = new TraceEvent();
            trace.traceId = "trace-001";
            trace.service = "api-gateway";
            trace.begin();

            // Inner Span context
            SpanEvent span = new SpanEvent();
            span.spanId = "span-001";
            span.operation = "GET /users";
            span.begin();

            // Work event within both contexts
            WorkEvent work = new WorkEvent();
            work.task = "DB-Query";
            work.duration = 100;
            work.commit();

            span.commit();
            trace.commit();

            r.stop();
            r.dump(path);
        }

        return path;
    }

    // Helper: Create multi-thread context recording
    private static Path createMultiThreadContextRecording() throws Exception {
        Path path = Utils.createTempFile("multithread-context", ".jfr");

        try (Recording r = new Recording()) {
            r.enable(ContextEvent.class);
            r.enable(WorkEvent.class);
            r.start();

            Thread thread1 = new Thread(() -> {
                ContextEvent ctx = new ContextEvent();
                ctx.requestId = "thread1-ctx";
                ctx.userId = "user-thread1";
                ctx.begin();

                WorkEvent work = new WorkEvent();
                work.task = "Thread1Task";
                work.duration = 10;
                work.commit();

                ctx.commit();
            }, "TestViewContext-Thread-1");

            Thread thread2 = new Thread(() -> {
                ContextEvent ctx = new ContextEvent();
                ctx.requestId = "thread2-ctx";
                ctx.userId = "user-thread2";
                ctx.begin();

                WorkEvent work = new WorkEvent();
                work.task = "Thread2Task";
                work.duration = 20;
                work.commit();

                ctx.commit();
            }, "TestViewContext-Thread-2");

            thread1.start();
            thread2.start();
            thread1.join();
            thread2.join();

            r.stop();
            r.dump(path);
        }

        return path;
    }

    // Helper: Create recording with multiple context types
    private static Path createMultipleContextTypesRecording() throws Exception {
        Path path = Utils.createTempFile("multiple-context-types", ".jfr");

        try (Recording r = new Recording()) {
            r.enable(TraceEvent.class);
            r.enable(SpanEvent.class);
            r.enable(WorkEvent.class);
            r.start();

            TraceEvent trace = new TraceEvent();
            trace.traceId = "trace-001";
            trace.service = "api-gateway";
            trace.begin();

            SpanEvent span = new SpanEvent();
            span.spanId = "span-001";
            span.operation = "POST /data";
            span.begin();

            WorkEvent work = new WorkEvent();
            work.task = "Processing";
            work.duration = 50;
            work.commit();

            span.commit();
            trace.commit();

            r.stop();
            r.dump(path);
        }

        return path;
    }

    // =========================================================================
    // GROUP BY Tests - require debug build (jfr query command)
    // =========================================================================

    // Test GROUP BY with contextual field reference
    private static void testGroupByContextualField() throws Throwable {
        System.out.println("Running testGroupByContextualField...");

        Path recording = createGroupByRecording();

        // Query: GROUP BY Trace.traceId
        String query = "SELECT COUNT(*), Trace.traceId FROM WorkEvent GROUP BY Trace.traceId";
        OutputAnalyzer output = ExecuteHelper.jfr("query", query,
                recording.toAbsolutePath().toString());

        // Verify output contains the trace IDs
        output.shouldContain("trace-001");
        output.shouldContain("trace-002");
        output.shouldContain("trace-003");

        // Verify count column exists
        output.shouldContain("Count");

        Files.delete(recording);
        System.out.println("  PASSED");
    }

    // Test GROUP BY with service field (different grouping)
    private static void testGroupByMultipleContextualTypes() throws Throwable {
        System.out.println("Running testGroupByMultipleContextualTypes...");

        Path recording = createGroupByRecording();

        // Query: GROUP BY Trace.service
        String query = "SELECT COUNT(*), Trace.service FROM WorkEvent GROUP BY Trace.service";
        OutputAnalyzer output = ExecuteHelper.jfr("query", query,
                recording.toAbsolutePath().toString());

        // Verify output contains the services
        output.shouldContain("api-service");
        output.shouldContain("db-service");

        Files.delete(recording);
        System.out.println("  PASSED");
    }

    // Test GROUP BY with aggregation functions
    private static void testGroupByContextualFieldWithAggregation() throws Throwable {
        System.out.println("Running testGroupByContextualFieldWithAggregation...");

        Path recording = createGroupByRecording();

        // Query: SUM aggregation with GROUP BY Trace.traceId
        String query = "SELECT SUM(duration), Trace.traceId FROM WorkEvent GROUP BY Trace.traceId";
        OutputAnalyzer output = ExecuteHelper.jfr("query", query,
                recording.toAbsolutePath().toString());

        // Verify trace IDs appear
        output.shouldContain("trace-001");
        output.shouldContain("trace-002");
        output.shouldContain("trace-003");

        // Verify aggregation column header appears
        output.shouldContain("Total");

        Files.delete(recording);
        System.out.println("  PASSED");
    }

    // Helper: Create recording for GROUP BY tests
    private static Path createGroupByRecording() throws Exception {
        Path path = Utils.createTempFile("groupby-context", ".jfr");

        try (Recording r = new Recording()) {
            r.enable(TraceEvent.class);
            r.enable(WorkEvent.class);
            r.start();

            // Create three traces with different numbers of events
            createTrace("trace-001", "api-service", 5);
            createTrace("trace-002", "db-service", 3);
            createTrace("trace-003", "api-service", 7);

            r.stop();
            r.dump(path);
        }

        return path;
    }

    // Helper: Create a trace with work events
    private static void createTrace(String traceId, String service, int numEvents) throws Exception {
        TraceEvent trace = new TraceEvent();
        trace.traceId = traceId;
        trace.service = service;
        trace.begin();

        for (int i = 0; i < numEvents; i++) {
            WorkEvent work = new WorkEvent();
            work.task = traceId + "-task-" + i;
            work.duration = i * 100;
            work.commit();
            Thread.sleep(5); // Small delay to ensure ordering
        }

        trace.commit();
    }
}
