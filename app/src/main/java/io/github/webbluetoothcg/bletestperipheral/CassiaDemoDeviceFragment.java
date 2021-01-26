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
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import com.biansemao.widget.ThermometerView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.renderer.YAxisRenderer;

public class CassiaDemoDeviceFragment extends ServiceFragment {
    private static final String TAG = CassiaDemoDeviceFragment.class.getCanonicalName();

    private ArrayList<BluetoothGattService> mServices = new ArrayList<>();

    // Alert Notification Service UI
    private TextView viewNewAlert;

    // Alert Notification Service
    private static final UUID ALERT_NOTIFICATION_SERVICE_UUID = UUID.fromString("00001811-0000-1000-8000-00805f9b34fb");
    private static final UUID NEW_ALERT_CHAR_UUID = UUID.fromString("00002A46-0000-1000-8000-00805f9b34fb");
    private static final String NEW_ALERT_DESCRIPTION = "Used to send SMS";

    // New Alert Vars
    private BluetoothGattCharacteristic mNewAlertChar;
    private byte[] mNewAlertCharValue = new byte[20]; // 默认为没有短信
    private boolean mNewAlertNotifyOn = false; // notify开关
  /*
  // New Alert要求返回的格式: https://www.bluetooth.com/wp-content/uploads/Sitecore-Media-Library/Gatt/Xml/Characteristics/org.bluetooth.characteristic.new_alert.xml
  // 示例：050148656c6c6f2c20576f726c6421 -> Hello, World!
  // [0]: categoryId
  // [1]: numberOfNewAlert
  // [2+]: utfs消息提示内容
  //
  struct org.bluetooth.characteristic.new_alert{
    struct org.bluetooth.characteristic.alert_category_id {
      uint8 categoryId; // 默认使用05，为SMS短信
    };
    uint8 numberOfNewAlert; // 默认使用01，暂认为只有1条
    utf8s textStringInformation; // length [0, 18]
  };
   */

    // Alert Notification Service UI
    private TextView viewCurrentTime;

    // Current Time Service
    private static final UUID CURRENT_TIME_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    private static final UUID CURRENT_TIME_CHAR_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb");

    // Current Time Vars
    private BluetoothGattCharacteristic mCurrentTimeChar;
    private byte[] mCurrentTimeCharValue = new byte[10];
  /*
  // Current Time Char 要求返回的时间格式
  // 示例：2020/10/08 20:06:10 -> e4070a0814060a000000
  // [0-1] year
  // [2] month
  // [3] day
  // [4] hour
  // [5] minute
  // [6] second
  // [7] dayOfWeek
  // [8] fractions256
  // [9] adjustReason
  struct {
    org.bluetooth.characteristic.exact_time_256 exactTime256 {
      org.bluetooth.characteristic.day_date_time dayDateTime {
        org.bluetooth.characteristic.date_time dateTime {
          uint16 year;
          uint8 month;
          uint8 day;
          uint8 hour;
          uint8 minute;
          uint8 second;
        };
        org.bluetooth.characteristic.day_of_week dayOfWeek {
          uint8 dayOfWeek;
        };
      };
      uint8 Fractions256;
    };
    uint8 adjustReason;
  }
  */

    // HeartRate Service UI
    private TextView viewHeartRateMeasurement;

    // HeartRate Service
    private static final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private static final UUID HEART_RATE_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
    private static final String HEART_RATE_MEASUREMENT_DESCRIPTION = "Used to send a heart rate measurement";
    private static final int HEART_RATE_MEASUREMENT_VALUE_FORMAT = BluetoothGattCharacteristic.FORMAT_UINT8;

    // HeartRate vars
    private BluetoothGattCharacteristic mHeartRateMeasurementCharacteristic;
    private boolean mHeartRateMeasurementNotifyOn = false; // 测量notify开关
    private int mHeartRateMeasurementValue = 60; // 实时心率

    // HealthThermometer UI
    private ThermometerView viewTemperatureMeasurement;
    private LineChart viewHeartRateChart;
    List<Entry> viewHeartRateChartEntries;
    public long viewHeartRateCounter;

    // HealthThermometer Service
    private static final UUID HEALTH_THERMOMETER_SERVICE_UUID = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb");
    private static final UUID TEMPERATURE_MEASUREMENT_UUID = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb");
    private static final String TEMPERATURE_MEASUREMENT_DESCRIPTION = "This characteristic is used to send a temperature measurement.";

    // HealthThermometer vars
    private BluetoothGattCharacteristic mTemperatureMeasurementCharacteristic;
    private boolean mTemperatureMeasurementNotifyOn = false; // 测量notify开关
    private int mTemperatureMeasurementValue = 3600; // 实时体温


    // 公共组件
    private Timer mTimer; // 定时器用于更新数据
    private ServiceFragmentDelegate mDelegate;

    // Alert Notification Service初始化
    public void createAlertNotification() {
        mNewAlertChar = new BluetoothGattCharacteristic(NEW_ALERT_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        mNewAlertChar.addDescriptor(
                Peripheral.getClientCharacteristicConfigurationDescriptor());
        //mNewAlertChar.addDescriptor(
        //        Peripheral.getCharacteristicUserDescriptionDescriptor(NEW_ALERT_DESCRIPTION));
        mNewAlertChar.setValue(mNewAlertCharValue);
        BluetoothGattService alertNotificationService = new BluetoothGattService(ALERT_NOTIFICATION_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        alertNotificationService.addCharacteristic(mNewAlertChar);
        mServices.add(alertNotificationService);
    }

    // Current Time Service初始化
    public void createCurrentTimeService() {
        // TODO: notify是否需要补充增加，按照SIG的定义notify属性是必须要定义的
        mCurrentTimeChar = new BluetoothGattCharacteristic(CURRENT_TIME_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        mCurrentTimeChar.setValue(mCurrentTimeCharValue);
        BluetoothGattService currentTimeService = new BluetoothGattService(CURRENT_TIME_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        currentTimeService.addCharacteristic(mCurrentTimeChar);
        mServices.add(currentTimeService);
    }

    // Temperature Service初始化
    public void createHealthThermometerService() {
        mTemperatureMeasurementCharacteristic = new BluetoothGattCharacteristic(TEMPERATURE_MEASUREMENT_UUID,
                BluetoothGattCharacteristic.PROPERTY_INDICATE, 0);
        mTemperatureMeasurementCharacteristic.addDescriptor(
                Peripheral.getClientCharacteristicConfigurationDescriptor());
        //mTemperatureMeasurementCharacteristic.addDescriptor(
        //        Peripheral.getCharacteristicUserDescriptionDescriptor(TEMPERATURE_MEASUREMENT_DESCRIPTION));
        BluetoothGattService healthThermometerService = new BluetoothGattService(HEALTH_THERMOMETER_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        healthThermometerService.addCharacteristic(mTemperatureMeasurementCharacteristic);
        mServices.add(healthThermometerService);
    }

    // HeartRate Service初始化
    public void createHeartRateService() {
        mHeartRateMeasurementCharacteristic = new BluetoothGattCharacteristic(HEART_RATE_MEASUREMENT_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY, /* No permissions */ 0);
        mHeartRateMeasurementCharacteristic.addDescriptor(
                Peripheral.getClientCharacteristicConfigurationDescriptor());
        //mHeartRateMeasurementCharacteristic.addDescriptor(
        //        Peripheral.getCharacteristicUserDescriptionDescriptor(HEART_RATE_MEASUREMENT_DESCRIPTION));
        BluetoothGattService heartRateService = new BluetoothGattService(HEART_RATE_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        heartRateService.addCharacteristic(mHeartRateMeasurementCharacteristic);
        mServices.add(heartRateService);
    }

    // 构造函数
    public CassiaDemoDeviceFragment() {
        createCurrentTimeService();
        createHeartRateService();
        createHealthThermometerService();
        createAlertNotification();
    }

    public void createHeartRateChart() {
        viewHeartRateChartEntries = new ArrayList<Entry>();
        viewHeartRateCounter = 0;
        LineDataSet dataSet = new LineDataSet(viewHeartRateChartEntries, "");
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        dataSet.setColor(Color.parseColor("#ffff696a"));
        dataSet.setLineWidth(2);
        LineData lineData = new LineData(dataSet);
        viewHeartRateChart.setData(lineData);

        viewHeartRateChart.getLegend().setEnabled(false);
        viewHeartRateChart.getDescription().setEnabled(false);
        XAxis xAxis = viewHeartRateChart.getXAxis();
        xAxis.setTextColor(Color.parseColor("#ffbebebe"));
        xAxis.setAxisLineColor(Color.parseColor("#ffbebebe"));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        xAxis.setAxisLineWidth(1f);
        YAxis leftYAxis = viewHeartRateChart.getAxisLeft();
        YAxis rightYAxis = viewHeartRateChart.getAxisRight();
        leftYAxis.setDrawAxisLine(false);
        rightYAxis.setEnabled(false);
        leftYAxis.setAxisMinimum(50);
        leftYAxis.setAxisMaximum(200);
        leftYAxis.setDrawTopYLabelEntry(true);
        leftYAxis.setGranularity(50);
        leftYAxis.setAxisLineColor(Color.parseColor("#ffbebebe"));
        leftYAxis.setTextColor(Color.parseColor("#ffbebebe"));
        leftYAxis.setGridDashedLine(new DashPathEffect(new float[]{10,5,10,5},0));
        leftYAxis.setZeroLineColor(Color.parseColor("#ffbebebe"));
    }

    public void updateHeartRateChart(long timestamp, int heartrate) {
        if (viewHeartRateChartEntries.size() > 120) {
            viewHeartRateChartEntries.remove(0);
        }
        viewHeartRateChartEntries.add(new Entry(viewHeartRateCounter++, heartrate));
        LineDataSet dataSet = new LineDataSet(viewHeartRateChartEntries, "");
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        dataSet.setColor(Color.parseColor("#ffff696a"));
        dataSet.setLineWidth(2);
        LineData lineData = new LineData(dataSet);
        viewHeartRateChart.setData(lineData);
        viewHeartRateChart.notifyDataSetChanged();
        viewHeartRateChart.invalidate(); // refresh
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // 获取各个控件并注册事件
        View view = inflater.inflate(R.layout.fragment_cassia_demo_device, container, false);
        viewHeartRateMeasurement = (TextView) view.findViewById(R.id.viewHeartRateMeasurementValue);
        viewTemperatureMeasurement = (ThermometerView) view.findViewById(R.id.tv_thermometer);
        viewCurrentTime = (TextView) view.findViewById(R.id.viewCurrentTimeValue);
        viewNewAlert = (TextView) view.findViewById(R.id.viewNewAlertValue);
        viewHeartRateChart = (LineChart) view.findViewById(R.id.viewHeartRateChart);

        // 设置控件默认值
        createHeartRateChart();

        // 其他初始化动作
        startDataUpdateTimer();

        return view;
    }

    // 控件更新界面
    private Runnable mEditTextHeartRateMeasurementUpdater = new Runnable() {
        @Override
        public void run() {
            viewHeartRateMeasurement.setText(Integer.toString(mHeartRateMeasurementValue));
        }
    };

    // 控件更新界面
    private Runnable mEditTextCurrentTimeUpdater = new Runnable() {
        @Override
        public void run() {
            byte[] payload = new byte[7];
            System.arraycopy(mCurrentTimeCharValue, 0, payload, 0, payload.length);
            byte[] yearBytes = Arrays.copyOfRange(payload, 0, 2);
            String dateTime = String.format("%4d-%02d-%02d %02d:%02d:%02d",
                    Utils.bytes2shortLE(yearBytes), payload[2], payload[3], payload[4], payload[5], payload[6]);
            viewCurrentTime.setText(dateTime);
        }
    };

    // 控件更新界面
    private Runnable mEditTextNewAlertUpdater = new Runnable() {
        @Override
        public void run() {
            byte[] payload = new byte[mNewAlertCharValue.length - 2];
            System.arraycopy(mNewAlertCharValue, 2, payload, 0, payload.length);
            String sms = new String(payload, StandardCharsets.UTF_8);
            viewNewAlert.setText(sms);
        }
    };

    // 控件更新界面
    private Runnable mEditTextTemperatureMeasurementUpdater = new Runnable() {
        @Override
        public void run() {
            float value = (float) mTemperatureMeasurementValue / 100;
            viewTemperatureMeasurement.setValueAndStartAnim(value);
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

    // GATT设置时间: 设置值 -> 更新UI
    private int gattSetCurrentTimeValue(byte[] value) {
        if (value.length != 10) {
            return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH; // 长度不足
        }
        System.arraycopy(value, 0, mCurrentTimeCharValue, 0, value.length);
        mCurrentTimeChar.setValue(value);
        getActivity().runOnUiThread(mEditTextCurrentTimeUpdater);
        return BluetoothGatt.GATT_SUCCESS;
    }

    // GATT设置当前短信: 设置值 -> 更新UI -> 发送notify
    private int gattSetNewAlert(byte[] value) {
        if (value.length > 20 || value.length < 3) {
            return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH; // 无效长度
        }
        mNewAlertCharValue = value;
        mNewAlertChar.setValue(value);
        getActivity().runOnUiThread(mEditTextNewAlertUpdater);
        if (mNewAlertNotifyOn) {
            mDelegate.sendNotificationToDevices(mNewAlertChar); // notify上报数据
        }
        return BluetoothGatt.GATT_SUCCESS;
    }

    // 心率定时器处理: 生成随机数 -> gatt更新 -> 更新控件 -> 发送通知
    private void heartRateTimerHandler() {
        mHeartRateMeasurementValue = Utils.getRandomRange(50, 140);
        long timestamp = System.currentTimeMillis();
        updateHeartRateChart(timestamp, mHeartRateMeasurementValue);
        gattSetHeartRateMeasurementValue(mHeartRateMeasurementValue);
        getActivity().runOnUiThread(mEditTextHeartRateMeasurementUpdater);
        if (mHeartRateMeasurementNotifyOn) { // 只有开启notify时才上报数据
            mDelegate.sendNotificationToDevices(mHeartRateMeasurementCharacteristic); // notify上报数据
        }
    }

    // 体温定时器处理：生成随机数 -> gatt更新 -> 更新控件 -> 发送通知
    private void temperatureTimerHandler() {
        mTemperatureMeasurementValue = Utils.getRandomRange(3600, 4000);
        gattSetTemperatureMeasurementValue(mTemperatureMeasurementValue);
        getActivity().runOnUiThread(mEditTextTemperatureMeasurementUpdater);
        if (mTemperatureMeasurementNotifyOn) {
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
        return mServices.toArray(new BluetoothGattService[mServices.size()]);
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
        if (characteristic.getUuid() == CURRENT_TIME_CHAR_UUID) {
            return gattSetCurrentTimeValue(value);
        } else if (characteristic.getUuid() == NEW_ALERT_CHAR_UUID) {
            return gattSetNewAlert(value);
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
        } else if (characteristic.getUuid() == NEW_ALERT_CHAR_UUID) {
            mNewAlertNotifyOn = true;
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
        } else if (characteristic.getUuid() == NEW_ALERT_CHAR_UUID) {
            mNewAlertNotifyOn = false;
        }
    }
}
