package akkastudy201803;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.javadsl.TestKit;

public class StreamLikeCounterDemoTest {
    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    static class Tick0 {
        final UUID uuid = UUID.randomUUID();
    }

    static class IncrementMessage {
        final long howMuch;

        IncrementMessage(final long howMuch) {
            this.howMuch = howMuch;
        }
    }

    static class PeriodicLongProduceDemoActor extends AbstractActorWithTimers {
        final ActorRef consumerActorRef;
        long counter = 0;

        PeriodicLongProduceDemoActor(final ActorRef consumerActorRef) {
            this.consumerActorRef = consumerActorRef;
            getTimers().startPeriodicTimer("tick0", new Tick0(), Duration.ofMillis(10));
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(Tick0.class, tick -> {
                counter += 10;
                consumerActorRef.tell(new IncrementMessage(10), getSelf());
            }).matchEquals("flush", m -> {
                getSender().tell(new Long(counter), getSelf());
                getContext().stop(getSelf());
            }).build();
        }
    }

    static class CountSumConsumerActor extends AbstractActor {
        long sum = 0;

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(IncrementMessage.class, m -> {
                this.sum += m.howMuch;
            }).matchEquals("flush", m -> {
                getSender().tell(new Long(sum), getSelf());
                getContext().stop(getSelf());
            }).build();
        }

    }

    @Test
    public void test() throws InterruptedException, ExecutionException {
        final ActorRef consumer = system.actorOf(Props.create(CountSumConsumerActor.class));
        List<ActorRef> producers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final ActorRef producer = system.actorOf(Props.create(PeriodicLongProduceDemoActor.class, consumer));
            producers.add(producer);
        }
        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException ignore) {
        }
        long sumOfProduced = 0;
        for (final ActorRef producer : producers) {
            final Long answer =
                (Long) Patterns.ask(producer, "flush", Duration.ofSeconds(1)).toCompletableFuture().get();
            sumOfProduced += answer.longValue();
        }
        final Long consumed = (Long) Patterns.ask(consumer, "flush", Duration.ofSeconds(1)).toCompletableFuture().get();
        assertEquals(sumOfProduced, consumed.longValue());
    }

}
