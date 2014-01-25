package com.hckthn.watchoutserver;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
	public static final String TAG = "MainActivity";
	public static final int CONTACT_REQUEST = 5000;
	
	public static final String MESSAGE_PREF = "msg_pref";
	public static final String CONTACT_BASE = "contact_names";
	public static final String CONTACT_NUMBER_BASE = "contact_numbers";
	
	SharedPreferences pref;
	
	Button startService;
	EditText messageBox;
	Button addContact;
	LinearLayout existingContacts;
	
	List<String> names;
	List<String> numbers;
	
	boolean isStarted = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		names = new ArrayList<String>();
		numbers = new ArrayList<String>();
		
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
		
		messageBox = (EditText) this.findViewById(R.id.alertMessage);
		
		addContact = (Button) this.findViewById(R.id.addContact);
		addContact.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Intent contactPicker = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
				MainActivity.this.startActivityForResult(contactPicker, CONTACT_REQUEST);
			}
		});
		
		existingContacts = (LinearLayout) this.findViewById(R.id.existingContacts);
		
		//Restore rows
		String allNames = pref.getString(CONTACT_BASE, "");
		String allNumbers = pref.getString(CONTACT_NUMBER_BASE, "");
		if(allNames.length() > 0){
			if(allNames.indexOf(",") != 0){
				String[] names = allNames.split(",");
				for(int i = 0; i < names.length; i++){
					this.names.add(names[i]);
				}
			} else {
				names.add(allNames);
			}
		}
		if(allNumbers.length() > 0){
			if(allNumbers.indexOf(",") != 0){
				String[] numbers = allNumbers.split(",");
				for(int i = 0; i < numbers.length; i++){
					this.numbers.add(numbers[i]);
				}
			} else {
				names.add(allNumbers);
			}
		}
		for(int i = 0; i < names.size(); i++){
			constructRow(names.get(i), numbers.get(i));
		}
		
		//Restore edittext
		messageBox.setText(pref.getString(MESSAGE_PREF, ""));			
	}
	
	@Override
	protected void onActivityResult(int reqCode, int result, Intent data){
		switch(reqCode){
		case CONTACT_REQUEST:
			if(result == Activity.RESULT_OK){
				Uri contact = data.getData();
				Cursor cursor = this.managedQuery(contact, null, null, null, null);
				if(cursor.moveToFirst()){
					int nameColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
					int phoneColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
					try {
						String name = cursor.getString(nameColumn);
						String number = cursor.getString(phoneColumn);						
						names.add(name);
						log("Names size: " + names.size());
						numbers.add(number);
						constructRow(name, number);
					} catch (Exception e){	
					}
				}
			}
			break;
		}
		super.onActivityResult(reqCode, result, data);
	}
	
	private void constructRow(final String name, final String number){
		View v = this.getLayoutInflater().inflate(R.layout.contact_row, null);
		((TextView)v.findViewById(R.id.contactName)).setText(name + "/" + number);
		v.findViewById(R.id.contactRemove).setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				names.remove(name);
				numbers.remove(number);
				((ViewGroup)((ViewGroup)v.getParent()).getParent()).removeView((View) v.getParent());
			}
		});
		existingContacts.addView(v);
	}
	
	@Override
	public void onDestroy(){
		super.onDestroy();
	}
	
	@Override
	public void onPause(){
		super.onPause();
		
		//Save contact rows
		String newName = "";
		String newNumber = "";
		if(names.size() == 0){
			log("Names empty");
			pref.edit().putString(CONTACT_BASE, "").commit();
			pref.edit().putString(CONTACT_NUMBER_BASE, "").commit();
		} else {
			log("Names contain stuff");
			for(int i = 0; i < names.size(); i++){
				newName += names.get(i) + ",";
				newNumber += numbers.get(i) + ",";
			}
			newName = newName.substring(0, newName.length()-1);
			log(newName);
			newNumber = newNumber.substring(0, newNumber.length()-1);
			log(newNumber);
			pref.edit().putString(CONTACT_BASE, newName).commit();
			pref.edit().putString(CONTACT_NUMBER_BASE, newNumber).commit();
		}
		
		//Put message
		pref.edit().putString(MESSAGE_PREF, messageBox.getText().toString()).commit();
	}
	
	@Override
	public void onResume(){		
		super.onResume();
	}

	private void log(String text){
		Log.d(TAG, text);
	}
}
