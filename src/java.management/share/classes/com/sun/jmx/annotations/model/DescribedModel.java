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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import javax.management.Descriptor;
import javax.management.modelmbean.DescriptorSupport;

/**
 * A base class for all the models providing name, description and tags.
 *
 */
abstract class DescribedModel {
    private String description = "";
    private String name;
    private final Map<String, String> tags = new HashMap<>();
    private Descriptor descriptor;

    protected static final String UNITS_KEY = "units";

    public final String getDescription() {
        return description;
    }

    public final String getName() {
        return name;
    }

    public final void setDescription(String description) {
        this.description = description;
    }

    public final void setName(String name) {
        this.name = name;
    }

    public void addTag(String key, String value) {
        tags.put(key, value);
    }

    public Map<String, String> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    public final Descriptor getDescriptor() {
        Descriptor d = null;
        if (descriptor != null) {
            String[] fldNames = descriptor.getFieldNames();
            d = new DescriptorSupport(
                fldNames,
                descriptor.getFieldValues(fldNames)
            );
        } else {
            d = new DescriptorSupport();
        }
        d.setField("name", name);
        d.setField("displayName", name);
        for(Map.Entry<String, String> e : tags.entrySet()) {
            d.setField(e.getKey(), e.getValue());
        }
        ammendDescriptor(d);
        return d;
    }

    protected void ammendDescriptor(Descriptor d) {}

    public final void setDescriptor(Descriptor descriptor) {
        this.descriptor = descriptor;
    }

    final protected MethodHandle toMethodHandle(Method m) throws IllegalAccessException {
        MethodHandle mh = null;
        try {
            mh = MethodHandles.publicLookup().unreflect(m);
            return mh;
        } catch (IllegalAccessException e) {
            // implementation in non-public class?
            try {
                return unreflectNonPublic(m);
            } catch (NoSuchMethodException ex) {
                // the IllegalAccessException will be rethrown later
            }
            throw e;
        }
    }

    private MethodHandle unreflectNonPublic(Method m) throws NoSuchMethodException, IllegalAccessException {
        Class<?> clz = m.getDeclaringClass();
        Deque<Class<?>> toSearch = new LinkedList<>();
        toSearch.add(clz);
        while ((clz = toSearch.poll()) != null) {
            if (!Modifier.isPublic(clz.getModifiers())) {
                toSearch.addAll(Arrays.asList(clz.getInterfaces()));
                Class<?> supr = clz.getSuperclass();
                if (!supr.equals(Object.class)) {
                    toSearch.add(supr);
                }
            } else {
                break;
            }
        }
        if (clz != null) {
            return MethodHandles.publicLookup().findVirtual(clz, m.getName(), MethodType.methodType(m.getReturnType(), m.getParameterTypes()));
        }

        throw new NoSuchMethodException("Unable to find a public version of method '" + m.getName() + "'");
    }
}
