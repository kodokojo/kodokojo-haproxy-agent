package io.kodokojo.ha.service.haproxy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.kodokojo.ha.config.properties.ApplicationConfig;
import io.kodokojo.ha.config.properties.RsyslogConfig;
import io.kodokojo.ha.model.Endpoint;
import io.kodokojo.ha.model.Service;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.Properties;
import java.util.Set;

public class VelocityHaproxyConfigurationGenerator implements HaproxyConfigurationGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(VelocityHaproxyConfigurationGenerator.class);

    private static final Properties VE_PROPERTIES = new Properties();

    static {
        VE_PROPERTIES.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        VE_PROPERTIES.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        VE_PROPERTIES.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogChute");
    }

    private final ApplicationConfig applicationConfig;

    private final RsyslogConfig rsyslogConfig;

    public VelocityHaproxyConfigurationGenerator(ApplicationConfig applicationConfig, RsyslogConfig rsyslogConfig) {
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig must be defined.");
        }
        if (rsyslogConfig == null) {
            throw new IllegalArgumentException("rsyslogConfig must be defined.");
        }
        this.applicationConfig = applicationConfig;
        this.rsyslogConfig = rsyslogConfig;
    }

    @Override
    public String generateConfiguration(Set<Endpoint> endpoints, Set<Service> services) {
        if (endpoints == null) {
            throw new IllegalArgumentException("endpoints must be defined.");
        }

        if(LOGGER.isTraceEnabled()) {
            Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
            String json = gson.toJson(endpoints);
            LOGGER.trace("Generate Haproxy configuration from :\n{}", json);
        }
        VelocityEngine ve = new VelocityEngine();
        ve.init(VE_PROPERTIES);

        Template template = ve.getTemplate(applicationConfig.haproxyTemplatePath());

        VelocityContext context = new VelocityContext();
        context.put("rsyslogHost", rsyslogConfig.host());
        context.put("rsyslogPort", rsyslogConfig.port());
        context.put("envName", applicationConfig.env());
        context.put("adminLogin", applicationConfig.adminLogin());
        context.put("adminPassword", applicationConfig.adminPassword());
        context.put("endpoints", endpoints);
        context.put("services", services);
        context.put("initialSshPort", 10022);
        context.put("initialTcpPort", 15000);
        StringWriter sw = new StringWriter();
        template.merge(context, sw);
        String res = sw.toString();
        return res;
    }
}
