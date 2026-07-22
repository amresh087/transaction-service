package com.retail.transaction.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.retail.transaction.client.AiTranformationServiceClient;
import com.retail.transaction.dto.EdiDataEvent;
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
    private final AiTranformationServiceClient aiTranformationServiceClient;
    private final KafkaTemplate<String, EdiDataEvent> ediDataEventKafkaTemplate;

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

            //String xmlPayload = convertEdiToXml(fileContent);
            log.info("Converted EDI payload for {} into XML with {} characters", event.getObjectName(), fileContent.length());

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
                    .payload(fileContent)
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
     * 
     * @param ediContent
     * @return
     */

    String convertEdiToXml(String ediContent) {
        if (!StringUtils.hasText(ediContent)) {
            return "<edi/>";
        }

        String normalizedContent = ediContent.replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();

        char elementSeparator;
        char releaseIndicator;
        char segmentTerminator;

        if (normalizedContent.startsWith("UNA") && normalizedContent.length() >= 9) {
            String unaHeader = normalizedContent.substring(0, 9);
            elementSeparator = unaHeader.charAt(4);
            releaseIndicator = unaHeader.charAt(6);
            segmentTerminator = unaHeader.charAt(8);
            normalizedContent = normalizedContent.substring(9).trim();
        } else {
            elementSeparator = '+';
            releaseIndicator = '?';
            segmentTerminator = '\'';
        }

        EdiDocument document = new EdiDocument();
        splitEdiSegments(normalizedContent, segmentTerminator, releaseIndicator).stream()
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .forEach(segmentText -> {
                    List<String> parts = splitEdiElements(segmentText, elementSeparator, releaseIndicator);
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

                    document.getSegments().add(new EdiSegment(segmentName, fields));
                });

        try {
            return new XmlMapper().writeValueAsString(document);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to serialize EDI content to XML, returning a fallback payload", ex);
            return "<edi><fallback>Unable to convert</fallback></edi>";
        }
    }

    /**
     * 
     * @param content
     * @param segmentTerminator
     * @param releaseIndicator
     * @return
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
     * 
     * @param segmentText
     * @param elementSeparator
     * @param releaseIndicator
     * @return
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

    /**
     * 
     * EdiDocument
     */
    @JacksonXmlRootElement(localName = "edi")
    static class EdiDocument {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "segment")
        private final List<EdiSegment> segments = new ArrayList<>();

        public List<EdiSegment> getSegments() {
            return segments;
        }
    }

    /**
     * 
     * EdiSegment
     */

    static class EdiSegment {
        @JacksonXmlProperty(isAttribute = true)
        private final String name;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "field")
        private final List<String> fields;

        EdiSegment(String name, List<String> fields) {
            this.name = name;
            this.fields = fields;
        }

        public String getName() {
            return name;
        }

        public List<String> getFields() {
            return fields;
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
