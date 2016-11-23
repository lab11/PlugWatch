package gridwatch.plugwatch.wit;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.util.Log;

import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.RxBleScanResult;
import com.polidea.rxandroidble.utils.ConnectionSharingAdapter;

import gridwatch.plugwatch.PlugWatchApp;
import gridwatch.plugwatch.configs.BluetoothConfig;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

/**
 * Created by nklugman on 11/24/16.
 */

public class TestLinker {


    private static TestLinker mInstance = null;

    private RxBleClient rxBleClient;
    private Subscription scanSubscription;
    private RxBleDevice bleDevice;
    private PublishSubject<Void> disconnectTriggerSubject = PublishSubject.create();
    private Context mContext;

    public TestLinker(Context r) {
        mContext = r;
        rxBleClient = PlugWatchApp.getRxBleClient(r);
        start_scanning();
    }

    private Observable<RxBleConnection> connectionObservable;

    private void start_scanning() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            BluetoothAdapter.getDefaultAdapter().enable();
        }
        scanSubscription = rxBleClient.scanBleDevices()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnUnsubscribe(this::clearSubscription)
                .subscribe(this::addScanResult, this::onScanFailure);
    }


    private void addScanResult(RxBleScanResult bleScanResult) {
        if (bleScanResult.getBleDevice().getName().contains("Smart")) {
            bleDevice = bleScanResult.getBleDevice();
            connectionObservable = bleDevice
                    .establishConnection(mContext, false)
                    .takeUntil(disconnectTriggerSubject)
                    .doOnUnsubscribe(this::clearSubscription)
                    .compose(new ConnectionSharingAdapter());
            getWiTenergy();
            scanSubscription.unsubscribe();
        } else {
            start_scanning();
        }
    }

    private void getWiTenergy() {
        Log.e("getWiTenergy", "hit");
        connectionObservable
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(BluetoothConfig.UUID_WIT_FFE1))
                .doOnNext(new Action1<Observable<byte[]>>() {
                    @Override
                    public void call(Observable<byte[]> observable) {
                        notificationHasBeenSetUp();
                    }
                })
                .flatMap(notificationObservable -> notificationObservable)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onNotificationReceivedFFE1, this::onNotificationSetupFailure);
    }


    private void onScanFailure(Throwable throwable) {
    }

    private void onNotificationSetupFailure(Throwable throwable) {
    }

    private void notificationHasBeenSetUp() {
    }

    private void onNotificationReceivedFFE1(byte[] value) {
        Log.e("notification", "hit");
    }

    private void clearSubscription() {
        scanSubscription = null;
    }


}
