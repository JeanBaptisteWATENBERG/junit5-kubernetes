package com.github.jeanbaptistewatenberg.junit5kubernetes.elasticsearch;

import org.apache.http.HttpHost;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.Version;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.*;
import org.elasticsearch.client.xpack.XPackInfoRequest;
import org.elasticsearch.client.xpack.XPackInfoResponse;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.rest.RestStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ElasticSearchPodTest {
    private static final String ELASTICSEARCH_VERSION = Version.CURRENT.toString();
    public static final String ELASTICSEARCH_IMAGE_NAME = "docker.elastic.co/elasticsearch/elasticsearch";
    private static final String ELASTICSEARCH_IMAGE = ELASTICSEARCH_IMAGE_NAME+":"+ELASTICSEARCH_VERSION;

    @Test
    void shouldCreateElasticSearchPod() {
        try(ElasticSearchPod pod = new ElasticSearchPod()){
            //S
            pod.create();
            //V
            assertThat(pod.getHttpHostAddress()).isEqualTo(
                    String.format("http://%s:%d", pod.getObjectHostIp(), pod.getHttpPort()));
        }
    }

    @Test
    void shouldCreateElasticSearchDefaultImageXpackShouldBeAvailable() {
        try(ElasticSearchPod pod = new ElasticSearchPod()){
            //S
            pod.create();
            final RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(
                    new HttpHost(pod.getObjectHostIp(), pod.getHttpPort(), "http")
            ));
            //E
            final XPackInfoResponse xpack = restHighLevelClient.xpack().info(new XPackInfoRequest(), RequestOptions.DEFAULT);
            //V
            assertThat(xpack).isNotNull();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void shouldCreateElasticSearchVersionXpackShouldBeAvailable() {
        try(ElasticSearchPod pod = new ElasticSearchPod(ELASTICSEARCH_IMAGE)){
            //S
            pod.create();
            final RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(
                    new HttpHost(pod.getObjectHostIp(), pod.getHttpPort(), "http")
            ));
            //E
            final XPackInfoResponse xpack = restHighLevelClient.xpack().info(new XPackInfoRequest(), RequestOptions.DEFAULT);
            //V
            assertThat(xpack).isNotNull();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void shouldCreateElasticSearchOssImageXpackShouldNotBeAvailable() {
        try(ElasticSearchPod pod = new ElasticSearchPod("docker.elastic.co/elasticsearch/elasticsearch-oss"+":"+ELASTICSEARCH_VERSION)){
            //S
            pod.create();
            final RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(
                    new HttpHost(pod.getObjectHostIp(), pod.getHttpPort(), "http")
            ));
            //E
            final ElasticsearchStatusException elasticsearchStatusException = assertThrows(ElasticsearchStatusException.class, () -> restHighLevelClient.xpack().info(new XPackInfoRequest(), RequestOptions.DEFAULT));
            //V
            assertThat(elasticsearchStatusException.status()).isEqualTo(RestStatus.BAD_REQUEST);
        }
    }

    @Test
    void shouldCreateElasticSearchDefaultAndClusterHealthShouldBeOk() {
        try(ElasticSearchPod pod = new ElasticSearchPod()){
            //S
            pod.create();
            final RestHighLevelClient restHighLevelClient = new RestHighLevelClient(RestClient.builder(
                    new HttpHost(pod.getObjectHostIp(), pod.getHttpPort(), "http")
            ));
            //E
            final ClusterHealthResponse health = restHighLevelClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
            //V
            assertThat(health.getStatus()).isEqualTo(ClusterHealthStatus.GREEN);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
