package io.kodokojo.ha.service.haproxy;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;

import static org.apache.commons.lang.StringUtils.isBlank;

public class DefaultHaproxyUpdater implements HaproxyUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHaproxyUpdater.class);

    private static final String CONFIGURATION_PATH = "/usr/local/etc/haproxy/haproxy.cfg";

    private static final String CERTIFICATES_PATH = "/usr/local/etc/haproxy/ssl/";

    private static final String NEW_CONFIGURATION_PATH = "/usr/local/etc/haproxy/newHaproxy.cfg";

    private static final String PID_PATH = "/tmp/haproxy.pid";

    private static final String VALIDATE_NEW_CONFIGURATION_CMD = "/usr/local/sbin/haproxy -c -f " + NEW_CONFIGURATION_PATH;

    private static final String UPDATE_CONFIGURATION_CMD_FORMAT = "/usr/local/sbin/haproxy -D -f " + CONFIGURATION_PATH + " -p " + PID_PATH + " -sf %s";

    @Override
    public boolean validateConfiguration(String configurationFileContent, Map<String, String> sslCertificates) {
        if (isBlank(configurationFileContent)) {
            throw new IllegalArgumentException("configurationFileContent must be defined.");
        }
        if (sslCertificates == null) {
            throw new IllegalArgumentException("sslCertificates must be defined.");
        }
        try {
            FileOutputStream output = new FileOutputStream(NEW_CONFIGURATION_PATH);
            IOUtils.write(configurationFileContent, output);
            IOUtils.closeQuietly(output);
            writeCertificates(sslCertificates);

            Process process = executeCommand(VALIDATE_NEW_CONFIGURATION_CMD);
            return process != null && process.exitValue() == 0;
        } catch (IOException e) {
            LOGGER.error("Unable to write on file {}.: {}", NEW_CONFIGURATION_PATH, e);
        }
        return false;
    }

    @Override
    public boolean updateConfiguration(String configurationFileContent, Map<String, String> sslCertificates) {
        if (isBlank(configurationFileContent)) {
            throw new IllegalArgumentException("configurationFileContent must be defined.");
        }
        if (sslCertificates == null) {
            throw new IllegalArgumentException("sslCertificates must be defined.");
        }
        try {
            FileOutputStream output = new FileOutputStream(CONFIGURATION_PATH);
            IOUtils.write(configurationFileContent, output);
            IOUtils.closeQuietly(output);
            writeCertificates(sslCertificates);
            InputStream pidInputStream = new FileInputStream(PID_PATH);
            String pid = IOUtils.toString(pidInputStream);
            IOUtils.closeQuietly(pidInputStream);

            Process process = executeCommand(String.format(UPDATE_CONFIGURATION_CMD_FORMAT, pid));
            return process != null && process.exitValue() == 0;
        } catch (IOException e) {
            LOGGER.error("Unable to write on file {}.: {}", NEW_CONFIGURATION_PATH, e);
        }
        return false;
    }

    protected Process executeCommand(String commandline) {
        assert StringUtils.isNotBlank(commandline) : "commandLine must be defined.";
        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        try {
            process = runtime.exec(commandline);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorOutput = IOUtils.toString(process.getErrorStream());
                LOGGER.warn("Command line '{}' return code {} :\n{}", commandline, exitCode, errorOutput);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to run command line '{}'.", commandline);

        } catch (InterruptedException e) {
            LOGGER.error("Command line process stopped by thread interruption.", e);
            Thread.currentThread().interrupt();
        }
        return process;
    }

    protected void writeCertificates(Map<String, String> certificates) {
        assert certificates != null : "certificates must be defined.";
        File sslDir = new File(CERTIFICATES_PATH);
        if (!sslDir.exists()) {
            sslDir.mkdirs();
        }

        for (Map.Entry<String, String> entry : certificates.entrySet()) {
            try {
                FileOutputStream outputStream = new FileOutputStream(CERTIFICATES_PATH + entry.getKey(), false);
                IOUtils.write(entry.getValue(), outputStream);
                IOUtils.closeQuietly(outputStream);
            } catch (IOException e) {
                LOGGER.error("Unable to write certificate {}: {}", CERTIFICATES_PATH + entry.getKey(), e);
            }
        }
    }
}
