package com.smokybob.NoteToGDocs;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.smokybob.NoteToGDocs.R;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class MainActivity extends Activity {

	static final int REQUEST_ACCOUNT_PICKER = 1;
	static final int REQUEST_AUTHORIZATION = 2;

	private static Drive service;
	private GoogleAccountCredential credential;
	private SharedPreferences settings;
	public static final String PREFS_NAME = "MyPrefsFile";
	/** text/plain MIME type. */
	private static final String SOURCE_MIME = "text/plain";
	private boolean isSaved=true;
	private boolean bFromBack=false;
	private NotificationManager mNotifyMgr;

	ProgressDialog pd;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Restore preferences
		settings = getSharedPreferences(PREFS_NAME, 0);
		//Check the Credentials
		checkCredential();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected (MenuItem item){
		boolean toRet =false;
		switch (item.getItemId()) {
		case R.id.done:
			if (!isSaved)
				UploadNote();
			toRet= true;
			break;
		case R.id.undo:

			ClearNote();

			toRet= true;
			break;
		case R.id.menu_settings:
			//FIXME: Enable Account Selection and other configurations
			// https://code.google.com/p/google-drive-sdk-samples/source/browse/android/src/com/example/android/notepad/Preferences.java
			//Open Settings Activity
			Intent settings = new Intent(this,SettingsActivity.class);
			startActivity(settings);
			toRet=true;
			//        default:
			//            return super.onOptionsItemSelected(item);
			break;
		}
		return toRet;
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		switch (requestCode) {
		case REQUEST_ACCOUNT_PICKER:
			if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
				String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
				if (accountName != null) {
					credential.setSelectedAccountName(accountName);
					service = getDriveService(credential);
					//Save selected account
					SharedPreferences.Editor editor = settings.edit();
					editor.putString("accountName", accountName);
					// Commit the edits!
					editor.commit();
					setContentView(R.layout.activity_main);
					isSaved=false;
				}
			}
			break;
		case REQUEST_AUTHORIZATION:
			if (resultCode == Activity.RESULT_OK) {
				isSaved=false;
				UploadNote();
			} else {
				isSaved=true;
				startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
			}
			break;
		}
	}


	@Override
	public void onBackPressed() {
		super.onBackPressed();
		bFromBack=true;
		try{
			//Check I've already saved the data
			if (!isSaved)
			{
				UploadNote();
			}
		}catch (Exception ex){
			Log.e("Smokybob", ex.getStackTrace().toString());
		}
	}

	private void ClearNote(){
		//MSO - 20121127 - Clean the text
		EditText editText1 = (EditText) findViewById(R.id.editText1);
		editText1.setText("");
	}
	private void UploadNote()
	{
		//Show the progress Dialog
		pd = ProgressDialog.show(MainActivity.this,"Note Upload In Progress","Please Wait...",true,false,null);
		EditText editText1 = (EditText) findViewById(R.id.editText1);
		new UploadNoteTask().execute(editText1.getText().toString());
	}

	private Drive getDriveService(GoogleAccountCredential credential) {
		return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
		.build();
	}

	private void createNotification(String title, String messageText, String fileId) {
		// Gets an instance of the NotificationManager service
		if (mNotifyMgr==null){
			mNotifyMgr = 
					(NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		}

		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(this)
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentTitle(title)
		.setContentText(messageText);

		if (fileId.length()>0){

			Intent resultIntent = new Intent(Intent.ACTION_VIEW); 
			Uri uri = Uri.parse("https://docs.google.com/document/d/"+fileId); 
			resultIntent.setData(uri);

			// Because clicking the notification opens a new ("special") activity, there's
			// no need to create an artificial back stack.
			PendingIntent resultPendingIntent =
					PendingIntent.getActivity(
							this,
							0,
							resultIntent,
							PendingIntent.FLAG_UPDATE_CURRENT
							);

			mBuilder.setDefaults(Notification.DEFAULT_ALL);		
			mBuilder.setAutoCancel(true);
			mBuilder.setContentIntent(resultPendingIntent);
		}

		// get the last notification ID from the shared preferences

		int iNotificationCnt;

		iNotificationCnt=settings.getInt("notificationID", 1);

		// Builds the notification and issues it.
		mNotifyMgr.notify(iNotificationCnt, mBuilder.build());

		//Increase the notificationID
		iNotificationCnt++;

		//If over 1000 Reset to 1
		if (iNotificationCnt>1000){
			iNotificationCnt=1;
		}

		//Save the notification ID
		SharedPreferences.Editor editor = settings.edit();
		editor.putInt("notificationID", iNotificationCnt);
		// Commit the edits!
		editor.commit();

	}

	private String getFileName(){
		//TODO - From config Get the structure
		String toRet="";
		//Default YYYYMMDD_HHmmss_Note
		DateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");

		// Get the date today using Calendar object.
		Date today = Calendar.getInstance().getTime();        
		// Using DateFormat format method we can create a string 
		// representation of a date with the defined format.
		toRet = df.format(today)+"_Note";

		return toRet;
	}

	private void checkCredential() {
		//Try to get Account Name from previous selection
		String accountName = settings.getString("accountName", "");
		credential = GoogleAccountCredential.usingOAuth2(this, DriveScopes.DRIVE);
		if (accountName.length()>0) {
			credential.setSelectedAccountName(accountName);
			service = getDriveService(credential);
			setContentView(R.layout.activity_main);
			isSaved=false;
		}
		else
		{
			//Ask the user for credentials
			startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
		}
	}

	private class UploadNoteTask extends AsyncTask<String , Integer, Boolean> {

		@Override
		protected Boolean doInBackground(String... content) {
			File newFile = new File();

			//Generate Title
			String fileTitle = getFileName();
			//Set File Info and text
			newFile.setTitle(fileTitle);
			newFile.setMimeType(SOURCE_MIME);


			try {
				File insertedFile = null;
				//Upload the file
				if (content != null && content[0].trim().length() > 0) {
					Insert fileRequest = service.files()
							.insert(newFile, ByteArrayContent.fromString(SOURCE_MIME, content[0]));
					//Auto Convert to Google Docs
					fileRequest.setConvert(true);
					insertedFile = fileRequest.execute();
					//TODO: Use string resources for different languages support
					createNotification(fileTitle, "File Uploaded", insertedFile.getId());

					isSaved=true;
				}

			} catch (UserRecoverableAuthIOException e) {
				startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
			} catch (IOException e) {
				isSaved=false;
				Log.e("Smokybob", e.getStackTrace().toString());
				createNotification(fileTitle, "File Upload error", "");
			}
			return isSaved;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			//Dismiss the notification if exists
			if (pd!=null && pd.isShowing()){
				try{
					pd.dismiss();
				} catch(IllegalArgumentException ex){
					//This exception is thrown when the back button is pressed so the dialog is not really there
				}
			}
			if (result && !bFromBack)
				//Force the back button call to "close" the app
				onBackPressed();

		}

	}


}

