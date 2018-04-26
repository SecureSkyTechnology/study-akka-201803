# 2018年3月～4月にかけての勉強・調査メモのまとめ

以下、2018年3月～4月にかけて勉強したり、サンプルコードを動かしたりしたときの各トピック毎のメモ・まとめです。

## ロギング/設定/デプロイ

- ロギング : https://doc.akka.io/docs/akka/2.5/logging.html
  - Akka独自のロガーシステムを持っていて、デフォルトは INFO レベル、他のactorと同じスレッド上で動く非同期ロガー。
  - SLF4Jなどの個別ロガーAPIを利用することもできるが、もともと非同期で動作するactorの中で、同期呼び出しのロガーAPIを直接呼ぶとパフォーマンスが落ちる。
  - ログレベルは error, warning, info, debug から選べる。
  - MDC利用可能 : https://doc.akka.io/docs/akka/2.5/logging.html#logging-thread-akka-source-and-actor-system-in-mdc
  - Marker利用可能 : https://doc.akka.io/docs/akka/2.5/logging.html#using-markers
  - Akka 自身の内部デバッグログ出力について : https://doc.akka.io/docs/akka/2.5/logging.html#auxiliary-logging-options
  - SLF4J のAPIをラップできる。依存関係に `ch.qos.logback:logback-classic` と `com.typesafe.akka:akka-slf4j_2.12` を加え、application.conf をカスタマイズする。

```
# 最低限度の設定
akka {
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
}
```

必要に応じてlogbackの設定ファイルを設置する。

- 設定
  - Scala, Akka と同じLightbendが開発・メンテしてる https://github.com/lightbend/config が組み込まれている。
  - 大体は↑のドキュメントと、 https://doc.akka.io/docs/akka/2.5/general/configuration.html を読めば事足りる。
  - Akkaのアプリケーションを作る場合は、classpathの直下に application.conf(またはJSONや.properties)を用意して、ツリー構造の設定を組み立てる。
    - そうしておけば、特に何も指定しなくても ActorSystem 生成時に自動で読み込まれ、 ActorSystem.setting().config() で Config オブジェクトを取得できる。
  - Akkaのモジュールとかライブラリを作る場合は、classpathの直下に reference.conf(またはJSONや.properties)を用意して、設定値のデフォルトを入れておく。
  - `-Dconfig.file=my-application.conf` などで、コマンドラインから application.conf を指定することもできる。
  - もし `-Dconfig.file=...` で、完全な置換ではなく、デフォルトのapplication.confを一部上書きの場合は、カスタムconf側で以下のようにclasspath中のapplicationをincludeする指示を先頭に入れておく。

```
include classpath("application")
```

see : https://github.com/lightbend/config/issues/188

- デプロイ
  - Akkaライブラリの reference.conf の扱いに注意。
  - one-jar, uber-jar など jar ファイルを一つにまとめるタイプでは、各ライブラリ毎にjarの直下にreference.confが含まれるため、まとめる際に重複してしまう。
  - maven-shade-plugin 利用時は、transformers 設定に以下を追加し、一つの reference.conf に結合するようにして対応できる。

```
<transformer implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
  <resource>reference.conf</resource>
</transformer>
```

## テストコードの書き方

公式ドキュメントに大体書いてあるので、これを眺めて、使えそうなテクニックがないか探す。
- https://doc.akka.io/docs/akka/2.5/testing.html

## Actor

大体公式ドキュメントに書いてある。
- https://doc.akka.io/docs/akka/2.5/general/actors.html
- https://doc.akka.io/docs/akka/2.5/general/addressing.html
- https://doc.akka.io/docs/akka/2.5/general/supervision.html
- https://doc.akka.io/docs/akka/2.5/actors.html

アクターの作り方、参照方法、親子辿り方、ライフサイクル、終了方法
- Actorを作る時は、Propsから作る。Actorクラスのコンストラクタ引数も指定可能 : https://doc.akka.io/docs/akka/2.5/actors.html#props
  - actorOf()の引数でActorPathに使う名前を指定できる。`"/"` は含められない。 `"$"` から始めることもできない。
  - 基本的に、普通に英数といくつかの記号で名前を指定すれば、現在コンテキストからの相対パスとしてActorPathを設定できる。(= `"/"` から始める必要がない。始められない。)
  - 同じ名前を指定することはできない。(指定すると InvalidActorNameException が発生する)
- ライフサイクル
  - https://doc.akka.io/docs/akka/2.5/actors.html#actor-lifecycle
- ActorRef, ActorPath からの存在確認
  - Identify -> ActorIdentity
  - https://doc.akka.io/docs/akka/2.5/actors.html#identifying-actors-via-actor-selection
- ActorPath  で wildcard 指定して tell() するとどうなる？
  - wildcard マッチした actor 全部にメッセージが送られる。
  - https://doc.akka.io/docs/akka/2.5/general/addressing.html#querying-the-logical-actor-hierarchy
- stop した Actor をあとになって同じ ActorPath で作り直せるか？
  - 動いてるActorと同じActorPathで新しくActorは作れない。 actorOf() で InvalidActorNameException 例外が発生する。
  - ただし、前のActorが停止状態であれば、同じActorPathで新しく別のActorを actorOf() で起動できる。
  - ※公式ドキュメント上は ActorPath の再利用には慎重になるべき、というガイドがある。
  - https://doc.akka.io/docs/akka/2.5/general/addressing.html#reusing-actor-paths
- `getContext().stop()` によるActor自らの終了
  - https://doc.akka.io/docs/akka/2.5/actors.html#stopping-actors
- PoisonPill による終了
  - https://doc.akka.io/docs/akka/2.5/actors.html#poisonpill
- Kill による終了
  - https://doc.akka.io/docs/akka/2.5/actors.html#killing-an-actor
- `getContext().stop()` による外部からの終了とTerminatedメッセージ
  - https://doc.akka.io/docs/akka/2.5/actors.html#lifecycle-monitoring-aka-deathwatch
- 終了したActorに対してメッセージを送信すると、Dead Letter チャネルに送られる。
  - Dead Letterの取得： https://doc.akka.io/docs/akka/2.5/event-bus.html#dead-letters
- matchAny いれないと、UnhandledMessage が ActorSystemのEventStreamに流れる。
- Inbox
  - https://doc.akka.io/docs/akka/2.5/actors.html#the-inbox
  - fire-and-forget. ただしメッセージ受信ができる。
- メッセージ受信のタイムアウト
  - https://doc.akka.io/docs/akka/2.5/actors.html#receive-timeout
- become/unbecome
  - https://doc.akka.io/docs/akka/2.5/actors.html#become-unbecome
- stashというのもできる
  - https://doc.akka.io/docs/akka/2.5/actors.html#stash

## Scheduler(tick-tack)

- https://doc.akka.io/docs/akka/2.5/scheduler.html
- https://doc.akka.io/docs/akka/2.5/actors.html#actors-timers
- 本格的にやるなら、akka-quartz-scheduler
  - https://qiita.com/t_hirohata/items/8a5c2c67aff8f4a7800d

## Fault Tolerance, エラーハンドリング（耐障害性）

- https://doc.akka.io/docs/akka/2.5/general/supervision.html
- https://doc.akka.io/docs/akka/2.5/fault-tolerance.html
- デフォルト
  - 通常例外についてはActorをrestart.
  - `ActorInitializationException`, `ActorKilledException`, `DeathPactException` (=Akka特有のabend系例外) についてはActorをstop.
  - https://doc.akka.io/docs/akka/2.5/fault-tolerance.html#default-supervisor-strategy

## ディスパッチャーとスレッド/アクターの配置

- https://doc.akka.io/docs/akka/2.5/dispatchers.html
- デフォルト : fork-join-executor
- アクターをどのスレッド上で動かすか決定するのがDispatcher
- Dispatcherは仕組みとしてはスレッドプールのコンテナとして動作し、fork join あるいは thread pool などを使ってアクターをスレッド上で動作させる。
- application.conf の設定ファイルでカスタム Dispatcher を定義して、 `Props.create(...).withDispatcher(名前)` 形式でDispatcherを指定してActorRefを作ることができる。
- 特に、Blocking処理が長くなるアクターは専用のDispatcherに分けたほうが良い。さらにBlocking処理もFutureに分離して、可能な限りアクター内の処理を早く終わらせるようにしたほうが良い。
  - Future を動かすスレッドを、application.conf でカスタマイズした専用 Dispatcher に指定可能。
  - https://doc.akka.io/docs/akka/2.5/dispatchers.html#available-solutions-to-blocking-operations

## ルーティング

- https://doc.akka.io/docs/akka/2.5/routing.html
- 用語
  - 「ルーター」：メッセージを配送するのに特化したアクター。
  - 「ルーティー」：ルーターがメッセージを配送する先のアクター。
- 種類
  - 「プール ルーター」(Pool)：ルーター自身がルーティーとなるアクターのsupervisorとなり、メッセージを配送する。
    - メリット : ルーター自身がルーティーのsupervisorになるため、ルーティーと密結合して制御できる。
    - デメリット : ルーター自身がルーティー作成を作成するため、コンストラクタ引数などが固定化され、ルーティー作成時の柔軟性が失われる。
  - 「グループ ルーター」(Group)：配送先をルーティーの actor path で指定して、メッセージを配送する。ルーティーは別のsupervisorが作っておく。
    - メリット : ルーティー作成の柔軟性を外部supervisorで確保できる。
    - デメリット : ルーティーを actor path で指定することから、actor path を定めづらいようなactorだと組み合わせるのが難しい。
- ロジック
  - ルーティー選択部分をロジックとして分けている。これにより、種類 x ロジックの組み合わせでルーティングを実現できる。
  - RoundRobinPool and RoundRobinGroup : ルーティーに対して順番にメッセージを振っていって、最後まで振ったら最初に戻る、ラウンドロビン方式。
  - RandomPool and RandomGroup : ランダムにルーティーを選んでメッセージを配送するランダム方式。
  - BalancingPool : アイドル状態のルーティーにのみメッセージを配送する。特殊なDispatcherを使うため、Group版はなし。また Broadcast Message は利用できない。
  - SmallestMailboxPool : メールボックスが最小のルーティーにメッセージを配送する。actor path経由ではメッセージボックスにアクセスできないため、Group版はなし。
  - BroadcastPool and BroadcastGroup : 全てのルーティーにメッセージを配送する Broadcast 方式。
  - ScatterGatherFirstCompletedPool and ScatterGatherFirstCompletedGroup : 全てのルーティーにメッセージを配送し、最初に返ってきたメッセージを、オリジナルの送信元に返す、早いもの勝ち方式。
  - TailChoppingPool and TailChoppingGroup : （詳細不明)
  - ConsistentHashingPool and ConsistentHashingGroup : メッセージのハッシュを作ってグルーピングし、特定の特徴を持つメッセージを特定のルーティーに送る。
- 特殊なメッセージ
  - Broadcase Message : 全てのルーティーにメッセージを配信するのに使うコンテナ。
  - PoinsonPill Message : プールルーターにPoisonPillを送ると、そのchild actor (= ルーティー)全てが即座にstopする。もしルーティーに、送ったメッセージを全て処理し終わってからstopして欲しい場合は、PoisonPillを Broadcast Message で送る。ルーティーが全てメッセージを処理し終わったらstopし、全てのルーティーがstopしたら、ルーターも自動でstopする。
  - Kill Message : ルーターに送った場合 ActorKilledException が発生する。その後どうなるかは、ルーター自身の supervisor に委ねられる。なおグループルーターの場合は、ルーターが終了するのみ(ルーティーは別supervisorなので影響しない)。
  - ルーティーの追加・削除 : ルーティーに現在のルーティー一覧/追加/削除/プールサイズ調整などを指示する特殊なメッセージがある。
- プールサイズも最小～最大指定などで、動的な増減を設定可能。
- 独自ロジック（状態によってルーティング先を変えるなど）のルーターを自作することも可能だが、パフォーマンスも両立させるのはハードル高そう。

##  Publish - Subscribe 型のメッセージング

- Actor間の通信は基本的には 1:1 通信。
- 1:N でのメッセージ配信を行うためには、Event Bus と呼ばれる仕組みを使う。
- https://doc.akka.io/docs/akka/2.5/event-bus.html
- ※ 1:N でのメッセージ配信なら Broadcast Router を使うのもありだが、メッセージ配信チャネルに対して自由に subscribe/unsubscribe できる仕組みとしては Event Bus の方が適切。
- Event Bus : Akka で Publish - Subscribe 配信を実現する機構。
  - https://doc.akka.io/docs/akka/2.5/event-bus.html#classifiers
  - 重要なのが Classifiers という考え方で、Event Bus に流れるメッセージのうち、どれを配信するか・どう配信するかの部分を抽象化している。
  - 配信メッセージの分類(classifier)などで何種類か実装が提供されており、プログラマはそれらのクラスを extends して、配信などの抽象化されているところをアプリの仕様に合わせて実装する流れ。
    - インターフェイスをうまく実装することで、配信したいメッセージをclassifierのためのメタ情報を含んだラッパークラスで包むこともできる。
- Event Stream : Actor System に組み込まれている Event Bus で、DeadLetter や ログメッセージが流れる。
  - https://doc.akka.io/docs/akka/2.5/event-bus.html#event-stream
  - `ActorSystem#eventStream().subscribe()/unsubscribe()/publish()` などが使える。
  - 独自に DeadLetter やログメッセージの配信にsubscribeできる他に、独自のオブジェクト(=メッセージ)を配信することもできる。Actorをsubscribeさせて受信可能。

## FSM(Finite State Machine)

- https://doc.akka.io/docs/akka/2.5/fsm.html
- State(S) x Event(E) -> Actions (A), State(S')
  - 状態S で イベントE が発生すると、アクションA を実行した後、状態を S' に遷移する。
- TCP/IP のソケットの状態遷移などがFSMで表現できる。
- Java実装では、`AbstractFSM` を継承してコンストラクタでDSL風に設定できる。
  - `AbstractFSM<State, Data>` の State で状態を表すクラス(大体はenum)を、Data で状態が保持するデータクラスを指定する。基本的には共通して使える基底クラス、enumを指定する。
  - `startWith()` で開始状態を、`when()` で状態 S に対してメッセージ E を受信した場合の状態遷移とデータ更新を、`onTransition()` で状態遷移時のアクションをそれぞれmatcher的な書き方で組み込める。
  - 状態遷移は `when()` のmatcherの中で、`goTo(状態)` や `stay()` などを使って表現する。
  - データ更新は `goTo()` や `stay()` に `using()` をつなげて表現できる。
  - `onTransition()` の中では、状態遷移や状態変更は基本的には行えないものと思われる。状態や新旧データを所与のものとして用い、処理をする。
  - `when()` の中でもそれなりにロジックは書けるので、 `onTransition()` との使い分けが難しそう。
- 終了状態にするには `stop()` を呼ぶ。
- 終了時に何かするには `onTermination()` を使い、Normal/Shutdown/Failure と3種類の Reason クラスが用意されてるので、どれにmatchするかで終了処理をカスタマイズできる。
- timeoutイベントだけでなく、専用のtimerも持てる。

## Future

- https://doc.akka.io/docs/akka/2.5/futures.html
- Future : JavaのFutureではなく、Scalaが提供するFutureとなる。
- Actorシステムにおいては、`tell()` はメッセージを送ったらそのままの非同期処理。一方、`ask()`ではメッセージを送ったらFutureが返されるのを期待するので、それにより同期処理を実現できる。
  - `ask()` が返した Future を `Await.result()` や `Await.ready()` に渡すことで、メッセージを送ったActorからの返事を「待つ」ことが可能となる。
  - `pipe()` を使うと、Future をさらに別のactorに接続することもできる。
- Promise もあるが、全体的に Scala の Future/Promise を利用することになり、今回はそこまで追いきれなかった。(Scala の Future/Promise だけで相応の学習コストがかかる)
  - 参考:
  - https://qiita.com/reki2000/items/6acf94a07dee8d26a744
  - https://docs.scala-lang.org/ja/overviews/core/futures.html
  - https://dwango.github.io/scala_text/future-and-promise.html
- Future を組み合わせて map, sequence, traverse, fold, reduce, zip などの処理を行うこともできる。
- Future が完了した時に `onComplete()` で callback を実行させることができる。
  - `onSuccess()`, `onFailure()` でのcallback設定もあったが、deprecatedされた。`onComplete()` に設定する `OnComplete.onComplete()` の引数に失敗時の例外と、成功時の値が渡ってくるのでそちらで判断することになる。
- Patterns クラスのstaticメソッドで、よく使うイディオムが提供されている。
  - `ask()`, `pipe()` に加えて `after()`, `gracefulStop()` がある。
  - Java8の `CompletionStage` クラスを返すようにした PatternsCS クラスもあり、戻り値が `CompletionStage` となった各staticメソッドを提供している。
- Java8 で導入された `CompletionStage`, `CompletableFuture` クラスが Akka の Future によく似ている。
  - `scala-java8-compat` を導入すると、Scala Future を Java8 の `CompletionStage` に変換できる。
  - https://doc.akka.io/docs/akka/2.5/java8-compat.html
- Java8 CompletableFuture と `ask()` を組み合わせた例：
  - https://doc.akka.io/docs/akka/2.5/actors.html#ask-send-and-receive-future
- Java8 CompletionStage, CompletableFuture の解説：
  - https://qiita.com/subaru44k/items/d98ad79d21abccedb20b
- `Agents` というのもあったらしいが、非推奨になったのでスルー。

## Java8 ラムダ式参考

Java版の Akka の場合、公式ドキュメント上の解説でもJava8 ラムダ式を多用している。筆者自身、ラムダ式が勉強不足だったので、以下のサイトなどを参考に勉強した。
- "Java8 Lambdaまとめ"
  - https://qiita.com/tasogarei/items/60b5e55d8f42732686c6
- "Java8のラムダ式を理解する"
  - https://qiita.com/sano1202/items/64593e8e981e8d6439d3
- "Java8のラムダ式やStream APIでクールに例外を扱う"
  - https://qiita.com/myui/items/6597087d6a0648ca1dec
- "Java8のStreamやOptionalでクールに例外を処理する方法"
  - https://qiita.com/q-ikawa/items/3f55089e9081e1a854bc

Scala Future 参考：
- "Futureをマスターする -インスタンス操作-"
  - https://qiita.com/mtoyoshi/items/f68beb17710c3819697f

## その他

以下のトピックは 2018年3月～4月における勉強では範囲外とした。
- アクターの永続化（イベントソーシング, Akka Persistence）
- リモート処理
- メッセージのルーティングと負荷分散
- ストリーミング処理(Akka Stream)
- エンドポイント構築(Akka Http, Alpakka)
- クラスタリング(Akka Cluster)
- 保証配信チャネル
- メールボックス
  - https://doc.akka.io/docs/akka/2.5/mailboxes.html
  - 性能や最適化を行うときのチューニングポイントになりそう。ディスパッチャーなどからも利用される。
  - "Bounded" と "Unbounded" があるらしい。また優先メッセージなどのカスタマイズもある。
  - 詳しくは実際にチューニングや調整が必要になった時に再度調査。
- Circuit Breaker : Futureや分散環境を意識した高度なトピックスになるため、後日調査としておく。
  - https://doc.akka.io/docs/akka/2.5/common/circuitbreaker.html

## 参考書籍

- "Akka in Action", Raymond Roestenburg, Rob Bakker, and Rob Williams (Manning Publications)
  - https://www.manning.com/books/akka-in-action
  - https://github.com/RayRoestenburg/akka-in-action (サンプルコード)
- "Akka 実践バイブル", Raymond Roestenburg, Rob Bakker, Rob Williams, 訳 : 前出祐吾, 根来和輝, 釘屋二郎, 監訳 : TIS株式会社 (翔泳社)
  - https://www.shoeisha.co.jp/book/detail/9784798153278
  - "Akka in Action" の日本語訳
- 参考レビュー
  - http://takezoe.hatenablog.com/entry/20121209/p1
  - http://takezoe.hatenablog.com/entry/2017/12/12/124924


