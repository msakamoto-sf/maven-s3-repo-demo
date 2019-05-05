# maven-s3-repo-demo

Mavenで非公開(not public)S3リポジトリにdeployするサンプルとデモ。

動作確認環境:
- Java11 (AdoptOpenJDK)
- Maven 3.5.2

## サンプルとデモの動かし方

### step1, deploy側の作業:

```
0. AWS CLIツールをインストールして、$HOME/.aws/{credentials|config}をセットアップしておく。

1. S3バケットを作成

2. 1. で作成したバケットに対してRead/Writeアクセスが許可されたAWSユーザのアクセスキーを $HOME/.aws/credentials に反映
(おそらく最低限、ListBucket/GetBucketLocation/GetObject/PutObject が必要)
(このユーザが、「成果物をS3にアップロードする」ロールになります)

3. $HOME/.m2/settings.xml に 2. のアクセスキーを反映
(詳細 : https://github.com/kuraun/aws-maven )

4. maven-s3-repo-demo-lib/pom.xml 中の s3:// で始まるバケット名を、1. で作成したバケット名に修正

5. maven-s3-repo-demo-lib をビルド -> デプロイ
$ cd maven-s3-repo-demo-lib/
$ ./mvnw clean package
$ ./mvnw deploy -P release
```

### step2, S3バケットのアクセス制御の調整:

- 6-A: 特定のAWSユーザ/IAMアカウント/ロールからのみ利用できるよう制限する。
  - -> 1. で作成したバケットに対してReadアクセスが許可されたAWSユーザのアクセスキーを $HOME/.aws/credentials に反映
  - (ListBucket/GetObject が必要)
  - (このユーザが、「成果物をS3からダウンロードする=利用する」ロールになります)
- 6-B: AWSユーザなら誰でも利用できるようパブリックRead可能にする。
  - -> 1. で作成したバケットに対して、Principal "*" について ListBucket/GetObject を Allow するバケットポリシーをattachする。
  - (詳細 : https://github.com/kuraun/aws-maven )
  - (Statement を2つ用意し、ListBucketばバケット名そのものに、GetObjectは `バケット名 + "/*"` に付与する点に注意。)
- NOTE-1: 6-Bの場合でも、利用する側でAWSアカウントが必要となる。AWSアカウントの無い状態でのアクセスはこのやり方では対応していない。
- NOTE-2: 6-Bの場合、2019年4月時点ではそもそもS3バケットへのパブリックアクセス自体が厳しくなってきており、パブリックアクセスやパブリックアクセスを許可するようなバケットポリシーのattachを制限するオプションがデフォルトで有効になっていたりする。そのため、6-Bを使うにはその辺りのオプション調整が必要となるかもしれない。
  - 2019年4月時点で実験したときは、S3バケットをマネジメントコンソールから作る時に、デフォルトで次のオプションがONになっている。
    - (a)「新規のパブリック ACL と、パブリックオブジェクトのアップロードをブロックする」
    - (b)「パブリック ACL を通じて付与されたパブリックアクセスを削除する」
    - (c)「新規のパブリックバケットポリシーをブロックする」
    - (d)「バケットにパブリックポリシーがある場合、パブリックアクセスとクロスアカウントアクセスをブロックする」
  - -> パブリックアクセスOKなバケットポリシーを設定する際に、一時的に上記4つのオプションをすべてOFFにしてから設定した。設定後は(a) - (c) はONに戻したが、(d)についてはOFFのままとして動作確認した。
  - 実運用ではより厳密な調査が必要となり、AWS S3ドキュメントを参照のこと。
- NOTE-3: バケットポリシーを使う場合、Conditionなどでより詳細な条件制御が可能となる。
  - 単にパブリックにするだけではなく、特定IPアドレスからのみ許可するなどの制御も可能。
  - ただしパブリックアクセスを許可しても、それはAWSアカウントが無くてもアクセスできることを意味しない。S3をHTTPで公開しているわけでもないため、S3バケットにAPIでアクセスする以上、AWSアカウントは必須となる。(実際、どこにもAWSアカウント情報がない場合、Mavenビルド時の最初のDLでアカウント情報が見つからずにエラーとなる)

### step3, ライブラリとして利用する側の作業:

```
1. maven-s3-repo-demo-client/pom.xml 中の s3:// で始まるバケット名を、1. で作成したバケット名に修正

2. maven-s3-repo-demo-client をビルド -> 動作確認
$ cd maven-s3-repo-demo-client/
$ ./mvnw clean package
$ java -jar target/maven-s3-repo-demo-client-1.0.jar
Hello, World! to abc.
```

## 細かい作業ログとメモ

### なぜ S3 リポジトリにdeployしようとしたか

- お仕事で、社内限定の非公開なJavaライブラリを作ろうと思った。
- 一応 Sonatype の Nexus は社内で動かしてるけど、インフラメンテが不要なS3にdeployできると楽そう。(社内限定でしか使わないので、ストレージ料金やリクエスト量などわずかで済む)
- ざっくりググると、以下のように spring がベースのMaven wagonプラグインで実現できるっぽい。
- Spring の aws-maven を利用するパターン:
  - spring-projects/aws-maven
    - https://github.com/spring-projects/aws-maven
  - Using S3 as Maven repository   ZestMoney Blog
    - https://blog.zestmoney.in/using-s3-as-maven-repository-4b96e8045ccf
  - AWSのS3サービスにMavenリポジトリを構築 - Qiita
    - https://qiita.com/akikinyan/items/1ead3a55ffaad585f455
  - Using an AWS S3 Bucket as your Maven Repository
    - https://tech.asimio.net/2018/06/27/Using-an-AWS-S3-Bucket-as-your-Maven-Repository.html
  - S3をmavenリポジトリとして使う - marblejediary
    - http://marblejenka.hatenablog.com/entry/20110423/1303558447
    - (2011年の記事と大分古く、springのアーティファクトIDも最新と異なる)
  - S3でmavenリポジトリをホストしてGradleでアップロードする
    - http://rejasupotaro.github.io/posts/2013/09/30/10/
  - AmazonS3に社内用Mavenリポジトリを作成し、Gradleから使う - Goalist Developers Blog
   - http://developers.goalist.co.jp/entry/2017/06/01/191213
- それ以外のwagon系プラグインを使った方法(古いのもあり、参考程度にして使わないほうが良さげ？)
  - Host Your Maven Artifacts Using Amazon S3 - DZone Cloud
    - https://dzone.com/articles/host-your-maven-artifacts-using-amazon-s3
  - Maven Deploy Artifacts to S3 Bucket ・ endeavor85/endeavor85.github.io Wiki
    - https://github.com/endeavor85/endeavor85.github.io/wiki/Maven-Deploy-Artifacts-to-S3-Bucket
  - jcaddel/maven-s3-wagon: Multi-threaded wagon to connect Maven with Amazon S3
    - https://github.com/jcaddel/maven-s3-wagon
- GitHubにもMavenリポジトリ作れるらしい。(ただ、private リポジトリを考えるといろいろ難しそうな予感がしたので今回は見送り)
  - GitHubにMavenリポジトリを作ってしまってライブラリを公開してしまおう - Qiita
    - https://qiita.com/narikei/items/db064f06e793372e7372
  - github/maven-plugins: Official GitHub Maven Plugins
    - https://github.com/github/maven-plugins

### サンプルのライブラリと、ライブラリのクライアントを作成

- 簡単なライブラリを `maven-s3-repo-demo-lib` として作ってみた。
  - `release` profileでは javadoc と sources のjarも生成するようにしてみた。
- このライブラリを使うクライアントとなるアプリを `maven-s3-repo-demo-client` として作ってみた。
- `maven-s3-repo-demo-lib` を `mvn install` でローカルインストールして、無事 `maven-s3-repo-demo-client` をビルド・実行成功。

archetype:generate からのコード生成:

```
$ mvn archetype:generate -DarchetypeArtifactId=maven-archetype-quickstart
(...)
Define value for property 'groupId': : com.example
Define value for property 'artifactId': : maven-s3-repo-demo-lib
Define value for property 'version':  1.0-SNAPSHOT: : 1.0
Define value for property 'package':  com.example: : com.example.lib

$ mvn archetype:generate -DarchetypeArtifactId=maven-archetype-quickstart
(...)
Define value for property 'groupId': : com.example
Define value for property 'artifactId': : maven-s3-repo-demo-client
Define value for property 'version':  1.0-SNAPSHOT: : 1.0
Define value for property 'package':  com.example: : com.example.client
```

ビルド:

```
$ cd maven-s3-repo-demo-lib/
$ mvn install -P release
( -> $HOME/.m2/repository/com/example/maven-s3-repo-demo-lib/ の下に成果物が配置される )

$ cd ../maven-s3-repo-demo-client/
$ mvn package
java -jar target/maven-s3-repo-demo-client-1.0.jar
Hello, World! to abc.
```

リリース時にのみ javadoc と sources のjarファイルを生成し、deployに含める時の参考:

- Maven – Cookbook - How to attach source and javadoc artifacts
  - https://maven.apache.org/plugin-developers/cookbook/attach-source-javadoc-artifacts.html
- Apache Maven Javadoc Plugin – Frequently Asked Questions -> How to deploy Javadoc jar file?
  - https://maven.apache.org/plugins/maven-javadoc-plugin/faq.html#How_to_deploy_Javadoc_jar_file

### S3 Bucket の作成

- `maven-s3-repo-demo` というBucketを AWS のWebコンソールから作成。
- リージョンは アジアパシフィック (東京) (= `ap-northeast-1`) にした。
  - see: https://docs.aws.amazon.com/ja_jp/general/latest/gr/rande.html
- それ以外の設定はすべてデフォルトにした。バケットとオブジェクトは非公開。

### AWS Maven Wagon を pom.xml に組み込み、settings.xml にアクセスキーを登録

https://github.com/spring-projects/aws-maven の Usage 解説に従い、 `maven-s3-repo-demo-lib` の  pom.xml を設定:

```xml

  <distributionManagement>
    <repository>
      <id>aws-release</id>
      <url>s3://maven-s3-repo-demo/release</url>
    </repository>
    <snapshotRepository>
      <id>aws-snapshot</id>
      <url>s3://maven-s3-repo-demo/snapshot</url>
    </snapshotRepository>
  </distributionManagement>

  <build>
    <extensions>
      <extension>
        <groupId>org.springframework.build</groupId>
        <artifactId>aws-maven</artifactId>
        <version>5.0.0.RELEASE</version>
      </extension>
    </extensions>
    <!-- (...) -->
  </build>
```

`$HOME/.m2/settings.xml` にアクセスキーを登録(今回はS3を作ったユーザのアクセスキーを指定した。おそらく S3のGET系だけでなく、putObjectも使えるロール/ポリシーがアタッチされてることがポイントと思われる):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <!-- (...) -->
  <servers>
    <server>
      <id>aws-release</id>
      <username>(your access key id)</username>
      <password>(your securet access key)</password>
    </server>
    <server>
      <id>aws-snapshot</id>
      <username>(your access key id)</username>
      <password>(your securet access key)</password>
    </server>
  </servers>
  <!-- (...) -->
</settings>
```

### mvn deploy -> 403 Forbidden で失敗

これで `mvn deploy` を実行すると、以下のように **403, AccessDenied エラーが発生してしまった。**

```
[INFO] --- maven-deploy-plugin:2.7:deploy (default-deploy) @ maven-s3-repo-demo-lib ---
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.jar
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  7.396 s
[INFO] Finished at: 2019-04-14T18:39:47+09:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-deploy-plugin:2.7:deploy (default-deploy) on project maven-s3-repo-demo-lib: Failed to deploy artifacts: Could not transfer artifact com.example:maven-s3-repo-demo-lib:jar:1.0 from/to aws-release (s3://maven-s3-repo-demo/release): Cannot write directory 'release/': Status Code: 403, AWS Service: Amazon S3, AWS Request ID: 743D4EC10A68B5D4, AWS Error Code: AccessDenied, AWS Error Message: Access Denied -> [Help 1]
```

`mvn deploy -X > fail.log` で詳細デバッグログを保存してみると、GETリクエストは成功しているが、PUTリクエストが403になってしまっている。

```
(...)
[DEBUG] Sending request: GET /?location HTTP/1.1
[DEBUG] >> "GET /?location HTTP/1.1[\r][\n]"
[DEBUG] >> "Host: maven-s3-repo-demo.s3.amazonaws.com[\r][\n]"
[DEBUG] >> "Authorization: AWS (...)[\r][\n]"
[DEBUG] >> "User-Agent: aws-sdk-java/1.7.1 Windows_10/10.0 OpenJDK_64-Bit_Server_VM/11.0.2+9/11.0.2[\r][\n]"
[DEBUG] >> "Date: Sun, 14 Apr 2019 09:44:05 GMT[\r][\n]"
[DEBUG] >> "Content-Type: application/x-www-form-urlencoded; charset=utf-8[\r][\n]"
[DEBUG] >> "Connection: Keep-Alive[\r][\n]"
[DEBUG] >> "[\r][\n]"
(...)
[DEBUG] << "HTTP/1.1 200 OK[\r][\n]"
[DEBUG] << "x-amz-id-2: (...)[\r][\n]"
[DEBUG] << "x-amz-request-id: 9205C96CD5843CFB[\r][\n]"
[DEBUG] << "Date: Sun, 14 Apr 2019 09:44:08 GMT[\r][\n]"
[DEBUG] << "Content-Type: application/xml[\r][\n]"
[DEBUG] << "Transfer-Encoding: chunked[\r][\n]"
[DEBUG] << "Server: AmazonS3[\r][\n]"
[DEBUG] << "[\r][\n]"
[DEBUG] Receiving response: HTTP/1.1 200 OK
(...)
[DEBUG] Sending request: PUT /release/ HTTP/1.1
[DEBUG] >> "PUT /release/ HTTP/1.1[\r][\n]"
[DEBUG] >> "Host: maven-s3-repo-demo.s3-ap-northeast-1.amazonaws.com[\r][\n]"
[DEBUG] >> "Authorization: AWS (...)[\r][\n]"
[DEBUG] >> "x-amz-acl: public-read[\r][\n]"
[DEBUG] >> "User-Agent: aws-sdk-java/1.7.1 Windows_10/10.0 OpenJDK_64-Bit_Server_VM/11.0.2+9/11.0.2[\r][\n]"
[DEBUG] >> "Date: Sun, 14 Apr 2019 09:44:06 GMT[\r][\n]"
[DEBUG] >> "Content-Type: application/octet-stream[\r][\n]"
[DEBUG] >> "Content-Length: 0[\r][\n]"
[DEBUG] >> "Connection: Keep-Alive[\r][\n]"
[DEBUG] >> "[\r][\n]"
(...)
[DEBUG] << "HTTP/1.1 403 Forbidden[\r][\n]"
[DEBUG] << "x-amz-request-id: 8CAB9576ABEFCD69[\r][\n]"
[DEBUG] << "x-amz-id-2: (...)[\r][\n]"
[DEBUG] << "Content-Type: application/xml[\r][\n]"
[DEBUG] << "Transfer-Encoding: chunked[\r][\n]"
[DEBUG] << "Date: Sun, 14 Apr 2019 09:44:06 GMT[\r][\n]"
[DEBUG] << "Server: AmazonS3[\r][\n]"
[DEBUG] << "[\r][\n]"
[DEBUG] Receiving response: HTTP/1.1 403 Forbidden
```

### 念の為 AWS CLI から S3 にアクセス -> OK

アクセスキーが間違ってる可能性を疑い、AWS CLI から S3 にアクセスしてみた。 `$HOME/.aws/credentials` の `[default]` の aws_access_key_id, aws_secret_access_key が settings.xml で設定した値と同じことを確認した上で、`aws s3 cp` でローカルファイルをS3バケットにputし、`aws s3 ls` でバケットの中身を読んでみたところ、正常に動作した。

```
$ aws s3 cp pom.xml s3://maven-s3-repo-demo/foo/pom.xml
upload: .\pom.xml to s3://maven-s3-repo-demo/foo/pom.xml

$ aws s3 ls s3://maven-s3-repo-demo/foo/
2019-04-14 18:53:10       3658 pom.xml
```

よってこの時点でアクセスキーの問題であることは考えづらい。

アクセスキーに紐づくユーザも、そもそも Administrator グループのユーザとしており、アタッチしているポリシーも以下のように管理者用の「全部許可」ポリシーになっている。そのため、ポリシーによるアクセス制御の問題であるとも考えづらい。

```
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "*",
      "Resource": "*"
    }
  ]
}
```

※また、逆に settings.xml 側の username/password を無効な値にしてみて `mvn deploy` してみたところ、明らかに認証エラーとわかるエラーに変化した。このことから、認証自体は通っており、その後の実際のS3オブジェクトのアクセスで403, "Cannot write directory" が発生したことを確信できる。

```
The AWS Access Key Id you provided does not exist in our records. (Service: Amazon S3; Status Code: 403; Error Code: InvalidAccessKeyId;...)
```

### forkされた OttoGroupSolutionProvider/aws-maven を使ってみるも、403で失敗する症状変化せず

- https://github.com/spring-projects/aws-maven
  - -> そもそもソースコードの最終更新が "5 years ago" と非常に古い。
- https://github.com/spring-projects/aws-maven/issues/68
  - -> Java11サポートを追加したforkの情報が掲載されている。
- https://search.maven.org/search?q=aws-maven
  - -> 6.0.0 や 7.0.0 などより新しいバージョンのforkリリースが確認できる。
- 新し目の fork は次の2つ。
- https://github.com/OttoGroupSolutionProvider/aws-maven
  - Java11 サポート追加やその他の修正に対応し、2019年2月に 6.0.1 をリリース。
- https://github.com/kuraun/aws-maven
  - AWS SDK for Java を Version 2 系にアップデートし、2019年2月に 7.0.0.RELEASE をリリース。
  - リリースタグをよく見ると、どうも 6.0.0 を共通として OttoGroupSolutionProvider/aws-maven とこちらに分派したっぽい。

今回は一応開発環境でJava11を使っていることもあり、 https://github.com/OttoGroupSolutionProvider/aws-maven を試してみた。

pom.xml では以下のように build extension を修正した:

```xml
  <build>
    <extensions>
        <groupId>com.github.ottogroupsolutionprovider</groupId>
        <artifactId>aws-maven</artifactId>
        <version>6.0.1</version>
    <!-- (...) -->
  </build>
```

settings.xml では以下のように `<configuration> - <wagonProvider>s3</wagonProvider>` を `<server>` に追加した:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <!-- (...) -->
  <servers>
    <server>
      <id>aws-release</id>
      <username>(your access key id)</username>
      <password>(your securet access key)</password>
      <configuration>
        <wagonProvider>s3</wagonProvider>
      </configuration>
    </server>
    <server>
      <id>aws-snapshot</id>
      <username>(your access key id)</username>
      <password>(your securet access key)</password>
      <configuration>
        <wagonProvider>s3</wagonProvider>
      </configuration>
    </server>
  </servers>
  <!-- (...) -->
</settings>
```

その結果、変更前と同様、403 + "Cannot write directory" エラーが発生してしまった。

```
[INFO] --- maven-deploy-plugin:2.7:deploy (default-deploy) @ maven-s3-repo-demo-lib ---
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.jar
[WARNING] The legacy profile format requires the 'profile ' prefix before the profile name. The latest code does not require such prefix, and will consider it as part of the profile name. Please remove the prefix if you are seeing this warning.
[WARNING] The legacy profile format requires the 'profile ' prefix before the profile name. The latest code does not require such prefix, and will consider it as part of the profile name. Please remove the prefix if you are seeing this warning.
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  6.788 s
[INFO] Finished at: 2019-04-14T19:10:13+09:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-deploy-plugin:2.7:deploy (default-deploy) on project maven-s3-repo-demo-lib: Failed to deploy artifacts: Could not transfer artifact com.example:maven-s3-repo-demo-lib:jar:1.0 from/to aws-release (s3://maven-s3-repo-demo/release): Cannot write directory 'release/': Access Denied (Service: Amazon S3; Status Code: 403; Error Code: AccessDenied; Request ID: DD0484E288A1D298; S3 Extended Request ID: (...)) -> [Help 1]
```

`mvn deploy -X` で詳細デバッグログを見てみると、以下のように HEAD -> GET で Location を取得するところまでは正常に動いているが、肝心の PUT リクエストでやはり 403 が発生していた。

```
[DEBUG] http-outgoing-0 >> "HEAD / HTTP/1.1[\r][\n]"
[DEBUG] http-outgoing-0 >> "Host: maven-s3-repo-demo.s3.ap-northeast-1.amazonaws.com[\r][\n]"
(...)
[DEBUG] http-outgoing-0 >> "Content-Type: application/octet-stream[\r][\n]"
[DEBUG] http-outgoing-0 >> "Connection: Keep-Alive[\r][\n]"
[DEBUG] http-outgoing-0 >> "[\r][\n]"

[DEBUG] http-outgoing-0 << HTTP/1.1 200 OK
(...)
[DEBUG] http-outgoing-0 << Date: Sun, 14 Apr 2019 10:14:04 GMT
[DEBUG] http-outgoing-0 << x-amz-bucket-region: ap-northeast-1
[DEBUG] http-outgoing-0 << Content-Type: application/xml
[DEBUG] http-outgoing-0 << Transfer-Encoding: chunked
[DEBUG] http-outgoing-0 << Server: AmazonS3

[DEBUG] http-outgoing-0 >> "GET /?location HTTP/1.1[\r][\n]"
[DEBUG] http-outgoing-0 >> "Host: maven-s3-repo-demo.s3.ap-northeast-1.amazonaws.com[\r][\n]"
(...)
[DEBUG] http-outgoing-0 >> "Content-Type: application/octet-stream[\r][\n]"
[DEBUG] http-outgoing-0 >> "Content-Length: 0[\r][\n]"
[DEBUG] http-outgoing-0 >> "Connection: Keep-Alive[\r][\n]"
[DEBUG] http-outgoing-0 >> "[\r][\n]"

[DEBUG] http-outgoing-0 << HTTP/1.1 200 OK
(...)
[DEBUG] http-outgoing-0 << Date: Sun, 14 Apr 2019 10:14:04 GMT
[DEBUG] http-outgoing-0 << Content-Type: application/xml
[DEBUG] http-outgoing-0 << Transfer-Encoding: chunked
[DEBUG] http-outgoing-0 << Server: AmazonS3
[DEBUG] Connection can be kept alive for 60000 MILLISECONDS
[DEBUG] Parsing XML response document with handler: class com.amazonaws.services.s3.model.transform.XmlResponsesSaxParser$BucketLocationHandler
[DEBUG] http-outgoing-0 << "8e[\r][\n]"
[DEBUG] http-outgoing-0 << "<?xml version="1.0" encoding="UTF-8"?>[\n]"
[DEBUG] http-outgoing-0 << "<LocationConstraint xmlns="http://s3.amazonaws.com/doc/2006-03-01/">ap-northeast-1</LocationConstraint>[\r][\n]"
[DEBUG] http-outgoing-0 << "0[\r][\n]"
[DEBUG] http-outgoing-0 << "[\r][\n]"


[DEBUG] http-outgoing-1 >> "PUT /release/ HTTP/1.1[\r][\n]"
[DEBUG] http-outgoing-1 >> "Host: maven-s3-repo-demo.s3.ap-northeast-1.amazonaws.com[\r][\n]"
(...)
[DEBUG] http-outgoing-1 >> "Content-Type: application/octet-stream[\r][\n]"
[DEBUG] http-outgoing-1 >> "Content-Length: 0[\r][\n]"
[DEBUG] http-outgoing-1 >> "Connection: Keep-Alive[\r][\n]"
[DEBUG] http-outgoing-1 >> "[\r][\n]"

[DEBUG] http-outgoing-1 << HTTP/1.1 403 Forbidden
(...)
[DEBUG] http-outgoing-1 << Content-Type: application/xml
[DEBUG] http-outgoing-1 << Transfer-Encoding: chunked
[DEBUG] http-outgoing-1 << Date: Sun, 14 Apr 2019 10:14:03 GMT
[DEBUG] http-outgoing-1 << Server: AmazonS3
[DEBUG] Connection can be kept alive for 60000 MILLISECONDS
[DEBUG] http-outgoing-1 << "f3[\r][\n]"
[DEBUG] http-outgoing-1 << "<?xml version="1.0" encoding="UTF-8"?>[\n]"
[DEBUG] http-outgoing-1 << "<Error><Code>AccessDenied</Code><Message>Access Denied</Message><RequestId>(...)</RequestId><HostId>(...)</HostId></Error>[\r][\n]"
[DEBUG] http-outgoing-1 << "0[\r][\n]"
[DEBUG] http-outgoing-1 << "[\r][\n]"
```

#### AWS SDK for Java Version 1 が原因かと自分で putObject するJavaサンプルコードを組んでみるがそちらは正常に動作する。

この段階で、AWS SDK for Java あたりが問題ではないか・・・？と疑いだして、自分でputObjectするJavaサンプルコードを組んでみた。

pom.xml: SDKのバージョンは OttoGroupSolutionProvider/aws-maven と同じバージョンに揃えた。

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>aws-sdk-java-v1-s3-demo</artifactId>
  <packaging>jar</packaging>
  <version>1.0-SNAPSHOT</version>
  <name>aws-sdk-java-v1-s3-demo</name>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-java-sdk-bom</artifactId>
        <version>1.11.276</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
     <dependency>
          <groupId>com.amazonaws</groupId>
          <artifactId>aws-java-sdk-s3</artifactId>
    </dependency>
    <dependency>
      <groupId>javax.xml.bind</groupId>
      <artifactId>jaxb-api</artifactId>
      <version>2.3.0</version>
    </dependency>
    <dependency>
      <groupId>com.sun.xml.bind</groupId>
      <artifactId>jaxb-core</artifactId>
      <version>2.3.0</version>
    </dependency>
    <dependency>
      <groupId>com.sun.xml.bind</groupId>
      <artifactId>jaxb-impl</artifactId>
      <version>2.3.0</version>
    </dependency>
    <dependency>
      <groupId>javax.activation</groupId>
      <artifactId>activation</artifactId>
      <version>1.1.1</version>
    </dependency>
  </dependencies>
</project>
```

src/main/com/example/App.java:

```java
package com.example;

import java.io.File;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

public class App {
    public static void main(String[] args) {
        final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
        try {
            s3.putObject("maven-s3-repo-demo", "foo/bar.txt", new File("(適当なローカルファイル)"));
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        }
        System.out.println("Done!");
    }
}
```

こちらは正常にアップロードできた。

参考:
- https://docs.aws.amazon.com/ja_jp/sdk-for-java/v1/developer-guide/welcome.html
- https://docs.aws.amazon.com/ja_jp/sdk-for-java/v1/developer-guide/examples-s3-objects.html
- https://github.com/awsdocs/aws-doc-sdk-examples/blob/master/java/example_code/s3/src/main/java/aws/example/s3/PutObject.java
- Java11 だと java.lang.NoClassDefFoundError: javax/xml/bind/JAXBException が発生してしまい、以下のSO記事を参考に xml や javax を依存関係に追加した:
  - jaxb - How to resolve java.lang.NoClassDefFoundError: javax/xml/bind/JAXBException in Java 9 - Stack Overflow
    - https://stackoverflow.com/questions/43574426/how-to-resolve-java-lang-noclassdeffounderror-javax-xml-bind-jaxbexception-in-j
  - jaxb - Java 11 package javax.xml.bind does not exist - Stack Overflow
    - https://stackoverflow.com/questions/52502189/java-11-package-javax-xml-bind-does-not-exist

### forkされた kuraun/aws-maven (AWS SDK for Java Version 2系列) にしたら deploy 成功

もうダメ元というかヤケクソ的に、残った最新forkである [kuraun/aws-maven](https://github.com/kuraun/aws-maven) を試してみた。

pom.xml では以下のように build extension を修正した:

```xml
  <build>
    <extensions>
        <groupId>io.github.kuraun</groupId>
        <artifactId>aws-maven</artifactId>
        <version>7.0.0.RELEASE</version>
    <!-- (...) -->
  </build>
```

settings.xml はそのままで修正不要。

`mvn deploy` をしてみると・・・ようやく BUILD SUCCESS し、S3 側でもアップロードされたファイルを確認できた！！

```
[INFO] --- maven-deploy-plugin:2.7:deploy (default-deploy) @ maven-s3-repo-demo-lib ---
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.jar
[WARNING] Ignoring profile 'admin' on line 4 because it did not start with 'profile ' and it was not 'default'.
Uploaded to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.jar (4.6 kB at 1.6 kB/s)
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom
Uploaded to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom (3.6 kB at 2.5 kB/s)
Downloading from aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/maven-metadata.xml
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/maven-metadata.xml
Uploaded to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/maven-metadata.xml (309 B at 316 B/s)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

`mvn deploy -P release` してみると、sources/javadoc のjarファイルもアップロードされる。

```
[INFO] --- maven-deploy-plugin:2.7:deploy (default-deploy) @ maven-s3-repo-demo-lib ---
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.jar
[WARNING] Ignoring profile 'admin' on line 4 because it did not start with 'profile ' and it was not 'default'.
Uploaded to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.jar (4.6 kB at 1.4 kB/s)
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom
Uploaded to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom (3.6 kB at 2.5 kB/s)
Downloading from aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/maven-metadata.xml
Downloaded from aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/maven-metadata.xml (309 B at 2.0 kB/s)
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/maven-metadata.xml
Uploaded to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/maven-metadata.xml (309 B at 285 B/s)
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0-javadoc.jar
Uploaded to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0-javadoc.jar (406 kB at 226 kB/s)
Uploading to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0-sources.jar
Uploaded to aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0-sources.jar (2.9 kB at 2.3 kB/s)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  23.835 s
[INFO] Finished at: 2019-04-14T19:35:00+09:00
[INFO] ------------------------------------------------------------------------
```

AWS CLI から確認できた様子：

```
$ aws s3 ls s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/
2019-04-14 19:35:02      10000
2019-04-14 19:35:00     405801 maven-s3-repo-demo-lib-1.0-javadoc.jar
2019-04-14 19:35:01         32 maven-s3-repo-demo-lib-1.0-javadoc.jar.md5
2019-04-14 19:35:00         40 maven-s3-repo-demo-lib-1.0-javadoc.jar.sha1
2019-04-14 19:35:01       2913 maven-s3-repo-demo-lib-1.0-sources.jar
2019-04-14 19:35:02         32 maven-s3-repo-demo-lib-1.0-sources.jar.md5
2019-04-14 19:35:02         40 maven-s3-repo-demo-lib-1.0-sources.jar.sha1
2019-04-14 19:34:55       4611 maven-s3-repo-demo-lib-1.0.jar
2019-04-14 19:34:56         32 maven-s3-repo-demo-lib-1.0.jar.md5
2019-04-14 19:34:56         40 maven-s3-repo-demo-lib-1.0.jar.sha1
2019-04-14 19:34:57       3649 maven-s3-repo-demo-lib-1.0.pom
2019-04-14 19:34:58         32 maven-s3-repo-demo-lib-1.0.pom.md5
2019-04-14 19:34:57         40 maven-s3-repo-demo-lib-1.0.pom.sha1
```

### ライブラリを参照するクライアント側のpom.xml設定

ここまでは「ライブラリ側」の setting.xml や pom.xml 設定。

今度は、ライブラリを参照する側の設定を見ていく。

まず settings.xml に追加した `<server>` エントリーをすべて削除し、pom.xmlでも特にリポジトリ設定が無い状態にして、ビルドしてみる。

```
$ cd maven-s3-repo-demo-client/
$ mvn package
(...)
[INFO] --------------------------------[ jar ]---------------------------------
Downloading from central: https://repo.maven.apache.org/maven2/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom
[WARNING] The POM for com.example:maven-s3-repo-demo-lib:jar:1.0 is missing, no dependency information available
Downloading from central: https://repo.maven.apache.org/maven2/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.jar
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  3.027 s
[INFO] Finished at: 2019-04-14T20:37:28+09:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal on project maven-s3-repo-demo-client: Could not resolve dependencies for project com.example:maven-s3-repo-demo-client:jar:1.0: Could not find artifact com.example:maven-s3-repo-demo-lib:jar:1.0 in central (https://repo.maven.apache.org/maven2) -> [Help 1]
```

当然、リポジトリが見つからず失敗する。エラーもシンプルな "Could not find artifact com.example:maven-s3-repo-demo-lib:jar:1.0" だけ。

続いて maven-s3-repo-demo-client の pom.xml に以下を挿入し、ビルドしてみる。

```xml
(...)
  </properties>

  <repositories>
    <repository>
      <id>aws-release</id>
      <url>s3://maven-s3-repo-demo/release</url>
      <releases>
        <enabled>true</enabled>
      </releases>
      <snapshots>
        <enabled>false</enabled>
      </snapshots>
    </repository>
    <repository>
      <id>aws-snapshot</id>
      <url>s3://maven-s3-repo-demo/snapshot</url>
      <releases>
        <enabled>false</enabled>
      </releases>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>
  </repositories>

  <dependencies>
(...)
```

`mvn clean package` :

```
(...)
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
(...)
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal on project maven-s3-repo-demo-client: Could not resolve dependencies for project com.example:maven-s3-repo-demo-client:jar:1.0: Failed to collect dependencies at com.example:maven-s3-repo-demo-lib:jar:1.0: Failed to read artifact descriptor for com.example:maven-s3-repo-demo-lib:jar:1.0: Could not transfer artifact com.example:maven-s3-repo-demo-lib:pom:1.0 from/to aws-release (s3://maven-s3-repo-demo/release): Cannot access s3://maven-s3-repo-demo/release with type default using the available connector factories: BasicRepositoryConnectorFactory: Cannot access s3://maven-s3-repo-demo/release using the registered transporter factories: WagonTransporterFactory: java.util.NoSuchElementException
```

エラーメッセージが長いが、どうも `s3://maven-s3-repo-demo/release` に対応するwagon側のクラスが見つからず、factoryでエラーになっているように思える。

そこで、ライブラリ側と同様に pom.xml の `build` に `extension` を追加する:

```xml
(...)
  <build>
    <extensions>
      <extension>
        <groupId>io.github.kuraun</groupId>
        <artifactId>aws-maven</artifactId>
        <version>7.0.0.RELEASE</version>
      </extension>
    </extensions>
    <plugins>
(...)
```

`mvn clean package` :

```
(...)
[INFO] --------------------------------[ jar ]---------------------------------
Downloading from aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom
[WARNING] Ignoring profile 'admin' on line 4 because it did not start with 'profile ' and it was not 'default'.
Downloaded from aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom (3.6 kB at 1.9 kB/s)
Downloading from aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.jar
Downloaded from aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.jar (4.6 kB at 20 kB/s)
(...)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
(...)
```

なんと、S3から正常にjarをDLして、ビルドに成功してしまった・・・。

原因は認証情報の読み込みが `$HOME/.aws/credentials` に自動でfallbackされ、そちらのキーが使われたため。

-> `$HOME/.aws/credentials` を適当にリネームし、さらに `$HOME/.m2/repository/com/example/` 以下から先程DLされてしまったキャッシュも削除してもう一度 `mvn clean package` すると・・・

```
[INFO] --------------------------------[ jar ]---------------------------------
Downloading from aws-release: s3://maven-s3-repo-demo/release/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom
[WARNING] Ignoring profile 'admin' on line 4 because it did not start with 'profile ' and it was not 'default'.
Downloading from central: https://repo.maven.apache.org/maven2/com/example/maven-s3-repo-demo-lib/1.0/maven-s3-repo-demo-lib-1.0.pom
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  4.272 s
[INFO] Finished at: 2019-04-14T20:51:27+09:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal on project maven-s3-repo-demo-client: \
  Could not resolve dependencies for project com.example:maven-s3-repo-demo-client:jar:1.0: \
  Failed to collect dependencies at com.example:maven-s3-repo-demo-lib:jar:1.0: \
  Failed to read artifact descriptor for com.example:maven-s3-repo-demo-lib:jar:1.0: \
  Could not transfer artifact com.example:maven-s3-repo-demo-lib:pom:1.0 from/to aws-release (s3://maven-s3-repo-demo/release): \
  Unable to load credentials from any of the providers in the chain AwsCredentialsProviderChain(\
    credentialsProviders=[SystemPropertyCredentialsProvider(), EnvironmentVariableCredentialsProvider(), \
    ProfileCredentialsProvider(), ContainerCredentialsProvider(), InstanceProfileCredentialsProvider()]) : \
    [SystemPropertyCredentialsProvider(): Unable to load credentials from system settings. \
    Access key must be specified either via environment variable (AWS_ACCESS_KEY_ID) or system property (aws.accessKeyId)., \
    EnvironmentVariableCredentialsProvider(): Unable to load credentials from system settings. \
    Access key must be specified either via environment variable (AWS_ACCESS_KEY_ID) or system property (aws.accessKeyId)., \
    ProfileCredentialsProvider(): (...)
```

今度は想定通り、認証情報が見つからずエラーになってくれました。

試しに `$HOME/.m2/settings.xml` の `<server>` でアクセスキー指定を戻してみましたが、これも上記と同じエラー。

**利用する側では settings.xml の `<server>` は無視するようです。**
推測ですが、mavenプラグイン側もAWS SDK for Javaを使ってる以上、AWS SDK がサポートしているアクセスキーの取得方式に従っているのではないでしょうか。deployのみ、特別に settings.xml の `<server>` 要素「も」参照しているように思います。(実際、 https://github.com/kuraun/aws-maven のREADME.mdにもそのような記述があります)

試しに `AWS_ACCESS_KEY_ID` と `AWS_SECRET_ACCESS_KEY` 環境変数を設定し、`aws s3 cli` が動作するのを確認して `mvn clean package` したところ S3 からjarをダウンロードし、正常にビルド完了しました。

**いずれにせよ、アクセスにはAWSユーザのアカウントが必要になるようです。**

今回はテスト用に、`maven-s3-repo-demo` S3 Bucketに対して必要最小限のアクセス権限を有するユーザを作成し、 `$HOME/.aws/credentials` もそれに合わせて書き換えてみます。**つまり、ライブラリの「開発者」と「利用者」を明確に分離してみます。**

1. AWSから、どのグループにも属さず、ポリシーも何もアタッチしていないユーザを作成します。Webコンソールにはアクセスさせない、ツールやSDK用のユーザとして作成します。今回は `lowpriv1` というユーザを作成しました。
3. `$HOME/.aws/credentials` の `[default]` のIDとキーを、 `lowpriv1` 用に変更します。
4. 以下の内容でポリシーを新規に作成し、`lowpriv1` のユーザにアタッチし、 `aws s3 ls s3://maven-s3-repo-demo/` で指定のbucket以下にreadアクセスできることを確認します。

Mavenからのアクセス用の最低限度のポリシー : `ListBucket` と `GetObject` を許可する。

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "MavenS3RepositoryDemo20190414",
            "Effect": "Allow",
            "Action": [
                "s3:GetObject",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::maven-s3-repo-demo",
                "arn:aws:s3:::maven-s3-repo-demo/*"
            ]
        }
    ]
}
```

ここまで確認できたら、`mvn clean package` でビルドします。
**今度は正常にjarファイルをDLし、ビルド成功しました。**
(なお `lowpriv1` ユーザからこのポリシーを削除すると、今度はjarがDLできずにビルド失敗します。)

- https://github.com/kuraun/aws-maven のREADME.md では、バケットの方に `ListBucket/GetObject` を公開で許可するポリシー(`Principal` が `*`)をattachしています。
- 全世界に対して公開するライブラリならそれでも構いませんが、内部でだけ使いたい場合は、今回の例のように個別にポリシーを調整します。

最後にもう一度 `$HOME/.m2/repository/com/example/maven-s3-repo-demo-lib` を削除し、Eclipse上で maven-s3-repo-demo-client を読み込んで、Eclipse上からもS3の成果物をDLしてビルド成功することを確認しました。

### バケットポリシーを使ったS3バケット公開

- S3バケットを作って、Write権限ありのIAMユーザでアップロードするところまでは同じ。
- 違うのは利用する側向けのアクセス制御で、 **こちらはS3バケットの方にバケットポリシーで公開条件を設定する。**
- 詳細は kuraun/aws-maven に書かれている通りだが、2019年4月時点ではS3バケットのパブリックアクセスを制限する方向にいろいろオプションが追加されている。そのため、パブリックアクセスを許可するようなバケットポリシーを設定するには、オプションの調整が必要になる。
  - 実験時の詳細は「サンプルとデモの動かし方」 -> step2 -> NOTE-2 参照。
- バケットポリシーを使う場合、Conditionなどでより詳細な条件制御が可能となる。
  - 単にパブリックにするだけではなく、特定IPアドレスからのみ許可するなどの制御も可能。
- ただしパブリックアクセスを許可しても、それはAWSアカウントが無くてもアクセスできることを意味しない。S3をHTTPで公開しているわけでもないため、S3バケットにAPIでアクセスする以上、AWSアカウントは必須となる。(実際、どこにもAWSアカウント情報がない場合、Mavenビルド時の最初のDLでアカウント情報が見つからずにエラーとなる)

以上
