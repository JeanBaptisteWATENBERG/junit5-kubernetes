package com.github.jeanbaptistewatenberg.impl;

import com.github.jeanbaptistewatenberg.Pod;
import com.github.jeanbaptistewatenberg.wait.WaitStrategy;
import com.github.jeanbaptistewatenberg.wait.impl.WaitRunningStatusStrategy;
import io.kubernetes.client.fluent.VisitableBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodFluent;
import io.kubernetes.client.openapi.models.V1PodFluentImpl;

public class GenericPodBuilder extends V1PodFluentImpl<GenericPodBuilder> implements VisitableBuilder<Pod,GenericPodBuilder> {

    private WaitStrategy waitStrategy = new WaitRunningStatusStrategy();

    V1PodFluent<?> fluent;
    Boolean validationEnabled;

    public GenericPodBuilder(){
        this(true);
    }
    public GenericPodBuilder(Boolean validationEnabled){
        this(new V1Pod(), validationEnabled);
    }
    public GenericPodBuilder(V1PodFluent<?> fluent){
        this(fluent, true);
    }
    public GenericPodBuilder(V1PodFluent<?> fluent,Boolean validationEnabled){
        this(fluent, new V1Pod(), validationEnabled);
    }
    public GenericPodBuilder(V1PodFluent<?> fluent,V1Pod instance){
        this(fluent, instance, true);
    }
    public GenericPodBuilder(V1PodFluent<?> fluent,V1Pod instance,Boolean validationEnabled){
        this.fluent = fluent;
        fluent.withApiVersion(instance.getApiVersion());

        fluent.withKind(instance.getKind());

        fluent.withMetadata(instance.getMetadata());

        fluent.withSpec(instance.getSpec());

        fluent.withStatus(instance.getStatus());

        this.validationEnabled = validationEnabled;
    }
    public GenericPodBuilder(V1Pod instance){
        this(instance,true);
    }
    public GenericPodBuilder(V1Pod instance,Boolean validationEnabled){
        this.fluent = this;
        this.withApiVersion(instance.getApiVersion());

        this.withKind(instance.getKind());

        this.withMetadata(instance.getMetadata());

        this.withSpec(instance.getSpec());

        this.withStatus(instance.getStatus());

        this.validationEnabled = validationEnabled;
    }

    public GenericPodBuilder withWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
        return this;
    }

    @Override
    public Pod build() {
        V1Pod podToCreate = new V1Pod();
        podToCreate.setApiVersion(fluent.getApiVersion());
        podToCreate.setKind(fluent.getKind());
        podToCreate.setMetadata(fluent.getMetadata());
        podToCreate.setSpec(fluent.getSpec());
        podToCreate.setStatus(fluent.getStatus());
        Pod buildable = new Pod(podToCreate);
        return buildable.withWaitStrategy(waitStrategy);
    }
}
