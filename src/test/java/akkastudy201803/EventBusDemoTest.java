package akkastudy201803;

import java.time.Duration;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.japi.LookupEventBus;
import akka.event.japi.ScanningEventBus;
import akka.event.japi.SubchannelEventBus;
import akka.testkit.javadsl.TestKit;
import akka.util.Subclassification;

/**
 * Event Bus sample from:
 * @see https://doc.akka.io/docs/akka/2.5/event-bus.html#classifiers
 */
public class EventBusDemoTest {
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

    static class MsgEnvelope {
        final String topic;
        final Object payload;

        MsgEnvelope(String topic, Object payload) {
            this.topic = topic;
            this.payload = payload;
        }
    }

    /**
     * publishing message class : MsgEnvelope
     * subscriber : ActorRef
     * classifier-compare class : String
     */
    static class LookupBusImpl extends LookupEventBus<MsgEnvelope, ActorRef, String> {
        @Override
        public String classify(MsgEnvelope event) {
            return event.topic;
        }

        @Override
        public void publish(MsgEnvelope event, ActorRef subscriber) {
            subscriber.tell(event.payload, ActorRef.noSender());
        }

        @Override
        public int compareSubscribers(ActorRef a, ActorRef b) {
            return a.compareTo(b);
        }

        @Override
        public int mapSize() {
            return 128;
        }
    }

    @Test
    public void testLookupEventBus() {
        final LookupBusImpl lookupBus = new LookupBusImpl();
        final TestKit probe = new TestKit(system);
        lookupBus.subscribe(probe.getRef(), "greetings");
        lookupBus.publish(new MsgEnvelope("time", System.currentTimeMillis()));
        lookupBus.publish(new MsgEnvelope("greetings", "hello"));
        probe.expectMsgEquals("hello");
    }

    /**
     * sub classifier for String data.
     */
    static class StartsWithSubclassification implements Subclassification<String> {
        @Override
        public boolean isEqual(String x, String y) {
            return x.equals(y);
        }

        @Override
        public boolean isSubclass(String x, String y) {
            return x.startsWith(y);
        }
    }

    /**
     * publishing message class : MsgEnvelope
     * subscriber : ActorRef
     * classifier-compare class : String
     */
    static class SubchannelBusImpl extends SubchannelEventBus<MsgEnvelope, ActorRef, String> {
        @Override
        public String classify(MsgEnvelope event) {
            return event.topic;
        }

        @Override
        public void publish(MsgEnvelope event, ActorRef subscriber) {
            subscriber.tell(event.payload, ActorRef.noSender());
        }

        @Override
        public Subclassification<String> subclassification() {
            return new StartsWithSubclassification();
        }
    }

    @Test
    public void testSubchannelEventBus() {
        final SubchannelBusImpl subchannelBus = new SubchannelBusImpl();
        final TestKit probe = new TestKit(system);
        subchannelBus.subscribe(probe.getRef(), "abc");
        subchannelBus.publish(new MsgEnvelope("xyzabc", "x"));
        subchannelBus.publish(new MsgEnvelope("bcdef", "b"));
        subchannelBus.publish(new MsgEnvelope("abc", "c"));
        probe.expectMsgEquals("c");
        subchannelBus.publish(new MsgEnvelope("abcdef", "d"));
        probe.expectMsgEquals("d");
    }

    /**
     * publishing message class : String
     * subscriber : ActorRef
     * classifier-compare class : Integer
     */
    static class ScanningBusImpl extends ScanningEventBus<String, ActorRef, Integer> {
        @Override
        public int compareClassifiers(Integer a, Integer b) {
            return a.compareTo(b);
        }

        @Override
        public int compareSubscribers(ActorRef a, ActorRef b) {
            return a.compareTo(b);
        }

        @Override
        public boolean matches(Integer classifier, String event) {
            return event.length() <= classifier;
        }

        @Override
        public void publish(String event, ActorRef subscriber) {
            subscriber.tell(event, ActorRef.noSender());
        }
    }

    @Test
    public void testScanningEventBus() {
        final ScanningBusImpl scanningBus = new ScanningBusImpl();
        final TestKit probe = new TestKit(system);
        scanningBus.subscribe(probe.getRef(), 3);
        scanningBus.publish("ab");
        probe.expectMsgEquals("ab");
        scanningBus.publish("abc");
        probe.expectMsgEquals("abc");
        scanningBus.publish("abcd");
        probe.expectNoMessage(Duration.ofSeconds(1));
    }
}
