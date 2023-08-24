// This is a test harness for your module
// You should do something interesting in this harness
// to test out the module and to provide instructions
// to users on how to use it by example.
import ble_beacon  from 'com.pilrhealth.beacon';

async function startBLE() {
    let ok = await requestPermissions([
        'android.permission.ACCESS_COARSE_LOCATION',
        'android.permission.ACCESS_FINE_LOCATION'
    ]);
    ok &&= await requestPermissions([
        'android.permission.ACCESS_BACKGROUND_LOCATION'
    ]);
    ok &&= await requestPermissions([
        'android.permission.BLUETOOTH_SCAN',
        'android.permission.BLUETOOTH_CONNECT',
        'android.permission.POST_NOTIFICATIONS',
    ]);
    if (ok) {
        console.debug("app starting detction")
        //ble_beacon.addFriend('Bacchus', '51166', '48165');
        ble_beacon.setFriendList('foo-0-0-footag, Bacchus-51166-48165');
        ble_beacon.betweenScanPeriod = 35 * 1000
        ble_beacon.transientTimeoutSecs = 60;
        ble_beacon.actualTimeoutSecs = 60;
        ble_beacon.minDurationSecs = 60
        ble_beacon.notificationTitle = "app.js customized title"
        ble_beacon.notificationText = "app.js customized text"
        ble_beacon.startBeaconDetection();
    }
} 

async function requestPermissions(permissions) {
    console.debug('request permissions', permissions);
    return new Promise((resolve) => {
        Ti.Android.requestPermissions(permissions, (result) => {
            if (result.success) {
                console.debug('got permission', result);
                resolve(true);
            } else {
                console.error('Failed to get permission', permissions, result);
                resolve(false);
            }
        });
    });
}

ble_beacon.addEventListener('ble.event', () => {
    log("GOT EVENT ble.event");
    fetchEvents();
});

function fetchEvents() {
    const events = JSON.parse(ble_beacon.fetchEvents());
    const formatted = JSON.stringify(events, undefined, 2);
    log("messages=", formatted);
}

Ti.App.addEventListener('resumed', () => {
    log("scanStats=", ble_beacon.scanStats())
    fetchEvents();
});


////////////////////////////////////////
//// boiler plate test stuff

let history = '';
function log(...theArguments) {
    // Stringify non-strings
    let mappedArgs = theArguments.map((argument) => {
        return (typeof argument === 'string') ? argument : JSON.stringify(argument, null, 2);
    });

    const message = mappedArgs.join(' ');
    const timestamp = new Date().toLocaleString('en-US', { hour12: false });

    // Use error-level for production or they will not show in Xcode console
    Ti.API[ENV_PROD ? 'error' : 'info'](message);

    history = `${history} [${timestamp}] ${message}\n\n`;
    consoleLabel.text = history;
    consoleView.scrollToBottom()
}

const win = Ti.UI.createWindow();

const consoleView = Ti.UI.createScrollView({
	contentWidth: Ti.UI.FILL,
	contentHeight: Ti.UI.SIZE
})
win.add(consoleView);
const consoleLabel = Ti.UI.createLabel({
	top: 10,
	right: 10,
	left: 10,

	width: Ti.UI.FILL,
	height: Ti.UI.SIZE,

	font: {
		fontFamily: 'Courier New'
	}
});
consoleView.add(consoleLabel);
win.open().then(startBLE);
