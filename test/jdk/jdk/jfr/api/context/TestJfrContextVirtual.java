/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.api.context;

import java.lang.reflect.Constructor;
import java.net.*;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.LockSupport;

import jdk.jfr.*;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.context.ContextRegistration;
import jdk.jfr.ContextType;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.ContextAwareEvent;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.SimpleEvent;

/**
 * @test
 * @summary JFR Context sanity test.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @build jdk.test.lib.jfr.ContextAwareEvent
 * @run main/othervm jdk.jfr.api.context.TestJfrContextVirtual
 */
public class TestJfrContextVirtual {
    private static final int VIRTUAL_THREAD_COUNT = 10000; // 10_000;
    private static final int STARTER_THREADS = 10; // 10;

    @Name("test.Tester")
    private static class TestEvent extends Event {
        @Name("id")
        public int id;
    }

    @Name("test")
    public static class TestContextType extends ContextType {
        @Name("taskid")
        public String taskid;
    }

    public static void main(String[] args) throws Throwable {
        // In order to be able to set/unset context one needs to obtain a ContextAccess instance
        // The instance is associated with a context name and a number of named attributes.
        // There is only a limited number of available attribute slots so it may happen that
        // the registration request can not be fullfilled - in that case an 'empty' context will be
        // returned where the operations of setting/unsetting context will be a noop.
        boolean ctxRegistered = FlightRecorder.registerContext(TestContextType.class);

        Asserts.assertTrue(ctxRegistered);

        Recording r = new Recording();

        r.enable(ContextAwareEvent.class).withThreshold(Duration.ofMillis(0)).withoutStackTrace();
        r.enable(TestEvent.class).withThreshold(Duration.ofMillis(0));
        r.setDestination(Paths.get("/tmp/dump.jfr"));
        r.start();

        SimpleEvent e = new SimpleEvent();
        e.id = 1;
        e.commit();

        new ContextAwareEvent(1).commit();

        long ts = System.nanoTime();
        ThreadFactory factory = Thread.ofVirtual().factory();
        CompletableFuture<?>[] c = new CompletableFuture[STARTER_THREADS];
        for (int j = 0; j < STARTER_THREADS; j++) {
            int id1 = j;
            c[j] = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < VIRTUAL_THREAD_COUNT / STARTER_THREADS; i++) {
                    int id = (i * STARTER_THREADS) + id1;
                    try {
                        Thread vt = factory.newThread(() -> {
                            TestContextType captured = new TestContextType();
                            // Any context-aware event emitted after the context was set wil contain the context fields
                            captured.taskid = "Task " + id;
                            captured.set();

                            SimpleEvent e1 = new SimpleEvent();
                            e1.id = id;
                            e1.begin();
                            new ContextAwareEvent(10000 + id).commit();
                            // createEvent(eventClass, 10000 + id);
                            LockSupport.parkNanos(10_000_000L); // 10ms
                            new ContextAwareEvent(20000 + id).commit();
                            // createEvent(eventClass, 20000 + id);
                            // context will be reset to the previous state when leaving the try block
                            e1.commit();
                        });
                        vt.start();
                        vt.join();
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            });
        }
        for (int j = 0; j < STARTER_THREADS; j++) {
            c[j].get();
        }

        r.stop();

        System.out.println("===> Test took: " + (double)((System.nanoTime() - ts) / 1_000_000L) + "ms");

        verifyEvents(r, 1, 3);
    }

    private static void verifyEvents(Recording r, int ... ids) throws Exception {
        List<Integer> eventIds = new ArrayList<>();
        for (RecordedEvent event : Events.fromRecording(r)) {
            System.out.println("===> [" + event.getEventType().getId() + "]: " + event);
            // int id = Events.assertField(event, "id").getValue();
            // System.out.println("Event id:" + id);
            // eventIds.add(id);

            // String ctx = Events.assertField(event, "_ctx_1").getValue();
            // System.out.println("===> context: " + ctx);
        }
        // Asserts.assertEquals(eventIds.size(), ids.length, "Wrong number of events");
        // for (int i = 0; i < ids.length; ++i) {
        //     Asserts.assertEquals(eventIds.get(i).intValue(), ids[i], "Wrong id in event");
        // }
    }

    private static void createEvent(Class<? extends Event> eventClass, int id) {
        try {
            Constructor<? extends Event> constructor = eventClass.getConstructor();
            Event event = (Event) constructor.newInstance();
            event.begin();
            eventClass.getDeclaredField("id").setInt(event, id);
            event.end();
            event.commit();
        } catch (Exception ignored) {}
    }
}
