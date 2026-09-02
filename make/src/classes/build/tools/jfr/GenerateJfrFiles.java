/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.jfr;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Predicate;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Purpose of this program is twofold:
 *
 * 1) Generate C++ classes to be used when writing native events for HotSpot.
 *
 * 2) Generate metadata (label, descriptions, field layout etc.) from XML
 * (metadata.xml) into a binary format (metadata.bin) that can be read quickly
 * during startup by the jdk.jfr module.
 *
 * INPUT FILES:
 *
 * -  metadata.xml  File that contains descriptions of events and types
 * -  metadata.xsd  Schema that verifies that metadata.xml is legit XML
 *
 * OUTPUT FILES:
 *
 * MODE: headers
 *
 * - jfrEventIds.hpp      List of IDs so events can be identified from native
 * - jfrTypes.hpp         List of IDs so types can be identified from native
 * - jfrPeriodic.hpp      Dispatch mechanism so Java can emit native periodic events
 * - jfrEventControl.hpp  Data structure for native event settings.
 * - jfrEventClasses.hpp  C++ event classes that can write data into native buffers
 *
 * MODE: metadata
 *
 *  - metadata.bin        Binary representation of the information in metadata.xml
 *
 */
public class GenerateJfrFiles {

    enum OutputMode {
        headers, metadata
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: java GenerateJfrFiles[.java]");
        out.println(" --mode <headers|metadata>");
        out.println(" --xml <path-to-metadata.xml> ");
        out.println(" --xsd <path-to-metadata.xsd>");
        out.println(" --output <output-file-or-directory>");
    }

    private static String consumeOption(String option, List<String> argList) throws Exception {
        int index = argList.indexOf(option);
        if (index >= 0 && index <= argList.size() - 2) {
            String result = argList.get(index + 1);
            argList.remove(index);
            argList.remove(index);
            return result;
        }
        throw new IllegalArgumentException("missing option " + option);
    }

    public static void main(String... args) throws Exception {
        try {
            List<String> argList = new ArrayList<>();
            argList.addAll(Arrays.asList(args));
            String mode = consumeOption("--mode", argList);
            String output = consumeOption("--output", argList);
            String xml = consumeOption("--xml", argList);
            String xsd = consumeOption("--xsd", argList);
            if (!argList.isEmpty()) {
                throw new IllegalArgumentException("unknown option " + argList);
            }
            OutputMode outputMode = OutputMode.valueOf(mode);
            File xmlFile = new File(xml);
            File xsdFile = new File(xsd);

            Metadata metadata = new Metadata(xmlFile, xsdFile);
            metadata.verify();
            metadata.wireUpTypes();

            if (outputMode == OutputMode.headers) {
                File outputDir = new File(output);
                printJfrEventIdsHpp(metadata, new File(outputDir, "jfrEventIds.hpp"));
                printJfrTypesHpp(metadata, new File(outputDir, "jfrTypes.hpp"));
                printJfrPeriodicHpp(metadata, new File(outputDir, "jfrPeriodic.hpp"));
                printJfrEventControlHpp(metadata, new File(outputDir, "jfrEventControl.hpp"));
                printJfrEventClassesHpp(metadata, new File(outputDir, "jfrEventClasses.hpp"));
                printJfrUsdtD(metadata, new File(outputDir, "jfr.d"));
                printJfrUsdtFireHpp(metadata, new File(outputDir, "jfrUsdtFire.hpp"));
            }

            if (outputMode == OutputMode.metadata) {
                File outputFile  = new File(output);
                try (var b = new DataOutputStream(
                        new BufferedOutputStream(
                            new FileOutputStream(outputFile)))) {
                    metadata.persist(b);
                }
            }
            System.exit(0);
        } catch (IllegalArgumentException iae) {
            System.err.println();
            System.err.println("GenerateJfrFiles: " + iae.getMessage());
            System.err.println();
            printUsage(System.err);
            System.err.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(1);
    }

    static class XmlType {
        final String name;
        final String fieldType;
        final String parameterType;
        final String javaType;
        final boolean unsigned;
        final String contentType;

        XmlType(String name, String fieldType, String parameterType, String javaType, String contentType,
                boolean unsigned) {
            this.name = name;
            this.fieldType = fieldType;
            this.parameterType = parameterType;
            this.javaType = javaType;
            this.unsigned = unsigned;
            this.contentType = contentType;
        }
    }

    static class XmlContentType {
        final String name;
        final String annotation;

        XmlContentType(String name, String annotation) {
            this.name = name;
            this.annotation = annotation;
        }
    }

    static class TypeElement {
        List<FieldElement> fields = new ArrayList<>();
        String name;
        String javaType;
        String label = "";
        String description = "";
        String category = "";
        boolean thread;
        boolean stackTrace;
        boolean startTime;
        String period = "";
        boolean cutoff;
        boolean throttle;
        String level = "";
        boolean experimental;
        boolean internal;
        // usdt="true" in metadata.xml
        boolean usdt;
        long id;
        boolean isEvent;
        boolean isRelation;
        boolean supportStruct = false;
        String commitState;
        public boolean primitive;

        public void persist(DataOutputStream pos) throws IOException {
            pos.writeInt(fields.size());
            for (FieldElement field : fields) {
                field.persist(pos);
            }
            pos.writeUTF(javaType);
            pos.writeUTF(label);
            pos.writeUTF(description);
            pos.writeUTF(category);
            pos.writeBoolean(thread);
            pos.writeBoolean(stackTrace);
            pos.writeBoolean(startTime);
            pos.writeUTF(period);
            pos.writeBoolean(cutoff);
            pos.writeBoolean(throttle);
            pos.writeUTF(level);
            pos.writeBoolean(experimental);
            pos.writeBoolean(internal);
            pos.writeLong(id);
            pos.writeBoolean(isEvent);
            pos.writeBoolean(isRelation);
        }
    }

    static class Metadata {
        static class TypeCounter {
            final long first;
            long last = -1;
            long count = 0;
            long id = -1;

            TypeCounter(long startId) {
                this.first = startId;
            }

            long next() {
                id = (id == -1) ? first : id + 1;
                count++;
                last = id;
                return id;
            }
        }

        static int RESERVED_EVENT_COUNT = 2;
        final Map<String, TypeElement> types = new LinkedHashMap<>();
        final Map<String, XmlType> xmlTypes = new LinkedHashMap<>();
        final Map<String, XmlContentType> xmlContentTypes = new LinkedHashMap<>();
        int lastEventId;
        private TypeCounter eventCounter;
        private TypeCounter typeCounter;

        Metadata(File metadataXml, File metadataSchema)
                throws ParserConfigurationException, SAXException, FileNotFoundException, IOException {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setSchema(schemaFactory.newSchema(metadataSchema));
            SAXParser sp = factory.newSAXParser();
            sp.parse(metadataXml, new MetadataHandler(this));
        }

        public void persist(DataOutputStream pos) throws IOException {
            pos.writeInt(types.values().size());
            for (TypeElement t : types.values()) {
                t.persist(pos);
            }
        }

        List<TypeElement> getEvents() {
            return getList(t -> t.isEvent);
        }

        List<TypeElement> getEventsAndStructs() {
            return getList(t -> t.isEvent || t.supportStruct);
        }

        @SuppressWarnings("unchecked")
        <T> List<T> getList(Predicate<? super TypeElement> pred) {
            List<T> result = new ArrayList<>(types.size());
            for (TypeElement t : types.values()) {
                if (pred.test(t)) {
                    result.add((T) t);
                }
            }
            return result;
        }

        List<TypeElement> getPeriodicEvents() {
            return getList(t -> t.isEvent && !t.period.isEmpty());
        }

        List<TypeElement> getTypes() {
            return getList(t -> !t.isEvent);
        }

        List<TypeElement> getStructs() {
            return getList(t -> !t.isEvent && t.supportStruct);
        }

        void verify() {
            for (TypeElement t : types.values()) {
                for (FieldElement f : t.fields) {
                    if (!xmlTypes.containsKey(f.typeName)) { // ignore primitives
                        if (!types.containsKey(f.typeName)) {
                            throw new IllegalStateException("Could not find definition of type '" + f.typeName
                                    + "' used by " + t.name + "#" + f.name);
                        }
                    }
                }
            }
        }

        void wireUpTypes() {
            // Add Java primitives
            for (var t : xmlTypes.entrySet()) {
                String name = t.getKey();
                XmlType xmlType = t.getValue();
                // Excludes Thread and Class
                if (!types.containsKey(name)) {
                    // Excludes u8, u4, u2, u1, Ticks and Ticksspan
                    if (!xmlType.javaType.isEmpty() && !xmlType.unsigned) {
                        TypeElement te = new TypeElement();
                        te.name = name;
                        te.javaType = xmlType.javaType;
                        te.primitive = true;
                        types.put(te.name, te);
                    }
                }
            }
            // Setup Java fully qualified names
            for (TypeElement t : types.values()) {
                if (t.isEvent) {
                    t.javaType = "jdk." + t.name;
                } else {
                    XmlType xmlType = xmlTypes.get(t.name);
                    if (xmlType != null && !xmlType.javaType.isEmpty()) {
                        t.javaType = xmlType.javaType;
                    } else {
                        t.javaType = "jdk.types." + t.name;
                    }
                }
            }
            // Setup content type, annotation, constant pool etc. for fields.
            for (TypeElement t : types.values()) {
                for (FieldElement f : t.fields) {
                    TypeElement type = types.get(f.typeName);
                    XmlType xmlType = xmlTypes.get(f.typeName);
                    if (type == null) {
                        if (xmlType == null) {
                            throw new IllegalStateException("Unknown type");
                        }
                        if (f.contentType.isEmpty()) {
                            f.contentType = xmlType.contentType;
                        }
                        String javaType = xmlType.javaType;
                        type = types.get(javaType);
                        Objects.requireNonNull(type);
                    }
                    if (type.primitive) {
                        f.constantPool = false;
                    }

                    if (xmlType != null) {
                        f.unsigned = xmlType.unsigned;
                    }

                    if (f.struct) {
                        f.constantPool = false;
                        type.supportStruct = true;
                    }
                    f.type = type;
                    XmlContentType xmlContentType = xmlContentTypes.get(f.contentType);
                    if (xmlContentType == null) {
                        f.annotations = "";
                    } else {
                        f.annotations = xmlContentType.annotation;
                    }
                    if (!f.relation.isEmpty()) {
                        f.relation = "jdk.types." + f.relation;
                    }
                }
            }

            // Low numbers for event so most of them
            // can fit in one byte with compressed integers
            eventCounter = new TypeCounter(RESERVED_EVENT_COUNT);
            for (TypeElement t : getEvents()) {
                t.id = eventCounter.next();
            }
            typeCounter = new TypeCounter(eventCounter.last + 1);
            for (TypeElement t : getTypes()) {
                t.id = typeCounter.next();
            }
        }

        public String getName(long id) {
            for (TypeElement t : types.values()) {
                if (t.id == id) {
                    return t.name;
                }
            }
            throw new IllegalStateException("Unexpected id " + id );
        }
    }

    static class FieldElement {
        final Metadata metadata;
        TypeElement type;
        String name;
        String typeName;
        boolean constantPool = true;
        public String transition;
        public String contentType;
        private String label;
        private String description;
        private String relation;
        private boolean experimental;
        private boolean unsigned;
        private boolean array;
        private String annotations;
        public boolean struct;
        // null = unspecified, TRUE = part of the USDT projection,
        // FALSE = explicitly excluded from the USDT projection.
        public Boolean usdt;

        FieldElement(Metadata metadata) {
            this.metadata = metadata;
        }

        public void persist(DataOutputStream pos) throws IOException {
            pos.writeUTF(name);
            pos.writeUTF(type.javaType);
            pos.writeUTF(label);
            pos.writeUTF(description);
            pos.writeBoolean(constantPool);
            pos.writeBoolean(array);
            pos.writeBoolean(unsigned);
            pos.writeUTF(annotations);
            pos.writeUTF(transition);
            pos.writeUTF(relation);
            pos.writeBoolean(experimental);
        }

        String getParameterType() {
            if (struct) {
                return "const JfrStruct" + typeName + "&";
            }
            XmlType xmlType = metadata.xmlTypes.get(typeName);
            if (xmlType != null) {
                return xmlType.parameterType;
            }
            return type != null ? "u8" : typeName;
        }

        String getParameterName() {
            return struct ? "value" : "new_value";
        }

        String getFieldType() {
            if (struct) {
                return "JfrStruct" + typeName;
            }
            XmlType xmlType = metadata.xmlTypes.get(typeName);
            if (xmlType != null) {
                return xmlType.fieldType;
            }
            return type != null ? "u8" : typeName;
        }
    }

    static class MetadataHandler extends DefaultHandler {
        final Metadata metadata;
        FieldElement currentField;
        TypeElement currentType;

        MetadataHandler(Metadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            switch (qName) {
            case "XmlContentType":
                String n = attributes.getValue("name"); // mandatory
                String a = attributes.getValue("annotation"); // mandatory
                metadata.xmlContentTypes.put(n, new XmlContentType(n, a));
                break;
            case "XmlType":
                String name = attributes.getValue("name"); // mandatory
                String parameterType = attributes.getValue("parameterType"); // mandatory
                String fieldType = attributes.getValue("fieldType"); // mandatory
                String javaType = getString(attributes, "javaType");
                String contentType = getString(attributes, "contentType");
                boolean unsigned = getBoolean(attributes, "unsigned", false);
                metadata.xmlTypes.put(name,
                        new XmlType(name, fieldType, parameterType, javaType, contentType, unsigned));
                break;
            case "Relation":
            case "Type":
            case "Event":
                currentType = new TypeElement();
                currentType.name = attributes.getValue("name"); // mandatory
                currentType.label = getString(attributes, "label");
                currentType.description = getString(attributes, "description");
                currentType.category = getString(attributes, "category");
                currentType.experimental = getBoolean(attributes, "experimental", false);
                currentType.internal = getBoolean(attributes, "internal", false);
                currentType.thread = getBoolean(attributes, "thread", false);
                currentType.stackTrace = getBoolean(attributes, "stackTrace", false);
                currentType.startTime = getBoolean(attributes, "startTime", true);
                currentType.period = getString(attributes, "period");
                currentType.cutoff = getBoolean(attributes, "cutoff", false);
                currentType.level = getString(attributes, "level");
                currentType.throttle = getBoolean(attributes, "throttle", false);
                currentType.commitState = getString(attributes, "commitState");
                currentType.usdt = getBoolean(attributes, "usdt", false);
                currentType.isEvent = "Event".equals(qName);
                currentType.isRelation = "Relation".equals(qName);
                break;
            case "Field":
                currentField = new FieldElement(metadata);
                currentField.name = attributes.getValue("name"); // mandatory
                currentField.typeName = attributes.getValue("type"); // mandatory
                currentField.label = getString(attributes, "label");
                currentField.description = getString(attributes, "description");
                currentField.contentType = getString(attributes, "contentType");
                currentField.struct = getBoolean(attributes, "struct", false);
                currentField.array = getBoolean(attributes, "array", false);
                currentField.transition = getString(attributes, "transition");
                currentField.relation = getString(attributes, "relation");
                currentField.experimental = getBoolean(attributes, "experimental", false);
                currentField.usdt = getBooleanObj(attributes, "usdt");
                break;
            }
        }

        private static String getString(Attributes attributes, String name) {
            String value = attributes.getValue(name);
            return value != null ? value : "";
        }

        private static boolean getBoolean(Attributes attributes, String name, boolean defaultValue) {
            String value = attributes.getValue(name);
            return value == null ? defaultValue : Boolean.valueOf(value);
        }

        private static Boolean getBooleanObj(Attributes attributes, String name) {
            String value = attributes.getValue(name);
            return value == null ? null : Boolean.valueOf(value);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (qName) {
            case "Relation":
            case "Type":
            case "Event":
                metadata.types.put(currentType.name, currentType);
                currentType = null;
                break;
            case "Field":
                currentType.fields.add(currentField);
                currentField = null;
                break;
            }
        }
    }

    static class Printer implements Closeable {
        final PrintStream out;

        Printer(File outputFile) throws FileNotFoundException {
            out = new PrintStream(new BufferedOutputStream(new FileOutputStream(outputFile)));
            write("/* AUTOMATICALLY GENERATED FILE - DO NOT EDIT */");
            write("");
        }

        void write(String text) {
            out.print(text);
            out.print("\n"); // Don't use Windows line endings
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    private static void printJfrPeriodicHpp(Metadata metadata, File outputFile) throws Exception {
        try (var out = new Printer(outputFile)) {
            out.write("#ifndef JFRFILES_JFRPERIODICEVENTSET_HPP");
            out.write("#define JFRFILES_JFRPERIODICEVENTSET_HPP");
            out.write("");
            out.write("#include \"utilities/macros.hpp\"");
            out.write("#if INCLUDE_JFR");
            out.write("#include \"jfrfiles/jfrEventIds.hpp\"");
            out.write("#include \"memory/allocation.hpp\"");
            out.write("");
            out.write("enum PeriodicType {BEGIN_CHUNK, INTERVAL, END_CHUNK};");
            out.write("");
            out.write("class JfrPeriodicEventSet : public AllStatic {");
            out.write(" public:");
            out.write("  static void requestEvent(JfrEventId id, jlong timestamp, PeriodicType periodicType) {");
            out.write("    _timestamp = Ticks(timestamp);");
            out.write("    _type = periodicType;");
            out.write("    switch(id) {");
            out.write("  ");
            for (TypeElement e : metadata.getPeriodicEvents()) {
                out.write("      case Jfr" + e.name + "Event:");
                out.write("        request" + e.name + "();");
                out.write("        break;");
                out.write("  ");
            }
            out.write("      default:");
            out.write("        break;");
            out.write("      }");
            out.write("    }");
            out.write("");
            out.write(" private:");
            out.write("");
            for (TypeElement e : metadata.getPeriodicEvents()) {
                out.write("  static void request" + e.name + "(void);");
                out.write("");
            }
            out.write(" static Ticks timestamp(void);");
            out.write(" static Ticks _timestamp;");
            out.write(" static PeriodicType type(void);");
            out.write(" static PeriodicType _type;");
            out.write("};");
            out.write("");
            out.write("#endif // INCLUDE_JFR");
            out.write("#endif // JFRFILES_JFRPERIODICEVENTSET_HPP");
        }
    }

    private static void printJfrEventControlHpp(Metadata metadata, File outputFile) throws Exception {
        try (var out = new Printer(outputFile)) {
            out.write("#ifndef JFRFILES_JFR_NATIVE_EVENTSETTING_HPP");
            out.write("#define JFRFILES_JFR_NATIVE_EVENTSETTING_HPP");
            out.write("");
            out.write("#include \"utilities/macros.hpp\"");
            out.write("#if INCLUDE_JFR");
            out.write("#include \"jfrfiles/jfrEventIds.hpp\"");
            out.write("");
            out.write("/**");
            out.write(" * Event setting. We add some padding so we can use our");
            out.write(" * event IDs as indexes into this.");
            out.write(" */");
            out.write("");
            out.write("struct jfrNativeEventSetting {");
            out.write("  jlong  threshold_ticks;");
            out.write("  jlong  miscellaneous;");
            out.write("  u1     stacktrace;");
            out.write("  u1     enabled;");
            out.write("  u1     large;");
            out.write("  u1     pad[5]; // Because GCC on linux ia32 at least tries to pack this.");
            out.write("};");
            out.write("");
            out.write("union JfrNativeSettings {");
            out.write("  // Array version.");
            out.write("  jfrNativeEventSetting bits[NUMBER_OF_EVENTS + NUMBER_OF_RESERVED_EVENTS];");
            out.write("  // Then, to make it easy to debug,");
            out.write("  // add named struct members also.");
            out.write("  struct {");
            out.write("    jfrNativeEventSetting pad[NUMBER_OF_RESERVED_EVENTS];");
            for (TypeElement t : metadata.getEvents()) {
                out.write("    jfrNativeEventSetting " + t.name + ";");
            }
            out.write("  } ev;");
            out.write("};");
            out.write("");
            out.write("#endif // INCLUDE_JFR");
            out.write("#endif // JFRFILES_JFR_NATIVE_EVENTSETTING_HPP");
        }
    }

    private static void printJfrEventIdsHpp(Metadata metadata, File outputFile) throws Exception {
        try (var out = new Printer(outputFile)) {
            out.write("#ifndef JFRFILES_JFREVENTIDS_HPP");
            out.write("#define JFRFILES_JFREVENTIDS_HPP");
            out.write("");
            out.write("#include \"utilities/macros.hpp\"");
            out.write("#if INCLUDE_JFR");
            out.write("");
            out.write("enum JfrEventId {");
            out.write("  JfrMetadataEvent = 0,");
            out.write("  JfrCheckpointEvent = 1,");
            for (TypeElement t : metadata.getEvents()) {
                out.write("  " + jfrEventId(t.name) + " = " + t.id + ",");
            }
            out.write("};");
            out.write("typedef enum JfrEventId JfrEventId;");
            out.write("");
            String first = metadata.getName(metadata.eventCounter.first);
            String last = metadata.getName(metadata.eventCounter.last);
            out.write("static const JfrEventId FIRST_EVENT_ID = " + jfrEventId(first) + ";");
            out.write("static const JfrEventId LAST_EVENT_ID = " + jfrEventId(last) + ";");
            out.write("static const int NUMBER_OF_EVENTS = " + metadata.eventCounter.count + ";");
            out.write("static const int NUMBER_OF_RESERVED_EVENTS = " + Metadata.RESERVED_EVENT_COUNT + ";");
            out.write("#endif // INCLUDE_JFR");
            out.write("#endif // JFRFILES_JFREVENTIDS_HPP");
        }
    }

    private static String jfrEventId(String name) {
        return "Jfr" + name + "Event";
    }

    private static void printJfrTypesHpp(Metadata metadata, File outputFile) throws Exception {
        try (var out = new Printer(outputFile)) {
            out.write("#ifndef JFRFILES_JFRTYPES_HPP");
            out.write("#define JFRFILES_JFRTYPES_HPP");
            out.write("");
            out.write("#include \"utilities/macros.hpp\"");
            out.write("#if INCLUDE_JFR");
            out.write("");
            out.write("#include <string.h>");
            out.write("#include \"memory/allocation.hpp\"");
            out.write("");
            out.write("enum JfrTypeId {");
            for (TypeElement type : metadata.getTypes()) {
                out.write("  " + jfrTypeId(type.name) + " = " + type.id + ",");
            }
            out.write("};");
            out.write("");
            String first = metadata.getName(metadata.typeCounter.first);
            String last = metadata.getName(metadata.typeCounter.last);
            out.write("static const JfrTypeId FIRST_TYPE_ID = " + jfrTypeId(first) + ";");
            out.write("static const JfrTypeId LAST_TYPE_ID = " + jfrTypeId(last) + ";");
            out.write("");
            out.write("class JfrType : public AllStatic {");
            out.write(" public:");
            out.write("  static jlong name_to_id(const char* type_name) {");

            Map<String, XmlType> javaTypes = new LinkedHashMap<>();
            for (XmlType xmlType : metadata.xmlTypes.values()) {
                if (!xmlType.javaType.isEmpty()) {
                    javaTypes.put(xmlType.javaType, xmlType);
                }
            }
            for (XmlType xmlType : javaTypes.values()) {
                String javaName = xmlType.javaType;
                String typeName = xmlType.name.toUpperCase();
                out.write("    if (strcmp(type_name, \"" + javaName + "\") == 0) {");
                out.write("      return TYPE_" + typeName + ";");
                out.write("    }");
            }
            out.write("    return -1;");
            out.write("  }");
            out.write("};");
            out.write("");
            out.write("#endif // INCLUDE_JFR");
            out.write("#endif // JFRFILES_JFRTYPES_HPP");
        }
    }

    private static String jfrTypeId(String name) {
        return  "TYPE_" + name.toUpperCase();
    }

    private static void printJfrEventClassesHpp(Metadata metadata, File outputFile) throws Exception {
        try (var out = new Printer(outputFile)) {
            out.write("#ifndef JFRFILES_JFREVENTCLASSES_HPP");
            out.write("#define JFRFILES_JFREVENTCLASSES_HPP");
            out.write("");
            out.write("#include \"jfrfiles/jfrTypes.hpp\"");
            out.write("#include \"jfr/utilities/jfrTypes.hpp\"");
            out.write("#include \"oops/klass.hpp\"");
            out.write("#include \"runtime/thread.hpp\"");
            out.write("#include \"utilities/macros.hpp\"");
            out.write("#include \"utilities/ticks.hpp\"");
            out.write("#if INCLUDE_JFR");
            out.write("#include \"jfr/recorder/service/jfrEvent.hpp\"");
            out.write("/*");
            out.write(" * Each event class has an assert member function verify() which is invoked");
            out.write(" * just before the engine writes the event and its fields to the data stream.");
            out.write(" * The purpose of verify() is to ensure that all fields in the event are initialized");
            out.write(" * and set before attempting to commit.");
            out.write(" *");
            out.write(" * We enforce this requirement because events are generally stack allocated and therefore");
            out.write(" * *not* initialized to default values. This prevents us from inadvertently committing");
            out.write(" * uninitialized values to the data stream.");
            out.write(" *");
            out.write(" * The assert message contains both the index (zero based) as well as the name of the field.");
            out.write(" */");
            out.write("");
            printTypes(out, metadata, false);
            printHelpers(out, false);
            out.write("");
            out.write("");
            out.write("#else // !INCLUDE_JFR");
            out.write("");
            out.write("template <typename T>");
            out.write("class JfrEvent {");
            out.write(" public:");
            out.write("  JfrEvent() {}");
            out.write("  void set_starttime(const Ticks&) const {}");
            out.write("  void set_endtime(const Ticks&) const {}");
            out.write("  bool should_commit() const { return false; }");
            out.write("  bool is_started() const { return false; }");
            out.write("  static bool is_enabled() { return false; }");
            out.write("  void commit() {}");
            out.write("};");
            out.write("");
            printTypes(out, metadata, true);
            printHelpers(out, true);
            out.write("");
            out.write("");
            out.write("#endif // INCLUDE_JFR");
            out.write("#endif // JFRFILES_JFREVENTCLASSES_HPP");
        }
    }

    // USDT (DTrace/SystemTap) collector support.
    //
    // Events marked usdt="true" in metadata.xml get a probe in the generated
    // jfr.d provider file plus a usdt_fire_<event>() helper in jfrUsdtFire.hpp,
    // called from the event's production site with raw argument values. Firing
    // is gated only by the probe's is-enabled semaphore (JFR_<PROBE>_ENABLED()):
    // a tracer that attaches flips the semaphore, and unsubscribed probes cost
    // one byte load and a branch. Firing is independent of JFR recording state.

    // A flattened probe argument. Strings cost one slot: not-NUL-terminated
    // sources (Symbol bytes) are copied into a per-thread scratch buffer.
    private record UsdtSlot(String dType, String cppExpr) {}

    // Hard ceiling for flattened probe arguments, matching sys/sdt.h's
    // STAP_PROBE1..12 macro family and every major eBPF consumer (libbpf,
    // parca-dev/usdt, OBI).
    private static final int USDT_MAX_SLOTS = 12;

    private static String usdtFireName(String eventName) {
        return "usdt_fire_" + usdtProbeName(eventName).replace("__", "_");
    }

    private static String usdtProbeName(String eventName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < eventName.length(); i++) {
            char c = eventName.charAt(i);
            boolean boundary = i > 0
                    && ((Character.isUpperCase(c) && i + 1 < eventName.length()
                            && Character.isLowerCase(eventName.charAt(i + 1)))
                        || (Character.isLowerCase(eventName.charAt(i - 1)) && Character.isUpperCase(c)));
            if (boundary) {
                sb.append("__");
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String usdtProbeMacro(String eventName) {
        return "JFR_" + usdtProbeName(eventName).replace("__", "_").toUpperCase();
    }

    private static String dTypeForPrimitive(String fieldType) {
        return switch (fieldType) {
            case "u1", "b1", "bool", "s1", "u2", "s2", "s4" -> "int";
            case "unsigned", "u4" -> "unsigned int";
            case "u8" -> "uint64_t";
            case "s8" -> "int64_t";
            default -> null; // char/float/double not supported
        };
    }

    private static List<UsdtSlot> usdtProjection(Metadata metadata, TypeElement event) {
        List<UsdtSlot> slots = new ArrayList<>();
        for (FieldElement f : event.fields) {
            if (Boolean.FALSE.equals(f.usdt)) {
                continue;
            }
            String m = f.name;
            if ("string".equals(f.typeName)) {
                slots.add(new UsdtSlot("char *", "(" + m + " ? " + m + " : \"\")"));
                continue;
            }
            if ("Tickspan".equals(f.typeName)) {
                slots.add(new UsdtSlot("uint64_t", m + ".value()"));
                continue;
            }
            if ("Ticks".equals(f.typeName)) {
                throw new IllegalStateException("Event " + event.name + " field " + f.name
                        + " of type Ticks has no USDT projection; exclude it with usdt=\"false\"");
            }
            if (f.type != null && f.type.primitive) {
                XmlType xmlType = metadata.xmlTypes.get(f.typeName);
                String dType = dTypeForPrimitive(xmlType.fieldType);
                if (dType == null) {
                    throw new IllegalStateException("Event " + event.name + " field " + f.name
                            + " of primitive type " + f.typeName + " (" + xmlType.fieldType
                            + ") has no USDT mapping");
                }
                slots.add(new UsdtSlot(dType, m));
                continue;
            }
            slots.addAll(projectMarkedType(metadata, event, f, m));
        }
        if (slots.isEmpty()) {
            throw new IllegalStateException("Event " + event.name
                    + " is marked usdt=\"true\" but has no projectable fields");
        }
        if (slots.size() > USDT_MAX_SLOTS) {
            throw new IllegalStateException("Event " + event.name
                    + " USDT projection needs " + slots.size()
                    + " probe arguments, exceeding the " + USDT_MAX_SLOTS + " slot ceiling");
        }
        return slots;
    }

    // Project an event field of declared type through that type's
    // usdt="true" sub-fields.
    private static List<UsdtSlot> projectMarkedType(Metadata metadata, TypeElement event, FieldElement f, String m) {
        List<UsdtSlot> slots = new ArrayList<>();
        switch (f.typeName) {
            case "Thread" -> {
                requireMarked(metadata, event, f, "Thread", "osThreadId");
                slots.add(new UsdtSlot("long", "JfrUsdtSupport::os_thread_id(" + m + ")"));
            }
            case "Class" -> {
                requireMarked(metadata, event, f, "Class", "name");
                requireMarked(metadata, event, f, "Symbol", "string");
                // Symbol bytes are not NUL-terminated; copy_string copies
                // them (NUL-terminated, length-capped) into a per-thread
                // scratch buffer so every string arg is a plain char*.
                slots.add(new UsdtSlot("char *", "(" + m + " && " + m + "->name()) ? JfrUsdtSupport::copy_string("
                        + m + "->name()->bytes(), (size_t)" + m + "->name()->utf8_length()) : \"\""));
            }
            case "GCName" -> {
                requireMarked(metadata, event, f, "GCName", "name");
                slots.add(new UsdtSlot("char *", "GCNameHelper::to_string((GCName)" + m + ")"));
            }
            case "GCCause" -> {
                requireMarked(metadata, event, f, "GCCause", "cause");
                slots.add(new UsdtSlot("char *", "GCCause::to_string((GCCause::Cause)" + m + ")"));
            }
            default -> throw new IllegalStateException("Event " + event.name + " field " + f.name
                    + " of type " + f.typeName + " has no viable USDT projection; "
                    + "mark the type's sub-fields with usdt=\"true\", "
                    + "or exclude the field with usdt=\"false\"");
        }
        return slots;
    }

    private static void requireMarked(Metadata metadata, TypeElement event, FieldElement field,
            String typeName, String subFieldName) {
        TypeElement t = metadata.types.get(typeName);
        if (t == null) {
            throw new IllegalStateException("Undefined projection type " + typeName);
        }
        for (FieldElement sf : t.fields) {
            if (sf.name.equals(subFieldName)) {
                if (!Boolean.TRUE.equals(sf.usdt)) {
                    throw new IllegalStateException("Event " + event.name + " field " + field.name
                            + " projects through " + typeName + "." + subFieldName
                            + ", which is not marked usdt=\"true\" in metadata.xml");
                }
                return;
            }
        }
        throw new IllegalStateException(typeName + " has no field " + subFieldName);
    }

    private static void printJfrUsdtFireHpp(Metadata metadata, File outputFile) throws Exception {
        // Compute all projections up front so validation errors surface
        // before any file is written.
        Map<String, List<UsdtSlot>> projections = new LinkedHashMap<>();
        for (TypeElement e : metadata.getEvents()) {
            if (e.usdt) {
                projections.put(e.name, usdtProjection(metadata, e));
            }
        }
        try (var out = new Printer(outputFile)) {
            out.write("#ifndef JFRFILES_JFRUSDTFIRE_HPP");
            out.write("#define JFRFILES_JFRUSDTFIRE_HPP");
            out.write("");
            out.write("#include \"gc/shared/gcCause.hpp\"");
            out.write("#include \"gc/shared/gcName.hpp\"");
            out.write("#include \"jfr/support/jfrUsdtSupport.hpp\"");
            out.write("#include \"jfr/utilities/jfrTypes.hpp\"");
            out.write("#include \"oops/klass.hpp\"");
            out.write("#include \"oops/symbol.hpp\"");
            out.write("#include \"utilities/ticks.hpp\"");
            out.write("");
            out.write("#if defined(LINUX) && defined(DTRACE_ENABLED)");
            out.write("// The is-enabled semaphore macros below come from the dtrace -h");
            out.write("// generated jfr.h; its _SDT_HAS_SEMAPHORES prolog is what makes them");
            out.write("// (and the probe notes' semaphore addresses) exist. The prolog flips");
            out.write("// EVERY provider in the TU at sys/sdt.h include time, so this header");
            out.write("// must never be included from a TU that also uses hotspot provider");
            out.write("// probes (dtrace.hpp includes sys/sdt.h without the prolog, and the");
            out.write("// jfr probes would silently lose their semaphores).");
            out.write("#ifdef _SYS_SDT_H");
            out.write("#error \"jfrUsdtFire.hpp must be included before anything that includes <sys/sdt.h>\"");
            out.write("#endif");
            out.write("#include \"dtracefiles/jfr.h\"");
            out.write("#endif");
            out.write("");
            for (var entry : projections.entrySet()) {
                TypeElement event = metadata.types.get(entry.getKey());
                String macro = usdtProbeMacro(entry.getKey());
                List<UsdtSlot> slots = entry.getValue();
                StringJoiner params = new StringJoiner(", ");
                for (FieldElement f : event.fields) {
                    if (Boolean.FALSE.equals(f.usdt)) {
                        continue;
                    }
                    params.add(f.getParameterType() + " " + f.name);
                }
                out.write("inline void " + usdtFireName(event.name) + "(" + params + ") {");
                out.write("#if defined(LINUX) && defined(DTRACE_ENABLED)");
                out.write("  if (" + macro + "_ENABLED()) {");
                StringJoiner args = new StringJoiner(", ");
                for (UsdtSlot slot : slots) {
                    args.add(slot.cppExpr());
                }
                out.write("    " + macro + "(" + args + ");");
                out.write("  }");
                out.write("#endif");
                out.write("}");
                out.write("");
            }
            out.write("#endif // JFRFILES_JFRUSDTFIRE_HPP");
        }
    }

    private static void printJfrUsdtD(Metadata metadata, File outputFile) throws Exception {
        // Compute all projections up front so validation errors surface
        // before any file is written.
        Map<String, List<UsdtSlot>> projections = new LinkedHashMap<>();
        for (TypeElement e : metadata.getEvents()) {
            if (e.usdt) {
                projections.put(e.name, usdtProjection(metadata, e));
            }
        }
        try (var out = new Printer(outputFile)) {
            out.write("provider jfr {");
            for (var entry : projections.entrySet()) {
                String probe = usdtProbeName(entry.getKey());
                StringJoiner args = new StringJoiner(", ");
                List<UsdtSlot> slots = entry.getValue();
                for (int i = 0; i < slots.size(); i++) {
                    args.add(slots.get(i).dType() + " arg" + i);
                }
                out.write("  probe " + probe + "(" + args + ");");
            }
            out.write("};");
            out.write("");
            out.write("#pragma D attributes Evolving/Evolving/Common provider jfr provider");
            out.write("#pragma D attributes Private/Private/Unknown provider jfr module");
            out.write("#pragma D attributes Private/Private/Unknown provider jfr function");
            out.write("#pragma D attributes Evolving/Evolving/Common provider jfr name");
            out.write("#pragma D attributes Evolving/Evolving/Common provider jfr args");
        }
    }

    private static void printHelpers(Printer out, boolean empty) {
        out.write("template <typename EventType>");
        out.write("class JfrNonReentrant : public EventType {");
        if (!empty) {
            out.write(" private:");
            out.write("  Thread* const _thread;");
            out.write("  int32_t _previous_nesting;");
        }
        out.write(" public:");
        out.write("  JfrNonReentrant(EventStartTime timing = TIMED)");
        if (empty) {
            out.write("  {}");
        } else {
            out.write("    : EventType(timing), _thread(Thread::current()), _previous_nesting(JfrThreadLocal::make_non_reentrant(_thread)) {}");
            out.write("");
            out.write("  JfrNonReentrant(Thread* thread, EventStartTime timing = TIMED)");
            out.write("    : EventType(timing), _thread(thread), _previous_nesting(JfrThreadLocal::make_non_reentrant(_thread)) {}");
        }
        if (!empty) {
          out.write("");
          out.write("  ~JfrNonReentrant() {");
          out.write("    if (_previous_nesting != -1) {");
          out.write("      JfrThreadLocal::make_reentrant(_thread, _previous_nesting);");
          out.write("    }");
          out.write("  }");
        }
        out.write("}; ");
    }

    private static void printTypes(Printer out, Metadata metadata, boolean empty) {
        for (TypeElement t : metadata.getStructs()) {
            printType(out, t, empty);
            out.write("");
        }
        for (TypeElement e : metadata.getEvents()) {
            printEvent(out, e, empty);
            out.write("");
        }
    }

    private static void printType(Printer out, TypeElement t, boolean empty) {
        out.write("struct JfrStruct" + t.name);
        out.write("{");
        if (!empty) {
            out.write(" private:");
            for (FieldElement f : t.fields) {
                printField(out, f);
            }
            out.write("");
        }
        out.write(" public:");
        for (FieldElement f : t.fields) {
            printTypeSetter(out, f, empty);
        }
        out.write("");
        if (!empty) {
            printWriteData(out, t);
        }
        out.write("};");
        out.write("");
    }

    private static void printEvent(Printer out, TypeElement event, boolean empty) {
        out.write("class Event" + event.name + " : public JfrEvent<Event" + event.name + ">");
        out.write("{");
        if (!empty) {
            out.write(" private:");
            for (FieldElement f : event.fields) {
                printField(out, f);
            }
            out.write("");
        }
        out.write(" public:");
        if (!empty) {
            out.write("  static const bool hasThread = " + event.thread + ";");
            out.write("  static const bool hasStackTrace = " + event.stackTrace + ";");
            out.write("  static const bool isInstant = " + !event.startTime + ";");
            out.write("  static const bool hasCutoff = " + event.cutoff + ";");
            out.write("  static const bool hasThrottle = " + event.throttle + ";");
            out.write("  static const bool isRequestable = " + !event.period.isEmpty() + ";");
            out.write("  static const JfrEventId eventId = Jfr" + event.name + "Event;");
            out.write("");
        }
        if (!empty) {
            out.write("  Event" + event.name + "(EventStartTime timing=TIMED) : JfrEvent<Event" + event.name
                    + ">(timing) {}");
        } else {
            out.write("  Event" + event.name + "(EventStartTime timing=TIMED) {}");
        }
        out.write("");
        int index = 0;
        for (FieldElement f : event.fields) {
            out.write("  void set_" + f.name + "(" + f.getParameterType() + " " + f.getParameterName() + ") {");
            if (!empty) {
                out.write("    this->_" + f.name + " = " + f.getParameterName() + ";");
                out.write("    DEBUG_ONLY(set_field_bit(" + index++ + "));");
            }
            out.write("  }");
        }
        out.write("");
        if (!empty) {
            printWriteData(out, event);
            out.write("");
        }
        out.write("  using JfrEvent<Event" + event.name
                + ">::commit; // else commit() is hidden by overloaded versions in this class");
        if (!event.fields.isEmpty()) {
            printConstructor2(out, event, empty);
            printCommitMethod(out, event, empty);
        }
        if (!empty) {
            printVerify(out, event.fields);
        }
        out.write("};");
    }

    private static void printWriteData(Printer out, TypeElement type) {
        out.write("  template <typename Writer>");
        out.write("  void writeData(Writer& w) {");
        if (type.isEvent && type.internal) {
            out.write("    JfrEventSetting::unhide_internal_types();");
        }
        for (FieldElement field : type.fields) {
            if (field.struct) {
                out.write("    _" + field.name + ".writeData(w);");
            } else {
                out.write("    w.write(_" + field.name + ");");
            }
        }
        out.write("  }");
    }

    private static void printTypeSetter(Printer out, FieldElement field, boolean empty) {
        if (!empty) {
            out.write("  void set_" + field.name + "(" + field.getParameterType() + " new_value) { this->_" + field.name
                    + " = new_value; }");
        } else {
            out.write("  void set_" + field.name + "(" + field.getParameterType() + " new_value) { }");
        }
    }

    private static void printVerify(Printer out, List<FieldElement> fields) {
        out.write("");
        out.write("#ifdef ASSERT");
        out.write("  void verify() const {");
        int index = 0;
        for (FieldElement f : fields) {
            out.write("    assert(verify_field_bit(" + index++
                    + "), \"Attempting to write an uninitialized event field: %s\", \"_" + f.name + "\");");
        }
        out.write("  }");
        out.write("#endif");
    }

    private static void printCommitMethod(Printer out, TypeElement event, boolean empty) {
        if (event.startTime) {
            StringJoiner sj = new StringJoiner(",\n              ");
            for (FieldElement f : event.fields) {
                sj.add(f.getParameterType() + " " + f.name);
            }
            out.write("");
            out.write("  void commit(" + sj.toString() + ") {");
            if (!empty) {
                out.write("    if (should_commit()) {");
                for (FieldElement f : event.fields) {
                    out.write("      set_" + f.name + "(" + f.name + ");");
                }
                out.write("      commit();");
                out.write("    }");
            }
            out.write("  }");
        }

        // Avoid clash with static commit() method
        if (event.fields.isEmpty()) {
            return;
        }

        out.write("");
        StringJoiner sj = new StringJoiner(",\n                     ");
        if (event.startTime) {
            sj.add("const Ticks& startTicks");
            sj.add("const Ticks& endTicks");
        }
        for (FieldElement f : event.fields) {
            sj.add(f.getParameterType() + " " + f.name);
        }
        out.write("  static void commit(" + sj.toString() + ") {");
        if (!empty) {
            out.write("    Event" + event.name + " me(UNTIMED);");
            out.write("");
            out.write("    if (me.should_commit()) {");
            if (event.startTime) {
                out.write("      me.set_starttime(startTicks);");
                out.write("      me.set_endtime(endTicks);");
            }
            for (FieldElement f : event.fields) {
                out.write("      me.set_" + f.name + "(" + f.name + ");");
            }
            out.write("      me.commit();");
            out.write("    }");
        }
        out.write("  }");
    }

    private static void printConstructor2(Printer out, TypeElement event, boolean empty) {
        if (!event.startTime) {
            out.write("");
            out.write("");
        }
        if (event.startTime) {
            out.write("");
            out.write("  Event" + event.name + "(");
            StringJoiner sj = new StringJoiner(",\n    ");
            for (FieldElement f : event.fields) {
                sj.add(f.getParameterType() + " " + f.name);
            }
            if (!empty) {
                out.write("    " + sj.toString() + ") : JfrEvent<Event" + event.name + ">(TIMED) {");
                out.write("    if (should_commit()) {");
                for (FieldElement f : event.fields) {
                    out.write("      set_" + f.name + "(" + f.name + ");");
                }
                out.write("    }");
            } else {
                out.write("    " + sj.toString() + ") {");
            }
            out.write("  }");
        }
    }

    private static void printField(Printer out, FieldElement field) {
        out.write("  " + field.getFieldType() + " _" + field.name + ";");
    }
}
