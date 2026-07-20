package com.retail.transaction.kafka;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.retail.transaction.client.DocumentServiceClient;
import com.retail.transaction.dto.TransformationEvent;
import com.retail.transaction.dto.TransformationJobStatusRequest;
import com.retail.transaction.service.JobStatusType;
import com.retail.transaction.service.TransactionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransformationEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransformationEventListener.class);

    private final TransactionService transactionService;

    private final DocumentServiceClient documentServiceClient;

    @KafkaListener(topics = "${app.kafka.topic.transformation-events:transformation-events}", groupId = "${spring.kafka.consumer.group-id:transaction-service-group}", containerFactory = "kafkaListenerContainerFactory")
    public void handleTransformationEvent(TransformationEvent event) {
        try {
            log.info("Received transformation event from topic 'transformation-events': {}", event);

            if (event == null || event.getTransactionTypeCode() == null) {
                log.warn("Invalid transformation event received: missing required fields");
                return;
            }

            if (!"TRANSFORMATION_REQUEST".equalsIgnoreCase(event.getEventType())) {
                log.debug("Ignoring transformation event with unsupported event type: {}", event.getEventType());
                return;
            }

            if (event.getJobId() != null && !event.getJobId().isBlank()) {
                try {
                    UUID jobId = UUID.fromString(event.getJobId());
                    TransformationJobStatusRequest request = TransformationJobStatusRequest.builder()
                            .jobName(event.getDocumentName())
                            .payload(JobStatusType.PROCESSING.getValue())
                            .build();
                    documentServiceClient.updateJobStatus(jobId, request);
                } catch (IllegalArgumentException ex) {
                    log.warn("Invalid jobId format received in transformation event: {}", event.getJobId(), ex);
                }
            }
            // Process the transformation event
            transactionService.processTransformationEvent(event);
            log.info("Successfully processed transformation event for transactionCode={}", event.getTransactionTypeCode());
        } catch (Exception e) {
            log.error("Error processing transformation event: {}", event, e);
        }
    }
}
