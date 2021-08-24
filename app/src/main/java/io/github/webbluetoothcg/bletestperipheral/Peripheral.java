/*
 * Copyright 2015 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.webbluetoothcg.bletestperipheral;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import io.github.webbluetoothcg.bletestperipheral.ServiceFragment.ServiceFragmentDelegate;

public class Peripheral extends Activity implements ServiceFragmentDelegate {

  private static final int REQUEST_ENABLE_BT = 1;
  private static final String TAG = Peripheral.class.getCanonicalName();
  private static final String CURRENT_FRAGMENT_TAG = "CURRENT_FRAGMENT";

  private static final UUID CHARACTERISTIC_USER_DESCRIPTION_UUID = UUID
      .fromString("00002901-0000-1000-8000-00805f9b34fb");
  private static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = UUID
      .fromString("00002902-0000-1000-8000-00805f9b34fb");

  private ServiceFragment mCurrentServiceFragment;
  private HashSet<BluetoothDevice> mBluetoothDevices;
  private BluetoothManager mBluetoothManager;
  private BluetoothAdapter mBluetoothAdapter;
  private AdvertiseData mAdvData;
  private AdvertiseData mAdvScanResponse;
  private AdvertiseSettings mAdvSettings;
  private AdvertisingSetParameters mAdvSetParameters;
  private BluetoothLeAdvertiser mAdvertiser;
  private Timer mTimer;
  private AdvertisingSet mCurrentAdvertisingSet;
  private BluetoothGattService[] mServices;
  private boolean[] mIsServiceAdded;
  private byte[] manufacturerData; // 11B

  private final AdvertisingSetCallback mAdvSetCallback = new AdvertisingSetCallback() {
    @Override
    public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
      mCurrentAdvertisingSet = advertisingSet;
      Log.i(TAG, "advertising set started, status: " + status);
    }

    @Override
    public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
      Log.i(TAG, "advertising set stopped");
    }

    @Override
    public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable, int status) {
      Log.i(TAG, "advertising enabled, status: " + status + ", enable:" + enable);
    }

    @Override
    public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
//      Log.i(TAG, "advertising data set, status: " + status);
    }

    @Override
    public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
      Log.i(TAG, "scan response data set, status: " + status);
    }

    @Override
    public void onAdvertisingParametersUpdated(AdvertisingSet advertisingSet, int txPower, int status) {
      Log.i(TAG, "advertising parameters updated, status: " + status + ", txPower:" + txPower);
    }
  };

  private BluetoothGattServer mGattServer;
  private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
    @Override
    public void onServiceAdded(int status, BluetoothGattService service) {
      super.onServiceAdded(status, service);
      int index = getServiceIndexByUUID(service.getUuid());
      if (index != -1) {
        mIsServiceAdded[index] = true; // 标记此service已添加成功
        Log.i(TAG, "add service ok:" + service.getUuid().toString());
      }
      // 找到下个未添加成功的继续添加
      addServiceOneByOne();
    }

    private int getServiceIndexByUUID(UUID uuid) {
      for (int index = 0; index < mServices.length; index++) {
        if (mServices[index].getUuid().equals(uuid)) {
          return index;
        }
      }
      return -1;
    }

    @Override
    public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
      super.onConnectionStateChange(device, status, newState);
      if (status == BluetoothGatt.GATT_SUCCESS) {
        if (newState == BluetoothGatt.STATE_CONNECTED) {
          mBluetoothDevices.add(device);
          updateConnectedDevicesStatus();
          Log.v(TAG, "Connected to device: " + device.getAddress());
        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
          mBluetoothDevices.remove(device);
          updateConnectedDevicesStatus();
          Log.v(TAG, "Disconnected from device");
        }
      } else {
        mBluetoothDevices.remove(device);
        updateConnectedDevicesStatus();
        // There are too many gatt errors (some of them not even in the documentation) so we just
        // show the error to the user.
        final String errorMessage = getString(R.string.status_errorWhenConnecting) + ": " + status;
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            Toast.makeText(Peripheral.this, errorMessage, Toast.LENGTH_LONG).show();
          }
        });
        Log.e(TAG, "Error when connecting: " + status);
      }
    }

    @Override
    public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
        BluetoothGattCharacteristic characteristic) {
      super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
      Log.d(TAG, "Device tried to read characteristic: " + characteristic.getUuid());
      Log.d(TAG, "Value: " + Arrays.toString(characteristic.getValue()));
      if (offset != 0) {
        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset,
            /* value (optional) */ null);
        return;
      }
      mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
          offset, characteristic.getValue());
    }

    @Override
    public void onNotificationSent(BluetoothDevice device, int status) {
      super.onNotificationSent(device, status);
      Log.v(TAG, "Notification sent. Status: " + status);
    }

    @Override
    public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
        BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
        int offset, byte[] value) {
      super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
          responseNeeded, offset, value);
      Log.v(TAG, "Characteristic Write request: " + Arrays.toString(value));
      int status = mCurrentServiceFragment.writeCharacteristic(characteristic, offset, value);
      if (responseNeeded) {
        mGattServer.sendResponse(device, requestId, status,
            /* No need to respond with an offset */ 0,
            /* No need to respond with a value */ null);
      }
    }

    @Override
    public void onDescriptorReadRequest(BluetoothDevice device, int requestId,
        int offset, BluetoothGattDescriptor descriptor) {
      super.onDescriptorReadRequest(device, requestId, offset, descriptor);
      Log.d(TAG, "Device tried to read descriptor: " + descriptor.getUuid());
      Log.d(TAG, "Value: " + Arrays.toString(descriptor.getValue()));
      if (offset != 0) {
        mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset,
            /* value (optional) */ null);
        return;
      }
      mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
          descriptor.getValue());
    }

    @Override
    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
        BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
        int offset,
        byte[] value) {
      super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded,
          offset, value);
      Log.v(TAG, "Descriptor Write Request " + descriptor.getUuid() + " " + Arrays.toString(value));
      int status = BluetoothGatt.GATT_SUCCESS;
      if (descriptor.getUuid() == CLIENT_CHARACTERISTIC_CONFIGURATION_UUID) {
        BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
        boolean supportsNotifications = (characteristic.getProperties() &
            BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
        boolean supportsIndications = (characteristic.getProperties() &
            BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;

        if (!(supportsNotifications || supportsIndications)) {
          status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
        } else if (value.length != 2) {
          status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
        } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
          status = BluetoothGatt.GATT_SUCCESS;
          mCurrentServiceFragment.notificationsDisabled(characteristic);
          descriptor.setValue(value);
        } else if (supportsNotifications &&
            Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
          status = BluetoothGatt.GATT_SUCCESS;
          mCurrentServiceFragment.notificationsEnabled(characteristic, false /* indicate */);
          descriptor.setValue(value);
        } else if (supportsIndications &&
            Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
          status = BluetoothGatt.GATT_SUCCESS;
          mCurrentServiceFragment.notificationsEnabled(characteristic, true /* indicate */);
          descriptor.setValue(value);
        } else {
          status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
        }
      } else {
        status = BluetoothGatt.GATT_SUCCESS;
        descriptor.setValue(value);
      }
      if (responseNeeded) {
        mGattServer.sendResponse(device, requestId, status,
            /* No need to respond with offset */ 0,
            /* No need to respond with a value */ null);
      }
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_peripherals);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    mBluetoothDevices = new HashSet<>();
    mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
    mBluetoothAdapter = mBluetoothManager.getAdapter();
    if (mBluetoothAdapter != null) mBluetoothAdapter.setName("Cassia Demo App");
    mCurrentServiceFragment = new CassiaDemoDeviceFragment();
    getFragmentManager()
            .beginTransaction()
            .add(R.id.fragment_container, mCurrentServiceFragment, CURRENT_FRAGMENT_TAG)
            .commit();

    mServices = mCurrentServiceFragment.getBluetoothGattServices(); // 获取所有的导出services
    mIsServiceAdded = new boolean[mServices.length];
    for (int index = 0; index < mServices.length; index++) {
      mIsServiceAdded[index] = false;
    }

    mAdvSettings = new AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setConnectable(true)
        .build();
    mAdvSetParameters = new AdvertisingSetParameters.Builder()
      .setLegacyMode(true)
      .setConnectable(true)
      .setScannable(true)
      .setInterval(160)
      .setTxPowerLevel(1).build();
    AdvertiseData.Builder builder = new AdvertiseData.Builder();
    mCurrentServiceFragment.addServiceData2AdvBuilder(builder);

    // 增加固定uid, 前6个字节
    manufacturerData = new byte[11];
    byte[] uidBytes = getUid();
    System.arraycopy(uidBytes, 0, manufacturerData, 0, uidBytes.length);

    builder.addManufacturerData(0xffff, manufacturerData);

    mAdvData = builder.build();
    mBluetoothAdapter.setName("Cassia Demo App");
    mAdvScanResponse = new AdvertiseData.Builder()
        .setIncludeDeviceName(true)
        .build();
  }

  // 获取uid：读取文件，没有的话则生成，并写入文件
  public byte[] getUid() {
    byte[] uid = getUidFromFile();
    if (uid == null) {
      uid = genRandMacBytes();
      saveUidToFile(uid);
    }
    return uid;
  }

  // 生成mac地址，mac地址以1819开头
  public byte[] genRandMacBytes(){
    byte[] arr = new byte[6];
    for (int i = 0; i < 6; i++) {
      arr[i] = (byte)ThreadLocalRandom.current().nextInt(0, 255);
    }
    arr[0] = 0x18;
    arr[1] = 0x19;
    return arr;
  }

  // uid保存到文件
  public void saveUidToFile(byte[] uid) {
    try {
      FileOutputStream outputStream = openFileOutput("cassiaDemoApp.key", Context.MODE_PRIVATE);
      outputStream.write(uid);
      Toast.makeText(this, "Save id success!", Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // 从文件获取uid
  public byte[] getUidFromFile() {
    try {
      FileInputStream inputStream = openFileInput("cassiaDemoApp.key");
      byte[] bytes = new byte[6];
      int hasRead = inputStream.read(bytes);
      return bytes;
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu_peripheral, menu);
    return true /* show menu */;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_ENABLE_BT) {
      if (resultCode == RESULT_OK) {
        if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
          Toast.makeText(this, R.string.bluetoothAdvertisingNotSupported, Toast.LENGTH_LONG).show();
          Log.e(TAG, "Advertising not supported");
        }
        onStart();
      } else {
        //TODO(g-ortuno): UX for asking the user to activate bt
        Toast.makeText(this, R.string.bluetoothNotEnabled, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Bluetooth not enabled");
        finish();
      }
    }
  }


  @Override
  protected void onStart() {
    super.onStart();
    resetStatusViews();
    // If the user disabled Bluetooth when the app was in the background,
    // openGattServer() will return null.
    mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
    if (mGattServer == null) {
      ensureBleFeaturesAvailable();
      return;
    }
    // Add a service for a total of three services (Generic Attribute and Generic Access
    // are present by default).
    if (mServices != null && mServices.length > 0) {
      mGattServer.addService(mServices[0]);
    }

    if (mBluetoothAdapter.isMultipleAdvertisementSupported()) {
      mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
      mAdvertiser.startAdvertisingSet(mAdvSetParameters, mAdvData, mAdvScanResponse, null, null,
              0, 0, mAdvSetCallback);
      startAdDataUpdateTimer();
    }
  }

  // 从services中获取未加入的，加入之
  private boolean addServiceOneByOne() {
    for (int index = 0; index < mIsServiceAdded.length; index++) {
      if (!mIsServiceAdded[index] && mGattServer != null) {
        return mGattServer.addService(mServices[index]);
      }
    }
    return false;
  }

  // 定时器方式停止广播、更新广播数据、开启广播
  private void startAdDataUpdateTimer() {
    mTimer = new Timer();
    mTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        if (mCurrentAdvertisingSet != null) {
          AdvertiseData.Builder builder = new AdvertiseData.Builder();
          mCurrentServiceFragment.addServiceData2AdvBuilder(builder);
          builder.addManufacturerData(0xffff, manufacturerData);
          AdvertiseData advData = builder.build();
          mCurrentAdvertisingSet.setAdvertisingData(advData);
        }
      }
    }, 0 /* delay */,  500);
  }

  private void cancelTimer() {
    if (mTimer != null) {
      mTimer.cancel();
    }
  }

  private void resetTimer() {
    cancelTimer();
    startAdDataUpdateTimer();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.action_disconnect_devices) {
      disconnectFromDevices();
      return true /* event_consumed */;
    }
    return false /* event_consumed */;
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (mGattServer != null) {
      mGattServer.close();
    }
    if (mBluetoothAdapter.isEnabled() && mAdvertiser != null) {
      cancelTimer();
      mAdvertiser.stopAdvertisingSet(mAdvSetCallback);
    }
    resetStatusViews();
  }

  @Override
  public void sendNotificationToDevices(BluetoothGattCharacteristic characteristic) {
    boolean indicate = (characteristic.getProperties()
        & BluetoothGattCharacteristic.PROPERTY_INDICATE)
        == BluetoothGattCharacteristic.PROPERTY_INDICATE;
    for (BluetoothDevice device : mBluetoothDevices) {
      // true for indication (acknowledge) and false for notification (unacknowledge).
      mGattServer.notifyCharacteristicChanged(device, characteristic, indicate);
    }
  }

  private void resetStatusViews() {
    updateConnectedDevicesStatus();
  }

  private void updateConnectedDevicesStatus() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (mCurrentServiceFragment != null) {
          List<BluetoothDevice> list = mBluetoothManager.getConnectedDevices(BluetoothGattServer.GATT);
          mCurrentServiceFragment.updateUIConnected(list.size() > 0 ? ("Connected To " + list.get(0).getAddress()) : "");
        }
      }
    });
  }

  public static BluetoothGattDescriptor getClientCharacteristicConfigurationDescriptor() {
    BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
        CLIENT_CHARACTERISTIC_CONFIGURATION_UUID,
        (BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
    descriptor.setValue(new byte[]{0, 0});
    return descriptor;
  }

  public static BluetoothGattDescriptor getCharacteristicUserDescriptionDescriptor(String defaultValue) {
    BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
        CHARACTERISTIC_USER_DESCRIPTION_UUID,
        (BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
    try {
      descriptor.setValue(defaultValue.getBytes("UTF-8"));
    } finally {
      return descriptor;
    }
  }

  private void ensureBleFeaturesAvailable() {
    if (mBluetoothAdapter == null) {
      Toast.makeText(this, R.string.bluetoothNotSupported, Toast.LENGTH_LONG).show();
      Log.e(TAG, "Bluetooth not supported");
      finish();
    } else if (!mBluetoothAdapter.isEnabled()) {
      // Make sure bluetooth is enabled.
      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
      if (mBluetoothAdapter != null) {
        mBluetoothAdapter.setName("Cassia Demo App");
      }
    }
  }
  private void disconnectFromDevices() {
    Log.d(TAG, "Disconnecting devices...");
    for (BluetoothDevice device : mBluetoothManager.getConnectedDevices(
        BluetoothGattServer.GATT)) {
      Log.d(TAG, "Devices: " + device.getAddress() + " " + device.getName());
      mGattServer.cancelConnection(device);
    }
  }
}
