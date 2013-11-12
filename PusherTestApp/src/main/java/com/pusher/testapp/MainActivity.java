package com.pusher.testapp;

import android.graphics.drawable.Drawable;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.pusher.client.Pusher;
import com.pusher.client.PusherOptions;
import com.pusher.client.channel.ChannelEventListener;
import com.pusher.client.connection.ConnectionEventListener;
import com.pusher.client.connection.ConnectionState;
import com.pusher.client.connection.ConnectionStateChange;

public class MainActivity extends ActionBarActivity
                          implements ConnectionEventListener, ChannelEventListener {

    private static final String TAG = "PUSHER";
    private static final String API_KEY = "157a2f34672776fc4a73";
    private static final String CHANNEL_NAME = "test-channel";

    private Pusher pusher;
    private PusherOptions options = new PusherOptions();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.container, new MainFragment())
                    .commit();
        }

        pusher = createPusher();
    }

    private Pusher createPusher() {
        return new Pusher(API_KEY, options);
    }

    private PusherOptions createOptionsFromUiState() {
        ToggleButton ssl = (ToggleButton) findViewById(R.id.toggle_ssl);

        PusherOptions options = new PusherOptions()
                .setEncrypted(ssl.isChecked());

        return options;
    }

    /*
     * PUSHER CALLBACKS
     */

    @Override
    public void onSubscriptionSucceeded(String s) {
        log("Subscription success: " + s);
    }

    @Override
    public void onConnectionStateChange(ConnectionStateChange connectionStateChange) {
        Log.i(TAG, "Got connection state change from [" + connectionStateChange.getPreviousState()
                + "] to [" + connectionStateChange.getCurrentState() + "]");

        log("State is now: " + connectionStateChange.getCurrentState().name());

        updateConnectionIndicator(R.id.pusher_status, connectionStateChange.getCurrentState() == ConnectionState.CONNECTED);

        updateConnectButton(connectionStateChange.getCurrentState());
    }

    @Override
    public void onError(String s, String s2, Exception e) {
        log("Error: " + s);
    }

    @Override
    public void onEvent(String s, String s2, String s3) {
        log("New event: " + s);
    }


    /*
     * UI callbacks
     */

    public void onClick_Connect(final View btnConnect) {
        Log.i(TAG, "Clicked connect button");

        if (pusher.getConnection().getState() == ConnectionState.DISCONNECTED) {
            pusher.connect(this);
        }
        else if (pusher.getConnection().getState() == ConnectionState.CONNECTED) {
            pusher.disconnect();
        }
        // Ignore presses in other states, button should be disabled
    }

    public void onClick_Ssl(final ToggleButton sslToggle) {
        options = createOptionsFromUiState();
    }

    /**
     * @param target resource ID, must be instance of TextArea
     * @param state true for green, false for red
     */
    private void updateConnectionIndicator(final int target, final boolean state) {

        final Drawable bg = state ? getResources().getDrawable(R.drawable.rect_green)
                                  : getResources().getDrawable(R.drawable.rect_red);

        final CharSequence text = state ? getResources().getText(R.string.connected)
                                        : getResources().getText(R.string.disconnected);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Updating connection indicator");
                TextView view = (TextView) findViewById(target);
                view.setBackground(bg);
                view.setText(text);
                view.invalidate();
            }
        });
    }

    private void updateConnectButton(final ConnectionState state) {
        final boolean enabled;
        final CharSequence text;

        switch (state) {
            case CONNECTED:
                enabled = true;
                text = getResources().getString(R.string.disconnect);
                break;
            case DISCONNECTED:
                enabled = true;
                text = getResources().getString(R.string.connect);
                break;
            case CONNECTING:
            case DISCONNECTING:
                enabled = false;
                text = getResources().getString(R.string.connect);
                break;
            default:
                throw new RuntimeException("Notified of switch to unknown state [" + state + "]");
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Button connectBtn = (Button) findViewById(R.id.btn_connect);
                connectBtn.setText(text);
                connectBtn.setEnabled(enabled);
                connectBtn.invalidate();
            }
        });
    }

    private void log(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView view = (TextView) findViewById(R.id.log_view);
                CharSequence currentLogs = view.getText();
                view.setText(currentLogs + "\n" + text);
                view.invalidate();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class MainFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }

}
