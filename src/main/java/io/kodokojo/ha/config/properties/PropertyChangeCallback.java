package io.kodokojo.ha.config.properties;

public interface PropertyChangeCallback {

    void changePropertyValue(String key, Object newValue);

}
