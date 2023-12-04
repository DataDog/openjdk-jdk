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

import javax.management.MBeanOperationInfo;

/**
 * Operation impact enumeration
 */
public enum Impact {
    /**
     * Indicates that the operation is read-like:
     * it returns information but does not change any state.
     */
    INFO(MBeanOperationInfo.INFO),
    /**
     * Indicates that the operation is write-like: it has an effect but does
     * not return any information from the MBean.
     */
    ACTION(MBeanOperationInfo.ACTION),
    /**
     * Indicates that the operation is both read-like and write-like:
     * it has an effect, and it also returns information from the MBean.
     */
    ACTION_INFO(MBeanOperationInfo.ACTION_INFO),
    /**
     * Indicates that the impact of the operation is unknown or cannot be
     * expressed using one of the other values.
     */
    UNKNOWN(MBeanOperationInfo.UNKNOWN);

    private int n;

    Impact(int n) {
        this.n = n;
    }

    /**
     * Will convert one of the following values
     * <ul>
     * <li>{@linkplain MBeanOperationInfo#ACTION}</li>
     * <li>{@linkplain MBeanOperationInfo#ACTION_INFO}</li>
     * <li>{@linkplain MBeanOperationInfo#INFO}</li>
     * <li>{@linkplain MBeanOperationInfo#UNKNOWN}</li>
     * </ul>
     * into the corresponding {@linkplain Impact} value
     * @param val A numeric representation of the impact. See {@linkplain MBeanOperationInfo}.
     * @return An {@linkplain Impact} instance corresponding to the numeric impact.
     */
    public static Impact fromInt(int val) {
        switch (val) {
            case MBeanOperationInfo.ACTION: return ACTION;
            case MBeanOperationInfo.INFO: return INFO;
            case MBeanOperationInfo.ACTION_INFO: return ACTION_INFO;
            default: return UNKNOWN;
        }
    }
}
