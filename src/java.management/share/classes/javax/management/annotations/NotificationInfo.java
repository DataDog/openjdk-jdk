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
import javax.management.Notification;

/**
 * This annotation may be used for a field of type {@linkplain NotificationSender}.
 * The annotated field can then be used to emit annotations of the declared types.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotificationInfo {
    /**
     * The actual notification type to be emitted.
     * <p>
     * Defaults to {@linkplain Notification}
     *
     * @return The notification implementation class; default is {@linkplain Notification}
     */
    Class<? extends Notification> implementation() default Notification.class;

    /**
     * A textual description.
     * <p>
     * May be omitted.
     *
     * @return The description; may be empty
     */
    String description() default "";

    /**
     * The notification types to be emitted (see {@linkplain Notification#getType()}}
     * @return The emitted notification types
     */
    String[] types();

    /**
     * Indicates the notification severity. Will be propagated as the "severity" field
     * in the associated {@linkplain Descriptor} instance.
     * @return The notification severity; default is 6
     */
    int severity() default 6;
}
