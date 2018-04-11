# study-akka-201803

2018年3月～4月にかけて Akka (Java版) の勉強をした時に作成したサンプルコード集

## Akka入門

- Akka とは
  - Lightbend社が開発した、並行・分散アプリケーション開発用の「アクター」ベースのツールキット。
- Akkaにおける「アクター」とは
  - 非同期メッセージングに応じた処理を行う軽量プロセス。
  - スレッドではなく、スレッド上で動作するワーカーみたいなもの。
  - スレッド上で、メッセージキューの出入りに応じて動作するアクターを切り替えていく。
    - そのためCPUやOSによるスレッド数の上限に縛られず、数万～百万単位のアクターを動かすことが可能となる。
- Scala版 / Java版の両方が提供されている。

### 公式サイト

- Akka公式サイト
  - http://akka.io/
- 公式ドキュメント
  - http://akka.io/docs/
    - Scala版 / Java版 それぞれドキュメントが用意されている。
  - 参考書籍など
    - https://doc.akka.io/docs/akka/2.5/additional/books.html
- Lightbend (Akka 開発元)
  - https://www.lightbend.com/
  - Scala や Play Frameworkや などの開発元でもあり、Reactにも関わっている。

### 入門者向けチュートリアル

公式ドキュメントの Introduction や Getting Started から入門者向けの概要・チュートリアルなどを参照できる。
- https://doc.akka.io/docs/akka/2.5/guide/introduction.html?language=java
  - Java版のイントロダクション（入門者向けの概要）
- https://developer.lightbend.com/guides/akka-quickstart-java/
  - イントロダクションからリンクされているクイックスタートガイド。
  - 挨拶文を表示する簡単なAkkaサンプルとなっている。
- https://doc.akka.io/docs/akka/2.5/guide/tutorial.html
  - イントロダクションの後半で紹介されている、実際にテストコードを書いたりしてActorrの使い方などAkkaの基礎を動かして学べるチュートリアル。

筆者個人の感想だが、公式のイントロダクションやチュートリアルが分かりやすかったので、英語ではあるが素直にこれらを読んで独習していくのがオススメ。

## 勉強メモなど

- [STUDY-MEMO-201803-04.md](STUDY-MEMO-201803-04.md) 参照

## requirement

* Java8

## 開発環境

* JDK >= 1.8.0_92
* Eclipse >= 4.5.2 (Mars.2 Release), "Eclipse IDE for Java EE Developers" パッケージを使用
* Maven >= 3.3.9 (maven-wrapperにて自動的にDLしてくれる)
* ソースコードやテキストファイル全般の文字コードはUTF-8を使用

## ビルドと実行

jarファイルをビルドして実行すると、コンソール上で動作するサンプルコードの一覧が表示されるので、動かしたいサンプルコードの番号を入力してください。サンプルコードの中身については対応するソースコードを参照してください。

```
cd study-akka-201803/

ビルド:
mvnw package

jarファイルから実行:
java -jar target/study-akka-201803-xxx.jar
--------------------
Welcome to akka demos!!
Demo No.[0] - akkastudy201803.demo.TickTackSchedulerDemo
(...)
Enter demo number (exit for -1):(動かしたいサンプルコードの番号を入力してENTER)
--------------------

Mavenプロジェクトから直接実行:
mvnw exec:java
```

## Eclipseプロジェクト用の設定

https://github.com/SecureSkyTechnology/howto-eclipse-setup の `setup-type1` を使用。README.mdで以下を参照のこと:

* Ecipseのインストール
* Clean Up/Formatter 設定
* GitでcloneしたMavenプロジェクトのインポート
