package akkastudy201803.demo.router;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.routing.RoundRobinGroup;

/**
 * @see https://doc.akka.io/docs/akka/2.5/routing.html
 */
public class RoundRobinGroupDemo implements Runnable {

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        try {
            CountDownLatch latch = new CountDownLatch(50);
            final ActorRef collector = system.actorOf(Props.create(ActorPathCollector.class, latch));
            final ActorRef actors[] = new ActorRef[5];
            actors[0] = system.actorOf(Props.create(WhatsYourActorPath.class, collector), "actor-0");
            actors[1] = system.actorOf(Props.create(WhatsYourActorPath.class, collector), "actor-1");
            actors[2] = system.actorOf(Props.create(WhatsYourActorPath.class, collector), "actor-2");
            actors[3] = system.actorOf(Props.create(WhatsYourActorPath.class, collector), "actor-3");
            actors[4] = system.actorOf(Props.create(WhatsYourActorPath.class, collector), "actor-4");
            List<String> actorPaths =
                Arrays.stream(actors).map(actor -> actor.path().toStringWithoutAddress()).collect(Collectors.toList());
            final ActorRef router1 = system.actorOf(new RoundRobinGroup(actorPaths).props(), "router1");
            for (int i = 0; i < 50; i++) {
                router1.tell("hello", ActorRef.noSender());
            }
            System.out.println("counting routed actor-path ...");
            latch.await();
            collector.tell("show", ActorRef.noSender());
        } catch (InterruptedException ignore) {
        } finally {
            system.terminate();
        }
    }
}
