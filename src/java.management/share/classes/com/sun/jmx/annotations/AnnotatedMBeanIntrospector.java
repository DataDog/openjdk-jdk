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

package com.sun.jmx.annotations;

import com.sun.jmx.annotations.model.MXBeanModel;

import java.security.Permission;
import java.util.Iterator;
import java.util.ServiceLoader;
import javax.management.DynamicMBean;
import javax.management.IntrospectionException;

/**
 * This introspector will turn an annotated MBean class into a compliant
 * {@linkplain DynamicMBean} implementation.
 */
public class AnnotatedMBeanIntrospector {
    private static final Permission CSJAM_ACCESS = new RuntimePermission("accessClassInPackage.com.sun.jmx.annotations.model");

    private volatile static ModelBuilder builder = null;

    /**
     * Takes an instance and tries to convert it to a {@linkplain DynamicMBean} implementation
     * according to any present {@link javax.management.annotations} annotations.
     *
     * @param <T> The type of the given instance
     * @param instance The instance
     * @return An {@linkplain  DynamicMBean} wrapper or null when the provided instance
     *         is not properly annotated.
     *
     * @throws IntrospectionException
     */
    public static <T> DynamicMBean toMBean(T instance) throws IntrospectionException {
        ModelBuilder mb = getBuilder();
        if (mb != null) {
            MXBeanModel<T> m = mb.buildModel(instance.getClass());
            if (m != null) {
                return new RegisteringAnnotatedMBean<>(m, instance);
            }
            return null;
        }
        throw new IntrospectionException("An appropriate MXBean Model Builder can not be located.");
    }

    private static ModelBuilder getBuilder() {
        if (builder == null) {
            ServiceLoader<ModelBuilder> l = ServiceLoader.load(ModelBuilder.class);
            Iterator<ModelBuilder> svcIter = l.iterator();
            if (svcIter.hasNext()) {
                builder = svcIter.next();
            }
        }
        return builder;
    }
}
