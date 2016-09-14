package io.kodokojo.ha.model;

import io.kodokojo.ha.model.Service;

import static org.apache.commons.lang.StringUtils.isBlank;

public class ServiceEvent {

    enum Type {
        STARTING,
        RUNNING,
        STOPPED
    }

    private final String orchectratorId;

    private final String projectName;

    private final String stackName;

    private final String serviceName;

    private final Service service;

    public ServiceEvent(String orchectratorId, String projectName, String stackName, String serviceName, Service service) {
        if (isBlank(orchectratorId)) {
            throw new IllegalArgumentException("orchectratorId must be defined.");
        }
        if (isBlank(serviceName)) {
            throw new IllegalArgumentException("serviceName must be defined.");
        }
        if (service == null) {
            throw new IllegalArgumentException("service must be defined.");
        }
        this.orchectratorId = orchectratorId;
        this.projectName = projectName;
        this.stackName = stackName;
        this.serviceName = serviceName;
        this.service = service;
    }

    public String getOrchectratorId() {
        return orchectratorId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getStackName() {
        return stackName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Service getService() {
        return service;
    }

    @Override
    public String toString() {
        return "ServiceEvent{" +
                "orchectratorId='" + orchectratorId + '\'' +
                ", projectName='" + projectName + '\'' +
                ", stackName='" + stackName + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", service=" + service +
                '}';
    }
}
