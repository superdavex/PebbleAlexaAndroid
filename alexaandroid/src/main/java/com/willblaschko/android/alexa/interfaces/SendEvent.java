package com.willblaschko.android.alexa.interfaces;

import android.util.Log;

import com.willblaschko.android.alexa.callbacks.AsyncCallback;
import com.willblaschko.android.alexa.connection.ClientUtil;
import com.willblaschko.android.alexa.interfaces.response.ResponseParser;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * An abstract class that supplies a DataOutputStream which is used to send a POST request to the AVS server
 * with a voice data intent, it handles the response with completePost() (called by extending classes)
 */
public abstract class SendEvent {

    private final static String TAG = "SendEvent";

    //the output stream that extending classes will use to pass data to the AVS server
    protected ByteArrayOutputStream mOutputStream = new ByteArrayOutputStream();
    protected AsyncCallback<Void, Exception> mCallback;

    private Call currentCall;

    //OkHttpClient for transfer of data
    Request.Builder mRequestBuilder = new Request.Builder();
    MultipartBody.Builder mBodyBuilder;

    /**
     * Set up all the headers that we need in our OkHttp POST/GET, this prepares the connection for
     * the event or the raw audio that we'll need to pass to the AVS server
     * @param url the URL we're posting to, this is either the default {@link com.willblaschko.android.alexa.data.Directive} or {@link com.willblaschko.android.alexa.data.Event} URL
     * @param accessToken the access token of the user who has given consent to the app
     */
    protected void prepareConnection(String url, String accessToken) {

        //set the request URL
        mRequestBuilder.url(url);

        //set our authentication access token header
        mRequestBuilder.addHeader("Authorization", "Bearer " + accessToken);

        mBodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", "metadata", RequestBody.create(MediaType.parse("application/json; charset=UTF-8"), getEvent()));

        //reset our output stream
        mOutputStream = new ByteArrayOutputStream();
    }

    /**
     * When finished adding voice data to the output, we close it using completePost() and it is sent off to the AVS server
     * and the response is parsed and returned
     * @return AvsResponse with all the data returned from the server
     * @throws IOException if the OkHttp request can't execute
     * @throws AvsException if we can't parse the response body into an {@link AvsResponse} item
     * @throws RuntimeException
     */
    protected AvsResponse completePost() throws IOException, AvsException, RuntimeException {
        Log.d(TAG,"Complete Post add form dadtaparasts");
        addFormDataParts(mBodyBuilder);
        Log.d(TAG,"Complete Post body build");
        mRequestBuilder.post(mBodyBuilder.build());
        return parseResponse();
    }

    protected AvsResponse completeGet() throws IOException, AvsException, RuntimeException {

        mRequestBuilder.get();
        return parseResponse();
    }

    protected void cancelCall() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
    }

    private AvsResponse parseResponse() throws IOException, AvsException, RuntimeException {

        Log.d(TAG,"Parse Response build");
        Request request = mRequestBuilder.build();


        Log.d(TAG,"Parse Response current call");
        currentCall = ClientUtil.getTLS12OkHttpClient().newCall(request);

        try {
            Log.d(TAG,"Parse Response execute");
            Response response = currentCall.execute();

            Log.d(TAG,"Parse Response code " + response.code() );
            if(response.code() == HttpURLConnection.HTTP_NO_CONTENT){
                Log.w(TAG, "Received a 204 response code from Amazon, is this expected?");
            }

            Log.d(TAG,response.headers().toString());

            final AvsResponse val = response.code() == HttpURLConnection.HTTP_NO_CONTENT ? new AvsResponse() :
                    ResponseParser.parseResponse(response.body().byteStream(), getBoundary(response));


            response.body().close();

            return val;
        } catch (IOException exp) {
            Log.d(TAG,exp.toString());
            if (!currentCall.isCanceled()) {
                return new AvsResponse();
            }
        }
        return null;
    }

    protected String getBoundary(Response response) throws IOException {
        Headers headers = response.headers();
        String header = headers.get("content-type");
        String boundary = "";

        if (header != null) {
            Pattern pattern = Pattern.compile("boundary=(.*?);");
            Matcher matcher = pattern.matcher(header);
            if (matcher.find()) {
                boundary = matcher.group(1);
            }
        } else {
            Log.i(TAG, "Body: " + response.body().string());
        }
        return boundary;
    }


    /**
     * When override, our extending classes can add their own data to the POST
     * @param builder with audio data
     */
    protected void addFormDataParts(MultipartBody.Builder builder){

    };

    /**
     * Get our JSON {@link com.willblaschko.android.alexa.data.Event} for this call
     * @return the JSON representation of the {@link com.willblaschko.android.alexa.data.Event}
     */
    @NotNull
    protected abstract String getEvent();

}