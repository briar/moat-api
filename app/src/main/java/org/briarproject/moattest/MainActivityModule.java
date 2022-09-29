package org.briarproject.moattest;

import org.briarproject.moattest.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class MainActivityModule {

	@Binds
	@IntoMap
	@ViewModelKey(MainViewModel.class)
	abstract ViewModel bindViewModel(MainViewModel mainViewModel);
}
