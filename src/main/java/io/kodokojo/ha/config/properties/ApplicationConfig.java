package io.kodokojo.ha.config.properties;

public interface ApplicationConfig extends PropertyConfig {

    @Key(value = "app.env", defaultValue = "defaultenv")
    String env();

    @Key(value = "mock.haproxy", defaultValue = "false")
    Boolean mockHaproxy();

    @Key(value = "expose.marathon", defaultValue = "true")
    Boolean exposeMarathon();
}
