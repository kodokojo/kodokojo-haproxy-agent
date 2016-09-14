package io.kodokojo.ha.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import org.apache.zookeeper.WatchedEvent;

import static akka.event.Logging.getLogger;
import static org.apache.commons.lang.StringUtils.isBlank;

public class ZookeeperEventHandlerActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public static final Props PROPS() {
        return Props.create(ZookeeperEventHandlerActor.class);
    }

    private ZookeeperEventMsg originalMsg;

    private ActorRef originalSender;

    public ZookeeperEventHandlerActor() {
        receive(ReceiveBuilder.match(ZookeeperEventMsg.class, msg -> {
            originalMsg = msg;
            originalSender = sender();

            getContext().actorFor(EndpointActor.PATH).tell(new ServiceLookupActor.ZookeeperServiceLookupMsg(msg.originalWatchedEvent.getPath(), msg.serviceName), self());

        })
                .match(ServiceLookupActor.ZookeeperServiceLookupResultMsg.class, msg -> {
                    getContext().actorFor(EndpointActor.PATH).tell(new HaProxyPersistenceStateActor.ServiceMayUpdateMsg(originalMsg.serviceName, msg.getServices()), originalSender);
                    getContext().stop(self());
                })
                .matchAny(this::unhandled).build());
    }

    public static class ZookeeperEventMsg {

        private final WatchedEvent originalWatchedEvent;

        private final String serviceName;

        public ZookeeperEventMsg(WatchedEvent originalWatchedEvent, String serviceName) {
            if (originalWatchedEvent == null) {
                throw new IllegalArgumentException("originalWatchedEvent must be defined.");
            }
            if (isBlank(serviceName)) {
                throw new IllegalArgumentException("serviceName must be defined.");
            }
            this.originalWatchedEvent = originalWatchedEvent;
            this.serviceName = serviceName;
        }
    }

}
