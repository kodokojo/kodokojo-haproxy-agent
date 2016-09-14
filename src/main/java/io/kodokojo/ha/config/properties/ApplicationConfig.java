package io.kodokojo.ha.config.properties;

public interface ApplicationConfig extends PropertyConfig {

    @Key(value = "env", defaultValue = "env")
    String env();

    @Key(value = "mock.haproxy", defaultValue = "false")
    Boolean mockHaproxy();
}
