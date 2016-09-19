package io.kodokojo.ha.service.haproxy;

import io.kodokojo.ha.config.properties.ApplicationConfig;
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

    public VelocityHaproxyConfigurationGenerator(ApplicationConfig applicationConfig) {
        if (applicationConfig == null) {
            throw new IllegalArgumentException("applicationConfig must be defined.");
        }
        this.applicationConfig = applicationConfig;
    }

    @Override
    public String generateConfiguration(Set<Endpoint> endpoints, Set<Service> services) {
        if (endpoints == null) {
            throw new IllegalArgumentException("endpoints must be defined.");
        }
        VelocityEngine ve = new VelocityEngine();
        ve.init(VE_PROPERTIES);

        Template template = ve.getTemplate("templates/haproxy.conf.vm");

        VelocityContext context = new VelocityContext();
        context.put("envName", applicationConfig.env());
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
