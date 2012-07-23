package it.quadrata.android.quad_prox_mob;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class NodeListActivity extends Activity {

	// Authentication credentials
	private static String server;
	private static String username;
	private static String realm;
	private static String password;
	private static String ticket;
	private static String token;

	// Cluster info
	private static String cluster;
	private static String version;
	private static String release;

	// Node info
	private static String node;
	private static String node_vers;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.nodes_list_layout);

		// Retrieving of login preferences, otherwise start login view
		final SharedPreferences authPref = getSharedPreferences("AuthPref",
				Context.MODE_PRIVATE);
		server = authPref.getString("server", null);
		username = authPref.getString("username", null);
		realm = authPref.getString("realm", null);
		password = authPref.getString("password", null);
		if ((server == null) || (username == null) || (password == null)) {
			Intent loginIntent = new Intent(NodeListActivity.this,
					AuthActivity.class);
			startActivityForResult(loginIntent, 0);
		} else {
			buildNodeList();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if ((requestCode == 0) && (resultCode == RESULT_OK)) {
			server = data.getStringExtra("server");
			username = data.getStringExtra("username");
			realm = data.getStringExtra("realm");
			password = data.getStringExtra("password");
			buildNodeList();
		}
	}

	private void buildNodeList() {
		// Cluster header views
		final TextView clusterInfo = (TextView) findViewById(R.id.clusterInfo);
		final TextView clusterVers = (TextView) findViewById(R.id.clusterVers);
		final TextView clusterNodes = (TextView) findViewById(R.id.clusterNodes);

		// Nodes list view and adapter
		ListView nodeListView = (ListView) findViewById(R.id.nodeList);
		final ArrayAdapter<NodeItem> nodeArrayAdapter = new ArrayAdapter<NodeItem>(
				this, R.layout.node_row_layout, R.id.nodeRow) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				return getNodeRowHolder(position, convertView, parent);
			}

			public View getNodeRowHolder(int position, View convertView,
					ViewGroup parent) {
				NodeRowHolder rowHolder = null;
				if (convertView == null) {
					LayoutInflater nodeInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					convertView = nodeInflater.inflate(
							R.layout.node_row_layout, null);
					rowHolder = new NodeRowHolder();
					rowHolder.nodeRowName = (TextView) convertView
							.findViewById(R.id.nodeRowName);
					rowHolder.nodeRowVers = (TextView) convertView
							.findViewById(R.id.nodeRowVers);
					convertView.setTag(rowHolder);
				} else {
					rowHolder = (NodeRowHolder) convertView.getTag();
				}
				NodeItem item = getItem(position);
				rowHolder.nodeRowName.setText(item.node_name);
				rowHolder.nodeRowVers.setText("Proxmox VE " + item.node_vers);
				return convertView;
			}
		};

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
					HttpClient serverHttpClient = httpApp.getHttpClient();

					// Proxmox ticket request
					HttpPost authRequest = new HttpPost(server
							+ "/api2/json/access/ticket");
					List<NameValuePair> authParameters = new ArrayList<NameValuePair>();
					authParameters.add(new BasicNameValuePair("username",
							username + "@" + realm));
					authParameters.add(new BasicNameValuePair("password",
							password));
					HttpEntity authEntity = new UrlEncodedFormEntity(
							authParameters);
					authRequest.setEntity(authEntity);
					String authResponse = serverHttpClient.execute(authRequest,
							serverResponseHandler);
					// Ticket and token extraction from authentication
					// json string
					JSONObject authObject = new JSONObject(authResponse);
					JSONObject data = authObject.getJSONObject("data");
					ticket = data.getString("ticket");
					token = data.getString("CSRFPreventionToken");

					// Cluster info request
					HttpGet versionRequest = new HttpGet(server
							+ "/api2/json/version");
					versionRequest.addHeader("Cookie", "PVEAuthCookie="
							+ ticket);
					String versionResponse = serverHttpClient.execute(
							versionRequest, serverResponseHandler);
					JSONObject versionObject = new JSONObject(versionResponse);
					JSONObject versionDataObject = versionObject
							.getJSONObject("data");
					cluster = server.substring(8, server.length() - 5);
					version = versionDataObject.getString("version");
					release = versionDataObject.getString("release");
					clusterVers.post(new Runnable() {
						@Override
						public void run() {
							clusterInfo.setText(cluster);
							clusterVers.setText("Proxmox VE " + version + "-"
									+ release);
						}
					});

					// Nodes list request
					HttpGet nodesRequest = new HttpGet(server
							+ "/api2/json/nodes");
					nodesRequest.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					String nodesResponse = serverHttpClient.execute(
							nodesRequest, serverResponseHandler);
					JSONObject nodesObject = new JSONObject(nodesResponse);
					JSONArray nodesArray = nodesObject.getJSONArray("data");
					final int nodesArrayLength = nodesArray.length();
					clusterNodes.post(new Runnable() {
						@Override
						public void run() {
							clusterNodes.setText(Integer
									.toString(nodesArrayLength));
						}
					});

					// Nodes list items creation
					JSONObject singleNodeObject = new JSONObject();
					for (int i = 0; i <= (nodesArrayLength - 1); i++) {
						singleNodeObject = nodesArray.getJSONObject(i);
						final NodeItem item = new NodeItem();
						item.node_name = singleNodeObject.getString("node");
						HttpGet nodesVersRequest = new HttpGet(server
								+ "/api2/json/nodes/" + item.node_name
								+ "/version");
						nodesVersRequest.addHeader("Cookie", "PVEAuthCookie="
								+ ticket);
						String nodesVersResponse = serverHttpClient.execute(
								nodesVersRequest, serverResponseHandler);
						JSONObject nodesVersObject = new JSONObject(
								nodesVersResponse);
						JSONObject nodesVersDataObject = nodesVersObject
								.getJSONObject("data");
						item.node_vers = (nodesVersDataObject
								.getString("version") + "-" + nodesVersDataObject
								.getString("release"));
						NodeListActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								nodeArrayAdapter.add(item);
							}
						});
					}
				} catch (Exception e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "No error message");
					}
					if (isOnline() == false) {
						showNoConnDialog();
					} else {
						showWrongDataDialog();
					}
				}
			}
		}).start();

		nodeListView.setAdapter(nodeArrayAdapter);

		nodeListView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				NodeItem item = (NodeItem) parent.getItemAtPosition(position);
				node = item.node_name;
				node_vers = item.node_vers;
				Intent vmListIntent = new Intent(NodeListActivity.this,
						VMListActivity.class);
				// Putting VM data into the intent for VM stats activity
				vmListIntent.putExtra("server", server);
				vmListIntent.putExtra("ticket", ticket);
				vmListIntent.putExtra("token", token);
				vmListIntent.putExtra("node", node);
				vmListIntent.putExtra("node_index", position);
				vmListIntent.putExtra("node_vers", node_vers);
				startActivity(vmListIntent);
			}
		});
	}

	private static class NodeItem {
		public String node_name;
		public String node_vers;
	}

	private static class NodeRowHolder {
		public TextView nodeRowName;
		public TextView nodeRowVers;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater nodesMenu_inflater = getMenuInflater();
		nodesMenu_inflater.inflate(R.menu.nodes_list_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.authPref:
			Intent loginIntent = new Intent(NodeListActivity.this,
					AuthActivity.class);
			startActivityForResult(loginIntent, 0);
			return true;
		case R.id.updateNodesPref:
			buildNodeList();
			return true;
		case R.id.logPref:
			Intent logIntent = new Intent(NodeListActivity.this,
					ClusterLogActivity.class);
			logIntent.putExtra("server", server);
			logIntent.putExtra("ticket", ticket);
			startActivity(logIntent);
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private final ResponseHandler<String> serverResponseHandler = new ResponseHandler<String>() {

		@Override
		public String handleResponse(HttpResponse response)
				throws ClientProtocolException, IOException {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);

			return result;
		}

	};

	private void showWrongDataDialog() {
		NodeListActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						NodeListActivity.this);
				builder.setTitle("Unable to authenticate");
				builder.setMessage("Wrong authentication data. \nDo you want to review it?");
				builder.setCancelable(false);
				builder.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								Intent loginIntent = new Intent(
										NodeListActivity.this,
										AuthActivity.class);
								startActivityForResult(loginIntent, 0);
							}
						});
				builder.setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								NodeListActivity.this.finish();
							}
						});
				AlertDialog alertDialog = builder.create();
				alertDialog.show();
			}
		});
	}

	private void showNoConnDialog() {
		NodeListActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						NodeListActivity.this);
				builder.setTitle("No network connection");
				builder.setMessage("An Internet connection is needed. \nDo you want to retry?");
				builder.setCancelable(false);
				builder.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								buildNodeList();
							}
						});
				builder.setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								NodeListActivity.this.finish();
							}
						});
				AlertDialog alertDialog = builder.create();
				alertDialog.show();
			}
		});
	}

	public boolean isOnline() {
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		return (networkInfo != null && networkInfo.isConnected() && !networkInfo.isFailover());
	}

}