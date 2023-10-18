package jdk.jfr.internal.context;

import jdk.jfr.FlightRecorder;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.PlatformRecorder;

public abstract class BaseContextType {
    public final void set() {
        if (shouldCaptureState()) {
            ContextRepository.getOrRegister(this.getClass()).write(this);
        }
    }

    public final void unset() {
        if (shouldCaptureState()) {
            ContextRepository.getOrRegister(this.getClass()).clear(this);
        }
    }

    public final boolean isActive() {
        return ContextRepository.getOrRegister(this.getClass()).isActive();
    }

    private static boolean shouldCaptureState() {
        return FlightRecorder.isInitialized() && PlatformRecorder.hasRecordings() && JVM.isContextEnabled();
    }
}
