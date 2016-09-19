package io.kodokojo.ha.poc;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ZookeeperWatcherLogger implements Watcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperWatcherLogger.class);

    private final ZooKeeper zooKeeper;

    private final Set<String> children = new HashSet<>();

    public ZookeeperWatcherLogger(String zookeeperUrl) throws IOException, KeeperException, InterruptedException {
        zooKeeper = new ZooKeeper(zookeeperUrl, 1000, this);
        List<String> children = zooKeeper.getChildren("/mesos", this);
        children.forEach(c -> {
            try {
                System.out.println(c);
                String path = "/mesos/" + c;
                Stat exists = zooKeeper.exists(path, this);
                byte[] data = zooKeeper.getData(path, this, exists);
                System.out.println(new String(data));
            } catch (KeeperException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        Stat stat = zooKeeper.exists("/mesos", this);
        if (stat != null) {

            List<String> marathonChildren = zooKeeper.getChildren("/mesos", this);
            marathonChildren.stream().forEach(System.out::println);
            byte[] data = zooKeeper.getData("/marathon/leader", this, stat);
            if (data == null) {
                List<String> leaderChildren = zooKeeper.getChildren("/marathon/leader", this);
                leaderChildren.stream().forEach(m -> {
                    String path = "/marathon/leader/" + m;
                    System.out.println("Request " + path);
                    try {
                        Stat exists = zooKeeper.exists(path, this);
                        if (exists != null) {
                            byte[] memberData = zooKeeper.getData(path, this, exists);
                            System.out.println("Member data :" + new String(memberData));

                            List<String> memberChildren = zooKeeper.getChildren(path, this);
                            memberChildren.stream().forEach(member -> System.out.println(path + "/" + member));

                        }
                    } catch (KeeperException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            } else {
                System.out.println("Leader : " + new String(data));
            }
        }
        this.children.addAll(children);
    }

    public static void main(String[] args) {

        String zookeeperUrl = args[0];
        try {
            try {
                ZookeeperWatcherLogger zookeeperWatcherLogger = new ZookeeperWatcherLogger(zookeeperUrl);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (KeeperException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //ZookeeperMarathonRootStateWatcher watcher = new ZookeeperMarathonRootStateWatcher(zookeeperUrl, null);
            while (true) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

        } catch (RuntimeException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void process(WatchedEvent event) {
        LOGGER.info("Receive event : {}", event);
        if (event.getType() != Event.EventType.None) {
            try {
                zooKeeper.exists(event.getPath(), this);
            } catch (KeeperException | InterruptedException e) {
                e.printStackTrace();
            }
            if (event.getType() == Event.EventType.NodeChildrenChanged) {
                try {
                    Collection<String> changedChildren = null;
                    try {
                        changedChildren = CollectionUtils.disjunction(children, zooKeeper.getChildren(event.getPath(), false));
                        changedChildren.stream().filter(c -> c.startsWith("app") || c.startsWith("task")).forEach(c -> {
                            try {
                                String path = event.getPath() + "/" + c;
                                Stat exists = zooKeeper.exists(path, this);
                                if (exists != null) {
                                    children.add(c);
                                    LOGGER.debug("node {} Added.", path);
                                    byte[] data = zooKeeper.getData(path, this, exists);
                                    LOGGER.debug("node {} contain : {}", path, new String(data));
                                    zooKeeper.getChildren(path, this);
                                }
                            } catch (KeeperException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        });
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println(StringUtils.join(changedChildren, ","));

                } catch (KeeperException e) {
                    e.printStackTrace();
                }
            }

            try {
                Stat exists = zooKeeper.exists(event.getPath(), this);
                if (exists != null) {
                    byte[] data = zooKeeper.getData(event.getPath(), this, exists);
                    LOGGER.info("Node data content :\n{}", new String(data));
                }
            } catch (KeeperException | InterruptedException e) {
                LOGGER.error("Unable to request Zk.", e);
            }
        }
    }
}


