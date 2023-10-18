package jdk.jfr;

import jdk.jfr.internal.context.ContextRepository;

public interface ContextAccess<T> {
    static <T> ContextAccess<T> forType(Class<T> type) {
        return ContextRepository.getOrRegister(type);
    }

    void set(T target);
    void unset();
}
