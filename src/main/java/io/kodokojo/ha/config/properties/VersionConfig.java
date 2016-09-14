package io.kodokojo.ha.config.properties;

public interface VersionConfig extends PropertyConfig {

    @Key("version")
    String version();

    @Key("gitSha1")
    String gitSha1();

    @Key("branch")
    String branch();

}
