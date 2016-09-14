package io.kodokojo.ha.service.haproxy;

import io.kodokojo.ha.model.Endpoint;
import io.kodokojo.ha.model.Service;

import java.util.Set;

public interface HaproxyConfigurationGenerator {

    String generateConfiguration(Set<Endpoint> endpoints, Set<Service> services);

}
