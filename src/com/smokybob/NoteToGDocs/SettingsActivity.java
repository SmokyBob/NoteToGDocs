package com.smokybob.NoteToGDocs;

import com.smokybob.NoteToGDocs.R;
import com.smokybob.NoteToGDocs.R.string;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.*;

import android.preference.PreferenceManager;
import android.util.Log;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.common.AccountPicker;
import com.google.api.client.googleapis.extensions.android.accounts.GoogleAccountManager;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.services.drive.DriveScopes;

public class SettingsActivity extends SherlockPreferenceActivity {

	public static final String[] ACCOUNT_TYPE = new String[] {GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE};

	private static final int CHOOSE_ACCOUNT = 0;

	private static final int STATE_INITIAL = 0;
	private static final int STATE_CHOOSING_ACCOUNT = 1;
	private static final int STATE_DONE = 3;

	private GoogleAccountManager mAccountManager;
	private Preference mAccountPreference;
	private ListPreference mSyncPreference;
	private SharedPreferences mPreferences;
	private int mState;
	
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

		// Initialize the preferred account setting.
		mAccountPreference = this.findPreference("selected_account_preference");
		mAccountPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				chooseAccount();
				return true;
			}
		});

//		mSyncPreference = (ListPreference) this.findPreference("sync_frequency_preference");
//		mSyncPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
//			@Override
//			public boolean onPreferenceChange(Preference preference, Object newValue) {
//				SharedPreferences.Editor editor =
//						PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
//				editor.putString("sync_frequency_preference", (String) newValue);
//				editor.commit();
//				setSyncFrequency(getPreferenceAccount());
//				return true;
//			}
//		});
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
		GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(this, DriveScopes.DRIVE);
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


}
