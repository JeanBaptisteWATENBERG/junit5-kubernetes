package com.github.jeanbaptistewatenberg.junit5kubernetes.core.junit;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.KubernetesGenericObject;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.KubernetesObject;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.util.Preconditions;
import org.junit.platform.commons.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

// Strongly inspired by test containers junit extension https://github.com/testcontainers/testcontainers-java/blob/master/modules/junit-jupiter/src/main/java/org/testcontainers/junit/jupiter/TestcontainersExtension.java
public class JunitKubernetesExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback, TestInstancePostProcessor {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(JunitKubernetesExtension.class);
    private static final String TEST_INSTANCE = "testInstance";
    private static final String SHARED_LIFECYCLE_AWARE_CONTAINERS = "sharedLifecycleAwareContainers";
    private static final String LOCAL_LIFECYCLE_AWARE_CONTAINERS = "localLifecycleAwareContainers";

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(TEST_INSTANCE, testInstance);
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getTestClass()
                .orElseThrow(() -> new ExtensionConfigurationException("JunitKubernetesExtension is only supported for classes."));

        ExtensionContext.Store store = context.getStore(NAMESPACE);
        List<StoreAdapter> sharedContainersStoreAdapters = findSharedKubernetesObject(testClass);

        sharedContainersStoreAdapters.forEach(adapter -> store.getOrComputeIfAbsent(adapter.getKey(), k -> adapter.start()));

        List<TestLifecycleAware> lifecycleAwareContainers = sharedContainersStoreAdapters
                .stream()
                .filter(this::isTestLifecycleAware)
                .map(lifecycleAwareAdapter -> (TestLifecycleAware) lifecycleAwareAdapter.container)
                .collect(toList());

        store.put(SHARED_LIFECYCLE_AWARE_CONTAINERS, lifecycleAwareContainers);
        signalBeforeTestToContainers(lifecycleAwareContainers, testDescriptionFrom(context));
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        signalAfterTestToContainersFor(SHARED_LIFECYCLE_AWARE_CONTAINERS, context);
    }

    private boolean isTestLifecycleAware(StoreAdapter adapter) {
        return adapter.container instanceof TestLifecycleAware;
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(NAMESPACE);

        List<TestLifecycleAware> lifecycleAwareContainers = collectParentTestInstances(context).parallelStream()
                .flatMap(this::findRestartContainers)
                .peek(adapter -> store.getOrComputeIfAbsent(adapter.getKey(), k -> adapter.start()))
                .filter(this::isTestLifecycleAware)
                .map(lifecycleAwareAdapter -> (TestLifecycleAware) lifecycleAwareAdapter.container)
                .collect(toList());

        store.put(LOCAL_LIFECYCLE_AWARE_CONTAINERS, lifecycleAwareContainers);
        signalBeforeTestToContainers(lifecycleAwareContainers, testDescriptionFrom(context));
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        signalAfterTestToContainersFor(LOCAL_LIFECYCLE_AWARE_CONTAINERS, context);
    }

    private void signalBeforeTestToContainers(List<TestLifecycleAware> lifecycleAwareContainers, TestDescription testDescription) {
        lifecycleAwareContainers.forEach(container -> container.beforeTest(testDescription));
    }

    private void signalAfterTestToContainersFor(String storeKey, ExtensionContext context) {
        List<TestLifecycleAware> lifecycleAwareContainers =
                (List<TestLifecycleAware>) context.getStore(NAMESPACE).get(storeKey);
        if (lifecycleAwareContainers != null) {
            TestDescription description = testDescriptionFrom(context);
            Optional<Throwable> throwable = context.getExecutionException();
            lifecycleAwareContainers.forEach(container -> container.afterTest(description, throwable));
        }
    }

    private Set<Object> collectParentTestInstances(final ExtensionContext context) {
        Set<Object> testInstances = new LinkedHashSet<>();
        Optional<ExtensionContext> current = Optional.of(context);
        while (current.isPresent()) {
            ExtensionContext ctx = current.get();
            Object testInstance = ctx.getStore(NAMESPACE).remove(TEST_INSTANCE);
            if (testInstance != null) {
                testInstances.add(testInstance);
            }
            current = ctx.getParent();
        }
        return testInstances;
    }

    private TestDescription testDescriptionFrom(ExtensionContext context) {
        return new TestContainersTestDescription(
                context.getUniqueId(),
                FilesystemFriendlyNameGenerator.filesystemFriendlyNameOf(context)
        );
    }

    private List<StoreAdapter> findSharedKubernetesObject(Class<?> testClass) {
        return ReflectionUtils.findFields(
                testClass,
                isSharedKubernetesObject(),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                .stream()
                .map(f -> getKubernetesObjectInstance(null, f))
                .collect(toList());
    }

    private Predicate<Field> isSharedKubernetesObject() {
        return isKubernetesObject().and(ReflectionUtils::isStatic);
    }

    private Stream<StoreAdapter> findRestartContainers(Object testInstance) {
        return ReflectionUtils.findFields(
                testInstance.getClass(),
                isRestartContainer(),
                ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                .stream()
                .map(f -> getKubernetesObjectInstance(testInstance, f));
    }

    private Predicate<Field> isRestartContainer() {
        return isKubernetesObject().and(ReflectionUtils::isNotStatic);
    }

    private static Predicate<Field> isKubernetesObject() {
        return field -> {
            boolean isAnnotatedWithKubernetesObject = AnnotationSupport.isAnnotated(field, KubernetesObject.class);
            if (isAnnotatedWithKubernetesObject) {
                boolean isKubernetesGenericObject = KubernetesGenericObject.class.isAssignableFrom(field.getType());

                if (!isKubernetesGenericObject) {
                    throw new ExtensionConfigurationException(String.format("FieldName: %s does not implement KubernetesGenericObject", field.getName()));
                }
                return true;
            }
            return false;
        };
    }

    private static StoreAdapter getKubernetesObjectInstance(final Object testInstance, final Field field) {
        try {
            field.setAccessible(true);
            KubernetesGenericObject kubernetesObjectInstance = Preconditions.notNull((KubernetesGenericObject) field.get(testInstance), "KubernetesGenericObject " + field.getName() + " needs to be initialized");
            return new StoreAdapter(field.getDeclaringClass(), field.getName(), kubernetesObjectInstance);
        } catch (IllegalAccessException e) {
            throw new ExtensionConfigurationException("Can not access container defined in field " + field.getName());
        }
    }

    /**
     * An adapter for {@link KubernetesGenericObject} that implement {@link ExtensionContext.Store.CloseableResource}
     * thereby letting the JUnit automatically stop containers once the current
     * {@link ExtensionContext} is closed.
     */
    private static class StoreAdapter implements ExtensionContext.Store.CloseableResource {
        private String key;

        private KubernetesGenericObject container;

        private StoreAdapter(Class<?> declaringClass, String fieldName, KubernetesGenericObject container) {
            this.key = declaringClass.getName() + "." + fieldName;
            this.container = container;
        }

        private StoreAdapter start() {
            container.create();
            return this;
        }

        public String getKey() {
            return key;
        }

        @Override
        public void close() {
            container.remove();
        }
    }
}
