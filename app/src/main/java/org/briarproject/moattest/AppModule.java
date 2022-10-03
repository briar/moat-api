package org.briarproject.moattest;

import java.util.concurrent.ExecutorService;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

import static java.util.concurrent.Executors.newCachedThreadPool;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

	@Provides
	@Singleton
	ExecutorService provideBackgroundExecutor() {
		return newCachedThreadPool();
	}
}
