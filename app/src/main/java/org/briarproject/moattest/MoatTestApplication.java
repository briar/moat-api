package org.briarproject.moattest;

import android.app.Application;

import org.conscrypt.Conscrypt;

import java.security.Security;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class MoatTestApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		Security.insertProviderAt(Conscrypt.newProvider(), 1);
	}
}
