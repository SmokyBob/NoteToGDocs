package com.smokybob.NoteToGDocs;

import java.io.IOException;
import java.util.Arrays;

import com.smokybob.NoteToGDocs.R;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
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
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;

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
import com.google.api.services.drive.model.ParentReference;

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
	private File curDriveFolder;
	private int selectedItem =-1;
	private ProgressDialog pd;
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
		//Get the Folder Preference Summary and replace text with the right Folder Name
		String folderSummary = mNoteFolderPreference.getSummary().toString();
		folderSummary=folderSummary.replace("%s", getFolderName());
		mNoteFolderPreference.setSummary(folderSummary);

		mNoteFolderPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {

			@Override
			public boolean onPreferenceClick(Preference preference) {
				// Load the Drive folders
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
					//Account selected, Store the data and initialize objects
					Account account = mAccountManager.getAccountByName(accountName);
					setAccount(account);
					service = getDriveService(credential);
					//Changed Account Set the folder to Root
					setFolder();
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

			selectedItem=-1;
			try {
				//Filter for the child folder
				String qStr = "'"+currentFolder[0]+"' in parents and mimeType = 'application/vnd.google-apps.folder'";

				//Get the list of Folders
				fList=service.files().list().setQ(qStr).execute();

				if (currentFolder[0]!="root"){
					//Add the dummy folder to back
					File flDummy = new File();
					flDummy.setId("..");
					flDummy.setTitle("..");

					flDummy.setParents(curDriveFolder.getParents());

					fList.getItems().add(0, flDummy);

				}


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

			pd.dismiss();
			//Dismiss the notification if exists
			if (alDialog!=null && alDialog.isShowing()){

				try{
					alDialog.dismiss();
				} catch(IllegalArgumentException ex){
					//This exception is thrown when the back button is pressed so the dialog is not really there
				}
			}


			//TODO: If there are no folder show a dialog
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
			alDialogBuild.setTitle("Folder Selection");
			alDialogBuild.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {


					File folder= fList.getItems().get(which);
					if (folder.getId()==".."){
						//Back Item Selected
						//curDriveFolder
						//Reload the List with parent folder child items
						ParentReference parent=folder.getParents().get(0);
						selectedItem=-1;
						if (parent.getIsRoot()){
							pd = ProgressDialog.show(getActivity(),"Folder List Loading","Please Wait...",true,false,null);
							new LoadFolderTask().execute("root");
						}
						else{
							pd = ProgressDialog.show(getActivity(),"Folder List Loading","Please Wait...",true,false,null);
							new LoadFolderTask().execute(parent.getId());
						}
					}else{
						//Check if it's the first selection or a second one to enter in subfolder
						if (which==selectedItem){
							//Second click to enter sub folder list
							//Reload the List with sub folders
							pd = ProgressDialog.show(getActivity(),"Folder List Loading","Please Wait...",true,false,null);
							new LoadFolderTask().execute(folder.getId());
						}else{
							//Store Selection
							curDriveFolder=folder;
							selectedItem=which;
						}
					}
				}
			});

			alDialogBuild.setCancelable(true);
			alDialogBuild.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					//Nothing to Do

				}
			});
			alDialogBuild.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {


					// Call the Set Folder to store info in the preferences
					setFolder();
					alDialog.dismiss();

				}
			});
			alDialog=alDialogBuild.show();

		}

	}


	private void loadFolderNames(){

		String folderId=mPreferences.getString("note_folder_parent_id","root");
		//Get the folder list for the selected account 
		pd = ProgressDialog.show(this,"Folder List Loading","Please Wait...",true,false,null);
		new LoadFolderTask().execute(folderId);

	}

	private String getFolderName(){
		if (curDriveFolder==null) {
			//Set the current folder instance with data from the settings
			curDriveFolder=new File();
			curDriveFolder.setId(mPreferences.getString("note_folder_id","root"));
			curDriveFolder.setTitle(mPreferences.getString("note_folder_name","root"));
			//Set the parent folder and his 
			curDriveFolder.setParents(Arrays.asList(new ParentReference()
			.setId(mPreferences.getString("note_folder_parent_id","root"))
			.setIsRoot(mPreferences.getBoolean("note_folder_parent_isRoot", true)))
					);
		}
		return curDriveFolder.getTitle();
	}

	private void setFolder(){


		SharedPreferences.Editor editor =mPreferences.edit();
		//Store the preferences
		editor.putString("note_folder_name", curDriveFolder.getTitle());
		editor.putString("note_folder_id", curDriveFolder.getId());

		if (curDriveFolder.getParents().get(0).getIsRoot()){
			//If the parent folder is root it's simplier to store root as the id instead of the real one
			editor.putString("note_folder_parent_id", "root");
			editor.putBoolean("note_folder_parent_isRoot",true);
		}else{
			editor.putString("note_folder_parent_id", curDriveFolder.getParents().get(0).getId());
			editor.putBoolean("note_folder_parent_isRoot", false);
		}


		editor.commit();

		//Update the summary
		mNoteFolderPreference.setSummary("Notes Stored in "+curDriveFolder.getTitle()+" Folder");

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
