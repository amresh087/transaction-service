package com.retail.transaction.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.retail.transaction.dto.EdiDataEvent;
import com.retail.transaction.dto.TransactionResponse;
import com.retail.transaction.dto.TransactionTypeRequest;
import com.retail.transaction.dto.TransactionTypeResponse;
import com.retail.transaction.dto.TransformationEvent;
import com.retail.transaction.edi.EdiConverter;
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
    private final KafkaTemplate<String, EdiDataEvent> ediDataEventKafkaTemplate;
    private final EdiConverter ediConverter;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${app.kafka.topic.edi-data-event:edi-data-event}")
    private String ediDataEventTopic;

    /**
     * Retrieves a list of all transactions.
     * @return List of transaction responses.
     */

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

    /**
     * Retrieves a list of all transaction types.
     * @return List of transaction type responses.
     */


    public List<TransactionTypeResponse> getTransactionTypes() {
        return transactionTypeRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Creates a new transaction type based on the provided request.
     * @param request
     * @return
     */


    public TransactionTypeResponse createTransactionType(TransactionTypeRequest request) {
        TransactionType entity = TransactionType.builder()
                .transactionCode(request.getTransactionCode())
                .documentName(request.getDocumentName())
                .purpose(request.getPurpose())
                .build();
        return toResponse(transactionTypeRepository.save(entity));
    }

    /**
     * Updates an existing transaction type based on the provided request.
     * @param transactionCode
     * @param request
     * @return
     */

    public TransactionTypeResponse updateTransactionType(String transactionCode, TransactionTypeRequest request) {
        TransactionType existing = transactionTypeRepository.findById(transactionCode)
                .orElseThrow(() -> new IllegalArgumentException("Transaction type not found: " + transactionCode));

        existing.setDocumentName(request.getDocumentName());
        existing.setPurpose(request.getPurpose());
        return toResponse(transactionTypeRepository.save(existing));
    }

    /**
     * Deletes a transaction type by its code.
     * @param transactionCode
     */
    public void deleteTransactionType(String transactionCode) {
        transactionTypeRepository.deleteById(transactionCode);
    }

    /**
     * Processes a transformation event.
     * @param event
     */
    public void processTransformationEvent(TransformationEvent event) {

        if (event == null || !StringUtils.hasText(event.getObjectName())) {
            log.warn("Transformation event has no object name, skipping MinIO download");
            return;
        }

        try (InputStream objectStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(event.getObjectName())
                        .build())) {

            String fileContent = new String(objectStream.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Downloaded transformation file {} from MinIO bucket {}. Content length: {}",
                    event.getObjectName(), bucketName, fileContent.length());

            String xmlPayload = ediConverter.convertToXml(fileContent);
            log.info("Converted EDI payload for {} into XML with {} characters", event.getObjectName(), xmlPayload.length());

            EdiDataEvent ediDataEvent = EdiDataEvent.builder()
                    .documentId(event.getDocumentId())
                    .documentName(event.getDocumentName())
                    .documentType(event.getDocumentType())
                    .tenant(event.getTenant())
                    .transactionTypeCode(event.getTransactionTypeCode())
                    .mappingType(event.getMappingType())
                    .status(event.getStatus())
                    .objectName(event.getObjectName())
                    .eventType(event.getEventType())
                    .jobId(event.getJobId())
                    .timestamp(event.getTimestamp() != null ? java.time.LocalDateTime.parse(event.getTimestamp()) : null)
                    .payload(xmlPayload)
                    .build();

            Message<EdiDataEvent> message = MessageBuilder
                    .withPayload(ediDataEvent)
                    .setHeader(KafkaHeaders.TOPIC, ediDataEventTopic)
                    .setHeader(KafkaHeaders.KEY, event.getDocumentId())
                    .setHeader("event_type", event.getEventType())
                    .setHeader("tenant", event.getTenant())
                    .build();

            ediDataEventKafkaTemplate.send(message);
            log.info("Published EDI data event to Kafka topic {} for object {}", ediDataEventTopic,
                    event.getObjectName());
                    
        } catch (Exception ex) {
            log.error("Failed to read transformation file {} from MinIO", event.getObjectName(), ex);
        }
    }



    /**
     * Converts a TransactionType entity to a TransactionTypeResponse.
     * @param entity
     * @return
     */
    private TransactionTypeResponse toResponse(TransactionType entity) {
        return TransactionTypeResponse.builder()
                .transactionCode(entity.getTransactionCode())
                .documentName(entity.getDocumentName())
                .purpose(entity.getPurpose())
                .build();
    }
}
