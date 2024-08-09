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
import com.sun.jmx.annotations.model.NotificationModel;
import com.sun.jmx.annotations.model.OperationModel;
import com.sun.jmx.annotations.model.ParameterModel;
import com.sun.jmx.mbeanserver.MXBeanMapping;
import com.sun.jmx.mbeanserver.MXBeanMappingFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.management.Descriptor;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.modelmbean.DescriptorSupport;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanConstructorInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;
import javax.management.openmbean.OpenDataException;

public final class MBeanInfoBuilder {
    private final MXBeanMappingFactory mappingFactory;

    MBeanInfoBuilder(MXBeanMappingFactory mf) {
        this.mappingFactory = mf;
    }

    public MBeanInfoBuilder() {
        this.mappingFactory = MXBeanMappingFactory.DEFAULT;
    }

    public <T> MBeanInfo toMBeanInfo(MXBeanModel<T> model) {
        String className = model.getName();
        List<ModelMBeanAttributeInfo> attrs = model.getAttributes().stream()
                                                .map(this::toAttrInfo).collect(Collectors.toList());
        List<ModelMBeanOperationInfo> ops = model.getOperations().stream()
                                                .map(this::toOperationInfo).collect(Collectors.toList());
        List<ModelMBeanNotificationInfo> ntfs = model.getNotifications().stream()
                                                .map(this::toNotificationInfo).collect(Collectors.toList());

        Descriptor d = new DescriptorSupport();
        d.setField("name", className);
        d.setField("descriptorType", "mbean");
        d.setField("immutableInfo", true);
        if (model.getService() != null) {
            d.setField("interfaceClassName", model.getService().getName());
        }
        model.getTags().entrySet().stream().forEach(e->{
            d.setField(e.getKey(), e.getValue());
        });

        return new ModelMBeanInfoSupport(
            className,
            model.getDescription(),
            attrs.toArray(new ModelMBeanAttributeInfo[attrs.size()]),
            new ModelMBeanConstructorInfo[0],
            ops.toArray(new ModelMBeanOperationInfo[ops.size()]),
            ntfs.toArray(new ModelMBeanNotificationInfo[ntfs.size()]),
            d
        );
    }

    public ModelMBeanNotificationInfo toNotificationInfo(NotificationModel mnm) {
        List<String> types = new ArrayList<>(mnm.getTypes());
        String name = mnm.getClazz().getName();
        Descriptor d = new DescriptorSupport();
        d.setField("name", name);
        d.setField("displayName", name);
        d.setField("descriptorType", "notification");
        d.setField("severity", mnm.getSeverity());
        return new ModelMBeanNotificationInfo(types.toArray(new String[types.size()]), name, mnm.getDescription(), d);
    }

    public ModelMBeanAttributeInfo toAttrInfo(AttributeModel mam) {
        MXBeanMapping mapping = getMapping(mam.getType());
        String type = mapType(mam.getType());
        Descriptor d = mam.getDescriptor();
        if (mapping != null) {
            d.setField("openType", mapping.getOpenType());
            d.setField("originalType", mam.getType().getName());
        }

        boolean isIs = mam.getType().getName().equals("boolean") || mam.getType().equals(Boolean.class);
        return new ModelMBeanAttributeInfo(mam.getName(), type, mam.getDescription(), mam.isReadable(), mam.isWritable(), isIs, d);
    }

    public ModelMBeanOperationInfo toOperationInfo(OperationModel mom) {
        int impact = MBeanOperationInfo.UNKNOWN;
        switch (mom.getImpact()) {
            case "INFO": {
                impact = MBeanOperationInfo.INFO;
                break;
            }
            case "ACTION_INFO": {
                impact = MBeanOperationInfo.ACTION_INFO;
                break;
            }
            case "ACTION": {
                impact = MBeanOperationInfo.ACTION;
                break;
            }
        }

        String type = mapType(mom.getType());
        return new ModelMBeanOperationInfo(mom.getName(), mom.getDescription(), toParamInfos(mom), type, impact, mom.getDescriptor());
    }

    private MBeanParameterInfo[] toParamInfos(OperationModel mom) {
        return toParamInfos(mom.getParameters());
    }

    private MBeanParameterInfo[] toParamInfos(List<ParameterModel> params) {
        List<MBeanParameterInfo> infos = params.stream()
                .map(this::toParamInfo)
                .collect(Collectors.toList());
        return infos.toArray(new MBeanParameterInfo[infos.size()]);
    }

    private MBeanParameterInfo toParamInfo(ParameterModel param) {
        String name = param.getName();
        String type = mapType(param.getType());
        String description = param.getDescription();

        return new MBeanParameterInfo(name, type, description, param.getDescriptor());

    }

    private String mapType(Class<?> type) {
        MXBeanMapping m = getMapping(type);
        return m != null ? m.getOpenClass().getName() : type.getName();
    }

    private MXBeanMapping getMapping(Class<?> type) {
        try {
            return mappingFactory.mappingForType(type, MXBeanMappingFactory.DEFAULT);
        } catch (OpenDataException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}