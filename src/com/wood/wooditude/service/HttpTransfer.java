package com.wood.wooditude.service;

import java.io.BufferedReader;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.text.DateFormat;
import java.util.Date;
import java.io.BufferedInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import org.json.JSONException;
import org.json.JSONObject;

import com.wood.wooditude.Consts;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

class HttpTransfer extends AsyncTask<String, String, Void> {

	private SSLContext sslContext;
	private Context appContext;
	// Add key here
	private String theRing = "";
	private String userpass;
	private String user;
	private String timestamp;

	public HttpTransfer(Context context) {
		appContext = context;
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(appContext);

		user = preferences.getString("username", null);
		String pass = preferences.getString("password", "b");
		if (user == null) {
			Toast toast = Toast.makeText(context, "Please configure username and password",
					Toast.LENGTH_LONG);
			toast.show();
		} else {
		userpass = Base64.encodeToString((user + ":" + pass).getBytes(),
				Base64.NO_WRAP);
		}

		timestamp = DateFormat.getDateTimeInstance().format(new Date());

		preferences
				.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
					@Override
					public void onSharedPreferenceChanged(
							SharedPreferences sharedPreferences, String key) {
						if (key.equals("username") || key.equals("password")) {
							user = sharedPreferences
									.getString("username", null);
							String pass = sharedPreferences.getString(
									"password", "b");
							Log.i("account details changed", user);
							userpass = Base64.encodeToString(
									(user + ":" + pass).getBytes(),
									Base64.NO_WRAP);
							try {
								runUploadDownload(null);
							} catch (MalformedURLException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				});
	}

	HostnameVerifier hostnameVerifier = new HostnameVerifier() {
    /* REMOVE this if you have a valid ssl cert */
		@Override
		public boolean verify(String hostname, SSLSession session) {
			HostnameVerifier hv = HttpsURLConnection
					.getDefaultHostnameVerifier();
			return hv.verify("serverhost", session);
		}
	};

	private void setupSSLContext() {
		try {
			InputStream certInput;
			// Load CAs from an InputStream
			// (could be from a resource or ByteArrayInputStream or ...)
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			certInput = this.appContext.getResources().getAssets()
					.open("serverssl.cert");
			InputStream caInput = new BufferedInputStream(certInput);
			Certificate ca;
			try {
				ca = cf.generateCertificate(caInput);
				System.out.println("ca="
						+ ((X509Certificate) ca).getSubjectDN());
			} finally {
				caInput.close();
			}

			// Create a KeyStore containing our trusted CAs
			String keyStoreType = KeyStore.getDefaultType();
			KeyStore keyStore = KeyStore.getInstance(keyStoreType);
			keyStore.load(null, null);
			keyStore.setCertificateEntry("ca", ca);

			// Create a TrustManager that trusts the CAs in our KeyStore
			String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
			TrustManagerFactory tmf = TrustManagerFactory
					.getInstance(tmfAlgorithm);
			tmf.init(keyStore);

			// Create an SSLContext that uses our TrustManager
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, tmf.getTrustManagers(), null);
		} catch (Exception b) {
			b.printStackTrace();
		}
	}

	private void runUploadDownload(String latLong) throws MalformedURLException {
		try {
			String output = "";
			String input = null;
			HttpsURLConnection request;

			if (user == null)
				return;

			if (sslContext == null)
				setupSSLContext();
			URL url = new URL("https://serverhost/receive.php");

			/*
			 * If we have no latLong we're just downloading and only need to
			 * pass the key.
			 */
			if (latLong == null)
				input = "thering=" + theRing;

			input = "timedate=" + timestamp + "&user=" + user + "&location="
					+ latLong + "&thering=" + theRing;

			request = (HttpsURLConnection) url.openConnection();
			request.setRequestProperty("Authorization", "Basic " + userpass);

			request.setSSLSocketFactory(sslContext.getSocketFactory());
			request.setHostnameVerifier(hostnameVerifier);

			request.setUseCaches(false);
			request.setDoOutput(true);
			request.setDoInput(true);
			request.setRequestProperty("Content-Type",
					"application/x-www-form-urlencoded");
			request.setRequestProperty("Content-length",
					String.valueOf(input.length()));

			request.setRequestMethod("POST");
			OutputStreamWriter post = new OutputStreamWriter(
					request.getOutputStream());
			//Log.i("writing to post", input);
			post.write(input);
			post.flush();

			BufferedReader in = new BufferedReader(new InputStreamReader(
					request.getInputStream()));
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				output += inputLine;
			}

			//Log.i(Consts.TAG, output);

			post.close();
			in.close();
			try {
				((LocationSync) appContext)
						.gotLocations(new JSONObject(output));
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (IOException e) {
			Log.i(Consts.TAG, "DAMN IT. Couldn't talk to server");
			e.printStackTrace();
		}
	}

	@Override
	protected Void doInBackground(String... params) {
		// int i = 0;
		// while (true) {
		// try {
		// Thread.sleep(2000);
		// } catch (InterruptedException e) {
		// e.printStackTrace();
		// }
		// i++;
		// Log.i("test", "testing" + i);
		try {
			if (params.length > 0) {
				String latlong = params[0];
				runUploadDownload(latlong);
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;

	}

	@Override
	protected void onPostExecute(Void result) {
		// TODO Auto-generated method stub
		super.onPostExecute(result);
	}
}
