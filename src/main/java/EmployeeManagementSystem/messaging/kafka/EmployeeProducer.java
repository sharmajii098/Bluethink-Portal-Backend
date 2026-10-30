package EmployeeManagementSystem.messaging.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmployeeProducer {

    private static final Logger log = LoggerFactory.getLogger(EmployeeProducer.class);
    private final KafkaTemplate<String, EmployeeEvent> kafkaTemplate;

    public EmployeeProducer(KafkaTemplate<String, EmployeeEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEmployeeCreatedEvent(EmployeeEvent event) {
        kafkaTemplate.send(
                KafkaTopicConfig.EMPLOYEE_CREATED_TOPIC,
                String.valueOf(event.getEmployeeId()),
                event
        ).whenComplete((result, exception) -> {
            // Never log the event, recipient, key, or a provider exception message.
            // A successful Kafka acknowledgement is not email delivery.
            if (exception == null) {
                log.info("Employee-created Kafka event acknowledged by broker");
            } else {
                log.error("Employee-created Kafka event publishing failed (type={})",
                        exception.getClass().getSimpleName());
            }
        });
    }
}
