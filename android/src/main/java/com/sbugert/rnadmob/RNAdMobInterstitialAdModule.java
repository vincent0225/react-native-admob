package com.sbugert.rnadmob;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.doubleclick.PublisherInterstitialAd;

public class RNAdMobInterstitialAdModule extends ReactContextBaseJavaModule {
  private final ReactApplicationContext mReactContext;
  PublisherInterstitialAd mInterstitialAd;
  String adUnitID;
  String testDeviceID;
  ReadableMap customTarget;
  Callback requestAdCallback;
  Callback showAdCallback;

  @Override
  public String getName() {
    return "RNAdMobInterstitial";
  }

  public RNAdMobInterstitialAdModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.mReactContext = reactContext;
  }

  private void initializeInterstitialAd() {
    mInterstitialAd = new PublisherInterstitialAd(mReactContext);

    if (adUnitID != null) {
      mInterstitialAd.setAdUnitId(adUnitID);
    }

    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        mInterstitialAd.setAdListener(new AdListener() {
          @Override
          public void onAdClosed() {
            sendEvent("interstitialDidClose", null);
            showAdCallback.invoke();
          }
          @Override
          public void onAdFailedToLoad(int errorCode) {
            WritableMap event = Arguments.createMap();
            String errorString = null;
            switch (errorCode) {
              case AdRequest.ERROR_CODE_INTERNAL_ERROR:
                errorString = "ERROR_CODE_INTERNAL_ERROR";
                break;
              case AdRequest.ERROR_CODE_INVALID_REQUEST:
                errorString = "ERROR_CODE_INVALID_REQUEST";
                break;
              case AdRequest.ERROR_CODE_NETWORK_ERROR:
                errorString = "ERROR_CODE_NETWORK_ERROR";
                break;
              case AdRequest.ERROR_CODE_NO_FILL:
                errorString = "ERROR_CODE_NO_FILL";
                break;
            }
            event.putString("error", errorString);
            sendEvent("interstitialDidFailToLoad", event);
            requestAdCallback.invoke(errorString);
          }
          @Override
          public void onAdLeftApplication() {
            sendEvent("interstitialWillLeaveApplication", null);
          }
          @Override
          public void onAdLoaded() {
            sendEvent("interstitialDidLoad", null);
            requestAdCallback.invoke();
          }
          @Override
          public void onAdOpened() {
            sendEvent("interstitialDidOpen", null);
          }
        });
      }
    });
  }

  private void sendEvent(String eventName, @Nullable WritableMap params) {
    getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
  }

  @ReactMethod
  public void setAdUnitID(String adUnitID) {
    this.adUnitID = adUnitID;
  }

  @ReactMethod
  public void setTestDeviceID(String testDeviceID) {
    this.testDeviceID = testDeviceID;
  }

  @ReactMethod
  public void setCustomTarget(ReadableMap customTarget) {
    this.customTarget = customTarget;
  }

  @ReactMethod
  public void requestAd(final Callback callback) {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run () {

        if (mInterstitialAd != null && (mInterstitialAd.isLoaded() || mInterstitialAd.isLoading()) ) {
          callback.invoke("Ad is already loaded."); // TODO: make proper error
        } else {
          requestAdCallback = callback;
          PublisherAdRequest.Builder adRequestBuilder = new PublisherAdRequest.Builder();

          if (testDeviceID != null){
            if (testDeviceID.equals("EMULATOR")) {
              adRequestBuilder = adRequestBuilder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);
            } else {
              adRequestBuilder = adRequestBuilder.addTestDevice(testDeviceID);
            }
          }

          if (customTarget != null){
            ReadableMapKeySetIterator iterator = customTarget.keySetIterator();
            while(iterator.hasNextKey()) {
              String key = iterator.nextKey();
              if (customTarget.getType(key) == ReadableType.String) {
                adRequestBuilder = adRequestBuilder.addCustomTargeting(key, customTarget.getString(key));
              } else {
                Log.d("PublisherAdBanner", "Only String Custom Target was handled");
              }
            }
          }

          if (mInterstitialAd != null) {
            mInterstitialAd = null; // Destroy Last InterstitialAd
          }
          initializeInterstitialAd();

          PublisherAdRequest adRequest = adRequestBuilder.build();
          mInterstitialAd.loadAd(adRequest);
        }
      }
    });
  }


  // Assume method was called after request ads callback fired.
  @ReactMethod
  public void showAd(final Callback callback) {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run () {
        if (mInterstitialAd.isLoaded()) {
          showAdCallback = callback;
          mInterstitialAd.show();
        } else {
          callback.invoke("Ad is not ready."); // TODO: make proper error
        }
      }
    });
  }

  @ReactMethod
  public void isReady(final Callback callback) {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run () {
        callback.invoke(mInterstitialAd.isLoaded());
      }
    });
  }
}
