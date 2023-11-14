/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8313816
 * @summary TODO
 * @requires vm.jvmti
 * @requires vm.flagless
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @modules java.instrument java.base/jdk.internal.org.objectweb.asm
 * @compile GetStackTraceAndRetransformTest.java
 * @run main/othervm/native GetStackTraceAndRetransformTest
 */

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.concurrent.CyclicBarrier;
import java.util.jar.JarFile;
import java.util.concurrent.locks.LockSupport;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.helpers.ClassFileInstaller;
public class GetStackTraceAndRetransformTest {
    public static final class Shared {
        public static volatile Instrumentation inst;
        public static volatile boolean retransformed = false;
        public static volatile boolean active = true;

        public static void waitForRetransformed() {
            while (!retransformed && active) {
                LockSupport.parkNanos(1000000);
            }
            Shared.retransformed = false;
        }

        public static void retransform() {
            try {
                Shared.inst.retransformClasses(new Class[] { Transformable.class });
            } catch (UnmodifiableClassException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String agentManifest =
            "Premain-Class: " + GetStackTraceAndRetransformTest.Agent.class.getName() + "\n"
            + "Can-Retransform-Classes: true\n";
    private static String launcherManifest =
            "Main-Class: " + GetStackTraceAndRetransformTest.Launcher.class.getName() + "\n";

    public static void main(String args[]) throws Throwable {
        String agentJar = buildAgent();
        String launcherJar = buildLauncher();
        String bootCpExt = GetStackTraceAndRetransformTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        int idx = bootCpExt.lastIndexOf("/classes/0/");
        if (idx != -1) {
            bootCpExt = bootCpExt.substring(0, idx) + "/classes/0/test/lib";
        }
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-Xbootclasspath/a:" + bootCpExt,
                "--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED",
                "-javaagent:" + agentJar,
                "-agentlib:GetStackTraceAndRetransformTest",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI",
                "-XX:ErrorFile=/tmp/hs_err.log",
                "-jar", launcherJar);
        System.err.println("===> cmd: " + pb.command());
        OutputAnalyzer oa = ProcessTools.executeProcess(pb);
        System.err.println("===> out: \n" + oa.getOutput());
        oa.shouldHaveExitValue(0);
    }

    private static String buildAgent() throws Exception {
        Path jar = Files.createTempFile(Paths.get("."), null, ".jar");
        String jarPath = jar.toAbsolutePath().toString();
        ClassFileInstaller.writeJar(jarPath,
                ClassFileInstaller.Manifest.fromString(agentManifest),
                Agent.class.getName());
        return jarPath;
    }

    private static String buildLauncher() throws Exception {
        Path jar = Files.createTempFile(Paths.get("."), null, ".jar");
        String jarPath = jar.toAbsolutePath().toString();
        ClassFileInstaller.writeJar(jarPath,
                ClassFileInstaller.Manifest.fromString(launcherManifest),
                GetStackTraceAndRetransformTest.class.getName(),
                Worker.class.getName(),
                Transformable.class.getName(),
                Shared.class.getName(),
                Launcher.class.getName(),
                SimpleTransformer.class.getName(),
                SimpleTransformer.class.getName() + "$1",
                SimpleTransformer.class.getName() + "$1$1");
        return jarPath;
    }

    private static class Transformable {
        static void callAction1() {
            Shared.retransform();
            capture(Thread.currentThread());
        }

        static void callAction2() {
            capture(Thread.currentThread());
        }

        static void callAction3() {
            capture(Thread.currentThread());
        }
    }

    public static class Launcher {
        public static void main(String[] args) {
            System.err.println("===> Launching");
            initialize(Transformable.class);

            Worker.doit();
            Worker.step();
            Worker.stop();

            WhiteBox wb = WhiteBox.getWhiteBox();
            wb.cleanMetaspaces();
            LockSupport.parkNanos(3_000_000_000L);
            System.err.println("===> Check");
            check();
        }
    }

    public static class Worker {
        private static Thread thrd = null;
        private static final CyclicBarrier barrier = new CyclicBarrier(2);

        static {
            System.loadLibrary("GetStackTraceAndRetransformTest");
        }

        public static void doit() {
            if (thrd != null) {
                throw new RuntimeException("Worker thread already started");
            }
            System.err.println("===> Starting worker thread");
            thrd = new Thread(() -> {
                int counter = 1;
                try {
                    while (Shared.active) {
                        barrier.await();
                        System.err.println("===> Calling action " + counter);
                        switch (counter) {
                            case 1:
                                Transformable.callAction1();
                                break;
                            case 2:
                                Transformable.callAction2();
                                break;
                            case 3:
                                Transformable.callAction3();
                                break;
                        }
                        counter = (counter % 3) + 1;
                        barrier.await();
                    }
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
                System.err.println("===> Exiting worker thread");
            }, "Worker Thread");
            thrd.setDaemon(true);
            thrd.start();
        }

        public static void step() {
            try {
                barrier.await(); // enter step
                barrier.await(); // exit step
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        public static void stop() {
            Shared.active = false;
            step();
            try {
                thrd.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class Agent implements ClassFileTransformer {
        public static void premain(String args, Instrumentation inst) {
            inst.addTransformer(new SimpleTransformer(), true);
            Shared.inst = inst;
        }
    }

    private static class SimpleTransformer implements ClassFileTransformer {
        private static int counter = 0;
        @Override
        public byte[] transform(ClassLoader loader,
                                String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer
                                ) throws IllegalClassFormatException {
            if (classBeingRedefined != null && className.equals("GetStackTraceAndRetransformTest$Transformable")) {
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

                try {
                    ClassVisitor cv = new ClassVisitor(Opcodes.ASM9, cw) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                            if (name.startsWith("callAction")) {
                                return new MethodVisitor(Opcodes.ASM5, mv) {
                                    @Override
                                    public void visitCode() {
                                        super.visitCode();
                                        mv.visitFieldInsn(Opcodes.GETSTATIC, System.class.getName().replace('.', '/'), "err", Type.getDescriptor(java.io.PrintStream.class));
                                        mv.visitLdcInsn("Hello from transformed method: " + name.replace("callAction", "") + ", " + (++counter));
                                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, java.io.PrintStream.class.getName().replace('.', '/'), "println", "(Ljava/lang/String;)V", false);
                                    }
                                };
                            }
                            return mv;
                        }
                    };
                    cr.accept(cv, 0);
                    Shared.retransformed = true;
                    System.err.println("===> retransformed");
                    return cw.toByteArray();
                } catch (Throwable t) {
                    t.printStackTrace(System.err);
                    throw t;
                }
            }
            return null;
        }
    }

    public static native void initialize(Class<?> target);
    public static native void capture(Thread thread);
    public static native void check();
}
