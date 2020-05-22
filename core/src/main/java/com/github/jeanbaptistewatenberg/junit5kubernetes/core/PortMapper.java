package com.github.jeanbaptistewatenberg.junit5kubernetes.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

public class PortMapper {
    private ThreadLocal<Map<String, Integer>> internalPortMapping = new ThreadLocal<>();

    public int computeAvailablePort(String name) {
        if (internalPortMapping.get() == null) {
            internalPortMapping.set(new HashMap<>());
        }
        try {
            ServerSocket serverSocket = new ServerSocket(0);
            int localPort = serverSocket.getLocalPort();
            internalPortMapping.get().put(name, localPort);
            return localPort;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getComputedPort(String name) {
        if (internalPortMapping.get() != null) {
            return internalPortMapping.get().get(name);
        }
        throw new RuntimeException("Uninitialized port mapper");
    }

    public boolean hasComputedPort(String name) {
        if (internalPortMapping.get() != null) {
            return internalPortMapping.get().containsKey(name);
        }
        throw new RuntimeException("Uninitialized port mapper");
    }
}
