package io.kodokojo.ha.service.zookeeper;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import io.kodokojo.ha.actor.EndpointActor;
import io.kodokojo.ha.actor.HaProxyPersistenceStateActor;
import io.kodokojo.ha.actor.ZookeeperEventHandlerActor;
import io.kodokojo.ha.config.properties.ApplicationConfig;
import io.kodokojo.ha.config.properties.MarathonConfig;
import io.kodokojo.ha.config.properties.MesosConfig;
import io.kodokojo.ha.model.PortDefinition;
import io.kodokojo.ha.model.Service;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang.StringUtils.isBlank;

public class ZookeeperMarathonRootStateWatcher implements Watcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperMarathonRootStateWatcher.class);

    private static final String MARATHON_STATE = "/marathon/state";

    private static final String TASKS_PATH_BEGIN = MARATHON_STATE + "/task:";

    private static final Pattern TASK_PATTERN = Pattern.compile("^\\/marathon\\/state\\/task:(.*)\\..*$");

    private final Set<String> appAndTasks;

    private final String zookeeperUrl;

    private final ApplicationConfig applicationConfig;

    private final MarathonConfig marathonConfig;

    private final MesosConfig mesosConfig;

    private ZooKeeper zooKeeper;

    private final ActorSystem actorSystem;

    private final Object monitor = new Object();

    @Inject
    public ZookeeperMarathonRootStateWatcher(String zooKeeperUrl, ApplicationConfig applicationConfig, MarathonConfig marathonConfig, MesosConfig mesosConfig, ActorSystem actorSystem) {
        if (isBlank(zooKeeperUrl)) {
            throw new IllegalArgumentException("zooKeeperUrl must be defined.");
        }
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig must be defined.");
        }
        if (marathonConfig == null) {
            throw new IllegalArgumentException("marathonConfig must be defined.");
        }
        if (mesosConfig == null) {
            throw new IllegalArgumentException("mesosConfig must be defined.");
        }
        if (actorSystem == null) {
            throw new IllegalArgumentException("actorSystem must be defined.");
        }
        this.zookeeperUrl = zooKeeperUrl;
        this.applicationConfig = applicationConfig;
        this.marathonConfig = marathonConfig;
        this.mesosConfig = mesosConfig;
        this.actorSystem = actorSystem;
        this.appAndTasks = new HashSet<>();
        marathonConfig.registerCallback((key, newValue) -> {
            if ("marathon.url".equals(key) && applicationConfig.exposeMarathon()) {
                requestEnvServiceUpdate();
            }
        });
        mesosConfig.registerCallback((key, newValue) -> {
            if ("mesos.url".equals(key) && applicationConfig.exposeMarathon()) {
                requestEnvServiceUpdate();
            }
        });
    }

    public synchronized void start() {
        if (zooKeeper == null) {
            try {
                this.zooKeeper = new ZooKeeper(zookeeperUrl, 1000, this);
                List<String> children = zooKeeper.getChildren("/marathon/state", this);
                children.stream().filter(node -> node.startsWith("task:"))
                        .forEach(c -> {
                            try {
                                String taskPath = MARATHON_STATE + "/" + c;
                                zooKeeper.exists(taskPath, this);
                                Matcher matcher = TASK_PATTERN.matcher(taskPath);
                                if (matcher.matches() && matcher.groupCount() == 1) {
                                    String endpointServiceName = matcher.group(1);
                                    LOGGER.info("Handle node {}.", taskPath);
                                    actorSystem.actorFor(EndpointActor.PATH).tell(new ZookeeperEventHandlerActor.ZookeeperEventMsg(taskPath, endpointServiceName), ActorRef.noSender());
                                }
                            } catch (KeeperException | InterruptedException e) {
                                e.printStackTrace();
                            }
                        });
            } catch (IOException | InterruptedException | KeeperException e) {
                LOGGER.error("Unable to initiate Zookeeper state.", e);
                throw new RuntimeException("Unable to initiate Zookeeper state.", e);
            }
        }
        requestEnvServiceUpdate();
        LOGGER.info("Haproxy-agent started.");
    }

    private void requestEnvServiceUpdate(){
        Set<Service> services = new HashSet<>();
        Service marathon = requestMarathonUpdateState();
        if (marathon != null) {
            services.add(marathon);
        }
        Service mesos = requestMesosUpdateState();
        if (mesos != null) {
            services.add(mesos);
        }
        if (CollectionUtils.isNotEmpty(services)) {
            ActorRef akkaEndpoint = actorSystem.actorFor(EndpointActor.PATH);
            LOGGER.info("Update {} access requested.", services);
            akkaEndpoint.tell(new HaProxyPersistenceStateActor.ServiceMayUpdateMsg("env", services), ActorRef.noSender());
        }
    }

    private Service requestMarathonUpdateState() {
        if (applicationConfig.exposeMarathon()) {
            String url = marathonConfig.url();
            Matcher matcher = Pattern.compile("^http://(.*):(.*)$").matcher(url);
            if (matcher.matches()) {

                Map<String, String> labels = new HashMap<>();
                PortDefinition portDefinition = new PortDefinition(PortDefinition.Protocol.TCP, PortDefinition.Type.HTTPS, 8080, 8080, 0, labels);
                return new Service("marathon", matcher.group(1), Integer.parseInt(matcher.group(2)), portDefinition);

            }
        }
        return null;
    }

    private Service requestMesosUpdateState() {
        if (applicationConfig.exposeMesos()) {
            String url = mesosConfig.url();
            LOGGER.debug("Update Mesos state to url {}", url);
            Matcher matcher = Pattern.compile("^(.*):(.*)$").matcher(url);
            if (matcher.matches()) {
                Map<String, String> labels = new HashMap<>();
                PortDefinition portDefinition = new PortDefinition(PortDefinition.Protocol.TCP, PortDefinition.Type.HTTPS, 5050, 5050, 0, labels);
                return new Service("mesos", matcher.group(1), Integer.parseInt(matcher.group(2)), portDefinition);
            }

        }
        return null;
    }

    @Override
    public void process(WatchedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must be defined.");
        }
        LOGGER.trace("Receive event: {}", event);
        if (event.getType() != Event.EventType.None) {
            if (event.getPath().equals(MARATHON_STATE) ||
                    event.getPath().startsWith(TASKS_PATH_BEGIN)) {

                switch (event.getType()) {
                    case NodeCreated:
                    case NodeDataChanged:
                        try {
                            zooKeeper.exists(event.getPath(), this);

                            sendServiceEvent(event, event.getPath());
                        } catch (KeeperException | InterruptedException e) {
                            e.printStackTrace();
                        }
                        break;
                    case NodeDeleted:
                        synchronized (monitor) {
                            try {
                                List<String> zooKeeperChildren = zooKeeper.getChildren("/marathon/state", this);
                                zooKeeperChildren.stream()
                                        .filter(c -> c.startsWith("task:"))
                                        .filter(c -> appAndTasks.contains(c))
                                        .forEach(c -> {
                                            LOGGER.debug("Handle deleted task event for path : {}", c);
                                            appAndTasks.remove(c);
                                            String servicePath = MARATHON_STATE + "/" + c;
                                            try {
                                                sendServiceEvent(event, servicePath);
                                            } catch (KeeperException | InterruptedException e) {
                                                e.printStackTrace();
                                            }

                                        });
                            } catch (KeeperException | InterruptedException e) {
                                LOGGER.error("Unable to list children for node {}.", MARATHON_STATE, e);
                            }
                        }
                        break;
                    case NodeChildrenChanged:
                        synchronized (monitor) {
                            try {
                                List<String> zooKeeperChildren = zooKeeper.getChildren("/marathon/state", this);
                                zooKeeperChildren.stream()
                                        .filter(c -> c.startsWith("task:"))
                                        .filter(c -> !appAndTasks.contains(c))
                                        .forEach(c -> {
                                            LOGGER.debug("Handle new task event for path : {}", c);
                                            appAndTasks.add(c);
                                            try {
                                                String servicePath = MARATHON_STATE + "/" + c;
                                                zooKeeper.exists(servicePath, this);
                                                sendServiceEvent(event, servicePath);
                                            } catch (KeeperException | InterruptedException e) {
                                                e.printStackTrace();
                                            }

                                        });
                            } catch (KeeperException | InterruptedException e) {
                                LOGGER.error("Unable to list children for node {}.", MARATHON_STATE, e);
                            }
                        }
                        break;
                }
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Following Zookeeper event had been filtered: {}", event);
            }
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Ignore following event :{}", event);
        }
    }

    private void sendServiceEvent(WatchedEvent event, String servicePath) throws KeeperException, InterruptedException {
        Matcher matcher = TASK_PATTERN.matcher(servicePath);
        Stat stat = zooKeeper.exists(servicePath, this);
        if (matcher.matches() && matcher.groupCount() == 1) {
            if (stat == null) {
                LOGGER.info("Receive a DELETE Zookeeper service node {}.", servicePath);
            } else {
                LOGGER.info("Receive a {} Zookeeper service node {}.", event.getType() == Event.EventType.NodeCreated ? "CREATION" : "UPDATE", servicePath);
            }
            actorSystem.actorFor(EndpointActor.PATH).tell(new ZookeeperEventHandlerActor.ZookeeperEventMsg(event.getPath(), matcher.group(1)), ActorRef.noSender());
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Service path {} not match regExp '{}'", servicePath, TASK_PATTERN.pattern());
        }

    }
}
