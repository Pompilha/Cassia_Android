# BLE Peripheral Simulator

The BLE Peripheral Simulator is an Android app that allows developers to try
out new features of Cassia Router without the need for a BLE Peripheral Device.

You can build it from source or install it from the [link](https://github.com/AcaciaNetworks/ble-test-peripheral-android/blob/master/app/build/outputs/apk/debug/app-debug.apk).

APP broadcast instructions:

* Device broadcast identification: Name is ble-test.
* Example of original broadcast package: 02010204160D1862
* Example of Scan response package: 0909626C652D74657374

A developer can use the app to simulate a BLE Peripheral with one of three services:

* Battery Service
* Heart Rate Service
* Health Thermometer Service

The developer can use the Cassia Router features to connect to the app to Read and Write Characteristics, Subscribe to Notifications for when the Characteristics change, and Read and Write Descriptors.

From the app a developer can set the characteristics' values, send notifications and disconnect.

![Battery Service](Battery%20Service.png)
![Heart Rate Service](Heart%20Rate%20Service.png)
![Health Thermometer Service](Health%20Thermometer%20Service.png)

### Caveats
* BLE broadcast uses API level 26, and currently only supports Android 8 or higher versions.
* Due to hardware chipset dependency, some devices don't have access to this feature. Reference: http://stackoverflow.com/questions/26482611/chipsets-devices-supporting-android-5-ble-peripheral-mode.
