package com.pusher.testapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

public class MainActivity extends ActionBarActivity
                          implements ConnectionEventListener, ChannelEventListener {

    private static final String API_KEY = "22364f2f790269bec0a0";
    private static final String CHANNEL_NAME = "channel";
    private static final String EVENT_NAME = "event";

    private TextView pusherStatus;
    private TextView netStatus;
    private TextView logView;
    private ToggleButton sslToggle;
    private Button connectBtn;
    private Button triggerEventBtn;

    private Pusher pusher;
    private PusherOptions options;

    /**
     * Reconnect immediately on receiving disconnected state transition if this flag is set.
     * Used to toggle SSL use on connection.
     */
    private boolean reconnect = false;

    private void createPusher() {
        // Potentially tear down existing instance
        if (pusher != null) pusher.disconnect();

        options = new PusherOptions()
                .setEncrypted(sslToggle.isChecked());

        pusher = new Pusher(API_KEY, options);
        pusher.subscribe(CHANNEL_NAME, this, EVENT_NAME);
    }

    /*
     * Pusher CALLBACKS
     */

    @Override
    public void onConnectionStateChange(final ConnectionStateChange connectionStateChange) {
        final ConnectionState newState = connectionStateChange.getCurrentState();

        final StringBuilder sb = new StringBuilder("State change: ").append(newState.name());
        if (newState == ConnectionState.CONNECTED) {
            sb.append(" (")
                    .append(options.isEncrypted() ? "SSL" : "Non SSL")
                    .append(")");
        }
        log("Pusher", sb.toString());

        updateUiOnStateChange(newState);

        if (newState == ConnectionState.DISCONNECTED && reconnect) {
            reconnect = false;
            // We received this update from the previous pusher instance which we tore
            // down to change the SSL option. Now that it is fully disconnected, we call
            // connect on the new instance we replaced it with.
            pusher.connect(this);
        }
    }

    @Override
    public void onSubscriptionSucceeded(final String s) {
        log("Pusher", "Subscribed to: " + s);
    }

    @Override
    public void onEvent(final String channel, final String event, final String data) {
        log("Pusher", "Event received: " + data);
    }

    @Override
    public void onError(final String message, final String code, final Exception e) {
        log("Pusher", "Error: [" + message + "] [" + code + "] [" + e + "]");
    }

    /*
     * UI callbacks
     */

    public void onClick_Connect(final View view) {
        if (pusher.getConnection().getState() == ConnectionState.DISCONNECTED) {
            log("App", "Connect button presses");
            pusher.connect(this);
        }
        else if (pusher.getConnection().getState() == ConnectionState.CONNECTED) {
            log("App", "Disconnect button pressed");
            pusher.disconnect();
        }
        // Ignore presses in other states, button should be disabled
    }

    public void onClick_Ssl(final View view) {
        log("App", "SSL toggled to " + sslToggle.isChecked());
        reconnect = true;
        createPusher();
    }

    public void onClick_ClearLog(final View view) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logView.setText("");
                logView.invalidate();
            }
        });
    }

    public void onClick_TriggerEvent(final View view) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    log("Server", "triggering event via REST API");
                    HttpPost method = new HttpPost("http://test.pusher.com/hello?env=default");
                    HttpClient client = new DefaultHttpClient();
                    HttpResponse httpResponse = client.execute(method);

                    final int statusCode = httpResponse.getStatusLine().getStatusCode();
                    if (statusCode != 200) {
                        log("Server", "Error triggering event: HTTP " + statusCode);
                    }
                }
                catch (final Exception e) {
                    log("Server", "Error triggering event: " + e.toString());
                }
                return null;
            }
        }.execute();
    }

    public void onClick_EmailLog(final View view) {
        final CharSequence logs = logView.getText();

        final Intent emailIntent = new Intent(Intent.ACTION_SENDTO,
                Uri.fromParts("mailto", "support@pusher.com", null));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Android test app logs");
        emailIntent.putExtra(Intent.EXTRA_TEXT, logs);
        startActivity(Intent.createChooser(emailIntent, "Send email..."));
    }

    /*
     * UI update
     */

    private void updateUiOnStateChange(final ConnectionState state) {
        final boolean connectEnabled;
        final Drawable indicatorBg;
        final CharSequence indicatorText;
        final CharSequence connectText;

        switch (state) {
            case CONNECTED:
                connectEnabled = true;
                connectText = getResources().getString(R.string.disconnect);
                indicatorBg = getResources().getDrawable(R.drawable.rect_green);
                indicatorText = getResources().getString(R.string.connected);
                break;
            case DISCONNECTED:
                connectEnabled = true;
                connectText = getResources().getString(R.string.connect);
                indicatorBg = getResources().getDrawable(R.drawable.rect_red);
                indicatorText = getResources().getString(R.string.disconnected);
                break;
            case CONNECTING:
                connectEnabled = false;
                indicatorBg = getResources().getDrawable(R.drawable.rect_orange);
                indicatorText = getResources().getString(R.string.connecting);
                connectText = getResources().getString(R.string.connect);
                break;
            case DISCONNECTING:
                connectEnabled = false;
                connectText = getResources().getString(R.string.connect);
                indicatorBg = getResources().getDrawable(R.drawable.rect_orange);
                indicatorText = getResources().getString(R.string.disconnecting);
                break;
            default:
                throw new RuntimeException("Notified of switch to unknown state [" + state + "]");
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectBtn.setText(connectText);
                connectBtn.setEnabled(connectEnabled);
                connectBtn.invalidate();

                sslToggle.setEnabled(connectEnabled);
                sslToggle.invalidate();

                pusherStatus.setBackgroundDrawable(indicatorBg);
                pusherStatus.setText(indicatorText);
                pusherStatus.invalidate();
            }
        });
    }

    private void log(final String tag, final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                logView.append("[" + tag + "] " + text + "\n");

                // Is sometimes null on startup, I can't fathom from the docs why,
                // as we don't touch any of this stuff til after they do in the examples.
                if (logView.getLayout() != null) {
                    final int scrollAmount =
                            logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
                    logView.scrollTo(0, scrollAmount > 0 ? scrollAmount : 0);
                }
                logView.invalidate();
            }
        });
    }

    private void updateNetStatus(final String connectionType) {
        final boolean connected = connectionType.length() > 0;

        final String text = connected ? "Connected (" + connectionType + ")" : "Disconnected";
        final int bgResource = connected ? R.drawable.rect_green : R.drawable.rect_red;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                netStatus.setBackgroundDrawable(getResources().getDrawable(bgResource));
                netStatus.setText(text);
                netStatus.invalidate();

                triggerEventBtn.setEnabled(connected);
                triggerEventBtn.invalidate();
            }
        });

        log("Internet", text);
    }

    public class NetworkInfoReceiver extends BroadcastReceiver {
        private String currentlyConnectedType = "";

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final ConnectivityManager mgr =
                    (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

            String connectionType = "";

            for (final NetworkInfo info : mgr.getAllNetworkInfo()) {
                if (info.isConnected()) {
                    connectionType = info.getTypeName();
                    break;
                }
            }

            if (!currentlyConnectedType.equals(connectionType)) {
                currentlyConnectedType = connectionType;
                updateNetStatus(connectionType);
            }
        }
    }

    /*
     * App Lifecycle
     */

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addCategory("com.pusher.testapp.MainActivity");
        this.registerReceiver(new NetworkInfoReceiver(), filter);

        this.pusherStatus = (TextView)findViewById(R.id.pusher_status);
        this.netStatus = (TextView)findViewById(R.id.net_status);
        this.logView = (TextView)findViewById(R.id.log_view);
        this.connectBtn = (Button)findViewById(R.id.btn_connect);
        this.triggerEventBtn = (Button)findViewById(R.id.btn_trigger);
        this.sslToggle = (ToggleButton)findViewById(R.id.toggle_ssl);

        this.logView.setMovementMethod(new ScrollingMovementMethod());

        createPusher();
    }

    @Override
    protected void onStart() {
        super.onStart();
        log("App", "Started");
    }

    @Override
    protected void onPause() {
        super.onPause();
        log("App", "Paused");
    }

    @Override
    protected void onResume() {
        super.onResume();
        log("App", "Resumed");
    }

    @Override
    protected void onStop() {
        super.onStop();
        log("App", "Stopped");

        // If we are not to leak resources, we need to release them when onStop is called on us.
        // This method tears down the WebSocket connection and terminates the associated threads.
        pusher.disconnect();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        log("App", "Restarted");

        // On restart, the Pusher instance still knows about our registered subscriptions,
        // so all we need to do to pick up where we left off is call connect
        pusher.connect();
    }

    /*
     * UI Boilerplate
     */

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
