package com.microsoft.valda.oms;

/*
 * Microsoft OMS log4j appender
 *
 * Copyright(c) Microsoft Corporation All rights reserved.
 *
 * This program is made available under the terms of the MIT License. See the LICENSE file in the project root for more information.
 */

import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;
import org.json.simple.JSONObject;
import sun.misc.IOUtils;

import java.net.URL;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import javax.net.ssl.HttpsURLConnection;
import javax.xml.ws.http.HTTPException;

public class OmsAppender extends AppenderSkeleton implements Appender {
    private static InetAddress inetAddress = null;
    private static final BlockingQueue<LoggingEvent> loggingEventQueue = new LinkedBlockingQueue<LoggingEvent>();

    private String customerId = null;
    private String sharedKey = null;
    private String logType = null;
    private String applicationName = null;
    private String serverName = null;

    private static OmsAppender instance;

    static {
        try {
            inetAddress = InetAddress.getLocalHost();
        }
        catch (UnknownHostException e) {
            inetAddress = null;
        }

        Thread thread = new Thread(new Runnable() {
            public void run() {
                processQueue();
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    public OmsAppender() {
        super();
        instance = this;
    }

    @Override
    protected void append(LoggingEvent event) {
        loggingEventQueue.add(event);
    }

    private void processEvent(LoggingEvent event) {
        Object logMsg = event.getMessage();
        if (logMsg instanceof Serializable) {
            if (logMsg instanceof String) {
                try {
                    String msg = (String) logMsg;
                    sendLog(event, msg);
                }
                catch (Exception e) {
                    errorHandler.error("Failed to process", e, ErrorCode.FLUSH_FAILURE);
                }
            }
        }

        logMsg = null;
    }

    private void sendLog(LoggingEvent event, String msg) throws NoSuchAlgorithmException, InvalidKeyException, IOException, HTTPException {
        //create JSON message
        JSONObject obj = new JSONObject();
        obj.put("LOG4JApplicationName", applicationName);
        obj.put("LOG4JServerName", serverName);
        obj.put("LOG4JLoggerName", event.getLoggerName());
        obj.put("LOG4JLogLevel", event.getLevel().toString());
        obj.put("LOG4JMessage", msg);
        if (event.getThrowableInformation() != null) {
            obj.put("LOG4JStackTrace", ThrowableUtil.getStacktrace(event.getThrowableInformation().getThrowable()));
        }
        else {
            obj.put("LOG4JStackTrace", "");
        }
        if (inetAddress != null) {
            obj.put("LOG4JIPAddress", inetAddress.getHostAddress());
        }
        else {
            obj.put("LOG4JIPAddress", "0.0.0.0");
        }
        String json = obj.toJSONString();

        String Signature = "";
        String encodedHash = "";
        String url = "";

        // Todays date input for OMS Log Analytics
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String timeNow = dateFormat.format(calendar.getTime());

        // String for signing the key
        String stringToSign="POST\n" + json.length() + "\napplication/json\nx-ms-date:"+timeNow+"\n/api/logs";
        byte[] decodedBytes = Base64.decodeBase64(sharedKey);
        Mac hasher = Mac.getInstance("HmacSHA256");
        hasher.init(new SecretKeySpec(decodedBytes, "HmacSHA256"));
        byte[] hash = hasher.doFinal(stringToSign.getBytes());

        encodedHash = DatatypeConverter.printBase64Binary(hash);
        Signature = "SharedKey " + customerId + ":" + encodedHash;

        url = "https://" + customerId + ".ods.opinsights.azure.com/api/logs?api-version=2016-04-01";
        URL objUrl = new URL(url);
        HttpsURLConnection con = (HttpsURLConnection) objUrl.openConnection();
        con.setDoOutput(true);
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Log-Type",logType);
        con.setRequestProperty("x-ms-date", timeNow);
        con.setRequestProperty("Authorization", Signature);

        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(json);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        if(responseCode != 200){
            throw new HTTPException(responseCode);
        }
    }

    @Override
    public void activateOptions() {
        try {
            super.activateOptions();
        }
        catch (Exception e) {
            errorHandler.error("Error while activating options for appender named [" + name + "].", e, ErrorCode.GENERIC_FAILURE);
        }
    }

    public void close() {
        this.closed = true;
    }

    private static void processQueue() {
        while (true) {
            try {
                LoggingEvent event = loggingEventQueue.poll(1L, TimeUnit.SECONDS);
                if (event != null) {
                    instance.processEvent(event);
                }
            }
            catch (InterruptedException e) {
                // No operations.
            }
        }
    }

    public boolean requiresLayout() {
        return false;
    }

    @Override
    public void finalize() {
        close();
        super.finalize();
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getSharedKey() {
        return sharedKey;
    }

    public void setSharedKey(String sharedKey) {
        this.sharedKey = sharedKey;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
}
