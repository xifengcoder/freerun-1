package com.benpaoba.freerun;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import android.content.ContentValues;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.GestureDetector.OnGestureListener;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.benpaoba.freerun.database.FreeRunContentProvider;
import com.benpaoba.freerun.database.RunRecordTable;

public class RunningMainActivity extends Activity {
	public static final String TAG = "RunningMap";
	
	private final static float DEFAULT_ZOOM_LEVEL = 18.0f;
	private final static int SAVA_FILE_INTERVALS = 60;
	private int mSportStatus;
	private DistanceInfoDao mDistanceInfoDao;
	// 定位相关
    LocationClient mLocClient;
	private CustomLocationListenner mLocationListener = new CustomLocationListenner();
	BitmapDescriptor mCurrentMarker;
    private ActivityManager mActivityManager;

	MapView mMapView;
	BaiduMap mBaiduMap;
	// UI相关
	private TextView mGpsSignalTextView;
	private ViewGroup mRunningStateLayout;
	private ViewGroup mOtherDetailsLayout;
	private ViewGroup mUnLockedControllerLayout;
	private ViewGroup mLockedControllerLayout;
	private TextView mCountDownView;
	private Button mMiddleButton;
	private Button mLeftButton;
	private Button mRightButton;
	private ImageButton mLockImageButton;
	private ViewGroup mViewRunControllerLayout;
	
	private ImageView mFullMapImageView;
	private TextView mRunDistanceTextView;
	private TextView mRunTimeTextView;
	
	boolean mIsFirstLoc = true;

	private boolean mIsBaiduMapFullScreen = false;
	
	private LatLng mPointLast = null;
	private LatLng mPointBeforeLast = null;
	
	private int mUpdateInterval = 0;
	
    private SoundClips.Player mSoundPlayer;
    private Context mContext;
    private static final int SPEED = 30;
	private static final int SLEEP_TIME = 5;
	
	
	private boolean hasMeasured = false;
	private boolean isScrolling = false;
	
	private int window_width;
	private int max_width;
	
	private float mScrollX;
	
	private LinearLayout layout_left;
	private LinearLayout layout_right;
	
	private GestureDetector mGestureDetector;
	private ViewTreeObserver viewTreeObserver;
	private ImageButton userIcon;
    private TextView mAverageSpeedView;
    private TextView mPaceSpeedView;
	
    private long mStartTime;
	private LocationManager mLocationManager;
	
	private ExecutorService mExecutor = Executors.newSingleThreadExecutor();
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		Log.d(TAG,"onCreate, id : " + Thread.currentThread().getId());
		mDistanceInfoDao = new DistanceInfoDao(this);
		setContentView(R.layout.activity_main);
		initView();
		mActivityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		mLocationManager.addGpsStatusListener(mGpsStatusListener);
		
		mSportStatus = SportsManager.STATUS_READY;
		//获取资源
		mGpsSignalTextView = (TextView) findViewById(R.id.tv_bmap_run_gps);
	    
		mViewRunControllerLayout = (FrameLayout)findViewById(R.id.view_run_controller);
		mFullMapImageView = (ImageView)findViewById(R.id.img_bmap_full);
		mFullMapImageView.setOnClickListener(mOnClickListener);
		
		mRunningStateLayout = (LinearLayout)findViewById(R.id.layout_running_on);
		mOtherDetailsLayout = (LinearLayout)findViewById(R.id.other_details_layout);
		
		mUnLockedControllerLayout = (LinearLayout)findViewById(R.id.layout_controller);
		mLockedControllerLayout = (RelativeLayout)findViewById(R.id.layout_lock);
		mCountDownView = (TextView)findViewById(R.id.tv_action_number);
		mMiddleButton = (Button)findViewById(R.id.btn_running_middle);
	    mLeftButton = (Button)findViewById(R.id.btn_running_back);
	    mRightButton = (Button)findViewById(R.id.btn_running_right);
	    mLockImageButton = (ImageButton)findViewById(R.id.ibtn_running_lock);
		mRunningStateLayout.setVisibility(View.GONE);
	 	mLockedControllerLayout.setVisibility(View.GONE);
	 	mCountDownView.setVisibility(View.GONE);
	 	
	 	mAverageSpeedView = (TextView)findViewById(R.id.tv_run_speed);
	 	mPaceSpeedView = (TextView)findViewById(R.id.tv_run_pace);
	 	mRunDistanceTextView = (TextView) findViewById(R.id.tv_run_distance);
	 	mRunTimeTextView = (TextView)findViewById(R.id.tv_run_time);
		mLeftButton.setOnClickListener(mOnClickListener);
	 	mMiddleButton.setOnClickListener(mOnClickListener);
	 	mRightButton.setOnClickListener(mOnClickListener);
	 	mLockImageButton.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View arg0) {
				// TODO Auto-generated method stub
				Log.d(TAG, "ImageButton, onLongClick, arg0 = " + arg0);
				if (mActivityManager.isUserAMonkey()) {
					AlertDialog.Builder builder = new AlertDialog.Builder(
							RunningMainActivity.this);
					builder.setMessage("你不能对此屏幕进行操作，因为你不是管理员");
					builder.setPositiveButton("I admit defeat", null);
					builder.show();
					return true;
				}
				return true;
			}
		});
	 	
	 	if(mSportStatus == SportsManager.STATUS_INITIAL || mSportStatus == SportsManager.STATUS_READY) {
	 	    mMiddleButton.setText(R.string.status_start);
	 	    //mLeftButton.setVisibility(View.GONE);
	 	    mRightButton.setVisibility(View.INVISIBLE);
	 	}
	 	
	    // 地图初始化
	 	mMapView = (MapView) findViewById(R.id.map_view);
	 	mBaiduMap = mMapView.getMap();
	 	Log.d(TAG,"onCreate(), height: " + mMapView.getHeight());
	 	// 开启定位图层
	 	mBaiduMap.setMyLocationEnabled(true);
	 	//startService(new Intent(this, LocationService.class));
	 	mLocationListener = new CustomLocationListenner();
        mLocClient = new LocationClient(this);
        mLocClient.registerLocationListener(mLocationListener);
        //定位参数设置
        LocationClientOption option = new LocationClientOption();
        option.setCoorType("bd09ll"); //返回的定位结果是百度经纬度，默认值gcj02
        option.setAddrType("all");    //返回的定位结果包含地址信息
        option.setScanSpan(1000);
        option.setOpenGps(true);
        mLocClient.setLocOption(option);
        mLocClient.start();
        mLocClient.requestLocation();
	}
	
	private int mUsedSatellitesCount = 0;
	private final GpsStatus.Listener mGpsStatusListener = new GpsStatus.Listener() {
		public void onGpsStatusChanged(int event) { // GPS状态变化时的回调，如卫星数
			GpsStatus status = mLocationManager.getGpsStatus(null); //取当前状态
			if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
				int maxSatellites = status.getMaxSatellites();
				Iterator<GpsSatellite> it = status.getSatellites().iterator();
				while (it.hasNext() && mUsedSatellitesCount <= maxSatellites) {
					GpsSatellite s = it.next();
					if(s.usedInFix()) {
						mUsedSatellitesCount++;
					}
				}
			}
			int gpsState = GpsManager.getGpsStatus(mUsedSatellitesCount);
			updateGpsSignalTextView(gpsState);
		}
	};
	
	private void updateGpsSignalTextView(int gpsState) {
		String gpsDescription = null;
		switch(gpsState) {
		case GpsManager.GPS_BAD:
			gpsDescription = String.format(
					getResources().getString(R.string.gps_signal_text),
					getResources().getString(R.string.bad_gps));
			break;
		case GpsManager.GPS_NORMAL:
			gpsDescription = String.format(
					getResources().getString(R.string.gps_signal_text),
					getResources().getString(R.string.nomarl_gps));
			break;
		case GpsManager.GPS_GOOD:
			gpsDescription = String.format(
					getResources().getString(R.string.gps_signal_text),
					getResources().getString(R.string.good_gps));
			break;
		default:
			break;
		}
		mGpsSignalTextView.setText(gpsDescription);
	}
	private void initView() {
		layout_left = (LinearLayout) findViewById(R.id.layout_left);
		layout_right = (LinearLayout) findViewById(R.id.layout_right);
		
		//layout_left.setOnTouchListener(new MyOnTouchListener());
		ScrollView scrollView_Left = (ScrollView) findViewById(R.id.layout_left_sroll);
		scrollView_Left.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// TODO Auto-generated method stub
				  Log.d(TAG, "MyOnTouchListener : onTouch()");
				  boolean value = mGestureDetector.onTouchEvent(event);
				  Log.d(TAG, "GestureDetector's onTouchEvent: " + value);
				return false;
			}
		});
	    mGestureDetector = new GestureDetector(getApplicationContext(),
	    		new MyOnGestureListener());
	    mGestureDetector.setIsLongpressEnabled(false);

		viewTreeObserver = layout_right.getViewTreeObserver();
		// 获取控件宽度
		viewTreeObserver.addOnPreDrawListener(new OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				if (!hasMeasured) {
					window_width = getWindowManager().getDefaultDisplay()
							.getWidth();
					max_width = layout_left.getWidth();
					RelativeLayout.LayoutParams layoutParams_left = (RelativeLayout.LayoutParams) layout_left
							.getLayoutParams();
					RelativeLayout.LayoutParams layoutParams_right = (RelativeLayout.LayoutParams) layout_right
							.getLayoutParams();

					// 注意： 设置layout_right的宽度。防止被在移动的时候控件被挤压
					layoutParams_right.width = window_width;
					layout_right.setLayoutParams(layoutParams_right);

					// 设置layout_left的初始位置.
					layoutParams_left.rightMargin = max_width;
					layoutParams_left.leftMargin = -max_width;
					layout_left.setLayoutParams(layoutParams_left);
					Log.v(TAG, "MAX_WIDTH=" + max_width + "width="
							+ window_width);
					hasMeasured = true;
				}
				return true;
			}
		});
		
	}
	private Uri mInsertUri = null;
	private File mSaveFile = null;
	private View.OnClickListener mOnClickListener = new View.OnClickListener() {
			
		@Override
		public void onClick(View view) {
			// TODO Auto-generated method stub
			Log.d(TAG,"OnClick, view = " + view);
			switch(view.getId()) {
			case R.id.btn_running_back:
				finish();
				break;
				
			case R.id.btn_running_middle:		
				if(mSportStatus == SportsManager.STATUS_READY) {
					if(GpsManager.isGpsOpen(RunningMainActivity.this)) {
						mStartTime = System.currentTimeMillis();
						SportsManager.createNewPointsFile();
						mSportStatus = SportsManager.STATUS_RUNNING;
						//startService(new Intent(RunningMainActivity.this, LocationService.class));
						Intent intent = new Intent(SportsManager.STATUS_ACTION);
						intent.putExtra("command",SportsManager.CMD_START);
						Log.d("LocationService","RunningMainActivity, sendBroadcast()");
						sendBroadcast(intent);
						mSoundPlayer.play(SoundClips.START_SPORT,0,0,0);
						
						mMiddleButton.setText(R.string.status_pause);
						mLeftButton.setVisibility(View.GONE);
						mRightButton.setText(R.string.status_finished);
						mRightButton.setVisibility(View.VISIBLE);
						mLockImageButton.setVisibility(View.VISIBLE);
						mRunningStateLayout.setVisibility(View.VISIBLE);
						ContentValues values = new ContentValues();
						values.put(RunRecordTable.COLUMN_DATE,mStartTime);
						mInsertUri = RunningMainActivity.this.getContentResolver().
								insert(FreeRunContentProvider.CONTENT_URI, values);
						Log.d("yxf","mInsertUri = " + mInsertUri);
						mSaveFile = new File(SportsManager.POINTS_DIR,SportsManager.POINTS_FILE
								+ mInsertUri.getLastPathSegment() + SportsManager.SUFFIX);
						startTimer();
					} else {
						new AlertDialog.Builder(RunningMainActivity.this)
				        .setTitle(mContext.getString(R.string.open_gps_title))
				        .setMessage(mContext.getString(R.string.open_gps_message))
	                    .setPositiveButton(mContext.getString(R.string.button_ok), new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface arg0, int arg1) {
								// TODO Auto-generated method stub
								GpsManager.startIntentForGpsSetting(mContext);
							}
						})
						.setNegativeButton(mContext.getString(R.string.button_cancel), null).show();
					}
				}else if(mSportStatus == SportsManager.STATUS_RUNNING) {
					Intent intent = new Intent(SportsManager.STATUS_ACTION);
					intent.putExtra("command",SportsManager.CMD_PAUSE);
					sendBroadcast(intent);
					mSoundPlayer.play(SoundClips.PAUSE_SPORT,0,0,0);
					mSportStatus = SportsManager.STATUS_PAUSED;
					mMiddleButton.setText(R.string.status_continue);
					mLockImageButton.setVisibility(View.INVISIBLE);
					cancelTimer();
				} else if(mSportStatus == SportsManager.STATUS_PAUSED) {
					Intent intent = new Intent(SportsManager.STATUS_ACTION);
					intent.putExtra("command",SportsManager.CMD_CONTINUE);
					sendBroadcast(intent);
					mSoundPlayer.play(SoundClips.CONTINUE_SPORT,0,0,0);
					mSportStatus = SportsManager.STATUS_RUNNING;
					mMiddleButton.setText(R.string.status_pause);
					mLockImageButton.setVisibility(View.VISIBLE);
					startTimer();
				}
				break;
			case R.id.btn_running_right:
//				Intent intent = new Intent(SportsManager.STATUS_ACTION);
//				intent.putExtra("command",SportsManager.CMD_FINISH);
//				sendBroadcast(intent);
				stopService(new Intent(RunningMainActivity.this, LocationService.class));
				mSoundPlayer.play(SoundClips.COMPLETE_SPORT,0,0,0);
				cancelTimer();
				
				ContentValues values = new ContentValues();
				values.put(RunRecordTable.COLUMN_DATE,mStartTime);
				values.put(RunRecordTable.COLUMN_USEDTIME,mCurrentIndividualStatusSeconds);
				values.put(RunRecordTable.COLUMN_DISTANCE,mCurrentDistance);
				Log.d(TAG,"click ,add to databases");

				RunningMainActivity.this.getContentResolver().update(mInsertUri,
						values,
						RunRecordTable.COLUMN_USEDTIME + "=? AND "
		                        + RunRecordTable.COLUMN_DISTANCE + "=?",
		                        new String[] { String.valueOf(mCurrentIndividualStatusSeconds),
		                			    String.valueOf(mCurrentDistance)}
						);
//				if(mCurrentDistance < 10) {
//					Utils.createConfirmDialog(RunningMainActivity.this,
//							R.drawable.btn_green_mini, "提示", "你的跑步距离太短，请重新运动一会儿后再点击结束！",
//				            "确定","取消",
//				            null,
//				            null).show();
//				} else {
				
				Log.d("yxf","saveFile : " + mSaveFile.getAbsolutePath());
				savePointsToFiles(mPointLists, mSaveFile);
				Intent intent = new Intent(RunningMainActivity.this, HistoryDetailsActivity.class);
				intent.putExtra("_id",Integer.valueOf(mInsertUri.getLastPathSegment()));
				intent.putExtra("total_time", mCurrentIndividualStatusSeconds);
				intent.putExtra("total_distance",mCurrentDistance);
				intent.putExtra("start_time",mStartTime);
				startActivity(intent);
//				}
				
				mSportStatus = SportsManager.STATUS_READY;
				mMiddleButton.setText(R.string.status_start);
				mRightButton.setVisibility(View.INVISIBLE);
				mRunningStateLayout.setVisibility(View.GONE);
			 	mLockedControllerLayout.setVisibility(View.GONE);
			 	mCountDownView.setVisibility(View.GONE);
			 	initialAndResetAllWidegts();
				break;
			case R.id.img_bmap_full:
				if(!mIsBaiduMapFullScreen) {
				    mViewRunControllerLayout.setVisibility(View.GONE);
				    mOtherDetailsLayout.setVisibility(View.GONE);
				    configOrientalOrHorizontalLayout(true);
				    Log.d(TAG,"bmap onclick, height: " + mMapView.getHeight());
				    mIsBaiduMapFullScreen = true;
				}else {
					mViewRunControllerLayout.setVisibility(View.VISIBLE);
				    mOtherDetailsLayout.setVisibility(View.VISIBLE);
				    configOrientalOrHorizontalLayout(false);
				    Log.d(TAG,"bmap onclick, height: " + mMapView.getHeight());
				    mIsBaiduMapFullScreen = false;
				}
			default:
				break;
			}
		}
	};

	private void initialAndResetAllWidegts() {
		mCurrentIndividualStatusSeconds = 0;
		mRunDistanceTextView.setText(Utils.getValueWith2Suffix(0.0f));
		mRunTimeTextView.setText(TimeFormatHelper.formatTime(0));
	}
	
	private void configOrientalOrHorizontalLayout(boolean horizontal) {
		RelativeLayout.LayoutParams distanceParams = (RelativeLayout.LayoutParams) 
				mRunDistanceTextView.getLayoutParams();
		RelativeLayout.LayoutParams timeParams = (RelativeLayout.LayoutParams)
				mRunTimeTextView.getLayoutParams();
		if(horizontal) {
		    distanceParams.addRule(RelativeLayout.CENTER_HORIZONTAL, 0);
		    mRunDistanceTextView.setLayoutParams(distanceParams);
		    mRunDistanceTextView.setTextSize(30);
		    
		    timeParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
		    timeParams.addRule(RelativeLayout.ALIGN_BASELINE, R.id.tv_run_distance);
		    timeParams.addRule(RelativeLayout.BELOW,0);
		    mRunTimeTextView.setLayoutParams(timeParams);
		    mRunTimeTextView.setTextSize(30);
		} else {
			distanceParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
			mRunDistanceTextView.setLayoutParams(distanceParams);
		    mRunDistanceTextView.setTextSize(80);
		    
		    timeParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT,0);
		    timeParams.removeRule(RelativeLayout.ALIGN_BASELINE);
		    timeParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
		    timeParams.addRule(RelativeLayout.BELOW, R.id.tv_run_distance);
		    
		    mRunTimeTextView.setLayoutParams(timeParams);
		    mRunTimeTextView.setTextSize(30);
		}
	}
	
	private BDLocation mLocation;
	/**
	 * 定位SDK监听函数
	 */
	public class CustomLocationListenner implements BDLocationListener {
		@Override
		public void onReceiveLocation(BDLocation location) {
			Log.d(TAG,
					"onReceiveLocation(),getLatitude = "
							+ location.getLatitude() + ", getLongitude = "
							+ location.getLongitude()
							+ ", networkLocationType: "
							+ location.getNetworkLocationType()
							+ ", locationType:" + location.getLocType() + ", Thread :"+Thread.currentThread().getId());

			// map view 销毁后不在处理新接收的位置
			if (location == null || mMapView == null) {
				return;
			}
			mLocation = location;
			MyLocationData locData = new MyLocationData.Builder()
					.accuracy(location.getRadius())
					// 此处设置开发者获取到的方向信息，顺时针0-360
					.direction(100).latitude(location.getLatitude())
					.longitude(location.getLongitude()).build();
			mBaiduMap.setMyLocationData(locData);
			    mIsFirstLoc = false;
				LatLng ll = new LatLng(location.getLatitude(),
			            location.getLongitude());
				MapStatusUpdate u = MapStatusUpdateFactory.newLatLngZoom(ll, DEFAULT_ZOOM_LEVEL);
				mBaiduMap.animateMapStatus(u);
			// draw the sports path
			if (mSportStatus == SportsManager.STATUS_RUNNING && 
					(location.getLocType() == BDLocation.TypeGpsLocation ||
					(location.getLocType() == BDLocation.TypeNetWorkLocation))) {
				LatLng currentPoint = new LatLng(location.getLatitude(),
						location.getLongitude());
				List<LatLng> pointLists = new ArrayList<LatLng>();
				if (mPointBeforeLast != null && mPointBeforeLast != null) {
					pointLists.add(mPointBeforeLast);
					pointLists.add(mPointLast);
					pointLists.add(currentPoint);
					OverlayOptions ooPolyline = new PolylineOptions().width(8)
							.color(0xAAFF0000).points(pointLists);
					mBaiduMap.addOverlay(ooPolyline);
				}

				mPointBeforeLast = mPointLast;
				mPointLast = currentPoint;
				
				RunningApplication.mLongtitude = location.getLongitude();
                RunningApplication.mLatitude = location.getLatitude();
				//Task in the executor pool is used for storing all the points.
				mExecutor.submit(new PointsRecordTask(location));
			}
		}
		
		public void onReceivePoi(BDLocation poiLocation) {
            if (poiLocation == null){
                return ;
            }
        }
	}

	private boolean[] isPalyed = new boolean[1000];
	private GpsLocation mCurrentGpsLocation;
	private GpsLocation mPrevGpsLocation;
	private double mCurrentDistance;
	private List<LatLng> mPointLists = new ArrayList<LatLng>();
	private DataOutputStream mOutPut;
	
	private class PointsRecordTask implements Callable<String>{
		private BDLocation mLocation;
		
        public PointsRecordTask(BDLocation location){
            this.mLocation = location;
        }
        
		@Override
		public String call() throws Exception {
			// TODO Auto-generated method stub
			if(mSportStatus == SportsManager.STATUS_RUNNING && 
					(mLocation.getLocType() == BDLocation.TypeGpsLocation ||
					mLocation.getLocType() == BDLocation.TypeNetWorkLocation)) {
				
				mCurrentGpsLocation = new GpsLocation(mLocation.getLatitude(), mLocation.getLongitude());
                double addedDistance = 0.0f;
                DistanceComputeInterface distanceComputeInterface = DistanceComputeImpl.getInstance();
                if(mPrevGpsLocation != null) {
                	addedDistance = (float) distanceComputeInterface.getShortDistance(mPrevGpsLocation.lat,mPrevGpsLocation.lng,mCurrentGpsLocation.lat,mCurrentGpsLocation.lng);
                }
				if(addedDistance > 0) {
					mCurrentDistance += addedDistance;
				}
				BigDecimal b = new BigDecimal(mCurrentDistance / 1000); 
		    	double formatDistance = b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
		    	mUpdateDisplayHandler.sendMessage(Message.obtain(mUpdateDisplayHandler, UPDATE_DISTANCE,formatDistance));
		    	//Whether to play sound 
		    	if((formatDistance > 0) && ((formatDistance * 100) % 100) == 0 && (!mSoundPlayer.isPlaying())) {
		    		final int integerDistance = (int)formatDistance;
		    		if(!isPalyed[integerDistance]) {
		    			mSoundPlayer.play(SoundClips.TIMETICK_EACHMILE_SPORT, (int)formatDistance, 
		    				    mCurrentIndividualStatusSeconds, 
		    				    (mCurrentIndividualStatusSeconds - mTimeBeforeOneMileSeconds));
		    			isPalyed[integerDistance] = true;
		    		}
        		    
	    		    mTimeBeforeOneMileSeconds = mCurrentIndividualStatusSeconds;
		    	}
		    	mUpdateDisplayHandler.sendMessage(Message.obtain(mUpdateDisplayHandler, UPDATE_DETAILS,
		    			(int)mCurrentIndividualStatusSeconds,0,mCurrentDistance));
				mPrevGpsLocation = mCurrentGpsLocation;
				
			    mPointLists.add(new LatLng(mLocation.getLatitude(), mLocation.getLongitude()));
			    if(mCurrentIndividualStatusSeconds % SAVA_FILE_INTERVALS == 0) {
			        //Write to file
				    savePointsToFiles(mPointLists,mSaveFile);
				    mPointLists.clear();
			    }
			}
			return null;
		}
	}
	
	private void savePointsToFiles(List<LatLng> lists, File fileName) {
		Log.d("yxf","saveFile(), fileName = " + fileName);
		if (Utils.isSDcardExist() && (Utils.getSDFreeSize() > 10)) {
             try {
            	 if (!fileName.exists()) {
	                 fileName.createNewFile();
	             }
                 mOutPut = new DataOutputStream(new FileOutputStream(fileName,true));
                 for(LatLng point : lists) {
                     if(mOutPut != null) {
                    	 mOutPut.writeDouble(point.latitude);
                    	 mOutPut.writeChar('\t');
                    	 mOutPut.writeDouble(point.longitude);
                    	 mOutPut.writeChar('\n');
     		         } 
                 }
             } catch (FileNotFoundException e) {
                 e.printStackTrace();
             } catch (IOException e) {
			     // TODO Auto-generated catch block
			      e.printStackTrace();
			 }
             
             try {
	             mOutPut.close();
	         } catch (IOException e) {
		         // TODO Auto-generated catch block
			     e.printStackTrace();
	        }
	    }//if
	}
	private long mCurrentIndividualStatusSeconds = 0;
	private long mTimeBeforeOneMileSeconds = 0;
	private TimerTask mUpdateTimerValuesTask = null;
	private Timer mTimer;
	
	private final static int UPDATE_TIMER = 0x01;
	private final static int UPDATE_DISTANCE = 0x02;
	private final static int UPDATE_DETAILS = 0x03;
	
	private Handler mUpdateDisplayHandler = new Handler() {
		double distance;
        @Override
        public void handleMessage(Message msg) {
        	switch(msg.what) {
        	case UPDATE_TIMER:
                updateDisplay();
                break;
        	case UPDATE_DISTANCE:
        		distance = (Double)msg.obj;
		    	updateDistance(distance);
        		break;
        	case UPDATE_DETAILS:
        		distance = (Double)msg.obj;
        		int usedTime = msg.arg1;
        		updateDetails(distance,usedTime);
        	}
        }
    };
    
    //update the time and distance display
    protected void updateDisplay() {
    	mRunTimeTextView.setText(TimeFormatHelper.formatTime(mCurrentIndividualStatusSeconds));
    }
    
    private void updateDetails(final double distance, final int usedTime) {
    	if(distance == 0 || usedTime == 0) {
    		return;
    	}
    	double averageSpeed = (60 * 60 * distance) / 1000 / usedTime;   //km/h
    	double paceSpeed = (1000 * usedTime ) / distance; //seconds
    	BigDecimal b = new BigDecimal(averageSpeed); 
    	double formatSpeed = b.setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    	
    	mAverageSpeedView.setText(String.valueOf(formatSpeed));
    	mPaceSpeedView.setText(TimeFormatHelper.formatTime((int)paceSpeed));
    }
	/**
     * 开启一个Timer，每隔一秒钟更新一下界面
     */
    private synchronized void startTimer() {
        mTimer = new Timer();
        /**
         * void schedule (TimerTask task, long delay, long period)
         * task--需要执行的任务
         * delay--用户调用schedule()方法后多长时间开始执行run()方法，以毫秒为单位；
         * period--第一次调用之后，以后每隔多长时间再一次执行run()方法，以毫秒为单位。
         */
        if(mUpdateTimerValuesTask == null) {
        	mUpdateTimerValuesTask = new TimerTask() {
                @Override
                public void run() {
                	if(mSportStatus == SportsManager.STATUS_RUNNING) {
                	    Log.d(TAG,"updateTimerValuesTask, run(), Thread: " + Thread.currentThread().getId());
                        updateTimerValues();
                        //new UpdateDistanceTask().execute();
                	}else {
                		this.cancel();
                	}
                }
            };
        }
        mTimer.schedule(mUpdateTimerValuesTask, 1000, 1000);
    }
    
    protected synchronized void updateTimerValues() {
    	mCurrentIndividualStatusSeconds++;
        Log.d(TAG,"updateTimerValues(), currentIndividualStatusSeconds = " + mCurrentIndividualStatusSeconds);
        mUpdateDisplayHandler.sendMessage(Message.obtain(mUpdateDisplayHandler, UPDATE_TIMER));
    }
    
    private synchronized void cancelTimer() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        if(mUpdateTimerValuesTask != null) {
        	mUpdateTimerValuesTask.cancel();
        	mUpdateTimerValuesTask = null;
        }
    }
	    
	@Override
	protected void onPause() {
		mMapView.onPause();
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
	}

	
	
	@Override
	protected void onResume() {
		mMapView.onResume();
		super.onResume();
		Log.d(TAG,"onResume, height: " + mMapView.getHeight());
		mSoundPlayer = SoundClips.getPlayer(this);
		String gpsSinalDescription = String.format(
				getResources().getString(R.string.gps_signal_text),
				GpsManager.isGpsOpen(this) ? getResources().getString(R.string.bad_gps) :
					getResources().getString(R.string.no_gps)
				);
		mGpsSignalTextView.setText(gpsSinalDescription);
		userIcon =(ImageButton)findViewById(R.id.user_icon);
		userIcon.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
                                Intent intent = new Intent();
				intent.setAction(Constants.ACTION_CHECK_PROFILE_INFO);
				startActivity(intent);
				/*
				RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) layout_left
						.getLayoutParams();
				TextView title = (TextView) findViewById(R.id.title);
				// 左移动
				if (layoutParams.leftMargin >= 0) {
					new AsynMove().execute(-SPEED);
					title.setVisibility(View.VISIBLE);
				} else {
					// 右移动
					new AsynMove().execute(SPEED);
					title.setVisibility(View.GONE);
				}
				*/
			}
		} );
	}

	 private class MyOnGestureListener implements OnGestureListener {
		  @Override
		  public boolean onDown(MotionEvent e) {
			  Log.d(TAG, "onDown: " + e.getAction());
	
		    return true;
		  }
		  /***
		   * e1 是起点，e2是终点，如果distanceX=e1.x-e2.x>0说明向左滑动。反之亦如此.
		   */
		  @Override
		  public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
		      float distanceY) {
			  Log.d(TAG, "onScroll: " + e1.getAction() + " : " + e2.getAction() 
					  + " : " + distanceX + " : " + distanceY);
			    mScrollX += distanceX;// distanceX:向左为正，右为负
			    RelativeLayout.LayoutParams layoutParams_left = (RelativeLayout.LayoutParams) layout_left
			        .getLayoutParams();
			    RelativeLayout.LayoutParams layoutParams_right = (RelativeLayout.LayoutParams) layout_right
				        .getLayoutParams();
			    
			    layoutParams_left.leftMargin -= mScrollX;
			    layoutParams_left.rightMargin += mScrollX;
			    layoutParams_right.leftMargin -=mScrollX;
			    //Log.d(TAG, "rightMargin = " + layoutParams.rightMargin + "\nleftMargin = " + layoutParams.leftMargin);
			    if (layoutParams_left.leftMargin >= 0) {
			      layoutParams_left.leftMargin = 0;
			      layoutParams_left.rightMargin = window_width-max_width;
			      layoutParams_right.leftMargin = max_width;

			    } else if (layoutParams_left.leftMargin <= -max_width) {
			         // 拖过头了不需要再执行AsynMove了
			      layoutParams_left.leftMargin = -max_width;
			      layoutParams_left.rightMargin = max_width;
			      layoutParams_right.leftMargin = 0;
			    } else {
				      Log.d(TAG, "leftMargin = " + layoutParams_left.leftMargin);
				      // 缩回去
				      if (layoutParams_left.leftMargin > max_width / 2) {
				        new AsynMove().execute(SPEED);
				      } else {
				        new AsynMove().execute(-SPEED);
				      }
				    
			    }
			    layout_left.setLayoutParams(layoutParams_left);
			    layout_right.setLayoutParams(layoutParams_right);
			  
		    return true;
		  }
		  @Override
		  public void onLongPress(MotionEvent e) {
			  Log.d(TAG, "onLongPress");
		  }
		  @Override
		  public void onShowPress(MotionEvent e) {
			  Log.d(TAG, "onShowPress");
		  }
		  @Override
		  public boolean onSingleTapUp(MotionEvent e) {
			  Log.d(TAG, "onSingleTapUp");

		    return false;
		  }
		  @Override
		  public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
		      float velocityY) {
			  Log.d(TAG, "onFling");
		    return false;
		  }
	  }
	
	
	class AsynMove extends AsyncTask<Integer, Integer, Void> {

		@Override
		protected Void doInBackground(Integer... params) {
			int times = 0;
			if (max_width % Math.abs(params[0]) == 0)// 整除
				times = max_width / Math.abs(params[0]);
			else
				times = max_width / Math.abs(params[0]) + 1;// 有余数

			for (int i = 0; i < times; i++) {
				publishProgress(params[0]);
				try {
					Thread.sleep(SLEEP_TIME);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			return null;
		}
		
		/**
		 * update UI
		 */
		@Override
		protected void onProgressUpdate(Integer... values) {
			RelativeLayout.LayoutParams layoutParams_left = (RelativeLayout.LayoutParams) layout_left
					.getLayoutParams();
			RelativeLayout.LayoutParams layoutParams_right = (RelativeLayout.LayoutParams) layout_right
					.getLayoutParams();
			// 右移动
			if (values[0 ] > 0) {
				layoutParams_right.leftMargin = Math.min(layoutParams_right.leftMargin
						+ values[0], max_width);
				layoutParams_left.leftMargin = Math.min(layoutParams_left.leftMargin
						+ values[0], 0);
				layoutParams_left.rightMargin =Math.max(layoutParams_left.rightMargin
						- values[0], window_width-max_width);
				
				Log.v(TAG, "\n**** Move to Right  *****\n" 
						+ "layout_right: " + layoutParams_right.leftMargin
						+ "\nlayout_left: " + layoutParams_left.leftMargin);
			} else {
				// 左移动
				layoutParams_right.leftMargin = Math.max(layoutParams_right.leftMargin
						+ values[0], 0);
				layoutParams_left.leftMargin = Math.max(layoutParams_left.leftMargin
						+ values[0], -max_width);
				layoutParams_left.rightMargin = Math.min(layoutParams_left.rightMargin
						-values[0], max_width);
				Log.v(TAG, "\n**** Move to Left  *****\n" 
						+ "layout_right: " + layoutParams_right.leftMargin
						+ "\nlayout_left: " + layoutParams_left.leftMargin);
			}
			layout_right.setLayoutParams(layoutParams_right);
			layout_left.setLayoutParams(layoutParams_left);

		}

	}
	
	@Override
	protected void onDestroy() {
		// 退出时销毁定位
		mLocClient.stop();
		// 关闭定位图层
		mBaiduMap.setMyLocationEnabled(false);
		mMapView.onDestroy();
		mMapView = null;
		synchronized(this) {
            cancelTimer();
        }
		super.onDestroy();
		if (mSoundPlayer != null) {
            mSoundPlayer.release();
            mSoundPlayer = null;
        }
	}
	
	private void updateDistance(double result) {
		mRunDistanceTextView.setText(String.valueOf(result));
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		
		if(keyCode == KeyEvent.KEYCODE_BACK) {
			new AlertDialog.Builder(this)
			        .setTitle("提示")
			        .setMessage("确定要退出吗?")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							// TODO Auto-generated method stub
							if(mSportStatus == SportsManager.STATUS_PAUSED || mSportStatus == SportsManager.STATUS_RUNNING) {
								Toast.makeText(RunningMainActivity.this, "必须先结束跑步才能退出！", Toast.LENGTH_SHORT).show();
								return;
							}else {
								RunningMainActivity.this.finish();
							}
						}
					})
					.setNegativeButton("后台运行", new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							// TODO Auto-generated method stub
							 ResolveInfo resolveInfo = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).
									 addCategory(Intent.CATEGORY_HOME), 0);
							 ActivityInfo activityInfo = resolveInfo.activityInfo;
							 Intent intent = new Intent(Intent.ACTION_MAIN);
							 intent.addCategory(Intent.CATEGORY_LAUNCHER);
							 intent.setComponent(new ComponentName(activityInfo.packageName,activityInfo.name));
							 startActivitySafely(intent);
						}
					}).show();
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
    private void startActivitySafely(Intent intent) {    
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);    
	    try {    
	        startActivity(intent);    
	    } catch (ActivityNotFoundException e) {    
	        e.printStackTrace(); 
	    } catch (SecurityException e) {
		   e.printStackTrace();
	    }    
	}    
	
}
