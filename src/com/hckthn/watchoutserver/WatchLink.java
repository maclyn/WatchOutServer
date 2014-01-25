package com.hckthn.watchoutserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

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
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

public class WatchLink extends Service {
	static final String TAG = "WatchLink";
	
	public static final String EOS = "<!EOS!>";

	public static final String DISMISS_INTENT = "com.hkthn.slidingwatchscreens.dismiss";
	public static final String NOTIFICATION_INTENT = "com.hkthn.slidingwatchscreens.notification";
	public static final int NOTIFICATION_ID = 300;
	public static final String UUID = "7bcc1440-858a-11e3-baa7-0800200c9a66"; 
		
	Handler h = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			//Object always going to be a string
			log("Handler received message");
			String s = (String) msg.obj;
			handleInput(s);
		}
	};
	
	SharedPreferences prefs;
	BroadcastReceiver br;
	
	BluetoothAdapter ba;
	LocationManager lm;

	MediaRecorder mr;
	SimpleDateFormat fileNameDate;
	SimpleDateFormat locationSdf;
	String saveLocation;
	
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
		lm = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		
		mr = new MediaRecorder();
		mr.setOnErrorListener(new OnErrorListener(){
			@Override
			public void onError(MediaRecorder arg0, int arg1, int arg2) {
				log("Media recorder error: " + arg1);
			}
		});
		mr.setOnInfoListener(new OnInfoListener(){
			@Override
			public void onInfo(MediaRecorder arg0, int arg1, int arg2) {
				log("Media info: " + arg1);
			}
		});
		
		saveLocation = Environment.getExternalStorageDirectory() + "/WatchOutRecording/";
		if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
			Toast.makeText(this, "Storage media unavailalbe! Unable to save recordings (WatchOut)!" , Toast.LENGTH_LONG).show();
		}
		File saveFile = new File(saveLocation);
		if(!saveFile.exists() || !saveFile.isDirectory()){
			saveFile.mkdirs();
		}
		
		fileNameDate = new SimpleDateFormat("yyyy-mm-dd_HH:mm:ss", Locale.US);
		locationSdf = new SimpleDateFormat("h:mm:ss aa EEE MMM dd", Locale.US);
		
		updateNotification("Watch link started...");
		
		attemptToConnect("Watch link waiting...");
	}
	
	public void startRecording(){
		try {
			log("Starting recording");
			mr.setAudioSource(MediaRecorder.AudioSource.MIC);
			mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			mr.setOutputFile(saveLocation + fileNameDate.format(GregorianCalendar.getInstance().getTime()) + ".3gp");
			
			try {
				mr.prepare();
				mr.start();
				log("Recording started");
				
				Timer t = new Timer();
				t.schedule(new TimerTask(){
					@Override
					public void run() {
						log("Recording stopped");
						mr.stop();
					}
				}, 120000);
			} catch (Exception e) {
				log("Error starting recording: " + e.getMessage());
			}
		} catch (Exception e) {
			log("Media recorder in bad state when called upon");
		}
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
		if(mr == null) mr.release();
		mr = null;
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
				String toSend = "Help! I'm in trouble! My message: " + message + "; My last location: ";
				
				//Get last location
				Location gpsL = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
				Location netL = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				if(gpsL != null){
					Date d = new Date(gpsL.getTime());
					String formatted = locationSdf.format(d);
					if(gpsL.getTime() - System.currentTimeMillis() < 60000){ //If it's less than a minute old, it's recent
						toSend += gpsL.getLatitude() + ", " + gpsL.getLongitude() + " at " + formatted + " (GPS)";
					} else { //Set listener for better data to send when you get a chance
						toSend += gpsL.getLatitude() + ", " + gpsL.getLongitude() + " at " + formatted + " (GPS, old data)";
						scheduleLocationUpdates();
					}
				} else if (netL != null){
					Date d = new Date(netL.getTime());
					String formatted = locationSdf.format(d);
					toSend += netL.getLatitude() + ", " + netL.getLongitude() + " at " + formatted + " (network, old data)";
					scheduleLocationUpdates();
				}
				
				//Help messages
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
				
				//Start recording
				startRecording();
			}
		} else {
			log("Error! Improper formatting");
		}
	}
	
	private void scheduleLocationUpdates(){
		Criteria locationCriteria = new Criteria();
		locationCriteria.setAccuracy(Criteria.ACCURACY_FINE);
		log("Calling for an update");
		lm.requestSingleUpdate(locationCriteria, new LocationListener(){
			@Override
			public void onLocationChanged(Location gpsL) {
				String toSend = "More accurate location of person in trouble: ";
				if(gpsL != null){
					Date d = new Date(gpsL.getTime());
					String formatted = locationSdf.format(d);
					if(gpsL.getTime() - System.currentTimeMillis() < 60000){ //If it's less than a minute old, it's recent
						toSend += gpsL.getLatitude() + ", " + gpsL.getLongitude() + " at " + formatted + " (GPS)";
					} else { //CAn't get anything better
						toSend += gpsL.getLatitude() + ", " + gpsL.getLongitude() + " at " + formatted + " (GPS, old data)";
					}
				}
			
				//Send message
				//Help messages
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

			@Override
			public void onProviderDisabled(String arg0) {
			}

			@Override
			public void onProviderEnabled(String arg0) {	
			}

			@Override
			public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
			}
		}, null);
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
							Message m = h.obtainMessage(1, toSend);
							h.sendMessage(m);
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
				log("Listening failed: " + e.getLocalizedMessage());
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
