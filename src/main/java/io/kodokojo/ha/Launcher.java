package io.kodokojo.ha;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.kodokojo.ha.actor.EndpointActor;
import io.kodokojo.ha.config.AkkaModule;
import io.kodokojo.ha.config.PropertyModule;
import io.kodokojo.ha.config.ServiceModule;
import io.kodokojo.ha.service.zookeeper.ZookeeperMarathonRootStateWatcher;

public class Launcher {

    public static void main(String[] args) {

        Injector injector = Guice.createInjector(new PropertyModule(args), new AkkaModule(), new ServiceModule());

        ActorSystem actorSystem = injector.getInstance(ActorSystem.class);
        ActorRef endpoint = actorSystem.actorOf(EndpointActor.PROPS(injector), "endpoint");

        try {
            ZookeeperMarathonRootStateWatcher instance = injector.getInstance(ZookeeperMarathonRootStateWatcher.class);
            instance.start();
            while (true) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

}
