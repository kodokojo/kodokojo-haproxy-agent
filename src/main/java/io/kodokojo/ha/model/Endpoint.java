package io.kodokojo.ha.model;

import org.apache.commons.lang.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.isBlank;

public class Endpoint {

    private String name;

    private int portIndex;

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
        res.addAll(getServiceNames("HTTP"));
        res.addAll(getServiceNames("HTTPS"));
        return res;
    }

    public Set<String> getServiceNames(String typeStr) {
        PortDefinition.Type typeCriteria = PortDefinition.Type.valueOf(typeStr.trim().toUpperCase());
        Set<String> res = services.stream()
                .filter(s -> s.getPortDefinition().getProtocol().equals(PortDefinition.Protocol.TCP))
                .filter(s -> s.getPortDefinition().getType() == typeCriteria)
                .map(s -> s.getName()).collect(Collectors.toSet());
        Set<String> addByLabel = services.stream()
                .filter(s -> s.getPortDefinition().getProtocol().equals(PortDefinition.Protocol.TCP))
                .filter(s -> s.getPortDefinition().getType() == typeCriteria)
                .filter(s -> s.getPortDefinition().getLabels().containsKey("frontName"))
                .map(s -> s.getPortDefinition().getLabels().get("frontName"))
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
