package com.github.jeanbaptistewatenberg.junit5kubernetes.core.impl;

import com.github.jeanbaptistewatenberg.junit5kubernetes.core.Service;
import com.github.jeanbaptistewatenberg.junit5kubernetes.core.wait.WaitStrategy;
import io.kubernetes.client.fluent.VisitableBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceBuilder;
import io.kubernetes.client.openapi.models.V1ServiceFluent;
import io.kubernetes.client.openapi.models.V1ServiceFluentImpl;

public class GenericServiceBuilder extends V1ServiceFluentImpl<GenericServiceBuilder> implements VisitableBuilder<Service, GenericServiceBuilder> {

    private WaitStrategy waitStrategy;
    V1ServiceFluent<?> fluent;

    public GenericServiceBuilder() {
        this(new V1Service());
    }

    public GenericServiceBuilder(V1ServiceFluent<?> fluent, V1Service instance) {

        this.fluent = fluent;
        fluent.withApiVersion(instance.getApiVersion());
        fluent.withKind(instance.getKind());
        fluent.withMetadata(instance.getMetadata());
        fluent.withSpec(instance.getSpec());
        fluent.withStatus(instance.getStatus());
    }

    public GenericServiceBuilder(V1Service instance) {

        this.fluent = this;
        this.withApiVersion(instance.getApiVersion());
        this.withKind(instance.getKind());
        this.withMetadata(instance.getMetadata());
        this.withSpec(instance.getSpec());
        this.withStatus(instance.getStatus());
    }

    @Override
    public Service build() {

        V1Service serviceToCreate = new V1ServiceBuilder()
            .withApiVersion(fluent.getApiVersion())
            .withKind(fluent.getKind())
            .withStatus(fluent.buildStatus())
            .withMetadata(fluent.buildMetadata())
            .withSpec(fluent.buildSpec())
            .build();

        Service buildable = new Service(serviceToCreate);
        return buildable.withWaitStrategy(waitStrategy);
    }

    public GenericServiceBuilder withWaitStrategy(WaitStrategy<V1Service> waitStrategy) {
        this.waitStrategy = waitStrategy;
        return this;
    }
}
