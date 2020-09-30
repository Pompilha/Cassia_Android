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
import android.os.Bundle;
import android.os.ParcelUuid;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class CassiaDemoServicesFragment extends ServiceFragment {
  private static final String TAG = CassiaDemoServicesFragment.class.getCanonicalName();

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
  private boolean mHeartRateMeasurementNotifyOn = false; // 心跳测量notify开关
  private int mHeartRate = 60; // 实时心率

  // 公共组件
  private Timer mTimer; // 定时器用于更新数据
  private ServiceFragmentDelegate mDelegate;

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
  public CassiaDemoServicesFragment() {
    createHeartRateService();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {

    // 获取各个控件并注册事件
    View view = inflater.inflate(R.layout.fragment_heart_rate, container, false);
    mEditTextHeartRateMeasurement = (EditText) view.findViewById(R.id.editText_heartRateMeasurementValue);

    // 设置控件默认值

    // 其他初始化动作
    startDataUpdateTimer();

    return view;
  }

  // 控件更新界面
  private Runnable mEditTextHeartRateMeasurementUpdater = new Runnable() {
    @Override
    public void run() {
      mEditTextHeartRateMeasurement.setText(Integer.toString(mHeartRate));
    }
  };

  // 心率定时器处理
  private void heartRateTimerHandler() {
    mHeartRate = Utils.getRandomRange(50, 140);
    if (mHeartRateMeasurementNotifyOn) { // 只有开启notify时才上报数据
      getActivity().runOnUiThread(mEditTextHeartRateMeasurementUpdater);
      mHeartRateMeasurementCharacteristic.setValue(new byte[]{0b00001000, 0, 0, 0});
      mHeartRateMeasurementCharacteristic.setValue(mHeartRate, HEART_RATE_MEASUREMENT_VALUE_FORMAT,/* offset */ 1);
      mDelegate.sendNotificationToDevices(mHeartRateMeasurementCharacteristic); // notify上报数据
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
  public BluetoothGattService[] getBluetoothGattService() {
    return new BluetoothGattService[] { mHeartRateService };
  }

  @Override
  public ParcelUuid getServiceUUID() {
    return new ParcelUuid(HEART_RATE_SERVICE_UUID);
  }

  @Override
  public byte[] getServiceData() {
    return new byte[] { (byte) mHeartRate };
  }

  @Override
  public byte[] getManufacturerData() {
    return Utils.int2bytesBE(10);
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
    if (characteristic.getUuid() != HEART_RATE_MEASUREMENT_UUID) {
      return;
    }
    if (indicate) {
      return;
    }
    // TODO: 不同的notify使用不同的变量控制
    mHeartRateMeasurementNotifyOn = true;
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getActivity(), R.string.notificationsEnabled, Toast.LENGTH_SHORT)
            .show();
      }
    });
  }

  @Override
  public void notificationsDisabled(BluetoothGattCharacteristic characteristic) {
    if (characteristic.getUuid() != HEART_RATE_MEASUREMENT_UUID) {
      return;
    }
    // TODO: 不同的notify使用不同的变量控制
    mHeartRateMeasurementNotifyOn = false;
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getActivity(), R.string.notificationsNotEnabled, Toast.LENGTH_SHORT)
            .show();
      }
    });
  }
}
