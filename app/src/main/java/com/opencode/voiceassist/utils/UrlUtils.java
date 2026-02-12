package com.opencode.voiceassist.utils;

public class UrlUtils {
    
    /**
     * Parse server URL string into host and port
     * Supports formats: "http://host:port", "https://host:port", "host:port", "host"
     * Returns array where [0] = host, [1] = port string
     */
    public static String[] parseServerUrl(String url) {
        String host = Constants.DEFAULT_OPENCODE_IP;
        String port = String.valueOf(Constants.DEFAULT_OPENCODE_PORT);
        
        if (url == null || url.trim().isEmpty()) {
            return new String[]{host, port};
        }
        
        String trimmed = url.trim();
        
        // Remove http:// or https:// prefix
        if (trimmed.startsWith("http://")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("https://")) {
            trimmed = trimmed.substring(8);
        }
        
        // Split host and port
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex > 0) {
            host = trimmed.substring(0, colonIndex);
            String portStr = trimmed.substring(colonIndex + 1);
            // Remove path part if any
            int slashIndex = portStr.indexOf('/');
            if (slashIndex > 0) {
                portStr = portStr.substring(0, slashIndex);
            }
            port = portStr;
        } else {
            host = trimmed;
        }
        
        // Use defaults if host is empty
        if (host.isEmpty()) {
            host = Constants.DEFAULT_OPENCODE_IP;
        }
        
        return new String[]{host, port};
    }
    
    /**
     * Format host and port into display URL string
     */
    public static String formatServerUrl(String host, int port) {
        return "http://" + host + ":" + port;
    }
    
    /**
     * Parse ASR URL (http://host:port or ws://host:port) into host and port
     * Returns array where [0] = host, [1] = port
     */
    public static String[] parseAsrUrl(String url, String defaultProtocol) {
        String host = "localhost";
        String port = defaultProtocol.equals("http") ? "8080" : "10095";
        
        if (url == null || url.trim().isEmpty()) {
            return new String[]{host, port};
        }
        
        String trimmed = url.trim();
        
        // Remove protocol prefix
        if (trimmed.startsWith("http://")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("https://")) {
            trimmed = trimmed.substring(8);
        } else if (trimmed.startsWith("ws://")) {
            trimmed = trimmed.substring(5);
        } else if (trimmed.startsWith("wss://")) {
            trimmed = trimmed.substring(6);
        }
        
        // Split host and port
        int colonIndex = trimmed.indexOf(':');
        if (colonIndex > 0) {
            host = trimmed.substring(0, colonIndex);
            String portStr = trimmed.substring(colonIndex + 1);
            // Remove path part if any
            int slashIndex = portStr.indexOf('/');
            if (slashIndex > 0) {
                portStr = portStr.substring(0, slashIndex);
            }
            if (!portStr.isEmpty()) {
                port = portStr;
            }
        } else {
            host = trimmed;
        }
        
        // Use defaults if host is empty
        if (host.isEmpty()) {
            host = "localhost";
        }
        
        return new String[]{host, port};
    }
}