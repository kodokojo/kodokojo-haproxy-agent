package io.kodokojo.ha.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import io.kodokojo.ha.service.haproxy.HaproxyUpdater;

import java.util.Map;

import static akka.event.Logging.getLogger;
import static org.apache.commons.lang.StringUtils.isBlank;

public class HaProxyUpdaterActor extends AbstractActor {

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);


    public static Props PROPS(HaproxyUpdater haproxyUpdater) {
        if (haproxyUpdater == null) {
            throw new IllegalArgumentException("haproxyUpdater must be defined.");
        }
        return Props.create(HaProxyUpdaterActor.class, haproxyUpdater);
    }

    private String currentConfigurationFile = "";

    public HaProxyUpdaterActor(HaproxyUpdater haproxyUpdater) {
        receive(ReceiveBuilder
                .match(UpdateHaProxyConfigurationMsg.class, msg -> {
                    String newConfigurationFile = msg.configuration;
                    if (!newConfigurationFile.equals(currentConfigurationFile)) {
                        if (haproxyUpdater.validateConfiguration(newConfigurationFile, msg.certificate)) {
                            currentConfigurationFile = newConfigurationFile;
                            haproxyUpdater.updateConfiguration(newConfigurationFile, msg.certificate);
                        } else {
                            LOGGER.warning("Try to reload an invalidated ha proxy configuration file.");
                        }
                    } else if(LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Configuration file content are the same, aborting ht reload.");
                    }
                })
                .matchAny(this::unhandled).build());
    }

    public static class UpdateHaProxyConfigurationMsg {

        private final ActorRef originalSender;

        private final String configuration;

        private final Map<String, String> certificate;

        public UpdateHaProxyConfigurationMsg(ActorRef originalSender, String configuration, Map<String, String> certificate) {
            if (originalSender == null) {
                throw new IllegalArgumentException("originalSender must be defined.");
            }
            if (isBlank(configuration)) {
                throw new IllegalArgumentException("configuration must be defined.");
            }
            this.originalSender = originalSender;
            this.configuration = configuration;
            this.certificate = certificate;
        }

        public ActorRef getOriginalSender() {
            return originalSender;
        }

        public String getConfiguration() {
            return configuration;
        }

        public Map<String, String> getCertificate() {
            return certificate;
        }
    }

    public static class UpdateHaProxyConfigurationResultMsg {

        private final UpdateHaProxyConfigurationMsg initialRequest;

        private final boolean updated;

        public UpdateHaProxyConfigurationResultMsg(UpdateHaProxyConfigurationMsg initialRequest, boolean updated) {
            this.initialRequest = initialRequest;
            this.updated = updated;
        }

        public UpdateHaProxyConfigurationMsg getInitialRequest() {
            return initialRequest;
        }

        public boolean isUpdated() {
            return updated;
        }
    }

}
