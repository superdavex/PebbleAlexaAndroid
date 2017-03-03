package sdsoft.pebblealexa;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;


import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;


import com.willblaschko.android.alexa.AlexaManager;
import com.willblaschko.android.alexa.audioplayer.AlexaAudioPlayer;
import com.willblaschko.android.alexa.callbacks.AsyncCallback;
import com.willblaschko.android.alexa.callbacks.AuthorizationCallback;

import com.willblaschko.android.alexa.interfaces.AvsItem;
import com.willblaschko.android.alexa.interfaces.AvsResponse;
import com.willblaschko.android.alexa.interfaces.audioplayer.AvsPlayContentItem;
import com.willblaschko.android.alexa.interfaces.audioplayer.AvsPlayRemoteItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsMediaNextCommandItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsMediaPauseCommandItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsMediaPlayCommandItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsMediaPreviousCommandItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsReplaceAllItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsReplaceEnqueuedItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsStopItem;
import com.willblaschko.android.alexa.interfaces.speaker.AvsAdjustVolumeItem;
import com.willblaschko.android.alexa.interfaces.speaker.AvsSetMuteItem;
import com.willblaschko.android.alexa.interfaces.speaker.AvsSetVolumeItem;
import com.willblaschko.android.alexa.interfaces.speechrecognizer.AvsExpectSpeechItem;
import com.willblaschko.android.alexa.interfaces.speechsynthesizer.AvsSpeakItem;


import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.Hypothesis;

import static android.content.Context.AUDIO_SERVICE;


/**
 * Created by dave on 2/3/2017.
 */

public class AlexaClass{
    private AlexaManager alexaManager;
    private AlexaAudioPlayer audioPlayer;
    private List<AvsItem> avsQueue = new ArrayList<>();
    Context _context;
    String TAG = "ALEXA_CLASS";
    File assetsDir;
    private Decoder psDecoder;
    private BSCallback mspcallback;
    private boolean mbSpeakResponse = true;
    private boolean mbTextToSpeech = true;
    public boolean bRecDecoderSetup = false;
    public boolean mbSpeakingAudio = false;

    public AlexaClass(Context context) {
        this._context = context;

        initAlexaAndroid();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        if (!prefs.getBoolean("firstTime", false)) {
            // Need to make sure this runs one after install
            Log.d(TAG,"First run need to install FFMPEG");
            FFmpeg ffmpegload = FFmpeg.getInstance(_context);
            try {
                ffmpegload.loadBinary(new LoadBinaryResponseHandler() {

                    @Override
                    public void onStart() {}

                    @Override
                    public void onFailure() {}

                    @Override
                    public void onSuccess() {
                        Log.d(TAG,"FFMPEG instlled");
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(_context);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("firstTime", true);
                        editor.commit();

                    }

                    @Override
                    public void onFinish() {}
                });
            } catch (FFmpegNotSupportedException e) {
                // Handle if FFmpeg is not supported by device
                Log.d(TAG,"FFMPEG not supported");
            }

        }

        //Run an async check on whether we're logged in or not
       // alexaManager.checkLoggedIn(mLoggedInCheck);

       //Check if the user is already logged in to their Amazon account
       // alexaManager.checkLoggedIn(AsyncCallback...);

        //Log the user in
        alexaManager.logIn(authCallback);

    }

    long AutoSetVolume(int volume){
        AudioManager am = (AudioManager) _context.getSystemService(AUDIO_SERVICE);
        final int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        long curVol= am.getStreamVolume(AudioManager.STREAM_MUSIC);
        long setVol = 0;

        setVol = (volume * 10) * max / 100;

        am.setStreamVolume(AudioManager.STREAM_MUSIC, (int) setVol, AudioManager.FLAG_VIBRATE);

        Log.d(TAG, "Received from pebble, cur vol " + curVol + " set vol " + volume);
        return curVol;
    }

    void AskAlexa(String sQuestion,BSCallback spresult,boolean bSpeakResponse, boolean bTextToSpeech){
        mspcallback = spresult;
        mbSpeakResponse = bSpeakResponse;
        mbTextToSpeech = bTextToSpeech;

        alexaManager.sendTextRequest(sQuestion, requestCallback);

    }

    private AuthorizationCallback authCallback = new AuthorizationCallback() {
        @Override
        public void onCancel(){
            //your on error code
            Log.i(TAG, "Cancel" );
        }

        @Override
        public void onSuccess() {
            Log.i(TAG, "auth Success-" );
            //alexaManager.sendTextRequest("What time is it", requestCallback);

        }

        @Override
        public void onError(Exception error) {
            //your on error code
            Log.i(TAG, "auth except-" + error.toString() );
        }

    };


    private void initAlexaAndroid(){
        //get our AlexaManager instance for convenience
        alexaManager = AlexaManager.getInstance(_context, "SD_PEBBLE_GATEWAY");

        //instantiate our audio player
        audioPlayer = AlexaAudioPlayer.getInstance(_context);

        //Callback to be able to remove the current item and check queue once we've finished playing an item
        audioPlayer.addCallback(alexaAudioPlayerCallback);

        // Init speech recog
        try {
            setupRecognizer();
        }
        catch(Exception e){

        }

    }

    //Our callback that deals with removing played items in our media player and then checking to see if more items exist
    private  AlexaAudioPlayer.Callback alexaAudioPlayerCallback = new AlexaAudioPlayer.Callback() {
        @Override
        public void playerPrepared(AvsItem pendingItem) {

        }

        @Override
        public void playerProgress(AvsItem currentItem, long offsetInMilliseconds, float percent) {};

        @Override
        public void dataError(AvsItem item, Exception e){};

        @Override
        public void itemComplete(AvsItem completedItem) {
            // Need to find out why its not instantiated some times and fix.
            if(mspcallback != null)
                mspcallback.audioDone();

            mbSpeakingAudio = false;
            avsQueue.remove(completedItem);
            checkQueue();
        }


        public  boolean playerError(AvsItem av,int what, int extra) {
            return false;
        }

        public void dataError(Exception e) {

        }
    };

    //async callback for commands sent to Alexa Voice
    private AsyncCallback<AvsResponse, Exception> requestCallback = new AsyncCallback<AvsResponse, Exception>() {
        @Override
        public void start() {
            //your on start code
        }

        @Override
        public void success(AvsResponse result) {
            Log.i(TAG, "Voice Success");
            handleResponse(result);
        }

        @Override
        public void failure(Exception error) {
            Log.i(TAG, "Send error " + error.toString() );
            //your on error code
        }

        @Override
        public void complete() {
            Log.i(TAG, "Response Complete");

            // Need to find out why its not instantiated some times and fix.
            if(! mbSpeakingAudio && mspcallback != null)
                mspcallback.audioDone();

            //your on complete code
        }
    };

    /**
     * Handle the response sent back from Alexa's parsing of the Intent, these can be any of the AvsItem types (play, speak, stop, clear, listen)
     * @param response a List<AvsItem> returned from the mAlexaManager.sendTextRequest() call in sendVoiceToAlexa()
     */
    private void handleResponse(AvsResponse response){
        Log.d("TAG", "Resposne code " + response.toString());

        if(response != null){
            //if we have a clear queue item in the list, we need to clear the current queue before proceeding
            //iterate backwards to avoid changing our array positions and getting all the nasty errors that come
            //from doing that
            for(int i = response.size() - 1; i >= 0; i--){
                if(response.get(i) instanceof AvsReplaceAllItem || response.get(i) instanceof AvsReplaceEnqueuedItem){
                    //clear our queue
                    avsQueue.clear();
                    //remove item
                    response.remove(i);
                }
            }
            avsQueue.addAll(response);
        }
        checkQueue();
    }

    void convertMP3toWav(byte[] bMP3){

        Log.d(TAG,"Converting MP3");
        String swavfiletmp="";
        String smp3filetemp = "";
        File outputDir;
        File outputFile;
        try {
            outputDir = _context.getCacheDir(); // context being the Activity pointer
            outputFile = File.createTempFile("SDTEMP", ".wav", outputDir);
            swavfiletmp = outputFile.getAbsolutePath();
            outputFile.delete();
        }
        catch(Exception e){
            Log.d(TAG,("Could not get wav temp file"));
            return;
        }
        final String swavfile = swavfiletmp;

        // Write to temp mp3
        try{
            outputDir = _context.getCacheDir(); // context being the Activity pointer
            outputFile = File.createTempFile("SDTEMP", ".mp3", outputDir);
            smp3filetemp = outputFile.getAbsolutePath();
            Log.d(TAG,"Writing MP3-" + smp3filetemp);
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(bMP3);
            fos.getFD().sync();
            fos.close();
        }
        catch(Exception e){
            Log.d(TAG,"Error writing temp mp3" + e.toString());
        }
        final String smp3file = smp3filetemp;

        String cmd[]=new String[11];
        cmd[0] = "-loglevel";
        cmd[1] = "panic";
        cmd[2] = "-i";
        cmd[3] =  smp3file ;
        cmd[4] = "-acodec";
        cmd[5] = "pcm_s16le";
        cmd[6] = "-ac";
        cmd[7] = "1";
        cmd[8] = "-ar";
        cmd[9] = "16000";
        cmd[10] = swavfile;

        //Log.d(TAG,"Killing Running FFMPEG");
        FFmpeg ffmpeg = FFmpeg.getInstance(_context);

        //ffmpeg.killRunningProcesses();
        Log.d(TAG,"Running FFMPEG");
        //'ffmpeg', '-i',input_name, '-acodec', 'pcm_s16le', '-ac', '1','-ar','16000', input_name + '.wav'])

        try {
            // to execute "ffmpeg -version" command you just need to pass "-version"
           ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {

                @Override
                public void onStart() {
                    Log.d(TAG,"FFMPEG start");
                }

                @Override
                public void onProgress(String message) {
                    Log.d("FFMPEG progress ", message);
                }
                @Override
                public void onFailure(String message) {
                    Log.d("FFMPEG_failure ", message);
                }
                @Override
                public void onSuccess(String message){

                    Log.d("FFMPEG_success ", message);
                    DataInputStream dis= null;
                    File file = new File(swavfile);
                    byte[] byteData = new byte[(int) file.length()];
                    Log.d(TAG,"WAV " + swavfile + " length is " + byteData.length);

                    try {
                        dis = new DataInputStream(new FileInputStream(swavfile));
                    }
                    catch(Exception e){}
                    try{
                        dis.readFully(byteData);
                        dis.close();
                        convertSpeechTT(byteData);
                    }
                    catch(Exception e){
                        Log.d(TAG,"Error reading wav");
                    }
                    finally{
                        Log.d(TAG,"Deleting Temp files ");
                        File filedel;
                        boolean bdeleted;

                        filedel = new File(smp3file);
                        bdeleted = filedel.delete();
                        if(! bdeleted)
                            Log.d(TAG,smp3file + " not deleted");

                        filedel = new File(swavfile);
                        bdeleted = filedel.delete();
                        if(! bdeleted)
                            Log.d(TAG,swavfile + " not deleted");
                    }
                 }

                @Override
                public void onFinish(){
                    Log.d(TAG,"FFMPEG_finish");
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // Handle if FFmpeg is already running
        }


        return;
    }
    /**
     * Check our current queue of items, and if we have more to parse (once we've reached a play or listen callback) then proceed to the
     * next item in our list.
     *
     * We're handling the AvsReplaceAllItem in handleResponse() because it needs to clear everything currently in the queue, before
     * the new items are added to the list, it should have no function here.
     */
    private void checkQueue() {

        //if we're out of things, hang up the phone and move on
        if (avsQueue.size() == 0) {
            return;
        }

        AvsItem current = avsQueue.get(0);

        if (current instanceof AvsPlayRemoteItem) {
            Log.d(TAG,"AvsPlayRemoteItem");
            //play a URL
            if (mbSpeakResponse && !audioPlayer.isPlaying()) {
                audioPlayer.playItem((AvsPlayRemoteItem) current);
            }else{
                avsQueue.remove(current);
            }
        } else if (current instanceof AvsPlayContentItem) {
            Log.d(TAG,"AvsPlayContentItem");
            //play a URL
            if (mbSpeakResponse && !audioPlayer.isPlaying()) {
                audioPlayer.playItem((AvsPlayContentItem) current);
            }
            else{
                avsQueue.remove(current);
            }
        } else if (current instanceof AvsSpeakItem) {
            Log.d(TAG,"Speak Item");
            mbSpeakingAudio = true;
            //play a sound file if audio enabled
            if (mbSpeakResponse && !audioPlayer.isPlaying())
                audioPlayer.playItem((AvsSpeakItem) current);

            if (mbTextToSpeech && !audioPlayer.isPlaying()) {
                // Comes in as MP3 need to get to wav
                try {
                   convertMP3toWav(((AvsSpeakItem) current).getAudio());
                }
                catch(Exception e){
                    Log.d(TAG,"Error converting mp3. " + e.toString());
                }
                finally{
                    if(!mbSpeakResponse) avsQueue.remove(current);
                }
            }

            // If not speaking or transcribing remove from queue
            if(!mbSpeakResponse && ! mbTextToSpeech)
                avsQueue.remove(current);

        } else if (current instanceof AvsStopItem) {
            //stop our play
            audioPlayer.stop();
            avsQueue.remove(current);
        } else if (current instanceof AvsReplaceAllItem) {
            audioPlayer.stop();
            avsQueue.remove(current);
        } else if (current instanceof AvsReplaceEnqueuedItem) {
            avsQueue.remove(current);
        } else if (current instanceof AvsExpectSpeechItem) {
            //listen for user input
            audioPlayer.stop();
            //startListening();
        } else if (current instanceof AvsSetVolumeItem) {
            setVolume(((AvsSetVolumeItem) current).getVolume());
            mspcallback.sendToPebble("Volume Set");
            avsQueue.remove(current);
        } else if(current instanceof AvsAdjustVolumeItem){
            adjustVolume(((AvsAdjustVolumeItem) current).getAdjustment());
            mspcallback.sendToPebble("Volume Set");
            avsQueue.remove(current);
        } else if(current instanceof AvsSetMuteItem){
            setMute(((AvsSetMuteItem) current).isMute());
            mspcallback.sendToPebble("Volume Set");
            avsQueue.remove(current);
        }else if(current instanceof AvsMediaPlayCommandItem){
            //fake a hardware "play" press
            sendMediaButton(_context, KeyEvent.KEYCODE_MEDIA_PLAY);
        }else if(current instanceof AvsMediaPauseCommandItem){
            //fake a hardware "pause" press
            sendMediaButton(_context, KeyEvent.KEYCODE_MEDIA_PAUSE);
        }else if(current instanceof AvsMediaNextCommandItem){
            //fake a hardware "next" press
            sendMediaButton(_context, KeyEvent.KEYCODE_MEDIA_NEXT);
        }else if(current instanceof AvsMediaPreviousCommandItem){
            //fake a hardware "previous" press
            sendMediaButton(_context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
        else{
            Log.d(TAG,"Unknown queue response " + current.toString());
        }

    }

    //adjust our device volume
    private void adjustVolume(long adjust){
        setVolume(adjust, true);
    }

    //set our device volume
    private void setVolume(long volume){
        setVolume(volume, false);
    }

    //set our device volume, handles both adjust and set volume to avoid repeating code
    private void setVolume(final long volume, final boolean adjust){
        AudioManager am = (AudioManager) _context.getSystemService(AUDIO_SERVICE);
        final int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        long vol= am.getStreamVolume(AudioManager.STREAM_MUSIC);
        if(adjust){
            vol += volume * max / 100;
        }else{
            vol = volume * max / 100;
        }
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (int) vol, AudioManager.FLAG_VIBRATE);

        //confirm volume change
        alexaManager.sendVolumeChangedEvent(volume, vol == 0, requestCallback);
    }

    //set device to mute
    private void setMute(final boolean isMute){
        AudioManager am = (AudioManager) _context.getSystemService(AUDIO_SERVICE);
        am.setStreamMute(AudioManager.STREAM_MUSIC, isMute);
        //confirm device mute
        alexaManager.sendMutedEvent(isMute, requestCallback);
    }

    private static void sendMediaButton(Context context, int keyCode) {
        KeyEvent keyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        context.sendOrderedBroadcast(intent, null);

        keyEvent = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent);
        context.sendOrderedBroadcast(intent, null);
    }

    private void setupRecognizer() throws IOException {
        Log.d(TAG,"Setting up recognition decoder");
        try {
            Assets assets = new Assets(_context);
            assetsDir = assets.syncAssets();

        } catch (IOException e) {
            Log.d(TAG, "Error getting decoder Assets" + e.toString());
            return;
        }

        Log.d(TAG,"Getting Decoder");

        try {
            System.loadLibrary("pocketsphinx_jni");
            Config config = Decoder.defaultConfig();
            config.setString("-dict",assetsDir + "/cmudict-en-us.dict"); //setDictionary
            config.setString("-hmm",assetsDir + "/en-us"); //setAcousticModel
            config.setString("-lm",assetsDir + "/en-us.lm.bin");
            psDecoder = new Decoder(config);


            //config.setString("-lm",assetsDir + "/en-us.lm.bin");
            //psDecoder = new Decoder(config);
            Log.d(TAG, "Done getting Decoder");
            bRecDecoderSetup = true;
        }
        catch(Exception e){
            Log.d(TAG, "Error getting Decoder - " + e.toString());
        }


    }

    private void convertSpeechTT(byte[] byteData){

        Long tsStart = System.currentTimeMillis()/1000;

        short[] shortData = new short[byteData.length/2];
        ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData);

        Log.d(TAG,"Converting Speetch to Text");

        psDecoder.startUtt();
        psDecoder.processRaw(shortData, shortData.length, false, false);

        psDecoder.endUtt();


        //Segment seg = ds.seg();
        Log.d("PS","Done utterance");
        Hypothesis hypo = psDecoder.hyp();
        double dif = (System.currentTimeMillis()/1000) - tsStart;
        if(hypo != null ) {
            String sHype = psDecoder.hyp().getHypstr();
            mspcallback.sendToPebble(sHype + "\r\n\r\n" + dif + "s");
            Log.d(TAG, "Hypo is - " + sHype);
        }
        else{
            Log.d(TAG,"No Hypothesis");
        }

        Log.d("PS","Processed in " + dif);
    }
}
