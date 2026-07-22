package com.retail.transaction.edi;

/**
 * Represents an EDI format configuration with its separators and type.
 */
public class EdiFormat {
    private final char elementSeparator;
    private final char releaseIndicator;
    private final char segmentTerminator;
    private final EdiFormatType formatType;

    public EdiFormat(char elementSeparator, char releaseIndicator, char segmentTerminator, 
                     EdiFormatType formatType) {
        this.elementSeparator = elementSeparator;
        this.releaseIndicator = releaseIndicator;
        this.segmentTerminator = segmentTerminator;
        this.formatType = formatType;
    }

    /**
     * Creates an EdiFormat from a specific format type (uses default separators).
     *
     * @param formatType the EDI format type
     * @return EdiFormat instance with default separators for that type
     */
    public static EdiFormat fromFormatType(EdiFormatType formatType) {
        return new EdiFormat(
                formatType.getElementSeparator(),
                formatType.getReleaseIndicator(),
                formatType.getSegmentTerminator(),
                formatType
        );
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

    public EdiFormatType getFormatType() {
        return formatType;
    }

    public String getFormatName() {
        return formatType.getFormatName();
    }

    @Override
    public String toString() {
        return "EdiFormat{" +
                "formatType=" + formatType.getFormatName() +
                ", elementSeparator='" + elementSeparator + '\'' +
                ", releaseIndicator='" + releaseIndicator + '\'' +
                ", segmentTerminator='" + segmentTerminator + '\'' +
                '}';
    }
}
