package com.smokybob.NoteToGDocs;

import java.io.IOException;

import com.smokybob.NoteToGDocs.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.*;

import android.util.Log;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.accounts.GoogleAccountManager;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

public class SettingsActivity extends SherlockPreferenceActivity {

	public static final String[] ACCOUNT_TYPE = new String[] {GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE};

	private static final int CHOOSE_ACCOUNT = 0;

	private static final int STATE_INITIAL = 0;
	private static final int STATE_CHOOSING_ACCOUNT = 1;
	private static final int STATE_DONE = 3;

	private GoogleAccountManager mAccountManager;
	private GoogleAccountCredential credential;
	private Preference mAccountPreference;
	private Preference mNoteFolderPreference;
	private SharedPreferences mPreferences;
	private int mState;
	private AlertDialog.Builder alDialogBuild =null;
	private AlertDialog alDialog =null;
	private FileList fList =null;
	private Drive service=null;
	/**
	 * Populate the activity with the top-level headers.
	 */

	@Override
	public Intent getIntent() {
		final Intent modIntent = new Intent(super.getIntent());
		//	    modIntent.putExtra(EXTRA_SHOW_FRAGMENT, PreferencesFragment.class.getName());
		modIntent.putExtra(EXTRA_NO_HEADERS, true);
		return modIntent;
	}	    

	private Context getActivity(){
		return this;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mState = STATE_INITIAL;

		mAccountManager = new GoogleAccountManager(getActivity());
		//		mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mPreferences =getSharedPreferences(getString( R.string.pref_file_key), 0);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.layout.preferences_screen);

		//Check Credential
		checkCredential();
		// Initialize the preferred account setting.
		mAccountPreference = this.findPreference("selected_account_preference");
		//Prepare the AlertDialog Builder for future use
		alDialogBuild = new AlertDialog.Builder(this);
		mAccountPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				chooseAccount();
				return true;
			}
		});

		mNoteFolderPreference = this.findPreference("note_folder_preference");

		String folderSummary = mNoteFolderPreference.getSummary().toString();
		folderSummary=folderSummary.replace("%s", getFolderName());
		mNoteFolderPreference.setSummary(folderSummary);

		mNoteFolderPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				// TODO Auto-generated method stub
				loadFolderNames();
				return false;
			}
		});

	}

	@Override
	public void onResume() {
		super.onResume();
		Account preferenceAccount = getPreferenceAccount();

		if (preferenceAccount != null) {
			mAccountPreference.setSummary(preferenceAccount.name);
			mState = STATE_DONE;
		} else {
			if (mState == STATE_INITIAL) {
				chooseAccount();
			}
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case CHOOSE_ACCOUNT:
			if (data != null) {

				Log.e(
						"Preferences",
						"SELECTED ACCOUNT WITH EXTRA: "
								+ data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME));
				Bundle b = data.getExtras();

				String accountName = b.getString(AccountManager.KEY_ACCOUNT_NAME);

				Log.d("Preferences", "Selected account: " + accountName);
				if (accountName != null && accountName.length() > 0) {
					Account account = mAccountManager.getAccountByName(accountName);
					setAccount(account);
					service = getDriveService(credential);
					//Changed Account Set the folder to Root
					setFolder("root", "root");
				}
			} else {
				mState = STATE_INITIAL;
			}
			break;
		}
	}

	/**
	 * Start an intent to prompt the user to choose the account to use with the
	 * app.
	 */
	private void chooseAccount() {
		mState = STATE_CHOOSING_ACCOUNT;
		credential = GoogleAccountCredential.usingOAuth2(this, DriveScopes.DRIVE);
		startActivityForResult(credential.newChooseAccountIntent(), CHOOSE_ACCOUNT);
	}

	/**
	 * Set the new account to use with the app.
	 * 
	 * @param account New account to use.
	 */
	private void setAccount(Account account) {
		if (account != null) {
			SharedPreferences.Editor editor =mPreferences.edit();
			editor.putString("accountName", account.name);
			editor.commit();

			mAccountPreference.setSummary(account.name);

			//Set the selected credential in the Credential manager
			credential.setSelectedAccountName(account.name);

			//Load Folder list
			loadFolderNames();
			mState = STATE_DONE;
		}
	}


	/**
	 * Get the currently preferred account to use with the app.
	 * 
	 * @return The preferred account if available, {@code null} otherwise.
	 */
	private Account getPreferenceAccount() {
		return mAccountManager.getAccountByName(mPreferences.getString("accountName",""));
	}

	private class LoadFolderTask extends AsyncTask<String , Integer, FileList> {

		@Override
		protected FileList doInBackground(String... currentFolder) {


			try {
				//Filter for the child folder
				String qStr = "'"+currentFolder[0]+"' in parents and mimeType = 'application/vnd.google-apps.folder'";

				//Get the list of Folders
				fList=service.files().list().setQ(qStr).execute();

			} catch (UserRecoverableAuthIOException e) {

				startActivityForResult(e.getIntent(), CHOOSE_ACCOUNT);
			} catch (IOException e) {
				Log.e("Smokybob", e.getStackTrace().toString());
				//FIXME: Show a toast for error 
			}
			return fList;
		}

		@Override
		protected void onPostExecute(FileList result) {
			super.onPostExecute(result);

			//Dismiss the notification if exists
			if (alDialog!=null){
				if(alDialog.isShowing()){

					try{
						alDialog.dismiss();
					} catch(IllegalArgumentException ex){
						//This exception is thrown when the back button is pressed so the dialog is not really there
					}
				}
			}

			//Create the list of folders
			CharSequence[] items = new CharSequence[result.getItems().size()];
			int i =0;

			//TODO: Sort the folder by name
			//Add the folder names to the Array
			for (File fl:result.getItems()){
				items[i]=fl.getTitle();
				i++;
			}

			//Create the new Dialog
			alDialogBuild.setItems(items, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {

					File folder= fList.getItems().get(which);

					// Call the Set Folder to store info in the preferences
					setFolder(folder.getTitle(),folder.getId());
				}
			});
			alDialog=alDialogBuild.show();

		}

	}


	private void loadFolderNames(){

		String folderId=mPreferences.getString("note_folder_id","root");
		//Get the folder list for the selected account 
		new LoadFolderTask().execute(folderId);

	}

	private String getFolderName(){
		return mPreferences.getString("note_folder_name","root");
	}

	private void setFolder(String folderName,String folderId){


		SharedPreferences.Editor editor =mPreferences.edit();

		editor.putString("note_folder_name", folderName);
		editor.putString("note_folder_id", folderId);
		editor.commit();


		mNoteFolderPreference.setSummary("Notes Stored in "+folderName+" Folder");

	}
	private Drive getDriveService(GoogleAccountCredential credential) {
		return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential)
		.build();
	}

	private void checkCredential() {
		//Try to get Account Name from previous selection
		String accountName = mPreferences.getString("accountName", "");
		credential = GoogleAccountCredential.usingOAuth2(this, DriveScopes.DRIVE);
		if (accountName.length()>0) {
			credential.setSelectedAccountName(accountName);
			service = getDriveService(credential);
		}
		else
		{
			//Ask the user for credentials
			startActivityForResult(credential.newChooseAccountIntent(), CHOOSE_ACCOUNT);
		}
	}
}
