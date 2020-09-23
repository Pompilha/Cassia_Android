# BLE Peripheral Simulator

The BLE Peripheral Simulator is an Android app that allows developers to try
out new features of Cassia Router without the need for a BLE Peripheral Device.

You can build it from source or install it from the [link](https://github.com/AcaciaNetworks/ble-test-peripheral-android/blob/master/app/build/outputs/apk/debug/app-debug.apk?raw=true).

APP broadcast instructions:

* Device broadcast identification: Name is ble-test.
* Parameters: LegacyMode, Connectable, Scannable, Interval(160ms), TxPowerLevel(1)
* Example of HeartRate broadcast package: 02010204160D1862
    * 0D18: Heart Rate Service UUID
    * 62: Heart Rate
* Example of Scan response package: 0909626C652D74657374
    * 626c652d74657374: APP(device) Name

A developer can use the app to simulate a BLE Peripheral with one of three services:

* Heart Rate Service
    * The broadcast packet contains heart rate data:
        * The broadcast packet is updated every second and is a random number between 80 and 120
        * Broadcast package example: 02010204160D1862
            * Heart Rate data index is 0x07
            * Heart Rate data is 0x62
    * Support notify report data:
        * Connect App
        * Open Notify: UUID 00002a37-0000-1000-8000-00805f9b34fb
        * The notify data is updated every second and is a random number between 80 and 120
* Battery Service
* Health Thermometer Service

The developer can use the Cassia Router features to connect to the app to Read and Write Characteristics, Subscribe to Notifications for when the Characteristics change, and Read and Write Descriptors.

From the app a developer can set the characteristics' values, send notifications and disconnect.

![Battery Service](Battery%20Service.png)
![Heart Rate Service](Heart%20Rate%20Service.png)
![Health Thermometer Service](Health%20Thermometer%20Service.png)

### Caveats
* Please keep the App front when using.
* Please close the app in time when not in use to prevent the battery from draining quickly.
* BLE broadcast uses API level 26, and currently only supports Android 8 or higher versions.
* Due to hardware chipset dependency, some devices don't have access to this feature. Reference: http://stackoverflow.com/questions/26482611/chipsets-devices-supporting-android-5-ble-peripheral-mode.

### Todo List
* App add version display
* Adapt to lower version android
