package com.sbugert.rnadmob;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.facebook.react.views.view.ReactViewGroup;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.doubleclick.AppEventListener;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.doubleclick.PublisherAdView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RNPublisherBannerViewManager extends SimpleViewManager<ReactViewGroup> implements AppEventListener {

  public static final String REACT_CLASS = "RNAdMobDFP";

  public static final String PROP_BANNER_SIZE = "bannerSize";
  public static final String PROP_AD_UNIT_ID = "adUnitID";
  public static final String PROP_CUSTOM_TARGET= "customTarget";
  public static final String PROP_TEST_DEVICE_ID = "testDeviceID";

  private String testDeviceID = null;
  private ReadableMap customTarget;

  public enum Events {
    EVENT_SIZE_CHANGE("onSizeChange"),
    EVENT_RECEIVE_AD("onAdViewDidReceiveAd"),
    EVENT_ERROR("onDidFailToReceiveAdWithError"),
    EVENT_WILL_PRESENT("onAdViewWillPresentScreen"),
    EVENT_WILL_DISMISS("onAdViewWillDismissScreen"),
    EVENT_DID_DISMISS("onAdViewDidDismissScreen"),
    EVENT_WILL_LEAVE_APP("onAdViewWillLeaveApplication"),
    EVENT_ADMOB_EVENT_RECEIVED("onAdmobDispatchAppEvent");

    private final String mName;

    Events(final String name) {
      mName = name;
    }

    @Override
    public String toString() {
      return mName;
    }
  }

  private ThemedReactContext mThemedReactContext;
  private RCTEventEmitter mEventEmitter;

  @Override
  public String getName() {
    return REACT_CLASS;
  }


  @Override
  public void onAppEvent(String name, String info) {
    String message = String.format("Received app event (%s, %s)", name, info);
    Log.d("PublisherAdBanner", message);
    WritableMap event = Arguments.createMap();
    event.putString(name, info);
    mEventEmitter.receiveEvent(viewID, Events.EVENT_ADMOB_EVENT_RECEIVED.toString(), event);
  }

  @Override
  protected ReactViewGroup createViewInstance(ThemedReactContext themedReactContext) {
    mThemedReactContext = themedReactContext;
    mEventEmitter = themedReactContext.getJSModule(RCTEventEmitter.class);
    ReactViewGroup view = new ReactViewGroup(themedReactContext);
    attachNewAdView(view);
    return view;
  }

  int viewID = -1;
  protected void attachNewAdView(final ReactViewGroup view) {
    final PublisherAdView adView = new PublisherAdView(mThemedReactContext);
    adView.setAppEventListener(this);
    // destroy old AdView if present
    PublisherAdView oldAdView = (PublisherAdView) view.getChildAt(0);
    view.removeAllViews();
    if (oldAdView != null) oldAdView.destroy();
    view.addView(adView);
    attachEvents(view);
  }

  protected void attachEvents(final ReactViewGroup view) {
    viewID = view.getId();
    final PublisherAdView adView = (PublisherAdView) view.getChildAt(0);
    adView.setAdListener(new AdListener() {
      @Override
      public void onAdLoaded() {
        int width = adView.getAdSize().getWidthInPixels(mThemedReactContext);
        int height = adView.getAdSize().getHeightInPixels(mThemedReactContext);
        int left = adView.getLeft();
        int top = adView.getTop();
        adView.measure(width, height);
        adView.layout(left, top, left + width, top + height);
        mEventEmitter.receiveEvent(view.getId(), Events.EVENT_RECEIVE_AD.toString(), null);

        // send measurements to js to style the AdView in react
        WritableMap event = Arguments.createMap();
        event.putDouble("width", adView.getAdSize().getWidth());
        event.putDouble("height", adView.getAdSize().getHeight());
        mEventEmitter.receiveEvent(view.getId(), Events.EVENT_SIZE_CHANGE.toString(), event);
      }

      @Override
      public void onAdFailedToLoad(int errorCode) {
        WritableMap event = Arguments.createMap();
        switch (errorCode) {
          case PublisherAdRequest.ERROR_CODE_INTERNAL_ERROR:
            event.putString("error", "ERROR_CODE_INTERNAL_ERROR");
            break;
          case PublisherAdRequest.ERROR_CODE_INVALID_REQUEST:
            event.putString("error", "ERROR_CODE_INVALID_REQUEST");
            break;
          case PublisherAdRequest.ERROR_CODE_NETWORK_ERROR:
            event.putString("error", "ERROR_CODE_NETWORK_ERROR");
            break;
          case PublisherAdRequest.ERROR_CODE_NO_FILL:
            event.putString("error", "ERROR_CODE_NO_FILL");
            break;
        }

        mEventEmitter.receiveEvent(view.getId(), Events.EVENT_ERROR.toString(), event);

        WritableMap resizeEvent = Arguments.createMap();
        resizeEvent.putDouble("width", 0);
        resizeEvent.putDouble("height", 0);
        mEventEmitter.receiveEvent(view.getId(), Events.EVENT_SIZE_CHANGE.toString(), resizeEvent);
      }

      @Override
      public void onAdOpened() {
        mEventEmitter.receiveEvent(view.getId(), Events.EVENT_WILL_PRESENT.toString(), null);
      }

      @Override
      public void onAdClosed() {
        mEventEmitter.receiveEvent(view.getId(), Events.EVENT_WILL_DISMISS.toString(), null);
      }

      @Override
      public void onAdLeftApplication() {
        mEventEmitter.receiveEvent(view.getId(), Events.EVENT_WILL_LEAVE_APP.toString(), null);
      }
    });
  }

  @Override
  @Nullable
  public Map<String, Object> getExportedCustomDirectEventTypeConstants() {
    MapBuilder.Builder<String, Object> builder = MapBuilder.builder();
    for (Events event : Events.values()) {
      builder.put(event.toString(), MapBuilder.of("registrationName", event.toString()));
    }
    return builder.build();
  }

  @ReactProp(name = PROP_BANNER_SIZE)
  public void setBannerSize(final ReactViewGroup view, final String sizeString) {
    // Multiple ad size . Ref: https://developers.google.com/mobile-ads-sdk/docs/dfp/android/banner#multiple_ad_sizes

    // store old ad unit ID (even if not yet present and thus null)
    PublisherAdView oldAdView = (PublisherAdView) view.getChildAt(0);
    String adUnitId = oldAdView.getAdUnitId();

    attachNewAdView(view);
    PublisherAdView newAdView = (PublisherAdView) view.getChildAt(0);
    List<AdSize> sizeList = new ArrayList<AdSize>();
    for(String subString : sizeString.split("\\|")){ // cycle through the array
      sizeList.add(getAdSizeFromString(subString));
    }

    newAdView.setAdSizes(sizeList.toArray(new AdSize[sizeList.size()]));
    newAdView.setAdUnitId(adUnitId);

    // send measurements to js to style the AdView in react
    int width;
    int height;
    WritableMap event = Arguments.createMap();
    AdSize adSize = sizeList.get(0);
    if (adSize == AdSize.SMART_BANNER) {
      width = (int) PixelUtil.toDIPFromPixel(adSize.getWidthInPixels(mThemedReactContext));
      height = (int) PixelUtil.toDIPFromPixel(adSize.getHeightInPixels(mThemedReactContext));
    }
    else {
      width = adSize.getWidth();
      height = adSize.getHeight();
    }
    event.putDouble("width", width);
    event.putDouble("height", height);
    mEventEmitter.receiveEvent(view.getId(), Events.EVENT_SIZE_CHANGE.toString(), event);

    loadAd(newAdView);
  }

  @ReactProp(name = PROP_AD_UNIT_ID)
  public void setAdUnitID(final ReactViewGroup view, final String adUnitID) {
    // store old banner size (even if not yet present and thus null)
    PublisherAdView oldAdView = (PublisherAdView) view.getChildAt(0);

    attachNewAdView(view);
    PublisherAdView newAdView = (PublisherAdView) view.getChildAt(0);
    newAdView.setAdUnitId(adUnitID);

    newAdView.setAdSizes(oldAdView.getAdSizes());

    loadAd(newAdView);
  }

  @ReactProp(name = PROP_TEST_DEVICE_ID)
  public void setPropTestDeviceID(final ReactViewGroup view, final String testDeviceID) {
    this.testDeviceID = testDeviceID;
  }

  @ReactProp(name = PROP_CUSTOM_TARGET)
  public void setPropCustomTarget(final ReactViewGroup view, final ReadableMap customTarget) {
    this.customTarget = customTarget;
  }


  private void loadAd(final PublisherAdView adView) {
    if (adView.getAdSizes() != null && adView.getAdUnitId() != null) {
      PublisherAdRequest.Builder adRequestBuilder = new PublisherAdRequest.Builder();
      if (testDeviceID != null){
        if (testDeviceID.equals("EMULATOR")) {
          adRequestBuilder = adRequestBuilder.addTestDevice(PublisherAdRequest.DEVICE_ID_EMULATOR);
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

      PublisherAdRequest adRequest = adRequestBuilder.build();
      adView.loadAd(adRequest);
    }
  }


  private AdSize getAdSizeFromString(String adSize) {
    switch (adSize) {
      case "banner":
        return AdSize.BANNER;
      case "largeBanner":
        return AdSize.LARGE_BANNER;
      case "mediumRectangle":
        return AdSize.MEDIUM_RECTANGLE;
      case "fullBanner":
        return AdSize.FULL_BANNER;
      case "leaderBoard":
        return AdSize.LEADERBOARD;
      case "smartBannerPortrait":
        return AdSize.SMART_BANNER;
      case "smartBannerLandscape":
        return AdSize.SMART_BANNER;
      case "smartBanner":
        return AdSize.SMART_BANNER;
      default:
        String[] sizeSubstring = adSize.split("x");
        if (sizeSubstring.length == 2) {
          return new AdSize(Integer.parseInt(sizeSubstring[0]), Integer.parseInt(sizeSubstring[1]));
        }
        return AdSize.BANNER;
    }
  }
}
