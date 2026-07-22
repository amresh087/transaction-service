package com.retail.transaction.edi.model;

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

/**
 * Represents an EDI segment with its name and fields.
 * A segment is a logical unit of data within an EDI document.
 */
public class EdiSegment {
    @JacksonXmlProperty(isAttribute = true)
    private final String name;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "field")
    private final List<String> fields;

    public EdiSegment(String name, List<String> fields) {
        this.name = name;
        this.fields = fields;
    }

    public String getName() {
        return name;
    }

    public List<String> getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return "EdiSegment{" +
                "name='" + name + '\'' +
                ", fields=" + fields +
                '}';
    }
}
