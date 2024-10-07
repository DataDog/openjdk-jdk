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

import java.util.concurrent.atomic.AtomicReference;
import javax.management.IntrospectionException;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.annotations.RegistrationEvent;
import javax.management.annotations.RegistrationKind;

/**
 * A specific subclass of {@linkplain AnnotatedMBean} to handle the
 * registration events.
 */
class RegisteringAnnotatedMBean<T> extends AnnotatedMBean<T> implements MBeanRegistration {
    private MBeanServer server;
    private ObjectName oName;

    public RegisteringAnnotatedMBean(MXBeanModel<T> model, T instance) throws IntrospectionException {
        super(model, instance);
    }

    @Override
    final public void preRegister2(MBeanServer mbs, ObjectName name) throws Exception {
        dispatchEvent(new RegistrationEvent(RegistrationKind.REGISTER, mbs, name));
    }

    @Override
    final public void registerFailed() {
        try {
            dispatchEvent(new RegistrationEvent(RegistrationKind.FAIL, server, oName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    final public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        if (name == null && model.getObjectName() == null) {
            throw new MBeanRegistrationException(null, "Undefined object name");
        }

        name = name != null ? name : model.getObjectName();

        if (instance instanceof MBeanRegistration)
            name = ((MBeanRegistration) instance).preRegister(server, name);

        this.server = server;
        this.oName = name;

        return name;
    }

    @Override
    final public void postRegister(Boolean registrationDone) {
        if (instance instanceof MBeanRegistration)
            ((MBeanRegistration) instance).postRegister(registrationDone);
    }

    @Override
    final public void preDeregister() throws Exception {
        if (instance instanceof MBeanRegistration)
            ((MBeanRegistration) instance).preDeregister();
    }

    @Override
    final public void postDeregister() {
        if (instance instanceof MBeanRegistration) {
            ((MBeanRegistration) instance).postDeregister();
        }

        try {
            dispatchEvent(new RegistrationEvent(RegistrationKind.DEREGISTER, server, oName));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            this.oName = null;
            this.server = null;
        }
    }

    private void dispatchEvent(RegistrationEvent e) throws Exception {
        AtomicReference<Exception> ex = new AtomicReference<>();
        model.getRegistrationHandlers()
            .forEach(h->{
                try {
                    h = h.bindTo(instance);
                    if (h.type().parameterCount() == 0) {
                        h.invoke();
                    } else {
                        h.invokeWithArguments(e);
                    }
                } catch (Throwable t) {
                    ex.set(new Exception(t));
                }
            });

        if (ex.get() != null) {
            throw ex.get();
        }
    }
}
