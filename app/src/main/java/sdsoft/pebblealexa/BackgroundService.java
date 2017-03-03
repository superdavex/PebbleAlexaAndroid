package sdsoft.pebblealexa;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by idave on 2/5/2017.
 */

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;


public class BackgroundService  extends Service {
    BackgroundService intentBG = null;
    private String TAG = "SDALEXA_Service";

    private PebbleKit.PebbleDataReceiver dataReceiver;

    int last_transaction_id = 0;
    long last_transaction_timpestamp = 0;

    static final int ONGOING_NOTIFICATION_ID = 1;

    boolean bAutoVolume = false;
    long curVol = 0;

    AlexaClass al;

    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate(){
        super.onCreate();
        Log.w("TAG", "ScreenListenerService---OnCreate ");
        Intent notificationIntent = new Intent(this, BackgroundService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("PebbleAlexa")
                .setContentText("PebbleAlexa")
                .setSmallIcon(R.drawable.ic_icon_format_black_24dp)
                .setContentIntent(pendingIntent)
                .setTicker("PebbleAlexa")
                .build();

        notification.priority= Notification.PRIORITY_MIN;

        startForeground(ONGOING_NOTIFICATION_ID, notification);

    }
    //define callback interface
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        intentBG = this;
        Log.d(TAG,"start");
        al = new AlexaClass(this);

        // Create a new receiver to get AppMessages from the C app
        PebbleKit.PebbleDataReceiver dataReceiver = new PebbleKit.PebbleDataReceiver(Constants.WATCH_UUID) {

            @Override
            public void receiveData(Context context, int transaction_id, PebbleDictionary dict) {

                // A new AppMessage was received, tell Pebble
                PebbleKit.sendAckToPebble(context, transaction_id);
                boolean bSpeakAudio = true;
                boolean bTextToSpeech = true;

                if(last_transaction_id != transaction_id || (System.currentTimeMillis() - last_transaction_timpestamp > 1000)  ){


                    if (dict.contains(Constants.MESSAGE_KEY_Speak_Audio)){
                        bSpeakAudio = dict.getUnsignedIntegerAsLong(Constants.MESSAGE_KEY_Speak_Audio) > 0;
                        Log.d(TAG, "Received from pebble MESSAGE_KEY_Speak_Audio - " + bSpeakAudio);
                    }

                    if (dict.contains(Constants.MESSAGE_KEY_Text_To_Speech)){
                        bTextToSpeech = dict.getUnsignedIntegerAsLong(Constants.MESSAGE_KEY_Text_To_Speech) > 0;
                        Log.d(TAG, "Received from pebble MESSAGE_KEY_Text_To_Speech - " + bTextToSpeech);
                    }

                    if (dict.contains(Constants.MESSAGE_KEY_Auto_Volume)){
                        bAutoVolume = dict.getUnsignedIntegerAsLong(Constants.MESSAGE_KEY_Auto_Volume) > 0;
                        Log.d(TAG, "Received from pebble MESSAGE_KEY_Auto_Volume - " + bAutoVolume);
                    }

                    if (dict.contains(Constants.MESSAGE_KEY_Volume_Level)){
                        int iAutoVolumeLevel = (int)dict.getUnsignedIntegerAsLong(Constants.MESSAGE_KEY_Volume_Level).intValue() ;

                        if(bAutoVolume)
                            curVol =al.AutoSetVolume(iAutoVolumeLevel);

                        Log.d(TAG, "Received from pebble MESSAGE_KEY_Volume_Level - " + iAutoVolumeLevel);
                    }

                    if (dict.contains(Constants.MESSAGE_KEY_RequestData)){

                            // Read the integer value
                            String ask = dict.getString(Constants.MESSAGE_KEY_RequestData);
                            Log.d(TAG, "Received from pebble id " + transaction_id + "  " + ask);

                            last_transaction_id = transaction_id;
                            last_transaction_timpestamp = System.currentTimeMillis();


                            //sendToPebble(ask + "\r\n\r\n...");
                            al.AskAlexa(ask, spCallbackInterface,bSpeakAudio,bTextToSpeech);
                    }

                }else{
                    Log.d(TAG, "Duplicate from pebble id " + transaction_id );
                }

            }

        };
        // Register the receiver
        PebbleKit.registerReceivedDataHandler(this, dataReceiver);

        return START_STICKY;
    }

    private void sendTextPebble(String sText){
        // Create a new dictionary
        PebbleDictionary dict = new PebbleDictionary();
        dict.addString(Constants.MESSAGE_KEY_RequestData, sText);
        // Send the dictionary
        PebbleKit.sendDataToPebble(intentBG, Constants.WATCH_UUID, dict);
        Log.d(TAG,"Send to Pebble");
    }

    public BSCallback spCallbackInterface = new BSCallback() {
        @Override
        public  void sendToPebble(String sSend) {
            Log.i(TAG, "Send Response");
            sendTextPebble(sSend);

        }

        @Override
        public void audioDone() {
            Log.i(TAG, "Audio playback done, set volume");
            if(bAutoVolume)
                al.AutoSetVolume((int)curVol);
        }

    };
}