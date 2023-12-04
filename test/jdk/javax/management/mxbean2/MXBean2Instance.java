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

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.management.annotations.AttributeAccess;
import javax.management.annotations.Impact;
import javax.management.annotations.ManagedAttribute;
import javax.management.annotations.ManagedOperation;
import javax.management.annotations.ManagedService;
import javax.management.annotations.NotificationInfo;
import javax.management.annotations.NotificationSender;
import javax.management.annotations.ParameterInfo;
import javax.management.annotations.RegistrationEvent;
import javax.management.annotations.RegistrationHandler;
import javax.management.annotations.RegistrationKind;
import javax.management.annotations.Tag;

@ManagedService(
    description = "Testing MXBean instance",
    objectName = "com.oracle.testing:type=default",
    service = MXBeanService.class,
    tags = @Tag(name = "customInfo", value = "xyz")
)
public class MXBean2Instance {
    @NotificationInfo(types = "test.mbean.label", description = "Label was set")
    NotificationSender ns1;

    @NotificationInfo(
        implementation = CustomNotification.class,
        description = "Custom notification",
        types = "com.oracle.testing.notification",
        severity = 1)
    NotificationSender ns2;

    @NotificationInfo(types = "test.mbean.threshold", description = "Counter threshold reached")
    NotificationSender ns3;

    final AtomicBoolean registered = new AtomicBoolean();

    @ManagedAttribute(access = AttributeAccess.READ, units = "ticks")
    int counter = 1;

    @ManagedAttribute(access = AttributeAccess.READ, name = "Label")
    private String label = "hello world";

    @ManagedAttribute(access = AttributeAccess.READWRITE)
    boolean enabled;

    @ManagedAttribute(access = AttributeAccess.READWRITE)
    ComplexType cType = new ComplexType("string value");

    @ManagedAttribute(access = AttributeAccess.READ)
    String[] fuzy = new String[]{"sa", "ba", "ca"};

    @ManagedOperation(
        impact = Impact.ACTION,
        description = "Increases the associated counter by 1"
    )
    public int count() {
        if (counter >= 5) {
            ns3.sendNotification("test.mbean.threshold", "Threshold reached", counter);
        }
        return ++counter;
    }

    @ManagedOperation(impact = Impact.ACTION_INFO)
    public void checkTime(@ParameterInfo(units = "ms") long ts) {
        System.err.println(new Date(ts));
    }

    @ManagedOperation
    public void printComplex(@ParameterInfo(name = "complex") ComplexType cx) {
        System.err.println(cx.toString());
    }

    @ManagedAttribute(description = "Combined service status")
    public String getStatus() {
        return label + " > " + counter;
    }

    @ManagedAttribute
    public void setLabel(String l) {
        ns2.sendNotification("test.mbean.label", "Label set", l);
        label = l;
    }

    @RegistrationHandler
    public void onRegistration(RegistrationEvent e) {
        if (e.getKind() == RegistrationKind.REGISTER) {
            registered.set(true);
            System.err.println("Registered " + e.getObjectName().getCanonicalName());
        } else if (e.getKind() == RegistrationKind.DEREGISTER) {
            System.err.println("Unregistered " + e.getObjectName().getCanonicalName());
            registered.set(false);
        }
    }
}