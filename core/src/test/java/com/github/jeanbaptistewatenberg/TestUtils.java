package com.github.jeanbaptistewatenberg;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestUtils {
    public static int responseStatus(URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        return con.getResponseCode();
    }
}
