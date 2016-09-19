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

    private Set<Endpoint> endpoints;

    private Set<Service> services;

    public HaProxyConfigurationStateActor(HaproxyConfigurationGenerator haproxyConfigurationGenerator) {
        endpoints = new HashSet<>();
        services = new HashSet<>();
        receive(ReceiveBuilder.match(ProjectCreateMsg.class, msg -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Receive a endpoint creation message for endpoint '{}'.", msg.endpoint.getName());
            }
            if (endpoints.contains(msg.endpoint)) {
                sender().tell(new ProjectAlreadyExist(msg.endpoint), self());
            } else {
                endpoints.add(msg.endpoint);
                requestHaProxyUpdate(haproxyConfigurationGenerator);
            }
        })
                .match(ProjectUpdateMsg.class, msg -> {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Receive a endpoint update request message for endpoint '{}'.", msg.initialEndpoint.getName());
                    }
                    if (endpoints.contains(msg.initialEndpoint)) {
                        LOGGER.debug("Endpoint '{}' already referenced, update her configuration.", msg.initialEndpoint.getName());
                        updateEndpoint(haproxyConfigurationGenerator, msg, msg.initialEndpoint);
                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Receive a endpoint update request message for endpoint '{}', lookup by endpoint name.", msg.initialEndpoint.getName());
                        }
                        Optional<Endpoint> projectOptional = endpoints.stream().filter(p -> p.getName().equals(msg.initialEndpoint.getName())).findFirst();
                        if (projectOptional.isPresent()) {
                            Endpoint previousEndpoint = projectOptional.get();
                            updateEndpoint(haproxyConfigurationGenerator, msg, previousEndpoint);
                        } else {
                            LOGGER.warning("Endpoint {} not yet exist, convert request to Endpoint creation request.", msg.initialEndpoint.getName());
                            self().tell(new ProjectCreateMsg(msg.initialMsg, msg.initialEndpoint), sender());
                        }
                    }
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
        endpoints.remove(previousEndpoint);
        previousEndpoint.getServices().stream()
                .filter(s -> !serviceNames.contains(s.getName()))
                .forEach(services::add);


        Endpoint newEndpoint = new Endpoint(previousEndpoint.getName(), previousEndpoint.getPortIndex(), services, previousEndpoint.getCertificate());
        endpoints.add(newEndpoint);
        requestHaProxyUpdate(haproxyConfigurationGenerator);
    }

    private void requestHaProxyUpdate(HaproxyConfigurationGenerator haproxyConfigurationGenerator) {
        String configuration = haproxyConfigurationGenerator.generateConfiguration(endpoints, services);
        Map<String, String> certificates = new HashedMap<>();
        endpoints.stream().filter(p -> StringUtils.isNotBlank(p.getCertificate()))
                .forEach(p -> {
                    Set<String> services = p.getServiceNames(PortDefinition.Type.HTTPS.toString(), null);
                    services.addAll(p.getServiceNames(PortDefinition.Type.WSS.toString(), null));
                    services.stream().forEach(s -> certificates.put(p.getName() + "-" + s + ".pem", p.getCertificate()));
                });
        getContext().actorFor(EndpointActor.PATH).tell(new HaProxyUpdaterActor.UpdateHaProxyConfigurationMsg(sender(), configuration, certificates), self());
    }

    public static class ProjectCreateMsg {

        private final HaProxyPersistenceStateActor.ServiceMayUpdateMsg initialMsg;

        private final Endpoint endpoint;

        public ProjectCreateMsg(HaProxyPersistenceStateActor.ServiceMayUpdateMsg msg, Endpoint endpoint) {
            if (msg == null) {
                throw new IllegalArgumentException("msg must be defined.");
            }
            if (endpoint == null) {
                throw new IllegalArgumentException("endpoint must be defined.");
            }
            initialMsg = msg;
            this.endpoint = endpoint;
        }
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


    public static class ProjectAlreadyExist {

        private final Endpoint endpoint;

        public ProjectAlreadyExist(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        public Endpoint getEndpoint() {
            return endpoint;
        }
    }

}
