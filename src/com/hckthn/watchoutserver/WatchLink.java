package com.hckthn.watchoutserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class WatchLink extends Service {
	static final String TAG = "WatchLink";

	public static final String DISMISS_INTENT = "com.hkthn.slidingwatchscreens.dismiss";
	public static final String NOTIFICATION_INTENT = "com.hkthn.slidingwatchscreens.notification";
	public static final int NOTIFICATION_ID = 300;
	public static final String UUID = "7bcc1440-858a-11e3-baa7-0800200c9a66"; 
	
	Handler handler;
	
	SharedPreferences prefs;
	BroadcastReceiver br;
	
	BluetoothAdapter ba;
	
	ConnectThread ct;
	
	IOThread io;
	
	boolean hasConnected = false;
	
	@Override
	public void onCreate(){
		super.onCreate();
		log("On create");
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		br = new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent) {
				if(intent.getAction().equals(NOTIFICATION_INTENT)){
					log("Got notification request");
					//Send to phone if possible
				}
			}
		};
		IntentFilter intf = new IntentFilter();
		intf.addAction(NOTIFICATION_INTENT);
		LocalBroadcastManager.getInstance(this).registerReceiver(br, intf);
		
		ba = BluetoothAdapter.getDefaultAdapter();
		
		updateNotification("Watch link waiting...");
		
		ct = new ConnectThread();
		ct.start();
		
		handler = new Handler(new Handler.Callback(){
			@Override
			public boolean handleMessage(Message msg) {
				//Only thing to handle is "read" data, so it's ok
				log("Bytes read");
				return true;
			}
		});
	}
	
	public void updateNotification(String text){
		Intent startSettings = new Intent(this, MainActivity.class);
		startSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent pi = PendingIntent.getActivity(this, -1, startSettings, 0);
		
		NotificationCompat.Builder n = new NotificationCompat.Builder(this)
			.setAutoCancel(false)
			.setOngoing(true)
			.setContentTitle("Link to watch running")
			.setContentText(text)
			.setSmallIcon(R.drawable.ic_launcher)
			.setContentIntent(pi);
			
		this.startForeground(NOTIFICATION_ID, n.build());
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
		log("On destroy");
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    return START_STICKY;
	}
	
	private void log(String text){
		Log.d(TAG, text);
	}
	
	private void handleSocketConnection(BluetoothSocket socket){
		log("New socket to watch found");
		updateNotification("Watch link connected!");
		io = new IOThread(socket);
		io.run();
	}
	
	private class IOThread extends Thread {
		private final BluetoothSocket bs;
		private final InputStream is;
		private final OutputStream os;
		
		public IOThread(BluetoothSocket socket){
			bs = socket;
			InputStream in = null;
			OutputStream out = null;
			
			try {
				in = bs.getInputStream();
				out = bs.getOutputStream();
			} catch (IOException e) {}
			is = in;
			os = out;
		}
		
		public void run(){
			byte[] readBuffer = new byte[4];
			int bytesIn;
			
			while(true){
				try {
					bytesIn = is.read(readBuffer);
					handler.obtainMessage(1, bytesIn, -1, readBuffer);
				} catch (Exception e) {
					break; //Done!
				}
			}
		}
		
		public void write(byte[] bytesOut){
			try {
				os.write(bytesOut);
			} catch (Exception e) {}
		}
		
		public void cancel(){
			try {
				bs.close();
			} catch (IOException e) {}
		}
	}
	
	private class ConnectThread extends Thread {
		private final BluetoothServerSocket server;
		
		public ConnectThread(){
			BluetoothServerSocket tmp = null;
			try {
				tmp = ba.listenUsingRfcommWithServiceRecord("WatchLink", java.util.UUID.fromString(UUID));
				log("Listening worked");
			} catch (Exception e){
				log("Listening failed" + e.getMessage());
			}
			server = tmp;
		}
		
		public void run(){
			BluetoothSocket socket = null;
			while(true){
				try {
					socket = server.accept();
					log("Server accepted");
				} catch (Exception e){
					log("Server failed: " + e.getCause().getMessage());
					log(e.toString());
					break;
				}
				
				if(socket != null){
					handleSocketConnection(socket);
					hasConnected = true;
					log("Has connected");
					try {
						server.close();
					} catch (Exception e) {
						log("Unable to close " + e.getMessage());
					}
					break;
				}
			}
		}
		
		public void cancel(){
			try {
				server.close();
			} catch (Exception e) {
				log("Unable to close " + e.getMessage());
			}
		}
	}
}
