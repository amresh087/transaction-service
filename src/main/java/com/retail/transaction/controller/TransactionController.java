package com.retail.transaction.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.retail.transaction.dto.TransactionTypeRequest;
import com.retail.transaction.dto.TransactionTypeResponse;
import com.retail.transaction.service.TransactionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/transaction")
public class TransactionController {

    private final TransactionService transactionService;



    @GetMapping("/transaction-types")
    public List<TransactionTypeResponse> getTransactionTypes() {
        return transactionService.getTransactionTypes();
    }

    @GetMapping(value = "/xml/{documentId}/{xmlType}", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getXmlByDocumentAndType(
            @PathVariable String documentId,
            @PathVariable String xmlType) {
        try {
            String xml = transactionService.getXmlByDocumentAndType(documentId, xmlType);
            if (xml == null || xml.isBlank()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(xml);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PostMapping("/transaction-types")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionTypeResponse createTransactionType(@RequestBody TransactionTypeRequest request) {
        return transactionService.createTransactionType(request);
    }

    @PutMapping("/transaction-types/{transactionCode}")
    public TransactionTypeResponse updateTransactionType(@PathVariable String transactionCode,
            @RequestBody TransactionTypeRequest request) {
        return transactionService.updateTransactionType(transactionCode, request);
    }


    @DeleteMapping("/transaction-types/{transactionCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransactionType(@PathVariable String transactionCode) {
        transactionService.deleteTransactionType(transactionCode);
    }
}
