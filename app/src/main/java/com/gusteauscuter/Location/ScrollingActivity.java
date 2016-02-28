package com.gusteauscuter.Location;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class ScrollingActivity extends AppCompatActivity {

    private static final int CHECK_INTERVAL = 1000 * 10;
    private static final int NETWORK_LISTENER_INTERVAL = 1000 * 1;
    private static final int GPS_LISTENER_INTERVAL = 1000 * 2;
    private static final float MIN_DISTANCE = 0;

    private static final String STATUS_START="start";
    private static final String STATUS_STOP="stop";
    private static final String STATUS_UPDATE="update";

    private int timesOfLocationUpdate = 0;
    private int timesOfGpsUpdate = 0;
    private int timesOfNetworkUpdate = 0;
    private int timesSatelliteStatus = 0;
    private int countSatellites = 0;
    private int countSatellitesValid = 0;

    private String timeString = "";
    private String mAddress = "\n";


    private boolean isColletStarted = false;
    //代码中慎用此项判断条件
    private boolean locationExist = true;

    private TextView currentLocationInfoTV;
    private TextView controlInfoTV;
    private TextView satellitesInfoTV;
    private FloatingActionButton fab;

    private static final String TAG = "LocationTagInfo";

    private LocationManager locationManager;
    private LocationListener gpsListener = null;
    private LocationListener networkListner = null;
    private Location currentLocation;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationListener();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        controlInfoTV = (TextView) findViewById(R.id.controlInfoTV);
        currentLocationInfoTV = (TextView) findViewById(R.id.currentLocationInfoTV);
        satellitesInfoTV = (TextView) findViewById(R.id.satellitesInfoTV);
        fab = (FloatingActionButton) findViewById(R.id.fab);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //获取定位服务
                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

                //开启GPS信息获取后台服务
                if (!isColletStarted) {
                    isColletStarted = true;
                    //注册监听事件
                    registerLocationListener();

                    Snackbar.make(view, "Start GPS Information Collect Service", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();

                } else {
                    isColletStarted = false;

                    //更新控制信息
                    updateControlInfo();
                    //注销监听
                    stopLocationListener();

                    Snackbar.make(view, "Stop GPS Information Collect Service", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
                }
            }
        });
    }

    private void registerLocationListener() {

        //判断GPS是否正常启动
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "请开启GPS导航,选择精确定位模式...", Toast.LENGTH_SHORT).show();
            //返回开启GPS导航设置界面
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivityForResult(intent, 0);
            //return;
        }

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "应用未获取权限，请开启应用权限后重试...", Toast.LENGTH_SHORT).show();
            return;
        }
        //监听gps状态
        locationManager.addGpsStatusListener(listenerGpsStatus);
        networkListner = new MyLocationListner();
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, NETWORK_LISTENER_INTERVAL, MIN_DISTANCE, networkListner);
        gpsListener = new MyLocationListner();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_LISTENER_INTERVAL, MIN_DISTANCE, gpsListener);


        new posToSever(STATUS_START).execute();
    }

    private void stopLocationListener() {

        fab.setVisibility(View.VISIBLE);

        timesOfLocationUpdate = 0;
        timesOfGpsUpdate = 0;
        timesOfNetworkUpdate = 0;
        timesSatelliteStatus = 0;
        //关闭服务
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "关闭监听服务,没有LocationManager权限");
            return;
        }
        locationManager.removeUpdates(networkListner);
        locationManager.removeUpdates(gpsListener);
        locationManager.removeGpsStatusListener(listenerGpsStatus);
        Log.e(TAG, "===成功关闭监听服务");

        new posToSever(STATUS_STOP).execute();

    }


    //位置监听
    private class MyLocationListner implements LocationListener {

        /**
         * 位置信息变化时触发
         */
        public void onLocationChanged(Location location) {

            if (LocationManager.GPS_PROVIDER.equals(location.getProvider()))
                timesOfGpsUpdate++;
            else if (LocationManager.NETWORK_PROVIDER.equals(location.getProvider()))
                timesOfNetworkUpdate++;

            // Called when a new location is found by the location provider.
            Log.i(TAG, "Got New Location of provider:" + location.getProvider());
            if (currentLocation != null) {
                if (isBetterLocation(location, currentLocation)) {
                    Log.i(TAG, "It's a better location");
                    currentLocation = location;
                    updateLocationInfo(location);
                } else {
                    Log.i(TAG, "Not very good!");
                }
            } else {
                Log.i(TAG, "It's first location");
                currentLocation = location;
                updateLocationInfo(location);
            }

            fabBlin();

        }

        /**
         * GPS状态变化时触发
         */
        public void onStatusChanged(String provider, int status, Bundle extras) {
            switch (status) {
                //GPS状态为可见时
                case LocationProvider.AVAILABLE:
                    Log.i(TAG, "当前GPS状态为可见状态");
                    break;
                //GPS状态为服务区外时
                case LocationProvider.OUT_OF_SERVICE:
                    Log.i(TAG, "当前GPS状态为服务区外状态");
                    break;
                //GPS状态为暂停服务时
                case LocationProvider.TEMPORARILY_UNAVAILABLE:
                    Log.i(TAG, "当前GPS状态为暂停服务状态");
                    break;
            }
        }

        /**
         * GPS开启时触发
         */
        public void onProviderEnabled(String provider) {
            satellitesInfoTV.setText(getResources().getString(R.string.gps_locationING));
        }

        /**
         * GPS禁用时触发
         */
        public void onProviderDisabled(String provider) {
            satellitesInfoTV.setText(getResources().getString(R.string.gps_outOfSevice));
        }

    }


    //状态监听
    GpsStatus.Listener listenerGpsStatus = new GpsStatus.Listener() {

        public void onGpsStatusChanged(int event) {
            timesSatelliteStatus++;
            switch (event) {
                //第一次定位
                case GpsStatus.GPS_EVENT_FIRST_FIX:
                    Log.i(TAG, "第一次定位");
                    break;
                //卫星状态改变
                case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                    Log.i(TAG, "卫星状态改变");
                    updateSatellitesInfo();
                    break;
                //定位启动
                case GpsStatus.GPS_EVENT_STARTED:
                    Log.i(TAG, "定位启动");
                    break;
                //定位结束
                case GpsStatus.GPS_EVENT_STOPPED:
                    Log.i(TAG, "定位结束");
                    break;
            }
            updateControlInfo();

            fabBlin();
        }


    };

    private void fabBlin() {
        if (fab.getVisibility() == View.VISIBLE) {
            fab.setVisibility(View.INVISIBLE);
        } else {
            fab.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 实时更新文本内容
     *
     * @param location 更新的位置信息
     */
    private void updateLocationInfo(Location location) {
        if (location != null) {
            locationExist = true;
            timesOfLocationUpdate++;

            long time = location.getTime();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            timeString = formatter.format(new Date(time));
            currentLocationInfoTV.setText("====位置信息===");
            currentLocationInfoTV.append("\n@来源 ：" + location.getProvider());
            currentLocationInfoTV.append("\t\t精度 ：" + location.getAccuracy() + "m");
            currentLocationInfoTV.append("\n@时间 ：" + timeString);

            currentLocationInfoTV.append("\n@经度 ：" + String.valueOf(location.getLongitude()));
            currentLocationInfoTV.append("\n@纬度 ：" + String.valueOf(location.getLatitude()));
            currentLocationInfoTV.append("\n@海拔 ：" + location.getAltitude() + "m");
            currentLocationInfoTV.append("\n@速度 ：" + location.getSpeed() + "m/s");
            currentLocationInfoTV.append("\n@方向 ：" + location.getBearing());
            //currentLocationInfoTV.append("\n格林威治时间：" + 1000*location.getTime());
            //currentLocationInfoTV.append("\n系统上电时间：     " +location.getElapsedRealtimeNanos());
            //currentLocationInfoTV.append("\n额外来源：" +location.getExtras());

            Log.i(TAG, "时间：" + timeString);
            Log.i(TAG, "经度：" + location.getLongitude());
            Log.i(TAG, "纬度：" + location.getLatitude());
            Log.i(TAG, "海拔：" + location.getAltitude());

            //查询地理位置信息
            getGeocoder(location);
            //上传信息到服务器
            new posToSever(STATUS_UPDATE,location,mAddress,timeString).execute();
            //更新控制信息
            updateControlInfo();

        } else {
            //定位失败提示信息
            //locationExist=false;
            currentLocationInfoTV.setText("信噪比过低，移步开阔地段，重试...");
            Log.i(TAG, "定位失败！");
        }
    }

    private void getGeocoder(Location location){
        //地理位置信息
        Geocoder gc = new Geocoder(this);
        List<Address> addresses = null;
        try {
            //根据经纬度获得地址信息
            addresses = gc.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            Log.i(TAG, "获取地理信息成功");

            if (addresses.size() > 0) {
                //获取address类的成员信息
                mAddress = "*地址：" + addresses.get(0).getAddressLine(0) ;
                //mAddress += "*国家：" + addresses.get(0).getCountryName() + "\n";
                //mAddress += "*城市：" + addresses.get(0).getLocality() +addresses.get(0).getSubLocality() + "\n";
                //mAddress += "*电话：" + addresses.get(0).getPhone() + "\n";
                mAddress += "[附近]" + addresses.get(0).getFeatureName();
                currentLocationInfoTV.append("\n"+mAddress);
                Log.i(TAG, mAddress);
            }
        } catch (IOException e) {
            Log.e(TAG, "获取地理信息失败");
            e.printStackTrace();
        }
    }


//    private void posToSever(Location location,String address, String timeString){
//        try {
//            initConn(location, address, timeString);
//            Log.i("-1", "=====发送请求成功！====");
//        } catch (IOException e) {
//            Log.i("-2", "=====请求超时！====");
//            e.printStackTrace();
//            return;
//        }
//    }

    class posToSever extends AsyncTask<String ,Void,Void>{

        String statusAT;
        Location locationAT;
        String addressAT;
        String timeAT;

        public posToSever(String status){
            statusAT=status;
        }

        public posToSever(String status, Location location,String address, String time){
            statusAT=status;
            locationAT=location;
            addressAT=address;
            timeAT=time;
        }

        /**
         * Override this method to perform a computation on a background thread. The
         * specified parameters are the parameters passed to {@link #execute}
         * by the caller of this task.
         * <p/>
         * This method can call {@link #publishProgress} to publish updates
         * on the UI thread.
         *
         * @param params The parameters of the task.
         * @return A result, defined by the subclass of this task.
         * @see #onPreExecute()
         * @see #onPostExecute
         * @see #publishProgress
         */
        @Override
        protected Void doInBackground(String... params) {
            try {

                URL url =new URL(getString(R.string.severPath));
                HttpURLConnection urlConnection= (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("Charset", "UTF-8");
                urlConnection.setConnectTimeout(30000);
                urlConnection.setDoInput(true);
                urlConnection.setUseCaches(false);

                StringBuffer requestPro = new StringBuffer();
                requestPro.append("STATUS=" + statusAT);
                requestPro.append("&time=" + timeAT);

                if(statusAT.equals(STATUS_UPDATE)) {
                    requestPro.append("&longitude="+ String.format("%08.5f",locationAT.getLongitude()));
                    requestPro.append("&latitude="+ String.format("%08.5f", locationAT.getLatitude()));
                    requestPro.append("&altitude="+ String.format("%08.2f", locationAT.getAltitude()));
                    requestPro.append("&provider="+ String.format("%7s",locationAT.getProvider()));
                    requestPro.append("&accuracy="+ String.format("%08.5f", locationAT.getAccuracy()));
                    requestPro.append("&speed="+ String.format("%08.2f", locationAT.getSpeed()));
                    requestPro.append("&bearing="+ String.format("%05.2f",locationAT.getBearing()));
                    //requestPro.append("&address=" + addressAT);
                    requestPro.append("&satellites=" + countSatellitesValid + "|" +countSatellites );
                }

                // 表单参数与get形式一样
                byte[] bytes = requestPro.toString().getBytes();
                urlConnection.getOutputStream().write(bytes);// 输入参数

                urlConnection.connect();

                System.out.println(urlConnection.getResponseCode()+urlConnection.getResponseMessage()); //响应代码 200表示成功
                urlConnection.disconnect();

                Log.i("-1", "=====发送状态==成功！====");
            } catch (IOException e) {
                Log.i("-2", "=====发送状态==超时！====");
                e.printStackTrace();

            }
            return null;
        }
    }



    private void updateSatellitesInfo() {
        GpsStatus gpsStatus = locationManager.getGpsStatus(null);
        //获取卫星颗数的默认最大值
        int maxSatellites = gpsStatus.getMaxSatellites();
        //创建一个迭代器保存所有卫星
        Iterator<GpsSatellite> iters = gpsStatus.getSatellites().iterator();
        if (iters.hasNext()) {
            satellitesInfoTV.setText("===卫星数据===");
        }
        countSatellites = 0;
        countSatellitesValid = 0;
        while (iters.hasNext() && countSatellites <= maxSatellites) {
            countSatellites++;
            GpsSatellite s = iters.next();
            if (countSatellites < 10)
                satellitesInfoTV.append("\n#卫星0" + countSatellites);
            else
                satellitesInfoTV.append("\n#卫星" + countSatellites);
            satellitesInfoTV.append("\t\t\t方向角" + s.getAzimuth());
            satellitesInfoTV.append("\t\t高度角" + s.getElevation());
            satellitesInfoTV.append("\t\t信噪比" + s.getSnr());
            //satellitesInfoTV.append("\t\t伪随机数" +s.getPrn());

            if (s.getSnr() != 0)
                countSatellitesValid++;
        }
        updateControlInfo();
    }

    private void updateControlInfo() {
        controlInfoTV.setText("===当前状态===");
        if (!locationExist) {
            controlInfoTV.setText("获取定位信息失败！请检查...");
        } else {
            if (isColletStarted) {
                controlInfoTV.append("实时更新中...");
                //controlInfoTV.append("卫星监听次数：" + timesSatelliteStatus);

            } else
                controlInfoTV.append("更新停止.");
        }
        controlInfoTV.append("\n有效位置次数：" + timesOfLocationUpdate);
        controlInfoTV.append("\t\t\t最小距离：" + MIN_DISTANCE + "m");
        controlInfoTV.append("\n网络位置更新：" + timesOfNetworkUpdate);
        controlInfoTV.append("\t\t\t监听周期：" + NETWORK_LISTENER_INTERVAL / 1000 + "s");
        controlInfoTV.append("\n卫星位置更新：" + timesOfGpsUpdate);
        controlInfoTV.append("\t\t\t监听周期：" + GPS_LISTENER_INTERVAL / 1000 + "s");
        controlInfoTV.append("\n搜索卫星数量：" + countSatellites + " | " + countSatellitesValid);
        controlInfoTV.append("\n卫星监听次数：" + timesSatelliteStatus);

    }

    /**
     * 返回查询条件
     *
     * @return
     */
    private Criteria getCriteria() {
        Criteria criteria = new Criteria();
        //设置定位精确度 Criteria.ACCURACY_COARSE比较粗略，Criteria.ACCURACY_FINE则比较精细
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        //设置是否要求速度
        criteria.setSpeedRequired(false);
        //设置是否需要方位信息
        criteria.setBearingRequired(false);
        //设置是否需要海拔信息
        criteria.setAltitudeRequired(false);
        // 设置对电源的需求
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        // 设置是否允许运营商收费
        criteria.setCostAllowed(false);
        return criteria;
    }


    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > CHECK_INTERVAL;
        boolean isSignificantlyOlder = timeDelta < -CHECK_INTERVAL;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location,
        // use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must
            // be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation
                .getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and
        // accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate
                && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

}
