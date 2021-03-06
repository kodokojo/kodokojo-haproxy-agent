package io.kodokojo.ha.service.haproxy;

import io.kodokojo.ha.model.PortDefinition;
import io.kodokojo.ha.model.Endpoint;
import io.kodokojo.ha.model.Service;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class VelocityHaproxyConfigurationGeneratorTest {

    @Test
    public void acceptanceTest() {
        VelocityHaproxyConfigurationGenerator generator = new VelocityHaproxyConfigurationGenerator();
        Set<Endpoint> endpoints = new HashSet<>();
        Set<Service> projectServices = new HashSet<>();
        projectServices.add(new Service("gitlab", "10.1.0.1", 10001, new PortDefinition(PortDefinition.Protocol.TCP, PortDefinition.Type.HTTPS, 443, 0, 10001, new HashMap<>())));
        HashMap<String, String> labels = new HashMap<>();
        labels.put("frontName", "registry-gitlab");
        projectServices.add(new Service("gitlab", "10.1.0.1", 10002, new PortDefinition(PortDefinition.Protocol.TCP, PortDefinition.Type.HTTPS, 80, 0, 10001, labels)));
        labels = new HashMap<>();
        //labels.put("frontName", "ws-gitlab");
        projectServices.add(new Service("gitlab", "10.1.0.1", 10002, new PortDefinition(PortDefinition.Protocol.TCP, PortDefinition.Type.WSS, 80, 0, 10001, labels)));
        projectServices.add(new Service("gitlab", "10.1.0.1", 10003, new PortDefinition(PortDefinition.Protocol.TCP, PortDefinition.Type.SSH, 22, 0, 10022, new HashMap<>())));
        projectServices.add(new Service("gitlab", "10.1.0.1", 10004, new PortDefinition(PortDefinition.Protocol.TCP, PortDefinition.Type.OTHER, 4444, 0, 14444, new HashMap<>())));
        endpoints.add(new Endpoint("monprojet", 1, projectServices, "UN super certificat"));
        Set<Service> services = new HashSet<>();
        String configuration = generator.generateConfiguration(endpoints, services);
        System.out.println(configuration);
    }

}