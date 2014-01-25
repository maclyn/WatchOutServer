package com.hckthn.watchoutserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

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
import android.telephony.SmsManager;
import android.util.Log;

public class WatchLink extends Service {
	static final String TAG = "WatchLink";
	
	public static final String EOS = "<!EOS!>";

	public static final String DISMISS_INTENT = "com.hkthn.slidingwatchscreens.dismiss";
	public static final String NOTIFICATION_INTENT = "com.hkthn.slidingwatchscreens.notification";
	public static final int NOTIFICATION_ID = 300;
	public static final String UUID = "7bcc1440-858a-11e3-baa7-0800200c9a66"; 
		
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
		
		updateNotification("Watch link started...");
		
		attemptToConnect("Watch link waiting...");
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
	
	private void handleInput(String dataIn){
		log("Data in: " + dataIn);
		dataIn = (String) dataIn.subSequence(0, dataIn.indexOf(EOS));
		int barPos = dataIn.indexOf("|");
		if(barPos != -1){
			String requestType = dataIn.substring(0, barPos);
			String requestData = dataIn.substring(barPos+1);
			log("Request type: " + requestType);
			log("Request data: " + requestData);
			
			if(requestType.equals("HELP_REQUEST")){
				log("Initializing help actions");
				io.write("HELP_ACK|Acknowledged.");
				
				String message = prefs.getString(MainActivity.MESSAGE_PREF, "");
				String toSend = "Help! I'm in trouble! Custom message: " + message;
				String allNumbers = prefs.getString(MainActivity.CONTACT_NUMBER_BASE, "");
				if(allNumbers.length() > 0){
					if(allNumbers.indexOf(",") != 0){
						String[] numbers = allNumbers.split(",");
						for(int i = 0; i < numbers.length; i++){
							SmsManager.getDefault().sendTextMessage(numbers[i], null, toSend, null, null);
						}
					} else {
						SmsManager.getDefault().sendTextMessage(allNumbers, null, toSend, null, null);
					}
				}
			}
		} else {
			log("Error! Improper formatting");
		}
	}
	
	private void attemptToConnect(String newUpdate){
		updateNotification(newUpdate);
		ct = new ConnectThread();
		ct.start();
	}
	
	private class IOThread extends Thread {
		private final BluetoothSocket bs;
		private final InputStream is;
		private final OutputStream os;
		
		public IOThread(BluetoothSocket socket){
			log("IOThread created");
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
			log("Running IOThread...");
			StringBuilder stringToSend = new StringBuilder();
			byte[] readBuffer = new byte[1024];
			int newBytes;
			
			while(true){
				try {
					while((newBytes = is.read(readBuffer)) != -1){ //So long as we're not at the end of stream
						stringToSend.append(new String(readBuffer, 0, newBytes, Charset.defaultCharset()));
						int eosIndex = stringToSend.indexOf(EOS);
						if(eosIndex != -1){
							String toSend = stringToSend.toString();
							handleInput(toSend);
							stringToSend = new StringBuilder();
						}
					}
				} catch (Exception e) {
					log("Exception:" + e.getMessage());
					log("ETOSTring: " + e.toString());
					log("IOThread done; connection lost");
					this.cancel();
					attemptToConnect("Connection lost; trying to rejoin...");
					break; //done -- connection lost
				}
			}
		}
		
		public void write(String dataIn){
			log("Writing bytes to output streams");
			dataIn = dataIn + EOS;
			try {
				byte[] dataBytes = dataIn.getBytes();
				os.write(dataBytes);
			} catch (Exception e) {}
		}
		
		public void cancel(){
			log("Cancelling IOThread...");
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
				updateNotification("Watch link waiting...");
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
					log("Server failed");
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
