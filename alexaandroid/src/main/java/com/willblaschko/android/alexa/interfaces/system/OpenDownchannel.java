package com.willblaschko.android.alexa.interfaces.system;

import com.willblaschko.android.alexa.callbacks.AsyncCallback;
import com.willblaschko.android.alexa.connection.ClientUtil;
import com.willblaschko.android.alexa.interfaces.AvsResponse;
import com.willblaschko.android.alexa.interfaces.SendEvent;
import com.willblaschko.android.alexa.interfaces.response.ResponseParser;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import okio.BufferedSource;

/**
 * Open Down Channel {@link com.willblaschko.android.alexa.data.Event} to open a persistent connection with the Alexa server. Currently doesn't seem to work as expected.
 *
 * {@link com.willblaschko.android.alexa.data.Event}
 *
 * @author will on 5/21/2016.
 */
public class OpenDownchannel extends SendEvent {

    private static final String TAG = "OpenDownchannel";
    private Call currentCall;
    private OkHttpClient client;
    private String url;
    private AsyncCallback<AvsResponse, Exception> callback;

    public OpenDownchannel(final String url, final AsyncCallback<AvsResponse, Exception> callback) {
        this.callback = callback;
        this.url = url;
        this.client = ClientUtil.getTLS12OkHttpClient();
    }

    /**
     * Open the connection
     * @param accessToken
     * @return true if canceled externally
     * @throws IOException
     */
    public boolean connect(String accessToken) throws IOException {
        if (callback != null) {
            callback.start();
        }

        final Request request = new Request.Builder()
                .url(url)
                .addHeader("Connection", "close")
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        Response response = null;
        try {
            currentCall = client.newCall(request);
            response = currentCall.execute();
            final String boundary = getBoundary(response);
            BufferedSource source = response.body().source();
            Buffer buffer = new Buffer();
            while (!source.exhausted()) {
                source.read(buffer, 8192);
                AvsResponse val = new AvsResponse();

                try {
                    val = ResponseParser.parseResponse(buffer.inputStream(), boundary, true);
                } catch (Exception exp) {
                    exp.printStackTrace();
                }

                if (callback != null) {
                    callback.success(val);
                }
            }
        } catch (IOException e) {
            onError(callback, e);
        } finally {
            if (response != null) {
                response.close();
            }
        }

        return currentCall != null && currentCall.isCanceled();
    }

    public void closeConnection() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
    }

    private void onError(final AsyncCallback<AvsResponse, Exception> callback, Exception e) {
        if (callback != null) {
            callback.failure(e);
            callback.complete();
        }
    }

    @Override
    @NotNull
    protected String getEvent() {
        return "";
    }
}
