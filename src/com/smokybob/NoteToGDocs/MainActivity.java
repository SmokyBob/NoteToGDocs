package com.smokybob.NoteToGDocs;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.SortedSet;
import java.util.TreeSet;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files.Insert;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.smokybob.NoteToGDocs.R;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {

	static final int REQUEST_ACCOUNT_PICKER = 1;
	static final int REQUEST_AUTHORIZATION = 2;

	private static Drive service;
	private GoogleAccountCredential credential;
	private SharedPreferences settings;

	/** text/plain MIME type. */
	private static final String SOURCE_MIME = "text/plain";
	private boolean isSaved=true;
	private boolean bFromBack=false;
	private NotificationManager mNotifyMgr;
	private String folderId;
	private ArrayList<File> sortedNoteList=null;
	private Boolean firstLineTime;

	private ProgressDialog pd;
	private AlertDialog.Builder alDialogBuild =null;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

	}

	@Override
	protected void onStart() {
		super.onStart();
		
		//Load/reload preferences n
		// Restore preferences
		settings = getSharedPreferences(getString( R.string.pref_file_key), 0);
		//Get the folder Id
		folderId= settings.getString("note_folder_id","root");

		//Get if the first line is to be set with time
		firstLineTime=settings.getBoolean("first_line_time", false);
		if (credential==null){


			//Check the Credentials
			checkCredential();

			//If the Credential is null than it's the first start and I need to check 
			//if I Need the user want the date and time as first line of the note
			if (firstLineTime){
				EditText editText1 = (EditText) findViewById(R.id.editText1);
				String tmp = getFileName().replace("_Note", "");
				tmp = tmp.replace("_", " ");
				editText1.setText(tmp);
			}

		}


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
			//Open Settings Activity
			Intent settings = new Intent(this,SettingsActivity.class);
			startActivity(settings);
			toRet=true;
			//        default:
			//            return super.onOptionsItemSelected(item);
			break;
		case R.id.previous_notes:
			if (Util.CheckNetwork(this)){
				//Show the progress Dialog
				pd = ProgressDialog.show(MainActivity.this,"Note List Loading","Please Wait...",true,false,null);
				//Load the Note List and show the file selector
				new NotesListTask().execute(folderId);
			}else
			{
				//Show a Toast for no network connection
				Toast.makeText(this, getString(R.string.no_network),Toast.LENGTH_SHORT).show();
		        
			}
			
			break;
		case R.id.feedback:
			//Start the intent to write an Email
			String emailAddress = "notetogdocs@gmail.com";
			String emailSubject = "Feedback/Feature Request";

			String emailAddressList[] = {emailAddress};

			Intent intent = new Intent(Intent.ACTION_SEND); 
			intent.setType("plain/text");
			intent.putExtra(Intent.EXTRA_EMAIL, emailAddressList);  
			intent.putExtra(Intent.EXTRA_SUBJECT, emailSubject); 
			startActivity(Intent.createChooser(intent, "Choose the email app to use:"));
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

	private Context getActivity(){
		return this;
	}

	private void ClearNote(){
		//MSO - 20121127 - Clean the text
		EditText editText1 = (EditText) findViewById(R.id.editText1);
		editText1.setText("");
	}
	private void UploadNote()
	{
		//TODO: For Future Release - Save the note and let the Service do the upload
		//Check if network is connected otherwise show a toast
		if (Util.CheckNetwork(this)){
			if (!bFromBack){
				//Show the progress Dialog
				pd = ProgressDialog.show(MainActivity.this,"Note Upload In Progress","Please Wait...",true,false,null);
			}
			EditText editText1 = (EditText) findViewById(R.id.editText1);
			new UploadNoteTask().execute(editText1.getText().toString());
		}else
		{
			//Show a Toast for no network connection
			Toast.makeText(this, getString(R.string.no_network),Toast.LENGTH_SHORT).show();
	        
		}
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

		//Notification Build Helper
		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(this)
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentTitle(title)
		.setContentText(messageText);

		//Check if file id exist = the file was created successfully on Drive
		if (fileId.length()>0){

			//Create a new intent with the URL of the document; Android will ask the proper App to handle it
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
			//Notify with all means possible (sound/vibration/light)
			mBuilder.setDefaults(Notification.DEFAULT_ALL);		
			//Auto clear the notification after it's selected
			mBuilder.setAutoCancel(true);
			//Specify the action on tap over the notification
			mBuilder.setContentIntent(resultPendingIntent);
		}

		// get the last notification ID from the shared preferences
		int iNotificationCnt;

		iNotificationCnt=settings.getInt("notificationID", 1);

		// Builds the notification and issues it.
		mNotifyMgr.notify(iNotificationCnt, mBuilder.build());

		//Increase the notificationID so that the next notification will NOT Update the current
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

	@SuppressLint("SimpleDateFormat")
	private String getFileName(){
		//From config Get the structure of the file name
		String titleFormat=settings.getString("note_title_format", getString(R.string.title_default_value));
		String toRet="";
		//Default YYYYMMDD_HHmmss_Note
		DateFormat df = new SimpleDateFormat(titleFormat);

		// Get the date today using Calendar object.
		Date today = Calendar.getInstance().getTime();        
		// Using DateFormat format method we can create a string 
		// representation of a date with the defined format.
		toRet = df.format(today)+"_Note";

		return toRet;
	}

	private void checkCredential() {
		//MSO - 20130120 - Reload credential and redraw the Activity only on the first start or if the activity was destroyed
		//Try to get Account Name from previous selection
		String accountName = settings.getString("accountName", "");
		//Get an Instance of the credential manager
		credential = GoogleAccountCredential.usingOAuth2(this, DriveScopes.DRIVE);
		if (accountName.length()>0) {
			//Set the account that will be useb by the app
			credential.setSelectedAccountName(accountName);
			//Get a Drive Service Instance with the right credential
			service = getDriveService(credential);
			//Show the UI
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
			if (folderId!="root"){
				//Set the parent Folder
				newFile.setParents(Arrays.asList(new ParentReference().setId(folderId)));
			}


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

	private class NotesListTask extends AsyncTask<String , Integer, ArrayList<File>> {
		private int selected;
		//Comparator for FileList Ordering
		public class EComparator implements Comparator<File> {

			public int compare(File o1, File o2) {
				return o1.getTitle().compareTo(o2.getTitle());
			}

		}

		@Override
		protected ArrayList<File> doInBackground(String... currentFolder) {
			ArrayList<File> toRet=new ArrayList<File>();
			try {
				//Filter the folder for documents Only and not trashed
				String qStr = "'"+currentFolder[0]+"' in parents and mimeType = 'application/vnd.google-apps.document' and trashed=false";

				//Get the list of Folders
				FileList fList=service.files().list().setQ(qStr).execute();

				//Create the TreeSet for Ordering
				SortedSet<File> sorter = new TreeSet<File>(new EComparator());

				//Add the Files to the Orderer
				for(File fl:fList.getItems()){
					sorter.add(fl);
				}
				//Save the Array with the ordered Elements
				toRet.addAll(sorter);


			} catch (UserRecoverableAuthIOException e) {
				startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
			} catch (IOException e) {
				Log.e("Smokybob", e.getStackTrace().toString());
				Toast.makeText(getActivity(), "Error in Loading the Documents, Try Again", Toast.LENGTH_SHORT).show();
			}
			return toRet;
		}

		@Override
		protected void onPostExecute(ArrayList<File> result) {
			super.onPostExecute(result);
			sortedNoteList = result;
			pd.dismiss();

			createNoteDialog();

		}

		private void deleteNote(File note){
			new AsyncTask<File, Integer, Boolean>(){

				@Override
				protected Boolean doInBackground(File... params) {
					Boolean toRet=false;
					try {
						//Trash the File on Drive
						service.files().trash(params[0].getId()).execute();
						//Remove the File From the Sorted List
						sortedNoteList.remove(params[0]);

						toRet=true;

					} catch (UserRecoverableAuthIOException e) {
						startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
					} catch (IOException e) {
						Log.e("Smokybob", e.getStackTrace().toString());
						toRet=false;
					}
					return toRet;
				}

				@Override
				protected void onPostExecute(Boolean result) {
					super.onPostExecute(result);

					if(result){
						Toast.makeText(getActivity(), "File Trashed", Toast.LENGTH_SHORT).show();
					}else{
						Toast.makeText(getActivity(), "File NOT Trashed", Toast.LENGTH_SHORT).show();
					}

					pd.dismiss();
					createNoteDialog();
				}

			}.execute(note);


		}

		private void createNoteDialog(){
			//If there are no file show a dialog
			if (sortedNoteList==null || sortedNoteList.isEmpty()){

				//Create and Show the Dialog
				new AlertDialog.Builder(getActivity())
				.setTitle("Sorry No Note found in the selected Folder")
				.setCancelable(true)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Dismiss the dialog on OK
						dialog.dismiss();
					}
				})
				.show();	
			}
			else{

				//Create the list of Notes
				CharSequence[] items = new CharSequence[sortedNoteList.size()];
				int i =0;

				//Add the folder names to the Array
				for (File fl:sortedNoteList){
					items[i]=fl.getTitle();
					i++;
				}

				//Create the new Dialog
				alDialogBuild = new AlertDialog.Builder(getActivity());
				alDialogBuild.setTitle("Previous Note Selection");

				alDialogBuild.setSingleChoiceItems(items,-1, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						//Remember the selection
						selected=which;

					}
				});
				alDialogBuild.setCancelable(true);
				alDialogBuild.setPositiveButton("Open", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						File note= sortedNoteList.get(selected);
						//Create a new intent with the URL of the document; Android will ask the proper App to handle it
						Intent resultIntent = new Intent(Intent.ACTION_VIEW); 
						Uri uri = Uri.parse("https://docs.google.com/document/d/"+note.getId()); 
						resultIntent.setData(uri);

						startActivity(resultIntent);

						dialog.dismiss();

					}
				});
				alDialogBuild.setNegativeButton("Trash", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						pd.dismiss();
						pd = ProgressDialog.show(getActivity(),"Trashing Note in Progress","Please Wait...",true,false,null);
						deleteNote(sortedNoteList.get(selected));


					}
				});


				alDialogBuild.show();
			}
		}
	}



}

