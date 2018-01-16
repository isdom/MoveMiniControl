package microbit.minimove;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MoveMini";
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice;
    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic mEventCharacteristic;

    private static final String DEVICE_NAME = "BBC micro:bit";
    private static final int RSSI_THRESHOLD = -100;
    private static final int REQUEST_ENABLE_BT = 1;

    private Timer mScanTimer;
    private static UUID MICROBIT_SERVICE =     UUID.fromString("e95d93af-251d-470a-a062-fa1922dfa9a8");
    private static UUID EVENT_CHARACTERISTIC = UUID.fromString("e95d5404-251d-470a-a062-fa1922dfa9a8");

    private static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    private boolean mWriteWithAnswer = false;

    private static int MES_DPAD_CONTROLLER_ID = 1104;
    private static int MES_DPAD_BUTTON_1_DOWN = 9; // forward
    private static int MES_DPAD_BUTTON_1_UP = 10; // stop
    private static int MES_DPAD_BUTTON_2_DOWN = 11; // backward
    private static int MES_DPAD_BUTTON_3_DOWN = 13; // left
    private static int MES_DPAD_BUTTON_4_DOWN = 15; // right


    protected enum State {IDLE, CONNECTING, CONNECTED};

    protected State state = State.IDLE;

    private boolean mConnected;
    private boolean mWritten;

    private Button up;
    private Button down;
    private Button left;
    private Button right;

    private Button connect;
    private Button disconnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        up = (Button) findViewById(R.id.up);
        down = (Button) findViewById(R.id.down);
        left = (Button) findViewById(R.id.left);
        right = (Button) findViewById(R.id.right);
        connect = (Button) findViewById(R.id.connect);
        disconnect = (Button) findViewById(R.id.disconnect);

        setTouchListeners();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @RequiresApi(api = Build.VERSION_CODES.M)
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }
    }

    private void setTouchListeners() {
        up.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handleDirectionCommand("forward", event);
                return true;
            }
        });

        down.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handleDirectionCommand("backward", event);
                return true;
            }
        });

        left.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handleDirectionCommand("left", event);
                return true;
            }
        });

        right.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handleDirectionCommand("right", event);
                return true;
            }
        });

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connect();
            }
        });

        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                disconnect();
            }
        });
    }

    private void handleDirectionCommand(String direction, MotionEvent event) {
        if (isConnected()) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if ("forward".equalsIgnoreCase(direction)) {
                        driveForward();
                    } else if ("backward".equalsIgnoreCase(direction)) {
                        driveBackward();
                    } else if ("left".equalsIgnoreCase(direction)) {
                        turnLeft();
                    } else if ("right".equalsIgnoreCase(direction)) {
                        turnRight();
                    } else {
                        stop();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    stop();
                    break;
            }
        } else {
            Toast.makeText(getApplicationContext(), "Not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void driveForward() {
        sendPacket(MES_DPAD_CONTROLLER_ID, MES_DPAD_BUTTON_1_DOWN);
    }

    private void driveBackward() {
        sendPacket(MES_DPAD_CONTROLLER_ID, MES_DPAD_BUTTON_2_DOWN);
    }

    private void turnLeft() {
        sendPacket(MES_DPAD_CONTROLLER_ID, MES_DPAD_BUTTON_3_DOWN);
    }

    private void turnRight() {
        sendPacket(MES_DPAD_CONTROLLER_ID, MES_DPAD_BUTTON_4_DOWN);
    }

    private void stop() {
        sendPacket(MES_DPAD_CONTROLLER_ID, MES_DPAD_BUTTON_1_UP);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;
            }
        }
    }

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "onConnectionStateChange: STATE_CONNECTED");
                gatt.discoverServices();
                mGatt = gatt;
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "onConnectionStateChange: STATE_DISCONNECTED");
                // This is necessary to handle a disconnect on the copter side
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mConnected = false;
                state = State.IDLE;
                // it should actually be notifyConnectionLost, but there is
                // no difference between a deliberate disconnect and a lost connection
            } else {
                Log.d(TAG, "onConnectionStateChange: else: " + newState);
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mConnected = false;
                state = State.IDLE;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.disconnect();
            } else {
                BluetoothGattService mbService = gatt.getService(MICROBIT_SERVICE);
                mEventCharacteristic = mbService.getCharacteristic(EVENT_CHARACTERISTIC);
                gatt.setCharacteristicNotification(mEventCharacteristic, true);
//                BluetoothGattDescriptor descriptor = mEventCharacteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
//                Log.d(TAG, "Descriptor: " + descriptor.toString());
//                if(descriptor != null) {
//                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//                    gatt.writeDescriptor(descriptor);
//                } else {
//                    Log.d(TAG, "Descriptor is null")
//                }
                Log.d(TAG, "Connected!");
                mConnected = true;
                mWritten = false;
                state = State.CONNECTED;
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "On write call for characteristic: " + characteristic.getUuid().toString());
            mWritten = true;
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d(TAG, "On read call for characteristic: " + characteristic.getUuid().toString());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "On changed call for characteristic: " + characteristic.getUuid().toString());
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.d(TAG, "On read call for descriptor: " + descriptor.getUuid().toString());
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "On write call for descriptor: " + descriptor.getUuid().toString());
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] anounce) {
            if (device != null && device.getName() != null) {
                Log.d(TAG, "Scanned device \"" + device.getName() + "\" RSSI: " + rssi);

                if (device.getName().startsWith(DEVICE_NAME) && rssi> RSSI_THRESHOLD) {
                    mBluetoothAdapter.stopLeScan(this);
                    if (mScanTimer != null) {
                        mScanTimer.cancel();
                        mScanTimer = null;
                    }
                    state = State.CONNECTING;
                    mDevice = device;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDevice.connectGatt(getApplicationContext(), true, mGattCallback);
                        }
                    });
                }
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void connect() {
        if (state != State.IDLE) {
            Toast.makeText(getApplicationContext(), "Connection already started", Toast.LENGTH_SHORT).show();
            return;
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Toast.makeText(getApplicationContext(), "Bluetooth needs to be started", Toast.LENGTH_SHORT).show();
            return;
        }

        mBluetoothAdapter.stopLeScan(mLeScanCallback);
        mBluetoothAdapter.startLeScan(mLeScanCallback);

        if (mScanTimer != null) {
            mScanTimer.cancel();
        }

        mScanTimer = new Timer();
        mScanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                state = State.IDLE;
//                Toast.makeText(getApplicationContext(), "BLE connection timeout", Toast.LENGTH_LONG).show();
            }
        }, 5000);

        state = State.CONNECTING;
    }

    public void disconnect() {
        Log.d(TAG, "disconnect()");
        runOnUiThread(new Runnable() {
            public void run() {
                if(mConnected) {
                    mGatt.disconnect();
                    //delay close command to fix potential NPE
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mGatt.close();
                            mGatt = null;
                        }
                    }, 100);
                    mConnected = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    if (mScanTimer != null) {
                        mScanTimer.cancel();
                        mScanTimer = null;
                    }
                    state = State.IDLE;
                }
            }
        });
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    public void sendPacket(int eventCode, int value) {
        runOnUiThread(new SendBlePacket(eventCode, value));
    }

    private class SendBlePacket implements Runnable {

        private final int eventCode;
        private final int value;

        public SendBlePacket(int eventCode, int value){
            this.eventCode = eventCode;
            this.value = value;
        }


        public void run() {
//            Log.d(TAG, "sendBlePacket run(): " + mConnected + " " + mWritten);
//            if(mConnected && mWritten) {
            if(mConnected) {
                if (mWriteWithAnswer) {
                    mEventCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    mWritten = false;
                } else {
                    mEventCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    mWritten = true;
                }
                ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                bb.putShort((short) eventCode);
                bb.putShort((short) value);
                mEventCharacteristic.setValue(bb.array());
                Log.d(TAG, "sendBlePacket: " + getHexString(bb.array()));
                mGatt.writeCharacteristic(mEventCharacteristic);
            }
        }
    }


    public static String getHexString(byte... array) {
        StringBuffer sb = new StringBuffer();
        for (byte b : array) {
            sb.append(String.format("%02X", b));
            sb.append(" ");
        }
        return sb.toString();
    }
}
