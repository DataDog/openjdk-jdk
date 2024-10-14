/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Datadog, Inc. All rights reserved.
 * Copyright (c) 2024, SAP SE. All rights reserved.
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

package jdk.jfr.internal.settings;

import static jdk.jfr.internal.util.TimespanUnit.SECONDS;
import static jdk.jfr.internal.util.TimespanUnit.MILLISECONDS;

import java.util.Objects;
import java.util.Set;

import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.MetadataDefinition;
import jdk.jfr.Name;
import jdk.jfr.internal.PlatformEventType;
import jdk.jfr.internal.Rate;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.util.ParsedRateOrPeriod;
import jdk.jfr.internal.util.TimespanUnit;
import jdk.jfr.internal.util.Utils;

@MetadataDefinition
@Label("Throttle")
@Description("Upper bounds the emission rate for an event")
@Name(Type.SETTINGS_PREFIX + "Rate")
public final class RateSetting extends JDKSettingControl {
    public static final String DEFAULT_VALUE = Rate.DEFAULT;
    private final PlatformEventType eventType;
    private String value = DEFAULT_VALUE;

    public RateSetting(PlatformEventType eventType) {
       this.eventType = Objects.requireNonNull(eventType);
    }

    @Override
    public String combine(Set<String> values) {
        ParsedRateOrPeriod max = null;
        boolean autoadapt = false;
        for (String value : values) {
            ParsedRateOrPeriod rate = ParsedRateOrPeriod.of(value);
            if (rate != null) {
                if (max == null || rate.isHigher(max)) {
                    max = rate;
                }
                autoadapt |= rate instanceof ParsedRateOrPeriod.ParsedRate;
            }
        }
        if (autoadapt) {
            max = max.toParsedRate();
        }
        // "off" is not supported
        return Objects.requireNonNullElse(max.toString(), DEFAULT_VALUE);
    }

    @Override
    public void setValue(String value) {
        ParsedRateOrPeriod rate = ParsedRateOrPeriod.of(value);
        if (rate != null) {
            eventType.setRate(rate.rate(), rate instanceof ParsedRateOrPeriod.ParsedRate);
        }
    }

    @Override
    public String getValue() {
        return value;
    }
}

