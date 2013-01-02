package com.smokybob.NoteToGDocs;

import java.io.IOException;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.smokybob.NoteToGDocs.R;
import com.smokybob.NoteToGDocs.R.id;
import com.smokybob.NoteToGDocs.R.layout;
import com.smokybob.NoteToGDocs.R.menu;

import android.net.Uri;
import android.os.Bundle;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class MainActivity extends Activity {

	static final int REQUEST_ACCOUNT_PICKER = 1;
	static final int REQUEST_AUTHORIZATION = 2;

	private static Uri fileUri;
	private static Drive service;
	private GoogleAccountCredential credential;
	private SharedPreferences settings;
	public static final String PREFS_NAME = "MyPrefsFile";
	/** text/plain MIME type. */
	private static final String TEXT_PLAIN = "text/plain";

	private void checkCredential() {
		//Try to get Account Name from previous selection
		String accountName = settings.getString("accountName", "");
		credential = GoogleAccountCredential.usingOAuth2(this, DriveScopes.DRIVE);
		if (accountName.length()>0) {
			credential.setSelectedAccountName(accountName);
			service = getDriveService(credential);
			setContentView(R.layout.activity_main);
		}
		else
		{
			//Ask the user for credentials
			startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
		}
	}

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
			//        	Intent settings = new Intent(this,SettingsActivity.class);
			//        	startActivity(settings);
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
				}
			}
			break;
		case REQUEST_AUTHORIZATION:
			if (resultCode == Activity.RESULT_OK) {
				UploadNote();
			} else {
				startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
			}
			break;
			//	    case CAPTURE_IMAGE:
			//	      if (resultCode == Activity.RESULT_OK) {
			//	    	  UploadNote();
			//	      }
		}
	}

	private void ClearNote(){
		//MSO - 20121127 - Clean the text
		EditText editText1 = (EditText) findViewById(R.id.editText1);
		editText1.setText("");
	}

	private void UploadNote(){
		//TODO - MSO - 20121127 - Lanciare il salvataggio async
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				//FIXME - Async Save, not Sync
				File newFile = new File();

				//Generate Title
				//TODO - From config Get the structure
				String fileTitle ="Test_noteGDocs";
				newFile.setTitle(fileTitle);
				newFile.setMimeType(TEXT_PLAIN);
				EditText editText1 = (EditText) findViewById(R.id.editText1);
				String content = editText1.getText().toString();

				try {
					File insertedFile = null;

					if (content != null && content.length() > 0) {
						service = getDriveService(credential);
						insertedFile = service.files()
								.insert(newFile, ByteArrayContent.fromString(TEXT_PLAIN, content))
								.execute();
					} else {
						insertedFile = service.files().insert(newFile).execute();
					}
					
					//TODO: Use string resources for different languages support
					createNotification(fileTitle, "File Uploaded correctly", insertedFile.getId());
				} catch (UserRecoverableAuthIOException e) {
					startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
				} catch (IOException e) {
					e.printStackTrace();
					createNotification(fileTitle, "File Upload error", "");
				}
			}
		});
		t.start();

	}

	private Drive getDriveService(GoogleAccountCredential credential) {
		return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
		.build();
	}

	public void createNotification(String title, String messageText, String fileId) {
		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(this)
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentTitle(title)
		.setContentText(messageText);
		
		if (fileId.length()>0){
			Intent resultIntent = new Intent( "com.google.android.apps.drive.DRIVE_OPEN");

			resultIntent.setType("text/plain");
			resultIntent.putExtra("EXTRA_FILE_ID", fileId);

			// Because clicking the notification opens a new ("special") activity, there's
			// no need to create an artificial back stack.
			PendingIntent resultPendingIntent =
			    PendingIntent.getActivity(
			    this,
			    0,
			    resultIntent,
			    PendingIntent.FLAG_UPDATE_CURRENT
			);
			
			mBuilder.setContentIntent(resultPendingIntent);
		}
		
		// Sets an ID for the notification
		int mNotificationId = 001;
		// Gets an instance of the NotificationManager service
		NotificationManager mNotifyMgr = 
		        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		// Builds the notification and issues it.
		mNotifyMgr.notify(mNotificationId, mBuilder.build());
	}

}
