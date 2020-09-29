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
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.AdvertiseData;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
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

public class HeartRateServiceFragment extends ServiceFragment {
  private static final String TAG = HeartRateServiceFragment.class.getCanonicalName();
  private static final int MIN_UINT = 0;
  private static final int MAX_UINT8 = (int) Math.pow(2, 8) - 1;
  private static final int MAX_UINT16 = (int) Math.pow(2, 16) - 1;
  /**
   * See
   * <a href="https://www.bluetooth.com/specifications/gatt/services/">Heart Rate Service</a>
   * <a href="https://www.bluetooth.com/specifications/gatt/characteristics/">Characteristics</a>
   */
  private static final UUID HEART_RATE_SERVICE_UUID = UUID
      .fromString("0000180D-0000-1000-8000-00805f9b34fb");

  /**
   * See <a href="https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml">
   * Heart Rate Measurement</a>
   */
  private static final UUID HEART_RATE_MEASUREMENT_UUID = UUID
      .fromString("00002A37-0000-1000-8000-00805f9b34fb");
  private static final int HEART_RATE_MEASUREMENT_VALUE_FORMAT = BluetoothGattCharacteristic.FORMAT_UINT8;
  private static final int INITIAL_HEART_RATE_MEASUREMENT_VALUE = 60;
  private static final int EXPENDED_ENERGY_FORMAT = BluetoothGattCharacteristic.FORMAT_UINT16;
  private static final int INITIAL_EXPENDED_ENERGY = 0;
  private static final String HEART_RATE_MEASUREMENT_DESCRIPTION = "Used to send a heart rate " +
      "measurement";

  /**
   * See <a href="https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.body_sensor_location.xml">
   * Body Sensor Location</a>
   */
  private static final UUID BODY_SENSOR_LOCATION_UUID = UUID
      .fromString("00002A38-0000-1000-8000-00805f9b34fb");
  private static final int LOCATION_OTHER = 0;

  /**
   * See <a href="https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_control_point.xml">
   * Heart Rate Control Point</a>
   */
  private static final UUID HEART_RATE_CONTROL_POINT_UUID = UUID
      .fromString("00002A39-0000-1000-8000-00805f9b34fb");

  private BluetoothGattService mHeartRateService;
  private BluetoothGattCharacteristic mHeartRateMeasurementCharacteristic;
  private BluetoothGattCharacteristic mBodySensorLocationCharacteristic;
  private BluetoothGattCharacteristic mHeartRateControlPoint;

  // 增加自定义char，支持校时、发送短信
  private static final UUID USER_DEFINE_SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
  private static final UUID USER_DEFINE_CHAR_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");

  private BluetoothGattService mUserDefineService;
  private BluetoothGattCharacteristic mUserDefineChar;
  private String mDateTime;
  private String mMessage;

  private boolean mNotifyOn = false;
  private int mHeartRate = INITIAL_HEART_RATE_MEASUREMENT_VALUE;
  private int mTemperature = 3680;
  private Timer mTimer;

  private ServiceFragmentDelegate mDelegate;

  private EditText mEditTextHeartRateMeasurement;

  private EditText mEditDateTime;
  private EditText mEditMessage;

  private final OnEditorActionListener mOnEditorActionListenerHeartRateMeasurement = new OnEditorActionListener() {
    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        String newHeartRateMeasurementValueString = textView.getText().toString();
        if (isValidCharacteristicValue(newHeartRateMeasurementValueString,
            HEART_RATE_MEASUREMENT_VALUE_FORMAT)) {
          int newHeartRateMeasurementValue = Integer.parseInt(newHeartRateMeasurementValueString);
          mHeartRateMeasurementCharacteristic.setValue(newHeartRateMeasurementValue,
              HEART_RATE_MEASUREMENT_VALUE_FORMAT,
              /* offset */ 1);
        } else {
          Toast.makeText(getActivity(), R.string.heartRateMeasurementValueInvalid,
              Toast.LENGTH_SHORT).show();
        }
      }
      return false;
    }
  };

  private final OnEditorActionListener mOnEditorActionListenerEnergyExpended = new OnEditorActionListener() {
    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        String newEnergyExpendedString = textView.getText().toString();
        if (isValidCharacteristicValue(newEnergyExpendedString,
            EXPENDED_ENERGY_FORMAT)) {
          int newEnergyExpended = Integer.parseInt(newEnergyExpendedString);
          mHeartRateMeasurementCharacteristic.setValue(newEnergyExpended,
              EXPENDED_ENERGY_FORMAT,
              /* offset */ 2);
        } else {
          Toast.makeText(getActivity(), R.string.energyExpendedInvalid,
              Toast.LENGTH_SHORT).show();
        }
      }
      return false;
    }
  };
  private EditText mEditTextEnergyExpended;
  private Spinner mSpinnerBodySensorLocation;

  private final OnItemSelectedListener mLocationSpinnerOnItemSelectedListener =
      new OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          setBodySensorLocationValue(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
      };

  private final OnClickListener mNotifyButtonListener = new OnClickListener() {
    @Override
    public void onClick(View v) {
      mDelegate.sendNotificationToDevices(mHeartRateMeasurementCharacteristic);
    }
  };

  public HeartRateServiceFragment() {
    mHeartRateMeasurementCharacteristic =
        new BluetoothGattCharacteristic(HEART_RATE_MEASUREMENT_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            /* No permissions */ 0);

    mHeartRateMeasurementCharacteristic.addDescriptor(
        Peripheral.getClientCharacteristicConfigurationDescriptor());

    mHeartRateMeasurementCharacteristic.addDescriptor(
        Peripheral.getCharacteristicUserDescriptionDescriptor(HEART_RATE_MEASUREMENT_DESCRIPTION));

    mBodySensorLocationCharacteristic =
        new BluetoothGattCharacteristic(BODY_SENSOR_LOCATION_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ);

    mHeartRateControlPoint =
        new BluetoothGattCharacteristic(HEART_RATE_CONTROL_POINT_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE);

    mHeartRateService = new BluetoothGattService(HEART_RATE_SERVICE_UUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY);
    mHeartRateService.addCharacteristic(mHeartRateMeasurementCharacteristic);
    mHeartRateService.addCharacteristic(mBodySensorLocationCharacteristic);
    mHeartRateService.addCharacteristic(mHeartRateControlPoint);

    // 增加自定义char，只支持写入时间、短信
    mUserDefineChar = new BluetoothGattCharacteristic(USER_DEFINE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE);
    mUserDefineService = new BluetoothGattService(USER_DEFINE_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY);
    mUserDefineService.addCharacteristic(mUserDefineChar);
  }

  // 生成[min, max]范围内的随机数
  public int getRandomRange(int min, int max) {
    Random random = new Random();
    return random.nextInt(max) % (max - min + 1) + min;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {

    // 获取各个控件并注册事件
    View view = inflater.inflate(R.layout.fragment_heart_rate, container, false);
    mSpinnerBodySensorLocation = (Spinner) view.findViewById(R.id.spinner_bodySensorLocation);
    mSpinnerBodySensorLocation.setOnItemSelectedListener(mLocationSpinnerOnItemSelectedListener);

    // 设置下拉列表为禁用状态
    mSpinnerBodySensorLocation.setEnabled(false);

    mEditTextHeartRateMeasurement = (EditText) view
        .findViewById(R.id.editText_heartRateMeasurementValue);
    mEditTextHeartRateMeasurement
        .setOnEditorActionListener(mOnEditorActionListenerHeartRateMeasurement);
    mEditTextEnergyExpended = (EditText) view
        .findViewById(R.id.editText_energyExpended);
    mEditTextEnergyExpended
        .setOnEditorActionListener(mOnEditorActionListenerEnergyExpended);
    Button notifyButton = (Button) view.findViewById(R.id.button_heartRateMeasurementNotify);
    notifyButton.setOnClickListener(mNotifyButtonListener);

    // User Define Service相关控件
    mEditDateTime = (EditText) view.findViewById(R.id.editText_dateTimeValue);
    mEditMessage = (EditText) view.findViewById(R.id.editText_messageValue);

    // 设置控件默认值
    setBodySensorLocationValue(LOCATION_OTHER);

    // 其他初始化动作
    startDataUpdateTimer();

    return view;
  }

  // 每秒更新一次，生成随机心率和体温
  // 心率：生成[50, 140]范围内的随机数，定时器方式更新心率数据并Notify发送
  // 体温：生成[3500, 4000]范围内的随机数，定时器方式更新体温数据并广播
  private void startDataUpdateTimer() {
    mTimer = new Timer();
    mTimer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        mHeartRate = getRandomRange(50, 140);
        mTemperature = getRandomRange(3500, 4000);
        setHeartRateMeasurementValue(INITIAL_EXPENDED_ENERGY);
        if (mNotifyOn) { // 只有开启notify时才上报数据
          mDelegate.sendNotificationToDevices(mHeartRateMeasurementCharacteristic);
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
    return new BluetoothGattService[] { mHeartRateService, mUserDefineService };
//    return new BluetoothGattService[] { mUserDefineService };
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
    return int2bytesBE(mTemperature);
  }

  // int 转 byte数组，大端
  public static byte[] int2bytesBE(int n) {
    byte[] b = new byte[4];
    b[3] = (byte) (n & 0xff);
    b[2] = (byte) (n >> 8 & 0xff);
    b[1] = (byte) (n >> 16 & 0xff);
    b[0] = (byte) (n >> 24 & 0xff);
    return b;
  }

  // byte[2]转short，大端
  public static short bytes2shortBE(byte[] bytes) {
    return (short) ((bytes[0] & 0xff) << 8 | (bytes[1] & 0xff));
  }

  private void setHeartRateMeasurementValue(int expendedEnergy) {

    Log.d(TAG, Arrays.toString(mHeartRateMeasurementCharacteristic.getValue()));
    /* Set the org.bluetooth.characteristic.heart_rate_measurement
     * characteristic to a byte array of size 4 so
     * we can use setValue(value, format, offset);
     *
     * Flags (8bit) + Heart Rate Measurement Value (uint8) + Energy Expended (uint16) = 4 bytes
     *
     * Flags = 1 << 3:
     *   Heart Rate Format (0) -> UINT8
     *   Sensor Contact Status (00) -> Not Supported
     *   Energy Expended (1) -> Field Present
     *   RR-Interval (0) -> Field not pressent
     *   Unused (000)
     */
    mHeartRateMeasurementCharacteristic.setValue(new byte[]{0b00001000, 0, 0, 0});
    // Characteristic Value: [flags, 0, 0, 0]
    mHeartRateMeasurementCharacteristic.setValue(mHeartRate,
        HEART_RATE_MEASUREMENT_VALUE_FORMAT,
        /* offset */ 1);
    // Characteristic Value: [flags, heart rate value, 0, 0]
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mEditTextHeartRateMeasurement.setText(Integer.toString(mHeartRate));
//        mEditTextEnergyExpended.setText(Integer.toString(expendedEnergy));
      }
    });
    mHeartRateMeasurementCharacteristic.setValue(expendedEnergy,
        EXPENDED_ENERGY_FORMAT,
        /* offset */ 2);
    // Characteristic Value: [flags, heart rate value, energy expended (LSB), energy expended (MSB)]
  }
  private void setBodySensorLocationValue(int location) {
    mBodySensorLocationCharacteristic.setValue(new byte[]{(byte) location});
    mSpinnerBodySensorLocation.setSelection(location);
  }

  private boolean isValidCharacteristicValue(String s, int format) {
    try {
      int value = Integer.parseInt(s);
      if (format == BluetoothGattCharacteristic.FORMAT_UINT8) {
        return (value >= MIN_UINT) && (value <= MAX_UINT8);
      } else if (format == BluetoothGattCharacteristic.FORMAT_UINT16) {
        return (value >= MIN_UINT) && (value <= MAX_UINT16);
      } else {
        throw new IllegalArgumentException(format + " is not a valid argument");
      }
    } catch (NumberFormatException e) {
      return false;
    }
  }

  @Override
  public int writeCharacteristic(BluetoothGattCharacteristic characteristic, int offset, byte[] value) {
    if (offset != 0) {
      return BluetoothGatt.GATT_INVALID_OFFSET;
    }
    if (characteristic.getUuid() == HEART_RATE_MEASUREMENT_UUID) {
        // Heart Rate control point is a 8bit characteristic
        if (value.length != 1) {
            return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
        }
        if ((value[0] & 1) == 1) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mHeartRateMeasurementCharacteristic.setValue(INITIAL_EXPENDED_ENERGY,
                            EXPENDED_ENERGY_FORMAT, /* offset */ 2);
                    mEditTextEnergyExpended.setText(Integer.toString(INITIAL_EXPENDED_ENERGY));
                }
            });
        }
    } else if (characteristic.getUuid() == USER_DEFINE_CHAR_UUID) {
        // cassia自定义命令: 第1个字节为命令字, 0x01 - 设置时间, 0x02 - 发送短信
        // 设置时间: 07e4091d010101，年月日时分秒
        // 发送短信：48656c6c6f20576f726c6421，Hello World!
        byte cmd = value[0];
        byte[] payload = new byte[value.length - 1];
        System.arraycopy(value, 1, payload, 0, payload.length);
        if (cmd == 0x01) {
          if (payload.length != 7) {
            return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
          }
          byte[] yearBytes = Arrays.copyOfRange(payload, 0, 2);
          mDateTime = String.format("%4d-%02d-%02d %02d:%02d:%02d",
                  bytes2shortBE(yearBytes), payload[2], payload[3], payload[4], payload[5], payload[6]);
          getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mEditDateTime.setText(mDateTime);
            }
          });
        } else if (cmd == 0x02) {
          mMessage = new String(payload, StandardCharsets.UTF_8);
          getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mEditMessage.setText(mMessage);
            }
          });
        }
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
    mNotifyOn = true;
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
    mNotifyOn = false;
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getActivity(), R.string.notificationsNotEnabled, Toast.LENGTH_SHORT)
            .show();
      }
    });
  }
}
