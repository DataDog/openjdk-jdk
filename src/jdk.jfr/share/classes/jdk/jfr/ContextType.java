package jdk.jfr;

import java.io.Closeable;
import java.util.stream.Stream;

import jdk.jfr.internal.context.BaseContextType;
import jdk.jfr.internal.context.ContextRepository;

public abstract class ContextType extends BaseContextType {
    public interface Registration {
        Stream<Class<? extends ContextType>> types();
    }

    protected ContextType() {}
}
