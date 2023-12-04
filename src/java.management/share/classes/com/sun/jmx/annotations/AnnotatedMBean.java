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

import com.sun.jmx.annotations.model.AttributeModel;
import com.sun.jmx.annotations.model.MXBeanModel;
import com.sun.jmx.annotations.model.OperationModel;
import com.sun.jmx.annotations.model.InjectableFieldModel;
import com.sun.jmx.mbeanserver.DynamicMBean2;
import com.sun.jmx.mbeanserver.MXBeanMapping;
import com.sun.jmx.mbeanserver.MXBeanMappingFactory;

import java.io.InvalidObjectException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ReflectionException;
import javax.management.annotations.NotificationSender;
import javax.management.annotations.NotificationInfos;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;


/**
 * A wrapper for the annotated MBeans. For the purposes of cooperation with
 * the rest of the JMX system the annotated MBean will behave like a
 * {@linkplain DynamicMBean2} and {@linkplain NotificationEmitter} instance.
 *
 */
abstract class AnnotatedMBean<T> implements DynamicMBean2, NotificationEmitter, NotificationSender {
    protected final MXBeanModel<T> model;
    protected final T instance;
    private NotificationBroadcasterSupport nbs = null;
    private final MBeanInfoBuilder mib;

    protected AnnotatedMBean(MXBeanModel<T> model, T instance) throws IntrospectionException {
        this.model = model;
        this.instance = instance;
        this.mib = new MBeanInfoBuilder(model.getMappingFactory());
        injectFields();
    }

    private void injectFields() throws IntrospectionException {
        for(InjectableFieldModel fm : model.getInjectableFields()) {
            if (fm.getType().isAssignableFrom(NotificationSender.class)) {
                // inject the NotificationSender instances
                fm.inject(instance, this);
            }
        }
    }

    private synchronized NotificationBroadcasterSupport getNbs() {
        if (nbs == null) {
            List<ModelMBeanNotificationInfo> ntfs = model.getNotifications().stream()
                                                        .map(mib::toNotificationInfo).collect(Collectors.toList());
            nbs = new NotificationBroadcasterSupport(ntfs.toArray(new ModelMBeanNotificationInfo[ntfs.size()]));
        }
        return nbs;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public String getClassName() {
        return model.getName();
    }

    @Override
    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        try {
            AttributeModel mam = model.getAttribute(attribute);
            if (mam == null) {
                throw new AttributeNotFoundException("Can not find attribute '" + attribute + "'");
            }
            MXBeanMapping m = getMapping(mam.getType());
            MethodHandle h = mam.getGetter().bindTo(instance);

            Object[] args = new Object[h.type().parameterCount()];
            Class<?>[] paramTypes = h.type().parameterArray();
            for(int i=0;i<args.length;i++) {
                if (NotificationSender.class.isAssignableFrom(paramTypes[i])) {
                    args[i] = this;
                }
            }
            Object val = args.length > 0 ? h.invokeWithArguments(args) : h.invoke();

            return m != null ? m.toOpenValue(val) : val;
        } catch (Throwable t) {
            throw new MBeanException((Exception) t);
        }
    }

    @Override
    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        try {
            AttributeModel mam = model.getAttribute(attribute.getName());
            if (mam == null) {
                throw new AttributeNotFoundException();
            }
            MXBeanMapping m = getMapping(mam.getType());
            MethodHandle h = mam.getSetter().bindTo(instance);
            Object[] args = new Object[h.type().parameterCount()];
            Class<?>[] paramTypes = h.type().parameterArray();
            for(int i=0;i<args.length;i++) {
                if (paramTypes[i].equals(NotificationSender.class)) {
                    h = h.bindTo(this);
                } else {
                    h = h.bindTo(m != null ? m.fromOpenValue(attribute.getValue()) : attribute.getValue());
                }
            }
            h.invoke();
        } catch (Throwable t) {
            throw new MBeanException((Exception) t);
        }
    }

    @Override
    public AttributeList getAttributes(String[] attributes) {
        AttributeList al = new AttributeList(attributes.length);
        for(String aName : attributes) {
            try {
                Object val = getAttribute(aName);
                al.add(new Attribute(aName, val));
            } catch (AttributeNotFoundException | MBeanException | ReflectionException ex) {
                // just ignore; method signature does not allow for exceptions
            }
        }
        return al;
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes) {
        AttributeList setAttrs = new AttributeList(attributes.size());
        for (Attribute a : attributes.asList()) {
            try {
                setAttribute(a);
                setAttrs.add(a);
            } catch (AttributeNotFoundException | MBeanException |
                     ReflectionException | InvalidAttributeValueException ex) {
                // just ignore; method signature does not allow for exceptions
            }
        }
        return setAttrs;
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        unwrapCompositeTypes(params, signature);

        String type = Arrays.asList(signature).stream().collect(Collectors.joining(", ", "(", ")"));
        try {
            OperationModel mom = model.getOperation(actionName, type);
            if (mom == null) {
                throw new MBeanException(null, "Unknown operation: " + actionName);
            }
            MXBeanMapping m = getMapping(mom.getType());

            MethodHandle mh = mom.getMethodHandle().bindTo(instance);

            java.lang.reflect.Parameter[] mParams = mom.getMethod().getParameters();
            Object[] transParams = new Object[mom.getMethod().getParameterCount()];

            for(int pIndex = 0, tIndex = 0;tIndex<transParams.length;tIndex++) {
                if (NotificationSender.class.isAssignableFrom(mParams[tIndex].getType()) && (
                        mParams[tIndex].isAnnotationPresent(NotificationInfos.class) ||
                        mParams[tIndex].isAnnotationPresent(javax.management.annotations.NotificationInfo.class))) {
                    transParams[tIndex] = this;
                } else {
                    java.lang.reflect.Parameter p = mParams[tIndex];
                    MXBeanMapping map = getMapping(p.getType());
                    Object val = params[pIndex++];
                    transParams[tIndex] = map != null ? map.fromOpenValue(val) : val;
                }
            }

            Object val = mh.invokeWithArguments(transParams);
            return m != null ? m.toOpenValue(val) : val;
        } catch (OpenDataException | InvalidObjectException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new MBeanException(e);
        } catch (Throwable t) {
            throw new MBeanException(new InvocationTargetException(t));
        }
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        return mib.toMBeanInfo(model);
    }

    @Override
    final public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        getNbs().addNotificationListener(listener, filter, handback);
    }

    @Override
    final public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
        getNbs().removeNotificationListener(listener);
    }

    @Override
    final public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws ListenerNotFoundException {
        getNbs().removeNotificationListener(listener, filter, handback);
    }

    @Override
    final public MBeanNotificationInfo[] getNotificationInfo() {
        return getNbs().getNotificationInfo();
    }

    @Override
    final public void sendNotification(Notification notification) {
        getNbs().sendNotification(notification);
    }

    private final AtomicLong notifCounter = new AtomicLong();

    @Override
    public void sendNotification(String type, String message, Object userData) {
        Notification n = new Notification(type, this, notifCounter.getAndIncrement(), message);
        n.setUserData(userData);
        nbs.sendNotification(n);
    }

    private MXBeanMapping getMapping(Class<?> type) throws OpenDataException {
        return model.getMappingFactory().mappingForType(type, MXBeanMappingFactory.DEFAULT);
    }

    private void unwrapCompositeTypes(Object[] params, String[] signature) {
        if (params != null) {
            for(int i=0;i<params.length;i++) {
                if (signature[i].equals(CompositeData.class.getName())) {
                    CompositeType ct = ((CompositeData)params[i]).getCompositeType();
                    signature[i] = ct.getTypeName();
                }
            }
        }
    }
}
