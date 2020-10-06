package com.github.jeanbaptistewatenberg.junit5kubernetes.elasticsearch;

import org.apache.http.HttpHost;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.rest.RestStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ElasticSearchPodTest {
    @Test
    void shouldCreateElasticSearchPod() {
        try(ElasticSearchPod pod = new ElasticSearchPod()){
            //S
            pod.create();
            //V
            assertThat(pod.getHttpHostAddress()).isEqualTo(
                    String.format("%s:%d", pod.getObjectHostIp(), pod.getHttpPort()));
            assertThat(pod.getTcpHost()).isEqualTo(new InetSocketAddress(pod.getObjectHostIp(), pod.getTcpPort()));
        }
    }

    @Test
    void canIndexDocument() throws IOException {
        try(ElasticSearchPod pod = new ElasticSearchPod()){
            //S
            pod.create();
            final RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(
                    new HttpHost(pod.getObjectHostIp(), pod.getHttpPort(), "http")
            ));
            //E
            final IndexRequest indexRequest = new IndexRequest("anyindice");
            indexRequest.source(Collections.singletonMap("key","value"));
            final IndexResponse response = restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
            //V
            assertThat(response.status()).isEqualTo(RestStatus.CREATED);
        }
    }


}
