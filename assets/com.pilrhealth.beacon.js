const ANDROID_PERMISSIONS = [
    // 'android.permission.BLUETOOTH_ADVERTISE',
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_BACKGROUND_LOCATION',
    'android.permission.BLUETOOTH_SCAN',
    'android.permission.BLUETOOTH_CONNECT',
];

const COARSE_LOCATION = 'android.permission.ACCESS_COARSE_LOCATION';
const FINE_LOCATION = 'android.permission.ACCESS_FINE_LOCATION';
const BACKGROUND_LOCATION = 'android.permission.ACCESS_BACKGROUND_LOCATION';
const BLUETOOTH_PERMS =  [
    'android.permission.BLUETOOTH_SCAN',
    'android.permission.BLUETOOTH_CONNECT',
]

let requestTaskP = null;
export async function requestPermissions() {
    console.error('DEBUG>>>requestPermisions taskP=', requestTaskP)
    if (requestTaskP === null) {
        requestTaskP = _requestPermissions();
    }
    const result = await requestTaskP;
    requestTaskP = null;
    return result;
}

async function _requestPermissions() {
    let r;
    if (!Ti.Android.hasPermission([ FINE_LOCATION ])) {
        await requestPermissionsP([ FINE_LOCATION, COARSE_LOCATION  ]);
    }
    await new Promise(resolve => setTimeout(resolve, 1000));
    await requestPermissionsP([ BACKGROUND_LOCATION ])
    await requestPermissionsP(BLUETOOTH_PERMS)

    return !!r.success;
}

async function requestPermissionsP(permissions) {
    return new Promise((resolve) => {
        console.error('DEBUG>>> requestPerms', permissions)
        Ti.Android.requestPermissions(permissions, (result) => {
            console.error('DEBUG>>> requestPerms result', permissions, result)
            resolve(result);
        });
    });
}
