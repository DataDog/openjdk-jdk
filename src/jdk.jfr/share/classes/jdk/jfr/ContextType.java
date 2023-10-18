package jdk.jfr;

import java.io.Closeable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jdk.jfr.internal.context.BaseContextType;
import jdk.jfr.internal.context.ContextRepository;

public abstract class ContextType extends BaseContextType {
    public interface Registration {
        Stream<Class<? extends ContextType>> types();
    }

    public interface Setter {
        void setAttribute(String name, String value);
        void clearAttribute(String name);
        void clearAll();
    }

    public static final class Captured<T extends ContextType.Capturable<T>> implements Closeable, AutoCloseable {
        private final T parent, current;

        Captured(T parent, T current) {
            assert parent != null;
            assert current != null;
            this.parent = parent;
            this.current = current;
        }

        @Override
        public void close() {
            parent.set();
        }

        public T get() {
            return current;
        }

        public Captured<T> capture() {
            return current.capture();
        }
    }

    public static abstract class Capturable<T extends Capturable<T>> extends ContextType {
        public Capturable() {}

        @SuppressWarnings("unchecked")
        public final Captured<T> capture() {
            return new Captured<T>((T) this, snapshot());
        }

        abstract protected T snapshot();
    }

    protected ContextType() {}

    public static Setter setterFor(Class<? extends ContextType> type) {
        return BaseContextType.setterFor(type);
    }
}
