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

package javax.management.annotations;

import com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory;
import com.sun.jmx.mbeanserver.MXBeanMappingFactory;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.management.ObjectName;

/**
 * Marks a class as an MXBean implementation.
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedService {
    /**
     * A textual description.
     * <p>
     * May be omitted.
     *
     * @return The description; may be empty
     */
    String description() default "";

    /**
     * The default {@linkplain ObjectName} to be used when registering the MBean.
     * <p>
     * May be omitted.
     *
     * @return The default {@linkplain ObjectName}; may be empty
     */
    String objectName() default "";

    /**
     * Custom tags attached to the operation.
     * <p>
     * A tag is basically a /key, value/ pair. Tags will be represented as fields
     * in the associated {@linkplain javax.management.Descriptor}
     *
     * @return The attached tags
     */
    Tag[] tags() default {};

    /**
     * The service interface this MBean is intended to be accessed through.
     * <p>
     * May be omitted.
     *
     * @return The service interface
     */
    Class<?> service() default Object.class;

    /**
     * A custom {@linkplain MXBeanMappingFactory} implementation.
     * <p>
     * May be omitted.
     *
     * @return The associated {@linkplain MXBeanMappingFactory} implementation
     */
    // TODO fix the modular boundaries
    // Class<? extends MXBeanMappingFactory> mapping() default DefaultMXBeanMappingFactory.class;
}
