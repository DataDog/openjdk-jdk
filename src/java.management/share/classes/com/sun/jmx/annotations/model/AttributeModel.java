/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.jmx.annotations.model;

import com.sun.jmx.mbeanserver.Introspector;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.management.Descriptor;
import javax.management.IntrospectionException;
import javax.management.annotations.AttributeAccess;
import javax.management.annotations.ManagedAttribute;
import javax.management.annotations.Tag;

/**
 * An MBean attribute specific model
 *
 */
public final class AttributeModel extends DescribedModel {
    private final Class<?> type;

    private MethodHandle getter, setter;
    private AttributeAccess access;

    public AttributeModel(Method m) throws IntrospectionException {
        ManagedAttribute annot = m.getAnnotation(ManagedAttribute.class);
        if (annot == null) {
            throw new IntrospectionException("Can not build attribute model for a non-annotated field");
        }
        this.access = annot.access();

        setDescription(annot.description());
        addTags(annot);

        inferAttribute(annot, m);
        if (getter != null) {
            type = getter.type().returnType();
        } else if (setter != null) {
            type = setter.type().parameterType(1);
        } else {
            throw new IntrospectionException("Unable to infer attribute type from " + m);
        }

        setDescriptor(Introspector.descriptorForElement(m));
    }

    public AttributeModel(Field f) throws IntrospectionException {
        ManagedAttribute annot = f.getAnnotation(ManagedAttribute.class);
        if (annot == null) {
            throw new IntrospectionException("Can not build attribute model for a non-annotated field");
        }

        this.access = annot.access();

        setDescription(annot.description());
        addTags(annot);
        type = f.getType();

        inferAttribute(annot, f);

        setDescriptor(Introspector.descriptorForElement(f));
    }

    private void inferAttribute(ManagedAttribute annot, Method m) throws IntrospectionException {
        try {
            AttributeAccess attrAccess = annot.access();
            String mName = m.getName();
            String aName = annot.name();
            if (aName.isEmpty()) {
                // infer the attribute name and type
                if (mName.startsWith("get") || mName.startsWith("is")) {
                    if (aName.isEmpty()) {
                        if (mName.startsWith("get")) {
                            aName = mName.substring(3);
                        } else {
                            aName = mName.substring(2);
                        }
                    }
                    attrAccess = annot.setter().isEmpty() ? AttributeAccess.READ : AttributeAccess.READWRITE;
                } else if (mName.startsWith("set")) {
                    if (aName.isEmpty()) {
                        aName = mName.substring(3);
                    }
                    attrAccess = annot.getter().isEmpty() ? AttributeAccess.WRITE : AttributeAccess.READWRITE;
                } else {
                    throw new IntrospectionException("Can not infer a valid attribute name: " + m);
                }
                if (annot.access() != AttributeAccess.READWRITE && attrAccess != annot.access()) {
                    throw new IntrospectionException("Declared attribute access [" + annot.access() + "] " +
                                                     "differs from the inferred one [" + attrAccess + "]");
                }
            }
            switch (attrAccess) {
                case READ: {
                    if (!isGetterSignature(m)) {
                        throw new IntrospectionException("Attribute getter must not have any arguments and must return a value: " + m);
                    }
                    getter = toMethodHandle(m);
                    setter = null;
                    break;
                }
                case WRITE: {
                    if (!isSetterSignature(m)) {
                        throw new IntrospectionException("Attribute setter must have exactly one argument and must not return a value: " + m);
                    }
                    setter = toMethodHandle(m);
                    getter = null;
                    break;
                }
                case READWRITE: {
                    if (!annot.getter().isEmpty()) {
                        try {
                            if (isSetterSignature(m)) {
                                getter = MethodHandles.publicLookup().findVirtual(m.getDeclaringClass(), annot.getter(), MethodType.methodType(m.getParameterTypes()[0]));
                            } else {
                                throw new IntrospectionException("Can not infer the attribute type from method " + m);
                            }
                        } catch (NoSuchMethodException | IllegalAccessException ex) {
                            throw new IntrospectionException(ex.getMessage());
                        }
                    }
                    if (!annot.setter().isEmpty()) {
                        try {
                            if (isGetterSignature(m)) {
                                setter = MethodHandles.publicLookup().findVirtual(m.getDeclaringClass(), annot.setter(), MethodType.methodType(void.class, m.getReturnType()));
                            } else {
                                throw new IntrospectionException("Can not infer the attribute type from method " + m);
                            }
                        } catch (NoSuchMethodException | IllegalAccessException ex) {
                            throw new IntrospectionException(ex.getMessage());
                        }
                    }
                    if (getter == null && isGetterSignature(m)) {
                        getter = MethodHandles.publicLookup().unreflect(m);
                    }
                    if (setter == null && isSetterSignature(m)) {
                        setter = MethodHandles.publicLookup().unreflect(m);
                    }
                    if (getter == null && setter == null) {
                        throw new IntrospectionException("An attribute defining method must be either a getter or setter");
                    }
                    break;
                }
            }

            setName(aName);
        } catch (IllegalAccessException ex) {
            throw new IntrospectionException(ex.getMessage());
        }
    }

    private static boolean isSetterSignature(Method m) {
        return m.getParameterCount() == 1 && m.getReturnType().equals(void.class);
    }

    private static boolean isGetterSignature(Method m) {
        return m.getParameterCount() == 0 && !m.getReturnType().equals(void.class);
    }

    private void inferAttribute(ManagedAttribute annot, Field f) throws IntrospectionException {
        if (isReadable(annot)) {
            if (annot.getter().isEmpty()) {
                try {
                    getter = MethodHandles.publicLookup().findGetter(f.getDeclaringClass(), f.getName(), f.getType());
                } catch (NoSuchFieldException e) {
                    throw new IntrospectionException(e.getMessage());
                } catch (IllegalAccessException e) {
                    try {
                        MethodHandle mh = MethodHandles.lookup().findStatic(Trampoline.class, "getField", MethodType.methodType(Object.class, Field.class, Object.class));
                        getter = mh.bindTo(f);
                    } catch (NoSuchMethodException | IllegalAccessException ex) {
                        throw new IntrospectionException(ex.getMessage());
                    }
                }
            } else {
                try {
                    getter = MethodHandles.publicLookup().findVirtual(f.getDeclaringClass(), annot.getter(), MethodType.methodType(f.getType()));
                } catch (NoSuchMethodException | IllegalAccessException ex) {
                    throw new IntrospectionException(ex.getMessage());
                }
            }
        } else {
            if (!annot.getter().isEmpty()) {
                throw new IntrospectionException("Can not specify 'getter' for a non-readable attribute");
            }
        }

        if (isWritable(annot)) {
            if (annot.setter().isEmpty()) {
                try {
                    setter = MethodHandles.publicLookup().findSetter(f.getDeclaringClass(), f.getName(), f.getType());
                } catch (NoSuchFieldException e) {
                    throw new IntrospectionException(e.getMessage());
                } catch (IllegalAccessException e) {
                    try {
                        MethodHandle mh = MethodHandles.lookup().findStatic(Trampoline.class, "setField", MethodType.methodType(void.class, Field.class, Object.class, Object.class));
                        setter = mh.bindTo(f);
                    } catch (NoSuchMethodException | IllegalAccessException ex) {
                        throw new IntrospectionException(ex.getMessage());
                    }
                }
            } else {
                try {
                    setter = MethodHandles.publicLookup().findVirtual(f.getDeclaringClass(), annot.setter(), MethodType.methodType(void.class, f.getType()));
                } catch (NoSuchMethodException | IllegalAccessException ex) {
                    throw new IntrospectionException(ex.getMessage());
                }
            }
        } else {
            if (!annot.setter().isEmpty()) {
                throw new IntrospectionException("Can not specify 'setter' for a non-writable attribute");
            }
        }
        String fldAttrName = f.getName();

        setName(annot.name().isEmpty() ? fldAttrName : annot.name());
    }

    public MethodHandle getGetter() {
        return getter;
    }

    public MethodHandle getSetter() {
        return setter;
    }

    public Class<?> getType() {
        return type;
    }

    public void setGetter(MethodHandle getter) {
        this.getter = getter;
    }

    public void setSetter(MethodHandle setter) {
        this.setter = setter;
    }

    public boolean isReadable() {
        return getter != null &&
               (access == AttributeAccess.READ ||
                access == AttributeAccess.READWRITE);
    }

    public boolean isWritable() {
        return setter != null &&
               (access == AttributeAccess.WRITE ||
                access == AttributeAccess.READWRITE);
    }

    AttributeAccess getAccess() {
        return access;
    }

    void setAccess(AttributeAccess access) {
        this.access = access;
    }

    @Override
    protected void ammendDescriptor(Descriptor d) {
        d.setField("descriptorType", "attribute");
        d.setField("role", "attribute");
    }

    private static boolean isReadable(ManagedAttribute ma) {
        return ma.access() == AttributeAccess.READ ||
               ma.access() == AttributeAccess.READWRITE;
    }

    private static boolean isWritable(ManagedAttribute ma) {
        return ma.access() == AttributeAccess.WRITE ||
               ma.access() == AttributeAccess.READWRITE;
    }

    private void addTags(ManagedAttribute annot) {
        if (!annot.units().isEmpty()) {
            addTag("units", annot.units());
        }
        for (Tag t : annot.tags()) {
            addTag(t.name(), t.value());
        }
    }
}
