package jdk.jfr.internal.context;

import java.lang.invoke.MethodHandles.Lookup;
import java.nio.LongBuffer;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.jfr.ContextType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.StringPool;

public final class ContextWriter {
    static final ContextWriter NULL = new ContextWriter(-1, null);
    private final int offset;
    private final List<ContextDescriptor> descriptors;

    ContextWriter(int offset, List<ContextDescriptor> descriptors) {
        this.offset = offset;
        this.descriptors = offset > -1 ? Collections.unmodifiableList(descriptors) : null;
    }

    public boolean isActive() {
        return offset != -1;
    }

    void write(BaseContextType target) {
        if (offset == -1 || descriptors == null) {
            return;
        }
        LongBuffer context = JVM.getThreadContextBuffer();
        if (context == null) {
            return;
        }
        int cntr = offset;
        for (ContextDescriptor cd : descriptors) {
            String value = (String) cd.access().get(target);
            context.put(cntr++, value != null ? StringPool.addString(value, false) : 0);
            if (cntr >= 8) {
                break;
            }
        }
    }

    void clear(BaseContextType target) {
        if (offset == -1 || descriptors == null) {
            return;
        }
        LongBuffer context = JVM.getThreadContextBuffer();
        if (context == null) {
            return;
        }
        int cntr = 0;
        for (ContextDescriptor cd : descriptors) {
            context.put(offset + (cntr++), 0L);
            cd.access().set(target, null);
        }
    }
}
