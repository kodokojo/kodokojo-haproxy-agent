package io.kodokojo.ha.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.kodokojo.ha.config.properties.*;
import io.kodokojo.ha.config.properties.provider.*;

import java.util.LinkedList;

public class PropertyModule extends AbstractModule {
    private final String[] args;

    public PropertyModule(String[] args) {
        if (args == null) {
            throw new IllegalArgumentException("args must be defined.");
        }
        this.args = args;
    }

    @Override
    protected void configure() {
        //
    }

    @Provides
    @Singleton
    PropertyValueProvider propertyValueProvider() {
        LinkedList<PropertyValueProvider> valueProviders = new LinkedList<>();
        OrderedMergedValueProvider valueProvider = new OrderedMergedValueProvider(valueProviders);

        if (args.length > 0) {
            JavaArgumentPropertyValueProvider javaArgumentPropertyValueProvider = new JavaArgumentPropertyValueProvider(args);
            valueProviders.add(javaArgumentPropertyValueProvider);
        }

        SystemEnvValueProvider systemEnvValueProvider = new SystemEnvValueProvider();
        valueProviders.add(systemEnvValueProvider);

        SystemPropertyValueProvider systemPropertyValueProvider = new SystemPropertyValueProvider();
        valueProviders.add(systemPropertyValueProvider);

        return valueProvider;
    }


    @Provides
    @Singleton
    ApplicationConfig provideApplicationConfig(PropertyValueProvider valueProvider) {
        return createConfig(ApplicationConfig.class, valueProvider);
    }

    @Provides
    @Singleton
    MarathonConfig provideMarathonConfig(ZookeeperConfig zookeeperConfig, PropertyValueProvider valueProvider) {
        MarathonConfigValueProvider marathonConfigValueProvider = new MarathonConfigValueProvider(zookeeperConfig.url(), valueProvider);
        MarathonConfig config = createConfig(MarathonConfig.class, marathonConfigValueProvider);
        marathonConfigValueProvider.setMarathonConfig(config);
        return config;
    }

    @Provides
    @Singleton
    ZookeeperConfig provideZookeeperConfig(PropertyValueProvider valueProvider) {
        return createConfig(ZookeeperConfig.class, valueProvider);
    }

    private <T extends PropertyConfig> T createConfig(Class<T> configClass, PropertyValueProvider valueProvider) {
        PropertyResolver resolver = new PropertyResolver(valueProvider);
        return resolver.createProxy(configClass);
    }
}
