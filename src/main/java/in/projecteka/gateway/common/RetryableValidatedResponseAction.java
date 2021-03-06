package in.projecteka.gateway.common;

import com.fasterxml.jackson.databind.JsonNode;
import in.projecteka.gateway.clients.ServiceClient;
import in.projecteka.gateway.common.cache.ServiceOptions;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static in.projecteka.gateway.common.Constants.CORRELATION_ID;
import static in.projecteka.gateway.common.Constants.GW_DEAD_LETTER_EXCHANGE;

@AllArgsConstructor
public class RetryableValidatedResponseAction<T extends ServiceClient>
        implements MessageListener, ValidatedResponseAction {
    private static final Logger logger = LoggerFactory.getLogger(RetryableValidatedResponseAction.class);
    private final AmqpTemplate amqpTemplate;
    private final Jackson2JsonMessageConverter converter;
    private final DefaultValidatedResponseAction<T> defaultValidatedResponseAction;
    private final ServiceOptions serviceOptions;
    private final String deadLetterRoutingKey;
    private final String clientIdRequestHeader;

    @Override
    public void onMessage(Message message) {
        var jsonNode = (JsonNode) converter.fromMessage(message,
                ParameterizedTypeReference.forType(JsonNode.class));
        String xCmId = message.getMessageProperties().getHeader(clientIdRequestHeader);
        try {
            String correlationId = MDC.get(CORRELATION_ID);
            routeResponse(xCmId, jsonNode, clientIdRequestHeader).block();
            MDC.put(CORRELATION_ID, correlationId);
        } catch (Exception e) {
            if (hasExceededRetryCount(message)) {
                logger.error("Exceeded retry attempts; parking the message");
                amqpTemplate.convertAndSend("gw.parking.exchange",
                        message.getMessageProperties().getReceivedRoutingKey(),
                        message);
            } else {
                logger.error("Failed to routeResponse; pushing to DLQ");
                throw new AmqpRejectAndDontRequeueException(e);
            }
        }
    }

    boolean hasExceededRetryCount(Message in) {
        List<Map<String, ?>> xDeathHeader = in.getMessageProperties().getXDeathHeader();
        if (xDeathHeader != null && !xDeathHeader.isEmpty()) {
            Long count = (Long) xDeathHeader.get(0).get("count");
            logger.info("[GW] Number of attempts {}", count);
            return count >= serviceOptions.getResponseMaxRetryAttempts();
        }
        logger.warn("xDeathHeader not found in message");
        return false;
    }

    @Override
    public Mono<Void> routeResponse(String clientId, JsonNode updatedRequest, String routingKey) {
        return defaultValidatedResponseAction.routeResponse(clientId, updatedRequest, routingKey);
    }

    @Override
    public Mono<Void> handleError(Throwable throwable, String xCmId, JsonNode jsonNode) {
        logger.error("Error in notifying CM with result; pushing to DLQ for retry", throwable);
        MessagePostProcessor messagePostProcessor = message -> {
            Map<String, Object> headers = message.getMessageProperties().getHeaders();
            headers.put("X-CM-ID", xCmId);
            return message;
        };
        return Mono.create(monoSink -> {
            // TODO check queue names
            amqpTemplate.convertAndSend(GW_DEAD_LETTER_EXCHANGE, deadLetterRoutingKey, jsonNode, messagePostProcessor);
            monoSink.success();
        });
    }
}
