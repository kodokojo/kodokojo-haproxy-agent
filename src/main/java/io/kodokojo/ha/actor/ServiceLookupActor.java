package io.kodokojo.ha.actor;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.ha.model.Service;
import io.kodokojo.ha.service.marathon.MarathonServiceLookup;
import org.apache.commons.lang.StringUtils;

import java.util.Set;

import static akka.event.Logging.getLogger;
import static org.apache.commons.lang.StringUtils.isBlank;

public class ServiceLookupActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    public final static Props PROPS(MarathonServiceLookup marathonServiceLookup) {
        if (marathonServiceLookup == null) {
            throw new IllegalArgumentException("marathonServiceLookup must be defined.");
        }
        return Props.create(ServiceLookupActor.class, marathonServiceLookup);
    }

    public ServiceLookupActor(MarathonServiceLookup marathonServiceLookup) {
        receive(ReceiveBuilder.match(ZookeeperServiceLookupMsg.class, msg -> {
            String marathonAppId = msg.zookeeperNodeServiceName.replaceAll("_", "/");
            Set<Service> services = marathonServiceLookup.lookup(marathonAppId);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Lookup {} return {}",marathonAppId,  StringUtils.join(services, ","));
            }
            sender().tell(new ZookeeperServiceLookupResultMsg(msg, services), self());
           // getContext().stop(self());
        }).matchAny(this::unhandled).build());
    }

    public static class ZookeeperServiceLookupMsg {

        private final String zookeeperServicePath;

        private final String zookeeperNodeServiceName;

        public ZookeeperServiceLookupMsg(String zookeeperServicePath,String zookeeperNodeServiceName) {
            if (isBlank(zookeeperServicePath)) {
                throw new IllegalArgumentException("zookeeperServicePath must be defined.");
            }
            if (isBlank(zookeeperNodeServiceName)) {
                throw new IllegalArgumentException("zookeeperNodeServiceName must be defined.");
            }
            this.zookeeperServicePath = zookeeperServicePath;
            this.zookeeperNodeServiceName = zookeeperNodeServiceName;
        }
    }

    public static class ZookeeperServiceLookupResultMsg {

        private final ZookeeperServiceLookupMsg request;

        private Set<Service> services;

        public ZookeeperServiceLookupResultMsg(ZookeeperServiceLookupMsg request, Set<Service> services) {
            if (request == null) {
                throw new IllegalArgumentException("request must be defined.");
            }
            if (services == null) {
                throw new IllegalArgumentException("services must be defined.");
            }
            this.request = request;
            this.services = services;
        }

        public ZookeeperServiceLookupMsg getRequest() {
            return request;
        }

        public Set<Service> getServices() {
            return services;
        }
    }


}
