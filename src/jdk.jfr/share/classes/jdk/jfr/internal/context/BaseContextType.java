package jdk.jfr.internal.context;

import jdk.jfr.ContextType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.PlatformRecorder;

public abstract class BaseContextType {
    @SuppressWarnings("unchecked")
    public final void set() {
        if (shouldCaptureState()) {
            ContextRepository.getOrRegister((Class<BaseContextType>)this.getClass()).set(this);
        }
    }

    @SuppressWarnings("unchecked")
    public final void unset() {
        if (shouldCaptureState()) {
            ContextRepository.getOrRegister((Class<BaseContextType>)this.getClass()).unset(this);
        }
    }

    public final boolean isActive() {
        return ContextRepository.getOrRegister(this.getClass()).isActive();
    }

    private static boolean shouldCaptureState() {
        return FlightRecorder.isInitialized() && PlatformRecorder.hasRecordings() && JVM.isContextEnabled();
    }
}
