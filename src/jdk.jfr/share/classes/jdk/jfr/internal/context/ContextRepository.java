package jdk.jfr.internal.context;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.jfr.ContextType;
import jdk.jfr.Description;
import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.Logger;
import static jdk.jfr.internal.LogLevel.DEBUG;
import static jdk.jfr.internal.LogLevel.INFO;
import static jdk.jfr.internal.LogTag.JFR;
import static jdk.jfr.internal.LogLevel.WARN;

public final class ContextRepository {
    public static final int CAPACITY = 8; // 8 slots
    private static final long[] EMPTY = new long[0];

    private static int slotPointer = 0; // pointer to the next slot

    private static final ContextDescriptor[] allDescriptors = new ContextDescriptor[CAPACITY];
    private static final ClassValue<ContextWriter> contextWriters = new ClassValue<>() {
        @SuppressWarnings("unchecked")
        @Override
        protected ContextWriter computeValue(Class<?> type) {
            if (ContextType.class.isAssignableFrom(type)) {
                return writerFor((Class<ContextType>) type);
            }
            return ContextWriter.NULL;
        }
    };

    private ContextRepository() {
    }

    public static <T extends BaseContextType> ContextWriter getOrRegister(Class<T> contextTypeClass) {
        return contextWriters.get(contextTypeClass);
    }

    private static <T extends BaseContextType> Set<ContextDescriptor> descriptorsOf(Class<T> contextTypeClass) {
        try {
            Set<ContextDescriptor> ctxDescriptors = new HashSet<>(8);
            Name typeNameAnnot = contextTypeClass.getAnnotation(Name.class);
            String id = typeNameAnnot.value();
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            int order = 0;
            for (Field f : contextTypeClass.getFields()) {
                Name nameAnnot = f.getAnnotation(Name.class);
                Label labelAnnot = f.getAnnotation(Label.class);
                Description descAnnot = f.getAnnotation(Description.class);
                if (nameAnnot != null || labelAnnot != null || descAnnot != null) {
                    String name = nameAnnot != null ? nameAnnot.value() : f.getName();
                    String label = labelAnnot != null ? labelAnnot.value() : name;
                    String desc = descAnnot != null ? descAnnot.value() : "";
                    ctxDescriptors.add(new ContextDescriptor(order++, id, name, label, desc, lookup.unreflectVarHandle(f)));
                }
            }
            return ctxDescriptors;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static <T extends BaseContextType> ContextWriter writerFor(Class<T> type) {
        Set<ContextDescriptor> ctxDescriptors = descriptorsOf(type);
        int offset = register(ctxDescriptors);
        if (offset == -2) {
            Logger.log(JFR, INFO,
                    "Context type " + type + " can not be registered after FlightRecorder has been initialized");
        } else if (offset == -3) {
            Logger.log(JFR, WARN, "Context capacity exhausted. Context type " + type + " was not registered.");
        } else {
            Logger.log(JFR, DEBUG, "Registered type: " + type);
        }
        return offset > -1 ? new ContextWriter(offset, ctxDescriptors) : ContextWriter.NULL;
    }

    private static int register(Set<ContextDescriptor> descriptors) {
        if (!shouldCaptureState()) {
            return -1;
        }
        if (FlightRecorder.isInitialized()) {
            return -2;
        }
        if (slotPointer + descriptors.size() > CAPACITY) {
            return -3;
        }
        final int offset = slotPointer;
        int top = 0;
        for (var descriptor : descriptors) {
            allDescriptors[descriptor.order() + slotPointer] = descriptor;
            top = Math.max(top, descriptor.order());
        }
        slotPointer += top + 1;
        JVM.setUsedContextSize(slotPointer);
        return offset;
    }

    public static ContextDescriptor[] registrations() {
        return Arrays.copyOf(allDescriptors, slotPointer);
    }

    private static boolean shouldCaptureState() {
        return FlightRecorder.isAvailable() && JVM.isContextEnabled();
    }
}
