package akkastudy201803;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.AbstractActor;
import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Identify;
import akka.actor.InvalidActorNameException;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

/**
 * @see https://doc.akka.io/docs/akka/2.5/actors.html
 * @see https://doc.akka.io/docs/akka/2.5/general/addressing.html
 */
public class ActorRefActorPathTest {
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

    static class HelloActor extends AbstractActor {
        final String name;

        HelloActor(final String name) {
            this.name = name;
        }

        static Props props(final String name) {
            return Props.create(HelloActor.class, name);
        }

        static class Hello {
        }

        static class AddChild {
            final String childName;

            AddChild(final String childName) {
                this.childName = childName;
            }
        }

        static class QueryChild {
            final String childName;

            QueryChild(final String childName) {
                this.childName = childName;
            }
        }

        static class QueryParent {
        }

        static class QuerySibling {
            final String siblingName;

            QuerySibling(final String siblingName) {
                this.siblingName = siblingName;
            }
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().match(AddChild.class, m -> {
                final ActorRef child = getContext().actorOf(props(m.childName), m.childName);
                getSender().tell(child, getSelf());
            }).match(QueryChild.class, m -> {
                getSender().tell(getContext().actorSelection(getSelf().path().child(m.childName)), getSelf());
            }).match(QuerySibling.class, m -> {
                getSender()
                    .tell(getContext().actorSelection(getSelf().path().parent().child(m.siblingName)), getSelf());
            }).match(Hello.class, m -> {
                getSender().tell("hello " + name, getSelf());
            }).build();
        }
    }

    @Test(expected = InvalidActorNameException.class)
    public void testActorOfNameUniqueConstraint() {
        system.actorOf(HelloActor.props("aaa"), "xxx");
        system.actorOf(HelloActor.props("bbb"), "xxx");
    }

    @Test(expected = InvalidActorNameException.class)
    public void testActorOfNameCanNOTContainSlash() {
        system.actorOf(HelloActor.props("bob"), "a/b");
    }

    @Test(expected = InvalidActorNameException.class)
    public void testActorOfNameCanNOTStartWithDollar() {
        system.actorOf(HelloActor.props("bob"), "$a");
    }

    @Test
    public void testActorPathNameBasics() {
        TestKit probe = new TestKit(system);
        ActorRef bob = system.actorOf(HelloActor.props("bob"), "bob");
        assertEquals("bob", bob.path().name());
        assertEquals("/user/bob", bob.path().toStringWithoutAddress());
        assertEquals("akka://" + system.name() + "/user/bob", bob.path().toString());

        bob.tell(new HelloActor.Hello(), probe.getRef());
        probe.expectMsgEquals("hello bob");

        bob.tell(new HelloActor.AddChild("jon"), probe.getRef());
        ActorRef jon = probe.expectMsgClass(ActorRef.class);
        jon.tell(new HelloActor.Hello(), probe.getRef());
        probe.expectMsgEquals("hello jon");

        assertEquals("jon", jon.path().name());
        assertEquals("akka://" + system.name() + "/user/bob/jon", jon.path().toString());

        bob.tell(new HelloActor.AddChild("alice"), probe.getRef());
        ActorRef alice = probe.expectMsgClass(ActorRef.class);
        alice.tell(new HelloActor.Hello(), probe.getRef());
        probe.expectMsgEquals("hello alice");

        assertEquals("alice", alice.path().name());
        assertEquals("akka://" + system.name() + "/user/bob/alice", alice.path().toString());

        system.actorSelection("bob").tell(new Identify(1), probe.getRef());
        ActorIdentity id = probe.expectMsgClass(ActorIdentity.class);
        assertFalse(id.getActorRef().isPresent());

        system.actorSelection("/user/bob").tell(new Identify(1), probe.getRef());
        id = probe.expectMsgClass(ActorIdentity.class);
        assertTrue(id.getActorRef().isPresent());
        assertEquals(bob, id.getActorRef().get());

        bob.tell(new HelloActor.QueryChild("jon"), probe.getRef());
        ActorSelection s = probe.expectMsgClass(ActorSelection.class);
        assertEquals("/user/bob/jon", s.pathString());
        s.tell(new HelloActor.Hello(), probe.getRef());
        probe.expectMsgEquals("hello jon");

        jon.tell(new HelloActor.QuerySibling("alice"), probe.getRef());
        s = probe.expectMsgClass(ActorSelection.class);
        assertEquals("/user/bob/alice", s.pathString());
        s.tell(new HelloActor.Hello(), probe.getRef());
        probe.expectMsgEquals("hello alice");
    }
}
