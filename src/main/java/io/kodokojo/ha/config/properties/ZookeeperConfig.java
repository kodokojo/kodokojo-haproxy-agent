package io.kodokojo.ha.config.properties;

public interface ZookeeperConfig extends PropertyConfig {

    @Key("zookeeper.url")
    String url();
}
