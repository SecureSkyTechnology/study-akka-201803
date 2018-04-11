package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * @see https://github.com/enragedginger/akka-quartz-scheduler
 */
public class AkkaQuartzSchedulerDemo implements Runnable {

    static class Tick {
        final String msg;

        Tick(final String msg) {
            this.msg = msg;
        }
    }

    static class AkkaQuartzSchedulerDemoActor extends AbstractActorWithTimers {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(Tick.class, tick -> {
                log.info("tick-tack#{}", tick.msg);
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            final ActorRef demo = system.actorOf(Props.create(AkkaQuartzSchedulerDemoActor.class));

            QuartzSchedulerExtension scheduler = QuartzSchedulerExtension.get(system);
            scheduler.schedule("Every3SecDemo", demo, new Tick("3sec"));
            scheduler.schedule("Every5SecDemo", demo, new Tick("5sec"));
            System.out.println(">>> Press ENTER to stop 3sec jobs <<<");
            br.readLine();
            scheduler.cancelJob("Every3SecDemo");
            System.out.println(">>> Press ENTER to stop 5sec jobs <<<");
            br.readLine();
            scheduler.cancelJob("Every5SecDemo");
            System.out.println(">>> Press ENTER to exit <<<");
            br.readLine();
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }
}
