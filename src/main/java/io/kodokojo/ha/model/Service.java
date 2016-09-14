package io.kodokojo.ha.model;

import static org.apache.commons.lang.StringUtils.isBlank;

public class Service {

    private final String name;

    private final String host;

    private final int port;

    private final PortDefinition portDefinition;

    public Service(String name, String host, int port, PortDefinition portDefinition) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("name must be defined.");
        }
        if (portDefinition == null) {
            throw new IllegalArgumentException("portDefinition must be defined.");
        }
        if (isBlank(host)) {
            throw new IllegalArgumentException("host must be defined.");
        }
        this.name =name;
        this.host = host;
        this.port = port;
        this.portDefinition = portDefinition;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public PortDefinition getPortDefinition() {
        return portDefinition;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Service service = (Service) o;

        if (port != service.port) return false;
        if (!name.equals(service.name)) return false;
        if (!host.equals(service.host)) return false;
        return portDefinition.equals(service.portDefinition);

    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + host.hashCode();
        result = 31 * result + port;
        result = 31 * result + portDefinition.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Service{" +
                "name='" + name + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", portDefinition=" + portDefinition +
                '}';
    }
}
