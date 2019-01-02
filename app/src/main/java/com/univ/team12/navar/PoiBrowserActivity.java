package com.univ.team12.navar;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.beyondar.android.view.OnClickBeyondarObjectListener;
import com.beyondar.android.world.BeyondarObject;
import com.beyondar.android.world.GeoObject;
import com.beyondar.android.world.World;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.SphericalUtil;
import com.univ.team12.navar.ar.ArBeyondarGLSurfaceView;
import com.univ.team12.navar.ar.ArFragmentSupport;
import com.univ.team12.navar.ar.OnTouchBeyondarViewListenerMod;
import com.univ.team12.navar.network.PlaceResponse;
import com.univ.team12.navar.network.PoiResponse;
import com.univ.team12.navar.network.RetrofitInterface;
import com.univ.team12.navar.network.poi.Result;
import com.univ.team12.navar.utils.UtilsCheck;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;

/**
 * Created by Amal Krishnan on 10-04-2017.
 */

public class PoiBrowserActivity extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks
    ,GoogleApiClient.OnConnectionFailedListener,OnClickBeyondarObjectListener,
        OnTouchBeyondarViewListenerMod{

    private final static String TAG="PoiBrowserActivity";

    private TextView textView;
    private Location mLastLocation;
    private GoogleApiClient mGoogleApiClient;
    private LayoutInflater layoutInflater;
    private ArFragmentSupport arFragmentSupport;
    private World world;
    private int minSpace = 2000;

    @BindView(R.id.poi_place_detail)
    CardView poi_cardview;
    @BindView(R.id.poi_place_close_btn)
    ImageButton poi_cardview_close_btn;
    @BindView(R.id.poi_place_name)
    TextView poi_place_name;
    @BindView(R.id.poi_place_address)
    TextView poi_place_addr;
    @BindView(R.id.poi_place_image)
    ImageView poi_place_image;
    @BindView(R.id.poi_place_ar_direction)
    Button poi_place_ar_btn;
    @BindView(R.id.poi_place_maps_direction)
    Button poi_place_maps_btn;
    @BindView(R.id.poi_brwoser_progress)
    ProgressBar poi_browser_progress;
    @BindView(R.id.seekBar)
    SeekBar seekbar;
    @BindView(R.id.seekbar_cardview)
    CardView seekbar_cardview;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_poi_browser);
        ButterKnife.bind(this);

        seekbar_cardview.setVisibility(View.GONE);
        poi_browser_progress.setVisibility(View.GONE);
        poi_cardview.setVisibility(View.GONE);

        if(!UtilsCheck.isNetworkConnected(this)){
            Snackbar mySnackbar = Snackbar.make(findViewById(R.id.poi_layout),
                    "Turn Internet On", Snackbar.LENGTH_SHORT);
            mySnackbar.show();
        }

        arFragmentSupport = (ArFragmentSupport) getSupportFragmentManager().findFragmentById(
                R.id.poi_cam_fragment);
        arFragmentSupport.setOnClickBeyondarObjectListener(this);
        arFragmentSupport.setOnTouchBeyondarViewListener(this);


        textView=(TextView) findViewById(R.id.loading_text);

        Set_googleApiClient(); //Sets the GoogleApiClient

        poi_cardview_close_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                seekbar_cardview.setVisibility(View.VISIBLE);
                poi_cardview.setVisibility(View.GONE);
                poi_place_image.setImageResource(android.R.color.transparent);
                poi_place_name.setText(" ");
                poi_place_addr.setText(" ");
            }
        });

        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

                if(i==0){
                    Poi_list_call(minSpace);
                }else{
                    Poi_list_call((i+1)*minSpace);
                }

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(seekBar.getProgress()==0){
                    Toast.makeText(PoiBrowserActivity.this, "Radius: 1000 Metres", Toast.LENGTH_SHORT).show();
                }else{
                    Toast.makeText(PoiBrowserActivity.this, "Radius: "+(seekBar.getProgress()+1)*minSpace+" Metres", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    void Poi_list_call(int radius){
        poi_browser_progress.setVisibility(View.VISIBLE);
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor).build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getResources().getString(R.string.directions_base_url))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RetrofitInterface apiService =
                retrofit.create(RetrofitInterface.class);

        final Call<PoiResponse> call = apiService.listPOI(String.valueOf(mLastLocation.getLatitude())+","+
                String.valueOf(mLastLocation.getLongitude()),radius,
                getResources().getString(R.string.google_maps_key));

        call.enqueue(new Callback<PoiResponse>() {
            @Override
            public void onResponse(Call<PoiResponse> call, Response<PoiResponse> response) {

                poi_browser_progress.setVisibility(View.GONE);
                seekbar_cardview.setVisibility(View.VISIBLE);

                List<Result> poiResult=response.body().getResults();

                Configure_AR(poiResult);
            }

            @Override
            public void onFailure(Call<PoiResponse> call, Throwable t) {
                poi_browser_progress.setVisibility(View.GONE);
            }
        });

    }

    void Poi_details_call(String placeid){

        poi_browser_progress.setVisibility(View.VISIBLE);

        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor).build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getResources().getString(R.string.directions_base_url))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RetrofitInterface apiService =
                retrofit.create(RetrofitInterface.class);

        final Call<PlaceResponse> call = apiService.getPlaceDetail(placeid,
                getResources().getString(R.string.google_maps_key));

        call.enqueue(new Callback<PlaceResponse>() {
            @Override
            public void onResponse(Call<PlaceResponse> call, Response<PlaceResponse> response) {

                seekbar_cardview.setVisibility(View.GONE);
                poi_cardview.setVisibility(View.VISIBLE);
                poi_browser_progress.setVisibility(View.GONE);

                final com.univ.team12.navar.network.place.Result result=response.body().getResult();

                poi_place_name.setText(result.getName());
                poi_place_addr.setText(result.getFormattedAddress());

                try {
                    HttpUrl url = new HttpUrl.Builder()
                            .scheme("https")
                            .host("maps.googleapis.com")
                            .addPathSegments("maps/api/place/photo")
                            .addQueryParameter("maxwidth", "400")
                            .addQueryParameter("photoreference", result.getPhotos().get(0).getPhotoReference())
                            .addQueryParameter("key", getResources().getString(R.string.google_maps_key))
                            .build();

                    new PoiPhotoAsync().execute(url.toString());

                }catch (Exception e){
                    Log.d(TAG, "onResponse: "+e.getMessage());
                    Toast.makeText(PoiBrowserActivity.this, "No image available", Toast.LENGTH_SHORT).show();
                }

                poi_place_maps_btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent;
                        try{
                            Uri.Builder builder = new Uri.Builder();
                            builder.scheme("http")
                                    .authority("maps.google.com")
                                    .appendPath("maps")
                                    .appendQueryParameter("saddr", mLastLocation.getLatitude()+","+mLastLocation.getLongitude())
                                    .appendQueryParameter("daddr",result.getGeometry().getLocation().getLat()+","+
                                                    result.getGeometry().getLocation().getLng());

                            intent = new Intent(android.content.Intent.ACTION_VIEW,
                                    Uri.parse( builder.build().toString()));
                            startActivity(intent);
                            finish();
                        }catch (Exception e){
                            Log.d(TAG, "onClick: mapNav Exception caught");
                            Toast.makeText(PoiBrowserActivity.this, "Unable to Open Maps Navigation", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                poi_place_ar_btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent=new Intent(PoiBrowserActivity.this,ArCamActivity.class);

                        try {
                            intent.putExtra("SRC", "Current Location");
                            intent.putExtra("DEST",  result.getGeometry().getLocation().getLat()+","+
                                    result.getGeometry().getLocation().getLng());
                            intent.putExtra("SRCLATLNG",  mLastLocation.getLatitude()+","+mLastLocation.getLongitude());
                            intent.putExtra("DESTLATLNG", result.getGeometry().getLocation().getLat()+","+
                                    result.getGeometry().getLocation().getLng());
                            startActivity(intent);
                            finish();
                        }catch (NullPointerException npe){
                            Log.d(TAG, "onClick: The IntentExtras are Empty");
                        }
                    }
                });

            }

            @Override
            public void onFailure(Call<PlaceResponse> call, Throwable t) {
                poi_browser_progress.setVisibility(View.GONE);
            }
        });

    }

    public class PoiPhotoAsync extends AsyncTask<String,Void,Bitmap> {

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            poi_place_image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poi_place_image.setImageBitmap(bitmap);
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            String imageURL = urls[0];

            Bitmap bitmap = null;
            try {
                InputStream input = new java.net.URL(imageURL).openStream();
                bitmap = BitmapFactory.decodeStream(input);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return bitmap;
        }
    }

    private void Configure_AR(List<Result> pois){

        layoutInflater=getLayoutInflater();

        world=new World(getApplicationContext());
        world.setGeoPosition(mLastLocation.getLatitude(),mLastLocation.getLongitude());
        world.setDefaultImage(R.drawable.map_marker);

        arFragmentSupport.getGLSurfaceView().setPullCloserDistance(25);

        for(int i=0;i<pois.size();i++) {
            Result poi = pois.get(i);

            double distance = Math.round(SphericalUtil.computeDistanceBetween(new LatLng(mLastLocation.getLatitude(),mLastLocation.getLongitude()), new LatLng(poi.getGeometry().getLocation().getLat(),
                            poi.getGeometry().getLocation().getLng())));

            GeoObject poiGeoObj=new GeoObject(minSpace*(i+1));
            poiGeoObj.setGeoPosition(poi.getGeometry().getLocation().getLat(),
                    poi.getGeometry().getLocation().getLng());
            poiGeoObj.setName(poi.getPlaceId());
            poiGeoObj.setDistanceFromUser(distance);

            String type=poi.getTypes().get(0);
            if(type.equals(getResources().getString(R.string.restaurant))){
                poiGeoObj.setImageResource(R.drawable.food_fork_drink);
            }else if(type.equals(getResources().getString(R.string.logding)) || type.equals(getResources().getString(R.string.locality)) || type.equals(getResources().getString(R.string.local_government_office)) || type.equals(getResources().getString(R.string.establishment)) || type.equals(getResources().getString(R.string.sublocality_level_1))){
                poiGeoObj.setImageResource(R.drawable.office);
            }else if(type.equals(getResources().getString(R.string.atm)) || type.equals(getResources().getString(R.string.bank)) || type.equals(getResources().getString(R.string.finance))){
                poiGeoObj.setImageResource(R.drawable.cash_usd);
            }else if(type.equals(getResources().getString(R.string.hosp))){
                poiGeoObj.setImageResource(R.drawable.hospital);
            }else if(type.equals(getResources().getString(R.string.movie))){
                poiGeoObj.setImageResource(R.drawable.filmstrip);
            }else if(type.equals(getResources().getString(R.string.cafe))){
                poiGeoObj.setImageResource(R.drawable.coffee);
            }else if(type.equals(getResources().getString(R.string.bakery))){
                poiGeoObj.setImageResource(R.drawable.food);
            }else if(type.equals(getResources().getString(R.string.mall)) || type.equals(getResources().getString(R.string.shopping_mall)) || type.equals(getResources().getString(R.string.store)) || type.equals(getResources().getString(R.string.home_goods_store)) || type.equals(getResources().getString(R.string.furniture_store))){
                poiGeoObj.setImageResource(R.drawable.shopping);
            }else if(type.equals(getResources().getString(R.string.pharmacy))){
                poiGeoObj.setImageResource(R.drawable.pharmacy);
            }else if(type.equals(getResources().getString(R.string.park)) || type.equals(getResources().getString(R.string.point_of_interest))){
                poiGeoObj.setImageResource(R.drawable.pine_tree);
            }else if(type.equals(getResources().getString(R.string.bus))){
                poiGeoObj.setImageResource(R.drawable.bus);
            }else if(type.equals(getResources().getString(R.string.mosque)) || type.equals(getResources().getString(R.string.place_of_worship)) || type.equals(getResources().getString(R.string.bank))){
                poiGeoObj.setImageResource(R.drawable.mosque);
            }else {
                poiGeoObj.setImageResource(R.drawable.map_icon);
                poiGeoObj.setImageUri(poi.getIcon());
            }

            world.addBeyondarObject(poiGeoObj);
        }

        textView.setVisibility(View.INVISIBLE);

        // ... and send it to the fragment
        arFragmentSupport.setWorld(world);

    }

    private void Set_googleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);

        }
        else {

            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);

            if (mLastLocation != null) {
                try {
                    Poi_list_call(minSpace);
                }catch (Exception e){
                    Log.d(TAG, "onCreate: Intent Error");
                }
            }
        }

    }

    @Override
    public void onClickBeyondarObject(ArrayList<BeyondarObject> beyondarObjects) {
        if (beyondarObjects.size() > 0) {
            Poi_details_call(beyondarObjects.get(0).getName());
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    @Override
    public void onTouchBeyondarView(MotionEvent event, ArBeyondarGLSurfaceView var2) {

        float x = event.getX();
        float y = event.getY();

        ArrayList<BeyondarObject> geoObjects = new ArrayList<BeyondarObject>();

        // This method call is better to don't do it in the UI thread!
        // This method is also available in the BeyondarFragment
        var2.getBeyondarObjectsOnScreenCoordinates(x, y, geoObjects);

        String textEvent = "";
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                textEvent = "Event type ACTION_DOWN: ";
                break;
            case MotionEvent.ACTION_UP:
                textEvent = "Event type ACTION_UP: ";
                break;
            case MotionEvent.ACTION_MOVE:
                textEvent = "Event type ACTION_MOVE: ";
                break;
            default:
                break;
        }

        Iterator<BeyondarObject> iterator = geoObjects.iterator();
        while (iterator.hasNext()) {
            BeyondarObject geoObject = iterator.next();
            textEvent = textEvent + " " + geoObject.getName();
            Log.d(TAG, "onTouchBeyondarView: ATTENTION !!! "+textEvent);

            // ...
            // Do something
            // ...
        }
    }
}
