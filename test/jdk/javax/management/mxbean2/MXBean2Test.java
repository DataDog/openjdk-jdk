/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import com.sun.jmx.mbeanserver.MXBeanMapping;
import com.sun.jmx.mbeanserver.MXBeanMappingFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.management.Descriptor;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;
import javax.management.modelmbean.DescriptorSupport;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.SimpleType;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;

/**
 * @test
 * @bug 8044507
 * @summary Test for the annotation defined MXBeans
 * @modules java.management/com.sun.jmx.mbeanserver
 * @build MXBean2Instance MXBeanService ComplexType
 * @run testng MXBean2Test
 */
public class MXBean2Test {
    private static final String INTERFACE_CLASS_NAME_KEY = "interfaceClassName";
    private static ModelMBeanAttributeInfo COUNTER_INFO, STATUS_INFO,
                                                 LABEL_INFO, ENABLED_INFO,
                                                 FUZY_INFO, CTYPE_INFO;
    private static final Set<ModelMBeanAttributeInfo> ALL_ATTRS = new HashSet<>();
    private static final Set<ModelMBeanOperationInfo> ALL_OPS = new HashSet<>();
    private static final Set<MBeanNotificationInfo> ALL_NOTIFS = new HashSet<>();

    private MBeanServer mbs;
    private MXBean2Instance instance;
    private ObjectName oName, oName1;

    static {
        setupAttributes();
        setupOperations();
        setupNotifications();
    }

    private static void setupAttributes() throws RuntimeOperationsException, RuntimeException {
        Descriptor d = new DescriptorSupport();
        d.setField("name", "counter");
        d.setField("displayName", "counter");
        d.setField("descriptorType", "attribute");
        d.setField("units", "ticks");
        d.setField("originalType", "int");
        d.setField("openType", SimpleType.INTEGER);
        d.setField("role", "attribute");
        COUNTER_INFO = new ModelMBeanAttributeInfo(
                "counter", "int", "", true, false, false, d
        );
        ALL_ATTRS.add(COUNTER_INFO);

        d = new DescriptorSupport();
        d.setField("name", "Status");
        d.setField("displayName", "Status");
        d.setField("descriptorType", "attribute");
        d.setField("originalType", "java.lang.String");
        d.setField("openType", SimpleType.STRING);
        d.setField("role", "attribute");
        STATUS_INFO = new ModelMBeanAttributeInfo(
                "Status", "java.lang.String", "Combined service status", true, false, false, d
        );
        ALL_ATTRS.add(STATUS_INFO);

        d = new DescriptorSupport();
        d.setField("name", "Label");
        d.setField("displayName", "Label");
        d.setField("descriptorType", "attribute");
        d.setField("originalType", "java.lang.String");
        d.setField("openType", SimpleType.STRING);
        d.setField("role", "attribute");
        LABEL_INFO = new ModelMBeanAttributeInfo(
                "Label", "java.lang.String", "", true, true, false, d
        );
        ALL_ATTRS.add(LABEL_INFO);

        d = new DescriptorSupport();
        d.setField("name", "enabled");
        d.setField("displayName", "enabled");
        d.setField("descriptorType", "attribute");
        d.setField("originalType", "boolean");
        d.setField("openType", SimpleType.BOOLEAN);
        d.setField("role", "attribute");
        ENABLED_INFO = new ModelMBeanAttributeInfo(
                "enabled", "boolean", "", true, true, true, d
        );
        ALL_ATTRS.add(ENABLED_INFO);

        try {
            d = new DescriptorSupport();
            d.setField("name", "fuzy");
            d.setField("displayName", "fuzy");
            d.setField("descriptorType", "attribute");
            d.setField("originalType", "[Ljava.lang.String;");
            d.setField("openType", ArrayType.getArrayType(SimpleType.STRING));
            d.setField("role", "attribute");
            FUZY_INFO = new ModelMBeanAttributeInfo(
                    "fuzy", "[Ljava.lang.String;", "", true, false, false, d
            );
            ALL_ATTRS.add(FUZY_INFO);
        } catch (RuntimeOperationsException |OpenDataException ex) {
            throw new RuntimeException(ex);
        }

        try {
            MXBeanMapping mm = MXBeanMappingFactory.DEFAULT.mappingForType(ComplexType.class, MXBeanMappingFactory.DEFAULT);
            d = new DescriptorSupport();
            d.setField("name", "cType");
            d.setField("displayName", "cType");
            d.setField("descriptorType", "attribute");
            d.setField("originalType", "ComplexType");
            d.setField("openType", mm.getOpenType());
            d.setField("role", "attribute");
            CTYPE_INFO = new ModelMBeanAttributeInfo(
                    "cType", "javax.management.openmbean.CompositeData", "", true, true, false, d
            );
            ALL_ATTRS.add(CTYPE_INFO);
        } catch (OpenDataException | RuntimeOperationsException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setupOperations() throws RuntimeOperationsException, RuntimeException {
        Descriptor d = new DescriptorSupport();
        d.setField("name", "complex");
        d.setField("displayName", "complex");
        MBeanParameterInfo[] paramInfos = new MBeanParameterInfo[] {
            new MBeanParameterInfo("complex", "javax.management.openmbean.CompositeData", "", d)
        };

        d = new DescriptorSupport();
        d.setField("name", "printComplex");
        d.setField("displayName", "printComplex");
        d.setField("descriptorType", "operation");
        d.setField("role", "operation");
        ModelMBeanOperationInfo printComplexInfo = new ModelMBeanOperationInfo(
            "printComplex", "", paramInfos, "void", MBeanOperationInfo.UNKNOWN, d
        );
        ALL_OPS.add(printComplexInfo);

        d = new DescriptorSupport();
        d.setField("name", "arg0");
        d.setField("displayName", "arg0");
        d.setField("units", "ms");
        paramInfos = new MBeanParameterInfo[] {
            new MBeanParameterInfo("arg0", "long", "", d)
        };

        d = new DescriptorSupport();
        d.setField("name", "checkTime");
        d.setField("displayName", "checkTime");
        d.setField("descriptorType", "operation");
        d.setField("role", "operation");
        ModelMBeanOperationInfo checkTimeInfo = new ModelMBeanOperationInfo(
            "checkTime", "", paramInfos, "void", MBeanOperationInfo.ACTION_INFO, d
        );
        ALL_OPS.add(checkTimeInfo);

        d = new DescriptorSupport();
        d.setField("name", "count");
        d.setField("displayName", "count");
        d.setField("descriptorType", "operation");
        d.setField("role", "operation");
        ModelMBeanOperationInfo countInfo = new ModelMBeanOperationInfo(
            "count", "Increases the associated counter by 1", new MBeanParameterInfo[0], "int", MBeanOperationInfo.ACTION, d
        );
        ALL_OPS.add(countInfo);
    }

    private static void setupNotifications() {
        Descriptor d = new DescriptorSupport();
        d.setField("name", "javax.management.Notification");
        d.setField("displayName", "javax.management.Notification");
        d.setField("descriptorType", "notification");
        d.setField("severity", 6);
        MBeanNotificationInfo methodNotifInfo = new MBeanNotificationInfo(
            new String[]{"test.mbean.label"}, "javax.management.Notification", "Label was set", d
        );
        ALL_NOTIFS.add(methodNotifInfo);

        d = new DescriptorSupport();
        d.setField("name", "CustomNotification");
        d.setField("displayName", "CustomNotification");
        d.setField("descriptorType", "notification");
        d.setField("severity", 1);
        MBeanNotificationInfo classNotifInfo = new MBeanNotificationInfo(
            new String[]{"com.oracle.testing.notification"}, "CustomNotification", "Custom notification", d
        );
        ALL_NOTIFS.add(classNotifInfo);

        d = new DescriptorSupport();
        d.setField("name", "javax.management.Notification");
        d.setField("displayName", "javax.management.Notification");
        d.setField("descriptorType", "notification");
        d.setField("severity", 6);
        MBeanNotificationInfo thresholdNotifInfo = new MBeanNotificationInfo(
            new String[]{"test.mbean.threshold"}, "javax.management.Notification", "Counter threshold reached", d
        );
        ALL_NOTIFS.add(thresholdNotifInfo);
    }

    @BeforeMethod
    public void setup() throws Exception {
        mbs = MBeanServerFactory.createMBeanServer();
        instance = new MXBean2Instance();
        oName = ObjectName.getInstance("com.oracle.testing:type=mxbean2");
        oName1 = ObjectName.getInstance("com.oracle.testing:type=default");
    }

    @AfterMethod
    public void teardown() {
        try {
            mbs.unregisterMBean(oName);
        } catch (Exception e) {}
        try {
            mbs.unregisterMBean(oName1);
        } catch (Exception e) {}
    }

    @Test
    public void testRegistration() throws Exception {
        mbs.registerMBean(instance, oName);
    }

    @Test
    public void testCreation() throws Exception {
        mbs.createMBean(MXBean2Instance.class.getName(), oName);
    }

    @Test
    public void testCreationDefaultOname() throws Exception {
        mbs.createMBean(MXBean2Instance.class.getName(), null);
    }

    @Test
    public void testMBeanInfo() throws Exception {
        mbs.registerMBean(instance, oName);
        MBeanInfo mbInfo = mbs.getMBeanInfo(oName);

        validateMBeanInfo(mbInfo);
    }

    private static void validateMBeanInfo(MBeanInfo info) {
        // check class name
        Assert.assertEquals("MXBean2Instance", info.getClassName());
        Descriptor d = info.getDescriptor();

        Assert.assertEquals("MXBeanService", d.getFieldValue(INTERFACE_CLASS_NAME_KEY));
        Assert.assertEquals("xyz", d.getFieldValue("customInfo"));

        MBeanAttributeInfo[] attrInfos = info.getAttributes();
        Set<MBeanAttributeInfo> attrInfoSet = new HashSet<>(Arrays.asList(attrInfos));

        Assert.assertTrue(attrInfoSet.containsAll(ALL_ATTRS));
        Assert.assertTrue(ALL_ATTRS.containsAll(attrInfoSet));

        MBeanOperationInfo[] opInfos = info.getOperations();
        Set<MBeanOperationInfo> opInfoSet = new HashSet<>(Arrays.asList(opInfos));

        Assert.assertTrue(opInfoSet.containsAll(ALL_OPS));
        Assert.assertTrue(ALL_OPS.containsAll(opInfoSet));

        MBeanNotificationInfo[] notifInfos = info.getNotifications();
        Set<MBeanNotificationInfo> notifInfoSet = new HashSet<>(Arrays.asList(notifInfos));

        Assert.assertTrue(notifInfoSet.containsAll(ALL_NOTIFS));
        Assert.assertTrue(ALL_NOTIFS.containsAll(notifInfoSet));

        System.err.println(notifInfoSet);
    }
}
