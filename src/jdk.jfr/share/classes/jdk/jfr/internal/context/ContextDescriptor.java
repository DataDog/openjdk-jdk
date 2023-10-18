package jdk.jfr.internal.context;

import java.lang.invoke.VarHandle;

public record ContextDescriptor(String holderId, String name, String label, String description, VarHandle access) {
}
