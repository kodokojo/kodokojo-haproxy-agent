package io.kodokojo.ha.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import com.google.inject.Injector;
import io.kodokojo.ha.config.properties.ApplicationConfig;
import io.kodokojo.ha.config.properties.ZookeeperConfig;
import io.kodokojo.ha.service.haproxy.HaproxyConfigurationGenerator;
import io.kodokojo.ha.service.haproxy.HaproxyUpdater;
import io.kodokojo.ha.service.marathon.MarathonServiceLookup;

import static akka.event.Logging.getLogger;

public class EndpointActor extends AbstractActor {

    public static final String PATH = "/user/endpoint";

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static final Props PROPS(Injector injector) {
        if (injector == null) {
            throw new IllegalArgumentException("injector must be defined.");
        }
        return Props.create(EndpointActor.class, injector);
    }

    public EndpointActor(Injector injector) {
        LOGGER.info("Starting endpoint");
        ActorRef haproxyPersistenceStateActorRef = getContext().actorOf(HaProxyPersistenceStateActor.PROPS(injector.getInstance(ApplicationConfig.class), injector.getInstance(ZookeeperConfig.class)), "haproxyStateActor");
        ActorRef haproxyConfigurationStateActorRef = getContext().actorOf(HaProxyConfigurationStateActor.PROPS(injector.getInstance(HaproxyConfigurationGenerator.class)));
        ActorRef haproxyUpdater = getContext().actorOf(HaProxyUpdaterActor.PROPS(injector.getInstance(HaproxyUpdater.class)));
        receive(ReceiveBuilder
                .match(ServiceLookupActor.ZookeeperServiceLookupMsg.class, msg -> {
            getContext().actorOf(ServiceLookupActor.PROPS(injector.getInstance(MarathonServiceLookup.class))).forward(msg, getContext());
        })
                .match(ZookeeperEventHandlerActor.ZookeeperEventMsg.class, msg -> {
                    getContext().actorOf(ZookeeperEventHandlerActor.PROPS()).forward(msg, getContext());
                })
                .match(HaProxyPersistenceStateActor.HaProxyZookeeperEventMsg.class, msg -> {
                    haproxyPersistenceStateActorRef.forward(msg, getContext());
                })
                .match(HaProxyPersistenceStateActor.ServiceMayUpdateMsg.class, msg -> {
                    haproxyPersistenceStateActorRef.forward(msg, getContext());
                }).match(HaProxyConfigurationStateActor.ProjectUpdateMsg.class, msg -> {
                    haproxyConfigurationStateActorRef.forward(msg, getContext());
                }).match(HaProxyUpdaterActor.UpdateHaProxyConfigurationMsg.class, msg -> {
                    haproxyUpdater.forward(msg, getContext());
                })
                .matchAny(this::unhandled).build());
    }
}
