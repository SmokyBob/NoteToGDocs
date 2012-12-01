package com.smokybob.NoteToGDocs;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
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
            //TODO - MSO - 20121127 - Lanciare il salvataggio async
            return true;
        case R.id.undo:
            //MSO - 20121127 - Clean the text
        	EditText editText1 = (EditText) findViewById(R.id.editText1);
        	editText1.setText("");
        	
            return true;
        case R.id.menu_settings:
        	//Open Settings Activity
        	Intent settings = new Intent(this,SettingsActivity.class);
        	startActivity(settings);
        default:
            return super.onOptionsItemSelected(item);
		}
	}
}
