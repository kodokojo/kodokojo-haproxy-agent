package io.kodokojo.ha.config.properties.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.kodokojo.ha.config.properties.MesosConfig;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.eclipse.jetty.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isBlank;

public class MesosConfigValueProvider extends AbstarctStringPropertyValueProvider implements Watcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MesosConfigValueProvider.class);

    private static final String MESOS_PATH = "/mesos";

    public static final String MESOS_URL = "mesos.url";

    private final ZooKeeper zooKeeper;

    private final PropertyValueProvider delegate;

    private OkHttpClient httpClient;

    private String leadearNodeName;

    private String previousUrl;

    private MesosConfig mesosConfig;

    public MesosConfigValueProvider(String zookeeperUrl, PropertyValueProvider delegate) {
        if (isBlank(zookeeperUrl)) {
            throw new IllegalArgumentException("zookeeperUrl must be defined.");
        }
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must be defined.");
        }
        this.delegate = delegate;
        this.httpClient = new OkHttpClient();
        try {
            zooKeeper = new ZooKeeper(zookeeperUrl, 1000, this);
        } catch (IOException e) {
            throw new RuntimeException("Unable to connect to zookeeper " + zookeeperUrl, e);
        }
        Stat exists = null;
        try {
            exists = zooKeeper.exists(MESOS_PATH, this);
        } catch (KeeperException | InterruptedException e) {
            throw new RuntimeException("Unable to check if node " + MESOS_PATH + " exist.", e);
        }
        if (exists != null) {
            previousUrl = provideValue(MESOS_URL);
        }

    }

    public void setMesosConfig(MesosConfig mesosConfig) {
        if (mesosConfig != null) {
            this.mesosConfig = mesosConfig;
        }
    }

    @Override
    protected String provideValue(String key) {
        if (isBlank(key)) {
            throw new IllegalArgumentException("key must be defined.");
        }
        if (MESOS_URL.equals(key)) {
            try {
                Stat exists = zooKeeper.exists(MESOS_PATH, this);
                if (exists != null) {
                    LOGGER.debug("{} exist.", MESOS_PATH);
                    List<String> members = zooKeeper.getChildren(MESOS_PATH, this);

                    String res = members.stream().map(leader -> {
                        String path = MESOS_PATH + "/" + leader;

                        try {
                            Stat memberExist = zooKeeper.exists(path, this);
                            if (memberExist != null) {
                                LOGGER.debug("{} exist.", path);
                                byte[] data = zooKeeper.getData(path, this, memberExist);
                                if (data == null) {
                                    LOGGER.warn("No leader url found.");
                                } else {
                                    String json = new String(data);
                                    LOGGER.debug("{} contain:\n{}", path, json);
                                    if (StringUtils.isNotBlank(json)) {
                                        return json;
                                    }
                                }
                            }
                        } catch (KeeperException | InterruptedException e) {
                            LOGGER.error("Unable to extract leader url from node member '{}' : {}", path, e);
                        }
                        return "";
                    }).filter(StringUtils::isNotBlank).sorted(String::compareTo).findFirst()
                            .get();
                    if (StringUtil.isNotBlank(res)) {
                        JsonParser parser = new JsonParser();
                        JsonObject json = (JsonObject) parser.parse(res);
                        JsonObject address = json.getAsJsonObject("address");
                        String host = address.getAsJsonPrimitive("ip").getAsString();
                        int port = address.getAsJsonPrimitive("port").getAsInt();

                        String url = "http://" + host + ":" + port;
                        Request request = new Request.Builder().url(url).build();
                        Response response = null;
                        try {
                            response = httpClient.newCall(request).execute();
                            if (response.code() == 307) {
                                String mesosUrl = response.header("Location");
                                if (StringUtils.isBlank(mesosUrl)) {
                                    LOGGER.error("Unable to define Mesos leader Url.");
                                } else {
                                    return mesosUrl;
                                }
                            }
                        } catch (IOException e) {
                            LOGGER.error("Unable to request Mesos on url {}", url, e);
                        } finally {
                            if (response != null) {
                                IOUtils.closeQuietly(response.body());
                            }
                        }


                        LOGGER.debug("Mesos url defined has {}:{}", host, port);
                        return host + ":" + port;
                    }

                } else if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("/mesos node note Exist !");
                }
            } catch (KeeperException | InterruptedException e) {
                LOGGER.error("Unable to check if node '{}' exist : {}", MESOS_PATH, e);
            }
        }
        return delegate.providePropertyValue(String.class, key);
    }

    @Override
    public void process(WatchedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must be defined.");
        }
        if (mesosConfig != null && event.getType() != Event.EventType.None && event.getPath().startsWith(MESOS_PATH)) {
            String value = provideValue(MESOS_URL);
            if (!previousUrl.equals(value)) {
                previousUrl = value;
                mesosConfig.updateProperty(MESOS_URL, value);
            }
        }
    }
}
