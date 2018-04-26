package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import scala.concurrent.duration.FiniteDuration;

/**
 * @see https://mikulskibartosz.name/always-stop-unused-akka-actors-a2ceeb1ed41
 */
public class ActorMemoryLeakDemo implements Runnable {

    static class Child extends AbstractActor {
        byte data[] = new byte[20_000_000];

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("produce", m -> {
                Random r = new Random();
                r.nextBytes(data);
            }).build();
        }
    }

    static class Parent extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("produce", m -> {
                getContext().actorOf(Props.create(Child.class)).tell("produce", ActorRef.noSender());
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            final ActorRef actor = system.actorOf(Props.create(Parent.class));

            System.out.println("Try monitoring memory usage by jvisualvm or jmc or other jmx tools :)");
            System.out.println("Also, try '-Xmx' option to limitation max heap, ex: '-Xmx100m'");
            System.out.println("Press ENTER to start memory leak demo :P");
            br.readLine();
            system.scheduler().schedule(
                FiniteDuration.create(2, TimeUnit.SECONDS),
                FiniteDuration.create(200, TimeUnit.MILLISECONDS),
                () -> {
                    actor.tell("produce", ActorRef.noSender());
                },
                system.dispatcher());
            System.out.println("Press ENTER or Ctrl-C to terminate.");
            br.readLine();
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }
}
