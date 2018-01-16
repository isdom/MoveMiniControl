package microbit.minimove;

import android.annotation.TargetApi;
import android.app.Activity;
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
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * BLE Connection
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BleConnection {

    private static final String TAG = "BleConnection";
    private final Activity mContext;

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
    public static int MES_DPAD_BUTTON_1_DOWN = 9; // forward
    public static int MES_DPAD_BUTTON_1_UP = 10; // stop
    public static int MES_DPAD_BUTTON_2_DOWN = 11; // backward
    public static int MES_DPAD_BUTTON_3_DOWN = 13; // left
    public static int MES_DPAD_BUTTON_4_DOWN = 15; // right

    protected enum State {IDLE, CONNECTING, CONNECTED};

    protected State state = State.IDLE;

    private boolean mConnected;
    private boolean mWritten;

    public BleConnection(Activity context) {
        this.mContext = context;
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
                // This is necessary to handle a disconnect on the rover side
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
                    mContext.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDevice.connectGatt(mContext, true, mGattCallback);
                        }
                    });
                }
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void connect() {
        if (state != State.IDLE) {
            Toast.makeText(mContext, "Connection already started", Toast.LENGTH_SHORT).show();
            return;
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mContext.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Toast.makeText(mContext, "Bluetooth needs to be started", Toast.LENGTH_SHORT).show();
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
        mContext.runOnUiThread(new Runnable() {
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

    public void sendDirectionPacket(int value) {
        mContext.runOnUiThread(new SendBlePacket(MES_DPAD_CONTROLLER_ID, value));
    }

    public void sendPacket(int eventCode, int value) {
        mContext.runOnUiThread(new SendBlePacket(eventCode, value));
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
                Log.d(TAG, "sendBlePacket: " + Utilities.getHexString(bb.array()));
                mGatt.writeCharacteristic(mEventCharacteristic);
            }
        }
    }

}