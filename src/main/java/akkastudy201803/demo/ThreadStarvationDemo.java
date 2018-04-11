package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import scala.concurrent.ExecutionContext;

/**
 * thread starvation sample from:
 * @see https://doc.akka.io/docs/akka/2.5/dispatchers.html
 */
public class ThreadStarvationDemo implements Runnable {

    static class BlockingFutureActor extends AbstractActor {
        ExecutionContext ec = getContext().dispatcher();

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(Integer.class, i -> {
                String tn1 = Thread.currentThread().getName();
                System.out.println("[" + tn1 + "]Calling blocking Future in : " + i);
                Futures.future(() -> {
                    Thread.sleep(5000);
                    String tn2 = Thread.currentThread().getName();
                    System.out.println("[" + tn2 + "](Ctrl-C to stop) Blocking future finished in : " + i);
                    return i;
                }, ec);
            }).build();
        }
    }

    static class PrintActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder().match(Integer.class, i -> {
                String tn = Thread.currentThread().getName();
                System.out.println("[" + tn + "]PrintActor in : " + i);
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            ActorRef actor1 = system.actorOf(Props.create(BlockingFutureActor.class));
            ActorRef actor2 = system.actorOf(Props.create(PrintActor.class));
            for (int i = 0; i < 500; i++) {
                actor1.tell(i, ActorRef.noSender());
                actor2.tell(i, ActorRef.noSender());
            }
            System.out.println(">>> Press ENTER to exit <<<");
            br.readLine();
            System.out.println("exiting...");
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }
}
