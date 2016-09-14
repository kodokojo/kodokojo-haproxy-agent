package io.kodokojo.ha.service.haproxy;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang.StringUtils.isBlank;

public class DefaultHaproxyUpdater implements HaproxyUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHaproxyUpdater.class);

    private static final String CONFIGURATION_PATH = "/usr/local/etc/haproxy/haproxy.cfg";

    private static final String CERTIFICATES_PATH = "/usr/local/etc/haproxy/ssl/";

    private static final String NEW_CONFIGURATION_PATH = "/usr/local/etc/haproxy/newHaproxy.cfg";

    private static final String PID_PATH = "/tmp/haproxy.pid";

    private static final String USR_LOCAL_SBIN_HAPROXY = "/usr/sbin/haproxy";

    private static final String UPDATE_CONFIGURATION_CMD_FORMAT = USR_LOCAL_SBIN_HAPROXY + "-D -f " + CONFIGURATION_PATH + " -p " + PID_PATH + " -sf %s";

    private static final String[] VALIDATE_NEW_CONFIGURATION_CMD_A = {"-f ", NEW_CONFIGURATION_PATH, "-c"};



    public DefaultHaproxyUpdater() {
        checkOrCreate(CONFIGURATION_PATH);
        checkOrCreate(NEW_CONFIGURATION_PATH);
    }

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

            Process process = executeCommand(VALIDATE_NEW_CONFIGURATION_CMD_A);
            boolean res = (process != null && process.exitValue() == 0);
            if (res) {
                LOGGER.info("Haproxy configuration file '{}' validated.", NEW_CONFIGURATION_PATH);
            } else if (process != null){
                String errOutput = IOUtils.toString(process.getErrorStream());
                String stdOutput = IOUtils.toString(process.getInputStream());

                LOGGER.warn("Fail to validate Haproxy configuration with command '{}':\n{}Err:\nStd:{}", VALIDATE_NEW_CONFIGURATION_CMD_A, errOutput, stdOutput);
                FileInputStream configInput = new FileInputStream(NEW_CONFIGURATION_PATH);
                LOGGER.warn("'{}' content :\n{}", NEW_CONFIGURATION_PATH, IOUtils.toString(configInput));
                IOUtils.closeQuietly(configInput);
            }
            return res;
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

            String commandline = String.format(UPDATE_CONFIGURATION_CMD_FORMAT, pid);
            Process process = executeCommand(new String[]{USR_LOCAL_SBIN_HAPROXY, commandline});
            boolean res = (process != null && process.exitValue() == 0);
            if (res) {
                LOGGER.info("Haproxy configuration file '{}' updated.", CERTIFICATES_PATH);
            } else if (process != null){
                String stdOutput = IOUtils.toString(process.getErrorStream());
                LOGGER.warn("Fail to reload Haproxy configuration with command '{}':\n{}", commandline, stdOutput);
            }
            return res;
        } catch (IOException e) {
            LOGGER.error("Unable to write on file {}.: {}", NEW_CONFIGURATION_PATH, e);
        }
        return false;
    }

    protected Process executeCommand(String[] args) {

        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        List<String> commandLine = new ArrayList<>();
        commandLine.add(USR_LOCAL_SBIN_HAPROXY);
        commandLine.addAll(Arrays.asList(args));

        try {
            process = runtime.exec(commandLine.toArray(new String[commandLine.size()]));
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String errorOutput = IOUtils.toString(process.getErrorStream());
                LOGGER.warn("Command line '{}' return code {} :\n{}", USR_LOCAL_SBIN_HAPROXY + " " +args, exitCode, errorOutput);
            }
        } catch (IOException e) {
            LOGGER.error("Unable to run command line '{}'.", args);

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

    private void checkOrCreate(String path) {
        File file = new File(path);
        LOGGER.debug("Check file '{}' exist ", path);
        if (!file.exists() ) {
            LOGGER.debug("File '{}' not exist.", path);
            File parentFile = file.getParentFile();
            LOGGER.debug("Try to create dir '{}'.", parentFile.getAbsolutePath());
            parentFile.mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                LOGGER.error("Unable to create file '{}'.", path);
            }
        }
    }
}
