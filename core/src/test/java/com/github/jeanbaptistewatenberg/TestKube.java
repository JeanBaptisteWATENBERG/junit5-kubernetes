package com.github.jeanbaptistewatenberg;

import io.kubernetes.client.openapi.*;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1APIResourceList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import javax.net.ssl.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class TestKube {
    OkHttpClient okHttpClient = new OkHttpClient();
    private static final Logger LOGGER = Logger.getLogger(TestKube.class.getName());

    @Test
    void should_test_differently_api() throws IOException, ApiException {
        ApiClient client = Config.defaultClient();
        client.setVerifyingSsl(false);
        client.setDebugging(true);
        OkHttpClient httpClient = client.getHttpClient().newBuilder().connectTimeout(0, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).build();
        client.setHttpClient(httpClient);
        LOGGER.info("build call");
        Call call = listPod(client);
        LOGGER.info("execute call");
        call.execute();
    }

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
            LOGGER.info("list pods");
            V1PodList list = coreV1Api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
            LOGGER.info("get resources with http info");
            ApiResponse<V1APIResourceList> apiResourcesWithHttpInfo = coreV1Api.getAPIResourcesWithHttpInfo();
            System.out.println(apiResourcesWithHttpInfo.getStatusCode());
            LOGGER.info("get resources");
            System.out.println(coreV1Api.getAPIResources().getResources());
        } catch (IOException | ApiException e) {
            LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }


    private Call listPod(ApiClient client) throws ApiException {
        {
            Object localVarPostBody = null;
            String localVarPath = "/api/v1/pods";
            List<Pair> localVarQueryParams = new ArrayList();
            List<Pair> localVarCollectionQueryParams = new ArrayList();


            Map<String, String> localVarHeaderParams = new HashMap();
            Map<String, String> localVarCookieParams = new HashMap();
            Map<String, Object> localVarFormParams = new HashMap();
            String[] localVarAccepts = new String[]{"application/json", "application/yaml", "application/vnd.kubernetes.protobuf", "application/json;stream=watch", "application/vnd.kubernetes.protobuf;stream=watch"};
            String localVarAccept = client.selectHeaderAccept(localVarAccepts);
            if (localVarAccept != null) {
                localVarHeaderParams.put("Accept", localVarAccept);
            }

            String[] localVarContentTypes = new String[0];
            String localVarContentType = client.selectHeaderContentType(localVarContentTypes);
            localVarHeaderParams.put("Content-Type", localVarContentType);
            String[] localVarAuthNames = new String[]{"BearerToken"};
            return client.buildCall(localVarPath, "GET", localVarQueryParams, localVarCollectionQueryParams, localVarPostBody, localVarHeaderParams, localVarCookieParams, localVarFormParams, localVarAuthNames, null);
        }
    }
}
