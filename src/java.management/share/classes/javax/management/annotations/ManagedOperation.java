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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.management.Descriptor;

/**
 * <p>Denotes a method to be exposed as an MBean operation.</p>
 *
 *
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedOperation {

    /**
     * The name the operation is to be known by.
     * <p>
     * May be omitted. In that case the name is inferred from the method name
     *
     * @return The operation name; may be empty
     */
    String name() default "";

    /**
     * A textual description.
     * <p>
     * May be omitted.
     *
     * @return The description; may be empty
     */
    String description() default "";

    /**
     * Units the result of this operation is measured in.
     * <p>
     * May be omitted.
     *
     * @return The operation result units; may be empty
     */
    String units() default "";

    /**
     * The operation {@linkplain Impact}.
     * <p>
     * The default is {@linkplain Impact#UNKNOWN}
     *
     * @return The operation {@linkplain Impact}; defaults to {@linkplain Impact#UNKNOWN}
     */
    Impact impact() default Impact.UNKNOWN;

    /**
     * Custom tags attached to the operation.
     * <p>
     * A tag is basically a /key, value/ pair. Tags will be represented as fields
     * in the associated {@linkplain Descriptor}
     *
     * @return The attached tags
     */
    Tag[] tags() default {};
}
