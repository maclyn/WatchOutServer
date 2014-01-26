package com.hckthn.watchoutserver;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class NotificationPusher extends NotificationListenerService {
	public static final String NOTIFICATION_SEND = "com.hckthn.watchoutserver.NOTFICATION_SEND";
	public static final String TAG = "NotificationPusher";
	
	BroadcastReceiver br;
	
	PackageManager pm;
	
	@Override
	public void onCreate(){
		super.onCreate();
		log("On create");
		pm = this.getPackageManager();
		
		br = new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent) {
				if(intent.getAction().equals(WatchLink.DISMISS_INTENT)){
					log("Got dismiss intent; trying to dismiss");
					String pkg = intent.getStringExtra("pkg");
					String tag = intent.getStringExtra("tag");
					int id = Integer.parseInt(intent.getStringExtra("id"));
					log("Pkg: " + pkg + " tag: " + tag + " id: " + id);
					if(tag.equals("null")) tag = null;
					NotificationPusher.this.cancelNotification(pkg, tag, id);
				}
			}			
		};
		IntentFilter dismissFilter = new IntentFilter();
		dismissFilter.addAction(WatchLink.DISMISS_INTENT);
		LocalBroadcastManager.getInstance(this).registerReceiver(br, dismissFilter);
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
		log("On destroy");
	}

	@Override
	public void onNotificationPosted(StatusBarNotification sbn) {
		log("Got notification");
		if(sbn.isOngoing()) return; //Don't want to capture these 
		
		String title = sbn.getNotification().extras.getString(Notification.EXTRA_TITLE);
		String text = sbn.getNotification().extras.getString(Notification.EXTRA_INFO_TEXT);
		try {
			title.replace("|", ":");
			text.replace("|", ":");
			if(title.isEmpty()){
				try {
					title = (String) pm.getApplicationLabel(pm.getPackageInfo(sbn.getPackageName(), 0).applicationInfo);
				} catch (Exception e) {
					title = sbn.getPackageName();
				}
			}
			
			if(text.isEmpty()){
				text = "Notification posted";
			}
		} catch (Exception e){
			log("Processing failed");
		}
		
		String toSend = "NOTIFICATION|" + title + "|" + text + "|" + sbn.getPackageName() + "|" + sbn.getTag() + "|" + sbn.getId();
		Intent i = new Intent();
		i.setAction(WatchLink.NOTIFICATION_INTENT);
		i.putExtra("toSend", toSend);
		LocalBroadcastManager.getInstance(this).sendBroadcast(i);
		log("Sent notification");
	}

	@Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
		log("Removed notification");
		log(sbn.getPackageName());
	}
	
	private void log(String text){
		Log.d(TAG, text);
	}
}
