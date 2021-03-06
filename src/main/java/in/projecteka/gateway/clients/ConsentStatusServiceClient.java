package in.projecteka.gateway.clients;

import in.projecteka.gateway.common.Constants;
import in.projecteka.gateway.common.IdentityService;
import in.projecteka.gateway.common.cache.ServiceOptions;
import in.projecteka.gateway.registry.BridgeRegistry;
import in.projecteka.gateway.registry.CMRegistry;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.projecteka.gateway.registry.ServiceType.HIU;

public class ConsentStatusServiceClient extends ServiceClient {
    private final CMRegistry cmRegistry;
    private final BridgeRegistry bridgeRegistry;

    public ConsentStatusServiceClient(ServiceOptions serviceOptions,
                                         WebClient.Builder webClientBuilder,
                                         IdentityService identityService,
                                         CMRegistry cmRegistry,
                                         BridgeRegistry bridgeRegistry) {
        super(serviceOptions, webClientBuilder, identityService);
        this.cmRegistry = cmRegistry;
        this.bridgeRegistry = bridgeRegistry;
    }

    @Override
    protected Mono<String> getResponseUrl(String clientId) {
        return bridgeRegistry.getHostFor(clientId, HIU).map(host -> host + Constants.PATH_CONSENT_REQUEST_ON_STATUS);
    }

    @Override
    protected Mono<String> getRequestUrl(String clientId) {
        return cmRegistry.getHostFor(clientId).map(host -> host + Constants.PATH_CONSENT_REQUEST_STATUS);
    }
}
