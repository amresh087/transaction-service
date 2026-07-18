package com.retail.transaction.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.retail.transaction.dto.TransactionResponse;
import com.retail.transaction.dto.TransactionTypeRequest;
import com.retail.transaction.dto.TransactionTypeResponse;
import com.retail.transaction.entity.TransactionType;
import com.retail.transaction.repository.TransactionTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionTypeRepository transactionTypeRepository;

    public List<TransactionResponse> getTransactions() {
        return List.of(
                TransactionResponse.builder()
                        .transactionId("TXN-1001")
                        .customerId("CUST-001")
                        .status("COMPLETED")
                        .amount(129.99)
                        .build(),
                TransactionResponse.builder()
                        .transactionId("TXN-1002")
                        .customerId("CUST-002")
                        .status("PENDING")
                        .amount(89.50)
                        .build()
        );
    }

    public List<TransactionTypeResponse> getTransactionTypes() {
        return transactionTypeRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public TransactionTypeResponse createTransactionType(TransactionTypeRequest request) {
        TransactionType entity = TransactionType.builder()
                .transactionCode(request.getTransactionCode())
                .documentName(request.getDocumentName())
                .purpose(request.getPurpose())
                .build();
        return toResponse(transactionTypeRepository.save(entity));
    }

    public TransactionTypeResponse updateTransactionType(String transactionCode, TransactionTypeRequest request) {
        TransactionType existing = transactionTypeRepository.findById(transactionCode)
                .orElseThrow(() -> new IllegalArgumentException("Transaction type not found: " + transactionCode));

        existing.setDocumentName(request.getDocumentName());
        existing.setPurpose(request.getPurpose());
        return toResponse(transactionTypeRepository.save(existing));
    }

    public void deleteTransactionType(String transactionCode) {
        transactionTypeRepository.deleteById(transactionCode);
    }

    private TransactionTypeResponse toResponse(TransactionType entity) {
        return TransactionTypeResponse.builder()
                .transactionCode(entity.getTransactionCode())
                .documentName(entity.getDocumentName())
                .purpose(entity.getPurpose())
                .build();
    }
}
