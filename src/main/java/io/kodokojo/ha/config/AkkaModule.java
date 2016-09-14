package io.kodokojo.ha.config;

import akka.actor.ActorSystem;
import com.google.inject.AbstractModule;

public class AkkaModule extends AbstractModule {


    @Override
    protected void configure() {

        ActorSystem actorSystem = ActorSystem.apply("koooo-haproxy-agent");
        //ActorRef deadletterlistener = actorSystem.actorOf(DeadLetterActor.PROPS(), "deadletterlistener");
        //actorSystem.eventStream().subscribe(deadletterlistener, DeadLetter.class);
        bind(ActorSystem.class).toInstance(actorSystem);
    }


}
