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
 * <p>Denotes a field or a method to be exposed as an MBean attribute.</p>
 *
 *
 *
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedAttribute {

    /**
     * The name the attribute is to be known by.
     * <p>
     * May be omitted. In that case the name is inferred from the field name
     * or method name (following JavaBeans rules)
     *
     * @return The attribute name; may be empty
     */
    String name() default "";

    /**
     * A textual description.
     * <p>
     * May be omitted.
     *
     * @return The attribute description; may be empty
     */
    String description() default "";

    /**
     * Units the value of this attribute is measured in.
     * <p>
     * May be omitted.
     *
     * @return The attribute units; may be empty
     */
    String units() default "";

    /**
     * The {@link AttributeAccess} policy.
     * <p>
     * Defaults to {@linkplain AttributeAccess#READWRITE}
     *
     * @return The attribute access ({@linkplain AttributeAccess})
     */
    AttributeAccess access() default AttributeAccess.READWRITE;

    /**
     * The name of the custom getter method.
     * <p>
     * Specifying the custom getter will suppress generating the artificial
     * field getter method and will use the specified one instead.
     * </p>
     * <p>
     * The custom getter method must be a publicly accessible method of
     * the attribute holder class. The method signature must conform to the
     * JavaBeans getter rules.
     * </p>
     * {$code
     * @ManagedAttribute(getter = "getNameExt")
     * private String name;
     * ...
     * public String getNameExt() {
     *   return name + "ext";
     * }
     * }
     * @return The attribute getter method; may be empty
     */
    String getter() default "";

    /**
     * The name of the custom setter method.
     * <p>
     * Specifying the custom setter will suppress generating the artificial
     * field setter method and will use the specified one instead.
     * </p>
     * <p>
     * The custom setter method must be a publicly accessible method of
     * the attribute holder class. The method signature must conform to the
     * JavaBeans setter rules.
     * </p>
     * {$code
     * @ManagedAttribute(setter = "setNameExt")
     * private String name;
     * ...
     * public void setNameExt(String val) {
     *   fireNotification();
     *   name = val;
     * }
     * }
     * @return The attribute setter method; may be empty
     */
    String setter() default "";

    /**
     * Custom tags attached to the attribute.
     * <p>
     * A tag is basically a /key, value/ pair. Tags will be represented as fields
     * in the associated {@linkplain Descriptor}
     *
     * @return The attached tags
     */
    Tag[] tags() default {};
}
