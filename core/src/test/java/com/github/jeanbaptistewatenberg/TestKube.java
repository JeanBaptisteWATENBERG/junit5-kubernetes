package com.github.jeanbaptistewatenberg;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.logging.Logger;

public class TestKube {
    OkHttpClient okHttpClient = new OkHttpClient();
    private static final Logger LOGGER = Logger.getLogger(TestKube.class.getName());
    @Test
    void test_kube() {

        Request request = new Request.Builder()
                .url("https://127.0.0.1:32768/api/v1/namespaces/default")
                .build();
        try {
            Response response = okHttpClient.newCall(request).execute();
            int code = response.code();
            LOGGER.info(String.valueOf(code));
            System.out.println(code);
            String body = IOUtils.toString(response.body().byteStream());
            LOGGER.info(body);
            System.out.println(body);
        } catch (IOException e) {
            LOGGER.severe(e.getMessage());
        }
    }

    @Test
    void test_api_client() {
        try {
            ApiClient client = Config.defaultClient();
            String basePath = client.getBasePath();
            System.out.println(basePath);
            LOGGER.info(basePath);
        } catch (IOException e) {
            LOGGER.severe(e.getMessage());
        }
    }
}
