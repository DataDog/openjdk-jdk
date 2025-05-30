/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.VirtualMachine.canGetMonitorInfo;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import java.util.*;
import java.io.*;

/**
 * The test for the implementation of an object of the type     <BR>
 * VirtualMachine.                                              <BR>
 *                                                              <BR>
 * The test checks up that results of the method                <BR>
 * <code>com.sun.jdi.VirtualMachine.canGetMonitorInfo()</code>  <BR>
 * complies with its spec.                                      <BR>
 * <BR>
 * The case for testing includes two threads in a debuggee.             <BR>
 * The test works as follows. After being started up,                   <BR>                                            <BR>
 * the debuggee creates a 'lockingObject' for synchronizing threads,    <BR>
 * enters a synchronized block in which it creates new thread, thread2, <BR>
 * informs the debugger of the thread2 creation,                        <BR>
 * and is waiting for reply.                                            <BR>
 * Since the thread2 use the same locking object in its 'run' method    <BR>
 * it is locked up until main thread leaves the synchronized block.     <BR>
 * Upon the receiption a message from the debuggee, the debugger        <BR>
 * - gets ThreadReference thread2 mirroring thread2 in the debuggee;    <BR>
 * - calls to the method VirtualMachine.canGetCurrentContendedMonitor() <BR>
 *   to detect whether debuggee's VM supports corresponding method.     <BR>
 *   This is because the debugger needs to get a special object,        <BR>
 *   ObjectReference monitor, mirroring a monitor in the debuggee       <BR>
 *   on which debuggee's thread2 is locked up and waiting;              <BR>
 * - If the needed facility is not supported the debugger cancels the test.<BR>
 * - Othewise, it suspends the thread2, calls to the method             <BR>
 *   tread2.currentContendedMonitor() to get the object "monitor"       <BR>
 *   mirroring a monitor in the debuggee and gets a boolean value       <BR>
 *   returned by the method VirtualMachine.canGetMonitorInfo()          <BR>
 *                                                                      <BR>
 * Then if the value is true the debugger checks up that call to        <BR>
 * the method monitor.waitingThreads() doesn't throw                    <BR>
 *    UnsupportedOperationException.                                    <BR>
 * If the value is false the debugger checks up that call to the        <BR>
 * the method monitor.waitingThreads() does throw                       <BR>
 *    UnsupportedOperationException.                                    <BR>
 */

public class cangetmonitorinfo001 {

    //----------------------------------------------------- templete section
    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int PASS_BASE = 95;

    //----------------------------------------------------- templete parameters
    static final String
    sHeader1 = "\n==> nsk/jdi/VirtualMachine/canGetMonitorInfo/cangetmonitorinfo001  ",
    sHeader2 = "--> debugger: ",
    sHeader3 = "##> debugger: ";

    //----------------------------------------------------- main method

    public static void main (String argv[]) {
        int result = run(argv, System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }
    }

    public static int run (String argv[], PrintStream out) {
        return new cangetmonitorinfo001().runThis(argv, out);
    }

    //--------------------------------------------------   log procedures

    private static Log  logHandler;

    private static void log1(String message) {
        logHandler.display(sHeader1 + message);
    }
    private static void log2(String message) {
        logHandler.display(sHeader2 + message);
    }
    private static void log3(String message) {
        logHandler.complain(sHeader3 + message);
    }

    //  ************************************************    test parameters

    private String debuggeeName =
        "nsk.jdi.VirtualMachine.canGetMonitorInfo.cangetmonitorinfo001a";

    private String testedClassName =
        "nsk.jdi.VirtualMachine.canGetMonitorInfo.Threadcangetmonitorinfo001a";

    //String mName = "nsk.jdi.VirtualMachine.canGetMonitorInfo";

    //====================================================== test program
    //------------------------------------------------------ common section

    static ArgumentHandler      argsHandler;

    static int waitTime;

    static VirtualMachine   vm   = null;

    ReferenceType     testedclass  = null;
    ThreadReference   thread2      = null;


    static int  testExitCode = PASSED;

    static final int returnCode0 = 0;
    static final int returnCode1 = 1;
    static final int returnCode2 = 2;
    static final int returnCode3 = 3;
    static final int returnCode4 = 4;

    //------------------------------------------------------ methods

    private int runThis (String argv[], PrintStream out) {

        Debugee debuggee;

        argsHandler     = new ArgumentHandler(argv);
        logHandler      = new Log(out, argsHandler);
        Binder binder   = new Binder(argsHandler, logHandler);

        if (argsHandler.verbose()) {
            debuggee = binder.bindToDebugee(debuggeeName + " -vbs");
        } else {
            debuggee = binder.bindToDebugee(debuggeeName);
        }

        waitTime = argsHandler.getWaitTime();


        IOPipe pipe     = new IOPipe(debuggee);

        debuggee.redirectStderr(out);
        log2(debuggeeName + " debuggee launched");
        debuggee.resume();

        String line = pipe.readln();
        if ((line == null) || !line.equals("ready")) {
            log3("signal received is not 'ready' but: " + line);
            return FAILED;
        } else {
            log2("'ready' recieved");
        }

        vm = debuggee.VM();
        ReferenceType debuggeeClass = debuggee.classByName(debuggeeName);

    //------------------------------------------------------  testing section
        log1("      TESTING BEGINS");

        for (int i = 0; ; i++) {

            pipe.println("newcheck");
            line = pipe.readln();

            if (line.equals("checkend")) {
                log2("     : returned string is 'checkend'");
                break ;
            } else if (!line.equals("checkready")) {
                log3("ERROR: returned string is not 'checkready'");
                testExitCode = FAILED;
                break ;
            }

            log1("new checkready: #" + i);

            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ variable part

            int expresult = returnCode0;

            String threadName = "Thread2";

            ObjectReference monitor      = null;
            List            classes      = null;


            label0: {

                log2("getting ThreadReference objects");
                try {
                    classes     = vm.classesByName(testedClassName);
                    testedclass = (ReferenceType) classes.get(0);
                } catch ( Exception e) {
                    log3("ERROR: Exception at very beginning !? : " + e);
                    expresult = returnCode1;
                    break label0;
                }

                thread2 = debuggee.threadByFieldNameOrThrow(debuggeeClass, "thread2", threadName);
            }

            label1: {
                if (expresult != returnCode0)
                    break label1;

                if (!vm.canGetCurrentContendedMonitor()) {
                    log2(".......vm.canGetCurrentContendedMonitor() == false; the test is cancelled");
                    break label1;
                }
                log2(".......vm.canGetCurrentContendedMonitor() == true");


                log2("       supending the thread2");
                thread2.suspend();

                log2("         monitor = thread2.currentContendedMonitor();");
                try {
                    monitor = waitForMonitor(thread2);
                } catch ( UnsupportedOperationException e1 ) {
                        log3("ERROR: UnsupportedOperationException");
                        expresult = returnCode1;
                } catch ( Exception e2 ) {
                        expresult = returnCode1;
                        log3("ERROR: UNEXPECTED Exception is thrown : " + e2);
                        e2.printStackTrace();
                }
                if (expresult != returnCode0)
                    break label1;


                if (vm.canGetMonitorInfo()) {
                    log2(".......vm.canGetMonitorInfo() == true");
                    log2("       checking up on no UnsupportedOperationException trown");
                    try {
                        monitor.waitingThreads();
                        log2("        no Exception thrown");
                    } catch ( UnsupportedOperationException e1 ) {
                        log3("ERROR: UnsupportedOperationException");
                        expresult = returnCode1;
                    } catch ( Exception e2 ) {
                        expresult = returnCode1;
                        log3("ERROR: UNEXPECTED Exception is thrown : " + e2);
                        e2.printStackTrace();
                    }
                } else {
                    log2(".......vm.canGetMonitorInfo() == false");
                    log2("       checking up on UnsupportedOperationException trown");
                    try {
                        monitor.waitingThreads();
                        log3("ERROR: no UnsupportedOperationException thrown");
                        expresult = returnCode1;
                    } catch ( UnsupportedOperationException e1 ) {
                        log2("        UnsupportedOperationException thrown");
                    } catch ( Exception e2 ) {
                        log3("ERROR: UNEXPECTED Exception is thrown : " + e2);
                        e2.printStackTrace();
                        expresult = returnCode1;
                    }
                }

            }

            log2("       resuming the thread2  (for case it was suspended)");
            thread2.resume();

            log2("......instructing mainThread to leave synchronized block");
            pipe.println("continue");
            line = pipe.readln();
            if (!line.equals("docontinue")) {
                log3("ERROR: returned string is not 'docontinue'");
                expresult = returnCode4;
            }

            //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            log2("     the end of testing");
            if (expresult != returnCode0)
                testExitCode = FAILED;
        }
        log1("      TESTING ENDS");

    //--------------------------------------------------   test summary section
    //-------------------------------------------------    standard end section

        pipe.println("quit");
        log2("waiting for the debuggee to finish ...");
        debuggee.waitFor();

        int status = debuggee.getStatus();
        if (status != PASSED + PASS_BASE) {
            log3("debuggee returned UNEXPECTED exit status: " +
                    status + " != PASS_BASE");
            testExitCode = FAILED;
        } else {
            log2("debuggee returned expected exit status: " +
                    status + " == PASS_BASE");
        }

        if (testExitCode != PASSED) {
            logHandler.complain("TEST FAILED");
        }
        return testExitCode;
    }

    // This function will wait until the debuggee thread has reached its monitor.
    // The debuggee thread is already running, and should just be a few instructions
    // away from reaching the monitor. We should not have to wait a long time.
    //
    // The debuggee thread should be suspended before calling this method,
    // and it should still be suspended after this call.
    private ObjectReference waitForMonitor(ThreadReference threadReference) throws Exception {
        // Wait for at most 10 seconds. That is more than enough.
        long remainingWaitMs = 10000;
        final long iterationWaitMs = 100;

        while (remainingWaitMs >= 0) {
            ObjectReference monitor = threadReference.currentContendedMonitor();
            if (monitor != null) {
                return monitor;
            }

            log2("Waiting for currentContendedMonitor. remainingWaitMs " + remainingWaitMs);
            remainingWaitMs -= iterationWaitMs;

            try {
                // Resume thread so it can reach its monitor. Suspend again after wait.
                threadReference.resume();
                Thread.currentThread().sleep(iterationWaitMs);
            } finally {
                threadReference.suspend();
            }
        }

        String msg = String.format("No currentContendedMonitor in thread '%s'", threadReference);
        throw new Exception(msg);
    }
}
