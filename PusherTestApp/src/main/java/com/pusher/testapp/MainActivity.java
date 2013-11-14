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

    private Pusher pusher;
    private PusherOptions options;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addCategory("com.pusher.testapp.MainActivity");
        this.registerReceiver(new NetworkInfoReceiver(), filter);

        ((TextView)findViewById(R.id.log_view)).setMovementMethod(new ScrollingMovementMethod());

        createPusher();
    }

    private void createPusher() {
        // Potentially tear down existing instance
        if (pusher != null) pusher.disconnect();

        final ToggleButton ssl = (ToggleButton)findViewById(R.id.toggle_ssl);
        options = new PusherOptions()
                .setEncrypted(ssl.isChecked());

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
     * Event trigger
     */

    private void triggerEvent() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    HttpPost method = new HttpPost("http://test.pusher.com/hello?env=default");
                    HttpClient client = new DefaultHttpClient();
                    HttpResponse httpResponse = client.execute(method);
                    log("Server", "triggering event via REST API");

                    final int statusCode = httpResponse.getStatusLine().getStatusCode();
                    if (statusCode != 200) {
                        log("Server", "Error triggering event: HTTP " + statusCode);
                    }
                }
                catch (Exception e) {
                    log("Server", "Error triggering event: " + e.toString());
                }
                return null;
            }
        }.execute();
    }

    /*
     * UI callbacks
     */

    public void onClick_Connect(final View btnConnect) {
        if (pusher.getConnection().getState() == ConnectionState.DISCONNECTED) {
            pusher.connect(this);
        }
        else if (pusher.getConnection().getState() == ConnectionState.CONNECTED) {
            pusher.disconnect();
        }
        // Ignore presses in other states, button should be disabled
    }

    public void onClick_TriggerEvent(final View btnTriggerEvent) {
        triggerEvent();
    }

    public void onClick_Ssl(final View sslToggle) {
        log("App", "Disconnecting to change SSL state");
        createPusher();
    }

    public void onClick_ClearLog(final View btnClearLog) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final TextView logs = (TextView)findViewById(R.id.log_view);
                logs.setText("");
                logs.invalidate();
            }
        });
    }

    public void onClick_EmailLog(final View btnEmailLogs) {
        final CharSequence logs = ((TextView)findViewById(R.id.log_view)).getText();

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
                final Button connectBtn = (Button)findViewById(R.id.btn_connect);
                connectBtn.setText(connectText);
                connectBtn.setEnabled(connectEnabled);
                connectBtn.invalidate();

                final ToggleButton sslToggle = (ToggleButton)findViewById(R.id.toggle_ssl);
                sslToggle.setEnabled(connectEnabled);
                sslToggle.invalidate();

                final TextView pusherStatus = (TextView)findViewById(R.id.pusher_status);
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
                final TextView view = (TextView) findViewById(R.id.log_view);
                view.append("[" + tag + "] " + text + "\n");

                // Is sometimes null on startup, I can't fathom from the docs why,
                // as we don't touch any of this stuff til after they do in the examples.
                if (view.getLayout() != null) {
                    final int scrollAmount = view.getLayout().getLineTop(view.getLineCount()) - view.getHeight();
                    view.scrollTo(0, scrollAmount > 0 ? scrollAmount : 0);
                }
                view.invalidate();
            }
        });
    }

    private void updateNetStatus(final Context context) {
        final ConnectivityManager mgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        boolean connected = false;
        String connectionType = "";

        for (final NetworkInfo info : mgr.getAllNetworkInfo()) {
            if (info.isConnected()) {
                connectionType = info.getTypeName();
                connected = true;
                break;
            }
        }

        final String text = connected ? "Connected (" + connectionType + ")" : "Disconnected";
        final int bgResource = connected ? R.drawable.rect_green : R.drawable.rect_red;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final TextView view = (TextView)findViewById(R.id.net_status);
                view.setBackgroundDrawable(getResources().getDrawable(bgResource));
                view.setText(text);
                view.invalidate();
            }
        });

        log("Internet", text);
    }

    public class NetworkInfoReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            updateNetStatus(context);
        }
    }

    /*
     * Hide/show
     */



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
