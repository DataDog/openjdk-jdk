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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.management.Notification;
import javax.management.annotations.NotificationInfo;

/**
 * An MBean notification specific model.
 *
 */
public final class NotificationModel {
    private final Class<? extends Notification> clazz;
    private final Set<String> types = new HashSet<>();
    private final String description;
    private final int severity;

    public NotificationModel(NotificationInfo n) {
        clazz = n.implementation();
        description = n.description();
        types.addAll(Arrays.asList(n.types()));
        severity = n.severity();
    }

    public Class<? extends Notification> getClazz() {
        return clazz;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public Set<String> getTypes() {
        return Collections.unmodifiableSet(types);
    }

    public int getSeverity() {
        return severity;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.clazz);
        hash = 29 * hash + Objects.hashCode(this.types);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NotificationModel other = (NotificationModel) obj;
        if (!Objects.equals(this.clazz, other.clazz)) {
            return false;
        }
        if (!Objects.equals(this.types, other.types)) {
            return false;
        }
        return true;
    }


}
