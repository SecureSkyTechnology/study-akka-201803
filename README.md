# study-akka-201803
2018年3月～4月にかけて Akka (Java版) の勉強をした時に作成したサンプルコード集

## requirement

* Java8

## 開発環境

* JDK >= 1.8.0_92
* Eclipse >= 4.5.2 (Mars.2 Release), "Eclipse IDE for Java EE Developers" パッケージを使用
* Maven >= 3.3.9 (maven-wrapperにて自動的にDLしてくれる)
* ソースコードやテキストファイル全般の文字コードはUTF-8を使用

## ビルドと実行

```
cd study-akka-201803/

ビルド:
mvnw package

jarファイルから実行:
java -jar target/study-akka-201803-xxx.jar

Mavenプロジェクトから直接実行:
mvnw exec:java
```

## Eclipseプロジェクト用の設定

https://github.com/SecureSkyTechnology/howto-eclipse-setup の `setup-type1` を使用。README.mdで以下を参照のこと:

* Ecipseのインストール
* Clean Up/Formatter 設定
* GitでcloneしたMavenプロジェクトのインポート
