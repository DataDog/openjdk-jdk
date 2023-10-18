package jdk.jfr.internal.context;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

public record ContextDescriptor(int order, String holderId, String name, String label, String description, VarHandle fAccess, MethodHandle mAccess) {
}
