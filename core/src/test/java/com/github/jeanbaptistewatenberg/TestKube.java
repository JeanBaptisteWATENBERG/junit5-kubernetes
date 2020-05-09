package com.github.jeanbaptistewatenberg;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1APIResourceList;
import io.kubernetes.client.util.Config;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import javax.net.ssl.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TestKube {
    OkHttpClient okHttpClient = new OkHttpClient();
    private static final Logger LOGGER = Logger.getLogger(TestKube.class.getName());


    @Test
    void test_api_client() {
        try {
            LOGGER.info("get client");
            ApiClient client = Config.defaultClient();
            client.setVerifyingSsl(false);
            client.setDebugging(true);
            // infinite timeout
            LOGGER.info("get http client");
            OkHttpClient httpClient = client.getHttpClient().newBuilder().connectTimeout(0, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build();
            LOGGER.info("set http client");
            client.setHttpClient(httpClient);
            LOGGER.info("setDefaultApiClient");
            Configuration.setDefaultApiClient(client);
            LOGGER.info("build core v1");
            CoreV1Api coreV1Api = new CoreV1Api(client);
            LOGGER.info("get resources with http info");
            ApiResponse<V1APIResourceList> apiResourcesWithHttpInfo = coreV1Api.getAPIResourcesWithHttpInfo();
            System.out.println(apiResourcesWithHttpInfo.getStatusCode());
            LOGGER.info("get resources");
            System.out.println(coreV1Api.getAPIResources().getResources());
        } catch (IOException | ApiException e) {
            LOGGER.severe(e.getMessage());
        }
    }


}
