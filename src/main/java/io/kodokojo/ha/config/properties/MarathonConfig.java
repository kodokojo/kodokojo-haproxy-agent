package io.kodokojo.ha.config.properties;

public interface MarathonConfig extends PropertyConfig {

    @Key("marathon.url")
    String url();

    @Key("marathon.user")
    String user();

    @Key("marathon.password")
    String password();

    @PropertyChangeRegister("marathon.url")
    void registerCallback(PropertyChangeCallback callback);

}
