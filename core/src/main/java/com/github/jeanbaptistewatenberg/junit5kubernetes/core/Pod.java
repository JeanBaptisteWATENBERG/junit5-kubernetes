package com.github.jeanbaptistewatenberg.junit5kubernetes.core;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.WaitStrategy;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.Copy;
import io.kubernetes.client.Exec;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import io.kubernetes.client.util.Yaml;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Pod extends KubernetesGenericObject<Pod> {
    public static final String JUNIT_5_KUBERNETES_POD_PREFIX = "junit5-kubernetes-pod-";
    public static final String JUNIT_5_KUBERNETES_LABEL = "junit5kubernetes";
    protected WaitStrategy<V1Pod> waitStrategy;
    protected final V1Pod podToCreate;
    protected final ThreadLocal<V1Pod> createdPod = new ThreadLocal<>();
    protected static final String SYSTEM_NAMESPACE = System.getProperty("kubernetesNamespace");
    protected static final String SYSTEM_PULL_SECRETS = System.getProperty("kubernetesPullSecrets");
    protected static final String DEBUG = System.getProperty("junitKubernetesDebug");
    protected static final String DISABLE_HTTP2 = System.getProperty("junitKubernetesDisableHttp2");
    protected static final String NAMESPACE = SYSTEM_NAMESPACE != null && !SYSTEM_NAMESPACE.trim().equals("") ? SYSTEM_NAMESPACE : "default";
    private static final Logger LOGGER = Logger.getLogger(Pod.class.getName());
    protected final CoreV1Api coreV1Api;
    private final List<FileToMountOnceStarted> filesToMountOnceStarted = new ArrayList<>();
    private Map<Integer, Integer> mappedPorts = new HashMap<>();

    public Pod(V1Pod podToCreate) {
        this.podToCreate = podToCreate;
        this.coreV1Api = initiateCoreV1Api();
    }

    private static CoreV1Api initiateCoreV1Api() {
        CoreV1Api coreV1Api;
        try {
            ApiClient client = Config.defaultClient();
            if (DEBUG != null && DEBUG.equalsIgnoreCase("true")) {
                client.setDebugging(true);
            }
            // infinite timeout
            OkHttpClient.Builder builder = client.getHttpClient().newBuilder()
                    .readTimeout(0, TimeUnit.SECONDS);

            if (DISABLE_HTTP2 != null && DISABLE_HTTP2.equalsIgnoreCase("true")) {
                builder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
            }

            OkHttpClient httpClient = builder.build();
            client.setHttpClient(httpClient);
            Configuration.setDefaultApiClient(client);

            coreV1Api = new CoreV1Api(client);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return coreV1Api;
    }

    // TODO study cleanup mechanism if JVM is interrupted before test completion
    public static void cleanup() {
        CoreV1Api coreV1Api = initiateCoreV1Api();
        try {
            V1PodList v1PodList = coreV1Api.listNamespacedPod(NAMESPACE, null, null, null, null, null, null, null, null, null);
            List<String> podNamesToRemove = v1PodList.getItems().stream()
                    .map(v1Pod -> v1Pod.getMetadata().getName())
                    .filter(v1PodName -> v1PodName.startsWith(JUNIT_5_KUBERNETES_POD_PREFIX))
                    .collect(Collectors.toList());
            podNamesToRemove.forEach(podName -> removePod(podName, coreV1Api));
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Pod withWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
        return this;
    }

    @Override
    public String getObjectName() {
        V1Pod v1Pod = this.createdPod.get();
        if (v1Pod != null) {
            return v1Pod.getMetadata().getName();
        } else {
            throw new RuntimeException("Can't get name of a non running object.");
        }
    }

    @Override
    public String getObjectHostIp() {
        V1Pod v1Pod = this.createdPod.get();
        if (v1Pod != null) {
            try {
                V1Pod retrievedPod = coreV1Api.readNamespacedPod(v1Pod.getMetadata().getName(), NAMESPACE, null, null, null);
                if (retrievedPod.getStatus() == null) {
                    throw new RuntimeException("Can't get ip of a non running object.");
                }
                return retrievedPod.getStatus().getHostIP();
            } catch (ApiException e) {
                throw logAndThrowApiException(e);
            }
        } else {
            throw new RuntimeException("Can't get ip of a non running object.");
        }
    }

    public ExecResult execInPod(String... command) {
        V1Pod v1Pod = this.createdPod.get();
        if (v1Pod == null) {
            throw new RuntimeException("Can't exec in a non running pod.");
        }

        Exec exec = new Exec();

        try {
            final Process proc = exec.exec(
                    NAMESPACE,
                    v1Pod.getMetadata().getName(),
                    command,
                    true,
                    true
            );

            Path tempOutFile = Files.createTempFile("junit5-kubernetes-output-temp", ".log");
            FileOutputStream outputStream = new FileOutputStream(tempOutFile.toFile());
            Path tempErrorFile = Files.createTempFile("junit5-kubernetes-error-temp", ".log");
            FileOutputStream errorStream = new FileOutputStream(tempErrorFile.toFile());

            Thread out =
                    new Thread(
                            () -> {
                                try {
                                    IOUtils.copy(proc.getInputStream(), outputStream);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                            });
            out.start();

            Thread errors =
                    new Thread(
                            () -> {
                                try {
                                    IOUtils.copy(proc.getErrorStream(), errorStream);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                            });

            errors.start();

            proc.waitFor();

            out.join();
            errors.join();

            return new ExecResult(proc, tempOutFile, tempErrorFile);
        } catch (ApiException e) {
            throw logAndThrowApiException(e);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void copyFileToPodContainer(String containerName, Path srcPath, Path destPath) {
        V1Pod v1Pod = this.createdPod.get();
        if (v1Pod == null) {
            throw new RuntimeException("Can't copy to a non running pod.");
        }
        Copy copy = new Copy();
        try {
            copy.copyFileToPod(NAMESPACE, v1Pod.getMetadata().getName(), containerName, srcPath, destPath);
        } catch (ApiException e) {
            throw logAndThrowApiException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Pod withCopyFileToPodContainer(String containerName, Path srcPath, Path destPath) {
        filesToMountOnceStarted.add(new FileToMountOnceStarted(containerName, srcPath, destPath));
        return this;
    }

    public Map<Integer, Integer> getMappedPorts() {
        return mappedPorts;
    }

    private static class FileToMountOnceStarted {
        private final String volumeMountName;
        private String containerName;
        private Path srcPath;
        private Path destPath;

        public FileToMountOnceStarted(String containerName, Path srcPath, Path destPath) {
            this.containerName = containerName;
            this.srcPath = srcPath;
            this.destPath = destPath;
            this.volumeMountName = UUID.randomUUID().toString().split("-")[0];
        }

        public String getContainerName() {
            return containerName;
        }

        public Path getSrcPath() {
            return srcPath;
        }

        public String getVolumeMountName() {
            return volumeMountName + hashCode();
        }

        public Path getDestPath() {
            return destPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FileToMountOnceStarted that = (FileToMountOnceStarted) o;
            return Objects.equals(volumeMountName, that.volumeMountName) &&
                    Objects.equals(containerName, that.containerName) &&
                    Objects.equals(srcPath, that.srcPath) &&
                    Objects.equals(destPath, that.destPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(volumeMountName, containerName, srcPath, destPath);
        }
    }

    public InputStream getLogStream() {
        V1Pod v1Pod = this.createdPod.get();
        if (v1Pod == null) {
            throw new RuntimeException("Can't retrieves logs of a non running pod.");
        }
        PodLogs logs = new PodLogs();
        try {
            return logs.streamNamespacedPodLog(v1Pod);
        } catch (ApiException e) {
            throw logAndThrowApiException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getLogs(String container) {
        V1Pod v1Pod = this.createdPod.get();
        if (v1Pod == null) {
            throw new RuntimeException("Can't retrieves logs of a non running pod.");
        }
        try {
            return coreV1Api.readNamespacedPodLog(v1Pod.getMetadata().getName(), NAMESPACE, container, false, null, null, false, null, null, false);
        } catch (ApiException e) {
            throw logAndThrowApiException(e);
        }
    }

    protected static RuntimeException logAndThrowApiException(ApiException e) {
        LOGGER.severe("Kubernetes API replied with " + e.getCode() + " status code, body " + e.getResponseBody() + " and message " + e.getMessage());
        System.out.println("Kubernetes API replied with " + e.getCode() + " status code and body " + e.getResponseBody()+ " and message " + e.getMessage());
        return new RuntimeException(e.getResponseBody(), e);
    }

    @Override
    public void create() {
        try {
            if (podToCreate.getMetadata() == null) {
                podToCreate.setMetadata(new V1ObjectMeta());
            }
            String podName = JUNIT_5_KUBERNETES_POD_PREFIX + UUID.randomUUID().toString().split("-")[0];
            podToCreate.getMetadata().setName(podName);
            if (SYSTEM_PULL_SECRETS != null && !SYSTEM_PULL_SECRETS.isEmpty() && podToCreate.getSpec() != null) {
                for (String secret : SYSTEM_PULL_SECRETS.split(",")) {
                    podToCreate.getSpec().addImagePullSecretsItem(new V1LocalObjectReferenceBuilder().withName(secret).build());
                }
            }
            onBeforeCreateKubernetesObject();
            podToCreate.getMetadata().putLabelsItem(JUNIT_5_KUBERNETES_LABEL, podName);
            List<V1ServicePort> ports = new ArrayList<>();

            boolean useNodePort = System.getProperty("junitKubernetesUsePortService") != null && System.getProperty("junitKubernetesUsePortService").equalsIgnoreCase("true");

            if (useNodePort) {
                ports = podToCreate.getSpec().getContainers().stream().flatMap(container -> {
                    if (container.getPorts() == null) {
                        return new ArrayList<V1ServicePort>().stream();
                    }

                    return container.getPorts().stream().map(port ->
                            new V1ServicePortBuilder()
                                    .withPort(port.getContainerPort())
                                    .withTargetPort(new IntOrString(port.getContainerPort()))
                                    .build()
                    );
                }).collect(Collectors.toList());

                podToCreate.getSpec().getContainers().forEach(container -> {
                    container.setPorts(container.getPorts().stream().map(port -> {
                        V1ContainerPort v1ContainerPort = new V1ContainerPort();
                        v1ContainerPort.setContainerPort(port.getContainerPort());
                        return v1ContainerPort;
                    }).collect(Collectors.toList()));
                });
            }
            V1Pod createdPod = coreV1Api.createNamespacedPod(NAMESPACE, podToCreate, null, null, null);
            if (useNodePort) {
                Map<String, String> selectorLabels = new HashMap<>();
                selectorLabels.put(JUNIT_5_KUBERNETES_LABEL, podName);

                V1Service nodePortService = new V1ServiceBuilder()
                        .withNewMetadata()
                            .withName(podName)
                        .endMetadata()
                        .withNewSpec()
                            .withType("NodePort")
                            .withSelector(selectorLabels)
                            .withPorts(ports)
                        .endSpec().build();

                V1Service namespacedService = coreV1Api.createNamespacedService(NAMESPACE, nodePortService, null, null, null);
                List<V1ServicePort> servicePorts = namespacedService.getSpec().getPorts();
                servicePorts.forEach(port -> {
                    mappedPorts.put(port.getPort(), port.getNodePort());
                });
            }
            this.createdPod.set(createdPod);
            if (this.waitStrategy != null) {
                try (Watch<V1Pod> watch = Watch.createWatch(
                        coreV1Api.getApiClient(),
                        coreV1Api.listNamespacedPodCall(
                                NAMESPACE, null, null, null, null, null,
                                null, null, null, true,
                                null),
                        new TypeToken<Watch.Response<V1Pod>>() {
                        }.getType())) {
                    this.waitStrategy.apply(watch, createdPod);
                } catch (IOException | ApiException e) {
                    if (e instanceof ApiException) {
                        throw logAndThrowApiException((ApiException) e);
                    }
                    throw new RuntimeException(e);
                }
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> removePod(podName, coreV1Api)));

            filesToMountOnceStarted.forEach(fileToMountOnceStarted -> copyFileToPodContainer(fileToMountOnceStarted.getContainerName(), fileToMountOnceStarted.getSrcPath(), fileToMountOnceStarted.getDestPath()));
            onKubernetesObjectReady();
        } catch (ApiException e) {
            throw logAndThrowApiException(e);
        }
    }

    @Override
    public void remove() {
        V1Pod v1Pod = this.createdPod.get();
        if (v1Pod != null) {
            String podName = v1Pod.getMetadata().getName();
            removePod(podName, coreV1Api);

        }
    }

    private static void removePod(String podName, CoreV1Api coreV1Api) {
        try {
            boolean useNodePort = System.getProperty("junitKubernetesUsePortService") != null && System.getProperty("junitKubernetesUsePortService").equalsIgnoreCase("true");
            if (useNodePort) {
                coreV1Api.deleteNamespacedService(podName, NAMESPACE, null, null, null, null, null, null);
            }

            coreV1Api.deleteNamespacedPod(podName, NAMESPACE, null, null, null, null, null, null);
        } catch (ApiException e) {
            throw logAndThrowApiException(e);
        } catch (JsonSyntaxException e) {
            if (e.getCause() instanceof IllegalStateException) {
                IllegalStateException ise = (IllegalStateException) e.getCause();
                if (ise.getMessage() != null && ise.getMessage().contains("Expected a string but was BEGIN_OBJECT")) {
                    // Catching exception because of issue https://github.com/kubernetes-client/java/issues/86
                } else throw e;
            } else throw e;
        }
    }

    @Override
    public String toString() {
        return "Pod{" +
                "waitStrategy=" + waitStrategy +
                "V1Pod=" + podToCreate +
                '}';
    }

    public class ExecResult implements AutoCloseable {
        private final int exitCode;
        private final Process process;
        private final File tempOutFile;
        private final File tempErrorFile;

        public ExecResult(Process process, Path tempOutFile, Path tempErrorFile) {
            this.exitCode = process.exitValue();
            this.process = process;
            this.tempOutFile = tempOutFile.toFile();
            this.tempErrorFile = tempErrorFile.toFile();
        }

        public int getExitCode() {
            return exitCode;
        }

        public InputStream getStandardOut() {
            if (!tempOutFile.exists()) {
                return null;
            }

            try {
                return new FileInputStream(tempOutFile);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        public String consumeStandardOutAsString(Charset charset) {
            if (!tempOutFile.exists()) {
                return "";
            }

            try {
                return IOUtils.toString(getStandardOut(), charset);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public InputStream getStandardError() {
            if (!tempErrorFile.exists()) {
                return null;
            }
            try {
                return new FileInputStream(tempErrorFile);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        public String consumeStandardErrorAsString(Charset charset) {
            if (!tempErrorFile.exists()) {
                return "";
            }
            try {
                return IOUtils.toString(getStandardError(), charset);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            process.destroy();
            tempErrorFile.deleteOnExit();
            tempOutFile.deleteOnExit();
        }
    }
}
