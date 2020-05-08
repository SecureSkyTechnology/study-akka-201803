package akkastudy201803.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import com.typesafe.config.Config;

import akka.actor.AbstractActor;
import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.event.MarkerLoggingAdapter;
import akka.event.slf4j.Slf4jLogMarker;
import scala.concurrent.duration.Duration;

/* Akka におけるログ出力のデモ
 * reference:
 * https://doc.akka.io/docs/akka/2.5/logging.html
 */
public class LoggingDemo implements Runnable {

    static final String TICK_KEY = "tick";

    // actor からのログ出力とmarker使用例
    static class LoggingDemoActor extends AbstractActor {
        final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
        int counter = 0;

        /* Marker は静的な「タグ」として使う。
         * (slf4jのデフォルト実装は生成したmarkerを内部でsingletonで保持するため、
         *  動的な文字列でMarkerを都度生成し続けると、インスタンス参照が残ってしまいOOMが発生しかねない。)
         * see : https://doc.akka.io/docs/akka/2.5/logging.html#using-markers
         * see : https://github.com/akka/akka/blob/master/akka-slf4j/src/test/scala/akka/event/slf4j/Slf4jLoggerSpec.scala
         */
        final MarkerLoggingAdapter mlog = Logging.withMarker(this);
        final Marker slf4jRawMarker = MarkerFactory.getMarker("slf4j mark");
        final Slf4jLogMarker marker = new Slf4jLogMarker(slf4jRawMarker);

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchAny(m -> {
                log.error("error-level demo : received Object.toString=[{}]", m);
                log.warning("warning-level demo : received Object.toString=[{}]", m);
                log.info("info-level demo : received Object.toString=[{}]", m);
                log.debug("debug-level demo : received Object.toString=[{}]", m);
                // NO TRACE LEVEL :P
                log.info("placeholder demo1 : {}, {}, {}", "hello", counter, new URL("http://localhost/hello"));
                log.info("placeholder demo2 : {}, {}, {}", new String[] { "abc", "def" });

                mlog.error(marker, "slf4j marker logging, error-level demo (counter={})", counter);
                mlog.warning(marker, "slf4j marker logging, warning-level demo (counter={})", counter);
                mlog.info(marker, "slf4j marker logging, info-level demo (counter={})", counter);
                mlog.debug(marker, "slf4j marker logging, debug-level demo (counter={})", counter);

                counter++;
            }).build();
        }
    }

    /* MDCを使う時の基本として、メッセージハンドラ内で set - clear で囲む。
     * slf4j 向けドキュメントでは、例として個別のメッセージハンドラ内で set - clear している。
     * https://doc.akka.io/docs/akka/2.5/logging.html#logging-thread-akka-source-and-actor-system-in-mdc
     * 
     * actorインスタンスそれぞれに紐づく属性(actor生成時に定まる値)をMDCで出力したい時は、
     * コンストラクタやpreStart()でsetし、postStop()でclearすると、いちいちメッセージハンドラ内でset-clearする手間が省ける。
     * 一方でMDC(特にslf4j + logback)はThreadLocalで実装されている。
     * actor がどのスレッドで動くか保証されていない(dispatcher指定で PinnedDispatcher 上に固定する場合を除く)以上、
     * preStart()でset / postStop() でclearしてしまうと、その間のメッセージハンドラ処理では個別のactorがsetした値が
     * 上書きされたり混在してしまうのではないか、という懸念が出てくる。
     * 
     * この懸念についてだが、そもそもakkaにおけるLoggerの仕組みとしてLoggerそれ自体がactorとして別スレッドで動作する。
     * つまりMDCの実装、特にslf4j + logbackにおける ThreadLocal を使った実装に基づく制限については、そもそも akka のロガーでは成立しない。
     * ではakkaのロガー機構はどうしているのかというと、LoggerにわたすメッセージにMDC用Mapオブジェクトを含め、
     * slf4jのakkaロガー実装の方で都度実際のMDCにputしてログ出力したらすぐclear、という流れになっている。
     * これにより DiagnosticLoggingAdapter のMDCの set - clear は ThreadLocal の仕組みとは分離されている。
     * 
     * よってコンストラクタ/preStart()で set し、postStop() で clearしてもMDCが混ざったり上書きされることは無い。
     * 
     * DiagnosticLoggingAdapter が提供しているMDCのget/setメソッドは、内部的にはMap参照となっている。
     * それを前提とすれば、ある程度自由にMDCの内容を制御できるようになりそう。
     * 
     * ソースコード参照(2.5.31):
     * https://github.com/akka/akka/blob/v2.5.31/akka-actor/src/main/scala/akka/event/Logging.scala
     * -> DiagnosticLoggingAdapter は LoggingAdapter にMDCのインターフェイスを追加した trait となっており、
     *    LoggingAdapter は各ロガーメソッドを notify(logLevel) に渡す trait となっている。
     * -> 実際に DiagnosticLoggingAdapter を返すところでは BusLogging に DiagnosticLoggingAdapter trait をくっつけていて、
     *    BusLoggingの実装を見るとここでようやく、ログ出力イベントを event-bus にpublishしている。
     *    このログ出力イベントに、MapとしてMDCを含む。
     * https://github.com/akka/akka/blob/v2.5.31/akka-slf4j/src/main/scala/akka/event/slf4j/Slf4jLogger.scala
     * -> ログ出力イベントを受け付けたら withMdc() の中でslf4jの実際のMDCをputし、ログ出力を終えたらclearしている。
     * 
     * 関連資料
     * https://github.com/akka/akka/issues/16461
     * https://stackoverflow.com/questions/31237506/does-akka-copy-mdc-from-source-actor-to-other-actors-and-futures
     * https://stackoverflow.com/questions/37556950/actor-mdc-context-in-aroundreceive-method
     */

    static class LoggingWithMdcDemo1Actor extends AbstractActorWithTimers {
        int counter = 0;
        final String name;
        // akka でMDCを扱う時のロガー取得
        final DiagnosticLoggingAdapter mdclog = Logging.getLogger(this);
        final String mdcval1;
        final String mdcval2;

        LoggingWithMdcDemo1Actor(final String name, final String mdcval1, final String mdcval2) {
            this.name = name;
            getTimers().startPeriodicTimer(TICK_KEY, "tick1", Duration.create(3, TimeUnit.SECONDS));
            this.mdcval1 = mdcval1;
            this.mdcval2 = mdcval2;
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchAny(m -> {
                // メッセージハンドラ内で set - clear する例
                final Map<String, Object> mdc = new HashMap<>();
                mdc.put("mdcval1", this.mdcval1);
                mdc.put("mdcval2", this.mdcval2);
                mdclog.setMDC(mdc);
                mdclog.error("logging with mdc(1), error-level demo (name={}, counter={})", name, counter);
                mdclog.warning("logging with mdc(1), warning-level demo (name={}, counter={})", name, counter);
                mdclog.info("logging with mdc(1), info-level demo (name={}, counter={})", name, counter);
                mdclog.debug("logging with mdc(1), debug-level demo (name={}, counter={})", name, counter);
                mdclog.clearMDC();
                counter++;
            }).build();
        }
    }

    /* preStart() で setMDC() して、 postStop() で clearMDC() する例。
     * メッセージハンドラで set - clear する手間が無くなる。
     * akkaのロガーの仕組み上、MDCのThreadLocal制限を気にする必要は無いためこれが可能となっている。
     */
    static class LoggingWithMdcDemo2Actor extends AbstractActorWithTimers {
        int counter = 0;
        final String name;
        // akka でMDCを扱う時のロガー取得
        final DiagnosticLoggingAdapter mdclog = Logging.getLogger(this);
        final String mdcval1;
        final String mdcval2;

        LoggingWithMdcDemo2Actor(final String name, final String mdcval1, final String mdcval2) {
            this.name = name;
            getTimers().startPeriodicTimer(TICK_KEY, "tick2", Duration.create(3, TimeUnit.SECONDS));
            this.mdcval1 = mdcval1;
            this.mdcval2 = mdcval2;
        }

        @Override
        public void preStart() throws Exception {
            final Map<String, Object> mdc = new HashMap<>();
            mdc.put("mdcval1", this.mdcval1);
            mdc.put("mdcval2", this.mdcval2);
            mdclog.setMDC(mdc);
            super.preStart();
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder().matchAny(m -> {
                mdclog.error("logging with mdc(2), error-level demo (name={}, counter={})", name, counter);
                mdclog.warning("logging with mdc(2), warning-level demo (name={}, counter={})", name, counter);
                mdclog.info("logging with mdc(2), info-level demo (name={}, counter={})", name, counter);
                mdclog.debug("logging with mdc(2), debug-level demo (name={}, counter={})", name, counter);
                counter++;
            }).build();
        }

        @Override
        public void postStop() throws Exception {
            mdclog.clearMDC();
            super.postStop();
        }
    }

    @Override
    public void run() {
        final ActorSystem system = ActorSystem.create();

        // ログ関連設定の取得
        final Config config = system.settings().config();
        System.out.println("akka.loglevel = " + config.getString("akka.loglevel"));
        System.out.println("akka.stdout-loglevel = " + config.getString("akka.stdout-loglevel"));
        System.out.println("akka.logging-filter = " + config.getString("akka.logging-filter"));
        List<String> loggers = config.getStringList("akka.loggers");
        for (int i = 0; i < loggers.size(); i++) {
            System.out.println("akka.loggers[" + i + "] = " + loggers.get(i));
        }
        System.out.println("akka.log-config-on-start = " + config.getString("akka.log-config-on-start"));

        System.out.println("Test Dead Letter message logging...");
        for (int i = 0; i < 30; i++) {
            final String msg = "Message to Dead Letter No." + i;
            system.deadLetters().tell(msg, ActorRef.noSender());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            // actor からの一般的な LoggingAdapter 経由のログ出力 (Marker 含む) の例
            final ActorRef logdemo = system.actorOf(Props.create(LoggingDemoActor.class));
            logdemo.tell("hello, akka logging", ActorRef.noSender());

            // DiagnosticLoggingAdapter によるMDCを使用したログ出力の例
            system.actorOf(Props.create(LoggingWithMdcDemo1Actor.class, "MDCデモ1", "日本語MDC1-1", "日本語MDC2-1"));

            // MDCを preStart() で set - postStop() で clear するActorをsingle threadなdispatcher上で2つ動かす。
            // -> DiagnosticLoggingAdapter のMDC管理は ThreadLocal に依存していないので、MDCの値が混在することは無い。
            system.actorOf(
                Props.create(LoggingWithMdcDemo2Actor.class, "MDCデモ2a", "日本語MDC1-2a", "日本語MDC2-2a").withDispatcher(
                    "my-single-threaded-dispatcher"));
            system.actorOf(
                Props.create(LoggingWithMdcDemo2Actor.class, "MDCデモ2b", "日本語MDC1-2b", "日本語MDC2-2b").withDispatcher(
                    "my-single-threaded-dispatcher"));

            System.out.println(">>> Press ENTER to exit <<<");
            // actor-system からのログ出力デモ
            system.log().error("error-level demo : ActorSystem#log()");
            system.log().warning("warning-level demo : ActorSystem#log()");
            system.log().info("info-level demo : ActorSystem#log()");
            system.log().debug("debug-level demo : ActorSystem#log()");
            br.readLine();

        } catch (IOException ioe) {
        } finally {
            system.terminate();
        }
        System.out.println(
            "\ntry : java -Dconfig.file=custom-application.conf -jar (...)/study-akka-201803-1.0.0-SNAPSHOT.jar");
    }
}
