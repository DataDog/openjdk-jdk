/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.context;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.time.Duration;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.Registered;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.experimental.ContextProvider;

import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.RecurseThread;
import static jdk.test.lib.Asserts.*;

/*
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.experimental
 * @run main/othervm jdk.jfr.event.context.TestContextBasic
 */
public class TestContextBasic {
    @Label("My Context Event")
    @Name("jdk.TestContext")
    @Registered
    @Enabled
    @ContextProvider
    public static class MyContextEvent extends Event {
        @Label("spanId")
        private String spanId;

        public MyContextEvent(String spanId) {
            this.spanId = spanId;
        }
    }

    @Label("My Work Event")
    @Name("jdk.TestWork")
    @Registered
    @Enabled
    public static class MyWorkEvent extends Event {

    }

    public static void main(String[] args) throws Exception {
        Recording r = new Recording();
        r.enable(MyContextEvent.class);
        r.enable(MyWorkEvent.class);
        r.start();


        MyContextEvent ctx = new MyContextEvent("123");
        ctx.begin();
        ctx.end();
        assertFalse(ctx.shouldCommit());
        ctx.commit();

        ctx = new MyContextEvent("456");
        ctx.begin();
        new MyWorkEvent().commit();
        ctx.end();
        assertTrue(ctx.shouldCommit());
        ctx.commit();

        r.stop();

        List<RecordedEvent> events = Events.fromRecording(r);
        System.out.println("===> events: " + events.size());
        for (RecordedEvent event : events) {
            System.out.println("===> " + event);
        }


//        hasEvent(r, MyWorkEvent.class.getName());
//        assertEquals(eventCount(r, MyContextEvent.class.getName()), 1);
//
        checkWithExecutionSamples();
//
//        hasEvent(r, MyContextEvent.class.getName());
    }

    private static void checkWithExecutionSamples() throws Exception {
        Thread t = new Thread(() -> {
            System.out.println("===> running thread");
            MyContextEvent ctx1 = new MyContextEvent("123");

            Random rand = new Random();
            long ts = System.nanoTime();
            long accumulated = 0;
            ctx1.begin();
            for (int i = 0; i < 1000000; i++) {
                accumulated += sieve(rand.nextInt(10000));
            }
            ctx1.end();
            ctx1.commit();

            System.out.println();
            System.out.println("=== " + accumulated);
            System.out.println("Duration: " + (System.nanoTime() - ts) / 1000000 + " ms");

//            assertTrue(ctx1.shouldCommit());

            new MyWorkEvent().commit();
        });
        t.setDaemon(true);
        t.start();

        Recording r = new Recording();
        r.enable(MyWorkEvent.class);
        r.enable(MyContextEvent.class);
        r.enable(EventNames.ExecutionSample).withPeriod(Duration.ofMillis(1));
        r.start();

        t.join();

        r.stop();
        r.dump(java.nio.file.Paths.get("/tmp", "recording.jfr"));
        List<RecordedEvent> events = Events.fromRecording(r);
        System.err.println("===> events: " + events.size());
//        for (RecordedEvent event : events) {
//            System.err.println("===> " + event);
//        }
    }

    private static void hasEvent(Recording r, String name) throws IOException {
        List<RecordedEvent> events = Events.fromRecording(r);
        Events.hasEvents(events);

        for (RecordedEvent event : events) {
            System.err.println("===> " + event.getEventType().getName());
            if (event.getEventType().getName().equals(name)) {
                return;
            }
        }
        fail("Missing event " + name + " in recording " + events.toString());
    }

    private static int eventCount(Recording r, String name) throws IOException {
        List<RecordedEvent> events = Events.fromRecording(r);
        Events.hasEvents(events);

        int count = 0;
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(name)) {
                count++;
            }
        }
        return count;
    }

    private static long sieve(int n) {
        // Create a boolean array "prime[0..n]" and initialize
        // all entries it as true. A value in prime[i] will
        // finally be false if i is Not a prime, true otherwise.
        boolean prime[] = new boolean[n+1];
        for(int i=0;i<=n;i++)
            prime[i] = true;

        for(int p = 2; p*p <=n; p++) {
            // If prime[p] is not changed, then it is a prime
            if(prime[p] == true) {
                // Updating all multiples of p
                for(int i = p*p; i <= n; i += p)
                    prime[i] = false;
            }
        }

        long hash = 13;
        // Print all prime numbers
        for(int i = 2; i <= n; i++) {
            hash = hash * 31 + (prime[i] ? 17 : 0);
        }
        return hash;
    }
}
