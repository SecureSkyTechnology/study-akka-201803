package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * @see https://doc.akka.io/docs/akka/2.5/actors.html#actors-timers
 */
public class TimerSchedulerDemo implements Runnable {

    static class SingleTick {
        final UUID uuid = UUID.randomUUID();
        final String key;
        final FiniteDuration duration;
        final Object msg;

        SingleTick(final String key, final FiniteDuration duration, final Object msg) {
            this.key = key;
            this.duration = duration;
            this.msg = msg;
        }
    }

    static class Tick1 {
        final UUID uuid = UUID.randomUUID();
    }

    static class Tick2 {
        final UUID uuid = UUID.randomUUID();
    }

    static class TimerSchedulerDemoActor extends AbstractActorWithTimers {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        int counter = 0;
        static final String TICK_KEY1 = "tick1";
        static final String TICK_KEY2 = "tick2";

        TimerSchedulerDemoActor() {
            getTimers().startPeriodicTimer(TICK_KEY1, new Tick1(), Duration.create(1000, TimeUnit.MILLISECONDS));
            getTimers().startPeriodicTimer(TICK_KEY2, new Tick2(), Duration.create(2000, TimeUnit.MILLISECONDS));
        }

        @Override
        public void postStop() {
            log.info("TimerSchedulerDemoActor stopped");
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(Tick1.class, tick -> {
                counter++;
                log.info("tick1({})#{}", tick.uuid, counter);
            }).match(Tick2.class, tick -> {
                counter++;
                log.info("tick2({})#{}", tick.uuid, counter);
            }).match(SingleTick.class, tick -> {
                getTimers().startSingleTimer(tick.key, tick.msg, tick.duration);
            }).matchAny(any -> {
                log.info("unexpected or SingleTick.msg : {}", any);
            }).build();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            final ActorRef tacker = system.actorOf(Props.create(TimerSchedulerDemoActor.class));

            System.out.println(">>> Press ENTER to send another single timer(1) <<<");
            br.readLine();
            tacker.tell(new SingleTick("single1", Duration.create(1, TimeUnit.SECONDS), "hello1"), ActorRef.noSender());

            System.out.println(">>> Press ENTER to send another single timer(2) <<<");
            br.readLine();
            tacker.tell(new SingleTick("single2", Duration.create(2, TimeUnit.SECONDS), "hello2"), ActorRef.noSender());

            System.out.println(">>> Press ENTER to send PoisonPill to timer actor <<<");
            br.readLine();
            tacker.tell(PoisonPill.getInstance(), ActorRef.noSender());

            System.out.println(">>> Press ENTER to exit <<<");
            br.readLine();
        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }

    }

}
