package io.kodokojo.ha.config.properties;

public interface ApplicationConfig extends PropertyConfig {

    @Key(value = "app.env", defaultValue = "defaultenv")
    String env();

    @Key(value = "mock.haproxy", defaultValue = "false")
    Boolean mockHaproxy();

    @Key(value = "expose.marathon", defaultValue = "true")
    Boolean exposeMarathon();

    @Key(value = "expose.mesos", defaultValue = "true")
    Boolean exposeMesos();

    @Key(value = "haproxy.template.path", defaultValue = "templates/haproxy.conf.vm")
    String haproxyTemplatePath();

    @Key(value = "app.admin.login")
    String adminLogin();

    @Key(value = "app.admin.password")
    String adminPassword();


}
