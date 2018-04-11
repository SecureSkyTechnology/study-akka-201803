package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

/**
 * @see https://doc.akka.io/docs/akka/2.5/actors.html#receive-timeout
 */
public class ActorReceiveTimeoutDemo implements Runnable {

    static class ActorReceiveTimeoutDemoActor extends AbstractActorWithTimers {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("start", m -> {
                getContext().setReceiveTimeout(Duration.create(2, TimeUnit.SECONDS));
                log.info("timeout started for 2sec");
            }).matchEquals("stop", m -> {
                getContext().setReceiveTimeout(Duration.Undefined());
                log.info("timeout stopped.");
            }).match(ReceiveTimeout.class, m -> {
                log.info("receive timeouted.");
            }).matchAny(m -> {
                log.info("enter start or stop command, Ctrl-C to exit. received message (matchAny) = " + m);
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            final ActorRef demo = system.actorOf(Props.create(ActorReceiveTimeoutDemoActor.class));
            System.out.println("ENTER 'start', 'stop' command, Ctrl-C to exit.");
            while (true) {
                final String cmd = br.readLine().trim();
                demo.tell(cmd, ActorRef.noSender());
            }
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }
}
