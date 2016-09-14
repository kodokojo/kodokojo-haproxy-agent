package io.kodokojo.ha.config;

import akka.actor.ActorSystem;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.kodokojo.ha.config.properties.ApplicationConfig;
import io.kodokojo.ha.config.properties.MarathonConfig;
import io.kodokojo.ha.config.properties.ZookeeperConfig;
import io.kodokojo.ha.service.haproxy.DefaultHaproxyUpdater;
import io.kodokojo.ha.service.haproxy.HaproxyConfigurationGenerator;
import io.kodokojo.ha.service.haproxy.HaproxyUpdater;
import io.kodokojo.ha.service.haproxy.VelocityHaproxyConfigurationGenerator;
import io.kodokojo.ha.service.marathon.MarathonServiceLookup;
import io.kodokojo.ha.service.marathon.RestMarathonServiceLookup;
import io.kodokojo.ha.service.zookeeper.ZookeeperMarathonRootStateWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ServiceModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceModule.class);

    @Override
    protected void configure() {

    }

    @Provides
    @Singleton
    MarathonServiceLookup provideMarathonServiceLookup(MarathonConfig marathonConfig) {
        return new RestMarathonServiceLookup(marathonConfig);
    }

    @Provides
    @Singleton
    ZookeeperMarathonRootStateWatcher provideZookeeperMarathonRootStateWatcher(ZookeeperConfig zookeeperConfig, ActorSystem actorSystem) {

        return new ZookeeperMarathonRootStateWatcher(zookeeperConfig.url(), actorSystem);
    }

    @Provides
    @Singleton
    HaproxyConfigurationGenerator provideHaproxyConfigurationGenerator() {
        return new VelocityHaproxyConfigurationGenerator();
    }

    @Provides
    @Singleton
    HaproxyUpdater provideHaproxyUpdater(ApplicationConfig applicationConfig) {
        if (applicationConfig.mockHaproxy()) {

            return new HaproxyUpdater() {
                @Override
                public boolean validateConfiguration(String configurationFileContent, Map<String, String> sslCertificate) {
                    LOGGER.error("HaproxyUpdater.validateConfiguration() not yet implemented");
                    LOGGER.error(configurationFileContent);
                    return true;
                }

                @Override
                public boolean updateConfiguration(String configurationFileContent, Map<String, String> sslCertificate) {
                    LOGGER.error("HaproxyUpdater.updateConfiguration() not yet implemented");
                    return true;
                }
            };
        }
        return  new DefaultHaproxyUpdater();
    }

}
