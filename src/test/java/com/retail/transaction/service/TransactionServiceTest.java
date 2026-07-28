package com.retail.transaction.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionServiceTest {

    @Test
    void resolvesEdiXmlObjectName() {
        assertEquals("edixml/sample.xml", TransactionService.resolveXmlObjectName("sample", "edixml"));
    }

    @Test
    void resolvesIdocXmlObjectName() {
        assertEquals("inbound/sample.xml", TransactionService.resolveXmlObjectName("sample", "idocxml"));
    }

    @Test
    void rejectsUnsupportedXmlType() {
        assertThrows(IllegalArgumentException.class, () -> TransactionService.resolveXmlObjectName("sample", "unknown"));
    }
}
