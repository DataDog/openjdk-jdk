package jdk.jfr.internal.context;

import java.nio.LongBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.jfr.ContextType;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.StringPool;

public final class ContextWriter implements ContextType.Setter {
    static final ContextWriter NULL = new ContextWriter(-1, null);
    private final int offset;
    private final Set<ContextDescriptor> descriptors;
    private final Map<String, ContextDescriptor> attributeIndexMap;

    ContextWriter(int offset, Set<ContextDescriptor> descriptors) {
        this.offset = offset;
        this.descriptors = offset > -1 ? Collections.unmodifiableSet(descriptors) : null;
        this.attributeIndexMap = this.descriptors != null ? this.descriptors.stream().collect(Collectors.toUnmodifiableMap(ContextDescriptor::name, cd -> cd)) : null;
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
        for (ContextDescriptor cd : descriptors) {
            if (cd.order() < 8) {
                String value = (String) cd.access().get(target);
                context.put(offset + cd.order(), value != null ? StringPool.addString(value, false) : 0);
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
        for (ContextDescriptor cd : descriptors) {
            if (cd.order() < 8) {
                context.put(offset + cd.order(), 0L);
                cd.access().set(target, null);
            }
        }
    }

    @Override
    public void clearAll() {
        if (offset == -1 || descriptors == null) {
            return;
        }
        LongBuffer context = JVM.getThreadContextBuffer();
        if (context == null) {
            return;
        }
        for (ContextDescriptor cd : descriptors) {
            context.put(offset + cd.order(), 0L);
        }
    }

    @Override
    public void setAttribute(String name, String value) {
        LongBuffer context = JVM.getThreadContextBuffer();
        if (context == null) {
            return;
        }
        int pos = getContextIndex(name, String.class);
        if (pos < 0) {
            System.err.println("===> set err: " + name + ": " + pos);
            return;
        }
        context.put(pos, StringPool.addString(value, false));
    }

    @Override
    public void clearAttribute(String name) {
        LongBuffer context = JVM.getThreadContextBuffer();
        if (context == null) {
            return;
        }
        int pos = getContextIndex(name, String.class);
        if (pos < 0) {
            System.err.println("===> clear err: " + name + ": " + pos);
            return;
        }
        context.put(pos, 0);
    }

    private int getContextIndex(String name, Class<?> type) {
        ContextDescriptor cd = attributeIndexMap.get(name);
        if (cd == null) {
            return -1;
        }
        if (cd.access().varType() != type) {
            return -2;
        }
        return offset + cd.order();
    }
}
