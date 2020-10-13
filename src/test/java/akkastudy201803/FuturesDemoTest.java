package akkastudy201803;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.dispatch.OnComplete;
import akka.dispatch.Recover;
import akka.japi.Function;
import akka.japi.Function2;
import akka.pattern.Patterns;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Promise;

/**
 * @see https://doc.akka.io/docs/akka/2.5/futures.html
 * @see https://doc.akka.io/docs/akka/2.5/actors.html#ask-send-and-receive-future
 * 
 * copied and modified from above akka docs sample code.
 */
public class FuturesDemoTest {
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

    static class DelayedHelloActor extends AbstractActor {
        @Override
        public Receive createReceive() {
            return receiveBuilder().match(String.class, m -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignore) {
                }
                getSender().tell("hello, " + m, getSelf());
            }).build();
        }
    }

    /*
     * https://doc.akka.io/docs/akka/2.5/futures.html#use-with-actors
     */

    @Test
    public void testSimpleFutureAskDemo() throws Exception {
        final ActorRef hello = system.actorOf(Props.create(DelayedHelloActor.class));
        Timeout timeout = new Timeout(1, TimeUnit.SECONDS);
        Future<Object> future = Patterns.ask(hello, "bob", timeout);
        String result = (String) Await.result(future, timeout.duration());
        assertEquals("hello, bob", result);
    }

    @Test
    public void testSimpleFuturePipeDemo() throws Exception {
        final ActorRef hello = system.actorOf(Props.create(DelayedHelloActor.class));
        Timeout timeout = new Timeout(1, TimeUnit.SECONDS);
        Future<Object> future = Patterns.ask(hello, "bob", timeout);
        TestKit probe = new TestKit(system);
        Patterns.pipe(future, system.dispatcher()).to(probe.getRef());
        probe.expectMsgEquals("hello, bob");
    }

    /*
     * https://doc.akka.io/docs/akka/2.5/futures.html#use-directly
     */

    @Test
    public void testUseFutureDirectlyDemo() throws Exception {
        TestKit probe = new TestKit(system);
        Future<String> f = Futures.future(new Callable<String>() {
            public String call() {
                return "Hello, World";
            }
        }, system.dispatcher());
        f.onComplete(new OnComplete<String>() {
            @Override
            public void onComplete(Throwable failure, String success) throws Throwable {
                probe.getRef().tell(success, ActorRef.noSender());
            }
        }, system.dispatcher());
        probe.expectMsgEquals("Hello, World");
    }

    @Test
    public void testSimplePromiseDemo() throws Exception {
        TestKit probe = new TestKit(system);
        Promise<String> promise = Futures.promise();
        Future<String> f = promise.future();
        f.onComplete(new OnComplete<String>() {
            @Override
            public void onComplete(Throwable failure, String success) throws Throwable {
                probe.getRef().tell(success, ActorRef.noSender());
            }
        }, system.dispatcher());
        promise.success("Hello, World");
        probe.expectMsgEquals("Hello, World");
    }

    /*
     * https://doc.akka.io/docs/akka/2.5/futures.html#functional-futures
     */

    @Test
    public void testMappingFutures() {
        TestKit probe = new TestKit(system);
        final ExecutionContext ec = system.dispatcher();
        Future<String> f1 = Futures.future(() -> {
            return "Hello, World";
        }, ec);
        Future<Integer> f2 = f1.map(new Mapper<String, Integer>() {
            @Override
            public Integer apply(String s) {
                return s.length();
            }
        }, ec);
        f2.onComplete(new OnComplete<Integer>() {
            @Override
            public void onComplete(Throwable failure, Integer success) throws Throwable {
                probe.getRef().tell(success, ActorRef.noSender());
            }
        }, system.dispatcher());
        probe.expectMsgEquals(12);
    }

    @Test
    public void testComposingFutureSequence() {
        TestKit probe = new TestKit(system);
        final ExecutionContext ec = system.dispatcher();
        Future<Integer> f1 = Futures.future(() -> {
            return 10;
        }, ec);
        Future<Integer> f2 = Futures.future(() -> {
            return 20;
        }, ec);
        Future<Integer> f3 = Futures.future(() -> {
            return 30;
        }, ec);
        Iterable<Future<Integer>> listOfFutureInts = Arrays.asList(f1, f2, f3);
        Future<Iterable<Integer>> futureListOfInts = Futures.sequence(listOfFutureInts, ec);
        Future<Long> futureSum = futureListOfInts.map(new Mapper<Iterable<Integer>, Long>() {
            @Override
            public Long apply(Iterable<Integer> ints) {
                long sum = 0;
                for (Integer i : ints) {
                    sum += i;
                }
                return sum;
            }
        }, ec);
        futureSum.onComplete(new OnComplete<Long>() {
            @Override
            public void onComplete(Throwable failure, Long success) throws Throwable {
                probe.getRef().tell(success, ActorRef.noSender());
            }
        }, system.dispatcher());
        probe.expectMsgEquals(60);
    }

    @Test
    public void testComposingFutureTraverse() {
        TestKit probe = new TestKit(system);
        final ExecutionContext ec = system.dispatcher();
        Iterable<String> listStrings = Arrays.asList("a", "b", "c");
        Future<Iterable<String>> f0 = Futures.traverse(listStrings, new Function<String, Future<String>>() {
            @Override
            public Future<String> apply(final String r) throws Exception {
                return Futures.future(() -> {
                    return r.toUpperCase();
                }, ec);
            }
        }, ec);
        f0.onComplete(new OnComplete<Iterable<String>>() {
            @Override
            public void onComplete(Throwable failure, Iterable<String> success) throws Throwable {
                probe.getRef().tell(success, ActorRef.noSender());
            }
        }, system.dispatcher());
        probe.expectMsgEquals(Arrays.asList("A", "B", "C"));
    }

    @Test
    public void testComposingFutureFold() {
        TestKit probe = new TestKit(system);
        final ExecutionContext ec = system.dispatcher();
        Future<String> f1 = Futures.future(() -> {
            return "my name ";
        }, ec);
        Future<String> f2 = Futures.future(() -> {
            return "is ";
        }, ec);
        Future<String> f3 = Futures.future(() -> {
            return "bob";
        }, ec);
        Iterable<Future<String>> futures = Arrays.asList(f1, f2, f3);
        Future<String> f = Futures.fold("Hello, ", futures, new Function2<String, String, String>() {
            @Override
            public String apply(String r, String t) {
                return r + t;
            }
        }, ec);
        f.onComplete(new OnComplete<String>() {
            @Override
            public void onComplete(Throwable failure, String success) throws Throwable {
                probe.getRef().tell(success, ActorRef.noSender());
            }
        }, system.dispatcher());
        probe.expectMsgEquals("Hello, my name is bob");
    }

    @Test
    public void testComposingFutureReduce() {
        TestKit probe = new TestKit(system);
        final ExecutionContext ec = system.dispatcher();
        Future<String> f0 = Futures.future(() -> {
            return "Hello, ";
        }, ec);
        Future<String> f1 = Futures.future(() -> {
            return "my name ";
        }, ec);
        Future<String> f2 = Futures.future(() -> {
            return "is ";
        }, ec);
        Future<String> f3 = Futures.future(() -> {
            return "bob";
        }, ec);
        Iterable<Future<String>> futures = Arrays.asList(f0, f1, f2, f3);
        Future<String> f = Futures.reduce(futures, new Function2<String, String, String>() {
            @Override
            public String apply(String r, String t) {
                return r + t;
            }
        }, ec);
        f.onComplete(new OnComplete<String>() {
            @Override
            public void onComplete(Throwable failure, String success) throws Throwable {
                probe.getRef().tell(success, ActorRef.noSender());
            }
        }, system.dispatcher());
        probe.expectMsgEquals("Hello, my name is bob");
    }

    /*
     * https://doc.akka.io/docs/akka/2.5/futures.html#callbacks
     */
    @Test
    public void testFutureOnCompleteCallbacksFailure() throws Exception {
        TestKit probe = new TestKit(system);
        final int a = 10;
        final int b = 0;
        Future<Integer> f = Futures.future(() -> {
            return a / b;
        }, system.dispatcher());
        // onSuccess(), onFailure() are deprecated.
        f.onComplete(new OnComplete<Integer>() {
            @Override
            public void onComplete(Throwable failure, Integer success) throws Throwable {
                probe.getRef().tell(failure, ActorRef.noSender());
            }
        }, system.dispatcher());
        Throwable t = probe.expectMsgClass(Throwable.class);
        assertTrue(t instanceof ArithmeticException);
        assertEquals(t.getMessage(), "/ by zero");
    }

    /*
     * https://doc.akka.io/docs/akka/2.5/futures.html#define-ordering
     */
    @Test
    public void testFutureOnCompleteAndThenChain() throws Exception {
        TestKit probe = new TestKit(system);
        Futures.successful(10).andThen(new OnComplete<Integer>() {
            @Override
            public void onComplete(Throwable failure, Integer success) throws Throwable {
                probe.getRef().tell(success, ActorRef.noSender());
            }
        }, system.dispatcher()).andThen(new OnComplete<Integer>() {
            @Override
            public void onComplete(Throwable failure, Integer success) throws Throwable {
                probe.getRef().tell(success + 1, ActorRef.noSender());
            }
        }, system.dispatcher());
        probe.expectMsgEquals(10);
        probe.expectMsgEquals(11);
    }

    /*
     * https://doc.akka.io/docs/akka/2.5/futures.html#auxiliary-methods
     */

    @Test
    public void testComposingFutureFallbackToChain() {
        TestKit probe = new TestKit(system);
        Future<String> f1 = Futures.failed(new IllegalStateException("ex1"));
        Future<String> f2 = Futures.failed(new IllegalStateException("ex2"));
        Future<String> f3 = Futures.successful("bar");
        Future<String> f4 = f1.fallbackTo(f2).fallbackTo(f3);
        f4.onComplete(new OnComplete<String>() {
            @Override
            public void onComplete(Throwable failure, String success) throws Throwable {
                probe.getRef().tell(success, ActorRef.noSender());
            }
        }, system.dispatcher());
        probe.expectMsgEquals("bar");
    }

    @Test
    public void testComposingFutureZipMap() {
        TestKit probe = new TestKit(system);
        final ExecutionContext ec = system.dispatcher();
        Future<String> f1 = Futures.successful("foo");
        Future<String> f2 = Futures.successful("bar");
        Future<String> f3 = f1.zip(f2).map(new Mapper<scala.Tuple2<String, String>, String>() {
            @Override
            public String apply(scala.Tuple2<String, String> zipped) {
                return zipped._1() + " " + zipped._2();
            }
        }, ec);
        f3.onComplete(new OnComplete<String>() {
            @Override
            public void onComplete(Throwable failure, String success) throws Throwable {
                probe.getRef().tell(success, ActorRef.noSender());
            }
        }, system.dispatcher());
        probe.expectMsgEquals("foo bar");
    }

    /*
     * https://doc.akka.io/docs/akka/2.5/futures.html#use-with-actors
     */

    @Test
    public void testRecoverByVal() throws Exception {
        final ExecutionContext ec = system.dispatcher();
        Future<Integer> f0 = Futures.future(() -> {
            return 1 / 0;
        }, ec).recover(new Recover<Integer>() {
            @Override
            public Integer recover(Throwable failure) throws Throwable {
                if (failure instanceof ArithmeticException) {
                    return -1;
                } else {
                    throw failure;
                }
            }
        }, ec);
        Timeout timeout = new Timeout(1, TimeUnit.SECONDS);
        Integer result = (Integer) Await.result(f0, timeout.duration());
        assertEquals(result.intValue(), -1);
    }

    @Test
    public void testRecoverByNewFuture() throws Exception {
        final ExecutionContext ec = system.dispatcher();
        Future<Integer> f0 = Futures.future(() -> {
            return 1 / 0;
        }, ec).recoverWith(new Recover<Future<Integer>>() {
            @Override
            public Future<Integer> recover(Throwable failure) throws Throwable {
                if (failure instanceof ArithmeticException) {
                    return Futures.future(() -> -1, ec);
                } else {
                    throw failure;
                }
            }
        }, ec);
        Timeout timeout = new Timeout(1, TimeUnit.SECONDS);
        Integer result = (Integer) Await.result(f0, timeout.duration());
        assertEquals(result.intValue(), -1);
    }

    /*
     * https://doc.akka.io/docs/akka/2.5/futures.html#after
     */
    @Test
    public void testAfterPatternDemo() throws Exception {
        TestKit probe = new TestKit(system);
        final ExecutionContext ec = system.dispatcher();
        Future<String> failedFuture = Futures.failed(new IllegalStateException("ex0"));
        Future<String> afterFuture =
            Patterns.after(Timeout.create(Duration.ofMillis(200)).duration(), system.scheduler(), ec, () -> {
                return failedFuture;
            });
        Future<String> f1 = Futures.future(() -> {
            Thread.sleep(500);
            return "foo";
        }, ec);
        Futures.firstCompletedOf(Arrays.asList(f1, afterFuture), ec).onComplete(new OnComplete<String>() {
            @Override
            public void onComplete(Throwable failure, String success) throws Throwable {
                if (Objects.nonNull(failure)) {
                    probe.getRef().tell(failure, ActorRef.noSender());
                } else {
                    probe.getRef().tell(success, ActorRef.noSender());
                }
            }
        }, system.dispatcher());
        Throwable t = probe.expectMsgClass(Throwable.class);
        assertTrue(t instanceof IllegalStateException);
        assertEquals(t.getMessage(), "ex0");
    }

    /*
     * https://doc.akka.io/docs/akka/2.5/futures.html#java-8-completionstage-and-completablefuture
     */

    @Test
    public void testScalaFutureToJavaCompletionStageDemo() throws Exception {
        final ExecutionContext ec = system.dispatcher();
        final CountDownLatch latch1 = new CountDownLatch(1);
        Future<Integer> scalaFuture = Futures.future(() -> {
            assertTrue(Thread.currentThread().getName().contains("akka.actor.default-dispatcher"));
            latch1.await();
            return 10;
        }, ec);

        CompletionStage<Integer> fromScalaFuture = FutureConverters.toJava(scalaFuture).thenApply(n -> {
            assertTrue(Thread.currentThread().getName().contains("ForkJoinPool.commonPool"));
            return n + 2;
        }).thenApply(n -> {
            assertTrue(Thread.currentThread().getName().contains("ForkJoinPool.commonPool"));
            return n * 2;
        }).thenApply(n -> {
            assertTrue(Thread.currentThread().getName().contains("ForkJoinPool.commonPool"));
            return n / 2;
        });

        latch1.countDown();

        assertEquals(fromScalaFuture.toCompletableFuture().get().intValue(), 12);
    }

    @Test
    public void testScalaFutureToJavaCompletionStageNonAsyncDemo() throws Exception {
        final ExecutionContext ec = system.dispatcher();
        Future<Integer> scalaFuture = Futures.future(() -> {
            assertTrue(Thread.currentThread().getName().contains("akka.actor.default-dispatcher"));
            return 10;
        }, ec);

        CompletionStage<Integer> completedStage = FutureConverters.toJava(scalaFuture).thenApply(n -> {
            assertTrue(Thread.currentThread().getName().contains("ForkJoinPool.commonPool"));
            return n + 2;
        });

        assertEquals(completedStage.toCompletableFuture().get().intValue(), 12);
        final String currentThreadName = Thread.currentThread().getName();

        CompletionStage<Integer> stage2 = completedStage.thenApply(n -> {
            assertEquals(Thread.currentThread().getName(), currentThreadName);
            return n * 2;
        }).thenApply(n -> {
            assertEquals(Thread.currentThread().getName(), currentThreadName);
            return n / 2;
        });

        assertEquals(stage2.toCompletableFuture().get().intValue(), 12);
    }

    @Test
    public void testScalaFutureToJavaCompletionStageAsyncDemo() throws Exception {
        final ExecutionContext ec = system.dispatcher();
        final CountDownLatch latch1 = new CountDownLatch(1);
        Future<Integer> scalaFuture = Futures.future(() -> {
            assertTrue(Thread.currentThread().getName().contains("akka.actor.default-dispatcher"));
            latch1.await();
            return 10;
        }, ec);

        CompletionStage<Integer> fromScalaFuture = FutureConverters.toJava(scalaFuture).thenApplyAsync(n -> {
            assertTrue(Thread.currentThread().getName().contains("ForkJoinPool.commonPool"));
            return n + 2;
        }).thenApplyAsync(n -> {
            assertTrue(Thread.currentThread().getName().contains("ForkJoinPool.commonPool"));
            return n * 2;
        }).thenApplyAsync(n -> {
            assertTrue(Thread.currentThread().getName().contains("ForkJoinPool.commonPool"));
            return n / 2;
        });

        latch1.countDown();

        assertEquals(fromScalaFuture.toCompletableFuture().get().intValue(), 12);
    }

    @Test
    public void testScalaFutureToJavaCompletionStageAsyncWithExecutorDemo() throws Exception {
        final ExecutionContext ec = system.dispatcher();
        final Executor ex = system.dispatcher();
        final CountDownLatch latch1 = new CountDownLatch(1);
        Future<Integer> scalaFuture = Futures.future(() -> {
            assertTrue(Thread.currentThread().getName().contains("akka.actor.default-dispatcher"));
            latch1.await();
            return 10;
        }, ec);

        CompletionStage<Integer> fromScalaFuture = FutureConverters.toJava(scalaFuture).thenApplyAsync(n -> {
            assertTrue(Thread.currentThread().getName().contains("akka.actor.default-dispatcher"));
            return n + 2;
        }, ex).thenApplyAsync(n -> {
            assertTrue(Thread.currentThread().getName().contains("akka.actor.default-dispatcher"));
            return n * 2;
        }, ex).thenApplyAsync(n -> {
            assertTrue(Thread.currentThread().getName().contains("akka.actor.default-dispatcher"));
            return n / 2;
        }, ex);

        latch1.countDown();

        assertEquals(fromScalaFuture.toCompletableFuture().get().intValue(), 12);
    }

    /*
     * https://doc.akka.io/docs/akka/2.5/actors.html#ask-send-and-receive-future
     */

    @Test
    public void testAskAndCompletableFutureAndPipeDemo() {
        final ActorRef hello = system.actorOf(Props.create(DelayedHelloActor.class));
        CompletableFuture<Object> f1 = Patterns.ask(hello, "bob", Duration.ofSeconds(1)).toCompletableFuture();
        CompletableFuture<Object> f2 = FutureConverters.toJava(Patterns.ask(hello, "jon", 1000)).toCompletableFuture();

        /* NOTE: 次の書き方だと、高確率で DelayedHelloActor からのレスポンスのタイムアウトでエラーになる。
         * 
        CompletableFuture<String> transformed = CompletableFuture.allOf(f1, f2).thenApply(v -> {
            final String s1 = (String) f1.join();
            final String s2 = (String) f2.join();
            return s1 + "/" + s2;
        });
         *
         * 他の DelayedHelloActor を使ったテストケースは問題ないのに、なぜこれだけそうなるか？
         * -> CompletableFuture.allOf(...) は javadoc によると
         * "指定されたすべてのCompletableFutureが完了したときに完了する新しいCompletableFutureを返" す。
         * このとき、非同期で動作する処理は少なくとも以下の4つが出てくる。
         * 1. ここのテストメソッドを実行中のスレッド
         * 2. DelayedHelloActor をdispatchする/される予定のスレッド
         * 3. f1 のスレッド
         * 4. f2 のスレッド
         * ここで 2. については特に dispatcher を指定していない。
         * また 3., 4. の生成でも特に dispatcher を指定していない。
         * そうなると、akka-system 内部が2個のスレッドしかたまたま空いてなかったときに、
         * その2個のスレッドに f1, f2 が先行して埋まってしまい、
         * その後に 2. の DelayedHelloActor が queueing されてしまうと、
         * f1, f2 とも1秒間レスポンスを待ち続けるが、肝心の 2. がその後ろでwaiting状態になるため、
         * 2. の処理が動かず f1, f2 とも timeout になる。
         * 
         * これが失敗ケースで発生したf1/f2のレスポンスタイムアウト状況を説明できる
         * 一番簡単なモデルとなる。
         * 逆に、「成功」するときはたまたま f1/f2 のいずれかより早く DelayedHelloActor
         * が dispatcher で動き出し、幸いにも f1/f2 の片方に対してレスポンスを返し、
         * それによりさらにもう片方も動き出し・・・ということで正常に動作したものと思われる。
         * 
         * -> 解決策として、そもそもやりたいことは Patterns.ask を Java の CompletableFuture
         * に変換するユースケースのデモであり、処理の例示として CompletableFuture を 2つ作成し、
         * それを連鎖して結合した結果を返したい。
         * その目的であれば CompletionStage.thenCombine() が適しており、
         * 実際それに変更したところ失敗することが無くなり、テストケースが安定した。 
         */
        CompletableFuture<String> transformed =
            f1.thenCombine(f2, (Object s1, Object s2) -> s1.toString() + "/" + s2.toString());
        TestKit probe = new TestKit(system);
        Patterns.pipe(transformed, system.dispatcher()).to(probe.getRef());
        probe.expectMsgEquals("hello, bob/hello, jon");
    }

    @Test
    public void testAskAndScalaFutureAndPipeDemo() {
        final ExecutionContext ec = system.dispatcher();
        final ActorRef hello = system.actorOf(Props.create(DelayedHelloActor.class));
        Timeout timeout = new Timeout(1, TimeUnit.SECONDS);
        Future<Object> f1 = Patterns.ask(hello, "bob", timeout);
        Future<Object> f2 = Patterns.ask(hello, "jon", timeout);

        TestKit probe = new TestKit(system);
        Future<String> transformed =
            Futures.sequence(Arrays.asList(f1, f2), ec).map(new Mapper<Iterable<Object>, String>() {
                @Override
                public String apply(Iterable<Object> source) {
                    List<String> results = new ArrayList<>();
                    source.forEach((o) -> {
                        results.add(o.toString());
                    });
                    return String.join("/", results);
                }
            }, ec);
        Patterns.pipe(transformed, system.dispatcher()).to(probe.getRef());
        probe.expectMsgEquals("hello, bob/hello, jon");
    }
}
