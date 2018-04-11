package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * thread starvation sample from:
 * @see https://doc.akka.io/docs/akka/2.5/dispatchers.html
 */
public class PinnedDispatcherDemo implements Runnable {

    static class PrintActor extends AbstractActor {
        final String name;

        PrintActor(final String name) {
            this.name = name;
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(Integer.class, i -> {
                Thread.sleep(500);
                String tn = Thread.currentThread().getName();
                System.out.println("[" + tn + "] PrintActor (" + name + ") in : " + i);
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            ActorRef actors[] = new ActorRef[5];
            actors[0] = system.actorOf(Props.create(PrintActor.class, "a0").withDispatcher("my-pinned-dispatcher"));
            actors[1] = system.actorOf(Props.create(PrintActor.class, "a1").withDispatcher("my-pinned-dispatcher"));
            actors[2] = system.actorOf(Props.create(PrintActor.class, "a2").withDispatcher("my-pinned-dispatcher"));
            actors[3] = system.actorOf(Props.create(PrintActor.class, "a3").withDispatcher("my-pinned-dispatcher"));
            actors[4] = system.actorOf(Props.create(PrintActor.class, "a4").withDispatcher("my-pinned-dispatcher"));
            for (int i = 0; i < 10; i++) {
                for (ActorRef actor : actors) {
                    actor.tell(i, ActorRef.noSender());
                    Thread.sleep(50);
                }
            }
            System.out.println(">>> Press ENTER to exit <<<");
            br.readLine();
            System.out.println("exiting...");
        } catch (IOException | InterruptedException ignore) {
        } finally {
            system.terminate();
        }
    }
}
