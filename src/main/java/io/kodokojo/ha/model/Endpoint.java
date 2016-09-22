package io.kodokojo.ha.model;

import com.google.gson.annotations.Expose;
import org.apache.commons.lang.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isBlank;

public class Endpoint {

    @Expose
    private String name;

    @Expose
    private int portIndex;

    @Expose
    private Set<Service> services;


    private final String certificate;

    public Endpoint(String name, int portIndex, Set<Service> services, String certificate) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("name must be defined.");
        }
        if (services == null) {
            throw new IllegalArgumentException("services must be defined.");
        }
        this.name = name;
        this.portIndex = portIndex;
        this.services = services;
        this.certificate = certificate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPortIndex() {
        return portIndex;
    }

    public void setPortIndex(int portIndex) {
        this.portIndex = portIndex;
    }

    public Set<Service> getServices() {
        return services;
    }

    public void setServices(HashSet<Service> services) {
        if (services != null) {
            this.services = services;
        }
    }

    public Set<String> getHTTPServiceNames() {
        Set<String> res = new HashSet<>();
        res.addAll(getServiceNames("HTTP", null));
        res.addAll(getServiceNames("WS", null));
        return res;
    }

    public Set<String> getHTTPSServiceNames() {
        Set<String> res = getHTTPServiceNames();
        res.addAll(getServiceNames("HTTPS", null));
        res.addAll(getServiceNames("WSS", null));
        return res;
    }

    public Set<String> getOnlyHTTPSServiceNames() {
        Set<String> res = getServiceNames("HTTP", null);
        res.addAll(getServiceNames("HTTPS", null));
        return res;
    }

    public Set<String> getServiceNameByType(String typeStr) {
        return getServiceNames(typeStr, null);
    }

    public Set<String> getServiceNames(String typeStr, String name) {
        PortDefinition.Type typeCriteria = PortDefinition.Type.valueOf(typeStr.trim().toUpperCase());
        Set<String> res = services.stream()
                .filter(s -> s.getPortDefinition().getProtocol().equals(PortDefinition.Protocol.TCP))
                .filter(s -> s.getPortDefinition().getType() == typeCriteria)
                .filter(s -> StringUtils.isBlank(name) || name.equals(s.getName()))
                .map(Service::getName).collect(Collectors.toSet());
        Set<String> addByLabel = services.stream()
                .filter(s -> s.getPortDefinition().getProtocol().equals(PortDefinition.Protocol.TCP))
                .filter(s -> s.getPortDefinition().getType() == typeCriteria)
                .filter(s -> s.getPortDefinition().getLabels().containsKey("frontName"))
                .filter(s -> StringUtils.isBlank(name) || name.equals(s.getPortDefinition().getLabels().get("frontName")))
                .map(s -> s.getPortDefinition().getLabels().get("frontName"))
                .collect(Collectors.toSet());
        res.addAll(addByLabel);
        return res;
    }

    public Set<Service> getServicesByTypeAndNames(String typeStr, String name) {
        PortDefinition.Type typeCriteria = PortDefinition.Type.valueOf(typeStr.trim().toUpperCase());
        Set<Service> res = services.stream()
                .filter(s -> s.getPortDefinition().getProtocol().equals(PortDefinition.Protocol.TCP))
                .filter(s -> s.getPortDefinition().getType() == typeCriteria)
                .filter(s -> StringUtils.isBlank(name) || name.equals(s.getName()))
                .collect(Collectors.toSet());
        Set<Service> addByLabel = services.stream()
                .filter(s -> s.getPortDefinition().getProtocol().equals(PortDefinition.Protocol.TCP))
                .filter(s -> s.getPortDefinition().getType() == typeCriteria)
                .filter(s -> s.getPortDefinition().getLabels().containsKey("frontName"))
                .filter(s -> StringUtils.isBlank(name) || name.equals(s.getPortDefinition().getLabels().get("frontName")))
                .collect(Collectors.toSet());
        res.addAll(addByLabel);
        return res;
    }

    public Set<Service> getServiceByName(String name) {
        System.out.println("try to find service named '" + name + "' in list " + StringUtils.join(services.stream().map(Service::getName).collect(Collectors.toList()), ","));
        Set<Service> res = services.stream()
                .filter(s -> s.getPortDefinition().getProtocol().equals(PortDefinition.Protocol.TCP))
                .filter(s -> s.getName().equals(name) || name.equals(s.getPortDefinition().getLabels().get("frontName")))
                .collect(Collectors.toSet());
        return res;
    }

    public String getCertificate() {
        return certificate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Endpoint endpoint = (Endpoint) o;

        if (portIndex != endpoint.portIndex) return false;
        if (!name.equals(endpoint.name)) return false;
        return services.equals(endpoint.services);

    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + portIndex;
        result = 31 * result + services.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Endpoint{" +
                "name='" + name + '\'' +
                ", portIndex=" + portIndex +
                ", services=" + services +
                ", certificate='" + certificate + '\'' +
                '}';
    }
}
