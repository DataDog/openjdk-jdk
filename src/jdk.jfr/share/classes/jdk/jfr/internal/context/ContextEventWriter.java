package jdk.jfr.internal.context;

import java.nio.LongBuffer;

import jdk.jfr.internal.JVM;
import jdk.jfr.internal.event.EventWriter;

public final class ContextEventWriter {
    public static void putContext(EventWriter writer) {
        LongBuffer context = JVM.getThreadContextBuffer();
        int limit = context.capacity();
        for (int i = 0; i < limit; i++) {
            writer.putLong(context.get(i));
        }
    }
}