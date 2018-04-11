package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import akka.actor.AbstractActorWithStash;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * @see https://doc.akka.io/docs/akka/2.5/actors.html#stash
 */
public class ActorStashDemo implements Runnable {
    static class ActorStashDemoActor extends AbstractActorWithStash {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("open", m -> {
                getContext().become(receiveBuilder().matchEquals("read", m2 -> {
                    log.info("read");
                }).matchEquals("write", m2 -> {
                    log.info("write");
                }).matchEquals("close", m2 -> {
                    log.info("close");
                    unstashAll();
                    getContext().unbecome();
                }).matchAny(msg -> stash()).build(), false);
            }).matchAny(m -> {
                log.info("stashed : " + m);
                stash();
            }).build();
        }

    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            final ActorRef demo = system.actorOf(Props.create(ActorStashDemoActor.class));
            demo.tell("aaa", ActorRef.noSender());
            demo.tell("open", ActorRef.noSender());
            demo.tell("aaa", ActorRef.noSender());
            demo.tell("read", ActorRef.noSender());
            demo.tell("write", ActorRef.noSender());
            demo.tell("open", ActorRef.noSender());
            demo.tell("read", ActorRef.noSender());
            demo.tell("close", ActorRef.noSender());
            demo.tell("read", ActorRef.noSender());
            demo.tell("aaa", ActorRef.noSender());
            demo.tell("write", ActorRef.noSender());
            demo.tell("close", ActorRef.noSender());
            demo.tell("bbbb", ActorRef.noSender());
            demo.tell("write", ActorRef.noSender());
            System.out.println(">>> Press ENTER to exit <<<");
            br.readLine();
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }

}
