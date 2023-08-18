// This is a test harness for your module
// You should do something interesting in this harness
// to test out the module and to provide instructions
// to users on how to use it by example.
import ble_beacon  from 'com.pilrhealth.beacon';

const ANDROID_PERMISSIONS = [
    //'android.permission.BLUETOOTH_ADVERTISE',
    'android.permission.BLUETOOTH_CONNECT',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.BLUETOOTH_SCAN',
];


// open a single window
const win = Ti.UI.createWindow();
const label = Ti.UI.createLabel();
win.add(label);

// TODO: write your module tests here
Ti.API.info("module is => " + ble_beacon);

console.error("DEBUG>>>>> HERE");

function startBLE() {
    console.error('starting BLE');
    Ti.Android.requestPermissions(
        ['android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION'],
        e => {
            Ti.Android.requestPermissions(
                ['android.permission.ACCESS_BACKGROUND_LOCATION'], e => {
                    if (e.success) {
                        Ti.Android.requestPermissions(
                            ['android.permission.BLUETOOTH_SCAN', 'android.permission.BLUETOOTH_CONNECT'], e => {
                                const what = ble_beacon.startBeaconDetection();
                                console.error('should have started, what=', what);
                                
                            });
                    }
                });
        });
}

async function requestPermissions(permissions) {
    return new Promise(resolve => {
        Ti.Android.requestPermissions(permissions, resolve)
    })
}


win.open().then(startBLE);
//win.open().then(() => {
    //console.error("DEBUG>>>>> HERE2");
//
    //const what = Ti.Android.requestPermissions(ANDROID_PERMISSIONS, result => {
        //console.error('got result2=', result)
        //if (result.success) {
            //console.error('startinng2...')
            //ble_beacon.startBeaconDetction();
        //}
    //});
    //console.error('should have started, what=', what);
   // 
//});

label.text = ble_beacon.example();

Ti.API.info("module exampleProp is => " + ble_beacon.exampleProp);
ble_beacon.exampleProp = "This is a test value";

if (Ti.Platform.name == "android") {
	const proxy = ble_beacon.createExample({
		message: "Creating an example Proxy",
		backgroundColor: "red",
		width: 100,
		height: 100,
		top: 100,
		left: 150
	});

	proxy.printMessage("Hello world!");
	proxy.message = "Hi world!.  It's me again.";
	proxy.printMessage("Hello world!");
	win.add(proxy);
}
