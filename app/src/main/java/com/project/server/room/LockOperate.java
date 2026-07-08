package com.project.server.room;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import localDatabase.EventLockDisplayData;
import localDatabase.Locks;
import logicBox.SharedSpace;

public class LockOperate extends AppCompatActivity implements EventLockDisplayData {

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "LockOperate";
    private static final int BT_PERMISSION_REQUEST = 100;

    String address;
    String lockNumber;
    String lockName;
    String lockAddress;

    TextView cLockAddress;
    TextView tvStatus;
    Button btnLock;
    Button btnUnlock;
    ImageView lockView;

    localDatabase.Locks locksLocalDb;
    ProgressDialog progressDialog;
    SharedSpace sharedSpace;

    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private ConnectedThread mConnectedThread;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_lock:
                    closeSocket();
                    startActivity(new Intent(getApplication(), Home.class));
                    finish();
                    return true;
                case R.id.action_Users:
                    closeSocket();
                    intentCall(getApplication(), LockUsers.class);
                    return true;
                case R.id.action_logs:
                    closeSocket();
                    intentCall(getApplication(), LockLog.class);
                    return true;
            }
            return false;
        }
    };

    private void intentCall(Context mContext, Class aClass) {
        Intent intent = new Intent(mContext, aClass);
        intent.putExtra("lockNumber", lockNumber);
        intent.putExtra("lockName", lockName);
        intent.putExtra("lockAddress", lockAddress);
        startActivity(intent);
        finish();
    }

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_operate);

        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        progressDialog = new ProgressDialog(this);
        locksLocalDb = new Locks(LockOperate.this, this);
        sharedSpace = new SharedSpace(LockOperate.this);

        lockView     = findViewById(R.id.lockpic);
        btnLock      = findViewById(R.id.btnLock);
        btnUnlock    = findViewById(R.id.btnUnlock);
        cLockAddress = findViewById(R.id.tv_lockAddress);
        tvStatus     = findViewById(R.id.tv_lockStatus);

        lockNumber  = getIntent().getExtras().getString("lockNumber");
        lockName    = getIntent().getExtras().getString("lockName");
        lockAddress = getIntent().getStringExtra("lockAddress");
        cLockAddress.setText(lockAddress);

        // Request Bluetooth permissions on Android 12+
        requestBluetoothPermissions();

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        address   = locksLocalDb.getData(lockNumber);
        Log.d(TAG, "MAC address: " + address);

        // Connect in background after a short delay
        progressDialog.setMessage("Connecting...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                connectBluetooth();
            }
        }, 50);

        // LOCK button — sends "L"
        btnLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mConnectedThread == null) {
                    Toast.makeText(getApplication(), "Not connected to device. Trying to reconnect...", Toast.LENGTH_SHORT).show();
                    connectBluetooth();
                    return;
                }
                mConnectedThread.write("L\n"); // Added newline for hardware compatibility
                lockView.setImageResource(R.drawable.lock);
                tvStatus.setText("Locked");
                tvStatus.setTextColor(0xFFF44336); // red
                Toast.makeText(getApplication(), "Door Locked", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Sent: L");
            }
        });

        // UNLOCK button — sends "U"
        btnUnlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mConnectedThread == null) {
                    Toast.makeText(getApplication(), "Not connected to device. Trying to reconnect...", Toast.LENGTH_SHORT).show();
                    connectBluetooth();
                    return;
                }
                mConnectedThread.write("U\n"); // Added newline for hardware compatibility
                lockView.setImageResource(R.drawable.unlock);
                tvStatus.setText("Unlocked");
                tvStatus.setTextColor(0xFF4CAF50); // green
                pushLogToFire(lockNumber);
                Toast.makeText(getApplication(), "Door Unlocked", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Sent: U");
            }
        });
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                        }, BT_PERMISSION_REQUEST);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BT_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Bluetooth permission granted");
            } else {
                Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void connectBluetooth() {
        if (address == null || address.isEmpty() || !BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e(TAG, "Invalid MAC address: " + address);
            showConnectionError("Invalid MAC address");
            return;
        }

        try {
            BluetoothDevice device = btAdapter.getRemoteDevice(address);
            try {
                btSocket = createBluetoothSocket(device);
            } catch (IOException e) {
                Log.e(TAG, "Socket create failed: " + e.getMessage());
                showConnectionError("Socket creation failed");
                return;
            }
            btAdapter.cancelDiscovery();
            try {
                btSocket.connect();
                Log.d(TAG, "Bluetooth connected");
                mConnectedThread = new ConnectedThread(btSocket);
                mConnectedThread.start();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
                        tvStatus.setText("Connected");
                        tvStatus.setTextColor(0xFF4CAF50);
                        Toast.makeText(getApplication(), "Connected to Lock", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "Connection failed: " + e.getMessage());
                closeSocket();
                showConnectionError("Connection failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Bluetooth error: ", e);
            showConnectionError(e.getMessage());
        }
    }

    private void showConnectionError(final String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
                tvStatus.setText("Disconnected");
                tvStatus.setTextColor(0xFFF44336);
                Toast.makeText(getApplication(), "Connection failed: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, MY_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e);
        }
        return device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    private void closeSocket() {
        try {
            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
            if (btSocket != null) {
                btSocket.close();
                btSocket = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void pushLogToFire(String lockNumber) {
        Map<String, Object> logData = new HashMap<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yy HH:mm");
        Date now = new Date();
        String timeStamp = dateFormat.format(now);
        logData.put("name", sharedSpace.getString("name"));
        logData.put("timestamp", timeStamp);
        FirebaseDatabase.getInstance().getReference("logs")
                .child(lockNumber).push().setValue(logData);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        closeSocket();
        startActivity(new Intent(getApplication(), Home.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeSocket();
    }

    @Override
    public void eventDisplayData(String id, String name, String location) { }

    @Override
    public void eventGetAllLockId(String lockId) { }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            closeSocket();
            startActivity(new Intent(getApplication(), Home.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Background thread for Bluetooth communication
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final OutputStream mmOutStream;
        private final InputStream mmInStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn  = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error getting streams", e);
            }
            mmOutStream = tmpOut;
            mmInStream  = tmpIn;
        }

        public void run() {
            byte[] buffer = new byte[256];
            int bytes;
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    Log.d(TAG, "Received: " + new String(buffer, 0, bytes));
                } catch (IOException e) {
                    Log.d(TAG, "Input stream disconnected");
                    break;
                }
            }
        }

        public void write(String message) {
            Log.d(TAG, "Sending: " + message);
            try {
                mmOutStream.write(message.getBytes());
            } catch (IOException e) {
                Log.e(TAG, "Write failed: " + e.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplication(), "Failed to send command", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close socket", e);
            }
        }
    }
}
