package akkastudy201803;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.javadsl.TestKit;

/**
 * @see https://doc.akka.io/docs/akka/2.5/event-bus.html#event-stream
 */
public class EventStreamTest {
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

    static interface SomeAbstractMessage {
        public String getMessage();
    }

    static class ConcreteMessageFoo implements SomeAbstractMessage {
        final String msg;

        ConcreteMessageFoo(final String msg) {
            this.msg = msg;
        }

        @Override
        public String getMessage() {
            return this.msg;
        }
    }

    static class ConcreteMessageBar implements SomeAbstractMessage {
        final String msg;
        final int num;

        ConcreteMessageBar(final String msg, final int num) {
            this.msg = msg;
            this.num = num;
        }

        @Override
        public String getMessage() {
            return this.msg;
        }

        public int getNumber() {
            return this.num;
        }
    }

    static class FooBarListener extends AbstractActor {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        final long id;
        final ActorRef sendto;

        FooBarListener(final long id, final ActorRef sendto) {
            this.id = id;
            this.sendto = sendto;
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(ConcreteMessageFoo.class, m -> {
                log.info("listener[{}] receives Foo({}) from : {}", id, m.getMessage(), getSender().path());
                sendto.tell(m, getSelf());
            }).match(ConcreteMessageBar.class, m -> {
                log.info(
                    "listener[{}] receives Bar({}, {}) from : {}",
                    id,
                    m.getMessage(),
                    m.getNumber(),
                    getSender().path());
                sendto.tell(m, getSelf());
            }).build();
        }
    }

    @Test
    public void testEventStreamClassifier() {
        final TestKit probeFoo = new TestKit(system);
        final TestKit probeAbstract = new TestKit(system);
        final ActorRef fooListener = system.actorOf(Props.create(FooBarListener.class, 1L, probeFoo.getRef()));
        final ActorRef abstractListener =
            system.actorOf(Props.create(FooBarListener.class, 2L, probeAbstract.getRef()));
        system.eventStream().subscribe(fooListener, ConcreteMessageFoo.class);
        system.eventStream().subscribe(abstractListener, SomeAbstractMessage.class);

        // fooListener and abstractListner can receive ConcreteMessageFoo
        system.eventStream().publish(new ConcreteMessageFoo("hello"));
        ConcreteMessageFoo m0 = probeFoo.expectMsgClass(ConcreteMessageFoo.class);
        assertEquals(m0.getMessage(), "hello");
        m0 = probeAbstract.expectMsgClass(ConcreteMessageFoo.class);
        assertEquals(m0.getMessage(), "hello");

        // fooListener does NOT subscribe to ConcreteMessageFoo nor SomeAbstractMessage.
        // => only abstractListner can receive ConcreteMessageFoo
        system.eventStream().publish(new ConcreteMessageBar("abcdefg", 10));
        probeFoo.expectNoMessage();
        ConcreteMessageBar m1 = probeAbstract.expectMsgClass(ConcreteMessageBar.class);
        assertEquals(m1.getMessage(), "abcdefg");
        assertEquals(m1.getNumber(), 10);
    }
}
