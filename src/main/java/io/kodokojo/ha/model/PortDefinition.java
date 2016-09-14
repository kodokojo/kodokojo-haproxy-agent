package io.kodokojo.ha.model;

import java.util.HashMap;
import java.util.Map;

public class PortDefinition {

    public enum Type {
        HTTP,
        HTTPS,
        WS,
        WSS,
        SSH,
        OTHER,
        IGNORE
    }

    public enum Protocol {
        TCP,
        UDP
    }

    private final Protocol protocol;

    private final Type type;

    private final int containerPort;

    private final int hostPort;

    private final int servicePort;

    private final Map<String, String> labels;

    public PortDefinition(Protocol protocol, Type type, int containerPort, int hostPort, int servicePort, Map<String, String> labels) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol must be defined.");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must be defined.");
        }
        if (labels == null) {
            throw new IllegalArgumentException("labels must be defined.");
        }
        this.protocol = protocol;
        this.type = type;
        this.containerPort = containerPort;
        this.hostPort = hostPort;
        this.servicePort = servicePort;
        this.labels = labels;
    }

    public PortDefinition(int containerPort) {
        this(Protocol.TCP,Type.HTTP, containerPort, -1, -1, new HashMap<>());
    }

    public Type getType() {
        return type;
    }

    public int getContainerPort() {
        return containerPort;
    }

    public int getHostPort() {
        return hostPort;
    }

    public int getServicePort() {
        return servicePort;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PortDefinition that = (PortDefinition) o;

        if (containerPort != that.containerPort) return false;
        if (hostPort != that.hostPort) return false;
        if (servicePort != that.servicePort) return false;
        if (protocol != that.protocol) return false;
        if (type != that.type) return false;
        return labels.equals(that.labels);

    }

    @Override
    public int hashCode() {
        int result = protocol.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + containerPort;
        result = 31 * result + hostPort;
        result = 31 * result + servicePort;
        result = 31 * result + labels.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "PortDefinition{" +
                "protocol=" + protocol +
                ", type=" + type +
                ", containerPort=" + containerPort +
                ", hostPort=" + hostPort +
                ", servicePort=" + servicePort +
                ", labels=" + labels +
                '}';
    }
}
