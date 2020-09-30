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
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseData;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class CassiaDemoDeviceFragment extends ServiceFragment {
  private static final String TAG = CassiaDemoDeviceFragment.class.getCanonicalName();

  // Current Time Service
  private static final UUID CURRENT_TIME_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
  

  // HeartRate Service UI
  private EditText mEditTextHeartRateMeasurement;

  // HeartRate Service
  private static final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
  private static final UUID HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
  private static final String HEART_RATE_MEASUREMENT_DESCRIPTION = "Used to send a heart rate measurement";
  private static final int HEART_RATE_MEASUREMENT_VALUE_FORMAT = BluetoothGattCharacteristic.FORMAT_UINT8;

  // HeartRate vars
  private BluetoothGattService mHeartRateService;
  private BluetoothGattCharacteristic mHeartRateMeasurementCharacteristic;
  private boolean mHeartRateMeasurementNotifyOn = false; // 测量notify开关
  private int mHeartRateMeasurementValue = 60; // 实时心率

  // HealthThermometer UI
  private EditText mEditTextTemperatureMeasurement;

  // HealthThermometer Service
  private static final UUID HEALTH_THERMOMETER_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
  private static final UUID TEMPERATURE_MEASUREMENT_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb");
  private static final String TEMPERATURE_MEASUREMENT_DESCRIPTION = "This characteristic is used to send a temperature measurement.";

  // HealthThermometer vars
  private BluetoothGattService mHealthThermometerService;
  private BluetoothGattCharacteristic mTemperatureMeasurementCharacteristic;
  private boolean mTemperatureMeasurementNotifyOn = false; // 测量notify开关
  private int mTemperatureMeasurementValue = 3600; // 实时体温


  // 公共组件
  private Timer mTimer; // 定时器用于更新数据
  private ServiceFragmentDelegate mDelegate;

  // Temperature Service初始化
  public void createHealthThermometerService() {
    mTemperatureMeasurementCharacteristic = new BluetoothGattCharacteristic(TEMPERATURE_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_INDICATE, 0);
    mTemperatureMeasurementCharacteristic.addDescriptor(
            Peripheral.getClientCharacteristicConfigurationDescriptor());
    mTemperatureMeasurementCharacteristic.addDescriptor(
            Peripheral.getCharacteristicUserDescriptionDescriptor(TEMPERATURE_MEASUREMENT_DESCRIPTION));
    mHealthThermometerService = new BluetoothGattService(HEALTH_THERMOMETER_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY);
    mHealthThermometerService.addCharacteristic(mTemperatureMeasurementCharacteristic);
  }

  // HeartRate Service初始化
  public void createHeartRateService() {
    mHeartRateMeasurementCharacteristic = new BluetoothGattCharacteristic(HEART_RATE_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY, /* No permissions */ 0);
    mHeartRateMeasurementCharacteristic.addDescriptor(
            Peripheral.getClientCharacteristicConfigurationDescriptor());
    mHeartRateMeasurementCharacteristic.addDescriptor(
            Peripheral.getCharacteristicUserDescriptionDescriptor(HEART_RATE_MEASUREMENT_DESCRIPTION));
    mHeartRateService = new BluetoothGattService(HEART_RATE_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY);
    mHeartRateService.addCharacteristic(mHeartRateMeasurementCharacteristic);
  }

  // 构造函数
  public CassiaDemoDeviceFragment() {
    createHeartRateService();
    createHealthThermometerService();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {

    // 获取各个控件并注册事件
    View view = inflater.inflate(R.layout.fragment_cassia_demo_device, container, false);
    mEditTextHeartRateMeasurement = (EditText) view.findViewById(R.id.editText_heartRateMeasurementValue);
    mEditTextTemperatureMeasurement = (EditText) view.findViewById(R.id.editText_temperatureMeasurementValue);

    // 设置控件默认值

    // 其他初始化动作
    startDataUpdateTimer();

    return view;
  }

  // 控件更新界面
  private Runnable mEditTextHeartRateMeasurementUpdater = new Runnable() {
    @Override
    public void run() {
      mEditTextHeartRateMeasurement.setText(Integer.toString(mHeartRateMeasurementValue));
    }
  };

  // 控件更新界面
  private Runnable mEditTextTemperatureMeasurementUpdater = new Runnable() {
    @Override
    public void run() {
      float value = (float) mTemperatureMeasurementValue / 100;
      mEditTextTemperatureMeasurement.setText(Float.toString(value));
    }
  };

  // GATT设置心率
  private void gattSetHeartRateMeasurementValue(int value) {
    mHeartRateMeasurementCharacteristic.setValue(new byte[]{0b00001000, 0, 0, 0});
    mHeartRateMeasurementCharacteristic.setValue(value, HEART_RATE_MEASUREMENT_VALUE_FORMAT,/* offset */ 1);
  }

  // GATT设置温度
  private void gattSetTemperatureMeasurementValue(int temperatureMeasurementValue) {
    mTemperatureMeasurementCharacteristic.setValue(new byte[]{0b00000000, 0, 0, 0, 0});
    // 点乘以10N次方，3600 * 10 ^ -2 = 36.00
    mTemperatureMeasurementCharacteristic.setValue(temperatureMeasurementValue, -2, BluetoothGattCharacteristic.FORMAT_FLOAT,/* offset */ 1);
  }

  // 心率定时器处理: 生成随机数 -> gatt更新 -> 更新控件 -> 发送通知
  private void heartRateTimerHandler() {
    mHeartRateMeasurementValue = Utils.getRandomRange(50, 140);
    if (mHeartRateMeasurementNotifyOn) { // 只有开启notify时才上报数据
      gattSetHeartRateMeasurementValue(mHeartRateMeasurementValue);
      getActivity().runOnUiThread(mEditTextHeartRateMeasurementUpdater);
      mDelegate.sendNotificationToDevices(mHeartRateMeasurementCharacteristic); // notify上报数据
    }
  }

  // 体温定时器处理：生成随机数 -> gatt更新 -> 更新控件 -> 发送通知
  private void temperatureTimerHandler() {
    mTemperatureMeasurementValue = Utils.getRandomRange(3500, 4000);
    if (mTemperatureMeasurementNotifyOn) {
      gattSetTemperatureMeasurementValue(mTemperatureMeasurementValue);
       getActivity().runOnUiThread(mEditTextTemperatureMeasurementUpdater);
      mDelegate.sendNotificationToDevices(mTemperatureMeasurementCharacteristic); // notify上报数据
    }
  }

  // 每500毫秒更新一次，生成随机心率和体温
  // 心率：生成[50, 140]范围内的随机数，定时器方式更新心率数据并Notify发送
  private void startDataUpdateTimer() {
    mTimer = new Timer();
    mTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        heartRateTimerHandler();
        temperatureTimerHandler();
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
    startDataUpdateTimer();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      mDelegate = (ServiceFragmentDelegate) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString()
          + " must implement ServiceFragmentDelegate");
    }
  }

  @Override
  public void onDetach() {
    cancelTimer();
    super.onDetach();
    mDelegate = null;
  }

  @Override
  public BluetoothGattService[] getBluetoothGattServices() {
    return new BluetoothGattService[] { mHeartRateService, mHealthThermometerService };
  }

  @Override
  public void addServiceData2AdvBuilder(AdvertiseData.Builder builder) {
    builder.addServiceData(ParcelUuid.fromString(TEMPERATURE_MEASUREMENT_UUID.toString()),
            Utils.int2bytesBE(mTemperatureMeasurementValue));
    byte[] heartrateBytes = new byte[] { (byte)(mHeartRateMeasurementValue & 0xFF) };
    builder.addServiceData(ParcelUuid.fromString(HEART_RATE_MEASUREMENT_UUID.toString()), heartrateBytes);
  }

  @Override
  public int writeCharacteristic(BluetoothGattCharacteristic characteristic, int offset, byte[] value) {
    if (offset != 0) {
      return BluetoothGatt.GATT_INVALID_OFFSET;
    }
    return BluetoothGatt.GATT_SUCCESS;
  }

  @Override
  public void notificationsEnabled(BluetoothGattCharacteristic characteristic, boolean indicate) {
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getActivity(), R.string.notificationsEnabled, Toast.LENGTH_SHORT)
                .show();
      }
    });

    // 更新notify开关
    if (characteristic.getUuid() == HEART_RATE_MEASUREMENT_UUID) {
      mHeartRateMeasurementNotifyOn = true;
    } else if (characteristic.getUuid() == TEMPERATURE_MEASUREMENT_UUID) {
      mTemperatureMeasurementNotifyOn = true;
    }
  }

  @Override
  public void notificationsDisabled(BluetoothGattCharacteristic characteristic) {
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getActivity(), R.string.notificationsNotEnabled, Toast.LENGTH_SHORT)
            .show();
      }
    });
    // 更新notify开关
    if (characteristic.getUuid() == HEART_RATE_MEASUREMENT_UUID) {
      mHeartRateMeasurementNotifyOn = false;
    } else if (characteristic.getUuid() == TEMPERATURE_MEASUREMENT_UUID) {
      mTemperatureMeasurementNotifyOn = false;
    }
  }
}
