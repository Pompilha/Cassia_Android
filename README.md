# BLE Peripheral Simulator

The BLE Peripheral Simulator is an Android app that allows developers to try
out new features of Cassia Router without the need for a BLE Peripheral Device.

You can build it from source or install it from the [link](https://github.com/AcaciaNetworks/ble-test-peripheral-android/blob/master/app/build/outputs/apk/debug/app-debug.apk?raw=true).

APP broadcast instructions:

* Device broadcast identification: Name is Cassia Demo App.
* Parameters: LegacyMode, Connectable, Scannable, Interval(160ms), TxPowerLevel(1)
* The broadcast packet is updated every 0.5 second and the temperature data is a random number between 35.00 and 40.00
* Example broadcast package: 02010207FFFF0000000E07
    * 0E07: temperature * 100 = 35.91
* Example of Scan response package: 12094361737369612044656d6f20417070
    * 4361737369612044656d6f20417070: Cassia Demo App

A developer can use the app to simulate a BLE Peripheral with one of these services:

* Heart Rate Service
    * Support notify report data:
        * Connect App
        * Open Notify: UUID 00002a37-0000-1000-8000-00805f9b34fb
        * The notify data is updated every second and is a random number between 80 and 120

The developer can use the Cassia Router features to connect to the app to Read and Write Characteristics, Subscribe to Notifications for when the Characteristics change, and Read and Write Descriptors.

From the app a developer can set the characteristics' values, send notifications and disconnect.

![Heart Rate Service](Heart%20Rate%20Service.png)

### Caveats
* Please keep the App front when using.
* Please close the app in time when not in use to prevent the battery from draining quickly.
* BLE broadcast uses API level 26, and currently only supports Android 8 or higher versions.
* Due to hardware chipset dependency, some devices don't have access to this feature. Reference: http://stackoverflow.com/questions/26482611/chipsets-devices-supporting-android-5-ble-peripheral-mode.

### Todo List
* App add stopping-advertising switch
* App add temperature display
* App add version display
* App disconnect function
* Adapt to lower version android
