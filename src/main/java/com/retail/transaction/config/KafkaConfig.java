package com.retail.transaction.config;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.retail.transaction.dto.EdiDataEvent;
import com.retail.transaction.dto.TransformationEvent;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:transaction-service-group}")
    private String groupId;

    @Bean
    public JsonDeserializer<TransformationEvent> jsonDeserializer() {
        JsonDeserializer<TransformationEvent> deserializer = new JsonDeserializer<>(TransformationEvent.class);
        deserializer.addTrustedPackages("*");
        deserializer.setUseTypeHeaders(false);
        return deserializer;
    }

    @Bean
    public ErrorHandlingDeserializer<TransformationEvent> errorHandlingDeserializer(
            JsonDeserializer<TransformationEvent> jsonDeserializer) {
        return new ErrorHandlingDeserializer<>(jsonDeserializer);
    }

    @Bean
    public ConsumerFactory<String, TransformationEvent> consumerFactory(
            ErrorHandlingDeserializer<TransformationEvent> errorHandlingDeserializer) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        return new DefaultKafkaConsumerFactory<>(configProps,
                new ErrorHandlingDeserializer<>(new StringDeserializer()),
                errorHandlingDeserializer);
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            log.error("Deserialization failed for record at partition {} offset {}. Error: {}",
                    record.partition(), record.offset(), exception.getMessage(), exception);
        };

        return new DefaultErrorHandler(recoverer, new FixedBackOff(0, 0));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransformationEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, TransformationEvent> consumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, TransformationEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public ProducerFactory<String, EdiDataEvent> ediDataEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return new DefaultKafkaProducerFactory<>(configProps, new StringSerializer(), new JsonSerializer<>(mapper));
    }

    @Bean
    public KafkaTemplate<String, EdiDataEvent> ediDataEventKafkaTemplate(
            ProducerFactory<String, EdiDataEvent> ediDataEventProducerFactory) {
        return new KafkaTemplate<>(ediDataEventProducerFactory);
    }
}
