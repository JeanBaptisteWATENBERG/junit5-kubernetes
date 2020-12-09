# Junit5-kubernetes ![Java CI with Gradle](https://github.com/JeanBaptisteWATENBERG/junit5-kubernetes/workflows/Java%20CI%20with%20Gradle/badge.svg)

Inspired by https://www.testcontainers.org/, this project aims at using a kubernetes pod directly form your junit5 test classes.
It hence fills the lack of kubernetes support of testcontainers while the library study it's implementation (https://github.com/testcontainers/testcontainers-java/issues/1135).

## Maven installation

```xml
<dependency>
  <groupId>com.github.jeanbaptistewatenberg.junit5kubernetes</groupId>
  <artifactId>core</artifactId>
  <version>2.3.1-beta</version>
  <scope>test</scope>
</dependency>
```

## Gradle installation

```
testImplementation("com.github.jeanbaptistewatenberg.junit5kubernetes:core:2.3.1-beta")
```

## *NEW* 2.3.1-beta Usage

Version `2.3.1-beta` introduces a new java property `junitKubernetesUsePortService`,
 when it is set to true it will create a `NodePort` service aside of your pods in order to ease pods access from outside your kubernetes cluster.
 
I am going to test this approach in the upcoming days and stabilize it if I see some benefits from using it. 

If you want to test it out you can refer to [TestUsingNodePortService.java](./core/src/test/java/com/github/jeanbaptistewatenberg/junit5kubernetes/core/TestUsingNodePortService.java) and feel free to report any issues. 

## Usage

```java
@JunitKubernetes
public class Test {

    private static final String NGINX_PORT = "nginx-80";
    private final PortMapper portMapper = new PortMapper();

    @KubernetesObject
    private KubernetesGenericObject pod = new GenericPodBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("demonginx")
            .withImage("nginx")
            .addNewPort()
            .withHostPort(portMapper.computeAvailablePort(NGINX_PORT))
            .withContainerPort(80)
            .endPort()
            .endContainer()
            .endSpec()
            .withWaitStrategy(new WaitRunningStatusStrategy())
            .build();

    @Test
    void should_start_a_pod() throws IOException {
        assertThat(pod.getObjectHostIp()).isNotBlank();
        assertThat(portMapper.getComputedPort("nginx-80")).isNotNull();

        URL url = new URL("http://" + pod.getObjectHostIp() + ":" + portMapper.getComputedPort(NGINX_PORT));
        int status = responseStatus(url);
        assertThat(status).isEqualTo(200);
    }
}
```

By default `Junit5-kubernetes` will create a new pod for each of your tests. If you want to reuse your pod accross your tests you can simply switch your `@KubernetesObject` to be `static`.

### GenericPodBuilder

`GenericPodBuilder` relies on [the official kubernetes java client](https://github.com/kubernetes-client/java). You can use it similarly as `V1PodBuilder`.

### PortMapper

In order to use a port you need to expose it to the host machine. `PortMapper` class is here to pick an available port for you and name it with the name you provided.
You can then use this port by calling `portMapper.getComputedPort(PORT_NAME)`.

### WaitStrategy

A `WaitStrategy` define the conditions your pod should meet before being considered as ready to execute your tests against.

Available `WaitStrategies` are :

 - `PodWaitRunningStatusStrategy` : Will wait until the Pod swith to "Running" phase (https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/)
 - `PodWaitReadyStrategy` : Will wait until the Pod containers swith to "Ready" status (https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/)
 - `PodWaitLogStrategy` : Will wait for a particular event in the logs (eg. [TestWaitForLog.java](./core/src/test/java/com/github/jeanbaptistewatenberg/TestWaitForLog.java))
 
 ### Configuration options
 
 You can configure the extension using JVM system properties. Available properties are :
 
  - kubernetesNamespace : namespace in which you want the extension to deploy the objects
  - kubernetesPullSecrets : Comma separated pull secrets (eg : `-DkubernetesPullSecrets=secret1,secret2`)
  - junitKubernetesDebug : print advanced logs about what the extension does, however it will make waiters fails as kubernetes java client `watch` is not compatible with this option
  - junitKubernetesDisableHttp2 : it will set kubernetes client to use only http 1 instead of 2

## Common helpers

### PostgreSQL helper

#### Maven

```xml
<dependency>
  <groupId>com.github.jeanbaptistewatenberg.junit5kubernetes</groupId>
  <artifactId>postgresql</artifactId>
  <version>2.3.1-beta</version>
</dependency>
```

#### Gradle

```
testImplementation("com.github.jeanbaptistewatenberg.junit5kubernetes:postgresql:2.3.1-beta")
```

#### Usage

```java
@JunitKubernetes
public class Test {

    @KubernetesObject
    private PostgreSQLPod pod = new PostgreSQLPod();

    @Test
    void should_start_a_pod() throws IOException {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(pod.getJdbcUrl());
        hikariConfig.setUsername(pod.getUsername());
        hikariConfig.setPassword(pod.getPassword());

        try (HikariDataSource ds = new HikariDataSource(hikariConfig)) {
            Statement statement = ds.getConnection().createStatement();
            statement.execute("SELECT 1");
            ResultSet resultSet = statement.getResultSet();
            resultSet.next();

            int resultSetInt = resultSet.getInt(1);
            assertThat(resultSetInt).isEqualTo(1);
        }
    }
}
```

### RabbitMQ helper

#### Maven

```xml
<dependency>
  <groupId>com.github.jeanbaptistewatenberg.junit5kubernetes</groupId>
  <artifactId>rabbitmq</artifactId>
  <version>2.3.1-beta</version>
</dependency>
```

#### Gradle

```
testImplementation("com.github.jeanbaptistewatenberg.junit5kubernetes:rabbitmq:2.3.1-beta")
```

#### Usage

```java
@JunitKubernetes
public class Test {

    @KubernetesObject
    private RabbitMQPod pod = new RabbitMQPod();

    @Test
    void should_start_a_pod() throws IOException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(pod.getAmqpUrl());
        factory.setUsername(pod.getAdminUsername());
        factory.setPassword(pod.getAdminPassword());
        Connection conn = factory.newConnection();

        assertThat(conn).isNotNull();
    }
}
```

### ElasticSearch helper

#### Maven

```xml
<dependency>
  <groupId>com.github.jeanbaptistewatenberg.junit5kubernetes</groupId>
  <artifactId>elasticsearch</artifactId>
  <version>2.3.1-beta</version>
</dependency>
```

#### Gradle

```
testImplementation("com.github.jeanbaptistewatenberg.junit5kubernetes:elasticsearch:2.3.1-beta")
```

#### Usage

```java
@JunitKubernetes
public class Test {

    @KubernetesObject
    private ElasticSearchPod pod = new ElasticSearchPod();

    @Test
    void should_start_a_pod() throws IOException {
            HttpHost httpHost = new HttpHost(pod.getObjectHostIp(), pod.getHttpPort(), "http");
            RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(httpHost));
            IndexRequest indexRequest = new IndexRequest("anyindice");
            indexRequest.source(Collections.singletonMap("key","value"));
            IndexResponse response = restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
            assertThat(response.status()).isEqualTo(RestStatus.CREATED);
    }
}
```