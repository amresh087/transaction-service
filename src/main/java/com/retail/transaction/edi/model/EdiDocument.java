package com.retail.transaction.edi.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Represents the root structure of an EDI document.
 * Contains a collection of EDI segments.
 */
@JacksonXmlRootElement(localName = "edi")
public class EdiDocument {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "segment")
    private final List<EdiSegment> segments = new ArrayList<>();

    public EdiDocument() {
    }

    public List<EdiSegment> getSegments() {
        return segments;
    }

    /**
     * Adds a segment to this document.
     *
     * @param segment the segment to add
     */
    public void addSegment(EdiSegment segment) {
        segments.add(segment);
    }

    /**
     * Gets the total number of segments in this document.
     *
     * @return the segment count
     */
    public int getSegmentCount() {
        return segments.size();
    }

    @Override
    public String toString() {
        return "EdiDocument{" +
                "segmentCount=" + segments.size() +
                '}';
    }
}
