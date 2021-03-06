package in.projecteka.gateway.registry.model;

import in.projecteka.gateway.registry.ServiceType;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BridgeServiceRequest {
    String id;
    String name;
    ServiceType type;
    boolean active;
}