package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * @see https://github.com/enragedginger/akka-quartz-scheduler
 */
public class AkkaQuartzSchedulerForPinnedDispatcherDemo implements Runnable {

    static class TimerDemo extends AbstractActor {
        private static final int BLOCKSEC_START = 5;
        private static final int BLOCKSEC_MAX = 15;
        private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        private int blockSec = BLOCKSEC_START;
        private boolean expandBlockSec = true;

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("tick", m -> {
                try {
                    log.info(">> timer demo blocking op start for {} secs.", this.blockSec);
                    Thread.sleep(this.blockSec * 1000);
                    log.info("<< timer demo blocking op end.");
                } catch (InterruptedException ignore) {
                }
                if (this.expandBlockSec) {
                    if (BLOCKSEC_MAX == this.blockSec) {
                        this.blockSec--;
                        this.expandBlockSec = false;
                    } else {
                        this.blockSec++;
                    }
                } else {
                    if (BLOCKSEC_START == this.blockSec) {
                        this.blockSec++;
                        this.expandBlockSec = true;
                    } else {
                        this.blockSec--;
                    }
                }
                log.info(".. timer demo next blocking secs = {}", this.blockSec);
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        final ActorRef demo = system.actorOf(Props.create(TimerDemo.class).withDispatcher("my-pinned-dispatcher"));
        QuartzSchedulerExtension scheduler = QuartzSchedulerExtension.get(system);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        final String AKKA_QUARTZ_SC_NAME = "Every10SecDemo";
        try {
            boolean finish = false;
            while (!finish) {
                System.out.println(">>> start or stop or exit <<<");
                final String cmd = br.readLine().trim();
                switch (cmd) {
                case "start":
                    scheduler.schedule(AKKA_QUARTZ_SC_NAME, demo, "tick");
                    break;
                case "stop":
                    scheduler.cancelJob(AKKA_QUARTZ_SC_NAME);
                    break;
                case "exit":
                    finish = true;
                }
            }
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
    }
}
