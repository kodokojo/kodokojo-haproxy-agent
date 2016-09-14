package io.kodokojo.ha.config.properties.provider;

import io.kodokojo.ha.config.properties.MarathonConfig;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isBlank;

public class MarathonConfigValueProvider extends AbstarctStringPropertyValueProvider implements Watcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarathonConfigValueProvider.class);

    private static final String LEADER_PATH = "/marathon/leader";

    public static final String MARATHON_URL = "marathon.url";

    private final ZooKeeper zooKeeper;

    private final PropertyValueProvider delegate;

    private String previousUrl;

    private MarathonConfig marathonConfig;

    public MarathonConfigValueProvider(String zookeeperUrl, PropertyValueProvider delegate) {
        if (isBlank(zookeeperUrl)) {
            throw new IllegalArgumentException("zookeeperUrl must be defined.");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must be defined.");
        }
        this.delegate = delegate;
        try {
            zooKeeper = new ZooKeeper(zookeeperUrl, 1000, this);
        } catch (IOException e) {
            throw new RuntimeException("Unable to connect to zookeeper " + zookeeperUrl, e);
        }
        Stat exists = null;
        try {
            exists = zooKeeper.exists(LEADER_PATH, this);
        } catch (KeeperException | InterruptedException e) {
            throw new RuntimeException("Unable to check if node " + LEADER_PATH + " exist.", e);
        }
        if (exists != null) {
            previousUrl = provideValue(MARATHON_URL);
        }

    }

    public void setMarathonConfig(MarathonConfig marathonConfig) {
        if (marathonConfig != null) {
            this.marathonConfig = marathonConfig;
        }
    }

    @Override
    protected String provideValue(String key) {
        if (isBlank(key)) {
            throw new IllegalArgumentException("key must be defined.");
        }
        if (MARATHON_URL.equals(key)) {
            try {
                Stat exists = zooKeeper.exists(LEADER_PATH, this);
                if (exists != null) {
                    List<String> members = zooKeeper.getChildren(LEADER_PATH, this);
                    String res = members.stream().findFirst().map(leader -> {
                        String path = LEADER_PATH + "/" + leader;
                        try {
                            Stat memberExist = zooKeeper.exists(path, this);
                            byte[] data = zooKeeper.getData(path, this, memberExist);
                            if (data == null) {
                                LOGGER.warn("No leader url found.");
                            } else {
                                return new String(data);
                            }
                        } catch (KeeperException | InterruptedException e) {
                            LOGGER.error("Unable to extract leader url from node member '{}' : {}", path, e);
                        }
                        return null;
                    }).get();
                    return "http://" + res;
                }
            } catch (KeeperException | InterruptedException e) {
                LOGGER.error("Unable to check if node '{}' exist : {}", LEADER_PATH, e);
            }
        }
        return delegate.providePropertyValue(String.class, key);
    }

    @Override
    public void process(WatchedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must be defined.");
        }
        if (marathonConfig != null && event.getType() != Event.EventType.None && event.getPath().startsWith(LEADER_PATH)) {
            String value = provideValue(MARATHON_URL);
            if (!previousUrl.equals(value)) {
                previousUrl = value;
                marathonConfig.updateProperty(MARATHON_URL, value);
            }
        }
    }
}
