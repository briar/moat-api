package org.briarproject.moattest;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import javax.inject.Inject;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.ViewModelProvider;
import dagger.hilt.android.AndroidEntryPoint;

import static android.os.Build.VERSION.SDK_INT;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

	MainViewModel viewModel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		viewModel = new ViewModelProvider(this).get(MainViewModel.class);
		setContentView(R.layout.activity_main);

		EditText countryCode = findViewById(R.id.countryCode);
		Button request = findViewById(R.id.request);
		TextView response = findViewById(R.id.response);

		viewModel.getResponse().observe(this, text -> {
			request.setEnabled(true);
			response.setText(text);
		});

		request.setOnClickListener(v -> {
			request.setEnabled(false);
			response.setText(null);
			viewModel.sendRequest(countryCode.getText().toString());
		});
	}
}