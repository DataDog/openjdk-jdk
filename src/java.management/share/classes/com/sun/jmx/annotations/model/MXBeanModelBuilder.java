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

import com.sun.jmx.annotations.ModelBuilder;
import com.sun.jmx.mbeanserver.DefaultMXBeanMappingFactory;
import com.sun.jmx.mbeanserver.Introspector;
import com.sun.jmx.mbeanserver.MXBeanMappingFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.management.Descriptor;
import javax.management.ImmutableDescriptor;
import javax.management.IntrospectionException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.annotations.ManagedAttribute;
import javax.management.annotations.ManagedService;
import javax.management.annotations.NotificationInfo;
import javax.management.annotations.NotificationInfos;
import javax.management.annotations.NotificationSender;
import javax.management.annotations.ManagedOperation;
import javax.management.annotations.RegistrationEvent;
import javax.management.annotations.RegistrationHandler;
import javax.management.annotations.Tag;
import javax.management.modelmbean.DescriptorSupport;

/**
 *
 * @author jbachorik
 */
final public class MXBeanModelBuilder implements ModelBuilder {
    @Override
    public <T> MXBeanModel<T> buildModel(Class<?> clz) throws IntrospectionException {
        return toMXBeanModel(clz);
    }

    private <T> MXBeanModel<T> toMXBeanModel(Class<?> clz) throws IntrospectionException {
        ManagedService anno = clz.getAnnotation(ManagedService.class);
        if (anno == null) {
            return null;
        }

        MXBeanModel<T> m = new MXBeanModel<>();

        addAnnotatedFields(clz, m);
        addAnnotatedOperations(clz, m);
        addAnnotatedAttributes(clz, m);
        addAnnotatedRegHandlers(clz, m);

        checkForOverlappingInterfaceMethods(m);

        m.updateAttributeAccess();

        Descriptor d = new DescriptorSupport();
        d.setField("mxbean2", true);

        m.setDescriptor(ImmutableDescriptor.union(d, Introspector.descriptorForElement(clz)));

        apply(clz.getName(), m, anno);

        return m;
    }

    private <T> void checkForOverlappingInterfaceMethods(MXBeanModel<T> m) throws IntrospectionException {
        Set<String> opKeys = getOperationsInterfaceMethods(m);
        Set<String> attrKeys = getAttributeInterfaceMethods(m);

        opKeys.retainAll(attrKeys);
        if (!opKeys.isEmpty()) {
            throw new IntrospectionException("Operation names are overlapping with attribute names");
        }
    }

    private <T> Set<String> getOperationsInterfaceMethods(MXBeanModel<T> m) {
        Set<String> opKeys = m.getOperations().stream()
            .map(om->om.getName() + "#" + om.getSignature())
            .collect(Collectors.toSet());
        return opKeys;
    }

    private <T> Set<String> getAttributeInterfaceMethods(MXBeanModel<T> m) {
        Set<String> attrKeys = m.getAttributes().stream()
            .flatMap(am->Stream.of(
                    am.getGetter() != null && am.getType() != boolean.class &&
                            am.getType() != Boolean.class ?
                            "get" + am.getName() + "#()" : null,
                    am.getGetter() != null && (am.getType() == boolean.class ||
                            am.getType() == Boolean.class) ?
                            "is" + am.getName() + "#()" : null,
                    am.getSetter() != null ?
                            "set" + am.getName() + "#(" + am.getType().getName() + ")" : null))
            .filter(i->i != null)
            .collect(Collectors.toSet());
        return attrKeys;
    }

    private <T> void apply(String className, MXBeanModel<T> m, ManagedService anno) throws IntrospectionException {
        String oName = anno.objectName();
        if (!oName.isEmpty()) {
            try {
                ObjectName on = ObjectName.getInstance(oName);
                m.setObjectName(on);
            } catch (MalformedObjectNameException e) {
            }
        }

        if (!anno.service().equals(Object.class)) {
            validateServiceInterface(anno.service(), m);
            m.setService(anno.service());
        }
        m.setName(className);
        m.setDescription(anno.description());
        for (Tag t : anno.tags()) {
            m.addTag(t.name(), t.value());
        }
        setMappingFactory(m, anno);
    }

    private <T> void validateServiceInterface(Class<?> service, MXBeanModel<T> model) throws IntrospectionException {
        if (service.isInterface()) {
            Set<Method> unknownMethods = new HashSet<>();

            for(Method m : service.getMethods()) {
                String mName = m.getName();
                if (isGetter(m) || isSetter(m)) {
                    String attrName = (mName.startsWith("get") || mName.startsWith("set")) ?
                                        mName.substring(3) :
                                        mName.substring(2);

                    String attrType = mName.startsWith("get") || mName.startsWith("is") ?
                                        m.getReturnType().getName() :
                                        m.getParameterTypes()[0].getName();

                    AttributeModel am = model.getAttribute(attrName);
                    if (am.getType().getName().equals(attrType)) continue;
                }

                if (model.getOperation(m.getName(), getSignature(m)) != null) continue;
                unknownMethods.add(m);
            }
            if (!unknownMethods.isEmpty()) {
                String msg = unknownMethods.stream()
                    .map(m->m.toString()).collect(Collectors.joining(
                    "Managed service does not expose the following methods: ", "\n", "")
                );
                throw new IntrospectionException(msg);
            }
        } else {
            throw new IntrospectionException("Service class must be an 'interface'");
        }
    }

    private static boolean isGetter(Method m) {
        String mName = m.getName();
        return (mName.startsWith("get") || mName.startsWith("is")) &&
                m.getParameterCount() == 0 &&
                m.getReturnType() != void.class;
    }

    private static boolean isSetter(Method m) {
        String mName = m.getName();
        return mName.startsWith("set") && m.getParameterCount() == 1 &&
               m.getReturnType() == void.class;
    }

    private static String getSignature(Method m) {
        return Arrays.asList(m.getParameters()).stream()
            .map(p->p.getType().getName())
            .collect(Collectors.joining(", ", "(", ")"));
    }

    private static <T> void setMappingFactory(MXBeanModel<T> m, ManagedService anno) {
        // TODO: re-enable when modules are properly set up
//        Class<? extends MXBeanMappingFactory> fctryClz = anno.mapping();
//        if (fctryClz.equals(DefaultMXBeanMappingFactory.class)) {
//        m.setMappingFactory(MXBeanMappingFactory.DEFAULT);
//        } else {
//            try {
//                MXBeanMappingFactory fctry = fctryClz.getConstructor().newInstance();
//                m.setMappingFactory(fctry);
//            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
//                     InvocationTargetException e) {
//                m.setMappingFactory(MXBeanMappingFactory.DEFAULT);
//            }
//        }
        m.setMappingFactory(MXBeanMappingFactory.DEFAULT);
    }

    private static <T> void addAnnotatedFields(Class<?> clz, MXBeanModel<T> model) throws IntrospectionException {
        for(Field f : clz.getDeclaredFields()) {
            if (f.isAnnotationPresent(NotificationInfos.class)) {
                if (f.getType().isAssignableFrom(NotificationSender.class)) {
                    model.addInjectableField(new InjectableFieldModel(f));
                    addNotificationInfos(f, model);
                }
            } else if (f.isAnnotationPresent(NotificationInfo.class)) {
                if (f.getType().isAssignableFrom(NotificationSender.class)) {
                    model.addInjectableField(new InjectableFieldModel(f));
                    model.addNotification(new NotificationModel(f.getAnnotation(NotificationInfo.class)));
                }
            }
        }
    }

    private static <T> void addNotificationInfos(AnnotatedElement ae, MXBeanModel<T> model) {
        NotificationInfos ns = ae.getAnnotation(NotificationInfos.class);
        if (ns != null) {
            for (NotificationInfo n : ns.value()) {
                model.addNotification(new NotificationModel(n));
            }
        }
    }

    private static <T> void addAnnotatedOperations(Class<?> clz, MXBeanModel<T> model) throws IntrospectionException {
        for(Method m : clz.getMethods()) {
            if (m.getAnnotation(ManagedOperation.class) != null) {
                OperationModel mom = toOperationModel(m);
                if (mom != null) {
                    model.addOperation(mom);
                }
            }
        }
    }

    private static <T> void addAnnotationsFromParameters(Parameter[] params,
                                                         MXBeanModel<T> model) {
        for (Parameter p : params) {
            if (p.isAnnotationPresent(NotificationInfos.class) ||
                p.isAnnotationPresent(NotificationInfo.class)) {
                NotificationInfos ns = p.getAnnotation(NotificationInfos.class);
                if (ns != null) {
                    for (NotificationInfo n : ns.value()) {
                        model.addNotification(new NotificationModel(n));
                    }
                }
                NotificationInfo n = p.getAnnotation(NotificationInfo.class);
                if (n != null) {
                    model.addNotification(new NotificationModel(n));
                }
            }
        }
    }

    private static <T> void addAnnotatedAttributes(Class<?> clz, MXBeanModel<T> model) throws IntrospectionException {
        for (Field f : clz.getDeclaredFields()) {
            if (f.getAnnotation(ManagedAttribute.class) != null) {
                AttributeModel mam = toAttributeModel(f);
                if (mam != null) {
                    model.addAttribute(mam);
                }
            }
        }

        for (Method m : clz.getMethods()) {
            if (m.getAnnotation(ManagedAttribute.class) != null) {
                AttributeModel mam = toAttributeModel(m);
                if (mam != null) {
                    model.addAttribute(mam);
                    addAnnotationsFromParameters(m.getParameters(), model);
                }
            }
        }
    }

    private static <T> void addAnnotatedRegHandlers(Class<?> clz, MXBeanModel<T> model) throws IntrospectionException {
        for (Method m : clz.getDeclaredMethods()) {
            if (m.getAnnotation(RegistrationHandler.class) != null) {
                checkRegHandler(m);
                try {
                    MethodHandle mh = MethodHandles.publicLookup().unreflect(m);
                    model.addRegistrationHandler(mh);
                } catch (IllegalAccessException e) {
                    throw new IntrospectionException(e.getMessage());
                }
            }
        }
    }

    private static void checkRegHandler(Method m) throws IntrospectionException {
        if (!isRegHandler(m)) {
            throw new IntrospectionException(
                "Invalid registration handler '" + m + "'. Registration handler " +
                "is expected to be public, non-abstract and having signature of " +
                "(javax.management.annotations.RegistrationEvent)void"
            );
        }
    }

    private static boolean isRegHandler(Method m) {
        Class<?>[] types = m.getParameterTypes();
        int mods = m.getModifiers();
        return Modifier.isPublic(mods) && !Modifier.isAbstract(mods) &&
               m.getReturnType() == void.class && types.length == 1 &&
               types[0].equals(RegistrationEvent.class);
    }

    private static OperationModel toOperationModel(Method m) throws IntrospectionException {
        ManagedOperation mo = m.getAnnotation(ManagedOperation.class);

        if (mo == null) {
            return null;
        }

        return new OperationModel(m);
    }

    private static AttributeModel toAttributeModel(Method m) throws IntrospectionException {
        ManagedAttribute ma = m.getAnnotation(ManagedAttribute.class);

        if (ma == null) {
            return null;
        }

        return new AttributeModel(m);
    }

    private static AttributeModel toAttributeModel(Field f) throws IntrospectionException {
        ManagedAttribute ma = f.getAnnotation(ManagedAttribute.class);

        if (ma == null) {
            return null;
        }

        return new AttributeModel(f);
    }
}
