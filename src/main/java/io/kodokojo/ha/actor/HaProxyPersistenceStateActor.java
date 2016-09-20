package io.kodokojo.ha.actor;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kodokojo.ha.config.properties.ApplicationConfig;
import io.kodokojo.ha.config.properties.ZookeeperConfig;
import io.kodokojo.ha.model.Endpoint;
import io.kodokojo.ha.model.Service;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.io.IOUtils;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import static akka.event.Logging.getLogger;
import static org.apache.commons.lang.StringUtils.isBlank;

public class HaProxyPersistenceStateActor extends AbstractActor {

    public static final String KODOKOJO_PORT_INDEX = "/kodokojo/portIndex";

    public static final Props PROPS(ApplicationConfig applicationConfig, ZookeeperConfig zookeeperConfig) {
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig must be defined.");
        }
        if (zookeeperConfig == null) {
            throw new IllegalArgumentException("zookeeperConfig must be defined.");
        }
        return Props.create(HaProxyPersistenceStateActor.class, applicationConfig, zookeeperConfig);
    }

    private static final String KODOKOJO_CONFIG = "/kodokojo/config";

    private static final String KODOKOJO_SSL = "/kodokojo/ssl";

    private static final String KODOKOJO_SERVICES = "/kodokojo/services";

    private final LoggingAdapter LOGGER = getLogger(getContext().system(), this);

    private final ZooKeeper zooKeeper;

    private String envName;

    private String sslCertificat;

    private Watcher watcher;

    public HaProxyPersistenceStateActor(ApplicationConfig applicationConfig, ZookeeperConfig zookeeperConfig) {
        if (isBlank(zookeeperConfig.url())) {
            throw new IllegalArgumentException("(zookeeperConfig.url()) must be defined.");
        }
        LOGGER.info("try to connect to zookeeper url {}", zookeeperConfig.url());
        try {
            zooKeeper = new ZooKeeper(zookeeperConfig.url(), 1000, event -> {
            }, false);
            watcher = new ZookeeperWatcher();
            envName = applicationConfig.env();
            Stat stat = zooKeeper.exists(KODOKOJO_CONFIG, watcher);
            if (stat == null) {
                stat = zooKeeper.exists("/kodokojo", watcher);
                if (stat == null) {
                    zooKeeper.create("/kodokojo", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                }


                zooKeeper.create(KODOKOJO_CONFIG, ("{\"env\":\"" + applicationConfig.env() + "\"}").getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                byte[] data = zooKeeper.getData(KODOKOJO_CONFIG, watcher, stat);
                extractConfigFromData(data);
            }
            stat = zooKeeper.exists(KODOKOJO_SERVICES, watcher);
            if (stat == null) {
                zooKeeper.create(KODOKOJO_SERVICES, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
            stat = zooKeeper.exists(KODOKOJO_SSL, watcher);
            if (stat == null) {
                LOGGER.warning("Not SSL certificate provided. Try to use a default ssl certificate.");
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream("ssl/server.pem");
                sslCertificat = IOUtils.toString(inputStream);
                org.apache.commons.io.IOUtils.closeQuietly(inputStream);
                zooKeeper.create(KODOKOJO_SSL, sslCertificat.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                byte[] data = zooKeeper.getData(KODOKOJO_SSL, watcher, stat);
                sslCertificat = new String(data);
                LOGGER.info("Using an unique wildcard certificate.");
            }

        } catch (IOException | InterruptedException | KeeperException e) {
            throw new RuntimeException("unable to connect to Zookeeper url " + zookeeperConfig.url(), e);
        }
        receive(ReceiveBuilder
                .match(HaProxyZookeeperEventMsg.class, msg -> {
                    if (msg.event.getType() == Watcher.Event.EventType.NodeDataChanged && msg.event.getPath().equals(KODOKOJO_CONFIG)) {
                        Stat stat = zooKeeper.exists(KODOKOJO_CONFIG, watcher);
                        byte[] data = zooKeeper.getData(KODOKOJO_CONFIG, watcher, stat);
                        extractConfigFromData(data);
                    }
                })
                .match(ServiceMayUpdateMsg.class, msg -> {
                    String[] split = msg.appId.split("_");
                    String endPointName = envName;
                    String serviceName = msg.appId;
                    if (split.length == 2) {
                        endPointName = split[0];
                        serviceName = split[1];
                    }
                    String path = KODOKOJO_SERVICES + "/" + endPointName;
                    Stat stat = zooKeeper.exists(path, watcher);
                    if (stat == null) {
                        int portIndex = generateNewPortIndex();
                        Endpoint endpoint = new Endpoint(endPointName, portIndex, msg.services, sslCertificat);
                        byte[] data = serialize(endpoint);
                        zooKeeper.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

                        getContext().actorFor(EndpointActor.PATH).tell(new HaProxyConfigurationStateActor.ProjectUpdateMsg(msg, endpoint), self());

                    } else {
                        byte[] data = zooKeeper.getData(path, watcher, stat);
                        Endpoint initialEndpoint = deserialize(Endpoint.class, data);


                        initialEndpoint.getServices().clear();
                        initialEndpoint.getServices().addAll(msg.services);
                        byte[] dataToSave = serialize(initialEndpoint);
                        zooKeeper.setData(path, dataToSave, stat.getVersion());
                        getContext().actorFor(EndpointActor.PATH).tell(new HaProxyConfigurationStateActor.ProjectUpdateMsg(msg, initialEndpoint), self());
                    }

                })
                .matchAny(this::unhandled).build());
    }

    protected final int generateNewPortIndex() {
        try {
            Stat stat = zooKeeper.exists(KODOKOJO_PORT_INDEX, watcher);
            Integer port = 1;
            if (stat == null) {
                zooKeeper.create(KODOKOJO_PORT_INDEX, port.toString().getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                byte[] data = zooKeeper.getData(KODOKOJO_PORT_INDEX, watcher, stat);
                int version = stat.getVersion();
                port = Integer.parseInt(new String(data));
                port++;
                zooKeeper.setData(KODOKOJO_PORT_INDEX, port.toString().getBytes(), version);

            }
            return port;
        } catch (KeeperException | InterruptedException e) {
            LOGGER.error("Unable to get project index: {}", e);
        }
        return -1;
    }

    private static <T> byte[] serialize(Object data) {
        Gson gson = new Gson();
        String json = gson.toJson(data);
        return json.getBytes();
    }

    private static <T> T deserialize(Class<T> classe, byte[] data) {
        Gson gson = new Gson();
        return gson.fromJson(new String(data), classe);
    }

    private void extractConfigFromData(byte[] data) {
        String configStr = new String(data);
        JsonParser parser = new JsonParser();
        JsonObject json = (JsonObject) parser.parse(configStr);
        if (org.apache.commons.lang.StringUtils.isBlank(envName) || "defaultenv".equals(envName)) {
            envName = json.getAsJsonPrimitive("env").getAsString().trim();
        }
    }

    public static class ServiceMayUpdateMsg {

        private final String appId;

        private final Set<Service> services;

        public ServiceMayUpdateMsg(String appId, Set<Service> services) {
            if (isBlank(appId)) {
                throw new IllegalArgumentException("appId must be defined.");
            }
            if (services == null) {
                throw new IllegalArgumentException("services must be defined.");
            }
            this.appId = appId;
            this.services = services;
        }

        public String getAppId() {
            return appId;
        }

        public Set<Service> getServices() {
            return services;
        }
    }

    private class ZookeeperWatcher implements Watcher {
        @Override
        public void process(WatchedEvent event) {
            if (event.getType() != Event.EventType.None) {
                getContext().actorFor(EndpointActor.PATH).tell(new HaProxyZookeeperEventMsg(event), ActorRef.noSender());
            }
        }
    }

    public static class HaProxyZookeeperEventMsg {

        private final WatchedEvent event;

        private HaProxyZookeeperEventMsg(WatchedEvent event) {
            this.event = event;
        }
    }

}
