# Junit5-kubernetes

Inspired by https://www.testcontainers.org/, this project aims at using a kubernetes pod directly form your junit5 test classes.
It hence fills the lack of kubernetes support of testcontainers while the library study it's implementation (https://github.com/testcontainers/testcontainers-java/issues/1135).

## Maven installation

Coming soon

## Gradle installation

Coming soon

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

By default `Junit5-kubernetes` will create a new pod for each of your tests. If you want ot reuse your pod accross your tests you can simply switch your `@KubernetesObject` to be `static`.

### GenericPodBuilder

`GenericPodBuilder` relies on [the official kubernetes java client](https://github.com/kubernetes-client/java). You can use it similarly as `V1PodBuilder`.

### PortMapper

In order to use a port you need to expose it to the host machine. `PortMapper` class is here to pick an available port for you and name it with the name you provided.
You can then use this port by calling `portMapper.getComputedPort(PORT_NAME)`.

### WaitStrategy

A `WaitStrategy` define the conditions your pod should meet before being considered as ready to execute your tests against.

Available `WaitStrategies` are :

 - `WaitRunningStatusStrategy` : Will wait until the Pod swith to "Running" phase (https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/)
 - `WaitLogStrategy` : Will wait for a particular event in the logs (eg. [TestWaitForLog.java](./core/src/test/java/com/github/jeanbaptistewatenberg/TestWaitForLog.java))