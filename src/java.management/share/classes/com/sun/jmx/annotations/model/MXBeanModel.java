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

import com.sun.jmx.mbeanserver.MXBeanMappingFactory;
import java.lang.invoke.MethodHandle;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.management.IntrospectionException;
import javax.management.ObjectName;
import javax.management.annotations.AttributeAccess;

/**
 * A model representing the annotated MBean class
 *
 * @param <T> The MBean implementation class
 */
final public class MXBeanModel<T> extends DescribedModel {
    private ObjectName oName;
    private Class<?> svc;
    private MXBeanMappingFactory mappingFactory;

    private final Map<String, OperationModel> operations = new HashMap<>();
    private final Map<String, AttributeModel> attributes = new HashMap<>();
    private final Set<NotificationModel> notifications = new HashSet<>();
    private final Set<InjectableFieldModel> injectableFields = new HashSet<>();
    private final Set<MethodHandle> registrationHandlers = new HashSet<>();

    public ObjectName getObjectName() {
        return oName;
    }

    public void setObjectName(ObjectName oName) {
        this.oName = oName;
    }

    public Class<?> getService() {
        return svc;
    }

    public void setService(Class<?> svc) {
        this.svc = svc;
    }

    public Collection<OperationModel> getOperations() {
        return Collections.unmodifiableCollection(operations.values());
    }

    public void addOperation(OperationModel m) throws IntrospectionException {
        String key = m.getName() + ":" + m.getSignature();
        if (operations.containsKey(key)) {
            throw new IntrospectionException(
                "Attempting to redefine operation '" + m.getName() + "' " +
                "already defined by '" + m.getMethod() + "'");
        }
        operations.put(key, m);
    }

    public OperationModel getOperation(String name, String type) {
        OperationModel m = operations.get(name + ":" + type);

        return m;
    }

    public Collection<AttributeModel> getAttributes() {
        return Collections.unmodifiableCollection(attributes.values());
    }

    public void addAttribute(AttributeModel m) throws IntrospectionException {
        String aName = m.getName();
        AttributeModel m1 = attributes.get(aName);
        if (m1 != null) {
            if (m1.getGetter() != null && m.getGetter() != null) {
                throw new IntrospectionException(
                        "Attempting to redefine attribute '" + aName + "' getter " +
                        "already defined by '" + m1.getGetter() + "'");
            }
            if (m1.getSetter() != null && m.getSetter() != null) {
                throw new IntrospectionException(
                        "Attempting to redefine attribute '" + aName + "' setter " +
                        "already defined by '" + m1.getSetter() + "'");
            }
            if (m1.getGetter() == null) {
                m1.setGetter(m.getGetter());
            }

            if (m1.getSetter() == null) {
                m1.setSetter(m.getSetter());
            }
        } else {
            attributes.put(aName, m);
        }
    }

    public AttributeModel getAttribute(String name) {
        AttributeModel m = attributes.get(name);

        return m;
    }

    public Collection<NotificationModel> getNotifications() {
        return Collections.unmodifiableCollection(notifications);
    }

    public void addNotification(NotificationModel n) {
        notifications.add(n);
    }

    public void addInjectableField(InjectableFieldModel inf) {
        injectableFields.add(inf);
    }

    public Collection<InjectableFieldModel> getInjectableFields() {
        return Collections.unmodifiableCollection(injectableFields);
    }

    public MXBeanMappingFactory getMappingFactory() {
        return mappingFactory;
    }

    public void addRegistrationHandler(MethodHandle handler) {
        registrationHandlers.add(handler);
    }

    public Collection<MethodHandle> getRegistrationHandlers() {
        return Collections.unmodifiableCollection(registrationHandlers);
    }

    public void setMappingFactory(MXBeanMappingFactory mappingFactory) {
        this.mappingFactory = mappingFactory;
    }

    public boolean isRegistrationHandler() {
        return !registrationHandlers.isEmpty();
    }

    void updateAttributeAccess() throws IntrospectionException {
        for (AttributeModel am : getAttributes()) {
            switch (am.getAccess()) {
                case READ: {
                    if (am.getGetter() == null) {
                        throw new IntrospectionException(
                            "Attribute '" + getName() + "' specifies READ access " +
                            "but does not provide the getter."
                        );
                    }
                    if (am.getSetter() != null) {
                        am.setAccess(AttributeAccess.READWRITE);
                    }
                    break;
                }
                case WRITE: {
                    if (am.getSetter() == null) {
                        throw new IntrospectionException(
                            "Attribute '" + getName() + "' specifies WRITE access " +
                            "but does not provide the setter."
                        );
                    }
                    if (am.getGetter() != null) {
                        am.setAccess(AttributeAccess.READWRITE);
                    }
                    break;
                }
                case READWRITE: {
                    if (am.getGetter() != null || am.getSetter() != null) {
                        if (am.getGetter() == null && am.getSetter() != null) {
                            am.setAccess(AttributeAccess.WRITE);
                        }
                        if (am.getSetter() == null && am.getGetter() != null) {
                            am.setAccess(AttributeAccess.READ);
                        }
                    } else {
                        throw new IntrospectionException(
                            "Attribute '" + getName() + "' specifies READWRITE access " +
                            "but does not provide neither getter or setter");
                    }
                }
            }
        }
    }
}