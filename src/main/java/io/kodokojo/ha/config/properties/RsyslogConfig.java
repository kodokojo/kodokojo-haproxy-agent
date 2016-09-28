package io.kodokojo.ha.config.properties;

public interface RsyslogConfig extends PropertyConfig {

    @Key(value = "rsyslog.host", defaultValue = "localhost")
    String host();

    @Key(value = "rsyslog.port", defaultValue = "514")
    String port();

}
