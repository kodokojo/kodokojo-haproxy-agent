package io.kodokojo.ha.actor;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.ha.model.Endpoint;
import io.kodokojo.ha.model.PortDefinition;
import io.kodokojo.ha.model.Service;
import io.kodokojo.ha.service.haproxy.HaproxyConfigurationGenerator;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static akka.event.Logging.getLogger;

public class HaProxyConfigurationStateActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static final Props PROPS(HaproxyConfigurationGenerator haproxyConfigurationGenerator) {
        if (haproxyConfigurationGenerator == null) {
            throw new IllegalArgumentException("haproxyConfigurationGenerator must be defined.");
        }
        return Props.create(HaProxyConfigurationStateActor.class, haproxyConfigurationGenerator);
    }

    private Map<String, Endpoint> endpoints;

    private Set<Service> services;

    public HaProxyConfigurationStateActor(HaproxyConfigurationGenerator haproxyConfigurationGenerator) {
        endpoints = new HashMap<>();
        services = new HashSet<>();
        receive(ReceiveBuilder
                .match(ProjectUpdateMsg.class, msg -> {
                    Endpoint endpoint = msg.initialEndpoint;
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Receive a endpoint update request message for endpoint '{}'.", endpoint.getName());
                    }
                    if (endpoints.get(endpoint.getName()) != null) {
                        endpoint = endpoints.get(endpoint.getName());
                        LOGGER.debug("Endpoint '{}' already referenced, update her configuration.", endpoint.getName());
                    } else {
                        endpoints.put(endpoint.getName(), endpoint);
                    }
                    updateEndpoint(haproxyConfigurationGenerator, msg, endpoint);
                })
                .match(HaProxyUpdaterActor.UpdateHaProxyConfigurationResultMsg.class, msg -> {
                    msg.getInitialRequest().getOriginalSender().forward(msg, getContext());
                })
                .matchAny(this::unhandled).build());
    }

    private void updateEndpoint(HaproxyConfigurationGenerator haproxyConfigurationGenerator, ProjectUpdateMsg msg, Endpoint previousEndpoint) {
        if (LOGGER.isDebugEnabled()) {
            Collection<Service> diffService = CollectionUtils.disjunction(previousEndpoint.getServices(), msg.initialEndpoint.getServices());
            Collection<Service> copyDiff = new HashSet<>(diffService);
            Set<Service> serviceChanged = diffService.stream().filter(s -> copyDiff.stream().filter(c -> s.getName().equals(c.getName())).findAny().isPresent()).collect(Collectors.toSet());
            if (CollectionUtils.isNotEmpty(serviceChanged)) {
                LOGGER.debug("Changed detected on following service of endpoint {} : {}", msg.initialEndpoint.getName(), StringUtils.join(serviceChanged, ","));
            }
        }
        Set<Service> services = msg.initialMsg.getServices();
        Set<String> serviceNames = services.stream().map(service -> service.getName()).collect(Collectors.toSet());
       // endpoints.remove(previousEndpoint);
        previousEndpoint.getServices().stream()
                .filter(s -> !serviceNames.contains(s.getName()))
                .forEach(services::add);

        if (CollectionUtils.isNotEmpty(services)) {
            Endpoint newEndpoint = new Endpoint(previousEndpoint.getName(), previousEndpoint.getPortIndex(), services, previousEndpoint.getCertificate());
            endpoints.put(newEndpoint.getName(), newEndpoint);
        } else {
            endpoints.remove(previousEndpoint.getName());
        }
        requestHaProxyUpdate(haproxyConfigurationGenerator);
    }

    private void requestHaProxyUpdate(HaproxyConfigurationGenerator haproxyConfigurationGenerator) {
        String configuration = haproxyConfigurationGenerator.generateConfiguration(new HashSet<>(endpoints.values()), services);
        Map<String, String> certificates = new HashedMap<>();
        endpoints.values().stream().filter(p -> StringUtils.isNotBlank(p.getCertificate()))
                .forEach(p -> {
                    Set<String> services = p.getServiceNames(PortDefinition.Type.HTTPS.toString(), null);
                    services.addAll(p.getServiceNames(PortDefinition.Type.WSS.toString(), null));
                    services.stream().forEach(s -> certificates.put(p.getName() + "-" + s + ".pem", p.getCertificate()));
                });
        getContext().actorFor(EndpointActor.PATH).tell(new HaProxyUpdaterActor.UpdateHaProxyConfigurationMsg(sender(), configuration, certificates), self());
    }


    public static class ProjectUpdateMsg {

        private final HaProxyPersistenceStateActor.ServiceMayUpdateMsg initialMsg;

        private final Endpoint initialEndpoint;

        public ProjectUpdateMsg(HaProxyPersistenceStateActor.ServiceMayUpdateMsg msg, Endpoint initialEndpoint) {
            if (msg == null) {
                throw new IllegalArgumentException("msg must be defined.");
            }
            if (initialEndpoint == null) {
                throw new IllegalArgumentException("endpoint must be defined.");
            }
            initialMsg = msg;
            this.initialEndpoint = initialEndpoint;
        }
    }

}
