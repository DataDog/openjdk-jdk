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
import java.lang.reflect.Method;
import java.util.stream.Collectors;
import javax.management.Descriptor;
import javax.management.IntrospectionException;
import javax.management.annotations.ManagedOperation;
import javax.management.annotations.Tag;

/**
 * An MBean operation specific model
 *
 */
public final class OperationModel extends ParametrizedModel {
    private MethodHandle methodHandle;
    final private Method method;
    final private String impact;

    public OperationModel(Method m) throws IntrospectionException {
        super(m.getParameters());

        ManagedOperation mo = m.getAnnotation(ManagedOperation.class);
        if (mo == null) {
            throw new IntrospectionException("Can not build operation model for a non-annotated method");
        }

        try {
            setName(mo.name().isEmpty() ? m.getName() : mo.name());
            setDescription(mo.description());
            if (!mo.units().isEmpty()) {
                addTag(UNITS_KEY, mo.units());
            }
            for (Tag t : mo.tags()) {
                addTag(t.name(), t.value());
            }

            method = m;
            methodHandle = toMethodHandle(m);
            impact = mo.impact().toString();

            setDescriptor(Introspector.descriptorForElement(m));
        } catch (IllegalAccessException e) {
            throw new IntrospectionException(e.getMessage());
        }
    }

    public Method getMethod() {
        return method;
    }

    public MethodHandle getMethodHandle() {
        return methodHandle;
    }

    public void setMethodHandle(MethodHandle methodHandle) {
        this.methodHandle = methodHandle;
    }

    public String getImpact() {
        return impact;
    }

    public Class<?> getType() {
        return method.getReturnType();
    }

    public String getSignature() {
        return getParameters().stream()
                .map(p->p.getType().getName())
                .collect(Collectors.joining(", ", "(", ")"));
    }

    @Override
    protected void ammendDescriptor(Descriptor d) {
        d.setField("descriptorType", "operation");
        d.setField("role", "operation");
    }

}
