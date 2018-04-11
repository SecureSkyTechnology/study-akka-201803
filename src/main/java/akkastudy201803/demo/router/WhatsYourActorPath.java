package akkastudy201803.demo.router;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;

public class WhatsYourActorPath extends AbstractActor {
    final ActorRef collector;

    WhatsYourActorPath(final ActorRef collector) {
        this.collector = collector;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().matchAny(m -> {
            try {
                Thread.sleep((int) (Math.random() * 50) + 50);
            } catch (InterruptedException ignore) {
            }
            collector.tell(getSelf().path(), getSelf());
        }).build();
    }
}
