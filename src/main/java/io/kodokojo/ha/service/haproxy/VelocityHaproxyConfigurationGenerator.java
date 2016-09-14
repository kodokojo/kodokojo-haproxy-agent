package io.kodokojo.ha.service.haproxy;

import io.kodokojo.ha.model.Endpoint;
import io.kodokojo.ha.model.Service;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.io.StringWriter;
import java.util.Properties;
import java.util.Set;

public class VelocityHaproxyConfigurationGenerator implements HaproxyConfigurationGenerator {

    private static final Properties VE_PROPERTIES = new Properties();

    static {
        VE_PROPERTIES.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        VE_PROPERTIES.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        VE_PROPERTIES.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogChute");
    }

    public VelocityHaproxyConfigurationGenerator() {

    }

    @Override
    public String generateConfiguration(Set<Endpoint> endpoints, Set<Service> services) {
        VelocityEngine ve = new VelocityEngine();
        ve.init(VE_PROPERTIES);

        Template template = ve.getTemplate("templates/haproxy.conf.vm");

        VelocityContext context = new VelocityContext();
        context.put("endpoints", endpoints);
        context.put("services", services);
        context.put("initialSshPort", 10022);
        context.put("initialTcpPort", 15000);
        StringWriter sw = new StringWriter();
        template.merge(context, sw);
        return sw.toString();
    }
}
