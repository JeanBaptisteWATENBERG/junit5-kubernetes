package com.github.jeanbaptistewatenberg;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
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
            ApiClient client = Config.defaultClient();
            // infinite timeout
            OkHttpClient httpClient = client.getHttpClient().newBuilder().readTimeout(0, TimeUnit.SECONDS).build();
            client.setHttpClient(httpClient);
            Configuration.setDefaultApiClient(client);

            CoreV1Api coreV1Api = new CoreV1Api(client);
            System.out.println(coreV1Api.getAPIResources().getResources());
        } catch (IOException | ApiException e) {
            LOGGER.severe(e.getMessage());
        }
    }


}
