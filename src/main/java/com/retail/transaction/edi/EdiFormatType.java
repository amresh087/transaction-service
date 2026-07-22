package com.retail.transaction.edi;

/**
 * Enum representing supported EDI format types.
 */
public enum EdiFormatType {
    /**
     * UN/EDIFACT (United Nations Electronic Data Interchange For Administration, Commerce and Transport)
     * - International standard
     * - Uses UNA header (9-character service string advice)
     * - Default separators: + (element), ? (release), ' (segment)
     */
    EDIFACT("EDIFACT", '+', '?', '\''),
    
    /**
     * ASC X12 (American Standards Committee X12)
     * - US standard commonly used in supply chain and healthcare
     * - Starts with ISA segment
     * - Default separators: ^ (element), \ (release), ~ (segment)
     */
    X12("X12", '^', '\\', '~');

    private final String formatName;
    private final char elementSeparator;
    private final char releaseIndicator;
    private final char segmentTerminator;

    EdiFormatType(String formatName, char elementSeparator, char releaseIndicator, char segmentTerminator) {
        this.formatName = formatName;
        this.elementSeparator = elementSeparator;
        this.releaseIndicator = releaseIndicator;
        this.segmentTerminator = segmentTerminator;
    }

    public String getFormatName() {
        return formatName;
    }

    public char getElementSeparator() {
        return elementSeparator;
    }

    public char getReleaseIndicator() {
        return releaseIndicator;
    }

    public char getSegmentTerminator() {
        return segmentTerminator;
    }
}
