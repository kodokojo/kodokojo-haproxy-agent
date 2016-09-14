package io.kodokojo.ha.service.haproxy;

import java.util.Map;

public interface HaproxyUpdater {

    boolean validateConfiguration(String configurationFileContent, Map<String, String> sslCertificate);

    boolean updateConfiguration(String configurationFileContent, Map<String, String> sslCertificate);

}
