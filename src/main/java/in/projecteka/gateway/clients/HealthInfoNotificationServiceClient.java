package in.projecteka.gateway.clients;

import in.projecteka.gateway.common.Constants;
import in.projecteka.gateway.common.IdentityService;
import in.projecteka.gateway.common.cache.ServiceOptions;
import in.projecteka.gateway.registry.CMRegistry;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class HealthInfoNotificationServiceClient extends ServiceClient {

    private final CMRegistry cmRegistry;
    public HealthInfoNotificationServiceClient(ServiceOptions serviceOptions,
                                               WebClient.Builder webClientBuilder,
                                               IdentityService centralRegistry,
                                               CMRegistry cmRegistry) {
        super(serviceOptions, webClientBuilder, centralRegistry);
        this.cmRegistry = cmRegistry;
    }

    @Override
    protected Mono<String> getResponseUrl(String clientId) {
        return Mono.empty();
    }

    @Override
    protected Mono<String> getRequestUrl(String clientId) {
        return cmRegistry.getHostFor(clientId).map(host -> host + Constants.PATH_HEALTH_INFORMATION_NOTIFY);
    }
}
