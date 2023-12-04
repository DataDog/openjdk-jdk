/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.jmx.annotations;

import com.sun.jmx.annotations.model.MXBeanModel;

import javax.management.IntrospectionException;

/**
 *
 * @author jbachorik
 */
public interface ModelBuilder {
    <T> MXBeanModel<T> buildModel(Class<?> clz) throws IntrospectionException;
}
