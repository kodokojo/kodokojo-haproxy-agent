package io.kodokojo.ha;

import akka.actor.ActorSystem;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.kodokojo.ha.actor.EndpointActor;
import io.kodokojo.ha.config.AkkaModule;
import io.kodokojo.ha.config.PropertyModule;
import io.kodokojo.ha.config.ServiceModule;
import io.kodokojo.ha.config.properties.VersionConfig;
import io.kodokojo.ha.config.properties.ZookeeperConfig;
import io.kodokojo.ha.service.zookeeper.ZookeeperMarathonRootStateWatcher;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) {

        Injector injector = Guice.createInjector(new PropertyModule(args), new AkkaModule(), new ServiceModule());

        VersionConfig versionConfig = injector.getInstance(VersionConfig.class);
        LOGGER.info("Starting Haproxy-agent version {} on branch {} and commit {}.", versionConfig.version(), versionConfig.branch(), versionConfig.gitSha1());

        ActorSystem actorSystem = injector.getInstance(ActorSystem.class);
        ZookeeperConfig zookeeperConfig = injector.getInstance(ZookeeperConfig.class);
        if (StringUtils.isBlank(zookeeperConfig.url())) {
            LOGGER.error("Unable to start whiteout Zookeeper URL");
            actorSystem.shutdown();
            System.exit(-1);
        }

        actorSystem.actorOf(EndpointActor.PROPS(injector), "endpoint");

        LOGGER.info("Starting zookeeper watcher on Zookeeper url {}.", zookeeperConfig.url());
        ZookeeperMarathonRootStateWatcher instance = injector.getInstance(ZookeeperMarathonRootStateWatcher.class);
        instance.start();


    }

}
