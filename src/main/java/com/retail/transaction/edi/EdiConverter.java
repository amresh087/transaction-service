package com.retail.transaction.edi;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.retail.transaction.edi.model.EdiDocument;
import com.retail.transaction.edi.model.EdiSegment;

/**
 * EdiConverter handles conversion of EDI (Electronic Data Interchange) formats
 * to XML and JSON. Supports EDIFACT and X12 formats.
 * 
 * EDIFACT (UN/EDIFACT):
 * - UN/EDIFACT standard format
 * - UNA header: 9-character service string advice
 * - Default separators: + (element), ? (release), ' (segment)
 * 
 * X12 (ASC X12):
 * - American Standards Committee X12 format
 * - Commonly used in US supply chain and healthcare
 * - Default separators: ^ (element), \ (release), ~ (segment)
 */
@Component
public class EdiConverter {

    private static final Logger log = LoggerFactory.getLogger(EdiConverter.class);

    /**
     * Converts EDI content to XML format.
     * Supports EDIFACT and X12 formats automatically.
     *
     * @param ediContent the EDI content to convert
     * @return XML representation of the EDI content
     */
    public String convertToXml(String ediContent) {
        if (!StringUtils.hasText(ediContent)) {
            return "<edi/>";
        }

        String normalizedContent = normalizeLineEndings(ediContent);
        EdiFormat ediFormat = detectFormat(normalizedContent);
        
        log.info("Detected EDI format: {}", ediFormat.getFormatName());
        return convertWithFormat(normalizedContent, ediFormat);
    }

    /**
     * Gets the detected format type from EDI content.
     *
     * @param ediContent the EDI content to analyze
     * @return the detected EdiFormatType (EDIFACT or X12)
     */
    public EdiFormatType getFormatType(String ediContent) {
        if (!StringUtils.hasText(ediContent)) {
            return EdiFormatType.EDIFACT; // default
        }
        String normalizedContent = normalizeLineEndings(ediContent);
        return detectFormat(normalizedContent).getFormatType();
    }

    /**
     * Gets the format name from EDI content.
     *
     * @param ediContent the EDI content to analyze
     * @return the format name (EDIFACT or X12)
     */
    public String getFormatName(String ediContent) {
        if (!StringUtils.hasText(ediContent)) {
            return "EDIFACT"; // default
        }
        String normalizedContent = normalizeLineEndings(ediContent);
        return detectFormat(normalizedContent).getFormatName();
    }

    /**
     * Converts EDI content to JSON format.
     *
     * @param ediContent the EDI content to convert
     * @return JSON representation of the EDI content
     */
    public String convertToJson(String ediContent) {
        if (!StringUtils.hasText(ediContent)) {
            return "{}";
        }

        String normalizedContent = normalizeLineEndings(ediContent);
        EdiFormat ediFormat = detectFormat(normalizedContent);

        try {
            EdiDocument document = parseEdi(normalizedContent, ediFormat);
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(document);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize EDI content to JSON, returning empty object", ex);
            return "{}";
        }
    }

    /**
     * Parses EDI content into an EdiDocument structure.
     *
     * @param ediContent the EDI content to parse
     * @return parsed EdiDocument
     */
    public EdiDocument parse(String ediContent) {
        if (!StringUtils.hasText(ediContent)) {
            return new EdiDocument();
        }

        String normalizedContent = normalizeLineEndings(ediContent);
        EdiFormat ediFormat = detectFormat(normalizedContent);

        return parseEdi(normalizedContent, ediFormat);
    }

    /**
     * Normalizes line endings to Unix format.
     */
    private String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
    }

    /**
     * Detects the EDI format from the content.
     * EDIFACT: Starts with UNA (9-char header) or UNB/UNH segments
     * X12: Starts with ISA segment (starts with "ISA")
     */
    private EdiFormat detectFormat(String content) {
        if (content.startsWith("UNA")) {
            // EDIFACT format with UNA header
            if (content.length() >= 9) {
                return new EdiFormat(
                        content.charAt(4),  // element separator
                        content.charAt(6),  // release indicator
                        content.charAt(8),  // segment terminator
                        EdiFormatType.EDIFACT
                );
            }
        } else if (content.startsWith("UNB") || content.startsWith("UNH")) {
            // EDIFACT format without UNA header
            return EdiFormat.fromFormatType(EdiFormatType.EDIFACT);
        } else if (content.startsWith("ISA")) {
            // X12 format detection - ISA is the functional group header
            return EdiFormat.fromFormatType(EdiFormatType.X12);
        }
        
        // Default to EDIFACT if format cannot be determined
        log.debug("EDI format not explicitly detected, defaulting to EDIFACT");
        return EdiFormat.fromFormatType(EdiFormatType.EDIFACT);
    }

    /**
     * Converts EDI with detected format.
     */
    private String convertWithFormat(String normalizedContent, EdiFormat format) {
        try {
            EdiDocument document = parseEdi(normalizedContent, format);
            return new XmlMapper().writeValueAsString(document);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize EDI content to XML, returning a fallback payload", ex);
            return "<edi><fallback>Unable to convert</fallback></edi>";
        }
    }

    /**
     * Parses EDI content with specified format.
     */
    private EdiDocument parseEdi(String normalizedContent, EdiFormat format) {
        String contentToParse = normalizedContent;

        // Handle format-specific header stripping
        if (format.getFormatType() == EdiFormatType.EDIFACT) {
            // Skip UNA header if present (EDIFACT only)
            if (normalizedContent.startsWith("UNA") && normalizedContent.length() >= 9) {
                contentToParse = normalizedContent.substring(9).trim();
            }
        } else if (format.getFormatType() == EdiFormatType.X12) {
            // X12 format - ISA segment is the header and should be processed normally
            log.debug("Parsing X12 format EDI content");
        }

        EdiDocument document = new EdiDocument();
        splitEdiSegments(contentToParse, format.getSegmentTerminator(), format.getReleaseIndicator())
                .stream()
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .forEach(segmentText -> {
                    List<String> parts = splitEdiElements(segmentText, format.getElementSeparator(),
                            format.getReleaseIndicator());
                    if (parts.isEmpty()) {
                        return;
                    }

                    String segmentName = parts.get(0);
                    List<String> fields = new ArrayList<>();
                    for (int index = 1; index < parts.size(); index++) {
                        String field = parts.get(index);
                        if (StringUtils.hasText(field)) {
                            fields.add(field);
                        }
                    }

                    document.addSegment(new EdiSegment(segmentName, fields));
                });

        return document;
    }

    /**
     * Splits EDI content into segments based on segment terminator.
     */
    private List<String> splitEdiSegments(String content, char segmentTerminator, char releaseIndicator) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == releaseIndicator) {
                escaped = true;
                continue;
            }

            if (c == segmentTerminator) {
                String segment = current.toString().trim();
                if (StringUtils.hasText(segment)) {
                    segments.add(segment);
                }
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        String lastSegment = current.toString().trim();
        if (StringUtils.hasText(lastSegment)) {
            segments.add(lastSegment);
        }

        return segments;
    }

    /**
     * Splits a segment into individual elements based on element separator.
     */
    private List<String> splitEdiElements(String segmentText, char elementSeparator, char releaseIndicator) {
        List<String> elements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < segmentText.length(); i++) {
            char c = segmentText.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == releaseIndicator) {
                escaped = true;
                continue;
            }

            if (c == elementSeparator) {
                elements.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(c);
        }

        elements.add(current.toString());
        return elements;
    }
}
