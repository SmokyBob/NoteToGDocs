package com.smokybob.NoteToGDocs;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class Util {
	//MSO - 20130411 - Check the network State
	public static boolean CheckNetwork(Activity activity){
		boolean toRet=false;
		
		//Get the Connectivity Manager from the calling Activiy
		ConnectivityManager connMgr = (ConnectivityManager) 
		        activity.getSystemService(Context.CONNECTIVITY_SERVICE);
		
		//Get The network Infos
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		
		//Check if Connected
		if (networkInfo != null && networkInfo.isConnected()){
			toRet= true;
		}
		else {
			toRet=false;
		}
		
		return toRet;
	} 
}
