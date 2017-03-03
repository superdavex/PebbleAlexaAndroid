package sdsoft.pebblealexa;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;


import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.HashMap;


public class Main extends AppCompatActivity {
    private HashMap<String, String> utterParam;
    String TAG = "PEBBLE_ALEXA";


    SharedPreferences prefs;

    private void sendTextPebble(int key,String sText){

        // Store setting
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("Favorite_" + (key - 5), sText);
        editor.commit();

        // Start the app if not started?
        PebbleKit.startAppOnPebble(getApplicationContext(), Constants.WATCH_UUID);

        // Create a new dictionary
        PebbleDictionary dict = new PebbleDictionary();
        dict.addString(key, sText);
        // Send the dictionary
        PebbleKit.sendDataToPebble(this, Constants.WATCH_UUID, dict);
        Log.d(TAG,"Send to Pebble key " + key);
    }

    EditText txtLast1;
    EditText txtLast2;
    EditText txtLast3;
    EditText txtLast4;
    EditText txtLast5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start Service
        Intent intent = new Intent(this, BackgroundService.class);
        startService(intent);

        setContentView(R.layout.activity_main);

        txtLast1= (EditText) findViewById(R.id.txtLast1);
        txtLast2= (EditText) findViewById(R.id.txtLast2);
        txtLast3= (EditText) findViewById(R.id.txtLast3);
        txtLast4= (EditText) findViewById(R.id.txtLast4);
        txtLast5= (EditText) findViewById(R.id.txtLast5);


        // Get setting
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        txtLast1.setText(prefs.getString("Favorite_1","What is the time?"));
        txtLast2.setText(prefs.getString("Favorite_2","What is the weather?"));
        txtLast3.setText(prefs.getString("Favorite_3","Tell me a joke."));
        txtLast4.setText(prefs.getString("Favorite_4","Set volume to 10."));
        txtLast5.setText(prefs.getString("Favorite_5","What is my flash briefing?"));


        txtLast1.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus) {
                    sendTextPebble(Constants.MESSAGE_KEY_Favorite1, txtLast1.getText().toString());
                }
            }
        });

        txtLast2.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus) {
                    // Cheat since first may not be done yet.  Need to fix
                    sendTextPebble(Constants.MESSAGE_KEY_Favorite1, txtLast1.getText().toString());
                    sendTextPebble(Constants.MESSAGE_KEY_Favorite2, txtLast2.getText().toString());
                }
            }
        });

        txtLast3.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus) {
                    sendTextPebble(Constants.MESSAGE_KEY_Favorite3, txtLast3.getText().toString());
                }
            }
        });

        txtLast4.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus) {
                    sendTextPebble(Constants.MESSAGE_KEY_Favorite4, txtLast4.getText().toString());
                }
            }
        });

        txtLast5.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus) {
                    sendTextPebble(Constants.MESSAGE_KEY_Favorite5, txtLast5.getText().toString());
                }
            }
        });

        return;
    }



    @Override
    public void onResume() {
        super.onResume();

    }

}
