package akkastudy201803.fsmdemo;

import static akkastudy201803.fsmdemo.State.Active;
import static akkastudy201803.fsmdemo.State.Idle;
import static akkastudy201803.fsmdemo.Uninitialized.Uninitialized;

import java.util.Arrays;
import java.util.LinkedList;

import akka.actor.AbstractFSM;
import akka.japi.pf.UnitMatch;
import scala.concurrent.duration.Duration;

public class Buncher extends AbstractFSM<State, Data> {
    {
        startWith(Idle, Uninitialized);

        when(
            Idle,
            matchEvent(
                SetTarget.class,
                Uninitialized.class,
                (setTarget, uninitialized) -> stay().using(new Todo(setTarget.getRef(), new LinkedList<>()))));

        onTransition(matchState(Active, Idle, () -> {
            // reuse this matcher
            final UnitMatch<Data> m =
                UnitMatch.create(
                    matchData(Todo.class, todo -> todo.getTarget().tell(new Batch(todo.getQueue()), getSelf())));
            m.match(stateData());
        }).state(Idle, Active, () -> {
            /* Do something here */}));

        when(
            Active,
            Duration.create(1, "second"),
            matchEvent(
                Arrays.asList(Flush.class, StateTimeout()),
                Todo.class,
                (event, todo) -> goTo(Idle).using(todo.copy(new LinkedList<>()))));

        whenUnhandled(
            matchEvent(Queue.class, Todo.class, (queue, todo) -> goTo(Active).using(todo.addElement(queue.getObj())))
                .anyEvent((event, state) -> {
                    log().warning("received unhandled request {} in state {}/{}", event, stateName(), state);
                    return stay();
                }));

        initialize();
    }
}
