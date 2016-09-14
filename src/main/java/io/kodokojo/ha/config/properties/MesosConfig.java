package io.kodokojo.ha.config.properties;

public interface MesosConfig extends PropertyConfig {

    @Key("mesos.url")
    String url();

    @PropertyChangeRegister("mesos.url")
    void registerCallback(PropertyChangeCallback callback);

}
