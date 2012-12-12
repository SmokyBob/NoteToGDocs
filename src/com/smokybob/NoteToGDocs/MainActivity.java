package com.smokybob.NoteToGDocs;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import android.net.Uri;
import android.os.Bundle;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class MainActivity extends Activity {
	
	static final int REQUEST_ACCOUNT_PICKER = 1;
	static final int REQUEST_AUTHORIZATION = 2;
	static final int CAPTURE_IMAGE = 3;
	
	private static Uri fileUri;
	private static Drive service;
	private GoogleAccountCredential credential;
	
	
	

	private void checkCredential() {
		GoogleAccountCredential _credential;
		if (credential!=null) {
			_credential=credential;
			setContentView(R.layout.activity_main);
			}
		else
		{
			//Ask the user for credentials
			_credential = GoogleAccountCredential.usingOAuth2(this, DriveScopes.DRIVE);
		    startActivityForResult(_credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
			
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//Check the Credentials
		checkCredential();
		//setContentView(R.layout.activity_main);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected (MenuItem item){
		switch (item.getItemId()) {
        case R.id.done:
            UploadNote();
            return true;
        case R.id.undo:
            
        	ClearNote();
        	
            return true;
        case R.id.menu_settings:
        	//Open Settings Activity
        	Intent settings = new Intent(this,SettingsActivity.class);
        	startActivity(settings);
        default:
            return super.onOptionsItemSelected(item);
		}
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
	    case CAPTURE_IMAGE:
	      if (resultCode == Activity.RESULT_OK) {
	    	  UploadNote();
	      }
	    }
	  }
	
	private void ClearNote(){
		//MSO - 20121127 - Clean the text
    	EditText editText1 = (EditText) findViewById(R.id.editText1);
    	editText1.setText("");
	}
	
	private void UploadNote(){
		//TODO - MSO - 20121127 - Lanciare il salvataggio async
		
	}
	
	private Drive getDriveService(GoogleAccountCredential credential) {
	    return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
	        .build();
	  }
	
}
