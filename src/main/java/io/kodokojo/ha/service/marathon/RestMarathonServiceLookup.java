package io.kodokojo.ha.service.marathon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.kodokojo.ha.config.properties.MarathonConfig;
import io.kodokojo.ha.config.properties.PropertyChangeCallback;
import io.kodokojo.ha.model.PortDefinition;
import io.kodokojo.ha.model.Service;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit.RestAdapter;
import retrofit.RetrofitError;

import java.util.*;

import static org.apache.commons.lang.StringUtils.isBlank;

public class RestMarathonServiceLookup implements MarathonServiceLookup {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestMarathonServiceLookup.class);

    private static final String MANAGED_BY_KODO_KOJO_HA = "managedByKodoKojoHa";

    private static final String LABELS = "labels";

    private static final String APP = "app";

    private MarathonRestApi marathonRestApi;

    public RestMarathonServiceLookup(MarathonConfig marathonConfig) {
        if (marathonConfig == null) {
            throw new IllegalArgumentException("marathonConfig must be defined.");
        }
        marathonConfig.registerCallback((key, newValue) -> {
            if ("marathon.url".equals(key)) {
                String url = (String) newValue;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Marathon url changed to {}.", url);
                }
                marathonRestApi = provideMarathonRestApi(url, marathonConfig.user(), marathonConfig.password());
            }
        });
        marathonRestApi = provideMarathonRestApi(marathonConfig.url(), marathonConfig.user(), marathonConfig.password());
        LOGGER.info("Marathon Service locator plugged on {}.", marathonConfig.url());
    }

    protected MarathonRestApi provideMarathonRestApi(String marathonUrl, String user, String password) {
        RestAdapter.Builder builder = new RestAdapter.Builder().setEndpoint(marathonUrl);
        if (StringUtils.isNotBlank(user)) {
            String basicAuthenticationValue = "Basic " + Base64.getEncoder().encodeToString(String.format("%s:%s", user, password).getBytes());
            builder.setRequestInterceptor(request -> request.addHeader("Authorization", basicAuthenticationValue));
        }
        RestAdapter adapter = builder.build();
        return adapter.create(MarathonRestApi.class);
    }

    @Override
    public Set<Service> lookup(String appId) {
        if (isBlank(appId)) {
            throw new IllegalArgumentException("appId must be defined.");
        }
        Set<Service> res = new HashSet<>();
        try {
            JsonObject marathonResponse = marathonRestApi.getAppdId(appId);
            if (marathonResponse != null) {
                JsonObject app = marathonResponse.getAsJsonObject(APP);
                JsonObject labels = app.getAsJsonObject(LABELS);
                if (labels.has(MANAGED_BY_KODO_KOJO_HA)
                        && labels.getAsJsonPrimitive(MANAGED_BY_KODO_KOJO_HA).getAsBoolean()) {
                    res = extractServicesFromJsonApp(app);
                }
            }
        } catch (RetrofitError e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Retrofit error while lookup appId '{}' : {}", appId, e);
            }
            if (e.getResponse().getStatus() == 404) {
                LOGGER.debug("Unable to found appId '{}'", appId);
            } else {
                LOGGER.error("An error occur while lookup appId '{}‘ [{}]. : {}", appId, e.getUrl(), e);
            }
        }
        return res;
    }

    //Protected for Test.
    protected final Set<Service> extractServicesFromJsonApp(JsonObject app) {
        assert app != null : "app must be defined.";

        Set<Service> res = new HashSet<>();

        List<PortDefinition> portDefinitions = new ArrayList<>();
        String serviceName = "unknown";
        if (app.has("id")) {
            String id = app.getAsJsonPrimitive("id").getAsString();
            String[] split = id.split("/");
            if (split.length > 0) {
                serviceName = split[split.length - 1];
            }
        }
        if (app.has("container")) {
            JsonObject container = app.getAsJsonObject("container");
            if (container.has("docker")) {
                JsonObject docker = container.getAsJsonObject("docker");
                if (docker.has("portMappings")) {
                    JsonArray portMappings = docker.getAsJsonArray("portMappings");
                    for (JsonElement element : portMappings) {
                        PortDefinition portDefinition = extractPortDefinitionFromJson((JsonObject) element);
                        portDefinitions.add(portDefinition);

                    }
                }
            }
        }

        JsonArray tasks = app.getAsJsonArray("tasks");
        for (JsonElement element : tasks) {
            JsonObject task = (JsonObject) element;
            if (task.has("host")) {
                String host = task.getAsJsonPrimitive("host").getAsString();
                JsonArray ports = task.getAsJsonArray("ports");
                for (int i = 0; i < ports.size(); i++) {
                    int exposedPort = ports.get(i).getAsInt();
                    PortDefinition portDefinition = portDefinitions.get(i);
                    if (portDefinition.getType() != PortDefinition.Type.IGNORE) {
                        String portDefServiceName = portDefinition.getLabels().get("serviceName");
                        if (StringUtils.isBlank(portDefServiceName)) {
                            portDefServiceName = serviceName;
                        }
                        res.add(new Service(portDefServiceName, host, exposedPort, portDefinition));
                    }
                }
            }
        }

        return res;
    }

    protected final PortDefinition extractPortDefinitionFromJson(JsonObject portMapping) {
        assert portMapping != null : "portMapping must be defined.";
        int containerPort = portMapping.has("containerPort") ? portMapping.getAsJsonPrimitive("containerPort").getAsInt() : -1;
        int hostPort = portMapping.has("hostPort") ? portMapping.getAsJsonPrimitive("hostPort").getAsInt() : -1;
        int servicePort = portMapping.has("servicePort") ? portMapping.getAsJsonPrimitive("servicePort").getAsInt() : -1;
        PortDefinition.Protocol protocol = portMapping.has("protocol") ? PortDefinition.Protocol.valueOf(portMapping.getAsJsonPrimitive("protocol").getAsString().toUpperCase()) : PortDefinition.Protocol.TCP;
        Map<String, String> labels = new HashMap<>();
        PortDefinition.Type type = PortDefinition.Type.IGNORE;
        if (portMapping.has("labels")) {
            JsonObject labelsJson = portMapping.getAsJsonObject("labels");
            for (Map.Entry<String, JsonElement> entry : labelsJson.entrySet()) {
                String value = entry.getValue().getAsString();
                if (entry.getKey().equals("applicationProtocol")) {
                    type = PortDefinition.Type.valueOf(value);
                } else {
                    labels.put(entry.getKey(), value);
                }
            }
        }
        return new PortDefinition(protocol, type, containerPort, hostPort, servicePort, labels);
    }

}
