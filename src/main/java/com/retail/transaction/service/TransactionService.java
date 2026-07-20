package com.retail.transaction.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.retail.transaction.dto.TransactionResponse;
import com.retail.transaction.dto.TransactionTypeRequest;
import com.retail.transaction.dto.TransactionTypeResponse;
import com.retail.transaction.dto.TransformationEvent;
import com.retail.transaction.entity.TransactionType;
import com.retail.transaction.repository.TransactionTypeRepository;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionTypeRepository transactionTypeRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

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
                        .build());
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

    public void processTransformationEvent(TransformationEvent event) {
        if (event == null || event.getObjectName() == null || event.getObjectName().isBlank()) {
            log.warn("Transformation event has no object name, skipping MinIO download");
            return;
        }

        try (InputStream objectStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(event.getObjectName())
                        .build())) {

            String fileContent = new String(objectStream.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Downloaded transformation file {} from MinIO bucket {}. Content length: {}",
                    event.getObjectName(), bucketName, fileContent.length());

        } catch (Exception ex) {
            log.error("Failed to read transformation file {} from MinIO", event.getObjectName(), ex);
        }
    }

    private TransactionTypeResponse toResponse(TransactionType entity) {
        return TransactionTypeResponse.builder()
                .transactionCode(entity.getTransactionCode())
                .documentName(entity.getDocumentName())
                .purpose(entity.getPurpose())
                .build();
    }
}
