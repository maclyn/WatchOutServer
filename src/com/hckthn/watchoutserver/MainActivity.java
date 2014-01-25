package com.hckthn.watchoutserver;

import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
	public static final int REQUEST_BT_ENABLE = 5000;
	public static final String TAG = "MainActivity";
	
	SharedPreferences pref;
	
	Button setupBluetooth;
	Button startService;
	BluetoothAdapter btAdapter;
	
	BluetoothDevice[] devices;
	
	boolean isStarted = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		pref = PreferenceManager.getDefaultSharedPreferences(this);
		
		startService = (Button) this.findViewById(R.id.startService);
		startService.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Intent watchLink = new Intent(MainActivity.this, WatchLink.class);
				if(!isStarted){
					MainActivity.this.startService(watchLink);
					startService.setText("Stop Service");
					isStarted = true;
				} else {
					MainActivity.this.stopService(watchLink);
					startService.setText("Start Service");
					isStarted = false;
				}
			}
		});
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
	}

	private void log(String text){
		Log.d(TAG, text);
	}
}
