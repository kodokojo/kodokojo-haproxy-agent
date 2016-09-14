package io.kodokojo.ha.service.marathon;

import io.kodokojo.ha.model.Service;

import java.util.Set;

public interface MarathonServiceLookup {

    Set<Service> lookup(String appId);

}
