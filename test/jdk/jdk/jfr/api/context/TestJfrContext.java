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

import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.*;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.ContextType;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.ContextAwareEvent;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary JFR Context sanity test.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @build jdk.test.lib.jfr.ContextAwareEvent
 * @run main/othervm jdk.jfr.api.context.TestJfrContext
 */
public class TestJfrContext {
    @Name("test")
    @Description("Test JFR Context")
    public static class TestContextType extends ContextType {
        @Name("state")
        @Description("Global application state")
        public String state;

        @Name("result")
        @Description("TRUE/FALSE")
        public String result; 
    }

    @Name("too_large")
    @Description("Not registered because of context capacity")
    public static class TooLargeContextType extends ContextType {
        @Name("attr1")
        public String attr1;

        @Name("attr2")
        public String attr2;

        @Name("attr3")
        public String attr3;

        @Name("attr4")
        public String attr4;

        @Name("attr5")
        public String attr5;

        @Name("attr6")
        public String attr6;

        @Name("attr7")
        public String attr7;

        @Name("attr8")
        public String attr8;
    }

    public static void main(String[] args) throws Throwable {
        boolean testCtxRegistered = FlightRecorder.registerContext(TestContextType.class);
        boolean largeCtxRegistered = FlightRecorder.registerContext(TooLargeContextType.class);

        Asserts.assertTrue(testCtxRegistered);
        Asserts.assertFalse(largeCtxRegistered);

        Recording r = new Recording();
        r.enable("jdk.JavaMonitorWait").with("threshold", "5 ms");
        r.setDestination(Paths.get("/tmp/dump.jfr"));
        r.start();

        TestContextType captured = new TestContextType();
        captured.state = "enabled";
        captured.result = "FALSE";
        captured.set();

        ClassLoader classLoader = TestJfrContext.class.getClassLoader();
        Class<? extends Event> eventClass =
                classLoader.loadClass("jdk.test.lib.jfr.ContextAwareEvent").asSubclass(Event.class);

        r.enable(eventClass).withThreshold(Duration.ofMillis(0)).withoutStackTrace();
        new ContextAwareEvent(1).commit();
        r.disable(eventClass);
        new ContextAwareEvent(2).commit();
        synchronized (captured) {
            captured.wait(20);
        }
        r.enable(eventClass).withThreshold(Duration.ofMillis(0)).withoutStackTrace();
        captured.state = "disabled";
        captured.result = "TRUE";
        captured.set();
        synchronized (captured) {
            TooLargeContextType tlct = new TooLargeContextType();
            tlct.attr1 = "value1";
            captured.wait(20);
        }
        new ContextAwareEvent(3).commit();
        r.stop();

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
}
