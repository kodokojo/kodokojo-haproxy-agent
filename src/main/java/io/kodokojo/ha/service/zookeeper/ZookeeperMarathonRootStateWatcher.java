package io.kodokojo.ha.service.zookeeper;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import io.kodokojo.ha.actor.EndpointActor;
import io.kodokojo.ha.actor.ZookeeperEventHandlerActor;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang.StringUtils.isBlank;

public class ZookeeperMarathonRootStateWatcher implements Watcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperMarathonRootStateWatcher.class);
    public static final String MARATHON_STATE = "/marathon/state";
    public static final String TASKS_PATH_BEGIN = MARATHON_STATE + "/task:";
    public static final Pattern TASK_PATTERN = Pattern.compile("^\\/marathon\\/state\\/task:(.*)\\..*$");

    private final Set<String> appAndTasks;

    private final String zookeeperUrl;

    private ZooKeeper zooKeeper;

    private final ActorSystem actorSystem;

    private final Object monitor = new Object();

    @Inject
    public ZookeeperMarathonRootStateWatcher(String zooKeeperUrl, ActorSystem actorSystem) {
        if (isBlank(zooKeeperUrl)) {
            throw new IllegalArgumentException("zooKeeperUrl must be defined.");
        }
        if (actorSystem == null) {
            throw new IllegalArgumentException("actorSystem must be defined.");
        }
        this.zookeeperUrl = zooKeeperUrl;
        this.actorSystem = actorSystem;
        this.appAndTasks = new HashSet<>();
        start();
    }

    public synchronized void start() {
        if (zooKeeper == null) {
            try {
                this.zooKeeper = new ZooKeeper(zookeeperUrl, 1000, this);
                List<String> children = zooKeeper.getChildren("/marathon/state", this);
                children.forEach(c -> {
                    try {
                        zooKeeper.exists(MARATHON_STATE + c, this);
                    } catch (KeeperException | InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException | InterruptedException | KeeperException e) {
                LOGGER.error("Unable to initiate Zookeeper state.", e);
                throw new RuntimeException("Unable to initiate Zookeeper state.", e);
            }
        }
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
            actorSystem.actorFor(EndpointActor.PATH).tell(new ZookeeperEventHandlerActor.ZookeeperEventMsg(event, matcher.group(1)), ActorRef.noSender());
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Service path {} not match regExp '{}'", servicePath, TASK_PATTERN.pattern());
        }

    }
}
