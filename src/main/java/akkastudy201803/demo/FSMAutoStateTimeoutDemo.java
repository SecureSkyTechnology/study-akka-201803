package akkastudy201803.demo;

import java.util.concurrent.CountDownLatch;

import akka.actor.AbstractFSM;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.FI.UnitApplyVoid;
import scala.concurrent.duration.Duration;

/**
 * @see https://doc.akka.io/docs/akka/2.5/fsm.html
 */
public class FSMAutoStateTimeoutDemo implements Runnable {

    static enum CountState {
        ONE,
        TWO,
        THREE,
        FOUR,
        FIVE
    }

    static class AutoStateTimeout extends AbstractFSM<CountState, Integer> {
        final CountDownLatch latch;

        AutoStateTimeout(final CountDownLatch latch) {
            this.latch = latch;
            startWith(CountState.ONE, 1);
            when(
                CountState.ONE,
                Duration.create(1, "second"),
                matchEvent(StateTimeout().getClass(), Integer.class, (event, data) -> goTo(CountState.TWO).using(2)));
            when(
                CountState.TWO,
                Duration.create(1, "second"),
                matchEvent(StateTimeout().getClass(), Integer.class, (event, data) -> goTo(CountState.THREE).using(3)));
            when(
                CountState.THREE,
                Duration.create(1, "second"),
                matchEvent(StateTimeout().getClass(), Integer.class, (event, data) -> goTo(CountState.FOUR).using(4)));
            when(
                CountState.FOUR,
                Duration.create(1, "second"),
                matchEvent(StateTimeout().getClass(), Integer.class, (event, data) -> goTo(CountState.FIVE).using(5)));
            when(
                CountState.FIVE,
                Duration.create(1, "second"),
                matchEvent(StateTimeout().getClass(), Integer.class, (event, data) -> stop()));

            UnitApplyVoid applier = () -> log().info("{} -> {}", this.stateData(), this.nextStateData());
            onTransition(
                matchState(CountState.ONE, CountState.TWO, applier)
                    .state(CountState.TWO, CountState.THREE, applier)
                    .state(CountState.THREE, CountState.FOUR, applier)
                    .state(CountState.FOUR, CountState.FIVE, applier));
            onTermination(
                matchStop(Normal(), (state, data) -> {
                log().info("stopped normally, state = {} / data = {}", state, data);
                latch.countDown();
            }));

            initialize();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();
        try {
            CountDownLatch latch = new CountDownLatch(1);
            system.actorOf(Props.create(AutoStateTimeout.class, latch));
            latch.await();
        } catch (InterruptedException ignore) {
        } finally {
            system.terminate();
        }

    }

}
