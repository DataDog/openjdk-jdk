package jdk.jfr.internal.context;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.LongBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.jfr.ContextAccess;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.StringPool;

public final class ContextWriter<T> implements ContextAccess<T> {
    static final ContextWriter<?> NULL = new ContextWriter<>(-1, null);
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

    @Override
    public void set(T target) {
        if (offset == -1 || descriptors == null) {
            return;
        }
        LongBuffer context = JVM.getThreadContextBuffer();
        if (context == null) {
            return;
        }
        for (ContextDescriptor cd : descriptors) {
            if (cd.order() < 8) {
                VarHandle fAccess = cd.fAccess();
                if (fAccess != null) {
                    Class<?> vType = fAccess.varType();
                    if (vType == String.class) {
                        String value = (String) fAccess.get(target);
                        context.put(offset + cd.order(), value != null ? StringPool.addString(value, false) : 0);
                    } else if (vType == boolean.class) {
                        context.put(offset + cd.order(), ((boolean) fAccess.get(target) ? 0 : 1));
                    } else {
                        context.put(offset + cd.order(), (long) fAccess.get(target));
                    }
                } else {
                    MethodHandle mAccess = cd.mAccess();
                    if (mAccess != null) {
                        Class<?> vType = mAccess.type().returnType();
                        try {
                            if (vType.isAssignableFrom(String.class)) {
                                String value = (String) mAccess.invoke(target);
                                context.put(offset + cd.order(), value != null ? StringPool.addString(value, false) : 0);
                            } else if (vType == boolean.class) {
                                context.put(offset + cd.order(), ((boolean) mAccess.invoke(target) ? 0 : 1));
                            } else {
                                context.put(offset + cd.order(), (long) mAccess.invoke(target));
                            }
                        } catch (Throwable t) {
                            throw new RuntimeException(t);
                        }
                    } else {
                        throw new IllegalStateException("Invalid context field descriptor: neither field nor method");
                    }
                }
            }
        }
    }

    @Override
    public void unset() {
        unset(null);
    }

    public void unset(T target) {
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
                if (target != null) {
                    VarHandle access = cd.fAccess();
                    if (access != null) {
                        if (access.varType() == String.class) {
                            access.set(target, null);
                        } else {
                            access.set(target, 0);
                        }
                    }
                }
            }
        }
    }
}
