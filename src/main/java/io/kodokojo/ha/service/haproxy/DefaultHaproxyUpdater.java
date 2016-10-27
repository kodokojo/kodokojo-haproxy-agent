package io.kodokojo.ha.service.haproxy;

import io.kodokojo.ha.config.properties.ApplicationConfig;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.isBlank;

public class DefaultHaproxyUpdater implements HaproxyUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHaproxyUpdater.class);

    private static final String CERTIFICATES_PATH = "/usr/local/etc/haproxy/ssl/";

    public static final String WILDCARD_CERTIFICATE_PATH = CERTIFICATES_PATH + "wildcard.pem";

    private static final String CONFIGURATION_PATH = "/usr/local/etc/haproxy/haproxy.cfg";

    private static final String NEW_CONFIGURATION_PATH = "/usr/local/etc/haproxy/newHaproxy.cfg";

    private static final String PID_PATH = "/tmp/haproxy.pid";

    private static final String USR_LOCAL_SBIN_HAPROXY = "/usr/sbin/haproxy";

    private static final String[] UPDATE_NEW_CONFIGURATION_CMD_A = {"-D", "-f", CONFIGURATION_PATH, "-p", PID_PATH, "-sf"};

    private static final String[] VALIDATE_NEW_CONFIGURATION_CMD_A = {"-f ", NEW_CONFIGURATION_PATH, "-c"};

    private final ApplicationConfig applicationConfig;

    public DefaultHaproxyUpdater(ApplicationConfig applicationConfig) {
        requireNonNull(applicationConfig, "applicationConfig must be defined.");
        checkOrCreate(CONFIGURATION_PATH);
        checkOrCreate(NEW_CONFIGURATION_PATH);
        this.applicationConfig = applicationConfig;
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
            boolean res = (process != null && (process.exitValue() == 139 || process.exitValue() == 0));
            if (res) {
                LOGGER.info("Haproxy configuration file '{}' validated.", NEW_CONFIGURATION_PATH);
            } else if (process != null) {
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
            File pidFile = new File(PID_PATH);
            if (!pidFile.exists()) {
                pidFile.getParentFile().mkdirs();
                pidFile.createNewFile();
            }
            InputStream pidInputStream = new FileInputStream(PID_PATH);
            String pid = IOUtils.toString(pidInputStream).replace("\\n", "");
            IOUtils.closeQuietly(pidInputStream);

            List<String> cmd = new ArrayList<>(Arrays.asList(UPDATE_NEW_CONFIGURATION_CMD_A));
            cmd.add(pid);
            Process process = executeCommand(cmd.toArray(new String[cmd.size()]));
            boolean res = (process != null && (process.exitValue() == 139 || process.exitValue() == 0));
            if (res) {
                LOGGER.info("Haproxy configuration file '{}' updated.", CERTIFICATES_PATH);
            } else if (process != null) {
                String errOutput = IOUtils.toString(process.getErrorStream());
                String stdOutput = IOUtils.toString(process.getInputStream());

                LOGGER.warn("Fail to update Haproxy configuration with command '{} {}':\n{}Err:\nStd:{}", USR_LOCAL_SBIN_HAPROXY, StringUtils.join(cmd, " "), errOutput, stdOutput);
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

    protected Process executeCommand(String[] args) {

        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        List<String> commandLine = new ArrayList<>();
        commandLine.add(USR_LOCAL_SBIN_HAPROXY);
        commandLine.addAll(Arrays.asList(args));

        try {
            process = runtime.exec(commandLine.toArray(new String[commandLine.size()]));
            int exitCode = process.waitFor();
            if (exitCode != 0 && exitCode != 139) {
                LOGGER.warn("Command line '{}' return code {}.", USR_LOCAL_SBIN_HAPROXY + " " + StringUtils.join(args, " "), exitCode);
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
        if (applicationConfig.useWildCardCertificat() && MapUtils.isNotEmpty(certificates)) {
            Map.Entry<String, String> firstEntry = certificates.entrySet().iterator().next();
            String certificatePath = WILDCARD_CERTIFICATE_PATH;
            writeCertificateIfRequiered(certificatePath, firstEntry.getValue());

        } else {
            LOGGER.debug("Write certificate {}.", certificates.size());
            for (Map.Entry<String, String> entry : certificates.entrySet()) {
                String certificatePath = CERTIFICATES_PATH + entry.getKey();
                writeCertificateIfRequiered(certificatePath, entry.getValue());
            }
        }
    }

    private void writeCertificateIfRequiered(String certificatePath, String content) {
        File certificateFile = new File(certificatePath);
        if (certificateFile.exists() && certificateFile.length() > 0) {
            LOGGER.info("Certificate {} already exist, ignore rewrite.", certificatePath);
        } else {
            try {
                FileOutputStream outputStream = new FileOutputStream(certificatePath, false);
                IOUtils.write(content, outputStream);
                LOGGER.debug("Write certificate {}.", certificatePath);
                IOUtils.closeQuietly(outputStream);
            } catch (IOException e) {
                LOGGER.error("Unable to write certificate {}: {}", certificatePath, e);
            }
        }
    }

    private void checkOrCreate(String path) {
        File file = new File(path);
        LOGGER.debug("Check file '{}' exist ", path);
        if (!file.exists()) {
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
