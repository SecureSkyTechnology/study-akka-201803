package akkastudy201803;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.AbstractActor;
import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.AllDeadLetters;
import akka.actor.Identify;
import akka.actor.Inbox;
import akka.actor.Kill;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.actor.UnhandledMessage;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.Duration;

/**
 * @see https://doc.akka.io/docs/akka/2.5/actors.html
 */
public class ActorBasicTest {
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

    static class HelloWorldActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("hello", m -> {
                getSender().tell("world", getSelf());
            }).build();
        }
    }

    @Test
    public void testSimpleMessageSendReceive() {
        TestKit probe = new TestKit(system);
        ActorRef actor = system.actorOf(Props.create(HelloWorldActor.class));
        actor.tell("hello", probe.getRef());
        probe.expectMsgEquals("world");
        assertEquals(actor, probe.getLastSender());
    }

    @Test
    public void testUnhandledMessage() {
        TestKit probe = new TestKit(system);
        system.eventStream().subscribe(probe.getRef(), UnhandledMessage.class);
        ActorRef actor = system.actorOf(Props.create(HelloWorldActor.class));
        actor.tell("xxxxx", probe.getRef());
        final UnhandledMessage m = probe.expectMsgClass(UnhandledMessage.class);
        assertEquals(m.getMessage(), "xxxxx");
        assertEquals(m.getSender(), probe.getRef());
        assertEquals(m.getRecipient(), actor);
    }

    static class SimpleMatchAnyActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("hello", m -> {
                getSender().tell("world", getSelf());
            }).matchAny(m -> {
                getSender().tell("unknown message", getSelf());
            }).build();
        }
    }

    @Test
    public void testSimpleMatchAnyActorDemo() {
        TestKit probe = new TestKit(system);
        ActorRef actor = system.actorOf(Props.create(SimpleMatchAnyActor.class));
        actor.tell("hello", probe.getRef());
        probe.expectMsgEquals("world");
        assertEquals(actor, probe.getLastSender());
        actor.tell("abcdefg", probe.getRef());
        probe.expectMsgEquals("unknown message");
        assertEquals(actor, probe.getLastSender());
    }

    static class NameAndAgeIsActor extends AbstractActor {
        final String name;
        final int age;

        NameAndAgeIsActor() {
            this.name = "bob";
            this.age = 10;
        }

        NameAndAgeIsActor(final String name, final int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("who are you?", m -> {
                getSender().tell("my name is " + name + ", age is " + age + " years old.", getSelf());
            }).build();
        }
    }

    @Test
    public void testActorCreateDemo() {
        TestKit probe = new TestKit(system);
        ActorRef actor1 = system.actorOf(Props.create(NameAndAgeIsActor.class));
        ActorRef actor2 = system.actorOf(Props.create(NameAndAgeIsActor.class, "jon", 12));
        actor1.tell("who are you?", probe.getRef());
        probe.expectMsgEquals("my name is bob, age is 10 years old.");
        actor2.tell("who are you?", probe.getRef());
        probe.expectMsgEquals("my name is jon, age is 12 years old.");
    }

    @Test
    public void testInboxDemo() throws TimeoutException {
        ActorRef actor = system.actorOf(Props.create(HelloWorldActor.class));
        Inbox inbox = Inbox.create(system);
        inbox.send(actor, "hello");
        assertEquals(inbox.receive(Duration.Zero()), "world");
    }

    @Test
    public void testInboxCanWatchActorTerminateion() throws TimeoutException {
        ActorRef actor = system.actorOf(Props.create(HelloWorldActor.class));
        Inbox inbox = Inbox.create(system);
        inbox.watch(actor);
        actor.tell(PoisonPill.getInstance(), ActorRef.noSender());
        assertTrue(inbox.receive(Duration.create(1, TimeUnit.SECONDS)) instanceof Terminated);
    }

    static class BecomeDemoActor extends AbstractActor {
        final Receive japaneseGreeter = receiveBuilder().matchEquals("repair", m -> {
            getContext().unbecome();
        }).matchAny(m -> {
            getSender().tell("こんにちは, " + m, getSelf());
        }).build();
        final Receive germanGreeter = receiveBuilder().matchEquals("repair", m -> {
            getContext().unbecome();
        }).matchAny(m -> {
            getSender().tell("Gutentag, " + m, getSelf());
        }).build();
        final Receive englishGreeter = receiveBuilder().matchEquals("repair", m -> {
            getContext().unbecome();
        }).matchAny(m -> {
            getSender().tell("Hello, " + m, getSelf());
        }).build();

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("japanese", m -> {
                getContext().become(japaneseGreeter);
            }).matchEquals("german", m -> {
                getContext().become(germanGreeter);
            }).matchEquals("english", m -> {
                getContext().become(englishGreeter);
            }).matchAny(m -> {
                getSender().tell("send me japanese/german/english", getSelf());
            }).build();
        }
    }

    @Test
    public void testBecomeUnbecome() {
        TestKit probe = new TestKit(system);
        ActorRef actor = system.actorOf(Props.create(BecomeDemoActor.class));
        actor.tell("japanese", ActorRef.noSender());
        actor.tell("bob", probe.getRef());
        probe.expectMsgEquals("こんにちは, bob");
        actor.tell("repair", probe.getRef());

        actor.tell("german", ActorRef.noSender());
        actor.tell("jon", probe.getRef());
        probe.expectMsgEquals("Gutentag, jon");
        actor.tell("repair", probe.getRef());

        actor.tell("english", ActorRef.noSender());
        actor.tell("eve", probe.getRef());
        probe.expectMsgEquals("Hello, eve");
        actor.tell("repair", probe.getRef());

        actor.tell("abc", probe.getRef());
        probe.expectMsgEquals("send me japanese/german/english");
    }

    static class DivPair {
        final int a;
        final int b;

        DivPair(final int a, final int b) {
            this.a = a;
            this.b = b;
        }
    }

    static class LifeCycleDemoActor extends AbstractActor {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        final UUID uuid = UUID.randomUUID();

        static Props props() {
            return Props.create(LifeCycleDemoActor.class);
        }

        @Override
        public void preStart() throws Exception {
            log.info("[{}] preStart() demo[before super.preStart()]", this.uuid);
            super.preStart();
            log.info("[{}] preStart() demo[after super.preStart()]", this.uuid);
        }

        @Override
        public void postStop() throws Exception {
            log.info("[{}] postStop() demo[before super.postStop()]", this.uuid);
            super.postStop();
            log.info("[{}] postStop() demo[after super.postStop()]", this.uuid);
        }

        @Override
        public void preRestart(Throwable reason, Optional<Object> message) throws Exception {
            log.info("[{}] preRestart() demo[before super.preRestart()]", this.uuid);
            super.preRestart(reason, message);
            log.info("[{}] preRestart() demo[after super.preRestart()]", this.uuid);
        }

        @Override
        public void postRestart(Throwable reason) throws Exception {
            log.info("[{}] postRestart() demo[before super.postRestart()]", this.uuid);
            super.postRestart(reason);
            log.info("[{}] postRestart() demo[after super.postRestart()]", this.uuid);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(DivPair.class, m -> {
                log.info("DivPiar received, a={}, b={}", m.a, m.b);
                final int r = m.a / m.b;
                getSender().tell(r, getSelf());
            }).matchEquals("uuid", m -> {
                getSender().tell(this.uuid.toString(), getSender());
            }).matchEquals("stop", m -> {
                log.info("stop received.");
                getContext().stop(getSelf());
            }).build();
        }
    }

    @Test
    public void testActorStartStopRestartDemo() {
        TestKit probe = new TestKit(system);
        ActorRef demo1 = system.actorOf(LifeCycleDemoActor.props());
        int i = 0;

        // 1st-actor, actor exists.
        demo1.tell(new Identify(i++), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertTrue(id.getActorRef().isPresent());
        assertEquals(demo1, id.getActorRef().get());

        // 1st-actor, store 1st actor's uuid.
        demo1.tell("uuid", probe.getRef());
        final String uuid1a = probe.expectMsgClass(String.class);

        // 1st-actor, test normal division
        demo1.tell(new DivPair(6, 2), probe.getRef());
        probe.expectMsgEquals(3);

        // 1st-actor, test 0 divide -> actor terminated & restart
        demo1.tell(new DivPair(1, 0), probe.getRef());
        demo1.tell(new Identify(i++), probe.getRef());
        id = probe.expectMsgClass(ActorIdentity.class);
        assertTrue(id.getActorRef().isPresent());
        assertEquals(demo1, id.getActorRef().get());

        // 1st-actor, uuid is NOT equals to former actor's uuid.
        demo1.tell("uuid", probe.getRef());
        final String uuid1b = probe.expectMsgClass(String.class);
        assertNotEquals(uuid1a, uuid1b);

        // 1st-actor, test normal division
        demo1.tell(new DivPair(12, 3), probe.getRef());
        probe.expectMsgEquals(4);

        // 1st-actor, stop operation
        probe.watch(demo1);
        demo1.tell("stop", ActorRef.noSender());
        probe.expectTerminated(demo1);
        probe.unwatch(demo1);

        // 1st-actor, stopped -> not exists.
        demo1.tell(new Identify(i++), probe.getRef());
        id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());

        ActorRef demo2 = system.actorOf(LifeCycleDemoActor.props());
        // 2nd-actor, actor exists.
        demo2.tell(new Identify(i++), probe.getRef());
        id = probe.expectMsgClass(ActorIdentity.class);
        assertTrue(id.getActorRef().isPresent());
        assertEquals(demo2, id.getActorRef().get());

        // 2nd-actor, store 2nd actor's uuid, not equals to 1st.
        demo2.tell("uuid", probe.getRef());
        final String uuid2a = probe.expectMsgClass(String.class);
        assertNotEquals(uuid1a, uuid2a);

        // 2nd-actor, test normal division
        demo2.tell(new DivPair(6, 2), probe.getRef());
        probe.expectMsgEquals(3);
    }

    @Test
    public void testSendPoisonPillTerminateActorButNotRestart() {
        TestKit probe = new TestKit(system);
        ActorRef demo = system.actorOf(LifeCycleDemoActor.props());

        demo.tell(new DivPair(6, 2), probe.getRef());
        probe.expectMsgEquals(3);

        probe.watch(demo);
        demo.tell(PoisonPill.getInstance(), ActorRef.noSender());
        probe.expectTerminated(demo);

        // DONT restart.
        demo.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }

    @Test
    public void testSendKillStopActorAndNotRestart() {
        TestKit probe = new TestKit(system);
        ActorRef demo = system.actorOf(LifeCycleDemoActor.props());

        demo.tell(new DivPair(6, 2), probe.getRef());
        probe.expectMsgEquals(3);

        probe.watch(demo);
        demo.tell(Kill.getInstance(), ActorRef.noSender());
        probe.expectTerminated(demo);

        // DONT restart.
        demo.tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());
    }

    static class TerminationWatchActor extends AbstractActor {
        ActorRef child;
        ActorRef lastSender = system.deadLetters();

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchEquals("poisonpill", m -> {
                child = getContext().actorOf(Props.empty());
                getContext().watch(child);
                child.tell(PoisonPill.getInstance(), ActorRef.noSender());
                lastSender = getSender();
            }).matchEquals("kill", m -> {
                child = getContext().actorOf(Props.empty());
                getContext().watch(child);
                child.tell(Kill.getInstance(), ActorRef.noSender());
                lastSender = getSender();
            }).matchEquals("stop", m -> {
                child = getContext().actorOf(Props.empty());
                getContext().watch(child);
                getContext().stop(child);
                lastSender = getSender();
            }).match(Terminated.class, t -> t.actor().equals(child), t -> {
                lastSender.tell("terminated", getSelf());
            }).build();
        }
    }

    @Test
    public void testTerminateReceive() {
        TestKit probe = new TestKit(system);
        ActorRef demo = system.actorOf(Props.create(TerminationWatchActor.class));

        demo.tell("poisonpill", probe.getRef());
        probe.expectMsgEquals("terminated");

        demo.tell("kill", probe.getRef());
        probe.expectMsgEquals("terminated");

        demo.tell("stop", probe.getRef());
        probe.expectMsgEquals("terminated");
    }

    @Test
    public void testMessageToTerminatedActorIsSentToDeadLetter() {
        TestKit probe = new TestKit(system);
        ActorRef demo = system.actorOf(Props.empty());
        // see : https://doc.akka.io/docs/akka/2.5/event-bus.html#dead-letters
        system.eventStream().subscribe(probe.getRef(), AllDeadLetters.class);
        demo.tell(PoisonPill.getInstance(), ActorRef.noSender());
        demo.tell("hello", probe.getRef());
        AllDeadLetters dl = probe.expectMsgClass(AllDeadLetters.class);
        assertEquals(dl.recipient(), demo);
        assertEquals(dl.sender(), probe.getRef());
    }

    @Test
    public void testStopAndStopAndStop() {
        TestKit probe = new TestKit(system);
        ActorRef demo = system.actorOf(Props.empty());
        probe.watch(demo);
        system.stop(demo);
        demo.tell(PoisonPill.getInstance(), ActorRef.noSender());
        system.stop(demo);
        demo.tell(PoisonPill.getInstance(), ActorRef.noSender());
        system.stop(demo);
        probe.expectTerminated(demo);
    }
}
