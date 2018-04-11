package akkastudy201803.demo.router;

import java.util.concurrent.CountDownLatch;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.routing.RoundRobinPool;

/**
 * @see https://doc.akka.io/docs/akka/2.5/routing.html
 */
public class RoundRobinPoolDemo implements Runnable {

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        try {
            CountDownLatch latch = new CountDownLatch(50);
            final ActorRef collector = system.actorOf(Props.create(ActorPathCollector.class, latch));
            final ActorRef router1 =
                system
                    .actorOf(new RoundRobinPool(5).props(Props.create(WhatsYourActorPath.class, collector)), "router1");
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
